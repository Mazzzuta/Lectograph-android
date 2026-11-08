package ru.lectograph.app.ai

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import ru.lectograph.app.BuildConfig

object GeminiClient {
    // AI is paused until a provider is selected.
    fun configured() = false
    fun request(context: Context, action: String, text: String, question: String): String {
        if (!configured()) throw IOException("Сервер обработки пока не подключён.")
        val preferences = context.getSharedPreferences("installation", 0)
        val id = preferences.getString("id", "").orEmpty().ifEmpty { UUID.randomUUID().toString().also { preferences.edit().putString("id", it).commit() } }
        val input = JSONObject().put("text", text)
        if (action == "question") input.put("question", question)
        val connection = URI.create(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/" + action).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15000; connection.readTimeout = 105000; connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json"); connection.setRequestProperty("X-Installation-Id", id)
        connection.instanceFollowRedirects = false; connection.doOutput = true
        try {
            val bytes = input.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size); connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = (if (code == 200) connection.inputStream else connection.errorStream) ?: throw IOException("Сервер обработки недоступен.")
            val body = stream.use { inputStream ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = inputStream.read(buffer); if (count == -1) break
                        if (output.size() + count > 512000) throw IOException("Ответ сервера слишком большой.")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray().toString(Charsets.UTF_8)
                }
            }
            val result = JSONObject(body)
            if (code != 200) throw IOException(result.optString("error", "Сервер обработки недоступен."))
            when (action) {
                "study" -> { result.getString("detailed"); result.getString("short"); result.getJSONArray("clarifications"); result.getJSONArray("studentQuestions") }
                "test" -> result.getJSONArray("questions")
                else -> result.getString("answer")
            }
            return result.toString()
        } finally { connection.disconnect() }
    }
}
