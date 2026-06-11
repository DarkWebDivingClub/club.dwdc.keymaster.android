package org.dwdc.keymaster

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class KeyMasterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Register BouncyCastle as the primary JCE provider.
        // Required because nostr-java's NIP-44 EncryptedPayloads uses
        // Cipher.getInstance("ChaCha20") with IvParameterSpec, which
        // only BouncyCastle's provider accepts on Android.
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
