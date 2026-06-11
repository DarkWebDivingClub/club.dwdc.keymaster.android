package org.dwdc.keymaster.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persists NIP-46 sessions in EncryptedSharedPreferences as a JSON array.
 * Same pattern as AccountRepository.
 */
class Nip46SessionRepository(context: Context) {

    private val prefs: SharedPreferences = SessionPrefs.get(context)
    private val gson = Gson()

    fun getSessions(): List<Nip46Session> {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<Nip46Session>>() {}.type
        return try { gson.fromJson(json, type) } catch (_: Exception) { emptyList() }
    }

    fun getSessionsForAccount(accountPubkey: String): List<Nip46Session> =
        getSessions().filter { it.accountPubkey == accountPubkey }

    fun addSession(session: Nip46Session) {
        val sessions = getSessions().toMutableList()
        // Replace existing session with same client pubkey
        sessions.removeAll { it.clientPubkey == session.clientPubkey }
        sessions.add(session)
        saveSessions(sessions)
    }

    fun removeSession(clientPubkey: String) {
        val sessions = getSessions().toMutableList()
        sessions.removeAll { it.clientPubkey == clientPubkey }
        saveSessions(sessions)
    }

    fun findSession(clientPubkey: String): Nip46Session? =
        getSessions().find { it.clientPubkey == clientPubkey }

    private fun saveSessions(sessions: List<Nip46Session>) {
        prefs.edit().putString(KEY_SESSIONS, gson.toJson(sessions)).apply()
    }

    private object SessionPrefs {
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
                "keymaster_nip46_sessions",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    companion object {
        private const val KEY_SESSIONS = "nip46_sessions"
    }
}
