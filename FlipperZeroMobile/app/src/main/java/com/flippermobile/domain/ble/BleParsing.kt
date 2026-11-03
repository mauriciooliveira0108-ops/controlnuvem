package com.flippermobile.domain.ble

import com.flippermobile.core.util.HexUtils

/** Estrutura AD (Advertising Data) do Bluetooth Core Spec: [len][type][data...]. */
data class AdStructure(val type: Int, val typeName: String, val dataHex: String)

object BleParsing {

    fun adTypeName(type: Int): String = when (type) {
        0x01 -> "Flags"
        0x02 -> "UUIDs 16-bit (parcial)"
        0x03 -> "UUIDs 16-bit (completa)"
        0x04 -> "UUIDs 32-bit (parcial)"
        0x05 -> "UUIDs 32-bit (completa)"
        0x06 -> "UUIDs 128-bit (parcial)"
        0x07 -> "UUIDs 128-bit (completa)"
        0x08 -> "Nome curto"
        0x09 -> "Nome completo"
        0x0A -> "Potência TX"
        0x16 -> "Dados de serviço (16-bit)"
        0x19 -> "Aparência"
        0x1B -> "Endereço LE"
        0xFF -> "Dados do fabricante"
        else -> String.format("Tipo 0x%02X", type)
    }

    /** Divide o registro de anúncio bruto em estruturas AD, ignorando o preenchimento de zeros. */
    fun parseAdvertisement(raw: ByteArray?): List<AdStructure> {
        if (raw == null) return emptyList()
        val result = mutableListOf<AdStructure>()
        var i = 0
        while (i < raw.size) {
            val len = raw[i].toInt() and 0xFF
            if (len == 0) break
            if (i + 1 + len > raw.size) break
            val type = raw[i + 1].toInt() and 0xFF
            val data = raw.copyOfRange(i + 2, i + 1 + len)
            result += AdStructure(type, adTypeName(type), HexUtils.toHex(data, " "))
            i += 1 + len
        }
        return result
    }

    /** Alguns identificadores de empresa atribuídos pelo Bluetooth SIG. */
    fun companyName(id: Int): String? = when (id) {
        0x0006 -> "Microsoft"
        0x004C -> "Apple"
        0x0075 -> "Samsung"
        0x00E0 -> "Google"
        0x0087 -> "Garmin"
        0x0157 -> "Huami (Xiaomi)"
        0x038F -> "Xiaomi"
        0x0059 -> "Nordic Semiconductor"
        0x000D -> "Texas Instruments"
        0x0002 -> "Intel"
        0x00D2 -> "Bose"
        0x012D -> "Sony"
        0x02E5 -> "Espressif"
        else -> null
    }

    /** Nomes de serviços GATT padronizados mais comuns (UUID de 16 bits). */
    fun gattServiceName(uuid: String): String {
        val short = shortUuid(uuid) ?: return "Serviço personalizado"
        return when (short) {
            0x1800 -> "Generic Access"
            0x1801 -> "Generic Attribute"
            0x180A -> "Device Information"
            0x180F -> "Battery Service"
            0x180D -> "Heart Rate"
            0x1809 -> "Health Thermometer"
            0x1810 -> "Blood Pressure"
            0x1812 -> "Human Interface Device"
            0x1816 -> "Cycling Speed and Cadence"
            0x1818 -> "Cycling Power"
            0x181A -> "Environmental Sensing"
            0x181C -> "User Data"
            0x1802 -> "Immediate Alert"
            0x1803 -> "Link Loss"
            0x1804 -> "Tx Power"
            0x1805 -> "Current Time"
            0xFE9F -> "Google"
            0xFD6F -> "Exposure Notification"
            else -> String.format("Serviço 0x%04X", short)
        }
    }

    fun gattCharacteristicName(uuid: String): String {
        val short = shortUuid(uuid) ?: return "Característica personalizada"
        return when (short) {
            0x2A00 -> "Device Name"
            0x2A01 -> "Appearance"
            0x2A04 -> "Peripheral Preferred Connection Parameters"
            0x2A05 -> "Service Changed"
            0x2A19 -> "Battery Level"
            0x2A24 -> "Model Number"
            0x2A25 -> "Serial Number"
            0x2A26 -> "Firmware Revision"
            0x2A27 -> "Hardware Revision"
            0x2A28 -> "Software Revision"
            0x2A29 -> "Manufacturer Name"
            0x2A37 -> "Heart Rate Measurement"
            0x2A38 -> "Body Sensor Location"
            0x2A6E -> "Temperature"
            0x2A6F -> "Humidity"
            else -> String.format("Característica 0x%04X", short)
        }
    }

    private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /** Extrai o UUID de 16 bits se o UUID usa a base do Bluetooth SIG. */
    fun shortUuid(uuid: String): Int? {
        val u = uuid.lowercase()
        if (u.length != 36 || !u.endsWith(BASE_SUFFIX) || !u.startsWith("0000")) return null
        return u.substring(4, 8).toIntOrNull(16)
    }

    /** Converte a máscara de propriedades de BluetoothGattCharacteristic em rótulos. */
    fun characteristicProperties(mask: Int): List<String> = buildList {
        if (mask and 0x01 != 0) add("BROADCAST")
        if (mask and 0x02 != 0) add("READ")
        if (mask and 0x04 != 0) add("WRITE_NO_RESP")
        if (mask and 0x08 != 0) add("WRITE")
        if (mask and 0x10 != 0) add("NOTIFY")
        if (mask and 0x20 != 0) add("INDICATE")
        if (mask and 0x40 != 0) add("SIGNED_WRITE")
        if (mask and 0x80 != 0) add("EXTENDED")
    }
}
