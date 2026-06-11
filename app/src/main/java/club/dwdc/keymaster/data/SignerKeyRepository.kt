package club.dwdc.keymaster.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import club.dwdc.keymaster.crypto.SchnorrUtils
import club.dwdc.keymaster.crypto.toHexString
import club.dwdc.keymaster.crypto.hexToByteArray
import java.security.SecureRandom

/**
 * Stores the random NIP-46 signer keypair in EncryptedSharedPreferences.
 *
 * This keypair is used to sign kind 24133 wrapper events and for the NIP-44
 * transport encryption between the signer and remote clients. It is NOT
 * derived from the BIP-39 seed — it's a random secp256k1 key generated once.
 */
class SignerKeyRepository(context: Context) {

    private val prefs: SharedPreferences = SignerKeyPrefs.get(context)

    fun getOrCreatePrivateKey(): ByteArray {
        val hex = prefs.getString(KEY_PRIVATE, null)
        if (hex != null) return hex.hexToByteArray()

        val privKey = ByteArray(32)
        SecureRandom().nextBytes(privKey)
        prefs.edit().putString(KEY_PRIVATE, privKey.toHexString()).apply()
        return privKey
    }

    fun getPublicKeyHex(): String {
        val privKey = getOrCreatePrivateKey()
        return SchnorrUtils.getPublicKey(privKey).toHexString()
    }

    private object SignerKeyPrefs {
        @Volatile
        private var instance: SharedPreferences? = null

        fun get(context: Context): SharedPreferences {
            return instance ?: synchronized(this) {
                instance ?: create(context).also { instance = it }
            }
        }

        private fun create(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "keymaster_signer_key",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    companion object {
        private const val KEY_PRIVATE = "signer_private_key"
    }
}
