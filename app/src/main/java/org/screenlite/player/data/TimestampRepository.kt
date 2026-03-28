package org.screenlite.player.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.screenlite.player.services.TimestampDataService
import org.screenlite.player.ui.timestamp.TimestampState
import org.screenlite.player.utils.AppLogger

class TimestampRepository {
    fun getTimestampStream(url: String): Flow<TimestampState> = callbackFlow {
        AppLogger.d("TimestampRepo", "Starting connection to $url")

        val service = TimestampDataService(
            serverUrl = url,
            onStateChanged = { state ->
                val result = trySend(state)
                AppLogger.d("TimestampRepo", "trySend ts=${state.timestamp} enabled=${state.isEnabled} success=${result.isSuccess}")
            }
        )

        awaitClose {
            AppLogger.d("TimestampRepo", "Closing connection")
            service.shutdown()
        }
    }
}