package org.dwdc.keymaster.nip46

import org.dwdc.keymaster.crypto.SchnorrUtils
import org.dwdc.keymaster.crypto.toHexString
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nostr.encryption.MessageCipher04
import nostr.encryption.MessageCipher44
import java.security.MessageDigest

/**
 * Builds kind 24133 NIP-46 response events.
 *
 * Supports both NIP-04 and NIP-44 transport encryption.
 */
object Nip46EventBuilder {

    // Must use disableHtmlEscaping for correct event ID computation
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    /**
     * Build a kind 24133 response event JSON string.
     *
     * @param useNip04 if true, encrypt with NIP-04; otherwise NIP-44
     */
    fun buildResponse(
        requestId: String,
        result: String?,
        error: String?,
        signerPrivKey: ByteArray,
        signerPubHex: String,
        clientPubHex: String,
        useNip04: Boolean = false
    ): String {
        // 1. Build inner JSON-RPC response
        val inner = JsonObject()
        inner.addProperty("id", requestId)
        if (error != null) {
            inner.addProperty("error", error)
        } else {
            inner.addProperty("result", result ?: "")
        }
        val innerJson = gson.toJson(inner)

        // 2. Encrypt: signer -> client
        val clientPubBytes = clientPubHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val ciphertext = if (useNip04) {
            MessageCipher04(signerPrivKey, clientPubBytes).encrypt(innerJson)
        } else {
            MessageCipher44(signerPrivKey, clientPubBytes).encrypt(innerJson)
        }

        // 3. Build outer event
        val createdAt = System.currentTimeMillis() / 1000
        val tags = JsonArray().apply {
            add(JsonArray().apply {
                add("p")
                add(clientPubHex)
            })
        }

        val event = JsonObject().apply {
            addProperty("pubkey", signerPubHex)
            addProperty("created_at", createdAt)
            addProperty("kind", 24133)
            add("tags", tags)
            addProperty("content", ciphertext)
        }

        // 4. Compute event ID
        val serialized = JsonArray().apply {
            add(0)
            add(signerPubHex)
            add(createdAt)
            add(24133)
            add(tags)
            add(ciphertext)
        }
        val serializedStr = gson.toJson(serialized)
        val eventIdBytes = MessageDigest.getInstance("SHA-256")
            .digest(serializedStr.toByteArray(Charsets.UTF_8))
        val eventIdHex = eventIdBytes.toHexString()

        // 5. Sign with signer keypair
        val sig = SchnorrUtils.sign(signerPrivKey, eventIdBytes)

        event.addProperty("id", eventIdHex)
        event.addProperty("sig", sig.toHexString())

        return gson.toJson(event)
    }
}
