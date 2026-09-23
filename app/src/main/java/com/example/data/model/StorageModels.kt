package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

data class StorageCategoryInfo(
    val category: StorageCategoryType,
    val name: String,
    val totalBytes: Long,
    val fileCount: Int,
    val color: Color
) {
    val formattedBytes: String
        get() = FileItem.formatFileSize(totalBytes)
}

enum class StorageCategoryType {
    IMAGES,
    VIDEOS,
    AUDIO,
    DOCUMENTS,
    APPS,
    OTHER,
    SYSTEM
}

data class StorageVolumeInfo(
    val name: String,
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val categories: List<StorageCategoryInfo> = emptyList()
) {
    val usedPercentage: Float
        get() = if (totalSpace > 0) (usedSpace.toFloat() / totalSpace.toFloat()) else 0f
}

data class DuplicateGroup(
    val contentHash: String,
    val size: Long,
    val files: List<FileItem>
)

data class LargeFileEntry(
    val fileItem: FileItem,
    val size: Long
)

data class EmptyFolderEntry(
    val fileItem: FileItem,
    val path: String
)

data class AppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val apkSize: Long,
    val dataSize: Long = 0L,
    val cacheSize: Long = 0L,
    val installDate: Long,
    val lastUsedDate: Long,
    val isSystemApp: Boolean
) {
    val totalSize: Long
        get() = apkSize + dataSize + cacheSize

    val formattedTotalSize: String
        get() = FileItem.formatFileSize(totalSize)
}

data class FileOperationProgress(
    val operationId: String = "",
    val operationName: String = "", // "Copying...", "Moving...", "Compressing...", "Extracting..."
    val isRunning: Boolean = false,
    val currentFileName: String = "",
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val canCancel: Boolean = true
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (bytesProcessed.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                else if (totalFiles > 0) (filesProcessed.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
                else 0f

    val formattedStatus: String
        get() = "$filesProcessed of $totalFiles files — ${FileItem.formatFileSize(bytesProcessed)} of ${FileItem.formatFileSize(totalBytes)}"
}
