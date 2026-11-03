package com.flippermobile.hardware.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcBarcode
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.flippermobile.core.util.HexUtils
import com.flippermobile.domain.model.NdefRecordInfo
import com.flippermobile.domain.model.NfcReading
import com.flippermobile.domain.nfc.NdefParser
import java.io.ByteArrayOutputStream

/**
 * Converte um android.nfc.Tag real em [NfcReading]. Operações de E/S bloqueantes:
 * chamar fora da thread principal. Nenhuma autenticação ou escrita é feita.
 */
object TagReader {

    fun read(tag: Tag, timestamp: Long = System.currentTimeMillis()): NfcReading {
        val techs = tag.techList.map { it.substringAfterLast('.') }
        val extra = LinkedHashMap<String, String>()
        var ndefRecords: List<NdefRecordInfo> = emptyList()
        var maxSize: Int? = null
        var writable: Boolean? = null
        var rawHex = ""
        var type = "Desconhecido"

        NfcA.get(tag)?.let { nfcA ->
            extra["ATQA"] = HexUtils.toHex(nfcA.atqa, " ")
            extra["SAK"] = String.format("0x%02X", nfcA.sak.toInt())
            extra["NfcA máx. transceive"] = "${nfcA.maxTransceiveLength} bytes"
            type = "ISO 14443-3A"
        }
        NfcB.get(tag)?.let { nfcB ->
            extra["NfcB application data"] = HexUtils.toHex(nfcB.applicationData, " ")
            extra["NfcB protocol info"] = HexUtils.toHex(nfcB.protocolInfo, " ")
            type = "ISO 14443-3B"
        }
        NfcF.get(tag)?.let { nfcF ->
            extra["FeliCa fabricante (PMm)"] = HexUtils.toHex(nfcF.manufacturer, " ")
            extra["FeliCa código de sistema"] = HexUtils.toHex(nfcF.systemCode, " ")
            type = "FeliCa (JIS 6319-4)"
        }
        NfcV.get(tag)?.let { nfcV ->
            extra["ISO 15693 DSFID"] = String.format("0x%02X", nfcV.dsfId.toInt())
            extra["ISO 15693 response flags"] = String.format("0x%02X", nfcV.responseFlags.toInt())
            type = "ISO 15693 (NfcV)"
        }
        NfcBarcode.get(tag)?.let { bc ->
            extra["Barcode"] = HexUtils.toHex(bc.barcode, " ")
            type = "NFC Barcode (Kovio)"
        }
        IsoDep.get(tag)?.let { iso ->
            iso.historicalBytes?.let { extra["IsoDep historical bytes"] = HexUtils.toHex(it, " ") }
            iso.hiLayerResponse?.let { extra["IsoDep hi-layer response"] = HexUtils.toHex(it, " ") }
            type = "ISO 14443-4 (IsoDep)"
        }

        // MifareClassic.get pode falhar em aparelhos sem controlador NXP.
        val classic = runCatching { MifareClassic.get(tag) }.getOrNull()
        if (classic != null) {
            type = when (classic.type) {
                MifareClassic.TYPE_CLASSIC -> "MIFARE Classic"
                MifareClassic.TYPE_PLUS -> "MIFARE Plus"
                MifareClassic.TYPE_PRO -> "MIFARE Pro"
                else -> "MIFARE (tipo desconhecido)"
            }
            extra["MIFARE tamanho"] = "${classic.size} bytes"
            extra["MIFARE setores/blocos"] = "${classic.sectorCount} / ${classic.blockCount}"
            extra["Observação"] = "Setores MIFARE Classic exigem chaves; o app não tenta autenticação."
        } else if (techs.contains("MifareClassic")) {
            extra["Observação"] = "Tag MIFARE Classic detectada, mas este aparelho não suporta a API MifareClassic."
        }

        val ultralight = MifareUltralight.get(tag)
        if (ultralight != null) {
            val result = readUltralight(tag, ultralight)
            type = result.first
            rawHex = HexUtils.toHex(result.second, " ")
            result.third?.let { extra["Leitura de memória"] = it }
        }

        Ndef.get(tag)?.let { ndef ->
            maxSize = ndef.maxSize
            writable = ndef.isWritable
            extra["NDEF tipo"] = ndef.type
            extra["NDEF pode ser somente leitura"] = if (ndef.canMakeReadOnly()) "Sim" else "Não"
            val message: NdefMessage? = ndef.cachedNdefMessage ?: runCatching {
                ndef.connect(); ndef.ndefMessage
            }.getOrNull().also { runCatching { ndef.close() } }
            if (message != null) {
                ndefRecords = message.records.map { r -> NdefParser.parse(r.tnf.toInt(), r.type, r.payload) }
                if (rawHex.isEmpty()) rawHex = HexUtils.toHex(message.toByteArray(), " ")
            }
        }
        if (NdefFormatable.get(tag) != null) extra["NDEF"] = "Tag formatável para NDEF (sem dados NDEF)"

        return NfcReading(
            timestamp = timestamp,
            uidHex = HexUtils.toHex(tag.id, ":"),
            technologies = techs,
            tagType = type,
            maxSize = maxSize,
            isWritable = writable,
            ndefRecords = ndefRecords,
            rawHex = rawHex,
            extraInfo = extra,
        )
    }

    /** Lê a memória de Ultralight/NTAG (sem senha). Retorna tipo, bytes lidos e observação. */
    private fun readUltralight(tag: Tag, ul: MifareUltralight): Triple<String, ByteArray, String?> {
        var typeName = when (ul.type) {
            MifareUltralight.TYPE_ULTRALIGHT -> "MIFARE Ultralight"
            MifareUltralight.TYPE_ULTRALIGHT_C -> "MIFARE Ultralight C"
            else -> "MIFARE Ultralight (família)"
        }
        var totalPages = when (ul.type) {
            MifareUltralight.TYPE_ULTRALIGHT_C -> 44 // páginas 0x2C+ contêm chave (não legíveis)
            else -> 16
        }
        // GET_VERSION (0x60) identifica NTAG21x. Uma resposta NAK coloca a tag em HALT,
        // por isso reconectamos antes da leitura de páginas.
        try {
            ul.connect()
            val version = ul.transceive(byteArrayOf(0x60))
            if (version.size >= 8 && version[2].toInt() == 0x04) {
                when (version[6].toInt() and 0xFF) {
                    0x0F -> { typeName = "NTAG213"; totalPages = 45 }
                    0x11 -> { typeName = "NTAG215"; totalPages = 135 }
                    0x13 -> { typeName = "NTAG216"; totalPages = 231 }
                }
            } else if (version.size >= 8 && version[2].toInt() == 0x03) {
                when (version[6].toInt() and 0xFF) {
                    0x0B -> { typeName = "MIFARE Ultralight EV1 (MF0UL11)"; totalPages = 20 }
                    0x0E -> { typeName = "MIFARE Ultralight EV1 (MF0UL21)"; totalPages = 41 }
                }
            }
        } catch (_: Exception) {
            // Tags sem GET_VERSION: mantém valores padrão.
        } finally {
            runCatching { ul.close() }
        }

        val out = ByteArrayOutputStream()
        var note: String? = null
        val fresh = MifareUltralight.get(tag) ?: return Triple(typeName, ByteArray(0), "Tag não disponível")
        try {
            fresh.connect()
            var page = 0
            while (page < totalPages) {
                val data = fresh.readPages(page) // 4 páginas = 16 bytes
                val pagesToKeep = minOf(4, totalPages - page)
                out.write(data, 0, pagesToKeep * 4)
                page += 4
            }
            note = "$totalPages páginas (${totalPages * 4} bytes) lidas"
        } catch (e: Exception) {
            note = "Leitura parcial: ${out.size()} bytes (${e.javaClass.simpleName}). Áreas protegidas não são lidas."
        } finally {
            runCatching { fresh.close() }
        }
        return Triple(typeName, out.toByteArray(), note)
    }
}
