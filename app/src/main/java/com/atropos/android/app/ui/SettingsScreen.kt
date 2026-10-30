/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * F-AND-001: Settings screen with explicit bridge host and port input controls.
 *
 * Exposes explicit input controls for bridge host and port with immediate validation feedback.
 * Provides connection test utilities displaying latency and failure diagnostics.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    var bridgeHost by remember { mutableStateOf(System.getenv("ATROPOS_BRIDGE_HOST") ?: "127.0.0.1") }
    var bridgePort by remember { mutableStateOf(System.getenv("ATROPOS_BRIDGE_PORT")?.toIntOrNull() ?: 4317) }
    var bridgePortText by remember { mutableStateOf(bridgePort.toString()) }
    var isConnected by remember { mutableStateOf(false) }
    var latencyMs by remember { mutableLongStateOf(0L) }
    var testStatusMessage by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val isPortValid = bridgePortText.toIntOrNull()?.let { it in 1..65535 } == true
    val isHostValid = bridgeHost.isNotBlank() && !bridgeHost.contains(" ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Engine Bridge Network",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = bridgeHost,
                    onValueChange = { bridgeHost = it.trim() },
                    label = { Text("Bridge Host / IP") },
                    isError = !isHostValid,
                    supportingText = {
                        if (!isHostValid) Text("Invalid host address")
                        else Text("Default: 127.0.0.1 (Loopback)")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = bridgePortText,
                    onValueChange = {
                        bridgePortText = it.trim()
                        it.toIntOrNull()?.let { p ->
                            if (p in 1..65535) bridgePort = p
                        }
                    },
                    label = { Text("Bridge Port") },
                    isError = !isPortValid,
                    supportingText = {
                        if (!isPortValid) Text("Must be a valid port (1-65535)")
                        else Text("Default: 4317")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (!isHostValid || !isPortValid) return@Button
                        isTesting = true
                        testStatusMessage = "Testing connection to $bridgeHost:$bridgePort..."
                        coroutineScope.launch {
                            val startTime = System.currentTimeMillis()
                            val (success, message, elapsed) = withContext(Dispatchers.IO) {
                                try {
                                    Socket().use { socket ->
                                        socket.connect(InetSocketAddress(bridgeHost, bridgePort), 2500)
                                        val end = System.currentTimeMillis()
                                        Triple(true, "Connected successfully", end - startTime)
                                    }
                                } catch (e: Exception) {
                                    val end = System.currentTimeMillis()
                                    Triple(false, "Refused or unreachable: ${e.message ?: "Connection timed out"}", end - startTime)
                                }
                            }
                            isConnected = success
                            latencyMs = elapsed
                            testStatusMessage = "$message (${elapsed}ms)"
                            isTesting = false
                        }
                    },
                    enabled = !isTesting && isHostValid && isPortValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTesting) "Testing..." else "Test Connection")
                }

                if (testStatusMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = testStatusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Runtime Environment",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Local-only: ${System.getenv("ATROPOS_LOCAL_ONLY") ?: "false"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Connected Status: ${if (isConnected) "ONLINE (${latencyMs}ms)" else "OFFLINE"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Providers",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Configure API keys in Termux environment variables or engine settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Local-only Mode",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Disables all network research planes. Local providers still work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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
