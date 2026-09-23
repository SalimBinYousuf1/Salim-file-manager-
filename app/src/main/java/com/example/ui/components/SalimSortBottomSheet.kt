package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SortField
import com.example.data.model.SortPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalimSortBottomSheet(
    currentSort: SortPreference,
    onDismiss: () -> Unit,
    onSortChanged: (SortPreference) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("sort_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(Modifier.height(16.dp))

            SortField.values().forEach { field ->
                val isSelected = currentSort.field == field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelected) {
                                // Toggle direction if already selected
                                onSortChanged(currentSort.copy(isAscending = !currentSort.isAscending))
                            } else {
                                onSortChanged(currentSort.copy(field = field))
                            }
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when (field) {
                            SortField.NAME -> "Name"
                            SortField.DATE_MODIFIED -> "Date Modified"
                            SortField.DATE_CREATED -> "Date Created"
                            SortField.SIZE -> "Size"
                            SortField.TYPE -> "Type"
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    if (isSelected) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (currentSort.isAscending) "Ascending" else "Descending",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = if (currentSort.isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = "Toggle direction",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Folders always first toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSortChanged(currentSort.copy(foldersFirst = !currentSort.foldersFirst))
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Folders always first",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Show directories at the top regardless of sort field",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = currentSort.foldersFirst,
                    onCheckedChange = { checked ->
                        onSortChanged(currentSort.copy(foldersFirst = checked))
                    }
                )
            }
        }
    }
}
