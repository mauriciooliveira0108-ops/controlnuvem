package com.flippermobile.features.infrared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flippermobile.core.di.AppContainer
import com.flippermobile.domain.export.ExportFormatter
import com.flippermobile.domain.ir.IrButton
import com.flippermobile.domain.ir.IrCodeParser.parseCode
import com.flippermobile.domain.ir.IrEncoder
import com.flippermobile.domain.ir.IrPresets
import com.flippermobile.domain.ir.IrProtocol
import com.flippermobile.domain.ir.IrRemoteConfig
import com.flippermobile.domain.ir.IrSignal
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.hardware.ir.CarrierRange
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InfraredViewModel(private val c: AppContainer) : ViewModel() {
    private val ir = c.irTransmitter

    val hasEmitter: Boolean = ir.hasEmitter
    val carrierRanges: List<CarrierRange> = ir.carrierRanges

    val remotes: StateFlow<List<IrRemoteConfig>> = c.irRepository.custom
        .map { IrPresets.all + it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IrPresets.all)

    private val selectedId = c.settings.settings.map { it.selectedIrRemoteId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, IrPresets.LG_TV.id)

    val selected: StateFlow<IrRemoteConfig> = combine(remotes, selectedId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.first()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IrPresets.LG_TV)

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun select(config: IrRemoteConfig) {
        viewModelScope.launch { c.settings.setSelectedIrRemote(config.id) }
    }

    fun press(button: IrButton) {
        val cfg = selected.value
        val code = cfg.commands[button]
        if (code == null) {
            _status.value = "Botão ${button.label} não configurado em ${cfg.name}"
            return
        }
        send(IrEncoder.encode(cfg.protocol, cfg.address, code), "${cfg.name} • ${button.label} (cmd 0x%02X)".format(code))
    }

    fun sendCustom(protocol: IrProtocol, addressText: String, commandText: String) {
        val address = parseCode(addressText)
        val command = parseCode(commandText)
        if (address == null || command == null) {
            _status.value = "Endereço/comando inválidos"
            return
        }
        val signal = runCatching { IrEncoder.encode(protocol, address, command) }.getOrElse {
            _status.value = it.message
            return
        }
        send(signal, "${protocol.displayName} addr 0x%X cmd 0x%X".format(address, command))
    }

    private fun send(signal: IrSignal, description: String) {
        if (!hasEmitter) {
            _status.value = "Emissor infravermelho indisponível neste aparelho"
            c.logger.w(LogCategory.IR, "Transmissão ignorada: sem emissor IR")
            return
        }
        if (_sending.value) return
        _sending.value = true
        viewModelScope.launch {
            c.haptics.tick()
            ir.transmit(signal)
                .onSuccess {
                    _status.value = "Enviado: $description"
                    c.logger.i(LogCategory.IR, "IR enviado: $description • ${signal.carrierHz} Hz • ${signal.pattern.size} pulsos")
                }
                .onFailure {
                    _status.value = "Falha: ${it.message}"
                    c.logger.e(LogCategory.IR, "Falha ao transmitir IR", it)
                    c.haptics.error()
                }
            _sending.value = false
        }
    }

    fun saveRemote(config: IrRemoteConfig) {
        if (config.name.isBlank()) {
            _messages.tryEmit("Informe um nome")
            return
        }
        viewModelScope.launch {
            runCatching { c.irRepository.save(config.copy(builtIn = false)) }
                .onSuccess { id ->
                    c.settings.setSelectedIrRemote(id)
                    c.logger.i(LogCategory.STORAGE, "Controle IR salvo: ${config.name}")
                    _messages.tryEmit("Controle salvo")
                }
                .onFailure { _messages.tryEmit("Erro ao salvar: ${it.message}") }
        }
    }

    fun deleteRemote(config: IrRemoteConfig) {
        if (config.builtIn) return
        viewModelScope.launch {
            c.irRepository.delete(config.id)
            c.settings.setSelectedIrRemote(IrPresets.LG_TV.id)
            _messages.tryEmit("Controle removido")
        }
    }

    fun exportSelected() {
        viewModelScope.launch {
            c.exporter.export("ir_controle", "txt", "text/plain", "Exportar controle IR", ExportFormatter.irConfigText(selected.value))
                .onFailure { _messages.tryEmit("Falha na exportação: ${it.message}") }
        }
    }
}
