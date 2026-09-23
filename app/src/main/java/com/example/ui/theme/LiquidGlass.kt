package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass Material & Reactive Glass System
 * Implements tactile translucency with upper specular highlight, controlled depth,
 * and automatic fallback to solid surfaces when reduced transparency is requested.
 */
object LiquidGlassDefaults {
    val DefaultShape = RoundedCornerShape(18.dp)
    val CardShape = RoundedCornerShape(16.dp)
    val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val FloatingBarShape = RoundedCornerShape(20.dp)
}

@Composable
fun Modifier.liquidGlass(
    shape: Shape = LiquidGlassDefaults.DefaultShape,
    transparency: Float = 0.75f,
    reduceTransparency: Boolean = false,
    isDarkOrAsgl: Boolean = false,
    borderWidth: Dp = 1.dp
): Modifier {
    if (reduceTransparency) {
        // Fallback to solid surface for accessibility
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
    }

    val alpha = transparency.coerceIn(0.2f, 0.98f)
    val baseColor = MaterialTheme.colorScheme.surface
    val glassColor = baseColor.copy(alpha = alpha)

    val specularAlpha = if (isDarkOrAsgl) 0.20f else 0.40f
    val borderColor = if (isDarkOrAsgl) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    return this
        .clip(shape)
        .background(glassColor)
        .drawBehind {
            // Upper specular highlight line
            drawLine(
                color = Color.White.copy(alpha = specularAlpha),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx()
            )
        }
        .border(borderWidth, borderColor, shape)
}
