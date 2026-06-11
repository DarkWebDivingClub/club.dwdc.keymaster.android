package club.dwdc.keymaster.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AppPermission { ALLOWED, DENIED }

class PermissionRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "keymaster_permissions",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getPermission(packageName: String, pubkeyHex: String): AppPermission? {
        val value = prefs.getString(key(packageName, pubkeyHex), null) ?: return null
        return try { AppPermission.valueOf(value) } catch (_: Exception) { null }
    }

    fun setPermission(packageName: String, pubkeyHex: String, permission: AppPermission) {
        prefs.edit().putString(key(packageName, pubkeyHex), permission.name).apply()
    }

    fun removePermission(packageName: String, pubkeyHex: String) {
        prefs.edit().remove(key(packageName, pubkeyHex)).apply()
    }

    /** Get all permissions for a specific account. Returns Map<packageName, AppPermission>. */
    fun getPermissionsForAccount(pubkeyHex: String): Map<String, AppPermission> {
        val suffix = ":$pubkeyHex"
        val result = mutableMapOf<String, AppPermission>()
        for ((k, v) in prefs.all) {
            if (k.startsWith("perm_") && k.endsWith(suffix) && v is String) {
                val pkg = k.removePrefix("perm_").removeSuffix(suffix)
                try { result[pkg] = AppPermission.valueOf(v) } catch (_: Exception) {}
            }
        }
        return result
    }

    /**
     * Migrate old single-account permission keys (perm_{pkg}) to
     * the new per-account format (perm_{pkg}:{pubkeyHex}).
     */
    fun migratePermissions(defaultPubkeyHex: String) {
        val editor = prefs.edit()
        for ((k, v) in prefs.all) {
            if (k.startsWith("perm_") && !k.contains(":") && v is String) {
                val pkg = k.removePrefix("perm_")
                editor.putString(key(pkg, defaultPubkeyHex), v)
                editor.remove(k)
            }
        }
        editor.apply()
    }

    private fun key(packageName: String, pubkeyHex: String) = "perm_$packageName:$pubkeyHex"
}
