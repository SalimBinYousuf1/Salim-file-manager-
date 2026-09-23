package com.example.ui.screens.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.data.model.StorageCategoryInfo
import com.example.data.model.StorageCategoryType
import com.example.data.model.StorageVolumeInfo
import com.example.data.repository.FileManagerRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    repository: FileManagerRepository,
    onOpenCategory: (StorageCategoryType, String) -> Unit,
    onOpenDuplicateFinder: () -> Unit,
    onOpenLargeFileFinder: () -> Unit,
    onOpenEmptyFolderFinder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var volumeInfo by remember { mutableStateOf<StorageVolumeInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun loadStorageStats() {
        coroutineScope.launch {
            isLoading = true
            volumeInfo = repository.getStorageVolumeInfo()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadStorageStats()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Storage Analyzer",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = { loadStorageStats() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh stats")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (isLoading || volumeInfo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Analyzing storage volume…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val vInfo = volumeInfo!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Main Storage Card
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("storage_breakdown_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = vInfo.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${FileItem.formatFileSize(vInfo.usedSpace)} used of ${FileItem.formatFileSize(vInfo.totalSpace)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${(vInfo.usedPercentage * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Segmented Proportional Bar
                        ProportionalStorageBar(
                            categories = vInfo.categories,
                            totalBytes = vInfo.totalSpace
                        )

                        Spacer(Modifier.height(20.dp))

                        // Category Legend with Tap-to-filter
                        Text(
                            text = "Categories (tap to view files)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(8.dp))

                        vInfo.categories.forEach { cat ->
                            CategoryLegendItem(
                                category = cat,
                                onClick = {
                                    if (cat.category != StorageCategoryType.SYSTEM) {
                                        onOpenCategory(cat.category, cat.name)
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Storage Tools Section (Part G)
                Text(
                    text = "Storage Cleanup Tools",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(Modifier.height(12.dp))

                StorageToolCard(
                    title = "Duplicate Finder",
                    description = "Find identical files using content hash comparison",
                    icon = Icons.Default.ContentCopy,
                    iconBgColor = ColorImage,
                    onClick = onOpenDuplicateFinder,
                    testTag = "open_duplicate_finder_button"
                )

                Spacer(Modifier.height(12.dp))

                StorageToolCard(
                    title = "Large File Finder",
                    description = "Inspect and delete space-consuming files (>100MB)",
                    icon = Icons.Default.VerticalAlignTop,
                    iconBgColor = ColorVideo,
                    onClick = onOpenLargeFileFinder,
                    testTag = "open_large_file_finder_button"
                )

                Spacer(Modifier.height(12.dp))

                StorageToolCard(
                    title = "Empty Folder Finder",
                    description = "Detect and batch remove directories containing zero files",
                    icon = Icons.Default.FolderOff,
                    iconBgColor = ColorFolder,
                    onClick = onOpenEmptyFolderFinder,
                    testTag = "open_empty_folder_finder_button"
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ProportionalStorageBar(
    categories: List<StorageCategoryInfo>,
    totalBytes: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
    ) {
        categories.forEach { cat ->
            if (cat.totalBytes > 0 && totalBytes > 0) {
                val weight = (cat.totalBytes.toFloat() / totalBytes.toFloat()).coerceAtLeast(0.005f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(weight)
                        .background(cat.color)
                )
            }
        }
    }
}

@Composable
fun CategoryLegendItem(
    category: StorageCategoryInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(category.color)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            if (category.category != StorageCategoryType.SYSTEM) {
                Text(
                    text = "${category.fileCount} files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = category.formattedBytes,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        if (category.category != StorageCategoryType.SYSTEM) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun StorageToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBgColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
