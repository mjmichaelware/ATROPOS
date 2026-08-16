/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import atropos.core.platform.PlatformDescriptor
import atropos.core.platform.PlatformHealth
import atropos.core.platform.PlatformWire

data class DesktopSurfaceSnapshot(
    val platform: String,
    val health: String,
    val capabilities: List<String>,
) {
    companion object {
        fun from(descriptor: PlatformDescriptor, health: PlatformHealth): DesktopSurfaceSnapshot =
            DesktopSurfaceSnapshot(
                platform = descriptor.name,
                health = if (health.healthy) "healthy" else "attention required",
                capabilities = descriptor.capabilities.map { it.name }.sorted(),
            )
    }
}

/** Compose Desktop presentation over the shared platform contract. */
@Composable
fun DesktopSurface(
    wire: PlatformWire = remember { PlatformWire() },
) {
    var snapshot by remember { mutableStateOf(DesktopSurfaceSnapshot.from(wire.descriptor(), wire.checkHealth())) }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("ATROPOS Desktop", style = MaterialTheme.typography.headlineMedium)
                Text("Platform: ${snapshot.platform}")
                Text("Engine health: ${snapshot.health}")
                Text("Capabilities: ${snapshot.capabilities.joinToString()}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        snapshot = DesktopSurfaceSnapshot.from(wire.descriptor(), wire.checkHealth())
                    }) {
                        Text("Refresh")
                    }
                }
            }
        }
    }
}
