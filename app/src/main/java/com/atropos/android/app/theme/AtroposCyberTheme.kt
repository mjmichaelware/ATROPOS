/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ultra-HIG Cyber-HUD design tokens and amorphic glassmorphism system for ATROPOS Android.
 *
 * Designed to provide the density and ergonomic precision of an advanced IDE,
 * with the ambient intelligence and aesthetics of a sovereign autonomous console.
 */
@Immutable
data class CyberHudColors(
    val voidAbyss: Color = Color(0xFF07090E),
    val panelSurface: Color = Color(0xFF0F131D),
    val cardSurface: Color = Color(0xFF161C2B),
    val overlayGlass: Color = Color(0xCC0F1424),
    val borderDim: Color = Color(0xFF1F293D),
    val borderSubtle: Color = Color(0x3338BDF8),
    val borderGlow: Color = Color(0x8000F0FF),
    val accentCyan: Color = Color(0xFF00F0FF),
    val accentEmerald: Color = Color(0xFF00E676),
    val accentAmber: Color = Color(0xFFFFB800),
    val accentRuby: Color = Color(0xFFFF1744),
    val accentViolet: Color = Color(0xFFA855F7),
    val accentCobalt: Color = Color(0xFF3B82F6),
    val textPrimary: Color = Color(0xFFF8FAFC),
    val textSecondary: Color = Color(0xFF94A3B8),
    val textMuted: Color = Color(0xFF64748B),
    val gutterBackground: Color = Color(0xFF0B0E17),
    val diffAddBackground: Color = Color(0x1F00E676),
    val diffRemoveBackground: Color = Color(0x1FFF1744)
)

val LocalCyberHudColors = staticCompositionLocalOf { CyberHudColors() }

@Composable
fun AtroposCyberTheme(
    content: @Composable () -> Unit
) {
    val hudColors = CyberHudColors()

    val darkScheme = darkColorScheme(
        primary = hudColors.accentCyan,
        onPrimary = hudColors.voidAbyss,
        primaryContainer = hudColors.cardSurface,
        onPrimaryContainer = hudColors.textPrimary,
        secondary = hudColors.accentViolet,
        onSecondary = Color.White,
        tertiary = hudColors.accentEmerald,
        background = hudColors.voidAbyss,
        onBackground = hudColors.textPrimary,
        surface = hudColors.panelSurface,
        onSurface = hudColors.textPrimary,
        surfaceVariant = hudColors.cardSurface,
        onSurfaceVariant = hudColors.textSecondary,
        outline = hudColors.borderDim,
        error = hudColors.accentRuby,
        onError = Color.White
    )

    CompositionLocalProvider(
        LocalCyberHudColors provides hudColors
    ) {
        MaterialTheme(
            colorScheme = darkScheme,
            content = content
        )
    }
}

/**
 * High-performance amorphic glass modifier.
 * Creates an acrylic frosted glass border with subtle ambient gradients.
 */
fun Modifier.cyberGlass(
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = Color(0xD90F1422),
    borderColor: Color = Color(0x3300F0FF),
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .background(backgroundColor, shape)
    .border(borderWidth, borderColor, shape)

/**
 * Neon pulse glow border for active computational nodes and running trajectories.
 */
@Composable
fun Modifier.neonPulseBorder(
    activeColor: Color = Color(0xFF00F0FF),
    shape: Shape = RoundedCornerShape(12.dp),
    borderWidth: Dp = 1.5.dp
): Modifier {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonAlpha"
    )

    return this
        .clip(shape)
        .border(
            width = borderWidth,
            brush = Brush.sweepGradient(
                listOf(
                    activeColor.copy(alpha = alpha),
                    activeColor.copy(alpha = alpha * 0.2f),
                    activeColor.copy(alpha = alpha)
                )
            ),
            shape = shape
        )
}

/**
 * Matrix background grid canvas with subtle perspective lines.
 */
fun Modifier.cyberGridBackground(
    gridColor: Color = Color(0x0C00F0FF),
    gridSpacing: Dp = 24.dp
): Modifier = this.drawBehind {
    val spacingPx = gridSpacing.toPx()
    var x = 0f
    while (x <= size.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
        x += spacingPx
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += spacingPx
    }
}
