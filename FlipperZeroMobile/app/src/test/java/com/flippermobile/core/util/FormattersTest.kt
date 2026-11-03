package com.flippermobile.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class FormattersTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun bytes_units() {
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1,5 KB", Formatters.bytes(1536))
        assertEquals("2,0 GB", Formatters.bytes(2L * 1024 * 1024 * 1024))
        assertEquals("—", Formatters.bytes(-1))
    }

    @Test fun timestamp_ptBR() {
        assertEquals("01/01/1970 00:00:00", Formatters.timestamp(0, utc))
        assertEquals("19700101_000001", Formatters.fileStamp(1000, utc))
    }

    @Test fun percent_isClamped() {
        assertEquals("100%", Formatters.percent(150))
        assertEquals("0%", Formatters.percent(-5))
    }
}
