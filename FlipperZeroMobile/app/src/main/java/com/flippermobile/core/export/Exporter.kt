package com.flippermobile.core.export

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.flippermobile.core.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exporta dados gravando um arquivo no cache privado e abrindo a planilha de compartilhamento
 * do Android. Nada é enviado automaticamente: o usuário escolhe o destino.
 */
class Exporter(private val context: Context) {

    suspend fun writeFile(baseName: String, extension: String, content: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // limpa exportações antigas (> 24h)
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val file = File(dir, "${baseName}_${Formatters.fileStamp(System.currentTimeMillis())}.$extension")
        file.writeText(content, Charsets.UTF_8)
        file
    }

    /** Retorna true se a planilha de compartilhamento foi aberta. */
    fun share(file: File, mimeType: String, title: String): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    suspend fun export(baseName: String, extension: String, mimeType: String, title: String, content: String): Result<File> =
        runCatching {
            val file = writeFile(baseName, extension, content)
            if (!share(file, mimeType, title)) throw IllegalStateException("Nenhum app disponível para compartilhar")
            file
        }
}
