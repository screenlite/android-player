package org.screenlite.player.services

import android.content.SharedPreferences
import org.screenlite.player.utils.AppLogger

private const val TAG = "CacheManager"

class CacheManager(
    private val sharedPrefs: SharedPreferences
) : SharedPreferences.OnSharedPreferenceChangeListener {

    fun start() {
        AppLogger.i(TAG, "Starting Cache Manager and registering listener")
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        
        checkAndCacheContent()
    }

    fun stop() {
        AppLogger.i(TAG, "Stopping Cache Manager and unregistering listener")
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        AppLogger.d(TAG, "Shared preference changed for key: $key")
        
        when (key) {
            "media_playlist_url", "cache_enabled_flag" -> {
                AppLogger.i(TAG, "Relevant preference changed, updating cache...")
                checkAndCacheContent()
            }
        }
    }

    private fun checkAndCacheContent() {
        val playlistUrl = sharedPrefs.getString("media_playlist_url", null)
        AppLogger.d(TAG, "Executing cache logic for: $playlistUrl")
    }
}