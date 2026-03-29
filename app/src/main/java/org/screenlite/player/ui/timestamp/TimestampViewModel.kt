package org.screenlite.player.ui.timestamp

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.screenlite.player.data.TimestampRepository
import org.screenlite.player.AppConstants
import org.screenlite.player.data.TimestampState
import org.screenlite.player.utils.AppLogger

class TimestampViewModel(
    private val prefs: SharedPreferences
) : ViewModel() {
    private var prevSyncMs: Long? = null
    private val TAG = "TimestampViewModel"

    val timestampState: StateFlow<TimestampState?> = prefs.timestampServerUrlFlow()
        .distinctUntilChanged()
        .transform { serverUrl ->
            if (serverUrl.isNullOrBlank()) {
                AppLogger.w(TAG, "Timestamp server URL is absent — timestamp feed disabled")
                emit(TimestampState())
            } else {
                AppLogger.i(TAG, "Timestamp server URL received ('$serverUrl') — subscribing to timestamp stream")
                emitAll(TimestampRepository.getTimestampStream(serverUrl))
            }
        }
        .catch { e ->
            AppLogger.e(TAG, "Unhandled error in timestamp pipeline — falling back to disabled state", e)
            emit(TimestampState())
        }
        .onCompletion { cause ->
            AppLogger.d(TAG, "Timestamp pipeline shut down. Cause: $cause")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null
        )

    val syncServerTimestampMs: StateFlow<Long?> = timestampState
        .map { state ->
            when {
                state == null -> null
                state.isEnabled -> {
                    if (prevSyncMs == null) {
                        AppLogger.d(TAG, "Timestamp feed started — first server timestamp received")
                    }
                    state.timestamp
                }
                else -> {
                    AppLogger.w(TAG, "Server timestamp lost — consumers will fall back to device time")
                    null
                }
            }.also { prevSyncMs = it }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null
        )

    private fun SharedPreferences.timestampServerUrlFlow(): Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == AppConstants.KEY_TIMESTAMP_SERVER) {
                val updatedUrl = prefs.getString(key, null)
                AppLogger.d(TAG, "Timestamp server URL changed in prefs: '$updatedUrl'")
                trySend(updatedUrl)
            }
        }

        registerOnSharedPreferenceChangeListener(listener)
        trySend(getString(AppConstants.KEY_TIMESTAMP_SERVER, null))

        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override fun onCleared() {
        super.onCleared()
        AppLogger.d(TAG, "TimestampViewModel cleared — timestamp stream will be torn down")
    }
}