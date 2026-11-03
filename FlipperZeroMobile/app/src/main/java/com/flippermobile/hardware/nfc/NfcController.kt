package com.flippermobile.hardware.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import com.flippermobile.core.logging.ActivityLogger
import com.flippermobile.domain.model.LogCategory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Controla o Reader Mode do NfcAdapter. O modo leitor só fica ativo quando:
 * (1) a Activity está em primeiro plano e (2) a tela NFC solicitou leitura.
 */
class NfcController(context: Context, private val logger: ActivityLogger) {

    private val adapter: NfcAdapter? = try {
        NfcAdapter.getDefaultAdapter(context.applicationContext)
    } catch (e: Exception) {
        null
    }

    val isSupported: Boolean get() = adapter != null
    fun isEnabled(): Boolean = try { adapter?.isEnabled == true } catch (e: Exception) { false }

    private val _tags = MutableSharedFlow<Tag>(extraBufferCapacity = 4)
    val tags: SharedFlow<Tag> = _tags.asSharedFlow()

    private val _readerActive = MutableStateFlow(false)
    val readerActive: StateFlow<Boolean> = _readerActive.asStateFlow()

    private var activityRef: WeakReference<Activity>? = null
    private var requested = false

    private val callback = NfcAdapter.ReaderCallback { tag ->
        // Executado em thread de binder do sistema.
        logger.i(LogCategory.NFC, "Tag detectada: ${tag.techList.joinToString { it.substringAfterLast('.') }}")
        _tags.tryEmit(tag)
    }

    fun onActivityResumed(activity: Activity) {
        activityRef = WeakReference(activity)
        apply()
    }

    fun onActivityPaused(activity: Activity) {
        disable(activity)
        activityRef = null
    }

    fun setScanningRequested(enable: Boolean) {
        requested = enable
        apply()
    }

    private fun apply() {
        val activity = activityRef?.get() ?: return
        if (requested) enable(activity) else disable(activity)
    }

    private fun enable(activity: Activity) {
        val a = adapter ?: return
        if (!isEnabled()) {
            _readerActive.value = false
            return
        }
        if (_readerActive.value) return
        try {
            val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE
            val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
            a.enableReaderMode(activity, callback, flags, extras)
            _readerActive.value = true
            logger.i(LogCategory.NFC, "Modo leitor NFC ativado")
        } catch (e: Exception) {
            _readerActive.value = false
            logger.e(LogCategory.NFC, "Não foi possível ativar o modo leitor", e)
        }
    }

    private fun disable(activity: Activity) {
        val a = adapter ?: return
        if (!_readerActive.value) return
        try {
            a.disableReaderMode(activity)
            logger.i(LogCategory.NFC, "Modo leitor NFC desativado")
        } catch (e: Exception) {
            logger.w(LogCategory.NFC, "Falha ao desativar modo leitor: ${e.message}")
        } finally {
            _readerActive.value = false
        }
    }
}
