package org.screenlite.player.utils

import android.util.Log
import org.screenlite.player.BuildConfig

object AppLogger {
    private const val GLOBAL_TAG = "ScreenliteApp"

    fun d(tag: String = GLOBAL_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String = GLOBAL_TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String = GLOBAL_TAG, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, message, throwable)
        }
    }

    fun e(tag: String = GLOBAL_TAG, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, message, throwable)
        }
    }
}