package org.screenlite.player.services

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.screenlite.player.utils.AppLogger

private const val TAG = "ScheduleUpdateQueue"
private const val PREFS_NAME = "schedule_queue"
private const val KEY_PENDING_SCHEDULE_MANIFEST = "pending_schedule_manifest"
private const val KEY_ACTIVE_SCHEDULE_MANIFEST  = "active_schedule_manifest"

@Serializable
data class ScheduleFile(
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val contentType: String,
    val fallbackUrls: List<String> = emptyList(),
    val etag: String? = null,
)

@Serializable
data class ScheduleDataV1(
    val files: List<ScheduleFile>
)

@Serializable(with = ScheduleManifestSerializer::class)
sealed interface ScheduleManifest {
    val cacheKey: String
}

@Serializable
data class ScheduleManifestV1(
    override val cacheKey: String,
    val schemaVersion: Int = 1,
    val data: ScheduleDataV1
) : ScheduleManifest
class ScheduleUpdateQueue(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _updates = MutableSharedFlow<ScheduleManifest>(extraBufferCapacity = 1)
    val updates: SharedFlow<ScheduleManifest> = _updates.asSharedFlow()

    fun enqueue(payload: JsonObject?) {
        val manifest = parse(payload) ?: return

        if (isDuplicate(manifest.cacheKey)) {
            AppLogger.i(TAG, "Cache key ${manifest.cacheKey} already pending or active, skipping")
            return
        }

        prefs.edit(commit = true) { putString(KEY_PENDING_SCHEDULE_MANIFEST, json.encodeToString(manifest)) }
        _updates.tryEmit(manifest)
        AppLogger.i(TAG, "Enqueued manifest with cache key ${manifest.cacheKey}")
    }

    fun loadPending(): ScheduleManifest? {
        val raw = prefs.getString(KEY_PENDING_SCHEDULE_MANIFEST, null) ?: return null
        return try {
            json.decodeFromString<ScheduleManifest>(raw)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not read pending manifest: ${e.message}")
            null
        }
    }

    fun onSwitched(manifest: ScheduleManifest) {
        prefs.edit(commit = true) {
            remove(KEY_PENDING_SCHEDULE_MANIFEST)
            putString(KEY_ACTIVE_SCHEDULE_MANIFEST, json.encodeToString(manifest))
        }
    }

    fun loadActive(): ScheduleManifest? {
        val raw = prefs.getString(KEY_ACTIVE_SCHEDULE_MANIFEST, null) ?: return null
        return try {
            json.decodeFromString<ScheduleManifest>(raw)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not read active manifest: ${e.message}")
            null
        }
    }

    private fun isDuplicate(cacheKey: String): Boolean {
        val pendingKey = loadPending()?.cacheKey
        val activeKey  = loadActive()?.cacheKey
        return cacheKey == pendingKey || cacheKey == activeKey
    }

    private fun parse(payload: JsonObject?): ScheduleManifest? {
        if (payload == null) {
            AppLogger.w(TAG, "Null payload, ignoring")
            return null
        }
        return try {
            json.decodeFromJsonElement<ScheduleManifest>(payload)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Invalid manifest payload: ${e.message}")
            null
        }
    }
}

object ScheduleManifestSerializer : JsonContentPolymorphicSerializer<ScheduleManifest>(ScheduleManifest::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ScheduleManifest> {
        val jsonObject = element.jsonObject

        val version = jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1

        return when (version) {
            1 -> ScheduleManifestV1.serializer()
            else -> throw IllegalArgumentException("Unsupported schema version: $version")
        }
    }
}