package com.flippermobile.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Formatação pura de valores exibidos na interface. */
object Formatters {
    private val ptBR = Locale("pt", "BR")

    fun timestamp(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val f = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", ptBR)
        f.timeZone = timeZone
        return f.format(Date(millis))
    }

    fun timeOnly(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val f = SimpleDateFormat("HH:mm:ss.SSS", ptBR)
        f.timeZone = timeZone
        return f.format(Date(millis))
    }

    fun fileStamp(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val f = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        f.timeZone = timeZone
        return f.format(Date(millis))
    }

    /** 1536 -> "1,5 KB" (base 1024, vírgula decimal pt-BR). */
    fun bytes(value: Long): String {
        if (value < 0) return "—"
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = value.toDouble() / 1024.0
        var i = 0
        while (v >= 1024.0 && i < units.lastIndex) {
            v /= 1024.0; i++
        }
        return String.format(ptBR, "%.1f %s", v, units[i])
    }

    fun percent(value: Int): String = "${value.coerceIn(0, 100)}%"

    fun dbm(rssi: Int): String = "$rssi dBm"

    fun yesNo(value: Boolean): String = if (value) "Sim" else "Não"
}
