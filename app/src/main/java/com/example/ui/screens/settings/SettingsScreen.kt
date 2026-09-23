package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.data.model.SortField
import com.example.data.model.ViewMode
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.data.repository.AppThemeSetting
import com.example.data.repository.FileManagerRepository
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    repository: FileManagerRepository,
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val settings by settingsRepository.settings.collectAsState()

    var cacheBytes by remember { mutableStateOf(repository.getThumbnailCacheSize()) }
    val excludedFolders by repository.excludedFolders.collectAsState(initial = emptyList())

    var showExcludeFolderDialog by remember { mutableStateOf(false) }
    var folderToExcludePath by remember { mutableStateOf("") }

    var showImportJsonDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section: Appearance
            SettingsSectionHeader(title = "Appearance")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppThemeSetting.values().forEach { themeOption ->
                            val label = when (themeOption) {
                                AppThemeSetting.SYSTEM -> "System"
                                AppThemeSetting.LIGHT -> "Light"
                                AppThemeSetting.DARK -> "Dark"
                                AppThemeSetting.ASGL -> "ASGL Atmosphere"
                            }
                            FilterChip(
                                selected = settings.theme == themeOption,
                                onClick = { settingsRepository.updateTheme(themeOption) },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Liquid Glass Translucency", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Adjust tactile transparency of floating bars and sheets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0.40f to "Near-Clear",
                            0.75f to "Balanced",
                            0.92f to "Near-Opaque"
                        ).forEach { (value, name) ->
                            FilterChip(
                                selected = kotlin.math.abs(settings.glassTransparency - value) < 0.08f,
                                onClick = { settingsRepository.updateGlassTransparency(value) },
                                label = { Text(name) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = "Reduce Transparency",
                        subtitle = "Fall back to solid opaque surfaces for accessibility",
                        checked = settings.reduceTransparency,
                        onCheckedChange = { settingsRepository.updateReduceTransparency(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = "Reduce Motion",
                        subtitle = "Prefer direct cuts over fluid spring animations",
                        checked = settings.reduceMotion,
                        onCheckedChange = { settingsRepository.updateReduceMotion(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = "High Contrast Mode",
                        subtitle = "Increase element borders and text contrast",
                        checked = settings.highContrast,
                        onCheckedChange = { settingsRepository.updateHighContrast(it) }
                    )
                }
            }

            // Section: Behavior & Files
            SettingsSectionHeader(title = "Behavior & Display")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsSwitchRow(
                        title = "Show Hidden Files",
                        subtitle = "Display dotfiles and system folders",
                        checked = settings.showHiddenFiles,
                        onCheckedChange = { settingsRepository.updateShowHiddenFiles(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = "Confirm Before Delete",
                        subtitle = "Ask for confirmation before moving files to Trash",
                        checked = settings.confirmBeforeDelete,
                        onCheckedChange = { settingsRepository.updateConfirmBeforeDelete(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsSwitchRow(
                        title = "Folders Always First",
                        subtitle = "Keep directories at the top in sort orders",
                        checked = settings.foldersAlwaysFirst,
                        onCheckedChange = { settingsRepository.updateFoldersAlwaysFirst(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Default View Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.defaultViewMode == ViewMode.LIST,
                            onClick = { settingsRepository.updateDefaultViewMode(ViewMode.LIST) },
                            label = { Text("List View") }
                        )
                        FilterChip(
                            selected = settings.defaultViewMode == ViewMode.GRID,
                            onClick = { settingsRepository.updateDefaultViewMode(ViewMode.GRID) },
                            label = { Text("Grid View") }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Trash Retention Period", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(7, 30, 90, -1).forEach { days ->
                            FilterChip(
                                selected = settings.trashRetentionDays == days,
                                onClick = { settingsRepository.updateTrashRetentionDays(days) },
                                label = { Text(if (days == -1) "Never" else "$days Days") }
                            )
                        }
                    }
                }
            }

            // Section: Storage & Cache
            SettingsSectionHeader(title = "Storage & Cache")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear Thumbnail Cache", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text(
                                text = "Cached media: ${FileItem.formatFileSize(cacheBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                repository.clearThumbnailCache()
                                cacheBytes = repository.getThumbnailCacheSize()
                                Toast.makeText(context, "Thumbnail cache cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }

            // Section: Backup & Restore
            SettingsSectionHeader(title = "Backup & Configuration")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val json = settingsRepository.exportSettingsJson()
                                clipboard.setText(AnnotatedString(json))
                                Toast.makeText(context, "Configuration JSON copied to clipboard", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export JSON")
                        }

                        Button(
                            onClick = {
                                importJsonText = ""
                                showImportJsonDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import JSON")
                        }
                    }
                }
            }

            // Section: About
            SettingsSectionHeader(title = "About")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Salim File Manager", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Version 1.0.0 (Production)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Exhaustive tactile file manager with 1.15s peek-and-trigger CRUD interactions, elastic swipe-to-reveal rows, local cryptographic duplicate analysis, and Trash safety retention.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showImportJsonDialog) {
        AlertDialog(
            onDismissRequest = { showImportJsonDialog = false },
            title = { Text("Import Settings JSON") },
            text = {
                OutlinedTextField(
                    value = importJsonText,
                    onValueChange = { importJsonText = it },
                    label = { Text("Paste JSON configuration") },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = settingsRepository.importSettingsJson(importJsonText)
                        showImportJsonDialog = false
                        if (success) {
                            Toast.makeText(context, "Settings restored successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Invalid JSON format", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportJsonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
