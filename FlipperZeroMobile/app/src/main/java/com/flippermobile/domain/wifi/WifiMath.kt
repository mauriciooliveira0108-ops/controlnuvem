package com.flippermobile.domain.wifi

/** Cálculos puros sobre Wi-Fi. */
object WifiMath {

    fun band(frequencyMhz: Int): String = when (frequencyMhz) {
        in 2400..2500 -> "2,4 GHz"
        in 4900..5899 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        in 58_320..70_200 -> "60 GHz"
        else -> "Desconhecida"
    }

    fun channel(frequencyMhz: Int): Int? = when {
        frequencyMhz == 2484 -> 14
        frequencyMhz in 2412..2472 -> (frequencyMhz - 2407) / 5
        frequencyMhz == 5935 -> 2
        frequencyMhz in 5955..7115 -> (frequencyMhz - 5950) / 5
        frequencyMhz in 4910..4995 -> (frequencyMhz - 4000) / 5
        frequencyMhz in 5000..5895 -> (frequencyMhz - 5000) / 5
        else -> null
    }

    /** Nível 0..4 a partir do RSSI (mesma escala aproximada usada pelas barras do sistema). */
    fun signalLevel(rssi: Int): Int = when {
        rssi >= -55 -> 4
        rssi >= -66 -> 3
        rssi >= -77 -> 2
        rssi >= -88 -> 1
        else -> 0
    }

    fun signalQuality(rssi: Int): String = when (signalLevel(rssi)) {
        4 -> "Excelente"
        3 -> "Bom"
        2 -> "Regular"
        1 -> "Fraco"
        else -> "Muito fraco"
    }

    /** Converte o inteiro little-endian do Android (ex.: WifiInfo.ipAddress) em "a.b.c.d". */
    fun ipv4FromLittleEndianInt(value: Int): String =
        listOf(value and 0xFF, (value shr 8) and 0xFF, (value shr 16) and 0xFF, (value ushr 24) and 0xFF)
            .joinToString(".")

    fun securityFromCapabilities(capabilities: String): String {
        val c = capabilities.uppercase()
        return when {
            c.contains("SAE") && c.contains("PSK") -> "WPA2/WPA3"
            c.contains("SAE") -> "WPA3"
            c.contains("OWE") -> "OWE (Enhanced Open)"
            c.contains("EAP") -> "WPA-Enterprise"
            c.contains("WPA2") || c.contains("RSN") -> "WPA2"
            c.contains("WPA") -> "WPA"
            c.contains("WEP") -> "WEP"
            else -> "Aberta"
        }
    }

    /** Remove aspas que o Android coloca no SSID e trata o valor "<unknown ssid>". */
    fun cleanSsid(raw: String?): String? {
        if (raw == null) return null
        val s = raw.removeSurrounding("\"")
        return if (s.isBlank() || s == "<unknown ssid>") null else s
    }
}
