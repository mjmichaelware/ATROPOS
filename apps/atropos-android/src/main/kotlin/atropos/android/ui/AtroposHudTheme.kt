/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class AtroposColorPalette(
    val canvas: Color = Color(0xFF07100D),
    val surface: Color = Color(0xFF0F172A),
    val surfaceBorder: Color = Color(0x14FFFFFF),
    val accentEmerald: Color = Color(0xFF10B981),
    val accentAmber: Color = Color(0xFFF59E0B),
    val accentRuby: Color = Color(0xFFEF4444),
    val accentCobalt: Color = Color(0xFF3B82F6)
)

data class EngineSixAnswers(
    val problem: String = "",
    val approach: String = "",
    val invariants: String = "",
    val changes: String = "",
    val verification: String = "",
    val confidence: Double = 1.0
)

data class EvidenceRef(
    val hash: String,
    val description: String,
    val verified: Boolean
)

@Composable
fun AtroposHudTheme(content: @Composable () -> Unit) {
    val palette = AtroposColorPalette()
    val colorScheme = darkColorScheme(
        background = palette.canvas,
        surface = palette.surface,
        primary = palette.accentEmerald,
        secondary = palette.accentCobalt,
        error = palette.accentRuby
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun SixAnswersRadarCard(
    answers: EngineSixAnswers,
    onInspectClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        val confidence = answers.confidence.toFloat()
        DecoupledHardwareVectorGraphicsRenderer.HardwareAcceleratedRadarCanvas(
            scores = listOf(confidence, confidence * 0.9f, confidence, confidence * 0.85f, confidence),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SovereignEvidenceDrawer(
    evidenceRefs: List<EvidenceRef>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier)
}

@Composable
fun NativeTerminalViewport(
    terminalSocketUrl: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07100D))
    )
}
