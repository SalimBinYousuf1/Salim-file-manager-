package com.example.ui.screens.apps

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppItem
import com.example.data.model.FileItem
import com.example.data.repository.FileManagerRepository
import com.example.ui.theme.ColorApk
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class AppSortMode {
    SIZE,
    NAME,
    INSTALL_DATE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    repository: FileManagerRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortMode by remember { mutableStateOf(AppSortMode.SIZE) }
    var filterSystemApps by remember { mutableStateOf(false) }

    var selectedAppForDetail by remember { mutableStateOf<AppItem?>(null) }

    fun loadApps() {
        coroutineScope.launch {
            isLoading = true
            apps = repository.getInstalledApps()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadApps()
    }

    val displayedApps = remember(apps, sortMode, filterSystemApps) {
        val filtered = if (filterSystemApps) apps else apps.filter { !it.isSystemApp }
        when (sortMode) {
            AppSortMode.SIZE -> filtered.sortedByDescending { it.totalSize }
            AppSortMode.NAME -> filtered.sortedBy { it.appName.lowercase() }
            AppSortMode.INSTALL_DATE -> filtered.sortedByDescending { it.installDate }
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("App Manager", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { loadApps() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sort and filter control row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = sortMode == AppSortMode.SIZE,
                        onClick = { sortMode = AppSortMode.SIZE },
                        label = { Text("Size") }
                    )
                    FilterChip(
                        selected = sortMode == AppSortMode.NAME,
                        onClick = { sortMode = AppSortMode.NAME },
                        label = { Text("Name") }
                    )
                    FilterChip(
                        selected = sortMode == AppSortMode.INSTALL_DATE,
                        onClick = { sortMode = AppSortMode.INSTALL_DATE },
                        label = { Text("Date") }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("System", style = MaterialTheme.typography.labelSmall)
                    Checkbox(
                        checked = filterSystemApps,
                        onCheckedChange = { filterSystemApps = it }
                    )
                }
            }

            HorizontalDivider()

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (displayedApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No applications found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(displayedApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAppForDetail = app }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Android,
                                contentDescription = null,
                                tint = ColorApk,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${app.formattedTotalSize} • v${app.versionName} • ${dateFormat.format(Date(app.installDate))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = {
                                coroutineScope.launch {
                                    val result = repository.extractApk(app.packageName)
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Extracted to Downloads: ${result.getOrThrow().name}", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Extraction failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Extract APK")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (selectedAppForDetail != null) {
        val app = selectedAppForDetail!!
        AlertDialog(
            onDismissRequest = { selectedAppForDetail = null },
            title = {
                Text(app.appName, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Package: ${app.packageName}", style = MaterialTheme.typography.bodySmall)
                    Text("Version: ${app.versionName}", style = MaterialTheme.typography.bodySmall)
                    Text("APK Size: ${FileItem.formatFileSize(app.apkSize)}", style = MaterialTheme.typography.bodySmall)
                    Text("Estimated Data: ${FileItem.formatFileSize(app.dataSize)}", style = MaterialTheme.typography.bodySmall)
                    Text("Installed: ${dateFormat.format(Date(app.installDate))}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) context.startActivity(launchIntent)
                        else Toast.makeText(context, "Cannot launch app", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Open")
                    }

                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                        Text("App Info")
                    }

                    if (!app.isSystemApp) {
                        Button(
                            onClick = {
                                selectedAppForDetail = null
                                val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.fromParts("package", app.packageName, null)
                                }
                                context.startActivity(uninstallIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Uninstall")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAppForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }
}
