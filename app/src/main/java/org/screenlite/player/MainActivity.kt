package org.screenlite.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import kotlinx.coroutines.launch
import org.screenlite.player.services.ScreenlitePlayerForegroundService
import org.screenlite.player.ui.timestamp.TimestampDisplay
import org.screenlite.player.ui.timestamp.TimestampViewModel
import org.screenlite.player.ui.timestamp.TimestampViewModelFactory
import org.screenlite.player.utils.AppLogger
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val timestampViewModel: TimestampViewModel by viewModels {
        TimestampViewModelFactory(getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE))
    }

    var syncServerTimestampMs: Long? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppLogger.d(TAG, "onCreate")

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        startPlayerService()
        observeTimestampState()

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                colors = SurfaceDefaults.colors(containerColor = Color.Black)
            ) {
                val remoteTs by timestampViewModel.syncServerTimestampMs.collectAsState(initial = null)

                Box(contentAlignment = Alignment.Center) {
                    if (remoteTs != null) {
                        TimestampDisplay(
                            remoteTimestampMs = remoteTs!!,
                            style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                        )
                    }
                }
            }
        }
    }

    private fun startPlayerService() {
        AppLogger.i(TAG, "Requesting foreground service start: ScreenlitePlayerForegroundService")
        try {
            val serviceIntent = Intent(this, ScreenlitePlayerForegroundService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start ScreenlitePlayerForegroundService", e)
        }
    }

    private fun observeTimestampState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                timestampViewModel.syncServerTimestampMs.collect { ts ->
                    syncServerTimestampMs = ts
                }
            }
        }
    }
}