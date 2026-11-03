package com.flippermobile.hardware.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.flippermobile.domain.ir.IrSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CarrierRange(val minHz: Int, val maxHz: Int) {
    operator fun contains(hz: Int) = hz in minHz..maxHz
}

/** Acesso ao emissor infravermelho físico via ConsumerIrManager. */
class IrTransmitter(context: Context) {
    private val manager: ConsumerIrManager? = try {
        context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    } catch (e: Exception) {
        null
    }

    val hasEmitter: Boolean = try { manager?.hasIrEmitter() == true } catch (e: Exception) { false }

    val carrierRanges: List<CarrierRange> by lazy {
        if (!hasEmitter) emptyList() else try {
            manager?.carrierFrequencies?.map { CarrierRange(it.minFrequency, it.maxFrequency) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Verdadeiro se a portadora é suportada (ou se o aparelho não informa as faixas). */
    fun supportsCarrier(hz: Int): Boolean = carrierRanges.isEmpty() || carrierRanges.any { hz in it }

    /** Transmite de forma bloqueante fora da thread principal. */
    suspend fun transmit(signal: IrSignal): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val m = manager ?: error("Emissor infravermelho indisponível neste aparelho")
            check(hasEmitter) { "Emissor infravermelho indisponível neste aparelho" }
            require(supportsCarrier(signal.carrierHz)) { "Frequência ${signal.carrierHz} Hz não suportada pelo emissor" }
            m.transmit(signal.carrierHz, signal.pattern)
        }
    }
}
