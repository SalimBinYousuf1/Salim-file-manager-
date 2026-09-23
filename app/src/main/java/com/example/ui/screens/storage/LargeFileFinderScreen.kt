package com.example.ui.screens.storage

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.data.model.LargeFileEntry
import com.example.data.repository.FileManagerRepository
import com.example.data.util.FileUtils
import com.example.ui.components.FileIconThumbnail
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFileFinderScreen(
    repository: FileManagerRepository,
    onNavigateBack: () -> Unit,
    onRevealInFolder: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Threshold options in MB
    val thresholds = listOf(50L, 100L, 250L, 500L)
    var selectedThresholdMb by remember { mutableStateOf(100L) }

    var isScanning by remember { mutableStateOf(true) }
    var largeFiles by remember { mutableStateOf<List<LargeFileEntry>>(emptyList()) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    fun scanFiles() {
        coroutineScope.launch {
            isScanning = true
            largeFiles = repository.findLargeFiles(selectedThresholdMb * 1024L * 1024L)
            isScanning = false
        }
    }

    LaunchedEffect(selectedThresholdMb) {
        scanFiles()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Large File Finder", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Threshold selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Threshold:", style = MaterialTheme.typography.labelMedium)
                thresholds.forEach { mb ->
                    FilterChip(
                        selected = selectedThresholdMb == mb,
                        onClick = { selectedThresholdMb = mb },
                        label = { Text(">$mb MB") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            HorizontalDivider()

            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (largeFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No files found larger than $selectedThresholdMb MB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(largeFiles, key = { it.fileItem.path }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { FileUtils.openFile(context, entry.fileItem.file) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileIconThumbnail(
                                fileItem = entry.fileItem,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.fileItem.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${FileItem.formatFileSize(entry.size)} • ${entry.fileItem.path}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = {
                                val parent = entry.fileItem.file.parentFile
                                if (parent != null) onRevealInFolder(parent)
                            }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Reveal in folder")
                            }

                            IconButton(
                                onClick = { fileToDelete = entry.fileItem.file },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete to Trash")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Move to Trash?") },
            text = { Text("Move '${fileToDelete?.name}' to Trash?") },
            confirmButton = {
                Button(
                    onClick = {
                        val f = fileToDelete
                        fileToDelete = null
                        if (f != null) {
                            coroutineScope.launch {
                                repository.moveToTrash(f)
                                scanFiles()
                                Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
