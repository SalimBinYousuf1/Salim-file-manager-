package com.example.ui.screens.browse

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BookmarkEntity
import com.example.data.model.*
import com.example.data.repository.FileManagerRepository
import com.example.data.repository.LocationSummaryData
import com.example.data.repository.LocationType
import com.example.data.repository.SettingsRepository
import com.example.data.util.FileUtils
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    repository: FileManagerRepository,
    settingsRepository: SettingsRepository,
    currentFolder: File,
    onNavigateToFolder: (File) -> Unit,
    onNavigateUp: () -> Unit,
    canGoBack: Boolean,
    onOpenSettings: () -> Unit,
    onOpenBookmarks: (() -> Unit)? = null,
    onOpenTrash: (() -> Unit)? = null,
    onOpenVault: (() -> Unit)? = null,
    onOpenNetwork: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by settingsRepository.settings.collectAsState()

    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Locations summary and Recent files for the Root "Files" Screen (Section 46, 47, 48)
    var locationSummaries by remember { mutableStateOf<List<LocationSummaryData>>(emptyList()) }
    var recentFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchScope by remember { mutableStateOf(SearchScope.THIS_FOLDER) }
    var searchResults by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    val recentSearches by repository.recentSearches.collectAsState(initial = emptyList())

    // Selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<FileItem>() }

    // Sheets & Dialogs state
    var activeCrudItem by remember { mutableStateOf<FileItem?>(null) }
    var activeDetailsItem by remember { mutableStateOf<FileItem?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }
    var currentSortPref by remember { mutableStateOf(SortPreference()) }

    var folderPickerOperation by remember { mutableStateOf<String?>(null) } // "copy" or "move"
    var filesToTransfer by remember { mutableStateOf<List<File>>(emptyList()) }

    var itemToDeleteWithConfirm by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Inline Creation state (Part F)
    var inlineCreatingFolder by remember { mutableStateOf(false) }
    var inlineCreatingFile by remember { mutableStateOf(false) }
    var inlineNameInput by remember { mutableStateOf(TextFieldValue("")) }

    // Rename dialog state
    var itemToRename by remember { mutableStateOf<FileItem?>(null) }
    var renameInput by remember { mutableStateOf(TextFieldValue("")) }

    // Bookmarks & Trash tracking
    val bookmarksList by repository.bookmarks.collectAsState(initial = emptyList())
    val trashList by repository.trashItems.collectAsState(initial = emptyList())
    val bookmarkedPaths = remember(bookmarksList) { bookmarksList.map { it.path }.toSet() }

    // Refresh data
    fun refreshFiles() {
        coroutineScope.launch {
            isLoading = true
            currentSortPref = repository.getFolderSortPreference(currentFolder.absolutePath)
            files = repository.listFiles(currentFolder)

            if (!canGoBack) {
                locationSummaries = repository.getLocationsSummary()
                recentFiles = repository.getRecentFiles(8)
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentFolder, settings.showHiddenFiles, canGoBack) {
        refreshFiles()
    }

    // Debounced search
    LaunchedEffect(searchQuery, searchScope, currentFolder) {
        if (searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(150)
            searchResults = repository.searchFiles(searchQuery, searchScope, currentFolder)
        } else {
            searchResults = emptyList()
        }
    }

    // Grid column count pinch logic
    var gridColumns by remember { mutableStateOf(settings.gridColumns.toFloat()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (isSelectionMode) {
                SalimSelectionTopBar(
                    selectedCount = selectedFiles.size,
                    onCancel = {
                        isSelectionMode = false
                        selectedFiles.clear()
                    },
                    onCopy = {
                        filesToTransfer = selectedFiles.map { it.file }
                        folderPickerOperation = "copy"
                    },
                    onMove = {
                        filesToTransfer = selectedFiles.map { it.file }
                        folderPickerOperation = "move"
                    },
                    onCompress = {
                        if (selectedFiles.isNotEmpty()) {
                            repository.startCompressOperation(selectedFiles.first().file) {
                                refreshFiles()
                                Toast.makeText(context, "Compression completed", Toast.LENGTH_SHORT).show()
                            }
                            isSelectionMode = false
                            selectedFiles.clear()
                        }
                    },
                    onShare = {
                        val file = selectedFiles.firstOrNull()?.file
                        if (file != null) FileUtils.shareFile(context, file)
                    },
                    onDelete = {
                        if (settings.confirmBeforeDelete) {
                            showBatchDeleteConfirmDialog = true
                        } else {
                            coroutineScope.launch {
                                selectedFiles.forEach { repository.moveToTrash(it.file) }
                                isSelectionMode = false
                                selectedFiles.clear()
                                refreshFiles()
                                Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            } else if (isSearchActive) {
                SalimSearchTopBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onCloseSearch = {
                        isSearchActive = false
                        searchQuery = ""
                    },
                    selectedScope = searchScope,
                    onScopeSelected = { searchScope = it }
                )
            } else {
                SalimBrowseTopBar(
                    title = if (!canGoBack) "Files" else currentFolder.name,
                    canGoBack = canGoBack,
                    onBackClick = onNavigateUp,
                    onSearchClick = { isSearchActive = true },
                    onSortClick = { showSortSheet = true },
                    onNewFolderClick = {
                        inlineCreatingFile = false
                        inlineCreatingFolder = true
                        inlineNameInput = TextFieldValue(
                            text = "New Folder",
                            selection = TextRange(0, 10)
                        )
                    },
                    onNewFileClick = {
                        inlineCreatingFolder = false
                        inlineCreatingFile = true
                        inlineNameInput = TextFieldValue(
                            text = "New File",
                            selection = TextRange(0, 8)
                        )
                    },
                    onSelectModeClick = {
                        isSelectionMode = true
                    },
                    viewMode = settings.defaultViewMode,
                    onToggleViewMode = {
                        val newMode = if (settings.defaultViewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                        settingsRepository.updateDefaultViewMode(newMode)
                    },
                    onSettingsClick = onOpenSettings
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isSearchActive) {
                // Search View
                if (searchQuery.isBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        if (recentSearches.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                TextButton(onClick = { coroutineScope.launch { repository.clearSearchHistory() } }) {
                                    Text("Clear All")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                recentSearches.forEach { search ->
                                    InputChip(
                                        selected = false,
                                        onClick = { searchQuery = search.query },
                                        label = { Text(search.query) },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { coroutineScope.launch { repository.deleteSearch(search.id) } },
                                                modifier = Modifier.size(16.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove")
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Type to search by filename",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No files match '$searchQuery'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(searchResults, key = { it.path }) { item ->
                            FileRowItem(
                                fileItem = item,
                                isSelectionMode = false,
                                isSelected = false,
                                onTap = {
                                    coroutineScope.launch { repository.recordSearch(searchQuery) }
                                    if (item.isDirectory) onNavigateToFolder(item.file) else FileUtils.openFile(context, item.file)
                                },
                                onLongPress1150ms = { activeCrudItem = item },
                                onLongPressDragSelect = { isSelectionMode = true; selectedFiles.add(item) },
                                onRename = {
                                    itemToRename = item
                                    renameInput = TextFieldValue(item.name)
                                },
                                onMove = {
                                    filesToTransfer = listOf(item.file)
                                    folderPickerOperation = "move"
                                },
                                onDelete = {
                                    itemToDeleteWithConfirm = item
                                    if (settings.confirmBeforeDelete) showDeleteConfirmDialog = true
                                    else {
                                        coroutineScope.launch {
                                            repository.moveToTrash(item.file)
                                            refreshFiles()
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            } else if (!canGoBack && !isSelectionMode) {
                // Main Mobile Interface (Sections 46, 47, 48 - No dashboard feel, clean Apple-grade utility)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("salim_main_files_screen")
                ) {
                    // Section: Locations
                    item {
                        Text(
                            text = "Locations",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }

                    items(locationSummaries, key = { it.id }) { loc ->
                        LocationRowItem(
                            location = loc,
                            onClick = { onNavigateToFolder(loc.targetFile) }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }

                    // Section: Recent Files (Section 46)
                    if (recentFiles.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(recentFiles, key = { it.path }) { item ->
                            FileRowItem(
                                fileItem = item,
                                isSelectionMode = false,
                                isSelected = false,
                                onTap = {
                                    if (item.isDirectory) onNavigateToFolder(item.file) else FileUtils.openFile(context, item.file)
                                },
                                onLongPress1150ms = { activeCrudItem = item },
                                onLongPressDragSelect = {
                                    isSelectionMode = true
                                    selectedFiles.add(item)
                                },
                                onRename = {
                                    itemToRename = item
                                    renameInput = TextFieldValue(item.name)
                                },
                                onMove = {
                                    filesToTransfer = listOf(item.file)
                                    folderPickerOperation = "move"
                                },
                                onDelete = {
                                    itemToDeleteWithConfirm = item
                                    if (settings.confirmBeforeDelete) showDeleteConfirmDialog = true
                                    else {
                                        coroutineScope.launch {
                                            repository.moveToTrash(item.file)
                                            refreshFiles()
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        }
                    }

                    // Section: Favorites & Quick Shortcuts
                    item {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Favorites & Shortcuts",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        ShortcutRowItem(
                            title = "Bookmarks",
                            subtitle = if (bookmarksList.size == 1) "1 item" else "${bookmarksList.size} items",
                            icon = Icons.Default.Bookmark,
                            iconTint = ColorFolder,
                            onClick = { onOpenBookmarks?.invoke() }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 56.dp)
                        )

                        ShortcutRowItem(
                            title = "Trash",
                            subtitle = if (trashList.size == 1) "1 item" else "${trashList.size} items",
                            icon = Icons.Default.Delete,
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = { onOpenTrash?.invoke() }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 56.dp)
                        )

                        ShortcutRowItem(
                            title = "Private Vault",
                            subtitle = "Encrypted local folder",
                            icon = Icons.Default.Lock,
                            iconTint = ColorImage,
                            onClick = { onOpenVault?.invoke() }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 56.dp)
                        )

                        ShortcutRowItem(
                            title = "Network Storage",
                            subtitle = "SMB, FTP, SFTP, WebDAV",
                            icon = Icons.Default.Dns,
                            iconTint = ColorDocument,
                            onClick = { onOpenNetwork?.invoke() }
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (files.isEmpty() && !inlineCreatingFolder && !inlineCreatingFile) {
                // Empty state (Part M, Section 33)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "This folder is empty.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                inlineCreatingFolder = true
                                inlineNameInput = TextFieldValue(
                                    text = "New Folder",
                                    selection = TextRange(0, 10)
                                )
                            },
                            modifier = Modifier.testTag("empty_state_new_folder_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("New Folder")
                        }
                    }
                }
            } else {
                // Inside Folder: Regular File Listing (Section 49, 50)
                Column(modifier = Modifier.fillMaxSize()) {
                    // Breadcrumb navigation row
                    SalimBreadcrumbRow(
                        currentFolder = currentFolder,
                        rootFolder = repository.getRootDirectory(),
                        onNavigateTo = onNavigateToFolder
                    )

                    if (settings.defaultViewMode == ViewMode.LIST) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // Inline Folder Creation Row
                            if (inlineCreatingFolder) {
                                item {
                                    InlineCreationRow(
                                        icon = Icons.Default.Folder,
                                        label = "Folder name",
                                        value = inlineNameInput,
                                        onValueChange = { inlineNameInput = it },
                                        onCommit = {
                                            val name = inlineNameInput.text.trim()
                                            if (name.isNotBlank()) {
                                                coroutineScope.launch {
                                                    repository.createFolder(currentFolder, name)
                                                    inlineCreatingFolder = false
                                                    refreshFiles()
                                                }
                                            } else {
                                                inlineCreatingFolder = false
                                            }
                                        },
                                        onCancel = { inlineCreatingFolder = false }
                                    )
                                }
                            }

                            // Inline Text File Creation Row
                            if (inlineCreatingFile) {
                                item {
                                    InlineCreationRow(
                                        icon = Icons.Default.Description,
                                        label = "File name (e.g. notes.txt)",
                                        value = inlineNameInput,
                                        onValueChange = { inlineNameInput = it },
                                        onCommit = {
                                            val name = inlineNameInput.text.trim()
                                            if (name.isNotBlank()) {
                                                coroutineScope.launch {
                                                    repository.createTextFile(currentFolder, name, "")
                                                    inlineCreatingFile = false
                                                    refreshFiles()
                                                }
                                            } else {
                                                inlineCreatingFile = false
                                            }
                                        },
                                        onCancel = { inlineCreatingFile = false }
                                    )
                                }
                            }

                            items(files, key = { it.path }) { item ->
                                val isSelected = selectedFiles.contains(item)
                                FileRowItem(
                                    fileItem = item,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onTap = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedFiles.remove(item) else selectedFiles.add(item)
                                        } else {
                                            if (item.isDirectory) onNavigateToFolder(item.file)
                                            else FileUtils.openFile(context, item.file)
                                        }
                                    },
                                    onLongPress1150ms = {
                                        if (!isSelectionMode) activeCrudItem = item
                                    },
                                    onLongPressDragSelect = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedFiles.add(item)
                                        }
                                    },
                                    onRename = {
                                        itemToRename = item
                                        renameInput = TextFieldValue(item.name)
                                    },
                                    onMove = {
                                        filesToTransfer = listOf(item.file)
                                        folderPickerOperation = "move"
                                    },
                                    onDelete = {
                                        itemToDeleteWithConfirm = item
                                        if (settings.confirmBeforeDelete) showDeleteConfirmDialog = true
                                        else {
                                            coroutineScope.launch {
                                                repository.moveToTrash(item.file)
                                                refreshFiles()
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    } else {
                        // Grid View with Pinch Column Count interpolation (Part A & Section 49)
                        val cols = gridColumns.toInt().coerceIn(2, 6)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        if (zoom > 1.05f && gridColumns > 2f) {
                                            gridColumns = (gridColumns - 0.05f).coerceAtLeast(2f)
                                        } else if (zoom < 0.95f && gridColumns < 6f) {
                                            gridColumns = (gridColumns + 0.05f).coerceAtMost(6f)
                                        }
                                    }
                                },
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(files, key = { it.path }) { item ->
                                val isSelected = selectedFiles.contains(item)
                                FileGridItem(
                                    fileItem = item,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onTap = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedFiles.remove(item) else selectedFiles.add(item)
                                        } else {
                                            if (item.isDirectory) onNavigateToFolder(item.file)
                                            else FileUtils.openFile(context, item.file)
                                        }
                                    },
                                    onLongPress1150ms = {
                                        if (!isSelectionMode) activeCrudItem = item
                                    },
                                    onLongPressDragSelect = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedFiles.add(item)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 1.15-Second CRUD Sheet (Part B)
    if (activeCrudItem != null) {
        val isBm = bookmarkedPaths.contains(activeCrudItem?.path)
        SalimCrudBottomSheet(
            fileItem = activeCrudItem,
            isBookmarked = isBm,
            onDismiss = { activeCrudItem = null },
            onOpen = { item ->
                if (item.isDirectory) onNavigateToFolder(item.file) else FileUtils.openFile(context, item.file)
            },
            onRename = { item ->
                itemToRename = item
                val stem = if (item.file.isFile && item.file.extension.isNotEmpty()) item.file.nameWithoutExtension else item.name
                renameInput = TextFieldValue(text = item.name, selection = TextRange(0, stem.length))
            },
            onCopy = { item ->
                filesToTransfer = listOf(item.file)
                folderPickerOperation = "copy"
            },
            onMove = { item ->
                filesToTransfer = listOf(item.file)
                folderPickerOperation = "move"
            },
            onCompress = { item ->
                repository.startCompressOperation(item.file) {
                    refreshFiles()
                    Toast.makeText(context, "Compressed to ZIP", Toast.LENGTH_SHORT).show()
                }
            },
            onShare = { item ->
                FileUtils.shareFile(context, item.file)
            },
            onToggleBookmark = { item ->
                coroutineScope.launch {
                    val added = repository.toggleBookmark(item.file)
                    val msg = if (added) "Added to Bookmarks" else "Removed from Bookmarks"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            onDetails = { item ->
                activeDetailsItem = item
            },
            onExtract = { item ->
                repository.startExtractOperation(item.file) {
                    refreshFiles()
                    Toast.makeText(context, "Archive extracted", Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = { item ->
                itemToDeleteWithConfirm = item
                if (settings.confirmBeforeDelete) {
                    showDeleteConfirmDialog = true
                } else {
                    coroutineScope.launch {
                        repository.moveToTrash(item.file)
                        refreshFiles()
                        Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // File/Folder Details Modal Sheet (Part E)
    if (activeDetailsItem != null) {
        SalimDetailsSheet(
            fileItem = activeDetailsItem,
            onDismiss = { activeDetailsItem = null },
            onRenameInline = { newName ->
                coroutineScope.launch {
                    val result = repository.renameFile(activeDetailsItem!!.file, newName)
                    if (result.isSuccess) {
                        refreshFiles()
                        activeDetailsItem = FileItem(result.getOrThrow())
                    }
                }
            }
        )
    }

    // Sort Bottom Sheet (Part K)
    if (showSortSheet) {
        SalimSortBottomSheet(
            currentSort = currentSortPref,
            onDismiss = { showSortSheet = false },
            onSortChanged = { newSort ->
                currentSortPref = newSort
                coroutineScope.launch {
                    repository.saveFolderSortPreference(currentFolder.absolutePath, newSort)
                    refreshFiles()
                }
            }
        )
    }

    // Folder Picker Dialog (Copy / Move)
    if (folderPickerOperation != null) {
        SalimFolderPickerDialog(
            title = if (folderPickerOperation == "copy") "Copy to..." else "Move to...",
            rootFolder = repository.getRootDirectory(),
            initialFolder = currentFolder,
            onDismiss = {
                folderPickerOperation = null
                filesToTransfer = emptyList()
            },
            onConfirm = { destinationFolder ->
                val op = folderPickerOperation
                val toTransfer = filesToTransfer
                folderPickerOperation = null
                filesToTransfer = emptyList()
                isSelectionMode = false
                selectedFiles.clear()

                if (op == "copy") {
                    repository.startCopyOperation(toTransfer, destinationFolder) {
                        refreshFiles()
                        Toast.makeText(context, "Copy complete", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    repository.startMoveOperation(toTransfer, destinationFolder) {
                        refreshFiles()
                        Toast.makeText(context, "Move complete", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Single item delete confirmation dialog
    if (showDeleteConfirmDialog && itemToDeleteWithConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Move to Trash?") },
            text = { Text("Move '${itemToDeleteWithConfirm?.name}' to Trash? You can restore it later.") },
            confirmButton = {
                Button(
                    onClick = {
                        val item = itemToDeleteWithConfirm
                        showDeleteConfirmDialog = false
                        if (item != null) {
                            coroutineScope.launch {
                                repository.moveToTrash(item.file)
                                refreshFiles()
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
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch delete confirmation dialog
    if (showBatchDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirmDialog = false },
            title = { Text("Move items to Trash?") },
            text = { Text("Move ${selectedFiles.size} selected items to Trash?") },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteConfirmDialog = false
                        coroutineScope.launch {
                            selectedFiles.forEach { repository.moveToTrash(it.file) }
                            isSelectionMode = false
                            selectedFiles.clear()
                            refreshFiles()
                            Toast.makeText(context, "Moved to Trash", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename dialog
    if (itemToRename != null) {
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val item = itemToRename
                        val newName = renameInput.text.trim()
                        itemToRename = null
                        if (item != null && newName.isNotBlank() && newName != item.name) {
                            coroutineScope.launch {
                                val result = repository.renameFile(item.file, newName)
                                if (result.isSuccess) {
                                    refreshFiles()
                                    Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Rename failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LocationRowItem(
    location: LocationSummaryData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (location.type) {
        LocationType.INTERNAL_STORAGE -> Icons.Default.PhoneAndroid
        LocationType.DOWNLOADS -> Icons.Default.Download
        LocationType.DOCUMENTS -> Icons.Default.Description
        LocationType.IMAGES -> Icons.Default.Image
        LocationType.VIDEOS -> Icons.Default.Videocam
        LocationType.AUDIO -> Icons.Default.Audiotrack
    }
    val iconTint = when (location.type) {
        LocationType.INTERNAL_STORAGE -> ColorFolder
        LocationType.DOWNLOADS -> ColorArchive
        LocationType.DOCUMENTS -> ColorDocument
        LocationType.IMAGES -> ColorImage
        LocationType.VIDEOS -> ColorVideo
        LocationType.AUDIO -> ColorAudio
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = location.title,
            tint = iconTint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = location.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
fun ShortcutRowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
fun InlineCreationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = { Text(label) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() })
        )
        IconButton(onClick = onCommit) {
            Icon(Icons.Default.Check, contentDescription = "Commit", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Cancel")
        }
    }
}
