package com.flippermobile.core.util

/** Utilitários puros (sem Android) para representar bytes. */
object HexUtils {
    private val HEX = "0123456789ABCDEF".toCharArray()

    /** "04:A2:3B" quando [separator] = ":"; string vazia para array vazio. */
    fun toHex(bytes: ByteArray?, separator: String = ""): String {
        if (bytes == null || bytes.isEmpty()) return ""
        val sb = StringBuilder(bytes.size * (2 + separator.length))
        bytes.forEachIndexed { i, b ->
            if (i > 0) sb.append(separator)
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /** Converte "04 A2 3b" / "04:A2:3B" / "04a23b" em bytes. Retorna null se inválido. */
    fun fromHex(text: String): ByteArray? {
        val clean = text.filter { !it.isWhitespace() && it != ':' && it != '-' }
        if (clean.length % 2 != 0) return null
        if (clean.any { Character.digit(it, 16) < 0 }) return null
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) or Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
    }

    /**
     * Dump estilo hexdump: offset, 16 bytes em hex e coluna ASCII imprimível.
     */
    fun hexDump(bytes: ByteArray, bytesPerLine: Int = 16): String {
        if (bytes.isEmpty()) return "(vazio)"
        val sb = StringBuilder()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + bytesPerLine, bytes.size)
            sb.append(String.format("%04X  ", offset))
            for (i in offset until offset + bytesPerLine) {
                if (i < end) sb.append(toHex(byteArrayOf(bytes[i]))).append(' ') else sb.append("   ")
            }
            sb.append(' ')
            for (i in offset until end) {
                val c = bytes[i].toInt() and 0xFF
                sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
            }
            if (end < bytes.size) sb.append('\n')
            offset = end
        }
        return sb.toString()
    }

    /**
     * Decodifica UTF-8 somente quando o resultado é majoritariamente texto legível.
     * Retorna null para dados binários, evitando exibir "lixo" como se fosse texto.
     */
    fun utf8IfPrintable(bytes: ByteArray, minPrintableRatio: Double = 0.85): String? {
        if (bytes.isEmpty()) return null
        val decoder = Charsets.UTF_8.newDecoder()
        val text = try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            return null
        }
        if (text.isEmpty()) return null
        val printable = text.count { !it.isISOControl() || it == '\n' || it == '\r' || it == '\t' }
        return if (printable.toDouble() / text.length >= minPrintableRatio) text else null
    }
}
