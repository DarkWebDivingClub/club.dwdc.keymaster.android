package club.dwdc.keymaster.crypto

import club.dwdc.keyvault.core.Bip32KeyVault
import club.dwdc.keyvault.nostr.KeyVaultIdentity
import nostr.base.PublicKey
import nostr.encryption.MessageCipher44

class NostrKeyService(mnemonic: String, passphrase: String = "", identity: String) {

    private val kvIdentity: KeyVaultIdentity =
        KeyVaultIdentity(Bip32KeyVault(mnemonic, passphrase), identity)

    /** Get the 32-byte x-only Schnorr public key as hex. */
    fun getPublicKeyHex(): String =
        kvIdentity.publicKey.rawData.toHexString()

    /** Get the public key as bech32 npub (NIP-19). */
    fun getNpub(): String =
        kvIdentity.publicKey.toBech32String()

    /**
     * Sign a Nostr event ID (32-byte SHA-256 hash) with BIP-340 Schnorr.
     * Uses signPrehashed() to avoid double-hashing.
     * @return 64-byte Schnorr signature as hex string
     */
    fun signEventId(eventIdHex: String): String =
        kvIdentity.signPrehashed(eventIdHex.hexToByteArray()).toHexString()

    /**
     * NIP-44 encrypt a plaintext message for the given recipient.
     * @param plaintext the message to encrypt
     * @param recipientPubKeyHex 32-byte x-only public key of the recipient (hex)
     * @return NIP-44 encrypted payload (base64)
     */
    fun encrypt(plaintext: String, recipientPubKeyHex: String): String =
        MessageCipher44(kvIdentity, PublicKey(recipientPubKeyHex)).encrypt(plaintext)

    /**
     * NIP-44 decrypt a ciphertext from the given sender.
     * @param ciphertext NIP-44 encrypted payload (base64)
     * @param senderPubKeyHex 32-byte x-only public key of the sender (hex)
     * @return decrypted plaintext
     */
    fun decrypt(ciphertext: String, senderPubKeyHex: String): String =
        MessageCipher44(kvIdentity, PublicKey(senderPubKeyHex)).decrypt(ciphertext)
}

fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

fun String.hexToByteArray(): ByteArray {
    check(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
