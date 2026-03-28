package org.screenlite.player.services

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.screenlite.player.utils.AppLogger

private const val TAG = "ServerMessageParser"

@Serializable
data class ServerMessage(
    val type: String,
    val data: JsonObject? = null,
    val timestamp: Long? = null
)

object ServerMessageParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseMessage(message: String): ServerMessage? {
        return try {
            json.decodeFromString<ServerMessage>(message)
        } catch (e: SerializationException) {
            AppLogger.w(TAG, "Failed to parse message: $message")
            null
        }
    }
}