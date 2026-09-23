package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.data.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalimCrudBottomSheet(
    fileItem: FileItem?,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onOpen: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onCopy: (FileItem) -> Unit,
    onMove: (FileItem) -> Unit,
    onCompress: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit,
    onToggleBookmark: (FileItem) -> Unit,
    onDetails: (FileItem) -> Unit,
    onExtract: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit
) {
    if (fileItem == null) return

    val isArchive = FileUtils.isArchive(fileItem.file)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("crud_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Name and Type
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${fileItem.readableType} • ${fileItem.formattedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 1. Open
            CrudMenuItem(
                icon = Icons.Default.OpenInNew,
                title = if (fileItem.isDirectory) "Open Folder" else "Open With...",
                onClick = { onDismiss(); onOpen(fileItem) }
            )

            // 2. Rename
            CrudMenuItem(
                icon = Icons.Default.DriveFileRenameOutline,
                title = "Rename",
                onClick = { onDismiss(); onRename(fileItem) }
            )

            // 3. Copy
            CrudMenuItem(
                icon = Icons.Default.ContentCopy,
                title = "Copy",
                onClick = { onDismiss(); onCopy(fileItem) }
            )

            // 4. Move
            CrudMenuItem(
                icon = Icons.Default.DriveFileMove,
                title = "Move",
                onClick = { onDismiss(); onMove(fileItem) }
            )

            // 5. Compress
            CrudMenuItem(
                icon = Icons.Default.FolderZip,
                title = "Compress to .zip",
                onClick = { onDismiss(); onCompress(fileItem) }
            )

            // 6. Share
            if (!fileItem.isDirectory) {
                CrudMenuItem(
                    icon = Icons.Default.Share,
                    title = "Share",
                    onClick = { onDismiss(); onShare(fileItem) }
                )
            }

            // 7. Add to Bookmarks / Favorites
            CrudMenuItem(
                icon = if (isBookmarked) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd,
                title = if (fileItem.isDirectory) {
                    if (isBookmarked) "Remove from Bookmarks" else "Add to Bookmarks"
                } else {
                    if (isBookmarked) "Remove from Favorites" else "Add to Favorites"
                },
                onClick = { onDismiss(); onToggleBookmark(fileItem) }
            )

            // 8. Details
            CrudMenuItem(
                icon = Icons.Default.Info,
                title = "Details",
                onClick = { onDismiss(); onDetails(fileItem) }
            )

            // 9. Extract (archives only)
            if (isArchive) {
                CrudMenuItem(
                    icon = Icons.Default.Unarchive,
                    title = "Extract Here",
                    onClick = { onDismiss(); onExtract(fileItem) }
                )
            }

            // Separator with 8dp gap
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(8.dp))

            // 11. Delete (destructive red)
            CrudMenuItem(
                icon = Icons.Default.Delete,
                title = "Move to Trash",
                isDestructive = true,
                onClick = { onDismiss(); onDelete(fileItem) }
            )
        }
    }
}

@Composable
private fun CrudMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (isDestructive) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = color
        )
    }
}
