package com.flippermobile.domain.ir

/**
 * Codificadores de protocolos IR documentados publicamente.
 * Produzem padrões alternados (marca/espaço) em microssegundos, começando por marca,
 * no formato exigido por android.hardware.ConsumerIrManager.transmit().
 */
enum class IrProtocol(val displayName: String, val carrierHz: Int, val maxAddress: Int, val maxCommand: Int) {
    NEC("NEC", 38_000, 0xFF, 0xFF),
    NEC_EXT("NEC estendido", 38_000, 0xFFFF, 0xFF),
    SAMSUNG32("Samsung32", 38_000, 0xFF, 0xFF),
    SONY_SIRC12("Sony SIRC 12-bit", 40_000, 0x1F, 0x7F);

    companion object {
        fun fromName(name: String): IrProtocol? = entries.firstOrNull { it.name == name }
    }
}

data class IrSignal(val carrierHz: Int, val pattern: IntArray) {
    val totalDurationUs: Long get() = pattern.fold(0L) { acc, v -> acc + v }

    override fun equals(other: Any?): Boolean =
        other is IrSignal && other.carrierHz == carrierHz && other.pattern.contentEquals(pattern)

    override fun hashCode(): Int = 31 * carrierHz + pattern.contentHashCode()
}

class InvalidIrCodeException(message: String) : IllegalArgumentException(message)

object IrEncoder {

    fun encode(protocol: IrProtocol, address: Int, command: Int): IrSignal {
        if (address !in 0..protocol.maxAddress) {
            throw InvalidIrCodeException("Endereço fora do intervalo para ${protocol.displayName}: 0..${protocol.maxAddress}")
        }
        if (command !in 0..protocol.maxCommand) {
            throw InvalidIrCodeException("Comando fora do intervalo para ${protocol.displayName}: 0..${protocol.maxCommand}")
        }
        val pattern = when (protocol) {
            IrProtocol.NEC -> nec(address, address.inv() and 0xFF, command)
            IrProtocol.NEC_EXT -> nec(address and 0xFF, (address ushr 8) and 0xFF, command)
            IrProtocol.SAMSUNG32 -> samsung32(address, command)
            IrProtocol.SONY_SIRC12 -> sony12(address, command)
        }
        return IrSignal(protocol.carrierHz, pattern)
    }

    private const val NEC_UNIT = 562
    private const val NEC_ONE_SPACE = 1687

    private fun nec(addrLow: Int, addrHigh: Int, command: Int): IntArray {
        val b = PatternBuilder()
        b.mark(9000).space(4500)
        val bytes = intArrayOf(addrLow, addrHigh, command, command.inv() and 0xFF)
        for (byte in bytes) pulseDistanceByte(b, byte)
        b.mark(NEC_UNIT)
        b.space(40_000) // intervalo final mínimo antes de outro quadro
        return b.build()
    }

    private fun samsung32(address: Int, command: Int): IntArray {
        val b = PatternBuilder()
        b.mark(4500).space(4500)
        val bytes = intArrayOf(address, address, command, command.inv() and 0xFF)
        for (byte in bytes) pulseDistanceByte(b, byte)
        b.mark(NEC_UNIT)
        b.space(47_000)
        return b.build()
    }

    private fun pulseDistanceByte(b: PatternBuilder, byte: Int) {
        for (bit in 0 until 8) { // LSB primeiro
            b.mark(NEC_UNIT)
            b.space(if ((byte shr bit) and 1 == 1) NEC_ONE_SPACE else NEC_UNIT)
        }
    }

    /** SIRC 12 bits: 7 bits de comando + 5 de endereço, LSB primeiro, quadro repetido 3x a cada 45 ms. */
    private fun sony12(address: Int, command: Int): IntArray {
        val b = PatternBuilder()
        val bits = (command and 0x7F) or ((address and 0x1F) shl 7)
        repeat(3) {
            var frameUs = 0
            b.mark(2400); b.space(600); frameUs += 3000
            for (i in 0 until 12) {
                val markUs = if ((bits shr i) and 1 == 1) 1200 else 600
                b.mark(markUs); b.space(600); frameUs += markUs + 600
            }
            b.space(45_000 - frameUs) // completa o período de 45 ms
        }
        return b.build()
    }
}

/** Monta um padrão alternado, fundindo durações consecutivas do mesmo tipo. */
internal class PatternBuilder {
    private val values = ArrayList<Int>()
    private var lastWasMark = false

    fun mark(us: Int): PatternBuilder = add(us, true)
    fun space(us: Int): PatternBuilder = add(us, false)

    private fun add(us: Int, isMark: Boolean): PatternBuilder {
        if (us <= 0) return this
        if (values.isEmpty()) {
            require(isMark) { "O padrão deve começar com marca" }
            values.add(us)
        } else if (isMark == lastWasMark) {
            values[values.lastIndex] = values.last() + us
        } else {
            values.add(us)
        }
        lastWasMark = isMark
        return this
    }

    fun build(): IntArray = values.toIntArray()
}
