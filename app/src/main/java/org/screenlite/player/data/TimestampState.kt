package org.screenlite.player.data

/**
 * Represents the state of the Timestamp at any given moment.
 * This is what the Activity observes to update the screen.
 */
data class TimestampState(
    val timestamp: Long? = null,     // The actual timestamp from the server
    val isEnabled: Boolean = false,  // Whether the connection is active
    val lastUpdateMillis: Long = System.currentTimeMillis() // Heartbeat for staleness detection
)