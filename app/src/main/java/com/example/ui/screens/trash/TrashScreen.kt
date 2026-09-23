package com.example.ui.screens.trash

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TrashEntity
import com.example.data.model.FileItem
import com.example.data.repository.FileManagerRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.components.SalimTrashTopBar
import com.example.ui.theme.ColorFolder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun TrashScreen(
    repository: FileManagerRepository,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val trashItems by repository.trashItems.collectAsState(initial = emptyList())
    val settings by settingsRepository.settings.collectAsState()

    var showEmptyTrashConfirmDialog by remember { mutableStateOf(false) }
    var itemForPermanentDelete by remember { mutableStateOf<TrashEntity?>(null) }
    var selectedItemForDetail by remember { mutableStateOf<TrashEntity?>(null) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SalimTrashTopBar(
                hasItems = trashItems.isNotEmpty(),
                onEmptyTrash = { showEmptyTrashConfirmDialog = true }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (trashItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Trash is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Files deleted in Salim will be stored here safely before permanent removal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Summary Banner
                    val totalTrashBytes = remember(trashItems) { trashItems.sumOf { it.size } }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${trashItems.size} items (${FileItem.formatFileSize(totalTrashBytes)})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            val retentionText = if (settings.trashRetentionDays > 0) "${settings.trashRetentionDays}-day retention" else "Manual purge"
                            Text(
                                text = retentionText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(trashItems, key = { it.id }) { item ->
                            val elapsedDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - item.deletedTimestamp)
                            val daysLeft = if (settings.trashRetentionDays > 0) (settings.trashRetentionDays - elapsedDays).coerceAtLeast(0) else null

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedItemForDetail = item }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (item.isDirectory) ColorFolder else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )

                                Spacer(Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Original: ${item.originalPath}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val statusText = buildString {
                                        append(FileItem.formatFileSize(item.size))
                                        append(" • Deleted ")
                                        append(dateFormat.format(Date(item.deletedTimestamp)))
                                        if (daysLeft != null) {
                                            append(" ($daysLeft days left)")
                                        }
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }

                                // Quick Restore Button
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val result = repository.restoreFromTrash(item)
                                            if (result.isSuccess) {
                                                Toast.makeText(context, "Restored '${item.name}'", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Restore failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("restore_trash_${item.id}")
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = "Restore")
                                }

                                // Delete Permanently Button
                                IconButton(
                                    onClick = { itemForPermanentDelete = item },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.testTag("delete_permanent_trash_${item.id}")
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete permanently")
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    // Empty Trash confirmation dialog
    if (showEmptyTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirmDialog = false },
            title = { Text("Empty Trash?") },
            text = { Text("All items in Trash will be permanently deleted. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyTrashConfirmDialog = false
                        coroutineScope.launch {
                            val res = repository.emptyTrash()
                            if (res.isSuccess) {
                                Toast.makeText(context, "Trash emptied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Permanent delete confirmation dialog for single item
    if (itemForPermanentDelete != null) {
        AlertDialog(
            onDismissRequest = { itemForPermanentDelete = null },
            title = { Text("Permanently delete?") },
            text = { Text("Permanently delete '${itemForPermanentDelete?.name}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val item = itemForPermanentDelete
                        itemForPermanentDelete = null
                        if (item != null) {
                            coroutineScope.launch {
                                repository.permanentlyDeleteTrash(item)
                                Toast.makeText(context, "Permanently deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemForPermanentDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
