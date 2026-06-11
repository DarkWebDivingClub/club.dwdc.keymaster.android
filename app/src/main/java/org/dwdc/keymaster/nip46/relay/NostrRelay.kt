package org.dwdc.keymaster.nip46.relay

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Minimal OkHttp WebSocket client for a single Nostr relay.
 * Handles connect/disconnect, sending EVENT/REQ/CLOSE messages,
 * and parsing incoming EVENT/EOSE/OK messages.
 */
class NostrRelay(
    val url: String,
    private val listener: RelayListener,
    private val client: OkHttpClient
) {

    private var webSocket: WebSocket? = null
    @Volatile
    var isConnected = false
        private set

    fun connect() {
        if (isConnected) return
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to $url")
                isConnected = true
                listener.onRelayConnected(this@NostrRelay)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    listener.onRelayMessage(this@NostrRelay, text)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling message from $url", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Connection failed to $url: ${t.message}")
                isConnected = false
                listener.onRelayDisconnected(this@NostrRelay, t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Relay closing $url: $code $reason")
                webSocket.close(1000, null)
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Relay closed $url: $code $reason")
                isConnected = false
                listener.onRelayDisconnected(this@NostrRelay, null)
            }
        })
    }

    fun disconnect() {
        isConnected = false
        webSocket?.close(1000, "disconnect")
        webSocket = null
    }

    fun sendRaw(message: String): Boolean {
        val ws = webSocket ?: return false
        Log.d(TAG, "-> $url: $message")
        return ws.send(message)
    }

    fun sendEvent(eventJson: String): Boolean {
        return sendRaw("[\"EVENT\",$eventJson]")
    }

    fun subscribe(subscriptionId: String, filterJson: String): Boolean {
        return sendRaw("[\"REQ\",\"$subscriptionId\",$filterJson]")
    }

    fun unsubscribe(subscriptionId: String): Boolean {
        return sendRaw("[\"CLOSE\",\"$subscriptionId\"]")
    }

    companion object {
        private const val TAG = "NostrRelay"
    }
}

interface RelayListener {
    fun onRelayConnected(relay: NostrRelay)
    fun onRelayMessage(relay: NostrRelay, message: String)
    fun onRelayDisconnected(relay: NostrRelay, error: Throwable?)
}
