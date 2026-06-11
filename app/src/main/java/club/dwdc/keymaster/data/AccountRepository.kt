package club.dwdc.keymaster.data

import android.content.Context
import android.content.SharedPreferences
import club.dwdc.keymaster.crypto.NostrKeyService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AccountRepository(context: Context) {

    private val prefs: SharedPreferences = SeedPrefs.get(context)
    private val gson = Gson()

    fun getAccounts(): List<Account> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        val type = object : TypeToken<List<Account>>() {}.type
        return try { gson.fromJson(json, type) } catch (_: Exception) { emptyList() }
    }

    fun addAccount(account: Account) {
        val accounts = getAccounts().toMutableList()
        if (accounts.none { it.pubkeyHex == account.pubkeyHex }) {
            accounts.add(account)
            saveAccounts(accounts)
        }
    }

    fun removeAccount(pubkeyHex: String) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.pubkeyHex == pubkeyHex }
        saveAccounts(accounts)
    }

    fun findAccountByPubkey(pubkeyHex: String): Account? =
        getAccounts().find { it.pubkeyHex == pubkeyHex }

    fun hasAccounts(): Boolean = getAccounts().isNotEmpty()

    /**
     * Migrate from single-account model. Reads the old "identity" key,
     * derives the pubkey, creates an Account, and removes the old key.
     * Also ensures the "default" account exists.
     */
    fun migrateFromSingleAccount(mnemonic: String, passphrase: String = "") {
        val identity = prefs.getString("identity", null) ?: "default"
        val pubkeyHex = NostrKeyService(mnemonic, passphrase, identity).getPublicKeyHex()
        addAccount(Account(identity, pubkeyHex))
        prefs.edit().remove("identity").apply()
        ensureDefaultAccount(mnemonic, passphrase)
    }

    /** Ensures a "default" account always exists. */
    fun ensureDefaultAccount(mnemonic: String, passphrase: String = "") {
        val accounts = getAccounts()
        if (accounts.none { it.identity == "default" }) {
            val pubkeyHex = NostrKeyService(mnemonic, passphrase, "default").getPublicKeyHex()
            addAccount(Account("default", pubkeyHex))
        }
    }

    private fun saveAccounts(accounts: List<Account>) {
        prefs.edit().putString(KEY_ACCOUNTS, gson.toJson(accounts)).apply()
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
    }
}
