package com.flippermobile.core.di

import android.content.Context
import com.flippermobile.core.export.Exporter
import com.flippermobile.core.haptics.Haptics
import com.flippermobile.core.logging.ActivityLogger
import com.flippermobile.data.db.AppDatabase
import com.flippermobile.data.prefs.SettingsRepository
import com.flippermobile.data.repository.BleRepository
import com.flippermobile.data.repository.IrRepository
import com.flippermobile.data.repository.LogRepository
import com.flippermobile.data.repository.NfcRepository
import com.flippermobile.data.repository.WifiRepository
import com.flippermobile.hardware.ble.BleScanner
import com.flippermobile.hardware.ble.GattInspector
import com.flippermobile.hardware.device.DeviceInfoProvider
import com.flippermobile.hardware.ir.IrTransmitter
import com.flippermobile.hardware.nfc.NfcController
import com.flippermobile.hardware.status.SystemStatusMonitor
import com.flippermobile.hardware.wifi.WifiAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Injeção de dependências manual (escopo de aplicação). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Escopo de longa duração para trabalhos de aplicação (logs, timeouts de hardware). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val settings = SettingsRepository(appContext)

    val logRepository by lazy { LogRepository(database.logDao()) }
    val nfcRepository by lazy { NfcRepository(database.nfcDao()) }
    val bleRepository by lazy { BleRepository(database.bleDao()) }
    val wifiRepository by lazy { WifiRepository(database.wifiDao()) }
    val irRepository by lazy { IrRepository(database.irDao()) }

    val logger by lazy { ActivityLogger(logRepository, ioScope) }
    val haptics by lazy { Haptics(appContext) }
    val exporter by lazy { Exporter(appContext) }

    val nfcController by lazy { NfcController(appContext, logger) }
    val bleScanner by lazy { BleScanner(appContext, logger, appScope) }
    val gattInspector by lazy { GattInspector(appContext, bleScanner, logger, appScope) }
    val wifiAnalyzer by lazy { WifiAnalyzer(appContext, logger) }
    val irTransmitter by lazy { IrTransmitter(appContext) }
    val deviceInfo by lazy { DeviceInfoProvider(appContext, irTransmitter) }
    val statusMonitor by lazy { SystemStatusMonitor(appContext, nfcController, bleScanner, irTransmitter) }
}
