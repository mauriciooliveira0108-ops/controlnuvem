package com.flippermobile.domain.log

import com.flippermobile.core.util.Formatters
import com.flippermobile.domain.model.LogCategory
import com.flippermobile.domain.model.LogEntry
import com.flippermobile.domain.model.LogLevel

/** Filtro e formatação pura dos registros do terminal. */
object LogFilter {

    fun filter(
        entries: List<LogEntry>,
        query: String,
        categories: Set<LogCategory> = emptySet(),
        minLevel: LogLevel = LogLevel.DEBUG,
    ): List<LogEntry> {
        val q = query.trim()
        return entries.filter { e ->
            e.level.ordinal >= minLevel.ordinal &&
                (categories.isEmpty() || e.category in categories) &&
                (q.isEmpty() || e.message.contains(q, ignoreCase = true) ||
                    e.category.tag.contains(q, ignoreCase = true) ||
                    e.level.name.contains(q, ignoreCase = true))
        }
    }

    fun levelSymbol(level: LogLevel): String = when (level) {
        LogLevel.DEBUG -> "D"
        LogLevel.INFO -> "I"
        LogLevel.WARN -> "W"
        LogLevel.ERROR -> "E"
    }

    fun formatLine(entry: LogEntry, timeFormatter: (Long) -> String = Formatters::timestamp): String =
        "${timeFormatter(entry.timestamp)} ${levelSymbol(entry.level)}/${entry.category.tag}: ${entry.message}"
}
