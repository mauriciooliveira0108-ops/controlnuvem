package com.flippermobile.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flippermobile.core.di.AppContainer
import com.flippermobile.data.prefs.AppSettings
import com.flippermobile.domain.model.LogCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val hapticsAvailable: Boolean get() = c.haptics.available

    private fun change(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            c.logger.i(LogCategory.APP, "Preferência alterada: $label")
        }
    }

    fun setHaptics(v: Boolean) = change("háptico=$v") { c.settings.setHaptics(v) }
    fun setScanlines(v: Boolean) = change("scanlines=$v") { c.settings.setScanlines(v) }
    fun setBleTimeout(v: Int) = change("timeout BLE=${v}s") { c.settings.setBleTimeout(v) }
    fun setBleShowUnnamed(v: Boolean) = change("BLE sem nome=$v") { c.settings.setBleShowUnnamed(v) }
    fun setLogRetention(v: Int) = change("retenção de logs=$v") { c.settings.setLogRetention(v) }
    fun setDebugLogs(v: Boolean) = change("logs debug=$v") { c.settings.setDebugLogs(v) }
}
