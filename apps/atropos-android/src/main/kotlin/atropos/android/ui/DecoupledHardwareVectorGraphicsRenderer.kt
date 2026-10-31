/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * ATOM 25.2: Hardware-Accelerated SVG and Motion Graphics Engine.
 *
 * Renders radar status diagrams, DAG graphs, and telemetry meters using
 * hardware-accelerated Compose Canvas draw calls and optimized vector graphics.
 *
 * Research Directive: Path morphing vector animation engines in Compose for
 * smooth transitions between graph layouts and radar status displays.
 */
object DecoupledHardwareVectorGraphicsRenderer {

    @Composable
    fun HardwareAcceleratedRadarCanvas(
        scores: List<Float>,
        accentColor: Color = Color(0xFF10B981),
        modifier: Modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Canvas(modifier = modifier) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2.5f

            // Draw concentric radar boundary rings
            drawCircle(
                color = Color(0x33FFFFFF),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5f)
            )
            drawCircle(
                color = Color(0x1AFFFFFF),
                radius = radius * 0.5f,
                center = center,
                style = Stroke(width = 1.0f)
            )

            // Draw polygon vertices based on dynamic scores
            if (scores.size >= 3) {
                val path = Path()
                val step = (2.0 * Math.PI) / scores.size
                scores.forEachIndexed { i, score ->
                    val angle = i * step - Math.PI / 2.0
                    val r = radius * score.coerceIn(0f, 1f)
                    val x = (center.x + r * Math.cos(angle)).toFloat()
                    val y = (center.y + r * Math.sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path = path, color = accentColor.copy(alpha = 0.25f))
                drawPath(path = path, color = accentColor, style = Stroke(width = 2.5f))
            }
        }
    }
}
