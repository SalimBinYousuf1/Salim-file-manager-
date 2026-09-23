package com.example.data.model

import java.io.File

data class FileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified(),
    val isHidden: Boolean = file.isHidden || file.name.startsWith("."),
    val extension: String = if (file.isFile) file.extension.lowercase() else "",
    val mimeType: String = "",
    val readableType: String = "",
    val isSelected: Boolean = false,
    val itemCount: Int = 0
) {
    val formattedSize: String
        get() = formatFileSize(size)

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
    }
}

enum class SortField {
    NAME,
    DATE_MODIFIED,
    DATE_CREATED,
    SIZE,
    TYPE
}

data class SortPreference(
    val field: SortField = SortField.NAME,
    val isAscending: Boolean = true,
    val foldersFirst: Boolean = true
)

enum class ViewMode {
    LIST,
    GRID
}

enum class SearchScope {
    THIS_FOLDER,
    THIS_VOLUME,
    EVERYWHERE
}
