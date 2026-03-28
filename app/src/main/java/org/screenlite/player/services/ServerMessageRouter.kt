package org.screenlite.player.services

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.screenlite.player.utils.AppLogger
import java.net.URI

private const val TAG = "ServerMessageRouter"
private const val KEY_TIMESTAMP_SERVER = "timestamp_server_url"
private const val MAX_URL_LENGTH = 200

object ServerMessageRouter {
    fun route(message: ServerMessage, prefs: SharedPreferences?) {
        when (message.type) {
            "update" -> handleUpdate(message.data)
            "command" -> handleCommand(message.data)
            "ts:server" -> handleTimestampServer(message.data, prefs)
            else -> AppLogger.w(TAG, "Unknown message type: ${message.type}")
        }
    }

    private fun handleUpdate(payload: JsonObject?) {
        AppLogger.i(TAG, "Handling update: $payload")
        // TODO: implement your update logic, e.g., refresh content
    }

    private fun handleCommand(payload: JsonObject?) {
        AppLogger.i(TAG, "Handling command: $payload")
        // TODO: implement your command logic, e.g., play/pause screen
    }

    private fun handleTimestampServer(payload: JsonObject?, prefs: SharedPreferences?) {
        val urlString = payload?.get("url")?.jsonPrimitive?.contentOrNull

        if (urlString.isNullOrBlank()) {
            prefs?.edit {
                putString(KEY_TIMESTAMP_SERVER, urlString)
            }
            AppLogger.i(TAG, "Timestamp server URL cleared")
            return
        }

        if (urlString.length > MAX_URL_LENGTH) {
            AppLogger.w(TAG, "ts:server URL too long, ignoring: $urlString")
            return
        }

        try {
            val uri = URI(urlString)
            if (uri.scheme != "ws" && uri.scheme != "wss") {
                AppLogger.w(TAG, "ts:server URL not WebSocket, ignoring: $urlString")
                return
            }

            prefs?.edit {
                putString(KEY_TIMESTAMP_SERVER, urlString)
            }
            AppLogger.i(TAG, "Saved timestamp server URL: $urlString")

        } catch (e: Exception) {
            AppLogger.w(TAG, "Invalid ts:server URL, ignoring: $urlString")
        }
    }
}