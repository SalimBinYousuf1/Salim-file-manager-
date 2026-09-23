package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SalimDatabase
import com.example.data.model.StorageCategoryType
import com.example.data.repository.AppThemeSetting
import com.example.data.repository.FileManagerRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.components.SalimOperationProgressBar
import com.example.ui.screens.apps.AppManagerScreen
import com.example.ui.screens.bookmarks.BookmarksScreen
import com.example.ui.screens.browse.BrowseScreen
import com.example.ui.screens.network.NetworkScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.storage.CategoryFilesScreen
import com.example.ui.screens.storage.DuplicateFinderScreen
import com.example.ui.screens.storage.EmptyFolderFinderScreen
import com.example.ui.screens.storage.LargeFileFinderScreen
import com.example.ui.screens.storage.StorageScreen
import com.example.ui.screens.trash.TrashScreen
import com.example.ui.screens.vault.PrivateVaultScreen
import com.example.ui.theme.AsglAtmosphereBackground
import com.example.ui.theme.SalimTheme
import com.example.ui.theme.liquidGlass
import java.io.File

enum class SalimTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    BROWSE("Browse", Icons.Filled.Folder, Icons.Outlined.Folder),
    STORAGE("Storage", Icons.Filled.PieChart, Icons.Outlined.PieChart),
    APPS("Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    TRASH("Trash", Icons.Filled.Delete, Icons.Outlined.Delete),
    MORE("More", Icons.Filled.Menu, Icons.Outlined.Menu)
}

sealed class SubScreen {
    object None : SubScreen()
    object DuplicateFinder : SubScreen()
    object LargeFileFinder : SubScreen()
    object EmptyFolderFinder : SubScreen()
    data class CategoryFiles(val category: StorageCategoryType, val title: String) : SubScreen()
    object Bookmarks : SubScreen()
    object Network : SubScreen()
    object Vault : SubScreen()
    object Settings : SubScreen()
}

class MainActivity : ComponentActivity() {
    private lateinit var database: SalimDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var fileManagerRepository: FileManagerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = SalimDatabase.getDatabase(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        fileManagerRepository = FileManagerRepository(applicationContext, database, settingsRepository)

        setContent {
            val settings by settingsRepository.settings.collectAsState()
            val isDark = when (settings.theme) {
                AppThemeSetting.SYSTEM -> isSystemInDarkTheme()
                AppThemeSetting.LIGHT -> false
                AppThemeSetting.DARK -> true
                AppThemeSetting.ASGL -> true
            }

            SalimTheme(
                themeSetting = settings.theme,
                darkTheme = isDark,
                highContrast = settings.highContrast
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (settings.theme == AppThemeSetting.ASGL) {
                        AsglAtmosphereBackground()
                    }
                    SalimMainApp(
                        repository = fileManagerRepository,
                        settingsRepository = settingsRepository
                    )
                }
            }
        }
    }
}

@Composable
fun SalimMainApp(
    repository: FileManagerRepository,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsState()
    var currentTab by remember { mutableStateOf(SalimTab.BROWSE) }
    var currentSubScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }

    // Folder navigation backstack
    val rootDir = remember { repository.getRootDirectory() }
    val folderBackStack = remember { mutableStateListOf(rootDir) }
    val currentFolder = folderBackStack.last()

    // Persistent Operation Progress
    val activeOperation by repository.currentOperation.collectAsState()

    // Permission check for MANAGE_EXTERNAL_STORAGE (Android 11+)
    var hasFullStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        )
    }

    // Intercept Back Press
    BackHandler(enabled = currentSubScreen !is SubScreen.None || folderBackStack.size > 1) {
        if (currentSubScreen !is SubScreen.None) {
            currentSubScreen = SubScreen.None
        } else if (folderBackStack.size > 1) {
            folderBackStack.removeAt(folderBackStack.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = if (settings.theme == AppThemeSetting.ASGL) Color.Transparent else MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Persistent Operation Progress Bar
                SalimOperationProgressBar(
                    operation = activeOperation,
                    onCancel = { repository.cancelCurrentOperation() }
                )

                if (currentSubScreen is SubScreen.None) {
                    val isGlassTheme = settings.theme == AppThemeSetting.ASGL || !settings.reduceTransparency
                    NavigationBar(
                        modifier = Modifier
                            .testTag("salim_bottom_nav")
                            .then(
                                if (settings.theme == AppThemeSetting.ASGL) {
                                    Modifier.liquidGlass(
                                        shape = RoundedCornerShape(0.dp),
                                        transparency = settings.glassTransparency,
                                        reduceTransparency = settings.reduceTransparency,
                                        isDarkOrAsgl = true
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        containerColor = if (settings.theme == AppThemeSetting.ASGL) Color.Transparent else MaterialTheme.colorScheme.surface
                    ) {
                        SalimTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = { Text(tab.title) },
                                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Optional Permission Guidance Banner (Only shown once if not yet granted on Android 11+)
            if (!hasFullStoragePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Grant All Files Access for full device storage browsing",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Text("Grant", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Screen Content Routing
            Box(modifier = Modifier.fillMaxSize()) {
                when (val sub = currentSubScreen) {
                    is SubScreen.DuplicateFinder -> {
                        DuplicateFinderScreen(
                            repository = repository,
                            onNavigateBack = { currentSubScreen = SubScreen.None }
                        )
                    }
                    is SubScreen.LargeFileFinder -> {
                        LargeFileFinderScreen(
                            repository = repository,
                            onNavigateBack = { currentSubScreen = SubScreen.None },
                            onRevealInFolder = { folder ->
                                currentSubScreen = SubScreen.None
                                currentTab = SalimTab.BROWSE
                                folderBackStack.clear()
                                folderBackStack.add(rootDir)
                                if (folder != rootDir) folderBackStack.add(folder)
                            }
                        )
                    }
                    is SubScreen.EmptyFolderFinder -> {
                        EmptyFolderFinderScreen(
                            repository = repository,
                            onNavigateBack = { currentSubScreen = SubScreen.None }
                        )
                    }
                    is SubScreen.CategoryFiles -> {
                        CategoryFilesScreen(
                            categoryType = sub.category,
                            categoryTitle = sub.title,
                            repository = repository,
                            onNavigateBack = { currentSubScreen = SubScreen.None }
                        )
                    }
                    is SubScreen.Bookmarks -> {
                        BookmarksScreen(
                            repository = repository,
                            onNavigateToFolder = { folder ->
                                currentSubScreen = SubScreen.None
                                currentTab = SalimTab.BROWSE
                                folderBackStack.clear()
                                folderBackStack.add(rootDir)
                                if (folder != rootDir) folderBackStack.add(folder)
                            }
                        )
                    }
                    is SubScreen.Network -> {
                        NetworkScreen(
                            repository = repository
                        )
                    }
                    is SubScreen.Vault -> {
                        PrivateVaultScreen(
                            repository = repository,
                            settingsRepository = settingsRepository
                        )
                    }
                    is SubScreen.Settings -> {
                        SettingsScreen(
                            repository = repository,
                            settingsRepository = settingsRepository,
                            onNavigateBack = { currentSubScreen = SubScreen.None }
                        )
                    }
                    is SubScreen.None -> {
                        when (currentTab) {
                            SalimTab.BROWSE -> {
                                BrowseScreen(
                                    repository = repository,
                                    settingsRepository = settingsRepository,
                                    currentFolder = currentFolder,
                                    onNavigateToFolder = { folder ->
                                        folderBackStack.add(folder)
                                    },
                                    onNavigateUp = {
                                        if (folderBackStack.size > 1) {
                                            folderBackStack.removeAt(folderBackStack.size - 1)
                                        }
                                    },
                                    canGoBack = folderBackStack.size > 1,
                                    onOpenSettings = {
                                        currentSubScreen = SubScreen.Settings
                                    },
                                    onOpenBookmarks = {
                                        currentSubScreen = SubScreen.Bookmarks
                                    },
                                    onOpenTrash = {
                                        currentTab = SalimTab.TRASH
                                    },
                                    onOpenVault = {
                                        currentSubScreen = SubScreen.Vault
                                    },
                                    onOpenNetwork = {
                                        currentSubScreen = SubScreen.Network
                                    }
                                )
                            }
                            SalimTab.STORAGE -> {
                                StorageScreen(
                                    repository = repository,
                                    onOpenCategory = { cat, title ->
                                        currentSubScreen = SubScreen.CategoryFiles(cat, title)
                                    },
                                    onOpenDuplicateFinder = {
                                        currentSubScreen = SubScreen.DuplicateFinder
                                    },
                                    onOpenLargeFileFinder = {
                                        currentSubScreen = SubScreen.LargeFileFinder
                                    },
                                    onOpenEmptyFolderFinder = {
                                        currentSubScreen = SubScreen.EmptyFolderFinder
                                    }
                                )
                            }
                            SalimTab.APPS -> {
                                AppManagerScreen(repository = repository)
                            }
                            SalimTab.TRASH -> {
                                TrashScreen(
                                    repository = repository,
                                    settingsRepository = settingsRepository
                                )
                            }
                            SalimTab.MORE -> {
                                MoreMenuScreen(
                                    onOpenBookmarks = { currentSubScreen = SubScreen.Bookmarks },
                                    onOpenNetwork = { currentSubScreen = SubScreen.Network },
                                    onOpenVault = { currentSubScreen = SubScreen.Vault },
                                    onOpenSettings = { currentSubScreen = SubScreen.Settings }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreMenuScreen(
    onOpenBookmarks: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("More Features", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MoreMenuItemCard(
                title = "Bookmarks & Favorites",
                subtitle = "Quick access to your saved files and directories",
                icon = Icons.Default.Bookmark,
                onClick = onOpenBookmarks
            )

            MoreMenuItemCard(
                title = "Network Storage",
                subtitle = "Connect to SMB, FTP, SFTP, and WebDAV servers",
                icon = Icons.Default.CloudQueue,
                onClick = onOpenNetwork
            )

            MoreMenuItemCard(
                title = "Private Vault",
                subtitle = "PIN-protected encrypted storage for secret files",
                icon = Icons.Default.Lock,
                onClick = onOpenVault
            )

            MoreMenuItemCard(
                title = "Settings & Customization",
                subtitle = "Themes, view preferences, retention, and backup",
                icon = Icons.Default.Settings,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
fun MoreMenuItemCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
