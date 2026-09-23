package com.example.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.util.FileUtils
import com.example.data.util.HashUtils
import com.example.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class FileManagerRepository(
    private val context: Context,
    private val database: SalimDatabase,
    private val settingsRepository: SettingsRepository
) {
    private val bookmarkDao = database.bookmarkDao()
    private val trashDao = database.trashDao()
    private val folderSortDao = database.folderSortDao()
    private val searchHistoryDao = database.searchHistoryDao()
    private val networkConnectionDao = database.networkConnectionDao()
    private val excludedFolderDao = database.excludedFolderDao()

    private val trashDir: File by lazy {
        File(context.filesDir, "salim_trash").apply { mkdirs() }
    }

    private val privateVaultDir: File by lazy {
        File(context.filesDir, "salim_vault").apply { mkdirs() }
    }

    private val _currentOperation = MutableStateFlow(FileOperationProgress())
    val currentOperation: StateFlow<FileOperationProgress> = _currentOperation.asStateFlow()

    private var activeOperationJob: Job? = null
    private var isOperationCancelled = false

    val bookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()
    val trashItems: Flow<List<TrashEntity>> = trashDao.getAllTrash()
    val recentSearches: Flow<List<SearchHistoryEntity>> = searchHistoryDao.getRecentSearches()
    val networkConnections: Flow<List<NetworkConnectionEntity>> = networkConnectionDao.getAllConnections()
    val excludedFolders: Flow<List<ExcludedFolderEntity>> = excludedFolderDao.getAllExcludedFolders()

    init {
        ensureInitialDemoFiles()
    }

    fun getRootDirectory(): File {
        val ext = Environment.getExternalStorageDirectory()
        return if (ext != null && ext.exists() && ext.canRead()) {
            ext
        } else {
            File(context.filesDir, "SalimStorage").apply { mkdirs() }
        }
    }

    private fun ensureInitialDemoFiles() {
        val root = getRootDirectory()
        try {
            val docs = File(root, "Documents").apply { mkdirs() }
            val photos = File(root, "Pictures").apply { mkdirs() }
            val notes = File(root, "Notes").apply { mkdirs() }
            val downloads = File(root, "Downloads").apply { mkdirs() }

            val readme = File(docs, "Welcome_to_Salim.txt")
            if (!readme.exists()) {
                readme.writeText(
                    "Welcome to Salim File Manager!\n\n" +
                    "Features:\n" +
                    "- Silky smooth 1.15s long-press CRUD sheet with peek animation\n" +
                    "- Swipe-to-reveal list actions (Rename, Move, Delete)\n" +
                    "- Real Storage Analyzer with proportional category bars\n" +
                    "- Hash-based Duplicate Finder, Large File Finder & Empty Folder Finder\n" +
                    "- Trash recovery with original path preservation\n" +
                    "- App Manager, Network Storage & Private Vault\n"
                )
            }

            val sampleNote = File(notes, "Project_Checklist.txt")
            if (!sampleNote.exists()) {
                sampleNote.writeText("1. Test 1.15s long press\n2. Test Swipe to reveal\n3. Test Duplicate Finder\n4. Test Trash restore")
            }

            val duplicateNote = File(downloads, "Project_Checklist_Copy.txt")
            if (!duplicateNote.exists()) {
                duplicateNote.writeText("1. Test 1.15s long press\n2. Test Swipe to reveal\n3. Test Duplicate Finder\n4. Test Trash restore")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getFolderSortPreference(folderPath: String): SortPreference {
        val saved = folderSortDao.getSortForFolder(folderPath)
        val global = settingsRepository.settings.value
        return if (saved != null) {
            SortPreference(
                field = try { SortField.valueOf(saved.sortField) } catch (e: Exception) { global.defaultSortField },
                isAscending = saved.isAscending,
                foldersFirst = saved.foldersFirst
            )
        } else {
            SortPreference(
                field = global.defaultSortField,
                isAscending = global.defaultSortAscending,
                foldersFirst = global.foldersAlwaysFirst
            )
        }
    }

    suspend fun saveFolderSortPreference(folderPath: String, sortPreference: SortPreference) {
        folderSortDao.setSortForFolder(
            FolderSortEntity(
                folderPath = folderPath,
                sortField = sortPreference.field.name,
                isAscending = sortPreference.isAscending,
                foldersFirst = sortPreference.foldersFirst
            )
        )
    }

    suspend fun listFiles(folder: File): List<FileItem> = withContext(Dispatchers.IO) {
        if (!folder.exists() || !folder.isDirectory) return@withContext emptyList()

        val settings = settingsRepository.settings.value
        val sortPref = getFolderSortPreference(folder.absolutePath)
        val excludedList = excludedFolderDao.getAllExcludedFoldersList().map { it.path }

        val rawFiles = folder.listFiles() ?: return@withContext emptyList()

        val filtered = rawFiles.filter { file ->
            if (excludedList.contains(file.absolutePath)) return@filter false
            if (!settings.showHiddenFiles && (file.isHidden || file.name.startsWith("."))) return@filter false
            true
        }

        val items = filtered.map { file ->
            val isDir = file.isDirectory
            val itemCount = if (isDir) file.list()?.size ?: 0 else 0
            FileItem(
                file = file,
                name = file.name,
                path = file.absolutePath,
                isDirectory = isDir,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                isHidden = file.isHidden || file.name.startsWith("."),
                extension = if (file.isFile) file.extension.lowercase() else "",
                mimeType = FileUtils.getMimeType(file),
                readableType = FileUtils.getReadableTypeName(file),
                itemCount = itemCount
            )
        }

        sortFileItems(items, sortPref)
    }

    private fun sortFileItems(items: List<FileItem>, pref: SortPreference): List<FileItem> {
        val comparator = Comparator<FileItem> { a, b ->
            if (pref.foldersFirst && a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val res = when (pref.field) {
                SortField.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                SortField.DATE_MODIFIED -> a.lastModified.compareTo(b.lastModified)
                SortField.DATE_CREATED -> a.lastModified.compareTo(b.lastModified)
                SortField.SIZE -> a.size.compareTo(b.size)
                SortField.TYPE -> a.readableType.compareTo(b.readableType, ignoreCase = true)
            }
            if (pref.isAscending) res else -res
        }
        return items.sortedWith(comparator)
    }

    suspend fun createFolder(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        val newFolder = File(parent, name)
        if (newFolder.exists()) {
            return@withContext Result.failure(Exception("Folder '$name' already exists"))
        }
        if (newFolder.mkdirs()) {
            Result.success(newFolder)
        } else {
            Result.failure(Exception("Could not create folder '$name'"))
        }
    }

    suspend fun createTextFile(parent: File, name: String, content: String = ""): Result<File> = withContext(Dispatchers.IO) {
        val fileName = if (name.endsWith(".txt", ignoreCase = true)) name else "$name.txt"
        val newFile = File(parent, fileName)
        if (newFile.exists()) {
            return@withContext Result.failure(Exception("File '$fileName' already exists"))
        }
        try {
            newFile.writeText(content)
            Result.success(newFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFile(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        val target = File(file.parentFile, newName)
        if (target.exists() && target != file) {
            return@withContext Result.failure(Exception("A file named '$newName' already exists"))
        }
        val success = file.renameTo(target)
        if (success) {
            bookmarkDao.deleteByPath(file.absolutePath)
            Result.success(target)
        } else {
            Result.failure(Exception("Failed to rename '${file.name}'"))
        }
    }

    suspend fun moveToTrash(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val isDir = file.isDirectory
            val size = if (file.isFile) file.length() else FileUtils.calculateFolderStats(file).second
            val trashTarget = File(trashDir, "${System.currentTimeMillis()}_${file.name}")

            val moved = file.renameTo(trashTarget)
            if (!moved) {
                // Fallback copy then delete
                if (isDir) {
                    file.copyRecursively(trashTarget, overwrite = true)
                    file.deleteRecursively()
                } else {
                    file.copyTo(trashTarget, overwrite = true)
                    file.delete()
                }
            }

            trashDao.insertTrash(
                TrashEntity(
                    originalPath = file.absolutePath,
                    trashPath = trashTarget.absolutePath,
                    name = file.name,
                    size = size,
                    isDirectory = isDir,
                    deletedTimestamp = System.currentTimeMillis(),
                    mimeType = FileUtils.getMimeType(trashTarget)
                )
            )
            bookmarkDao.deleteByPath(file.absolutePath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromTrash(trashEntity: TrashEntity): Result<File> = withContext(Dispatchers.IO) {
        try {
            val trashFile = File(trashEntity.trashPath)
            var originalDest = File(trashEntity.originalPath)

            // If original parent directory no longer exists, restore to root directory
            if (originalDest.parentFile?.exists() != true) {
                originalDest.parentFile?.mkdirs()
            }
            if (originalDest.exists()) {
                originalDest = FileUtils.getConflictResolvedFile(originalDest.parentFile ?: getRootDirectory(), originalDest.name)
            }

            val restored = trashFile.renameTo(originalDest)
            if (!restored) {
                if (trashFile.isDirectory) {
                    trashFile.copyRecursively(originalDest, overwrite = true)
                    trashFile.deleteRecursively()
                } else {
                    trashFile.copyTo(originalDest, overwrite = true)
                    trashFile.delete()
                }
            }
            trashDao.deleteTrash(trashEntity)
            Result.success(originalDest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun permanentlyDeleteTrash(trashEntity: TrashEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trashFile = File(trashEntity.trashPath)
            if (trashFile.isDirectory) {
                trashFile.deleteRecursively()
            } else {
                trashFile.delete()
            }
            trashDao.deleteTrash(trashEntity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun emptyTrash(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val all = trashDao.getAllTrashList()
            for (item in all) {
                val f = File(item.trashPath)
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            }
            trashDao.clearTrash()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startCopyOperation(sourceFiles: List<File>, destinationFolder: File, onComplete: () -> Unit) {
        activeOperationJob?.cancel()
        isOperationCancelled = false
        activeOperationJob = CoroutineScope(Dispatchers.IO).launch {
            val totalFilesCount = sourceFiles.sumOf { if (it.isDirectory) FileUtils.calculateFolderStats(it).first else 1 }
            val totalBytesCount = sourceFiles.sumOf { if (it.isDirectory) FileUtils.calculateFolderStats(it).second else it.length() }

            _currentOperation.value = FileOperationProgress(
                operationId = "copy_${System.currentTimeMillis()}",
                operationName = "Copying files...",
                isRunning = true,
                totalFiles = totalFilesCount,
                totalBytes = totalBytesCount
            )

            var cumulativeFiles = 0
            var cumulativeBytes = 0L

            for (source in sourceFiles) {
                if (isOperationCancelled) break
                FileUtils.copyWithProgress(
                    source = source,
                    destDir = destinationFolder,
                    onProgress = { done, _, bytes, _, currentFile ->
                        _currentOperation.value = _currentOperation.value.copy(
                            currentFileName = currentFile,
                            filesProcessed = cumulativeFiles + done,
                            bytesProcessed = cumulativeBytes + bytes
                        )
                    },
                    isCancelled = { isOperationCancelled }
                )
                val (fCount, bCount) = if (source.isDirectory) FileUtils.calculateFolderStats(source) else Pair(1, source.length())
                cumulativeFiles += fCount
                cumulativeBytes += bCount
            }

            _currentOperation.value = _currentOperation.value.copy(isRunning = false)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun startMoveOperation(sourceFiles: List<File>, destinationFolder: File, onComplete: () -> Unit) {
        activeOperationJob?.cancel()
        isOperationCancelled = false
        activeOperationJob = CoroutineScope(Dispatchers.IO).launch {
            val totalFilesCount = sourceFiles.sumOf { if (it.isDirectory) FileUtils.calculateFolderStats(it).first else 1 }
            val totalBytesCount = sourceFiles.sumOf { if (it.isDirectory) FileUtils.calculateFolderStats(it).second else it.length() }

            _currentOperation.value = FileOperationProgress(
                operationId = "move_${System.currentTimeMillis()}",
                operationName = "Moving files...",
                isRunning = true,
                totalFiles = totalFilesCount,
                totalBytes = totalBytesCount
            )

            var cumulativeFiles = 0
            var cumulativeBytes = 0L

            for (source in sourceFiles) {
                if (isOperationCancelled) break
                val target = File(destinationFolder, source.name)
                val movedDirect = source.renameTo(target)
                if (!movedDirect) {
                    FileUtils.copyWithProgress(
                        source = source,
                        destDir = destinationFolder,
                        onProgress = { done, _, bytes, _, currentFile ->
                            _currentOperation.value = _currentOperation.value.copy(
                                currentFileName = currentFile,
                                filesProcessed = cumulativeFiles + done,
                                bytesProcessed = cumulativeBytes + bytes
                            )
                        },
                        isCancelled = { isOperationCancelled }
                    )
                    if (source.isDirectory) source.deleteRecursively() else source.delete()
                }
                val (fCount, bCount) = if (source.isDirectory) FileUtils.calculateFolderStats(source) else Pair(1, source.length())
                cumulativeFiles += fCount
                cumulativeBytes += bCount
                _currentOperation.value = _currentOperation.value.copy(
                    filesProcessed = cumulativeFiles,
                    bytesProcessed = cumulativeBytes
                )
            }

            _currentOperation.value = _currentOperation.value.copy(isRunning = false)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun startCompressOperation(file: File, onComplete: () -> Unit) {
        activeOperationJob?.cancel()
        isOperationCancelled = false
        activeOperationJob = CoroutineScope(Dispatchers.IO).launch {
            _currentOperation.value = FileOperationProgress(
                operationId = "zip_${System.currentTimeMillis()}",
                operationName = "Compressing to ZIP...",
                isRunning = true,
                totalFiles = 1,
                totalBytes = file.length()
            )

            FileUtils.compressToZip(
                source = file,
                onProgress = { done, total, bytes, totalB, current ->
                    _currentOperation.value = _currentOperation.value.copy(
                        currentFileName = current,
                        filesProcessed = done,
                        totalFiles = total,
                        bytesProcessed = bytes,
                        totalBytes = totalB
                    )
                },
                isCancelled = { isOperationCancelled }
            )

            _currentOperation.value = _currentOperation.value.copy(isRunning = false)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun startExtractOperation(archiveFile: File, onComplete: () -> Unit) {
        activeOperationJob?.cancel()
        isOperationCancelled = false
        activeOperationJob = CoroutineScope(Dispatchers.IO).launch {
            _currentOperation.value = FileOperationProgress(
                operationId = "extract_${System.currentTimeMillis()}",
                operationName = "Extracting archive...",
                isRunning = true,
                totalFiles = 1,
                totalBytes = archiveFile.length()
            )

            FileUtils.extractZip(
                zipFile = archiveFile,
                onProgress = { done, total, bytes, totalB, current ->
                    _currentOperation.value = _currentOperation.value.copy(
                        currentFileName = current,
                        filesProcessed = done,
                        totalFiles = total,
                        bytesProcessed = bytes,
                        totalBytes = totalB
                    )
                },
                isCancelled = { isOperationCancelled }
            )

            _currentOperation.value = _currentOperation.value.copy(isRunning = false)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun cancelCurrentOperation() {
        isOperationCancelled = true
        activeOperationJob?.cancel()
        _currentOperation.value = _currentOperation.value.copy(isRunning = false)
    }

    suspend fun toggleBookmark(file: File): Boolean = withContext(Dispatchers.IO) {
        val path = file.absolutePath
        val isBookmarked = bookmarkDao.isBookmarked(path)
        if (isBookmarked) {
            bookmarkDao.deleteByPath(path)
            false
        } else {
            bookmarkDao.insertBookmark(
                BookmarkEntity(
                    path = path,
                    name = file.name,
                    isDirectory = file.isDirectory
                )
            )
            true
        }
    }

    suspend fun isBookmarked(path: String): Boolean = withContext(Dispatchers.IO) {
        bookmarkDao.isBookmarked(path)
    }

    suspend fun updateBookmarkOrder(bookmarks: List<BookmarkEntity>) = withContext(Dispatchers.IO) {
        val updated = bookmarks.mapIndexed { index, item -> item.copy(sortOrder = index) }
        bookmarkDao.updateBookmarks(updated)
    }

    suspend fun getStorageVolumeInfo(): StorageVolumeInfo = withContext(Dispatchers.IO) {
        val root = getRootDirectory()
        val totalSpace = root.totalSpace.coerceAtLeast(1024L * 1024L * 1024L)
        val freeSpace = root.freeSpace
        val usedSpace = (totalSpace - freeSpace).coerceAtLeast(0L)

        var imageBytes = 0L; var imageCount = 0
        var videoBytes = 0L; var videoCount = 0
        var audioBytes = 0L; var audioCount = 0
        var docBytes = 0L; var docCount = 0
        var appBytes = 0L; var appCount = 0
        var otherBytes = 0L; var otherCount = 0

        fun scanFolder(folder: File, depth: Int = 0) {
            if (depth > 6) return
            val list = folder.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    scanFolder(f, depth + 1)
                } else {
                    val len = f.length()
                    when (f.extension.lowercase()) {
                        "jpg", "jpeg", "png", "webp", "gif", "svg" -> {
                            imageBytes += len; imageCount++
                        }
                        "mp4", "mkv", "mov", "avi", "webm" -> {
                            videoBytes += len; videoCount++
                        }
                        "mp3", "wav", "flac", "m4a", "aac", "ogg" -> {
                            audioBytes += len; audioCount++
                        }
                        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> {
                            docBytes += len; docCount++
                        }
                        "apk" -> {
                            appBytes += len; appCount++
                        }
                        else -> {
                            otherBytes += len; otherCount++
                        }
                    }
                }
            }
        }

        scanFolder(root)

        val scannedTotal = imageBytes + videoBytes + audioBytes + docBytes + appBytes + otherBytes
        val systemBytes = (usedSpace - scannedTotal).coerceAtLeast(1024L * 1024L * 250L)

        val categories = listOf(
            StorageCategoryInfo(StorageCategoryType.IMAGES, "Images", imageBytes, imageCount, ColorImage),
            StorageCategoryInfo(StorageCategoryType.VIDEOS, "Videos", videoBytes, videoCount, ColorVideo),
            StorageCategoryInfo(StorageCategoryType.AUDIO, "Audio", audioBytes, audioCount, ColorAudio),
            StorageCategoryInfo(StorageCategoryType.DOCUMENTS, "Documents", docBytes, docCount, ColorDocument),
            StorageCategoryInfo(StorageCategoryType.APPS, "Apps & APKs", appBytes, appCount, ColorApk),
            StorageCategoryInfo(StorageCategoryType.OTHER, "Other Files", otherBytes, otherCount, ColorOther),
            StorageCategoryInfo(StorageCategoryType.SYSTEM, "System & OS", systemBytes, 1, ColorFolder)
        )

        StorageVolumeInfo(
            name = "Internal Storage",
            totalSpace = totalSpace,
            freeSpace = freeSpace,
            usedSpace = usedSpace,
            categories = categories
        )
    }

    suspend fun getFilesForCategory(categoryType: StorageCategoryType): List<FileItem> = withContext(Dispatchers.IO) {
        val root = getRootDirectory()
        val result = mutableListOf<FileItem>()

        fun scanFolder(folder: File, depth: Int = 0) {
            if (depth > 6) return
            val list = folder.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    scanFolder(f, depth + 1)
                } else {
                    val ext = f.extension.lowercase()
                    val matches = when (categoryType) {
                        StorageCategoryType.IMAGES -> ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg")
                        StorageCategoryType.VIDEOS -> ext in listOf("mp4", "mkv", "mov", "avi", "webm")
                        StorageCategoryType.AUDIO -> ext in listOf("mp3", "wav", "flac", "m4a", "aac", "ogg")
                        StorageCategoryType.DOCUMENTS -> ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
                        StorageCategoryType.APPS -> ext == "apk"
                        StorageCategoryType.OTHER -> ext !in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "mp4", "mkv", "mov", "avi", "webm", "mp3", "wav", "flac", "m4a", "aac", "ogg", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "apk")
                        StorageCategoryType.SYSTEM -> false
                    }
                    if (matches) {
                        result.add(
                            FileItem(
                                file = f,
                                mimeType = FileUtils.getMimeType(f),
                                readableType = FileUtils.getReadableTypeName(f)
                            )
                        )
                    }
                }
            }
        }

        scanFolder(root)
        result.sortedByDescending { it.size }
    }

    suspend fun findDuplicates(
        onProgress: (scannedCount: Int, totalFiles: Int) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val root = getRootDirectory()
        val allFiles = mutableListOf<File>()

        fun collectFiles(f: File) {
            val list = f.listFiles() ?: return
            for (child in list) {
                if (child.isDirectory) collectFiles(child)
                else if (child.isFile && child.length() > 0) allFiles.add(child)
            }
        }
        collectFiles(root)

        val total = allFiles.size
        val hashMap = mutableMapOf<String, MutableList<File>>()

        var scanned = 0
        for (file in allFiles) {
            scanned++
            onProgress(scanned, total)
            val hash = HashUtils.computeFileHash(file)
            if (hash != null) {
                hashMap.getOrPut(hash) { mutableListOf() }.add(file)
            }
        }

        hashMap.filter { it.value.size > 1 }.map { entry ->
            val first = entry.value.first()
            DuplicateGroup(
                contentHash = entry.key,
                size = first.length(),
                files = entry.value.map {
                    FileItem(
                        file = it,
                        mimeType = FileUtils.getMimeType(it),
                        readableType = FileUtils.getReadableTypeName(it)
                    )
                }
            )
        }.sortedByDescending { it.size * it.files.size }
    }

    suspend fun findLargeFiles(minSizeBytes: Long = 100L * 1024L * 1024L): List<LargeFileEntry> = withContext(Dispatchers.IO) {
        val root = getRootDirectory()
        val results = mutableListOf<LargeFileEntry>()

        fun scan(f: File) {
            val list = f.listFiles() ?: return
            for (child in list) {
                if (child.isDirectory) {
                    scan(child)
                } else if (child.isFile && child.length() >= minSizeBytes) {
                    results.add(
                        LargeFileEntry(
                            fileItem = FileItem(
                                file = child,
                                mimeType = FileUtils.getMimeType(child),
                                readableType = FileUtils.getReadableTypeName(child)
                            ),
                            size = child.length()
                        )
                    )
                }
            }
        }
        scan(root)
        results.sortedByDescending { it.size }
    }

    suspend fun findEmptyFolders(): List<EmptyFolderEntry> = withContext(Dispatchers.IO) {
        val root = getRootDirectory()
        val results = mutableListOf<EmptyFolderEntry>()

        fun scan(f: File): Boolean {
            if (!f.isDirectory) return false
            val children = f.listFiles() ?: emptyArray()
            var hasAnyFiles = false

            for (child in children) {
                if (child.isFile) {
                    hasAnyFiles = true
                } else if (child.isDirectory) {
                    val childHasFiles = scan(child)
                    if (childHasFiles) hasAnyFiles = true
                }
            }

            if (!hasAnyFiles && f != root) {
                results.add(
                    EmptyFolderEntry(
                        fileItem = FileItem(
                            file = f,
                            isDirectory = true,
                            readableType = "Empty Folder"
                        ),
                        path = f.absolutePath
                    )
                )
            }
            return hasAnyFiles
        }

        scan(root)
        results
    }

    suspend fun getInstalledApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = mutableListOf<AppItem>()

        for (appInfo in packages) {
            try {
                val name = pm.getApplicationLabel(appInfo).toString()
                val pkgInfo = pm.getPackageInfo(appInfo.packageName, 0)
                val sourceDir = appInfo.sourceDir
                val apkFile = if (sourceDir != null) File(sourceDir) else null
                val apkSize = apkFile?.length() ?: 0L

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                appList.add(
                    AppItem(
                        packageName = appInfo.packageName,
                        appName = name,
                        versionName = pkgInfo.versionName ?: "1.0",
                        apkSize = apkSize,
                        dataSize = (apkSize * 0.4).toLong(), // Estimate data
                        cacheSize = (apkSize * 0.15).toLong(), // Estimate cache
                        installDate = pkgInfo.firstInstallTime,
                        lastUsedDate = pkgInfo.lastUpdateTime,
                        isSystemApp = isSystem
                    )
                )
            } catch (e: Exception) {
                // Ignore app query errors
            }
        }
        appList.sortedByDescending { it.totalSize }
    }

    suspend fun extractApk(packageName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val sourceApk = File(appInfo.sourceDir)
            val label = pm.getApplicationLabel(appInfo).toString().replace(" ", "_")
            val downloadsDir = File(getRootDirectory(), "Downloads").apply { mkdirs() }
            val destFile = FileUtils.getConflictResolvedFile(downloadsDir, "${label}_base.apk")

            sourceApk.copyTo(destFile, overwrite = true)
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchFiles(
        query: String,
        scope: SearchScope,
        currentFolder: File
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim().lowercase()

        val searchRoot = when (scope) {
            SearchScope.THIS_FOLDER -> currentFolder
            SearchScope.THIS_VOLUME, SearchScope.EVERYWHERE -> getRootDirectory()
        }

        val results = mutableListOf<FileItem>()
        fun scan(f: File, depth: Int = 0) {
            if (depth > 8) return
            val list = f.listFiles() ?: return
            for (child in list) {
                if (child.name.lowercase().contains(q)) {
                    results.add(
                        FileItem(
                            file = child,
                            mimeType = FileUtils.getMimeType(child),
                            readableType = FileUtils.getReadableTypeName(child),
                            itemCount = if (child.isDirectory) child.list()?.size ?: 0 else 0
                        )
                    )
                }
                if (child.isDirectory) {
                    scan(child, depth + 1)
                }
            }
        }

        scan(searchRoot)
        results.sortedBy { it.name.lowercase().indexOf(q) }
    }

    suspend fun recordSearch(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            searchHistoryDao.insertSearch(SearchHistoryEntity(query = query.trim()))
        }
    }

    suspend fun deleteSearch(id: Long) = withContext(Dispatchers.IO) {
        searchHistoryDao.deleteSearchById(id)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchHistoryDao.clearAllSearches()
    }

    suspend fun testNetworkConnection(host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 4000)
            }
            Result.success("Successfully reached $host on port $port")
        } catch (e: Exception) {
            Result.failure(Exception("Could not connect to '$host:$port'. Check host address and network status."))
        }
    }

    suspend fun saveNetworkConnection(connection: NetworkConnectionEntity): Long = withContext(Dispatchers.IO) {
        networkConnectionDao.insertConnection(connection)
    }

    suspend fun deleteNetworkConnection(connection: NetworkConnectionEntity) = withContext(Dispatchers.IO) {
        networkConnectionDao.deleteConnection(connection)
    }

    suspend fun addExcludedFolder(path: String) = withContext(Dispatchers.IO) {
        excludedFolderDao.insertExcluded(ExcludedFolderEntity(path = path))
    }

    suspend fun removeExcludedFolder(path: String) = withContext(Dispatchers.IO) {
        excludedFolderDao.deleteExcluded(ExcludedFolderEntity(path = path))
    }

    fun getThumbnailCacheSize(): Long {
        return context.cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun clearThumbnailCache() {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun getPrivateVaultFolder(): File = privateVaultDir
}
