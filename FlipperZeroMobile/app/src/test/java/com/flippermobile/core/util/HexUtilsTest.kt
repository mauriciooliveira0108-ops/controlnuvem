package com.flippermobile.core.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexUtilsTest {
    @Test fun toHex_withSeparator() {
        assertEquals("04:A2:FF", HexUtils.toHex(byteArrayOf(0x04, 0xA2.toByte(), 0xFF.toByte()), ":"))
    }

    @Test fun toHex_emptyAndNull() {
        assertEquals("", HexUtils.toHex(ByteArray(0)))
        assertEquals("", HexUtils.toHex(null))
    }

    @Test fun fromHex_roundTrip() {
        val bytes = HexUtils.fromHex("04 a2:3B-ff")!!
        assertArrayEquals(byteArrayOf(0x04, 0xA2.toByte(), 0x3B, 0xFF.toByte()), bytes)
        assertEquals("04A23BFF", HexUtils.toHex(bytes))
    }

    @Test fun fromHex_invalid() {
        assertNull(HexUtils.fromHex("ABC"))
        assertNull(HexUtils.fromHex("ZZ"))
    }

    @Test fun utf8IfPrintable_detectsText() {
        assertEquals("Olá NFC", HexUtils.utf8IfPrintable("Olá NFC".toByteArray()))
    }

    @Test fun utf8IfPrintable_rejectsBinary() {
        assertNull(HexUtils.utf8IfPrintable(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)))
        assertNull(HexUtils.utf8IfPrintable(byteArrayOf(0xC3.toByte(), 0x28)))
    }

    @Test fun hexDump_formatsAsciiColumn() {
        val dump = HexUtils.hexDump("ABC".toByteArray())
        assertEquals("0000  41 42 43" + " ".repeat(13 * 3) + "  ABC", dump)
    }
}
