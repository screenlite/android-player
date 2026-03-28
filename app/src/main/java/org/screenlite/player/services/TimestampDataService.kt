package org.screenlite.player.services

import kotlinx.coroutines.*
import okio.ByteString
import org.screenlite.player.network.WebSocketManager
import org.screenlite.player.ui.timestamp.TimestampState
import org.screenlite.player.utils.AppLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TimestampDataService(
    serverUrl: String,
    private val onStateChanged: (TimestampState) -> Unit,
    private val timeout: Duration = 5.seconds
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var latestTimestamp: Long? = null
    private var timeoutJob: Job? = null

    private val wsManager = WebSocketManager(
        clientName = "TimestampServer",
        serverUrl = serverUrl,
        enableMessageLogging = false,
        onBytesReceived = ::handleIncomingBytes
    )

    init {
        wsManager.connect()
        AppLogger.i(TAG, "TimestampDataService started")
    }

    private fun handleIncomingBytes(bytes: ByteString) {
        if (bytes.size < 8) {
            AppLogger.w(TAG, "Byte array too short: size=${bytes.size}")
            return
        }
        try {
            val ts = ByteBuffer.wrap(bytes.toByteArray())
                .order(ByteOrder.LITTLE_ENDIAN)
                .long

            latestTimestamp = ts

            onStateChanged(TimestampState(timestamp = ts, isEnabled = true))
            AppLogger.d(TAG, "Emitted timestamp=$ts isEnabled=true")

            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                delay(timeout)
                AppLogger.w(TAG, "Timeout — emitting disabled state")
                onStateChanged(TimestampState(timestamp = latestTimestamp ?: 0L, isEnabled = false))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing timestamp bytes", e)
        }
    }

    fun shutdown() {
        AppLogger.i(TAG, "Shutting down TimestampDataService")
        timeoutJob?.cancel()
        wsManager.shutdown()
        scope.cancel()
    }

    companion object {
        private const val TAG = "TimestampDataService"
    }
}