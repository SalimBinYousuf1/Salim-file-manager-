package com.example.data.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Implements Part A: Raw Touch Mechanics
 * - ACTION_DOWN -> instant 0.97x scale
 * - Release < 200ms & < 8dp movement -> Tap registered
 * - 500ms down & < 8dp movement -> Peek phase: 1.05x scale + subtle shadow lift
 * - 1150ms (1.15s) continuous hold -> CRUD Menu trigger + light impact haptic
 * - If lifted before 1150ms -> springs back to 1.0x, peek cancels
 * - If movement > 8dp -> cancel gesture
 */
fun Modifier.salimRawTouchItem(
    onTap: () -> Unit,
    onLongPress1150ms: () -> Unit,
    onLongPressDragSelect: (() -> Unit)? = null
): Modifier = composed {
    val view = LocalView.current
    val density = LocalDensity.current
    val slopPx = with(density) { 8.dp.toPx() }

    val scaleAnim = remember { Animatable(1.0f) }
    val elevationAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    this
        .scale(scaleAnim.value)
        .shadow(elevationAnim.value.dp)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Main, requireUnconsumed = false)
                val startPos = down.position
                val startTime = System.currentTimeMillis()

                // 1. Instant 0.97x scale feedback on ACTION_DOWN
                coroutineScope.launch {
                    scaleAnim.animateTo(0.97f, spring(stiffness = Spring.StiffnessHigh))
                }

                var isPeeking = false
                var menuTriggered = false
                var cancelled = false
                var dragAfterMenu = false

                // Timer job for 500ms Peek and 1150ms CRUD Menu
                val timerJob: Job = coroutineScope.launch {
                    // Wait for 500ms peek phase
                    delay(500)
                    isPeeking = true
                    // Peek: 1.05x scale-up with subtle shadow lift
                    scaleAnim.animateTo(
                        1.05f,
                        spring(dampingRatio = 0.86f, stiffness = 380f)
                    )
                    elevationAnim.animateTo(
                        6f,
                        spring(dampingRatio = 0.86f, stiffness = 380f)
                    )

                    // Wait for remaining 650ms to reach exact 1.15s (1150ms total)
                    delay(650)
                    menuTriggered = true
                    // Light impact haptic
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // If user immediately drags or lifts, we either select or show CRUD
                }

                // Track pointer movements
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                    val totalMovement = (change.position - startPos).getDistance()

                    if (!menuTriggered && totalMovement > slopPx) {
                        // Moved > 8dp before 1.15s -> cancel gesture (scroll/drag)
                        cancelled = true
                        timerJob.cancel()
                        break
                    }

                    if (menuTriggered && totalMovement > slopPx) {
                        // User started dragging after 1.15s mark!
                        dragAfterMenu = true
                    }

                    if (!change.pressed) {
                        // Finger lifted
                        val duration = System.currentTimeMillis() - startTime
                        timerJob.cancel()

                        if (!cancelled) {
                            if (duration < 200 && totalMovement <= slopPx) {
                                // Tap!
                                onTap()
                            } else if (menuTriggered) {
                                if (dragAfterMenu && onLongPressDragSelect != null) {
                                    onLongPressDragSelect()
                                } else {
                                    onLongPress1150ms()
                                }
                            }
                        }
                        break
                    }
                }

                timerJob.cancel()
                // Spring back to 1.0x & zero elevation
                coroutineScope.launch {
                    scaleAnim.animateTo(
                        1.0f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                    )
                    elevationAnim.animateTo(0f)
                }
            }
        }
}

/**
 * Swipe-to-reveal row modifier
 * Elastic resistance formula: resisted = raw / (1 + raw / 200dp)
 */
class SwipeRevealState(
    val maxRevealWidthPx: Float
) {
    var offsetX = Animatable(0f)
    var isRevealed by mutableStateOf(false)

    suspend fun reset() {
        offsetX.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 380f))
        isRevealed = false
    }

    suspend fun reveal() {
        offsetX.animateTo(-maxRevealWidthPx, spring(dampingRatio = 0.86f, stiffness = 380f))
        isRevealed = true
    }
}
