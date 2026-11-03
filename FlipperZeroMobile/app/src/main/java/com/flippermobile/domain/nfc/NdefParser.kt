package com.flippermobile.domain.nfc

import com.flippermobile.core.util.HexUtils
import com.flippermobile.domain.model.NdefRecordInfo

/**
 * Interpretação pura de registros NDEF (NFC Forum RTD), sem dependência de Android.
 * Recebe os campos brutos já extraídos de android.nfc.NdefRecord.
 */
object NdefParser {

    const val TNF_EMPTY = 0
    const val TNF_WELL_KNOWN = 1
    const val TNF_MIME_MEDIA = 2
    const val TNF_ABSOLUTE_URI = 3
    const val TNF_EXTERNAL_TYPE = 4
    const val TNF_UNKNOWN = 5
    const val TNF_UNCHANGED = 6

    /** Tabela de prefixos do URI Record Type Definition (NFC Forum). */
    val URI_PREFIXES = arrayOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
        "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:",
        "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
        "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
        "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:"
    )

    fun tnfName(tnf: Int): String = when (tnf) {
        TNF_EMPTY -> "EMPTY"
        TNF_WELL_KNOWN -> "WELL_KNOWN"
        TNF_MIME_MEDIA -> "MIME_MEDIA"
        TNF_ABSOLUTE_URI -> "ABSOLUTE_URI"
        TNF_EXTERNAL_TYPE -> "EXTERNAL_TYPE"
        TNF_UNKNOWN -> "UNKNOWN"
        TNF_UNCHANGED -> "UNCHANGED"
        else -> "RESERVED($tnf)"
    }

    fun parse(tnf: Int, type: ByteArray, payload: ByteArray): NdefRecordInfo {
        val typeText = String(type, Charsets.US_ASCII)
        val (kind, interpreted) = interpret(tnf, type, typeText, payload)
        return NdefRecordInfo(
            tnf = tnf,
            tnfName = tnfName(tnf),
            typeHex = HexUtils.toHex(type, " "),
            typeText = typeText,
            payloadHex = HexUtils.toHex(payload, " "),
            interpreted = interpreted,
            kind = kind,
        )
    }

    private fun interpret(tnf: Int, type: ByteArray, typeText: String, payload: ByteArray): Pair<String, String?> =
        when (tnf) {
            TNF_EMPTY -> "Vazio" to null
            TNF_WELL_KNOWN -> when (typeText) {
                "T" -> "Texto" to parseText(payload)
                "U" -> "URI" to parseUri(payload)
                "Sp" -> "Smart Poster" to "Smart Poster (${payload.size} bytes aninhados)"
                "Hs", "Hr", "Hc" -> "Handover" to "Registro de handover ($typeText)"
                else -> "Well-known ($typeText)" to HexUtils.utf8IfPrintable(payload)
            }
            TNF_MIME_MEDIA -> {
                val mime = typeText.lowercase()
                val text = if (mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") ||
                    mime == "text/vcard" || mime == "text/x-vcard"
                ) {
                    String(payload, Charsets.UTF_8)
                } else HexUtils.utf8IfPrintable(payload)
                "MIME $typeText" to text
            }
            TNF_ABSOLUTE_URI -> "URI absoluta" to typeText
            TNF_EXTERNAL_TYPE -> {
                if (typeText == "android.com:pkg") "Android Application Record" to String(payload, Charsets.UTF_8)
                else "Externo ($typeText)" to HexUtils.utf8IfPrintable(payload)
            }
            else -> "Desconhecido" to HexUtils.utf8IfPrintable(payload)
        }

    /** RTD Text: byte de status (bit 7 = UTF-16, bits 0-5 = tamanho do idioma). */
    fun parseText(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt() and 0xFF
        val utf16 = status and 0x80 != 0
        val langLength = status and 0x3F
        if (1 + langLength > payload.size) return null
        val lang = String(payload, 1, langLength, Charsets.US_ASCII)
        val textBytes = payload.copyOfRange(1 + langLength, payload.size)
        val text = String(textBytes, if (utf16) Charsets.UTF_16 else Charsets.UTF_8)
        return if (lang.isNotEmpty()) "[$lang] $text" else text
    }

    /** RTD URI: primeiro byte = código de prefixo. */
    fun parseUri(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val code = payload[0].toInt() and 0xFF
        val prefix = URI_PREFIXES.getOrElse(code) { "" }
        val rest = String(payload, 1, payload.size - 1, Charsets.UTF_8)
        return prefix + rest
    }
}
