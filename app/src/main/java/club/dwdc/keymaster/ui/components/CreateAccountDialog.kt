package club.dwdc.keymaster.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateAccountDialog(
    onDismiss: () -> Unit,
    onCreate: (identity: String) -> Unit
) {
    var identity by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Account") },
        text = {
            Column {
                Text(
                    text = "Enter an identity string for key derivation (e.g. alice@example.com or #2).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = identity,
                    onValueChange = {
                        identity = it
                        error = null
                    },
                    label = { Text("Identity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = identity.trim()
                    if (trimmed.isEmpty()) {
                        error = "Identity is required"
                    } else {
                        onCreate(trimmed)
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
