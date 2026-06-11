package club.dwdc.keymaster.data

/**
 * Represents an active NIP-46 remote signer session.
 *
 * @param clientPubkey the client's secp256k1 public key (hex)
 * @param clientName human-readable name from the nostrconnect URL (may be null)
 * @param accountPubkey the Nostr pubkey of the KeyMaster account bound to this session (hex)
 * @param accountIdentity the identity string used for key derivation
 * @param relays the relay URLs from the nostrconnect URL
 * @param permissions comma-separated NIP-46 permissions (may be null)
 * @param createdAt Unix timestamp (seconds) when the session was created
 */
data class Nip46Session(
    val clientPubkey: String,
    val clientName: String?,
    val accountPubkey: String,
    val accountIdentity: String,
    val relays: List<String>,
    val permissions: String?,
    val createdAt: Long
)
