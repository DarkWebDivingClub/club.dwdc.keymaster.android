package club.dwdc.keymaster.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import club.dwdc.keymaster.data.AccountRepository
import club.dwdc.keymaster.data.PermissionRepository
import club.dwdc.keymaster.data.SeedRepository
import club.dwdc.keymaster.nip46.Nip46Service
import club.dwdc.keymaster.nip46.NostrConnectUrl
import club.dwdc.keymaster.ui.components.Nip46ConfirmDialog
import club.dwdc.keymaster.ui.screens.HomeScreen
import club.dwdc.keymaster.ui.screens.Nip46ScanScreen
import club.dwdc.keymaster.ui.screens.SetupScreen
import club.dwdc.keymaster.ui.theme.KeyMasterTheme
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Migrate from single-account to multi-account if needed
        val seedRepo = SeedRepository(this)
        val accountRepo = AccountRepository(this)
        if (seedRepo.hasSeed()) {
            val mnemonic = seedRepo.getMnemonic()!!
            val passphrase = seedRepo.getPassphrase()
            if (!accountRepo.hasAccounts()) {
                accountRepo.migrateFromSingleAccount(mnemonic, passphrase)
                PermissionRepository(this).migratePermissions(accountRepo.getAccounts().first().pubkeyHex)
            }
            // Always ensure the "default" account exists
            accountRepo.ensureDefaultAccount(mnemonic, passphrase)
        }

        setContent {
            KeyMasterTheme {
                val navController = rememberNavController()
                val startDest = if (seedRepo.hasSeed()) "home" else "setup"

                // State for the NIP-46 confirmation dialog
                var pendingConnectUrl by remember { mutableStateOf<NostrConnectUrl?>(null) }
                var pendingConnectRawUrl by remember { mutableStateOf("") }
                var pendingConnectIdentity by remember { mutableStateOf("") }

                NavHost(navController, startDestination = startDest) {
                    composable("setup") {
                        SetupScreen(
                            onSetupComplete = {
                                navController.navigate("home") {
                                    popUpTo("setup") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("home") {
                        HomeScreen(
                            onNavigateToNip46Scan = { identity ->
                                navController.navigate("nip46scan/$identity")
                            }
                        )
                    }
                    composable(
                        "nip46scan/{identity}",
                        arguments = listOf(navArgument("identity") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val identity = backStackEntry.arguments?.getString("identity") ?: "default"
                        Nip46ScanScreen(
                            onUrlScanned = { connectUrl, rawUrl ->
                                pendingConnectUrl = connectUrl
                                pendingConnectRawUrl = rawUrl
                                pendingConnectIdentity = identity
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // NIP-46 confirmation dialog (shown on top of any screen)
                pendingConnectUrl?.let { connectUrl ->
                    Nip46ConfirmDialog(
                        connectUrl = connectUrl,
                        onConfirm = {
                            Nip46Service.startConnect(
                                this@MainActivity,
                                pendingConnectRawUrl,
                                pendingConnectIdentity
                            )
                            pendingConnectUrl = null
                            // Navigate back to home
                            navController.popBackStack("home", inclusive = false)
                        },
                        onDismiss = {
                            pendingConnectUrl = null
                        }
                    )
                }
            }
        }
    }
}
