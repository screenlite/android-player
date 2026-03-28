package org.screenlite.player.ui.timestamp

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TimestampViewModelFactory(private val prefs: SharedPreferences) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimestampViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimestampViewModel(prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}