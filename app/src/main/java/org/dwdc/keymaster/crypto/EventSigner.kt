package org.dwdc.keymaster.crypto

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import java.security.MessageDigest

/**
 * Shared utility for Nostr event ID computation and signing.
 * Used by NIP-55 (SignerActivity, SignerContentProvider) and NIP-46 (Nip46RequestHandler).
 */
object EventSigner {

    // disableHtmlEscaping() prevents Gson from escaping '=' as '\u003d' in JSON strings.
    // NIP-01 event ID = SHA-256 of the canonical JSON serialization; escaping '='
    // produces a different hash than what other Nostr libraries (rust-nostr, nostr-tools) compute.
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    /**
     * Compute the NIP-01 event ID (SHA-256 of the canonical serialization).
     *
     * @param eventJson the event JSON object (must have created_at, kind, tags, content)
     * @param pubkey the 32-byte x-only public key hex of the signer
     * @return 32-byte event ID as hex string
     */
    fun computeEventId(eventJson: String, pubkey: String): String {
        val eventObj = JsonParser.parseString(eventJson).asJsonObject
        val serialized = JsonArray().apply {
            add(0)
            add(pubkey)
            add(eventObj.get("created_at"))
            add(eventObj.get("kind"))
            add(eventObj.get("tags"))
            add(eventObj.get("content").asString)
        }
        val serializedStr = gson.toJson(serialized)
        val eventIdBytes = MessageDigest.getInstance("SHA-256")
            .digest(serializedStr.toByteArray(Charsets.UTF_8))
        return eventIdBytes.toHexString()
    }

    /**
     * Sign an event using the user's NostrKeyService.
     * Computes the event ID, signs it, and returns the full signed event JSON.
     *
     * @return Triple of (eventIdHex, signatureHex, signedEventJson)
     */
    fun signEvent(eventJson: String, keyService: NostrKeyService): Triple<String, String, String> {
        val eventObj = JsonParser.parseString(eventJson).asJsonObject
        val pubkey = keyService.getPublicKeyHex()
        val eventIdHex = computeEventId(eventJson, pubkey)
        val signatureHex = keyService.signEventId(eventIdHex)

        eventObj.addProperty("id", eventIdHex)
        eventObj.addProperty("pubkey", pubkey)
        eventObj.addProperty("sig", signatureHex)
        val signedEventJson = gson.toJson(eventObj)

        return Triple(eventIdHex, signatureHex, signedEventJson)
    }
}
