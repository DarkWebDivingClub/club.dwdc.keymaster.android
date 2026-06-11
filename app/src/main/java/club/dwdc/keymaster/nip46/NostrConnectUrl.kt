package club.dwdc.keymaster.nip46

import android.net.Uri

/**
 * Parses `nostrconnect://<client-pubkey>?relay=...&secret=...&name=...&perms=...` URLs.
 *
 * See NIP-46: https://github.com/nostr-protocol/nips/blob/master/46.md
 */
data class NostrConnectUrl(
    val clientPubkey: String,
    val relays: List<String>,
    val secret: String?,
    val perms: String?,
    val name: String?
) {
    companion object {
        /**
         * Parse a nostrconnect:// URL string.
         * @throws IllegalArgumentException if the URL is invalid
         */
        fun parse(url: String): NostrConnectUrl {
            require(url.startsWith("nostrconnect://")) { "Not a nostrconnect:// URL" }

            val uri = Uri.parse(url)
            val clientPubkey = uri.host
                ?: throw IllegalArgumentException("Missing client pubkey in URL")

            require(clientPubkey.length == 64 && clientPubkey.all { it in '0'..'9' || it in 'a'..'f' }) {
                "Invalid client pubkey: must be 64-char hex"
            }

            val relays = uri.getQueryParameters("relay")
            require(relays.isNotEmpty()) { "At least one relay is required" }

            return NostrConnectUrl(
                clientPubkey = clientPubkey,
                relays = relays,
                secret = uri.getQueryParameter("secret"),
                perms = uri.getQueryParameter("perms"),
                name = uri.getQueryParameter("name")
            )
        }
    }
}
