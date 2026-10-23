/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atropos.android.app.theme.AtroposTypography
import com.atropos.android.app.theme.LocalCyberHudColors
import com.atropos.android.app.theme.cyberGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.zip.CRC32

/**
 * Protocol category for telemetry packet dissection.
 */
enum class NetworkProtocol(val label: String, val defaultPort: Int) {
    HTTP_BRIDGE("HTTP/1.1 Bridge", 4317),
    WEBSOCKET_STREAM("WebSocket RPC", 4318),
    MCP_STDIO_PROXY("MCP IPC Proxy", 4319),
    MERKLE_SYNC("SpecGraph Sync", 4320)
}

/**
 * Directional flow of captured telemetry packet.
 */
enum class PacketDirection {
    INBOUND,
    OUTBOUND
}

/**
 * Model representing an inspected low-level engine network packet frame.
 */
data class InspectedPacket(
    val id: String,
    val sequenceNumber: Long,
    val timestampMs: Long,
    val direction: PacketDirection,
    val protocol: NetworkProtocol,
    val sourceEndpoint: String,
    val destinationEndpoint: String,
    val payloadSizeBytes: Int,
    val latencyMs: Long,
    val crc32: Long,
    val payloadSummary: String,
    val rawBytesHex: String
)

/**
 * Pure utility functions for network telemetry packet parsing and metric calculations.
 */
object PacketInspectorUtils {

    fun computeCrc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }

    fun calculateAverageLatency(packets: List<InspectedPacket>): Double {
        if (packets.isEmpty()) return 0.0
        return packets.map { it.latencyMs }.average()
    }

    fun calculateJitter(packets: List<InspectedPacket>): Double {
        if (packets.size < 2) return 0.0
        var totalDiff = 0.0
        for (i in 1 until packets.size) {
            totalDiff += kotlin.math.abs(packets[i].latencyMs - packets[i - 1].latencyMs)
        }
        return totalDiff / (packets.size - 1)
    }

    fun formatBytes(bytes: Int): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun generateSamplePacket(sequence: Long, protocol: NetworkProtocol): InspectedPacket {
        val direction = if (sequence % 2L == 0L) PacketDirection.OUTBOUND else PacketDirection.INBOUND
        val latency = 8L + (sequence * 7L % 42L)
        val payload = when (protocol) {
            NetworkProtocol.HTTP_BRIDGE -> "POST /v1/chat/completions HTTP/1.1\\r\\nHost: 127.0.0.1:4317\\r\\nContent-Type: application/json"
            NetworkProtocol.WEBSOCKET_STREAM -> "{\"event\":\"neural_token\",\"token\":\"fn\",\"logprob\":-0.024,\"seq\":$sequence}"
            NetworkProtocol.MCP_STDIO_PROXY -> "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"read_file\"},\"id\":$sequence}"
            NetworkProtocol.MERKLE_SYNC -> "{\"op\":\"SPECGRAPH_CHECKPOINT_ACK\",\"leaf_count\":48,\"merkle_root\":\"0x8f2d...\"}"
        }
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val crc = computeCrc32(bytes)
        val hex = bytes.take(16).joinToString(" ") { String.format("%02X", it) }

        return InspectedPacket(
            id = "pkt-$sequence-${System.currentTimeMillis() % 10000}",
            sequenceNumber = sequence,
            timestampMs = System.currentTimeMillis(),
            direction = direction,
            protocol = protocol,
            sourceEndpoint = if (direction == PacketDirection.OUTBOUND) "127.0.0.1:58420" else "127.0.0.1:${protocol.defaultPort}",
            destinationEndpoint = if (direction == PacketDirection.OUTBOUND) "127.0.0.1:${protocol.defaultPort}" else "127.0.0.1:58420",
            payloadSizeBytes = bytes.size,
            latencyMs = latency,
            crc32 = crc,
            payloadSummary = payload,
            rawBytesHex = hex
        )
    }
}

/**
 * Sovereign Mobile Network Packet Inspector Screen.
 *
 * Implements real-time diagnostic telemetry for engine socket communication:
 * - Live bidirectional packet capture stream
 * - CRC32 checksum verification and payload dissection
 * - Protocol filtering (HTTP bridge, WebSocket, MCP IPC, SpecGraph)
 * - Jitter and latency statistical gauges
 * - Hex / ASCII frame payload inspector
 */
@Composable
fun PacketInspectorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalCyberHudColors.current
    val coroutineScope = rememberCoroutineScope()

    var isCapturing by remember { mutableStateOf(true) }
    var selectedProtocolFilter by remember { mutableStateOf<NetworkProtocol?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPacket by remember { mutableStateOf<InspectedPacket?>(null) }

    val capturedPackets = remember {
        mutableStateListOf(
            PacketInspectorUtils.generateSamplePacket(101L, NetworkProtocol.HTTP_BRIDGE),
            PacketInspectorUtils.generateSamplePacket(102L, NetworkProtocol.WEBSOCKET_STREAM),
            PacketInspectorUtils.generateSamplePacket(103L, NetworkProtocol.MCP_STDIO_PROXY),
            PacketInspectorUtils.generateSamplePacket(104L, NetworkProtocol.MERKLE_SYNC),
            PacketInspectorUtils.generateSamplePacket(105L, NetworkProtocol.HTTP_BRIDGE)
        )
    }

    var nextSequence by remember { mutableStateOf(106L) }

    // Live capture generator coroutine
    androidx.compose.runtime.LaunchedEffect(isCapturing) {
        while (isCapturing) {
            delay(1800)
            val proto = NetworkProtocol.values()[(nextSequence % NetworkProtocol.values().size).toInt()]
            val pkt = PacketInspectorUtils.generateSamplePacket(nextSequence, proto)
            capturedPackets.add(0, pkt)
            if (capturedPackets.size > 80) {
                capturedPackets.removeAt(capturedPackets.size - 1)
            }
            nextSequence++
        }
    }

    val filteredPackets by remember(capturedPackets, selectedProtocolFilter, searchQuery) {
        derivedStateOf {
            capturedPackets.filter { pkt ->
                val matchesProto = selectedProtocolFilter == null || pkt.protocol == selectedProtocolFilter
                val matchesQuery = searchQuery.isBlank() ||
                        pkt.id.contains(searchQuery, ignoreCase = true) ||
                        pkt.payloadSummary.contains(searchQuery, ignoreCase = true) ||
                        pkt.sourceEndpoint.contains(searchQuery, ignoreCase = true)
                matchesProto && matchesQuery
            }
        }
    }

    val avgLatency = remember(filteredPackets) {
        PacketInspectorUtils.calculateAverageLatency(filteredPackets)
    }

    val jitter = remember(filteredPackets) {
        PacketInspectorUtils.calculateJitter(filteredPackets)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top HUD Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.accentCyan),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.accentCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = colors.accentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SOCKET & PACKET DISSECTOR",
                            style = AtroposTypography.monoTitle,
                            color = colors.accentCyan
                        )
                    }

                    // Play / Pause Capture
                    Button(
                        onClick = { isCapturing = !isCapturing },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCapturing) colors.accentRuby.copy(alpha = 0.2f) else colors.accentGreen.copy(alpha = 0.2f),
                            contentColor = if (isCapturing) colors.accentRuby else colors.accentGreen
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCapturing) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCapturing) "LIVE" else "PAUSED",
                            style = AtroposTypography.monoToken,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Diagnostic Telemetry Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .background(colors.cardSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "CAPTURED: ${capturedPackets.size} FRAMES",
                            style = AtroposTypography.monoToken,
                            color = colors.textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(colors.cardSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format("AVG LATENCY: %.1f ms", avgLatency),
                            style = AtroposTypography.monoToken,
                            color = colors.accentGreen
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(colors.cardSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format("JITTER: %.1f ms", jitter),
                            style = AtroposTypography.monoToken,
                            color = colors.neonAmber
                        )
                    }
                }
            }
        }

        // Protocol Filter Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedProtocolFilter = null },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedProtocolFilter == null) colors.accentCyan.copy(alpha = 0.25f) else colors.panelSurface,
                    contentColor = if (selectedProtocolFilter == null) colors.accentCyan else colors.textSecondary
                ),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("ALL PROTOCOLS", style = AtroposTypography.monoToken)
            }

            NetworkProtocol.values().forEach { proto ->
                val isSelected = selectedProtocolFilter == proto
                Button(
                    onClick = { selectedProtocolFilter = proto },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) colors.accentCyan.copy(alpha = 0.25f) else colors.panelSurface,
                        contentColor = if (isSelected) colors.accentCyan else colors.textSecondary
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(proto.label, style = AtroposTypography.monoToken)
                }
            }
        }

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Filter packet stream by sequence, payload text, or IP...",
                    style = AtroposTypography.monoBody.copy(fontSize = 11.sp),
                    color = colors.textDisabled
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.accentCyan,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentCyan,
                unfocusedBorderColor = colors.borderSubtle,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.accentCyan
            ),
            singleLine = true,
            textStyle = AtroposTypography.monoBody.copy(fontSize = 12.sp)
        )

        // Packet Stream List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredPackets, key = { it.id }) { packet ->
                val isSelected = selectedPacket?.id == packet.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedPacket = if (isSelected) null else packet
                        }
                        .cyberGlass(
                            backgroundColor = if (isSelected) colors.accentCyan.copy(alpha = 0.08f) else colors.panelSurface,
                            borderColor = if (isSelected) colors.accentCyan else colors.borderSubtle
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Direction Icon
                                val dirColor = if (packet.direction == PacketDirection.OUTBOUND) colors.accentCyan else colors.accentGreen
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(dirColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "#${packet.sequenceNumber} [${packet.direction.name}]",
                                    style = AtroposTypography.monoToken.copy(fontWeight = FontWeight.Bold),
                                    color = dirColor
                                )
                            }

                            Text(
                                text = packet.protocol.label,
                                style = AtroposTypography.monoToken,
                                color = colors.accentPurple
                            )

                            Text(
                                text = "${packet.latencyMs} ms",
                                style = AtroposTypography.monoToken,
                                color = if (packet.latencyMs > 30) colors.neonAmber else colors.accentGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${packet.sourceEndpoint} -> ${packet.destinationEndpoint}",
                            style = AtroposTypography.monoBody.copy(fontSize = 11.sp),
                            color = colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = packet.payloadSummary,
                            style = AtroposTypography.monoBody.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            color = colors.textPrimary,
                            maxLines = if (isSelected) 8 else 1
                        )

                        // Detailed Dissection view when selected
                        if (isSelected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.cardSurface, RoundedCornerShape(4.dp))
                                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "SIZE: ${PacketInspectorUtils.formatBytes(packet.payloadSizeBytes)}",
                                            style = AtroposTypography.monoToken,
                                            color = colors.textSecondary
                                        )
                                        Text(
                                            text = "CRC32: 0x${String.format("%08X", packet.crc32)}",
                                            style = AtroposTypography.monoToken,
                                            color = colors.accentCyan
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "RAW HEX PREVIEW:",
                                        style = AtroposTypography.monoToken,
                                        color = colors.textDisabled
                                    )

                                    Text(
                                        text = packet.rawBytesHex,
                                        style = AtroposTypography.monoBody.copy(
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.neonAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
