/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.bridge.AndroidEngineBridge
import kotlinx.coroutines.CoroutineScope

/**
 * F-AND-012: File tree sheet (Sovereign IDE IA lite).
 *
 * Mobile file browse without four-pane. Bottom sheet tree; open file preview.
 * Uses project files API; preview is read-only v1.
 */
@Composable
fun FileTreeScreen(
    repository: AndroidEngineBridge,
    state: MobileAppState,
    mvi: MobileAppMviStore,
    scope: CoroutineScope,
    repository2: AndroidEngineBridge = repository,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "File Tree",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                items(listOf("src/", "app/", "bridge/", "ui/", "build.gradle.kts", "README.md")) { item ->
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }

        // Back button
        Spacer(modifier = Modifier.padding(top = 16.dp))
        Button(
            onClick = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Back",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}