package com.flippermobile.domain.export

import com.flippermobile.core.util.Formatters
import com.flippermobile.domain.ir.IrRemoteConfig
import com.flippermobile.domain.log.LogFilter
import com.flippermobile.domain.model.BleDeviceInfo
import com.flippermobile.domain.model.LogEntry
import com.flippermobile.domain.model.NfcReading
import com.flippermobile.domain.model.WifiConnectionInfo
import com.flippermobile.domain.model.WifiScanResultInfo

/** Geração pura de conteúdo exportável (texto, CSV e JSON). */
object ExportFormatter {

    fun jsonString(value: String?): String {
        if (value == null) return "null"
        val sb = StringBuilder("\"")
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    fun csvField(value: String?): String {
        val v = value ?: ""
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' }) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else v
    }

    fun nfcReadingsJson(readings: List<NfcReading>, time: (Long) -> String = Formatters::timestamp): String =
        readings.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { r ->
            val records = r.ndefRecords.joinToString(prefix = "[", postfix = "]", separator = ",") { rec ->
                "{\"tnf\":${jsonString(rec.tnfName)},\"type\":${jsonString(rec.typeText)}," +
                    "\"kind\":${jsonString(rec.kind)},\"payloadHex\":${jsonString(rec.payloadHex)}," +
                    "\"interpreted\":${jsonString(rec.interpreted)}}"
            }
            val extras = r.extraInfo.entries.joinToString(prefix = "{", postfix = "}", separator = ",") {
                "${jsonString(it.key)}:${jsonString(it.value)}"
            }
            "  {\"timestamp\":${jsonString(time(r.timestamp))},\"label\":${jsonString(r.label)}," +
                "\"uid\":${jsonString(r.uidHex)},\"type\":${jsonString(r.tagType)}," +
                "\"technologies\":${r.technologies.joinToString(prefix = "[", postfix = "]", separator = ",") { jsonString(it) }}," +
                "\"maxSize\":${r.maxSize ?: "null"},\"writable\":${r.isWritable ?: "null"}," +
                "\"extra\":$extras,\"ndef\":$records,\"rawHex\":${jsonString(r.rawHex)}}"
        }

    fun nfcReadingText(r: NfcReading, time: (Long) -> String = Formatters::timestamp): String = buildString {
        appendLine("=== LEITURA NFC ===")
        appendLine("Data: ${time(r.timestamp)}")
        if (r.label.isNotBlank()) appendLine("Rótulo: ${r.label}")
        appendLine("UID: ${r.uidHex.ifBlank { "(indisponível)" }}")
        appendLine("Tipo: ${r.tagType}")
        appendLine("Tecnologias: ${r.technologies.joinToString()}")
        r.maxSize?.let { appendLine("Tamanho NDEF máx.: $it bytes") }
        r.isWritable?.let { appendLine("Gravável: ${Formatters.yesNo(it)}") }
        r.extraInfo.forEach { (k, v) -> appendLine("$k: $v") }
        if (r.ndefRecords.isEmpty()) appendLine("NDEF: nenhum registro")
        r.ndefRecords.forEachIndexed { i, rec ->
            appendLine("-- Registro #${i + 1} (${rec.kind}) --")
            appendLine("TNF: ${rec.tnfName}  Tipo: ${rec.typeText}")
            appendLine("Payload (hex): ${rec.payloadHex}")
            rec.interpreted?.let { appendLine("Interpretado: $it") }
        }
        if (r.rawHex.isNotBlank()) appendLine("Bruto (hex): ${r.rawHex}")
    }

    fun bleDevicesCsv(devices: List<BleDeviceInfo>, time: (Long) -> String = Formatters::timestamp): String =
        buildString {
            appendLine("visto_em,nome,endereco,rssi_dbm,tx_power,conectavel,tipo,servicos,fabricante,anuncio_hex")
            devices.forEach { d ->
                appendLine(
                    listOf(
                        time(d.lastSeen), d.name ?: "", d.address, d.rssi.toString(), d.txPower?.toString() ?: "",
                        d.connectable?.toString() ?: "", d.deviceType, d.serviceUuids.joinToString(" "),
                        d.manufacturerData.entries.joinToString(" ") { String.format("0x%04X=%s", it.key, it.value) },
                        d.rawAdvertisementHex,
                    ).joinToString(",") { csvField(it) }
                )
            }
        }

    fun wifiReport(
        connection: WifiConnectionInfo?,
        scan: List<WifiScanResultInfo>,
        timestamp: Long,
        time: (Long) -> String = Formatters::timestamp,
    ): String = buildString {
        appendLine("=== RELATÓRIO WI-FI — ${time(timestamp)} ===")
        if (connection == null || !connection.connected) {
            appendLine("Conexão atual: desconectado")
        } else {
            appendLine("SSID: ${connection.ssid ?: "(oculto: ${connection.ssidHiddenReason ?: "indisponível"})"}")
            connection.bssid?.let { appendLine("BSSID: $it") }
            connection.rssi?.let { appendLine("RSSI: $it dBm") }
            connection.frequencyMhz?.let { appendLine("Frequência: $it MHz (${connection.band}, canal ${connection.channel ?: "?"})") }
            connection.linkSpeedMbps?.let { appendLine("Velocidade do link: $it Mbps") }
            connection.standard?.let { appendLine("Padrão: $it") }
            if (connection.ipv4.isNotEmpty()) appendLine("IPv4: ${connection.ipv4.joinToString()}")
            if (connection.ipv6.isNotEmpty()) appendLine("IPv6: ${connection.ipv6.joinToString()}")
            connection.gateway?.let { appendLine("Gateway: $it") }
            if (connection.dns.isNotEmpty()) appendLine("DNS: ${connection.dns.joinToString()}")
        }
        appendLine()
        appendLine("Redes visíveis: ${scan.size}")
        scan.forEach {
            appendLine("${it.ssid.ifBlank { "(SSID oculto)" }} | ${it.bssid} | ${it.rssi} dBm | ${it.frequencyMhz} MHz ${it.band} ch ${it.channel ?: "?"} | ${it.security}")
        }
    }

    fun logsText(entries: List<LogEntry>, time: (Long) -> String = Formatters::timestamp): String =
        entries.joinToString("\n") { LogFilter.formatLine(it, time) }

    fun irConfigText(config: IrRemoteConfig): String = buildString {
        appendLine("=== CONTROLE IR: ${config.name} ===")
        appendLine("Protocolo: ${config.protocol.displayName} (${config.protocol.carrierHz} Hz)")
        appendLine(String.format("Endereço: 0x%X", config.address))
        config.commands.entries.sortedBy { it.key.ordinal }.forEach {
            appendLine(String.format("%-10s 0x%02X", it.key.label, it.value))
        }
    }
}
