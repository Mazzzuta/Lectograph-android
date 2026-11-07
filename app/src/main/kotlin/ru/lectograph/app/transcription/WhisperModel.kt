package ru.lectograph.app.transcription

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CancellationException

object WhisperModel {
    const val SIZE = 147951465L
    private const val SHA256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"
    fun file(context: Context) = File(context.filesDir, "models/ggml-base.bin")
    fun ready(context: Context) = file(context).length() == SIZE
    fun download(context: Context, cancelled: () -> Boolean, progress: (Int) -> Unit) {
        val target = file(context)
        val dir = requireNotNull(target.parentFile)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Не удалось создать папку модели")
        if (dir.usableSpace < SIZE + 32 * 1024 * 1024L) throw IOException("Для модели нужно не менее 180 МБ свободного места")
        val connection = URL("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin").openConnection() as HttpURLConnection
        connection.connectTimeout = 20000; connection.readTimeout = 20000
        val atomic = AtomicFile(target)
        var output: FileOutputStream? = null
        try {
            if (connection.responseCode != 200) throw IOException("Сервер модели недоступен. Повторите загрузку позже.")
            val digest = MessageDigest.getInstance("SHA-256")
            val stream = atomic.startWrite(); output = stream
            connection.inputStream.use { input ->
                val buffer = ByteArray(65536); var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (cancelled()) throw CancellationException()
                    total += count
                    if (total > SIZE) throw IOException("Размер модели не совпадает")
                    stream.write(buffer, 0, count); digest.update(buffer, 0, count)
                    progress((100 * total / SIZE).toInt())
                }
                val hash = digest.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
                if (total != SIZE || hash != SHA256) throw IOException("Модель загружена с ошибкой. Повторите загрузку.")
            }
            if (cancelled()) throw CancellationException()
            atomic.finishWrite(stream); output = null
        } finally { output?.let(atomic::failWrite); connection.disconnect() }
    }
}
