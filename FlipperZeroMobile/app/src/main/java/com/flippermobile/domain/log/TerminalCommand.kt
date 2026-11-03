package com.flippermobile.domain.log

/** Comandos internos do Activity Terminal. Não há acesso a shell/root: apenas ações do próprio app. */
sealed interface TerminalCommand {
    data object Help : TerminalCommand
    data object Clear : TerminalCommand
    data object Status : TerminalCommand
    data object Export : TerminalCommand
    data object Version : TerminalCommand
    data class Search(val query: String) : TerminalCommand
    data class Echo(val text: String) : TerminalCommand
    data class Unknown(val input: String) : TerminalCommand
    data object Empty : TerminalCommand

    companion object {
        val HELP_TEXT = listOf(
            "help            lista comandos",
            "status          estado de NFC, Bluetooth, Wi-Fi, IR e bateria",
            "find <texto>    filtra os logs (vazio remove o filtro)",
            "clear           apaga os logs locais",
            "export          exporta os logs como .txt",
            "version         versão do app e do Android",
            "echo <texto>    registra uma anotação no log",
        )

        fun parse(input: String): TerminalCommand {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return Empty
            val cmd = trimmed.substringBefore(' ').lowercase()
            val arg = trimmed.substringAfter(' ', "").trim()
            return when (cmd) {
                "help", "?" -> Help
                "clear", "cls" -> Clear
                "status" -> Status
                "export" -> Export
                "version", "ver" -> Version
                "find", "grep", "search" -> Search(arg)
                "echo", "note" -> Echo(arg)
                else -> Unknown(trimmed)
            }
        }
    }
}
