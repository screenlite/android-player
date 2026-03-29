package org.screenlite.player.data

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import okio.ByteString
import org.screenlite.player.network.WebSocketManager
import org.screenlite.player.data.TimestampState
import org.screenlite.player.utils.AppLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object TimestampRepository {
    private const val TAG = "TimestampRepository"

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val activeStreams = ConcurrentHashMap<String, Flow<TimestampState>>()

    fun getTimestampStream(serverUrl: String, timeout: Duration = 5.seconds): Flow<TimestampState> {
        return activeStreams.getOrPut(serverUrl) {
            callbackFlow {
                AppLogger.d(TAG, "Starting connection to $serverUrl")

                var latestTimestamp: Long? = null
                var timeoutJob: Job? = null

                val wsManager = WebSocketManager(
                    clientName = "TimestampServer",
                    serverUrl = serverUrl,
                    enableMessageLogging = false,
                    onBytesReceived = { bytes ->
                        if (bytes.size < 8) return@WebSocketManager
                        try {
                            val ts = ByteBuffer.wrap(bytes.toByteArray())
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .long

                            latestTimestamp = ts
                            trySend(TimestampState(timestamp = ts, isEnabled = true))

                            timeoutJob?.cancel()
                            timeoutJob = launch {
                                delay(timeout)
                                AppLogger.w(TAG, "Timeout — emitting disabled state")
                                trySend(TimestampState(timestamp = latestTimestamp ?: 0L, isEnabled = false))
                            }
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error parsing timestamp bytes", e)
                        }
                    }
                )

                wsManager.connect()
                AppLogger.i(TAG, "Timestamp WebSocket started")

                awaitClose {
                    AppLogger.i(TAG, "Flow cancelled. Shutting down Timestamp WebSocket and timers.")
                    timeoutJob?.cancel()
                    wsManager.shutdown()
                }
            }
                .shareIn(
                    scope = repositoryScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
                    replay = 1
                )
        }
    }
}