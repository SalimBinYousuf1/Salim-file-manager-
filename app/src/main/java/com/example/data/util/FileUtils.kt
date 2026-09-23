package com.example.data.util

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {

    fun getMimeType(file: File): String {
        if (file.isDirectory) return "inode/directory"
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "json" -> "application/json"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "log" -> "text/plain"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/x-rar-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            else -> "application/octet-stream"
        }
    }

    fun getReadableTypeName(file: File): String {
        if (file.isDirectory) return "Folder"
        val ext = file.extension.lowercase()
        return when (ext) {
            "pdf" -> "PDF Document"
            "jpg", "jpeg" -> "JPEG Image"
            "png" -> "PNG Image"
            "webp" -> "WebP Image"
            "gif" -> "GIF Animation"
            "svg" -> "Vector Graphic"
            "mp4", "mkv", "mov", "avi", "webm" -> "Video File (${ext.uppercase()})"
            "mp3", "wav", "flac", "m4a", "aac", "ogg" -> "Audio File (${ext.uppercase()})"
            "zip" -> "ZIP Archive"
            "rar" -> "RAR Archive"
            "7z" -> "7-Zip Archive"
            "tar", "gz" -> "Compressed Archive"
            "txt" -> "Plain Text Document"
            "doc", "docx" -> "Word Document"
            "xls", "xlsx" -> "Excel Spreadsheet"
            "ppt", "pptx" -> "PowerPoint Presentation"
            "apk" -> "Android Package (APK)"
            "kt", "java", "xml", "json", "html", "css", "js", "py", "c", "cpp" -> "Source Code (${ext.uppercase()})"
            else -> if (ext.isNotEmpty()) "${ext.uppercase()} File" else "Binary File"
        }
    }

    fun isArchive(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in listOf("zip", "rar", "7z", "tar", "gz")
    }

    suspend fun calculateFolderStats(folder: File): Pair<Int, Long> = withContext(Dispatchers.IO) {
        var count = 0
        var totalBytes = 0L

        fun scan(f: File) {
            val list = f.listFiles() ?: return
            for (child in list) {
                count++
                if (child.isDirectory) {
                    scan(child)
                } else {
                    totalBytes += child.length()
                }
            }
        }

        scan(folder)
        Pair(count, totalBytes)
    }

    fun openFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val mimeType = getMimeType(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun copyWithProgress(
        source: File,
        destDir: File,
        onProgress: (filesDone: Int, totalFiles: Int, bytesDone: Long, totalBytes: Long, currentFile: String) -> Unit,
        isCancelled: () -> Boolean
    ): File = withContext(Dispatchers.IO) {
        val (totalFiles, totalBytes) = if (source.isDirectory) calculateFolderStats(source) else Pair(1, source.length())
        var filesDone = 0
        var bytesDone = 0L

        var destination = File(destDir, source.name)
        if (destination.exists()) {
            destination = getConflictResolvedFile(destDir, source.name)
        }

        fun copyRecursive(src: File, dest: File) {
            if (isCancelled()) return
            if (src.isDirectory) {
                dest.mkdirs()
                val list = src.listFiles() ?: return
                for (child in list) {
                    if (isCancelled()) return
                    copyRecursive(child, File(dest, child.name))
                }
            } else {
                onProgress(filesDone, totalFiles, bytesDone, totalBytes, src.name)
                src.inputStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (isCancelled()) return
                            output.write(buffer, 0, read)
                            bytesDone += read
                            onProgress(filesDone, totalFiles, bytesDone, totalBytes, src.name)
                        }
                    }
                }
                filesDone++
                onProgress(filesDone, totalFiles, bytesDone, totalBytes, src.name)
            }
        }

        copyRecursive(source, destination)
        destination
    }

    fun getConflictResolvedFile(parent: File, originalName: String): File {
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""

        var index = 1
        var candidate: File
        do {
            candidate = File(parent, "$baseName ($index)$extension")
            index++
        } while (candidate.exists())
        return candidate
    }

    suspend fun compressToZip(
        source: File,
        onProgress: (filesDone: Int, totalFiles: Int, bytesDone: Long, totalBytes: Long, currentFile: String) -> Unit,
        isCancelled: () -> Boolean
    ): File = withContext(Dispatchers.IO) {
        val baseName = if (source.isDirectory) source.name else source.nameWithoutExtension
        val parent = source.parentFile ?: source
        var zipFile = File(parent, "$baseName.zip")
        if (zipFile.exists()) {
            zipFile = getConflictResolvedFile(parent, "$baseName.zip")
        }

        val (totalFiles, totalBytes) = if (source.isDirectory) calculateFolderStats(source) else Pair(1, source.length())
        var filesDone = 0
        var bytesDone = 0L

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            fun zipEntry(f: File, relativePath: String) {
                if (isCancelled()) return
                if (f.isDirectory) {
                    val list = f.listFiles() ?: return
                    for (child in list) {
                        if (isCancelled()) return
                        zipEntry(child, "$relativePath${child.name}${if (child.isDirectory) "/" else ""}")
                    }
                } else {
                    onProgress(filesDone, totalFiles, bytesDone, totalBytes, f.name)
                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    f.inputStream().use { fis ->
                        val buffer = ByteArray(64 * 1024)
                        var len: Int
                        while (fis.read(buffer).also { len = it } != -1) {
                            if (isCancelled()) return
                            zos.write(buffer, 0, len)
                            bytesDone += len
                            onProgress(filesDone, totalFiles, bytesDone, totalBytes, f.name)
                        }
                    }
                    zos.closeEntry()
                    filesDone++
                    onProgress(filesDone, totalFiles, bytesDone, totalBytes, f.name)
                }
            }

            if (source.isDirectory) {
                val list = source.listFiles() ?: emptyArray()
                for (child in list) {
                    zipEntry(child, "${child.name}${if (child.isDirectory) "/" else ""}")
                }
            } else {
                zipEntry(source, source.name)
            }
        }

        zipFile
    }

    suspend fun extractZip(
        zipFile: File,
        onProgress: (filesDone: Int, totalFiles: Int, bytesDone: Long, totalBytes: Long, currentFile: String) -> Unit,
        isCancelled: () -> Boolean
    ): File = withContext(Dispatchers.IO) {
        val parent = zipFile.parentFile ?: zipFile
        val destFolder = getConflictResolvedFile(parent, zipFile.nameWithoutExtension).apply { mkdirs() }

        var totalEntries = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            while (zis.nextEntry != null) {
                totalEntries++
            }
        }

        var filesDone = 0
        var bytesDone = 0L

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (isCancelled()) break
                val outFile = File(destFolder, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    onProgress(filesDone, totalEntries, bytesDone, zipFile.length(), entry.name)
                    FileOutputStream(outFile).use { fos ->
                        val buffer = ByteArray(64 * 1024)
                        var len: Int
                        while (zis.read(buffer).also { len = it } != -1) {
                            if (isCancelled()) break
                            fos.write(buffer, 0, len)
                            bytesDone += len
                        }
                    }
                    filesDone++
                    onProgress(filesDone, totalEntries, bytesDone, zipFile.length(), entry.name)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        destFolder
    }

    fun getVideoAudioMetadata(file: File): Map<String, String> {
        val data = mutableMapOf<String, String>()
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durationMs != null) {
                val sec = (durationMs / 1000) % 60
                val min = (durationMs / (1000 * 60)) % 60
                val hr = durationMs / (1000 * 60 * 60)
                data["Duration"] = if (hr > 0) String.format("%02d:%02d:%02d", hr, min, sec) else String.format("%02d:%02d", min, sec)
            }
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (width != null && height != null) {
                data["Resolution"] = "${width} × ${height}"
            }
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            if (bitrate != null) {
                data["Bitrate"] = "${bitrate / 1000} kbps"
            }
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (mime != null) {
                data["Codec / Format"] = mime
            }
            retriever.release()
        } catch (e: Exception) {
            // ignore if unsupported format
        }
        return data
    }
}
