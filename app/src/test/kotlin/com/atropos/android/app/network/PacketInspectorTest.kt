/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketInspectorTest {

    @Test
    fun testComputeCrc32() {
        val data = "Hello Atropos Telemetry".toByteArray(Charsets.UTF_8)
        val crc = PacketInspectorUtils.computeCrc32(data)
        assertTrue(crc > 0)

        // CRC32 of empty bytes is 0
        assertEquals(0L, PacketInspectorUtils.computeCrc32(byteArrayOf()))
    }

    @Test
    fun testCalculateAverageLatency() {
        val p1 = PacketInspectorUtils.generateSamplePacket(1L, NetworkProtocol.HTTP_BRIDGE)
            .copy(latencyMs = 10L)
        val p2 = PacketInspectorUtils.generateSamplePacket(2L, NetworkProtocol.WEBSOCKET_STREAM)
            .copy(latencyMs = 20L)
        val p3 = PacketInspectorUtils.generateSamplePacket(3L, NetworkProtocol.MCP_STDIO_PROXY)
            .copy(latencyMs = 30L)

        val avg = PacketInspectorUtils.calculateAverageLatency(listOf(p1, p2, p3))
        assertEquals(20.0, avg, 0.001)

        val emptyAvg = PacketInspectorUtils.calculateAverageLatency(emptyList())
        assertEquals(0.0, emptyAvg, 0.001)
    }

    @Test
    fun testCalculateJitter() {
        val p1 = PacketInspectorUtils.generateSamplePacket(1L, NetworkProtocol.HTTP_BRIDGE)
            .copy(latencyMs = 10L)
        val p2 = PacketInspectorUtils.generateSamplePacket(2L, NetworkProtocol.HTTP_BRIDGE)
            .copy(latencyMs = 15L) // diff 5
        val p3 = PacketInspectorUtils.generateSamplePacket(3L, NetworkProtocol.HTTP_BRIDGE)
            .copy(latencyMs = 25L) // diff 10

        val jitter = PacketInspectorUtils.calculateJitter(listOf(p1, p2, p3))
        assertEquals(7.5, jitter, 0.001)

        val singleJitter = PacketInspectorUtils.calculateJitter(listOf(p1))
        assertEquals(0.0, singleJitter, 0.001)
    }

    @Test
    fun testFormatBytes() {
        assertEquals("512 B", PacketInspectorUtils.formatBytes(512))
        assertEquals("1.0 KB", PacketInspectorUtils.formatBytes(1024))
        assertEquals("2.0 MB", PacketInspectorUtils.formatBytes(2 * 1024 * 1024))
    }

    @Test
    fun testGenerateSamplePacket() {
        val pkt = PacketInspectorUtils.generateSamplePacket(42L, NetworkProtocol.MERKLE_SYNC)
        assertEquals(42L, pkt.sequenceNumber)
        assertEquals(NetworkProtocol.MERKLE_SYNC, pkt.protocol)
        assertEquals(PacketDirection.OUTBOUND, pkt.direction)
        assertTrue(pkt.payloadSizeBytes > 0)
        assertTrue(pkt.rawBytesHex.isNotBlank())
        assertTrue(pkt.crc32 > 0)
    }
}
