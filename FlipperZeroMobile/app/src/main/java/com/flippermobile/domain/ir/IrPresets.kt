package com.flippermobile.domain.ir

enum class IrButton(val label: String) {
    POWER("Power"), VOL_UP("Vol +"), VOL_DOWN("Vol -"), MUTE("Mudo"),
    CH_UP("Canal +"), CH_DOWN("Canal -"), UP("Cima"), DOWN("Baixo"),
    LEFT("Esquerda"), RIGHT("Direita"), OK("OK"), MENU("Menu"),
    INPUT("Entrada"), BACK("Voltar");

    companion object {
        fun fromName(name: String): IrButton? = entries.firstOrNull { it.name == name }
    }
}

/** Configuração de controle remoto: protocolo + endereço + comando por botão. */
data class IrRemoteConfig(
    val id: Long = 0,
    val name: String,
    val protocol: IrProtocol,
    val address: Int,
    val commands: Map<IrButton, Int>,
    val builtIn: Boolean = false,
) {
    fun signalFor(button: IrButton): IrSignal? =
        commands[button]?.let { IrEncoder.encode(protocol, address, it) }

    companion object {
        /** Serializa "POWER=8;VOL_UP=2" (decimal) para armazenamento local. */
        fun encodeCommands(commands: Map<IrButton, Int>): String =
            commands.entries.sortedBy { it.key.ordinal }.joinToString(";") { "${it.key.name}=${it.value}" }

        fun decodeCommands(text: String): Map<IrButton, Int> =
            text.split(';').mapNotNull { part ->
                val kv = part.split('=')
                if (kv.size != 2) return@mapNotNull null
                val button = IrButton.fromName(kv[0].trim()) ?: return@mapNotNull null
                val value = kv[1].trim().toIntOrNull() ?: return@mapNotNull null
                button to value
            }.toMap()
    }
}

/**
 * Predefinições baseadas em códigos amplamente documentados em bases públicas
 * (ex.: LIRC/IRDB). A compatibilidade depende do modelo do aparelho.
 */
object IrPresets {
    val LG_TV = IrRemoteConfig(
        id = -1, name = "TV LG (NEC 0x04)", protocol = IrProtocol.NEC, address = 0x04, builtIn = true,
        commands = mapOf(
            IrButton.POWER to 0x08, IrButton.VOL_UP to 0x02, IrButton.VOL_DOWN to 0x03,
            IrButton.MUTE to 0x09, IrButton.CH_UP to 0x00, IrButton.CH_DOWN to 0x01,
            IrButton.UP to 0x40, IrButton.DOWN to 0x41, IrButton.LEFT to 0x07,
            IrButton.RIGHT to 0x06, IrButton.OK to 0x44, IrButton.MENU to 0x43,
            IrButton.INPUT to 0x0B, IrButton.BACK to 0x28,
        )
    )

    val SAMSUNG_TV = IrRemoteConfig(
        id = -2, name = "TV Samsung (Samsung32 0x07)", protocol = IrProtocol.SAMSUNG32, address = 0x07, builtIn = true,
        commands = mapOf(
            IrButton.POWER to 0x02, IrButton.VOL_UP to 0x07, IrButton.VOL_DOWN to 0x0B,
            IrButton.MUTE to 0x0F, IrButton.CH_UP to 0x12, IrButton.CH_DOWN to 0x10,
            IrButton.UP to 0x60, IrButton.DOWN to 0x61, IrButton.LEFT to 0x65,
            IrButton.RIGHT to 0x62, IrButton.OK to 0x68, IrButton.MENU to 0x1A,
            IrButton.INPUT to 0x01, IrButton.BACK to 0x58,
        )
    )

    val SONY_TV = IrRemoteConfig(
        id = -3, name = "TV Sony (SIRC 12-bit, end. 1)", protocol = IrProtocol.SONY_SIRC12, address = 1, builtIn = true,
        commands = mapOf(
            IrButton.POWER to 21, IrButton.VOL_UP to 18, IrButton.VOL_DOWN to 19,
            IrButton.MUTE to 20, IrButton.CH_UP to 16, IrButton.CH_DOWN to 17,
            IrButton.UP to 116, IrButton.DOWN to 117, IrButton.LEFT to 52,
            IrButton.RIGHT to 51, IrButton.OK to 101, IrButton.MENU to 96,
            IrButton.INPUT to 37,
        )
    )

    val all: List<IrRemoteConfig> = listOf(LG_TV, SAMSUNG_TV, SONY_TV)
}

object IrCodeParser {
    /** Converte "0x1F", "1Fh", "1f" ou "31" em inteiro (hex quando prefixado/sufixado ou com dígitos A-F). */
    fun parseCode(text: String): Int? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        return when {
            t.startsWith("0x") -> t.substring(2).toIntOrNull(16)
            t.endsWith("h") -> t.dropLast(1).toIntOrNull(16)
            t.any { it in 'a'..'f' } -> t.toIntOrNull(16)
            else -> t.toIntOrNull()
        }
    }

    fun format(value: Int): String = String.format("0x%02X", value)
}
