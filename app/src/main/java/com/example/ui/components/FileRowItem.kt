package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.FileItem
import com.example.data.util.salimRawTouchItem
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FileRowItem(
    fileItem: FileItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress1150ms: () -> Unit,
    onLongPressDragSelect: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    SwipeableActionRow(
        onRename = onRename,
        onMove = onMove,
        onDelete = onDelete,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surface
                )
                .salimRawTouchItem(
                    onTap = onTap,
                    onLongPress1150ms = onLongPress1150ms,
                    onLongPressDragSelect = onLongPressDragSelect
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("file_row_${fileItem.name}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onTap() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            FileIconThumbnail(
                fileItem = fileItem,
                modifier = Modifier.size(44.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                val subText = if (fileItem.isDirectory) {
                    "${fileItem.itemCount} items • ${dateFormat.format(Date(fileItem.lastModified))}"
                } else {
                    "${fileItem.formattedSize} • ${dateFormat.format(Date(fileItem.lastModified))}"
                }
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FileGridItem(
    fileItem: FileItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress1150ms: () -> Unit,
    onLongPressDragSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .salimRawTouchItem(
                onTap = onTap,
                onLongPress1150ms = onLongPress1150ms,
                onLongPressDragSelect = onLongPressDragSelect
            )
            .testTag("file_grid_${fileItem.name}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f),
                contentAlignment = Alignment.Center
            ) {
                FileIconThumbnail(
                    fileItem = fileItem,
                    modifier = Modifier.fillMaxSize()
                )

                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onTap() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (fileItem.isDirectory) "${fileItem.itemCount} items" else fileItem.formattedSize,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FileIconThumbnail(
    fileItem: FileItem,
    modifier: Modifier = Modifier
) {
    val isImage = fileItem.extension in listOf("jpg", "jpeg", "png", "webp", "gif")

    if (isImage && fileItem.file.exists()) {
        AsyncImage(
            model = fileItem.file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        val (icon, tint) = getIconAndTintForFile(fileItem)
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

fun getIconAndTintForFile(fileItem: FileItem): Pair<ImageVector, Color> {
    if (fileItem.isDirectory) {
        return Pair(Icons.Default.Folder, ColorFolder)
    }
    return when (fileItem.extension) {
        "jpg", "jpeg", "png", "webp", "gif", "svg" -> Pair(Icons.Default.Image, ColorImage)
        "mp4", "mkv", "mov", "avi", "webm" -> Pair(Icons.Default.Movie, ColorVideo)
        "mp3", "wav", "flac", "m4a", "aac", "ogg" -> Pair(Icons.Default.Audiotrack, ColorAudio)
        "pdf", "doc", "docx", "txt" -> Pair(Icons.Default.Article, ColorDocument)
        "xls", "xlsx", "csv" -> Pair(Icons.Default.TableChart, ColorDocument)
        "ppt", "pptx" -> Pair(Icons.Default.Slideshow, ColorDocument)
        "zip", "rar", "7z", "tar", "gz" -> Pair(Icons.Default.FolderZip, ColorArchive)
        "apk" -> Pair(Icons.Default.Android, ColorApk)
        "kt", "java", "xml", "json", "html", "css", "js", "py" -> Pair(Icons.Default.Code, ColorCode)
        else -> Pair(Icons.Default.InsertDriveFile, ColorOther)
    }
}
