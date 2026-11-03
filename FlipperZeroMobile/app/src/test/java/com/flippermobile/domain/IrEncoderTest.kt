package com.flippermobile.domain

import com.flippermobile.domain.ir.InvalidIrCodeException
import com.flippermobile.domain.ir.IrButton
import com.flippermobile.domain.ir.IrEncoder
import com.flippermobile.domain.ir.IrPresets
import com.flippermobile.domain.ir.IrProtocol
import com.flippermobile.domain.ir.IrRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrEncoderTest {

    /** Decodifica um quadro pulse-distance (NEC/Samsung) de volta para 32 bits, LSB primeiro. */
    private fun decodePulseDistance(pattern: IntArray): Long {
        var value = 0L
        for (bit in 0 until 32) {
            val space = pattern[3 + bit * 2]
            if (space > 1000) value = value or (1L shl bit)
        }
        return value
    }

    @Test fun nec_structure() {
        val s = IrEncoder.encode(IrProtocol.NEC, 0x04, 0x08)
        assertEquals(38_000, s.carrierHz)
        // líder (2) + 32 bits * 2 + marca final + espaço final
        assertEquals(2 + 64 + 2, s.pattern.size)
        assertEquals(9000, s.pattern[0])
        assertEquals(4500, s.pattern[1])
        val decoded = decodePulseDistance(s.pattern)
        assertEquals(0x04L, decoded and 0xFF)
        assertEquals(0xFBL, (decoded shr 8) and 0xFF)
        assertEquals(0x08L, (decoded shr 16) and 0xFF)
        assertEquals(0xF7L, (decoded shr 24) and 0xFF)
    }

    @Test fun necExtended_usesSixteenBitAddress() {
        val decoded = decodePulseDistance(IrEncoder.encode(IrProtocol.NEC_EXT, 0x1234, 0x56).pattern)
        assertEquals(0x34L, decoded and 0xFF)
        assertEquals(0x12L, (decoded shr 8) and 0xFF)
    }

    @Test fun samsung32_repeatsAddress() {
        val s = IrEncoder.encode(IrProtocol.SAMSUNG32, 0x07, 0x02)
        assertEquals(4500, s.pattern[0])
        val decoded = decodePulseDistance(s.pattern)
        assertEquals(0x07L, decoded and 0xFF)
        assertEquals(0x07L, (decoded shr 8) and 0xFF)
        assertEquals(0x02L, (decoded shr 16) and 0xFF)
        assertEquals(0xFDL, (decoded shr 24) and 0xFF)
    }

    @Test fun sony_threeFramesOf45ms() {
        val s = IrEncoder.encode(IrProtocol.SONY_SIRC12, 1, 21)
        assertEquals(40_000, s.carrierHz)
        assertEquals(3 * 45_000L, s.totalDurationUs)
        assertEquals(2400, s.pattern[0])
        // bit 0 do comando 21 (0b0010101) = 1 -> marca de 1200 µs
        assertEquals(1200, s.pattern[2])
        assertEquals(600, s.pattern[4])
    }

    @Test fun patternsAlternateAndArePositive() {
        IrProtocol.entries.forEach { p ->
            val s = IrEncoder.encode(p, 1, 1)
            assertTrue(s.pattern.all { it > 0 })
            assertTrue("padrão < 2 s", s.totalDurationUs < 2_000_000)
        }
    }

    @Test(expected = InvalidIrCodeException::class)
    fun rejectsOutOfRangeCommand() {
        IrEncoder.encode(IrProtocol.SONY_SIRC12, 1, 200)
    }

    @Test fun commandsSerializationRoundTrip() {
        val cmds = IrPresets.LG_TV.commands
        val text = IrRemoteConfig.encodeCommands(cmds)
        assertEquals(cmds, IrRemoteConfig.decodeCommands(text))
        assertEquals(mapOf(IrButton.POWER to 8), IrRemoteConfig.decodeCommands("POWER=8;INVALID=3;MUTE=x"))
    }

    @Test fun allPresetsEncode() {
        IrPresets.all.forEach { cfg -> cfg.commands.keys.forEach { b -> cfg.signalFor(b)!! } }
    }
}

class IrCodeParserTest {
    @Test fun parsesHexAndDecimal() {
        val p = com.flippermobile.domain.ir.IrCodeParser
        assertEquals(0x1F, p.parseCode("0x1F"))
        assertEquals(0x1F, p.parseCode("1Fh"))
        assertEquals(0x1F, p.parseCode(" 1f "))
        assertEquals(31, p.parseCode("31"))
        assertEquals(null, p.parseCode("zz"))
        assertEquals(null, p.parseCode(""))
        assertEquals("0x0B", p.format(11))
    }
}
