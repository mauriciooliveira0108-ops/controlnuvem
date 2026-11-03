package com.flippermobile.domain

import com.flippermobile.domain.nfc.NdefParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NdefParserTest {
    @Test fun parsesTextRecord() {
        val payload = byteArrayOf(0x02) + "pt".toByteArray() + "Olá".toByteArray()
        val r = NdefParser.parse(NdefParser.TNF_WELL_KNOWN, "T".toByteArray(), payload)
        assertEquals("Texto", r.kind)
        assertEquals("[pt] Olá", r.interpreted)
        assertEquals("WELL_KNOWN", r.tnfName)
    }

    @Test fun parsesUriWithPrefix() {
        val payload = byteArrayOf(0x04) + "example.com".toByteArray()
        val r = NdefParser.parse(NdefParser.TNF_WELL_KNOWN, "U".toByteArray(), payload)
        assertEquals("https://example.com", r.interpreted)
    }

    @Test fun parsesMimeText() {
        val r = NdefParser.parse(NdefParser.TNF_MIME_MEDIA, "text/plain".toByteArray(), "abc".toByteArray())
        assertEquals("abc", r.interpreted)
        assertEquals("61 62 63", r.payloadHex)
    }

    @Test fun parsesAndroidApplicationRecord() {
        val r = NdefParser.parse(NdefParser.TNF_EXTERNAL_TYPE, "android.com:pkg".toByteArray(), "com.x".toByteArray())
        assertEquals("Android Application Record", r.kind)
        assertEquals("com.x", r.interpreted)
    }

    @Test fun malformedTextIsNull() {
        assertNull(NdefParser.parseText(byteArrayOf(0x10)))
        assertNull(NdefParser.parseUri(ByteArray(0)))
    }

    @Test fun binaryPayloadNotInterpreted() {
        val r = NdefParser.parse(NdefParser.TNF_UNKNOWN, ByteArray(0), byteArrayOf(0, 1, 2, 3))
        assertNull(r.interpreted)
    }
}

class NfcReadingCodecTest {
    @Test fun recordsRoundTrip() {
        val records = listOf(
            com.flippermobile.domain.model.NdefRecordInfo(1, "WELL_KNOWN", "54", "T", "02 70 74", "[pt] a|b\nc", "Texto"),
            com.flippermobile.domain.model.NdefRecordInfo(0, "EMPTY", "", "", "", null, "Vazio"),
        )
        val text = com.flippermobile.domain.nfc.NfcReadingCodec.encodeRecords(records)
        assertEquals(records, com.flippermobile.domain.nfc.NfcReadingCodec.decodeRecords(text))
    }

    @Test fun mapRoundTrip() {
        val map = linkedMapOf("ATQA" to "00 44", "SAK|x" to "0\n0")
        val codec = com.flippermobile.domain.nfc.NfcReadingCodec
        assertEquals(map, codec.decodeMap(codec.encodeMap(map)))
        assertEquals(emptyMap<String, String>(), codec.decodeMap(""))
    }
}
