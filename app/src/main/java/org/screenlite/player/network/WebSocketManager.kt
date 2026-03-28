package org.screenlite.player.network

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import okhttp3.*
import okio.ByteString
import org.screenlite.player.utils.AppLogger
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.random.Random

class WebSocketManager(
    private val clientName: String,
    private val serverUrl: String,
    private val enableMessageLogging: Boolean = false,
    private val onTextMessageReceived: ((String) -> Unit)? = null,
    private val onBytesReceived: ((ByteString) -> Unit)? = null
    ) {

    companion object {
        private const val INITIAL_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 30_000L
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val JITTER_FACTOR = 0.2
    }

    private val tag = "WS-[$clientName]"

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Reconnecting : ConnectionState()
        data class Failed(val reason: String) : ConnectionState()
    }

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var activeSocket: WebSocket? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val isManuallyClosed = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)

    private var reconnectJob: Job? = null
    private var reconnectDelay = INITIAL_DELAY_MS

    private val outgoingChannel = Channel<String>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (message in outgoingChannel) {
                state.first { it == ConnectionState.Connected }
                logDebug("Sending message (${message.length} bytes)")
                activeSocket?.send(message)
            }
        }
    }

    fun connect() {
        if (!isConnecting.compareAndSet(false, true)) {
            logDebug("Connect skipped: already connecting")
            return
        }

        if (_state.value == ConnectionState.Connected) {
            logDebug("Connect skipped: already connected")
            isConnecting.set(false)
            return
        }

        isManuallyClosed.set(false)
        _state.value = ConnectionState.Connecting

        logInfo("Connecting to $serverUrl")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        activeSocket = okHttpClient.newWebSocket(request, socketListener)
    }

    fun disconnect() {
        logInfo("Manual disconnect")

        isManuallyClosed.set(true)
        reconnectJob?.cancel()

        activeSocket?.close(1000, "Manual close")
        activeSocket = null

        isConnecting.set(false)
        _state.value = ConnectionState.Disconnected
    }

    fun send(message: String) {
        scope.launch {
            outgoingChannel.send(message)
        }
    }

    fun shutdown() {
        logInfo("Shutting down WebSocketManager")
        disconnect()
        scope.cancel()
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }

    private val socketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            logInfo("Connected")
            activeSocket = webSocket
            reconnectDelay = INITIAL_DELAY_MS
            isConnecting.set(false)
            _state.value = ConnectionState.Connected
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (enableMessageLogging) logDebug("Received text message (${text.length} bytes)")
            onTextMessageReceived?.invoke(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (enableMessageLogging) logDebug("Received byte message (${bytes.size} bytes)")
            onBytesReceived?.invoke(bytes)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logWarn("Closed: code=$code reason=$reason")
            activeSocket = null
            isConnecting.set(false)

            if (!isManuallyClosed.get()) {
                scheduleReconnect("Closed: $code $reason")
            } else {
                _state.value = ConnectionState.Disconnected
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            activeSocket = null
            isConnecting.set(false)

            val isNetworkError = when (t) {
                is ConnectException,
                is SocketTimeoutException,
                is UnknownHostException,
                is EOFException,
                is SocketException -> true
                else -> false
            }

            val reason = if (isNetworkError) {
                "Network error: ${t.message}"
            } else {
                "Unexpected error: ${t.message}"
            }

            if (isNetworkError) {
                logWarn(reason)
            } else {
                logError(reason, t)
            }

            if (!isManuallyClosed.get()) {
                scheduleReconnect(reason)
            } else {
                _state.value = ConnectionState.Failed(reason)
            }
        }
    }

    private fun scheduleReconnect(reason: String) {
        if (reconnectJob?.isActive == true) {
            logDebug("Reconnect already scheduled")
            return
        }

        _state.value = ConnectionState.Reconnecting

        reconnectJob = scope.launch {
            val jitter = (reconnectDelay * JITTER_FACTOR * Random.nextDouble()).toLong()
            val delayWithJitter = reconnectDelay + jitter

            logInfo("Reconnecting in ${delayWithJitter}ms (reason: $reason)")

            delay(delayWithJitter)

            reconnectDelay = min(
                (reconnectDelay * BACKOFF_MULTIPLIER).toLong(),
                MAX_DELAY_MS
            )

            connect()
        }
    }

    private fun logDebug(message: String) =
        AppLogger.d(tag, message)

    private fun logInfo(message: String) =
        AppLogger.i(tag, message)

    private fun logWarn(message: String) =
        AppLogger.w(tag, message)

    private fun logError(message: String, t: Throwable? = null) =
        AppLogger.e(tag, message, t)
}