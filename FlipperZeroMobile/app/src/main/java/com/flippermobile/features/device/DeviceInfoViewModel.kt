package com.flippermobile.features.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flippermobile.core.di.AppContainer
import com.flippermobile.domain.model.InfoSection
import com.flippermobile.domain.model.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface DeviceUiState {
    data object Loading : DeviceUiState
    data class Ready(val sections: List<InfoSection>) : DeviceUiState
    data class Error(val message: String) : DeviceUiState
}

class DeviceInfoViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<DeviceUiState>(DeviceUiState.Loading)
    val state: StateFlow<DeviceUiState> = _state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    init { load() }

    fun load() {
        _state.value = DeviceUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val sections = withContext(Dispatchers.IO) { c.deviceInfo.collect() }
                c.logger.i(LogCategory.DEVICE, "Informações do dispositivo coletadas (${sections.sumOf { it.items.size }} itens)")
                DeviceUiState.Ready(sections)
            } catch (e: Exception) {
                c.logger.e(LogCategory.DEVICE, "Falha ao coletar informações", e)
                DeviceUiState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }

    fun export() {
        val s = _state.value as? DeviceUiState.Ready ?: return
        val text = s.sections.joinToString("\n\n") { sec ->
            "== ${sec.title} ==\n" + sec.items.joinToString("\n") { "${it.label}: ${it.value}" }
        }
        viewModelScope.launch {
            c.exporter.export("dispositivo", "txt", "text/plain", "Exportar informações", text)
                .onFailure { _messages.tryEmit("Falha na exportação: ${it.message}") }
        }
    }
}
