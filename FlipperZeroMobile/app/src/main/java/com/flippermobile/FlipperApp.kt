package com.flippermobile

import android.app.Application
import android.os.Build
import com.flippermobile.core.di.AppContainer
import com.flippermobile.domain.model.LogCategory
import kotlinx.coroutines.launch

class FlipperApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val logger = container.logger
        container.appScope.launch {
            container.settings.settings.collect { s ->
                container.haptics.enabled = s.hapticsEnabled
                logger.retention = s.logRetention
                logger.debugEnabled = s.debugLogs
            }
        }
        logger.i(LogCategory.APP, "Flipper Zero Mobile iniciado • ${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        container.statusMonitor.start()
    }
}
