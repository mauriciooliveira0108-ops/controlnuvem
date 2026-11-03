package com.flippermobile.hardware.device

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.nfc.NfcAdapter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.flippermobile.core.util.Formatters
import com.flippermobile.domain.model.Compat
import com.flippermobile.domain.model.InfoItem
import com.flippermobile.domain.model.InfoSection
import com.flippermobile.hardware.ir.IrTransmitter
import java.io.File

/** Coleta informações reais do aparelho usando APIs públicas. */
class DeviceInfoProvider(context: Context, private val ir: IrTransmitter) {
    private val ctx = context.applicationContext
    private val pm = ctx.packageManager

    fun collect(): List<InfoSection> = listOf(
        system(), cpu(), memory(), battery(), connectivity(), sensors(), features()
    )

    private fun system() = InfoSection(
        "Sistema",
        listOf(
            InfoItem("Fabricante", Build.MANUFACTURER.replaceFirstChar { it.uppercase() }),
            InfoItem("Marca", Build.BRAND),
            InfoItem("Modelo", Build.MODEL),
            InfoItem("Dispositivo", Build.DEVICE),
            InfoItem("Produto", Build.PRODUCT),
            InfoItem("Android", Build.VERSION.RELEASE),
            InfoItem("API Level", Build.VERSION.SDK_INT.toString()),
            InfoItem("Patch de segurança", Build.VERSION.SECURITY_PATCH.ifBlank { "—" }),
            InfoItem("Build", Build.DISPLAY),
            InfoItem("Tempo ligado", formatUptime(SystemClock.elapsedRealtime())),
        )
    )

    private fun cpu(): InfoSection {
        val items = mutableListOf(
            InfoItem("ABIs suportadas", Build.SUPPORTED_ABIS.joinToString()),
            InfoItem("Arquitetura (os.arch)", System.getProperty("os.arch") ?: "—"),
            InfoItem("Núcleos", Runtime.getRuntime().availableProcessors().toString()),
            InfoItem("Hardware", Build.HARDWARE),
            InfoItem("Placa", Build.BOARD),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items += InfoItem("SoC", "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim())
        }
        readCpuInfoHardware()?.let { items += InfoItem("CPU (/proc/cpuinfo)", it) }
        maxCpuFrequency()?.let { items += InfoItem("Frequência máx.", it) }
        return InfoSection("Processador", items)
    }

    private fun memory(): InfoSection {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val rt = Runtime.getRuntime()
        return InfoSection(
            "Memória e armazenamento",
            listOfNotNull(
                InfoItem("RAM total", Formatters.bytes(mi.totalMem)),
                InfoItem("RAM disponível", Formatters.bytes(mi.availMem)),
                InfoItem("Memória baixa", Formatters.yesNo(mi.lowMemory)),
                InfoItem("Heap máx. do app", Formatters.bytes(rt.maxMemory())),
                stat?.let { InfoItem("Armazenamento total", Formatters.bytes(it.totalBytes)) },
                stat?.let { InfoItem("Armazenamento livre", Formatters.bytes(it.availableBytes)) },
            )
        )
    }

    private fun battery(): InfoSection {
        val intent: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Carregando"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descarregando"
            BatteryManager.BATTERY_STATUS_FULL -> "Completa"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Não carregando"
            else -> "Desconhecido"
        }
        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Boa"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Superaquecida"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Esgotada"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensão"
            BatteryManager.BATTERY_HEALTH_COLD -> "Fria"
            else -> "Desconhecida"
        }
        val plug = when (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Tomada"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Sem fio"
            else -> "Desconectado"
        }
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        val currentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
        return InfoSection(
            "Bateria",
            listOfNotNull(
                InfoItem("Nível", if (pct >= 0) "$pct%" else "—"),
                InfoItem("Estado", status),
                InfoItem("Saúde", health),
                InfoItem("Fonte", plug),
                temp?.let { InfoItem("Temperatura", String.format("%.1f °C", it / 10f)) },
                voltage?.let { InfoItem("Tensão", "$it mV") },
                tech?.let { InfoItem("Tecnologia", it) },
                currentUa?.let { InfoItem("Corrente instantânea", "${it / 1000} mA") },
            )
        )
    }

    private fun connectivity(): InfoSection {
        val nfc = runCatching { NfcAdapter.getDefaultAdapter(ctx) }.getOrNull()
        val bt = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        val hasBle = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val items = mutableListOf(
            InfoItem(
                "NFC",
                when {
                    nfc == null -> "Não suportado"
                    runCatching { nfc.isEnabled }.getOrDefault(false) -> "Suportado (ligado)"
                    else -> "Suportado (desligado)"
                },
                if (nfc != null) Compat.SUPPORTED else Compat.UNSUPPORTED,
            ),
            InfoItem("NFC Host Card Emulation", Formatters.yesNo(pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)),
                if (pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) Compat.SUPPORTED else Compat.UNSUPPORTED),
            InfoItem("MIFARE Classic (NXP)", Formatters.yesNo(pm.hasSystemFeature("com.nxp.mifare")),
                if (pm.hasSystemFeature("com.nxp.mifare")) Compat.SUPPORTED else Compat.PARTIAL),
            InfoItem("Bluetooth", if (bt == null) "Não suportado" else if (runCatching { bt.isEnabled }.getOrDefault(false)) "Ligado" else "Desligado",
                if (bt != null) Compat.SUPPORTED else Compat.UNSUPPORTED),
            InfoItem("Bluetooth LE", Formatters.yesNo(hasBle), if (hasBle) Compat.SUPPORTED else Compat.UNSUPPORTED),
        )
        if (bt != null) {
            val ext = runCatching { bt.isLeExtendedAdvertisingSupported }.getOrDefault(false)
            val coded = runCatching { bt.isLeCodedPhySupported }.getOrDefault(false)
            val twoM = runCatching { bt.isLe2MPhySupported }.getOrDefault(false)
            items += InfoItem("BLE 5: PHY 2M / Coded / Ext. adv.", "${Formatters.yesNo(twoM)} / ${Formatters.yesNo(coded)} / ${Formatters.yesNo(ext)}",
                if (twoM || coded || ext) Compat.SUPPORTED else Compat.PARTIAL)
        }
        val wifi = pm.hasSystemFeature(PackageManager.FEATURE_WIFI)
        items += InfoItem("Wi-Fi", Formatters.yesNo(wifi), if (wifi) Compat.SUPPORTED else Compat.UNSUPPORTED)
        items += InfoItem("Wi-Fi Direct", Formatters.yesNo(pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)),
            if (pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) Compat.SUPPORTED else Compat.UNSUPPORTED)
        items += InfoItem("Wi-Fi Aware", Formatters.yesNo(pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)),
            if (pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) Compat.SUPPORTED else Compat.UNSUPPORTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val rtt = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)
            items += InfoItem("Wi-Fi RTT", Formatters.yesNo(rtt), if (rtt) Compat.SUPPORTED else Compat.UNSUPPORTED)
        }
        items += InfoItem(
            "Infravermelho",
            if (ir.hasEmitter) "Emissor presente" else "Emissor infravermelho indisponível",
            if (ir.hasEmitter) Compat.SUPPORTED else Compat.UNSUPPORTED,
        )
        if (ir.hasEmitter && ir.carrierRanges.isNotEmpty()) {
            items += InfoItem("Faixas IR", ir.carrierRanges.joinToString { "${it.minHz}–${it.maxHz} Hz" })
        }
        val telephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        items += InfoItem("Telefonia", Formatters.yesNo(telephony), if (telephony) Compat.SUPPORTED else Compat.UNSUPPORTED)
        val uwb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pm.hasSystemFeature("android.hardware.uwb")
        items += InfoItem("UWB", Formatters.yesNo(uwb), if (uwb) Compat.SUPPORTED else Compat.UNSUPPORTED)
        val usbHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        items += InfoItem("USB Host (OTG)", Formatters.yesNo(usbHost), if (usbHost) Compat.SUPPORTED else Compat.UNSUPPORTED)
        return InfoSection("Conectividade", items)
    }

    private fun sensors(): InfoSection {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val list = sm?.getSensorList(Sensor.TYPE_ALL) ?: emptyList()
        if (list.isEmpty()) return InfoSection("Sensores", listOf(InfoItem("Sensores", "Nenhum informado", Compat.UNSUPPORTED)))
        return InfoSection(
            "Sensores (${list.size})",
            list.sortedBy { it.type }.map { s ->
                InfoItem(s.name, "${s.vendor} • tipo ${s.stringType.substringAfterLast('.')} • ${String.format("%.2f mA", s.power)}", Compat.SUPPORTED)
            }
        )
    }

    private fun features(): InfoSection {
        fun f(label: String, feature: String) =
            pm.hasSystemFeature(feature).let { InfoItem(label, Formatters.yesNo(it), if (it) Compat.SUPPORTED else Compat.UNSUPPORTED) }
        return InfoSection(
            "Outros recursos",
            listOf(
                f("Câmera", PackageManager.FEATURE_CAMERA_ANY),
                f("Flash", PackageManager.FEATURE_CAMERA_FLASH),
                f("GPS", PackageManager.FEATURE_LOCATION_GPS),
                f("Biometria (digital)", PackageManager.FEATURE_FINGERPRINT),
                f("Microfone", PackageManager.FEATURE_MICROPHONE),
                f("Vibração (háptica)", "android.hardware.vibrator"),
                f("Tela sensível ao toque", PackageManager.FEATURE_TOUCHSCREEN),
            )
        )
    }

    private fun readCpuInfoHardware(): String? = runCatching {
        File("/proc/cpuinfo").readLines()
            .firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") }
            ?.substringAfter(':')?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun maxCpuFrequency(): String? = runCatching {
        val cpus = File("/sys/devices/system/cpu").listFiles()?.filter { it.name.matches(Regex("cpu[0-9]+")) } ?: return@runCatching null
        val max = cpus.mapNotNull { File(it, "cpufreq/cpuinfo_max_freq").takeIf { f -> f.canRead() }?.readText()?.trim()?.toLongOrNull() }.maxOrNull()
        max?.let { String.format("%.2f GHz", it / 1_000_000.0) }
    }.getOrNull()

    private fun formatUptime(ms: Long): String {
        val s = ms / 1000
        return String.format("%dd %02dh %02dm", s / 86400, (s % 86400) / 3600, (s % 3600) / 60)
    }
}
