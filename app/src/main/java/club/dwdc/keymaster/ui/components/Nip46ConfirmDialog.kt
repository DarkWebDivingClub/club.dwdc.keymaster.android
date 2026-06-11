package club.dwdc.keymaster.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import club.dwdc.keymaster.nip46.NostrConnectUrl

/**
 * Confirmation dialog shown after scanning/pasting a nostrconnect:// URL.
 * Displays client name, relay URLs, and requested permissions.
 */
@Composable
fun Nip46ConfirmDialog(
    connectUrl: NostrConnectUrl,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayName = connectUrl.name
        ?: (connectUrl.clientPubkey.take(8) + "..." + connectUrl.clientPubkey.takeLast(8))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Remote Client?") },
        text = {
            Column {
                Text(
                    text = "Client",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (connectUrl.relays.size == 1) "Relay" else "Relays",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                for (relay in connectUrl.relays) {
                    Text(
                        text = relay,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (!connectUrl.perms.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Permissions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = connectUrl.perms.replace(",", ", "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
