package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeableActionRow(
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val maxRevealWidthPx = with(density) { 180.dp.toPx() } // 3 buttons * 60dp
    val elasticDenominatorDp = with(density) { 200.dp.toPx() }

    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        // Trailing revealed action buttons (Rename, Move, Delete)
        Row(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    coroutineScope.launch { offsetX.animateTo(0f, spring(0.86f, 380f)) }
                    onRename()
                },
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    Icons.Default.DriveFileRenameOutline,
                    contentDescription = "Rename",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            IconButton(
                onClick = {
                    coroutineScope.launch { offsetX.animateTo(0f, spring(0.86f, 380f)) }
                    onMove()
                },
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Icon(
                    Icons.Default.DriveFileMove,
                    contentDescription = "Move",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            IconButton(
                onClick = {
                    coroutineScope.launch { offsetX.animateTo(0f, spring(0.86f, 380f)) }
                    onDelete()
                },
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.errorContainer)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Foreground content with elastic resistance
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value < -maxRevealWidthPx / 2) {
                                    offsetX.animateTo(-maxRevealWidthPx, spring(0.86f, 380f))
                                } else {
                                    offsetX.animateTo(0f, spring(0.86f, 380f))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, spring(0.86f, 380f))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val current = offsetX.value
                            val newRaw = current + dragAmount
                            // Resistance formula: resisted delta = raw / (1 + raw / 200dp)
                            val target = if (newRaw < -maxRevealWidthPx) {
                                val overflow = abs(newRaw) - maxRevealWidthPx
                                val resistedOverflow = overflow / (1f + overflow / elasticDenominatorDp)
                                -(maxRevealWidthPx + resistedOverflow)
                            } else if (newRaw > 0) {
                                0f
                            } else {
                                newRaw
                            }
                            coroutineScope.launch {
                                offsetX.snapTo(target)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
