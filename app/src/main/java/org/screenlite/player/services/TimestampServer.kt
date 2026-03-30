package org.screenlite.player.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.screenlite.player.utils.AppLogger
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "TimestampServer"
private const val BROADCAST_INTERVAL_MS = 10L

class TimestampServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    private val connectionCount = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var broadcastJob: Job? = null

    init {
        isReuseAddr = true
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val count = connectionCount.incrementAndGet()
        AppLogger.i(TAG, "Client connected (total: $count)")
        if (count == 1) startBroadcast()
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        val count = connectionCount.decrementAndGet()
        AppLogger.i(TAG, "Client disconnected (total: $count)")
        if (count == 0) stopBroadcast()
    }

    override fun onMessage(conn: WebSocket, message: String) {}

    override fun onError(conn: WebSocket?, ex: Exception) {
        AppLogger.e(TAG, "Error", ex)
    }

    override fun onStart() {
        AppLogger.i(TAG, "Listening on port ${address.port}")
    }

    private fun startBroadcast() {
        broadcastJob = scope.launch {
            val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            while (isActive) {
                buf.clear()
                buf.putLong(System.currentTimeMillis())
                broadcast(buf.array().clone())
                delay(BROADCAST_INTERVAL_MS)
            }
        }
    }

    private fun stopBroadcast() {
        broadcastJob?.cancel()
        broadcastJob = null
    }

    fun shutdown() {
        stopBroadcast()
        scope.cancel()
        try { stop() } catch (_: Exception) {}
    }
}
