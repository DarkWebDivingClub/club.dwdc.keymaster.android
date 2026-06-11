package club.dwdc.keymaster.nip46

import android.content.Context
import android.util.Log
import club.dwdc.keymaster.crypto.EventSigner
import club.dwdc.keymaster.crypto.NostrKeyService
import club.dwdc.keymaster.crypto.hexToByteArray
import club.dwdc.keymaster.data.Nip46Session
import club.dwdc.keymaster.data.Nip46SessionRepository
import club.dwdc.keymaster.data.SeedRepository
import club.dwdc.keymaster.data.SignerKeyRepository
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nostr.encryption.MessageCipher04
import nostr.encryption.MessageCipher44

/**
 * Dispatches NIP-46 JSON-RPC methods received in kind 24133 events.
 *
 * Supports both NIP-04 and NIP-44 transport encryption, auto-detecting
 * the format on incoming messages (NIP-04 if content contains "?iv=").
 * Responses use the same encryption as the incoming request.
 */
class Nip46RequestHandler(private val context: Context) {

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val signerKeyRepo = SignerKeyRepository(context)
    private val sessionRepo = Nip46SessionRepository(context)
    private val seedRepo = SeedRepository(context)

    /**
     * Handle an incoming kind 24133 event.
     *
     * @param senderPubHex the pubkey of the event sender (the client)
     * @param content the encrypted content of the event
     * @return the signed response event JSON, or null if the event should be ignored
     */
    fun handleEvent(senderPubHex: String, content: String): String? {
        val session = sessionRepo.findSession(senderPubHex)
        if (session == null) {
            Log.w(TAG, "No session for client $senderPubHex, ignoring")
            return null
        }

        val signerPrivKey = signerKeyRepo.getOrCreatePrivateKey()
        val signerPubHex = signerKeyRepo.getPublicKeyHex()
        val clientPubBytes = senderPubHex.hexToByteArray()

        // Auto-detect encryption: NIP-04 if "?iv=" present, else NIP-44
        val useNip04 = content.contains("?iv=")
        val decrypted: String
        try {
            decrypted = if (useNip04) {
                MessageCipher04(signerPrivKey, clientPubBytes).decrypt(content)
            } else {
                MessageCipher44(signerPrivKey, clientPubBytes).decrypt(padBase64(content))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt NIP-46 request from $senderPubHex (nip04=$useNip04)", e)
            Log.d(TAG, "Raw content (${content.length} chars): $content")
            return null
        }

        Log.d(TAG, "NIP-46 request from $senderPubHex: $decrypted")

        // Parse JSON-RPC request
        val request: JsonObject
        try {
            request = JsonParser.parseString(decrypted).asJsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Invalid JSON-RPC request", e)
            return null
        }

        val id = request.get("id")?.asString ?: return null
        val method = request.get("method")?.asString ?: return null
        val params = request.getAsJsonArray("params")

        // Dispatch
        val (result, error) = try {
            dispatch(method, params, session)
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching $method", e)
            Pair(null, e.message ?: "Internal error")
        }

        // Respond with same encryption format the client used
        return Nip46EventBuilder.buildResponse(
            requestId = id,
            result = result,
            error = error,
            signerPrivKey = signerPrivKey,
            signerPubHex = signerPubHex,
            clientPubHex = senderPubHex,
            useNip04 = useNip04
        )
    }

    /**
     * Build the connect ack response for a nostrconnect:// URL.
     * Uses NIP-04 by default since most clients (welshman) default to NIP-04.
     */
    fun buildConnectAck(connectUrl: NostrConnectUrl): String {
        val signerPrivKey = signerKeyRepo.getOrCreatePrivateKey()
        val signerPubHex = signerKeyRepo.getPublicKeyHex()

        return Nip46EventBuilder.buildResponse(
            requestId = connectUrl.secret ?: "",
            result = connectUrl.secret ?: "ack",
            error = null,
            signerPrivKey = signerPrivKey,
            signerPubHex = signerPubHex,
            clientPubHex = connectUrl.clientPubkey,
            useNip04 = true
        )
    }

    private fun dispatch(
        method: String,
        params: com.google.gson.JsonArray?,
        session: Nip46Session
    ): Pair<String?, String?> {
        val mnemonic = seedRepo.getMnemonic()
            ?: return Pair(null, "No seed configured")
        val passphrase = seedRepo.getPassphrase()
        val keyService = NostrKeyService(mnemonic, passphrase, session.accountIdentity)

        return when (method) {
            "connect" -> Pair("ack", null)
            "ping" -> Pair("pong", null)
            "get_public_key" -> Pair(keyService.getPublicKeyHex(), null)
            "sign_event" -> handleSignEvent(params, keyService)
            "nip44_encrypt" -> handleNip44Encrypt(params, keyService)
            "nip44_decrypt" -> handleNip44Decrypt(params, keyService)
            else -> Pair(null, "Unsupported method: $method")
        }
    }

    private fun handleSignEvent(
        params: com.google.gson.JsonArray?,
        keyService: NostrKeyService
    ): Pair<String?, String?> {
        val eventJson = params?.get(0)?.toString()
            ?: return Pair(null, "Missing event parameter")

        val (_, _, signedEventJson) = EventSigner.signEvent(eventJson, keyService)
        return Pair(signedEventJson, null)
    }

    private fun handleNip44Encrypt(
        params: com.google.gson.JsonArray?,
        keyService: NostrKeyService
    ): Pair<String?, String?> {
        if (params == null || params.size() < 2)
            return Pair(null, "Missing parameters")

        val thirdPartyPub = params.get(0).asString
        val plaintext = params.get(1).asString
        val ciphertext = keyService.encrypt(plaintext, thirdPartyPub)
        return Pair(ciphertext, null)
    }

    private fun handleNip44Decrypt(
        params: com.google.gson.JsonArray?,
        keyService: NostrKeyService
    ): Pair<String?, String?> {
        if (params == null || params.size() < 2)
            return Pair(null, "Missing parameters")

        val thirdPartyPub = params.get(0).asString
        val ciphertext = params.get(1).asString
        val decrypted = keyService.decrypt(ciphertext, thirdPartyPub)
        return Pair(decrypted, null)
    }

    private fun padBase64(s: String): String {
        val remainder = s.length % 4
        if (remainder == 0) return s
        return s + "=".repeat(4 - remainder)
    }

    companion object {
        private const val TAG = "Nip46Handler"
    }
}
