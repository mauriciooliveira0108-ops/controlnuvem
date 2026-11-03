package com.flippermobile.domain

import com.flippermobile.domain.export.ExportFormatter
import com.flippermobile.domain.log.LogFilter
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.domain.model.LogEntry
import com.flippermobile.domain.model.LogLevel
import com.flippermobile.domain.model.NdefRecordInfo
import com.flippermobile.domain.model.NfcReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogAndExportTest {
    private val logs = listOf(
        LogEntry(1, 1000, LogLevel.INFO, LogCategory.NFC, "Tag detectada"),
        LogEntry(2, 2000, LogLevel.ERROR, LogCategory.BLE, "Falha no scan"),
        LogEntry(3, 3000, LogLevel.DEBUG, LogCategory.WIFI, "Varredura iniciada"),
    )

    @Test fun filterByQueryCategoryAndLevel() {
        assertEquals(1, LogFilter.filter(logs, "falha").size)
        assertEquals(listOf(1L), LogFilter.filter(logs, "", setOf(LogCategory.NFC)).map { it.id })
        assertEquals(2, LogFilter.filter(logs, "", minLevel = LogLevel.INFO).size)
        assertEquals(1, LogFilter.filter(logs, "wifi").size)
    }

    @Test fun formatLine() {
        assertEquals("T E/BLE: Falha no scan", LogFilter.formatLine(logs[1]) { "T" })
    }

    @Test fun jsonEscaping() {
        assertEquals("\"a\\\"b\\\\c\\n\"", ExportFormatter.jsonString("a\"b\\c\n"))
        assertEquals("null", ExportFormatter.jsonString(null))
    }

    @Test fun csvEscaping() {
        assertEquals("\"a,b\"", ExportFormatter.csvField("a,b"))
        assertEquals("\"x\"\"y\"", ExportFormatter.csvField("x\"y"))
        assertEquals("plain", ExportFormatter.csvField("plain"))
    }

    @Test fun nfcJsonContainsFields() {
        val r = NfcReading(
            timestamp = 0, uidHex = "04:A2", technologies = listOf("NfcA", "Ndef"), tagType = "MIFARE Ultralight",
            maxSize = 137, isWritable = true, rawHex = "",
            ndefRecords = listOf(NdefRecordInfo(1, "WELL_KNOWN", "55", "U", "04", "https://a", "URI")),
        )
        val json = ExportFormatter.nfcReadingsJson(listOf(r)) { "T" }
        assertTrue(json.contains("\"uid\":\"04:A2\""))
        assertTrue(json.contains("\"maxSize\":137"))
        assertTrue(json.contains("\"interpreted\":\"https://a\""))
        assertTrue(ExportFormatter.nfcReadingText(r) { "T" }.contains("Registro #1 (URI)"))
    }
}

class TerminalCommandTest {
    @Test fun parsesCommands() {
        val p = com.flippermobile.domain.log.TerminalCommand
        assertEquals(com.flippermobile.domain.log.TerminalCommand.Help, p.parse(" HELP "))
        assertEquals(com.flippermobile.domain.log.TerminalCommand.Search("ble erro"), p.parse("find ble erro"))
        assertEquals(com.flippermobile.domain.log.TerminalCommand.Search(""), p.parse("find"))
        assertEquals(com.flippermobile.domain.log.TerminalCommand.Echo("oi"), p.parse("echo oi"))
        assertEquals(com.flippermobile.domain.log.TerminalCommand.Empty, p.parse("   "))
        assertEquals(com.flippermobile.domain.log.TerminalCommand.Unknown("rm -rf /"), p.parse("rm -rf /"))
    }
}
