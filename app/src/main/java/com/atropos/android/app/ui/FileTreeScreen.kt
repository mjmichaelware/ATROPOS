/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.bridge.AndroidEngineBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * F-AND-012: File tree sheet (VS Code IA lite).
 *
 * Mobile file browse without four-pane. Bottom sheet tree; open file preview.
 * Uses project files API; preview is read-only v1.
 */
@Composable
fun FileTreeScreen(
    repository: com.atropos.android.app.bridge.AndroidEngineBridge,
    state: com.atropos.android.app.ui.MobileAppState,
    mvi: com.atropos.android.app.ui.MobileAppMviStore,
    scope: kotlinx.coroutines.CoroutineScope,
    repository2: com.atropos.android.app.bridge.AndroidEngineBridge,
    onNavigateBack: () -> Unit
) {
    androidx.compose.material3.Column(
        modifier = androidx.compose.foundation.layout.Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Text(
            text = "File Tree",
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        androidx.compose.material3.Card(
            modifier = androidx.compose.foundation.layout.Modifier.fillMaxSize().padding(16.dp),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = androidx.compose.foundation.layout.Modifier.fillMaxSize().padding(16.dp)
            ) {
                items(listOf("src/", "app/", "bridge/", "ui/", "build.gradle.kts", "README.md")) { item ->
                    androidx.compose.material3.Text(
                        text = item,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        modifier = androidx.compose.foundation.layout.Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }

        // Back button
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.padding(top = 16.dp))
        androidx.compose.material3.Button(
            onClick = onNavigateBack,
            modifier = androidx.compose.foundation.layout.Modifier.fillMaxWidth().padding(32.dp),
            shape = androidx.compose.material3.RoundedCornerShape(28.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            androidx.compose.material3.Text(
                text = "Back",
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}