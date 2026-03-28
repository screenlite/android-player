package org.screenlite.player.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.screenlite.player.AppConstants
import org.screenlite.player.utils.AppLogger

private const val TAG = "ScreenlitePlayerService"
private const val CHANNEL_ID = "ScreenlitePlayerChannel"
private const val NOTIFICATION_ID = 1
class ScreenlitePlayerForegroundService : Service() {
    private var serverDataService: ServerDataService? = null
    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "onCreate: Service is being created")
        createNotificationChannel()

        sharedPrefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)

        serverDataService = ServerDataService(
            serverUrl = AppConstants.WEBSOCKET_URL,
            sharedPrefs = sharedPrefs
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "onStartCommand: Received start command with startId: $startId")

        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screenlite Player")
                .setContentText("Connected to Signage Server")
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            AppLogger.e(TAG, "onStartCommand: Failed to start foreground", e)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i(TAG, "onDestroy: Service is being destroyed")
        serverDataService?.shutdown()
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screenlite Player Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}