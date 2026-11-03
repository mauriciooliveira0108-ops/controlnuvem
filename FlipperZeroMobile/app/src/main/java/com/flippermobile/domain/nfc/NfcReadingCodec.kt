package com.flippermobile.domain.nfc

import com.flippermobile.domain.model.NdefRecordInfo
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Serialização simples e estável para persistir registros NDEF e metadados em colunas de texto.
 * Cada campo é codificado com URL-encoding; campos separados por '|' e itens por '\n'.
 */
object NfcReadingCodec {
    private fun enc(s: String?): String = if (s == null) "~" else URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String? = if (s == "~") null else URLDecoder.decode(s, "UTF-8")

    fun encodeRecords(records: List<NdefRecordInfo>): String =
        records.joinToString("\n") { r ->
            listOf(r.tnf.toString(), r.tnfName, r.typeHex, r.typeText, r.payloadHex, r.interpreted, r.kind)
                .joinToString("|") { enc(it) }
        }

    fun decodeRecords(text: String): List<NdefRecordInfo> {
        if (text.isBlank()) return emptyList()
        return text.split('\n').mapNotNull { line ->
            val f = line.split('|')
            if (f.size != 7) return@mapNotNull null
            NdefRecordInfo(
                tnf = dec(f[0])?.toIntOrNull() ?: 0,
                tnfName = dec(f[1]) ?: "",
                typeHex = dec(f[2]) ?: "",
                typeText = dec(f[3]) ?: "",
                payloadHex = dec(f[4]) ?: "",
                interpreted = dec(f[5]),
                kind = dec(f[6]) ?: "",
            )
        }
    }

    fun encodeMap(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${enc(it.key)}|${enc(it.value)}" }

    fun decodeMap(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        text.split('\n').forEach { line ->
            val f = line.split('|')
            if (f.size == 2) {
                val k = dec(f[0]); val v = dec(f[1])
                if (k != null && v != null) result[k] = v
            }
        }
        return result
    }
}
