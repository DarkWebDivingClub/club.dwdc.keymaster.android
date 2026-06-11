package club.dwdc.keymaster.crypto

import org.bouncycastle.crypto.ec.CustomNamedCurves
import java.math.BigInteger
import java.security.MessageDigest

/**
 * Standalone BIP-340 Schnorr signing for secp256k1.
 *
 * Used to sign kind 24133 wrapper events with the random NIP-46 signer keypair,
 * which is not derived from the BIP-39 seed and thus can't use KeyVaultIdentity.
 *
 * Ported from Bip32KeyVault.java's schnorrSign() implementation.
 */
object SchnorrUtils {

    private val SECP256K1 = CustomNamedCurves.getByName("secp256k1")
    private val N = SECP256K1.n
    private val G = SECP256K1.g

    /**
     * BIP-340 Schnorr sign a 32-byte message hash.
     * @param privateKey 32-byte secp256k1 private key
     * @param messageHash 32-byte message (typically SHA-256 of Nostr event serialization)
     * @return 64-byte Schnorr signature (R.x || s)
     */
    fun sign(privateKey: ByteArray, messageHash: ByteArray): ByteArray {
        require(messageHash.size == 32) { "Message hash must be 32 bytes" }

        val d = BigInteger(1, privateKey)
        val P = G.multiply(d).normalize()
        val px = bigIntTo32Bytes(P.xCoord.toBigInteger())

        // If P.y is odd, negate d
        val dAdj = if (P.yCoord.toBigInteger().testBit(0)) N.subtract(d) else d

        // Deterministic nonce: k = tagged_hash("BIP0340/nonce", t || px || m) mod n
        val aux = ByteArray(32)
        val t = xorBytes(bigIntTo32Bytes(dAdj), taggedHash("BIP0340/aux", aux))
        val nonceInput = concat(t, px, messageHash)
        val kHash = taggedHash("BIP0340/nonce", nonceInput)
        var k = BigInteger(1, kHash).mod(N)
        require(k != BigInteger.ZERO) { "Nonce is zero" }

        val R = G.multiply(k).normalize()
        if (R.yCoord.toBigInteger().testBit(0)) {
            k = N.subtract(k)
        }
        val rx = bigIntTo32Bytes(R.xCoord.toBigInteger())

        // Challenge: e = tagged_hash("BIP0340/challenge", rx || px || m) mod n
        val eHash = taggedHash("BIP0340/challenge", concat(rx, px, messageHash))
        val e = BigInteger(1, eHash).mod(N)

        // s = (k + e * dAdj) mod n
        val s = k.add(e.multiply(dAdj)).mod(N)

        return concat(rx, bigIntTo32Bytes(s))
    }

    /**
     * Derive the 32-byte x-only public key from a private key.
     * @param privateKey 32-byte secp256k1 private key
     * @return 32-byte x-only public key
     */
    fun getPublicKey(privateKey: ByteArray): ByteArray {
        val d = BigInteger(1, privateKey)
        val P = G.multiply(d).normalize()
        return bigIntTo32Bytes(P.xCoord.toBigInteger())
    }

    private fun taggedHash(tag: String, data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val tagHash = md.digest(tag.toByteArray(Charsets.UTF_8))
        md.reset()
        md.update(tagHash)
        md.update(tagHash)
        md.update(data)
        return md.digest()
    }

    private fun bigIntTo32Bytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        if (bytes.size == 32) return bytes
        if (bytes.size > 32) return bytes.copyOfRange(bytes.size - 32, bytes.size)
        val padded = ByteArray(32)
        System.arraycopy(bytes, 0, padded, 32 - bytes.size, bytes.size)
        return padded
    }

    private fun xorBytes(a: ByteArray, b: ByteArray): ByteArray {
        return ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val result = ByteArray(arrays.sumOf { it.size })
        var pos = 0
        for (a in arrays) {
            System.arraycopy(a, 0, result, pos, a.size)
            pos += a.size
        }
        return result
    }
}
