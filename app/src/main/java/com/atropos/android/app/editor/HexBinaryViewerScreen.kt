/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.editor

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewColumn
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
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * Endianness display mode for binary analysis.
 */
enum class ByteEndianness {
    BIG_ENDIAN,
    LITTLE_ENDIAN
}

/**
 * Sample binary payload presets for engine bytecode inspection.
 */
enum class BinaryPreset(val label: String, val description: String) {
    DEX_HEADER(
        "Android DEX Header",
        "Magic bytes 'dex\\n039\\0' followed by checksum, SHA-1 signature, and header size."
    ),
    JAVA_CLASS(
        "JVM Classfile",
        "Standard Java 0xCAFEBABE magic bytes followed by major/minor version 65.0."
    ),
    MERKLE_ROOT(
        "SpecGraph Merkle Leaf",
        "SHA-256 cryptographic digest binding state integrity proofs."
    ),
    CUSTOM_TEXT(
        "UTF-8 ASCII Buffer",
        "Interactive UTF-8 payload encoding Atropos sovereign governance declaration."
    )
}

/**
 * Utility functions for hex formatting, pattern matching, and bit manipulation.
 */
object HexBinaryUtils {

    fun generatePresetBytes(preset: BinaryPreset): ByteArray {
        return when (preset) {
            BinaryPreset.DEX_HEADER -> byteArrayOf(
                0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x39, 0x00,
                0x12, 0x34, 0x56, 0x78.toByte(), 0x9a.toByte(), 0xbc.toByte(), 0xde.toByte(), 0xf0.toByte(),
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x10, 0x00,
                0x70, 0x00, 0x00, 0x00, 0x78, 0x56, 0x34, 0x12
            )
            BinaryPreset.JAVA_CLASS -> byteArrayOf(
                0xca.toByte(), 0xfe.toByte(), 0xba.toByte(), 0xbe.toByte(),
                0x00, 0x00, 0x00, 0x41, 0x00, 0x22, 0x07, 0x00,
                0x02, 0x01, 0x00, 0x18, 0x61, 0x74, 0x72, 0x6f,
                0x70, 0x6f, 0x73, 0x2f, 0x4d, 0x61, 0x69, 0x6e
            )
            BinaryPreset.MERKLE_ROOT -> {
                val input = "Atropos Sovereign Merkle Leaf: 2026-10-22T20:56:53Z".toByteArray(Charsets.UTF_8)
                MessageDigest.getInstance("SHA-256").digest(input)
            }
            BinaryPreset.CUSTOM_TEXT -> {
                "Sovereign Engine Bytecode & AST Integrity Ledger - Strict Local Autonomy".toByteArray(Charsets.UTF_8)
            }
        }
    }

    fun byteToHex(b: Byte): String {
        return String.format("%02X", b.toInt() and 0xFF)
    }

    fun byteToBinary(b: Byte): String {
        val intVal = b.toInt() and 0xFF
        return String.format("%8s", Integer.toBinaryString(intVal)).replace(' ', '0')
    }

    fun byteToAscii(b: Byte): Char {
        val intVal = b.toInt() and 0xFF
        return if (intVal in 32..126) intVal.toChar() else '·'
    }

    fun findMatches(bytes: ByteArray, query: String): List<Int> {
        if (query.isBlank()) return emptyList()
        val cleanQuery = query.replace(" ", "").trim()

        val matches = mutableListOf<Int>()

        // 1. Check if valid hex query
        val isHex = cleanQuery.length % 2 == 0 && cleanQuery.all { it in "0123456789abcdefABCDEF" }
        if (isHex && cleanQuery.isNotEmpty()) {
            val queryBytes = cleanQuery.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            for (i in 0..(bytes.size - queryBytes.size)) {
                var found = true
                for (j in queryBytes.indices) {
                    if (bytes[i + j] != queryBytes[j]) {
                        found = false
                        break
                    }
                }
                if (found) {
                    for (k in queryBytes.indices) {
                        matches.add(i + k)
                    }
                }
            }
        }

        // 2. Also check ASCII text query if no hex match or non-hex
        if (matches.isEmpty()) {
            val textQuery = query.trim()
            val textBytes = textQuery.toByteArray(Charsets.UTF_8)
            if (textBytes.isNotEmpty()) {
                for (i in 0..(bytes.size - textBytes.size)) {
                    var found = true
                    for (j in textBytes.indices) {
                        if (bytes[i + j] != textBytes[j]) {
                            found = false
                            break
                        }
                    }
                    if (found) {
                        for (k in textBytes.indices) {
                            matches.add(i + k)
                        }
                    }
                }
            }
        }

        return matches.distinct().sorted()
    }
}

/**
 * Sovereign Mobile Hex & Binary Bytecode Viewer Screen.
 *
 * Implements high-density raw memory inspection with:
 * - 8 or 16 column hex dump with offset and ASCII translation
 * - Interactive offset selector with active bitwise inspector
 * - Bit flipping capability for live binary mutation experiments
 * - Preset selector (DEX, Classfile, Merkle root, ASCII buffer)
 * - Dual hex/ASCII pattern search engine with match navigation
 */
@Composable
fun HexBinaryViewerScreen(
    modifier: Modifier = Modifier,
    initialPreset: BinaryPreset = BinaryPreset.DEX_HEADER
) {
    val colors = LocalCyberHudColors.current
    val coroutineScope = rememberCoroutineScope()

    var activePreset by remember { mutableStateOf(initialPreset) }
    var rawBytes by remember { mutableStateOf(HexBinaryUtils.generatePresetBytes(initialPreset)) }
    var bytesPerRow by remember { mutableIntStateOf(16) }
    var endianness by remember { mutableStateOf(ByteEndianness.BIG_ENDIAN) }
    var selectedOffset by remember { mutableStateOf<Int?>(0) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableIntStateOf(0) }

    val searchMatches by remember(rawBytes, searchQuery) {
        derivedStateOf { HexBinaryUtils.findMatches(rawBytes, searchQuery) }
    }

    val listState = rememberLazyListState()

    val totalRows = (rawBytes.size + bytesPerRow - 1) / bytesPerRow

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.voidAbyss)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Toolbar: Header & Status
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.accentCyan),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = colors.accentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HEX & BYTECODE INSPECTOR",
                            style = AtroposTypography.monoTitle,
                            color = colors.accentCyan
                        )
                    }

                    // Byte Count Badge
                    Box(
                        modifier = Modifier
                            .background(colors.accentCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, colors.accentCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${rawBytes.size} BYTES",
                            style = AtroposTypography.monoToken,
                            color = colors.accentCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Presets Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BinaryPreset.values().forEach { preset ->
                        val isSelected = activePreset == preset
                        Button(
                            onClick = {
                                activePreset = preset
                                rawBytes = HexBinaryUtils.generatePresetBytes(preset)
                                selectedOffset = 0
                                searchQuery = ""
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) colors.accentCyan.copy(alpha = 0.25f) else colors.panelSurface,
                                contentColor = if (isSelected) colors.accentCyan else colors.textSecondary
                            ),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset.label,
                                style = AtroposTypography.monoToken,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Density & Endianness Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { bytesPerRow = if (bytesPerRow == 16) 8 else 16 },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.panelSurface),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewColumn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = colors.accentPurple
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${bytesPerRow} COLS",
                                style = AtroposTypography.monoToken,
                                color = colors.accentPurple
                            )
                        }

                        Button(
                            onClick = {
                                endianness = if (endianness == ByteEndianness.BIG_ENDIAN) {
                                    ByteEndianness.LITTLE_ENDIAN
                                } else {
                                    ByteEndianness.BIG_ENDIAN
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.panelSurface),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flip,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = colors.neonAmber
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (endianness == ByteEndianness.BIG_ENDIAN) "BIG-ENDIAN" else "LIL-ENDIAN",
                                style = AtroposTypography.monoToken,
                                color = colors.neonAmber
                            )
                        }
                    }

                    // Reset button
                    Button(
                        onClick = {
                            rawBytes = HexBinaryUtils.generatePresetBytes(activePreset)
                            selectedOffset = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.panelSurface),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset buffer",
                            modifier = Modifier.size(14.dp),
                            tint = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RESET",
                            style = AtroposTypography.monoToken,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        // Search Engine Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.borderSubtle),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        activeMatchIndex = 0
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Search Hex (e.g. 'CA FE') or ASCII ('dex')",
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
                                    imageVector = Icons.Default.CleaningServices,
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

                if (searchMatches.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${activeMatchIndex + 1}/${searchMatches.size}",
                        style = AtroposTypography.monoToken,
                        color = colors.accentGreen
                    )
                    IconButton(
                        onClick = {
                            if (activeMatchIndex > 0) {
                                activeMatchIndex--
                            } else {
                                activeMatchIndex = searchMatches.size - 1
                            }
                            val targetOffset = searchMatches[activeMatchIndex]
                            selectedOffset = targetOffset
                            coroutineScope.launch {
                                listState.animateScrollToItem(targetOffset / bytesPerRow)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Match",
                            tint = colors.accentCyan
                        )
                    }
                    IconButton(
                        onClick = {
                            if (activeMatchIndex < searchMatches.size - 1) {
                                activeMatchIndex++
                            } else {
                                activeMatchIndex = 0
                            }
                            val targetOffset = searchMatches[activeMatchIndex]
                            selectedOffset = targetOffset
                            coroutineScope.launch {
                                listState.animateScrollToItem(targetOffset / bytesPerRow)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Match",
                            tint = colors.accentCyan
                        )
                    }
                }
            }
        }

        // Virtualized Hex & ASCII Stream Table
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.borderSubtle),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(totalRows) { rowIndex ->
                    val rowStart = rowIndex * bytesPerRow
                    val rowLength = minOf(bytesPerRow, rawBytes.size - rowStart)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selectedOffset != null && selectedOffset!! in rowStart until (rowStart + rowLength))
                                    colors.accentCyan.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Offset Column
                        Text(
                            text = String.format("%08X: ", rowStart),
                            style = AtroposTypography.monoBody.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.accentCyan
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Hex Bytes Columns
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (col in 0 until bytesPerRow) {
                                val byteIndex = rowStart + col
                                if (byteIndex < rawBytes.size) {
                                    val b = rawBytes[byteIndex]
                                    val isSelected = selectedOffset == byteIndex
                                    val isMatch = searchMatches.contains(byteIndex)

                                    val bg = when {
                                        isSelected -> colors.accentCyan.copy(alpha = 0.4f)
                                        isMatch -> colors.neonAmber.copy(alpha = 0.35f)
                                        else -> Color.Transparent
                                    }

                                    val textColor = when {
                                        isSelected -> colors.accentCyan
                                        isMatch -> colors.neonAmber
                                        b.toInt() == 0 -> colors.textDisabled
                                        else -> colors.textPrimary
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(bg, RoundedCornerShape(2.dp))
                                            .clickable { selectedOffset = byteIndex }
                                            .padding(horizontal = 2.dp)
                                    ) {
                                        Text(
                                            text = HexBinaryUtils.byteToHex(b),
                                            style = AtroposTypography.monoBody.copy(
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = if (isSelected || isMatch) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = textColor
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "  ",
                                        style = AtroposTypography.monoBody.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                        color = Color.Transparent
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // ASCII Column
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            for (col in 0 until rowLength) {
                                val byteIndex = rowStart + col
                                val b = rawBytes[byteIndex]
                                val isSelected = selectedOffset == byteIndex
                                val isMatch = searchMatches.contains(byteIndex)

                                val textColor = when {
                                    isSelected -> colors.accentCyan
                                    isMatch -> colors.neonAmber
                                    else -> colors.accentGreen
                                }

                                Text(
                                    text = HexBinaryUtils.byteToAscii(b).toString(),
                                    style = AtroposTypography.monoBody.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected || isMatch) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = textColor,
                                    modifier = Modifier.clickable { selectedOffset = byteIndex }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Byte & Bit Inspector Panel
        selectedOffset?.let { offset ->
            if (offset in rawBytes.indices) {
                val currentByte = rawBytes[offset]
                val unsignedVal = currentByte.toInt() and 0xFF
                val binaryString = HexBinaryUtils.byteToBinary(currentByte)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cyberGlass(backgroundColor = colors.panelSurface, borderColor = colors.accentCyan),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FindInPage,
                                    contentDescription = null,
                                    tint = colors.accentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "OFFSET 0x${String.format("%04X", offset)} (${offset}d)",
                                    style = AtroposTypography.monoToken,
                                    color = colors.accentCyan
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "HEX: 0x${HexBinaryUtils.byteToHex(currentByte)}",
                                    style = AtroposTypography.monoToken,
                                    color = colors.neonAmber
                                )
                                Text(
                                    text = "DEC: $unsignedVal",
                                    style = AtroposTypography.monoToken,
                                    color = colors.accentGreen
                                )
                                Text(
                                    text = "CHAR: '${HexBinaryUtils.byteToAscii(currentByte)}'",
                                    style = AtroposTypography.monoToken,
                                    color = colors.textPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bit Manipulation Grid (Bit 7 down to Bit 0)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BITS:",
                                style = AtroposTypography.monoToken,
                                color = colors.textSecondary
                            )

                            for (bit in 7 downTo 0) {
                                val isSet = (currentByte.toInt() and (1 shl bit)) != 0
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isSet) colors.accentGreen.copy(alpha = 0.25f) else colors.panelSurface,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSet) colors.accentGreen else colors.borderSubtle,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable {
                                            // Toggle bit in mutable byte array
                                            val newBytes = rawBytes.clone()
                                            newBytes[offset] = (currentByte.toInt() xor (1 shl bit)).toByte()
                                            rawBytes = newBytes
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "b$bit",
                                            style = AtroposTypography.monoBody.copy(fontSize = 9.sp),
                                            color = colors.textDisabled
                                        )
                                        Text(
                                            text = if (isSet) "1" else "0",
                                            style = AtroposTypography.monoTitle.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isSet) colors.accentGreen else colors.textSecondary
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
}
