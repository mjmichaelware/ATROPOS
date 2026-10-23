/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HexBinaryViewerTest {

    @Test
    fun testByteToHexFormatting() {
        assertEquals("00", HexBinaryUtils.byteToHex(0x00.toByte()))
        assertEquals("0A", HexBinaryUtils.byteToHex(0x0A.toByte()))
        assertEquals("7F", HexBinaryUtils.byteToHex(0x7F.toByte()))
        assertEquals("CA", HexBinaryUtils.byteToHex(0xCA.toByte()))
        assertEquals("FE", HexBinaryUtils.byteToHex(0xFE.toByte()))
        assertEquals("FF", HexBinaryUtils.byteToHex(0xFF.toByte()))
    }

    @Test
    fun testByteToBinaryFormatting() {
        assertEquals("00000000", HexBinaryUtils.byteToBinary(0x00.toByte()))
        assertEquals("00000001", HexBinaryUtils.byteToBinary(0x01.toByte()))
        assertEquals("11111111", HexBinaryUtils.byteToBinary(0xFF.toByte()))
        assertEquals("10101010", HexBinaryUtils.byteToBinary(0xAA.toByte()))
        assertEquals("01010101", HexBinaryUtils.byteToBinary(0x55.toByte()))
    }

    @Test
    fun testByteToAsciiFormatting() {
        assertEquals('A', HexBinaryUtils.byteToAscii('A'.code.toByte()))
        assertEquals('z', HexBinaryUtils.byteToAscii('z'.code.toByte()))
        assertEquals('9', HexBinaryUtils.byteToAscii('9'.code.toByte()))
        // Non-printable control characters should map to dot or placeholder
        assertEquals('·', HexBinaryUtils.byteToAscii(0x00.toByte()))
        assertEquals('·', HexBinaryUtils.byteToAscii(0x1F.toByte()))
        assertEquals('·', HexBinaryUtils.byteToAscii(0x7F.toByte()))
    }

    @Test
    fun testDexHeaderPresetBytes() {
        val dexBytes = HexBinaryUtils.generatePresetBytes(BinaryPreset.DEX_HEADER)
        assertTrue(dexBytes.size >= 8)
        // Magic dex\n039\0
        assertEquals('d'.code.toByte(), dexBytes[0])
        assertEquals('e'.code.toByte(), dexBytes[1])
        assertEquals('x'.code.toByte(), dexBytes[2])
        assertEquals(0x0a.toByte(), dexBytes[3])
    }

    @Test
    fun testJavaClassPresetBytes() {
        val classBytes = HexBinaryUtils.generatePresetBytes(BinaryPreset.JAVA_CLASS)
        assertTrue(classBytes.size >= 4)
        assertEquals(0xca.toByte(), classBytes[0])
        assertEquals(0xfe.toByte(), classBytes[1])
        assertEquals(0xba.toByte(), classBytes[2])
        assertEquals(0xbe.toByte(), classBytes[3])
    }

    @Test
    fun testMerkleRootPresetBytes() {
        val merkle = HexBinaryUtils.generatePresetBytes(BinaryPreset.MERKLE_ROOT)
        // SHA-256 digest is exactly 32 bytes
        assertEquals(32, merkle.size)
    }

    @Test
    fun testHexPatternSearch() {
        val sample = byteArrayOf(0x01, 0x02, 0xca.toByte(), 0xfe.toByte(), 0xba.toByte(), 0xbe.toByte(), 0x05)
        val matches = HexBinaryUtils.findMatches(sample, "cafe")
        assertEquals(listOf(2, 3), matches)

        val fullMatch = HexBinaryUtils.findMatches(sample, "CAFE BABE")
        assertEquals(listOf(2, 3, 4, 5), fullMatch)
    }

    @Test
    fun testAsciiPatternSearch() {
        val sample = "Hello Atropos Engine".toByteArray(Charsets.UTF_8)
        val matches = HexBinaryUtils.findMatches(sample, "Atropos")
        assertTrue(matches.isNotEmpty())
        assertEquals(6, matches.first())
        assertEquals(12, matches.last())
    }

    @Test
    fun testEmptySearchReturnsEmpty() {
        val sample = byteArrayOf(0x01, 0x02, 0x03)
        assertTrue(HexBinaryUtils.findMatches(sample, "").isEmpty())
        assertTrue(HexBinaryUtils.findMatches(sample, "   ").isEmpty())
    }
}
