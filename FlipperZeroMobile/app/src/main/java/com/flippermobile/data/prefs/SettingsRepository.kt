package com.flippermobile.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flipper_settings")

data class AppSettings(
    val hapticsEnabled: Boolean = true,
    val scanlinesEnabled: Boolean = true,
    val bleScanTimeoutSeconds: Int = 30,
    val bleShowUnnamed: Boolean = true,
    val logRetention: Int = 2000,
    val selectedIrRemoteId: Long = -1L,
    val debugLogs: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val HAPTICS = booleanPreferencesKey("haptics")
        val SCANLINES = booleanPreferencesKey("scanlines")
        val BLE_TIMEOUT = intPreferencesKey("ble_timeout")
        val BLE_UNNAMED = booleanPreferencesKey("ble_unnamed")
        val LOG_RETENTION = intPreferencesKey("log_retention")
        val IR_REMOTE = longPreferencesKey("ir_remote")
        val DEBUG_LOGS = booleanPreferencesKey("debug_logs")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                hapticsEnabled = p[Keys.HAPTICS] ?: true,
                scanlinesEnabled = p[Keys.SCANLINES] ?: true,
                bleScanTimeoutSeconds = p[Keys.BLE_TIMEOUT] ?: 30,
                bleShowUnnamed = p[Keys.BLE_UNNAMED] ?: true,
                logRetention = p[Keys.LOG_RETENTION] ?: 2000,
                selectedIrRemoteId = p[Keys.IR_REMOTE] ?: -1L,
                debugLogs = p[Keys.DEBUG_LOGS] ?: false,
            )
        }

    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[Keys.HAPTICS] = v }
    suspend fun setScanlines(v: Boolean) = context.dataStore.edit { it[Keys.SCANLINES] = v }
    suspend fun setBleTimeout(seconds: Int) = context.dataStore.edit { it[Keys.BLE_TIMEOUT] = seconds.coerceIn(5, 300) }
    suspend fun setBleShowUnnamed(v: Boolean) = context.dataStore.edit { it[Keys.BLE_UNNAMED] = v }
    suspend fun setLogRetention(v: Int) = context.dataStore.edit { it[Keys.LOG_RETENTION] = v.coerceIn(100, 20_000) }
    suspend fun setSelectedIrRemote(id: Long) = context.dataStore.edit { it[Keys.IR_REMOTE] = id }
    suspend fun setDebugLogs(v: Boolean) = context.dataStore.edit { it[Keys.DEBUG_LOGS] = v }
}
