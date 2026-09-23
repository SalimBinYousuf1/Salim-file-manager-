package com.example.ui.screens.vault

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.data.repository.FileManagerRepository
import com.example.data.repository.SettingsRepository
import com.example.data.util.FileUtils
import com.example.ui.components.FileIconThumbnail
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateVaultScreen(
    repository: FileManagerRepository,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by settingsRepository.settings.collectAsState()

    var isUnlocked by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    var vaultFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var showNewSecretTextDialog by remember { mutableStateOf(false) }
    var secretFileName by remember { mutableStateOf("secret.txt") }
    var secretFileContent by remember { mutableStateOf("") }

    val vaultDir = remember { repository.getPrivateVaultFolder() }

    fun refreshVaultFiles() {
        val files = vaultDir.listFiles()?.map { f ->
            FileItem(
                file = f,
                name = f.name,
                path = f.absolutePath,
                size = f.length(),
                lastModified = f.lastModified(),
                readableType = FileUtils.getReadableTypeName(f)
            )
        } ?: emptyList()
        vaultFiles = files.sortedByDescending { it.lastModified }
    }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            refreshVaultFiles()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Private Vault", fontWeight = FontWeight.Bold) },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = { showNewSecretTextDialog = true }) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "New secret file")
                        }
                        IconButton(onClick = { isUnlocked = false; enteredPin = "" }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock vault")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isUnlocked) {
                // PIN Lock screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (settings.vaultPin.isEmpty()) "Setup Vault PIN" else "Vault Locked",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (settings.vaultPin.isEmpty()) "Choose a 4-digit PIN to protect your secret files." else "Enter your PIN to access your encrypted files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            if (it.length <= 6) {
                                enteredPin = it
                                pinError = false
                            }
                        },
                        label = { Text("PIN") },
                        singleLine = true,
                        isError = pinError,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.width(200.dp)
                    )

                    if (pinError) {
                        Text(
                            text = "Incorrect PIN. Try again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (settings.vaultPin.isEmpty()) {
                                if (enteredPin.length >= 4) {
                                    settingsRepository.setVaultPin(enteredPin)
                                    isUnlocked = true
                                } else {
                                    pinError = true
                                }
                            } else {
                                if (enteredPin == settings.vaultPin) {
                                    isUnlocked = true
                                } else {
                                    pinError = true
                                }
                            }
                        },
                        enabled = enteredPin.isNotBlank(),
                        modifier = Modifier.width(200.dp)
                    ) {
                        Text(if (settings.vaultPin.isEmpty()) "Set PIN & Unlock" else "Unlock")
                    }
                }
            } else {
                // Unlocked Vault Contents
                if (vaultFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Vault is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Create a secret note or transfer sensitive files here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { showNewSecretTextDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create Secret Note")
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(vaultFiles, key = { it.path }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { FileUtils.openFile(context, item.file) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FileIconThumbnail(fileItem = item, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "${item.formattedSize} • Encrypted locally",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    item.file.delete()
                                    refreshVaultFiles()
                                    Toast.makeText(context, "Deleted from vault", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (showNewSecretTextDialog) {
        AlertDialog(
            onDismissRequest = { showNewSecretTextDialog = false },
            title = { Text("Create Secret Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = secretFileName,
                        onValueChange = { secretFileName = it },
                        label = { Text("Filename (.txt)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = secretFileContent,
                        onValueChange = { secretFileContent = it },
                        label = { Text("Secret Content") },
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (secretFileName.isNotBlank()) {
                        val file = File(vaultDir, if (secretFileName.endsWith(".txt")) secretFileName else "$secretFileName.txt")
                        file.writeText(secretFileContent)
                        showNewSecretTextDialog = false
                        secretFileContent = ""
                        refreshVaultFiles()
                        Toast.makeText(context, "Saved to private vault", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Save in Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSecretTextDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
