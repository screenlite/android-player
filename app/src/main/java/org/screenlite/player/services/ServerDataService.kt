package org.screenlite.player.services

import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.screenlite.player.network.WebSocketManager
import org.screenlite.player.utils.AppLogger

private const val TAG = "ServerDataService"

/**
 * Service to receive and process messages from server via WebSocket.
 * Uses a bounded channel to avoid memory overflow if server sends too many messages.
 */
class ServerDataService(
    serverUrl: String,
    private val sharedPrefs: SharedPreferences,
    private val scheduleQueue: ScheduleUpdateQueue
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val incomingChannel = Channel<String>(capacity = 500)

    private val wsManager = WebSocketManager(
        clientName = "Server",
        serverUrl = serverUrl,
        enableMessageLogging = false,
        onTextMessageReceived = { incomingJson ->
            scope.launch {
                incomingChannel.send(incomingJson)
            }
        }
    )

    init {
        scope.launch {
            for (msg in incomingChannel) {
                try {
                    val message = ServerMessageParser.parseMessage(msg)
                    if (message != null) {
                        ServerMessageRouter.route(message, sharedPrefs, scheduleQueue)
                    } else {
                        AppLogger.w(TAG, "Failed to parse message: $msg")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error processing message: $msg", e)
                }
            }
        }

        wsManager.connect()
    }

    fun shutdown() {
        AppLogger.i(TAG, "Shutting down ServerDataService")
        wsManager.shutdown()
        scope.cancel()
    }
}