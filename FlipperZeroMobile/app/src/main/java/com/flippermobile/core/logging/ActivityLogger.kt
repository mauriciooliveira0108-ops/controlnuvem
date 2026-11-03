package com.flippermobile.core.logging

import android.util.Log
import com.flippermobile.data.repository.LogRepository
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.domain.model.LogEntry
import com.flippermobile.domain.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Registro de atividades real do aplicativo. Os eventos são gravados localmente (Room)
 * de forma sequencial e exibidos no Activity Terminal.
 */
class ActivityLogger(
    private val repository: LogRepository,
    scope: CoroutineScope,
) {
    @Volatile var retention: Int = 2000
    @Volatile var debugEnabled: Boolean = false

    private val queue = Channel<LogEntry>(capacity = Channel.UNLIMITED)
    private var writesSinceTrim = 0

    init {
        scope.launch {
            for (entry in queue) {
                try {
                    repository.insert(entry)
                    if (++writesSinceTrim >= 100) {
                        writesSinceTrim = 0
                        repository.trim(retention)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Falha ao gravar log", e)
                }
            }
        }
    }

    fun log(level: LogLevel, category: LogCategory, message: String) {
        if (level == LogLevel.DEBUG && !debugEnabled) return
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, "$TAG/${category.tag}", message)
        queue.trySend(LogEntry(timestamp = System.currentTimeMillis(), level = level, category = category, message = message))
    }

    fun d(category: LogCategory, message: String) = log(LogLevel.DEBUG, category, message)
    fun i(category: LogCategory, message: String) = log(LogLevel.INFO, category, message)
    fun w(category: LogCategory, message: String) = log(LogLevel.WARN, category, message)
    fun e(category: LogCategory, message: String, error: Throwable? = null) =
        log(LogLevel.ERROR, category, if (error != null) "$message: ${error.javaClass.simpleName}: ${error.message}" else message)

    companion object {
        private const val TAG = "FlipperMobile"
    }
}
