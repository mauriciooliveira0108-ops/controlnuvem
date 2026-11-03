package com.flippermobile.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Feedback tátil curto, respeitando a preferência do usuário e a presença de vibrador. */
class Haptics(context: Context) {
    @Volatile var enabled: Boolean = true

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    val available: Boolean get() = vibrator?.hasVibrator() == true

    fun tick() = vibrate(15)
    fun success() = vibrate(40)
    fun error() = vibrate(120)

    private fun vibrate(ms: Long) {
        if (!enabled) return
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
            // Alguns fabricantes lançam SecurityException; feedback tátil é opcional.
        }
    }
}
