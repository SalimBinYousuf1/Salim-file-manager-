package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.data.util.ExifUtils
import com.example.data.util.FileUtils
import com.example.data.util.ImageExifData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalimDetailsSheet(
    fileItem: FileItem?,
    onDismiss: () -> Unit,
    onRenameInline: (String) -> Unit
) {
    if (fileItem == null) return

    val file = fileItem.file
    val coroutineScope = rememberCoroutineScope()

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(fileItem.name) }

    var folderCount by remember { mutableStateOf<Int?>(null) }
    var folderTotalSize by remember { mutableStateOf<Long?>(null) }
    var isCalculatingFolder by remember { mutableStateOf(fileItem.isDirectory) }

    var exifData by remember { mutableStateOf<ImageExifData?>(null) }
    var mediaMeta by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var locationRemoved by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(fileItem) {
        if (fileItem.isDirectory) {
            isCalculatingFolder = true
            val (count, size) = FileUtils.calculateFolderStats(file)
            folderCount = count
            folderTotalSize = size
            isCalculatingFolder = false
        } else {
            val ext = file.extension.lowercase()
            if (ext in listOf("jpg", "jpeg", "png", "webp")) {
                withContext(Dispatchers.IO) {
                    exifData = ExifUtils.readExifData(file)
                }
            } else if (ext in listOf("mp4", "mkv", "mov", "mp3", "wav", "flac", "m4a")) {
                withContext(Dispatchers.IO) {
                    mediaMeta = FileUtils.getVideoAudioMetadata(file)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("details_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Inline Editable Name
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Name",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    if (isEditingName) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = { editedName = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(onClick = {
                                if (editedName.isNotBlank() && editedName != fileItem.name) {
                                    onRenameInline(editedName)
                                }
                                isEditingName = false
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Save name", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                editedName = fileItem.name
                                isEditingName = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel edit")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = fileItem.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { isEditingName = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit name", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Type & Size
            DetailRow(icon = Icons.Default.Category, label = "Type", value = fileItem.readableType)
            DetailRow(icon = Icons.Default.Description, label = "MIME Type", value = fileItem.mimeType.ifEmpty { "None" })

            val sizeString = if (fileItem.isDirectory) {
                if (isCalculatingFolder) "Calculating…" else "${FileItem.formatFileSize(folderTotalSize ?: 0L)} (${folderTotalSize ?: 0L} bytes)"
            } else {
                "${fileItem.formattedSize} (${fileItem.size} bytes)"
            }
            DetailRow(icon = Icons.Default.Storage, label = "Size", value = sizeString)

            if (fileItem.isDirectory) {
                val countString = if (isCalculatingFolder) "Calculating…" else "${folderCount ?: 0} items"
                DetailRow(icon = Icons.Default.FolderOpen, label = "Contains", value = countString)
            }

            // Location
            DetailRow(icon = Icons.Default.Place, label = "Location", value = fileItem.path)

            // Timestamps
            DetailRow(icon = Icons.Default.AccessTime, label = "Last Modified", value = dateFormat.format(Date(fileItem.lastModified)))

            // Permissions
            val permissions = buildString {
                append(if (file.canRead()) "r" else "-")
                append(if (file.canWrite()) "w" else "-")
                append(if (file.canExecute()) "x" else "-")
            }
            DetailRow(icon = Icons.Default.Security, label = "Permissions", value = permissions)

            // Media & EXIF Details
            exifData?.let { exif ->
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Image Metadata",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                exif.dimensions?.let { DetailRow(icon = Icons.Default.AspectRatio, label = "Dimensions", value = it) }
                exif.cameraModel?.let { DetailRow(icon = Icons.Default.CameraAlt, label = "Camera", value = it) }
                exif.dateTaken?.let { DetailRow(icon = Icons.Default.CalendarToday, label = "Date Taken", value = it) }

                if (exif.hasGps && !locationRemoved) {
                    Spacer(Modifier.height(4.dp))
                    DetailRow(icon = Icons.Default.GpsFixed, label = "GPS Coordinates", value = exif.gpsCoordinates ?: "")
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    val success = ExifUtils.removeLocationData(file)
                                    if (success) {
                                        locationRemoved = true
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.LocationOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remove Location Data (Privacy)")
                    }
                } else if (locationRemoved) {
                    Text(
                        text = "✓ Location metadata stripped successfully",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (mediaMeta.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Media Info",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                mediaMeta.forEach { (k, v) ->
                    DetailRow(icon = Icons.Default.PlayCircle, label = k, value = v)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
