package com.example.ui.screens.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.data.model.StorageCategoryType
import com.example.data.repository.FileManagerRepository
import com.example.data.util.FileUtils
import com.example.ui.components.FileIconThumbnail
import com.example.ui.components.SalimCrudBottomSheet
import com.example.ui.components.SalimDetailsSheet
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilesScreen(
    categoryType: StorageCategoryType,
    categoryTitle: String,
    repository: FileManagerRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var activeCrudItem by remember { mutableStateOf<FileItem?>(null) }
    var activeDetailsItem by remember { mutableStateOf<FileItem?>(null) }

    fun loadFiles() {
        coroutineScope.launch {
            isLoading = true
            files = repository.getFilesForCategory(categoryType)
            isLoading = false
        }
    }

    LaunchedEffect(categoryType) {
        loadFiles()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(categoryTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No $categoryTitle found on this volume",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files, key = { it.path }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { FileUtils.openFile(context, item.file) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileIconThumbnail(fileItem = item, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.formattedSize} • ${item.path}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (activeCrudItem != null) {
        SalimCrudBottomSheet(
            fileItem = activeCrudItem,
            isBookmarked = false,
            onDismiss = { activeCrudItem = null },
            onOpen = { FileUtils.openFile(context, it.file) },
            onRename = { },
            onCopy = { },
            onMove = { },
            onCompress = { },
            onShare = { FileUtils.shareFile(context, it.file) },
            onToggleBookmark = { },
            onDetails = { activeDetailsItem = it },
            onExtract = { },
            onDelete = {
                coroutineScope.launch {
                    repository.moveToTrash(it.file)
                    loadFiles()
                }
            }
        )
    }

    if (activeDetailsItem != null) {
        SalimDetailsSheet(
            fileItem = activeDetailsItem,
            onDismiss = { activeDetailsItem = null },
            onRenameInline = { }
        )
    }
}
