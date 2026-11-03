package com.flippermobile.features.home

import androidx.lifecycle.ViewModel
import com.flippermobile.core.di.AppContainer
import com.flippermobile.hardware.status.SystemStatus
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val status: StateFlow<SystemStatus> = container.statusMonitor.status

    fun refresh() = container.statusMonitor.refresh()
    fun tick() = container.haptics.tick()
}
