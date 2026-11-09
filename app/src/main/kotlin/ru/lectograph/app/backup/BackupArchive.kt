package ru.lectograph.app.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

object BackupArchive {
    const val LIMIT = 32 * 1024 * 1024
    @Volatile var busy = false
    data class Entry(val title: String, val subject: String, val notes: String, val created: Long, val duration: Long,
        val segments: List<ru.lectograph.app.models.TranscriptSegment>, val materials: Map<String, String>)
    data class Archive(val subjects: List<String>, val lectures: List<Entry>)
    fun read(input: InputStream): Archive {
        val bytes = input.use { source ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) { val count = source.read(buffer); if (count < 0) break
                require(output.size() + count <= LIMIT) { "Файл превышает 32 МБ" }; output.write(buffer, 0, count) }
            output.toByteArray()
        }
        require(bytes.size <= LIMIT) { "Файл превышает 32 МБ" }
        return decode(bytes.toString(Charsets.UTF_8))
    }
    fun decode(text: String): Archive {
        val root = JSONObject(text)
        require(root.getString("format") == "lectograph" && root.getInt("version") == 1) { "Неподдерживаемый формат копии" }
        fun string(obj: JSONObject, key: String, limit: Int): String {
            val value = obj.get(key); require(value is String && value.length <= limit) { "Некорректное поле $key" }; return value
        }
        val names = root.getJSONArray("subjects")
        require(names.length() <= 5000)
        val subjects = (0 until names.length()).map { val value = names.get(it); require(value is String && value.isNotBlank() && value.length <= 1000); value }
        require(subjects.distinct().size == subjects.size)
        val records = root.getJSONArray("lectures"); require(records.length() <= 5000)
        var totalSegments = 0
        val lectures = (0 until records.length()).map { i ->
            val obj = records.getJSONObject(i)
            val title = string(obj, "title", 1000); require(title.isNotBlank())
            val subject = string(obj, "subject", 1000); require(subject.isEmpty() || subject in subjects)
            val created = obj.getLong("created"); val duration = obj.getLong("duration"); require(created >= 0 && duration >= 0)
            val segments = obj.getJSONArray("segments"); totalSegments += segments.length(); require(totalSegments <= 200000)
            var previous = -1L
            val transcript = (0 until segments.length()).map { j ->
                val segment = segments.getJSONObject(j)
                val start = segment.getLong("start"); val end = segment.getLong("end")
                require(start >= 0 && end >= start && start >= previous); previous = start
                ru.lectograph.app.models.TranscriptSegment(start, end, string(segment, "text", 100000))
            }
            val materials = obj.getJSONObject("materials")
            require(materials.keys().asSequence().all { it in listOf("study", "test", "question") })
            val saved = materials.keys().asSequence().associateWith { key ->
                val value = materials.getJSONObject(key)
                when (key) {
                    "study" -> { require(value.get("short") is String && value.get("detailed") is String); value.getJSONArray("clarifications"); value.getJSONArray("studentQuestions") }
                    "test" -> { val questions = value.getJSONArray("questions"); require(questions.length() <= 10)
                        for (q in 0 until questions.length()) { val item = questions.getJSONObject(q); require(item.get("question") is String && item.get("explanation") is String && item.get("sourceQuote") is String)
                            val options = item.getJSONArray("options"); require(options.length() == 4 && (0..3).all { options.get(it) is String }); require(item.getInt("correctIndex") in 0..3) }
                    }
                    "question" -> require(value.get("answer") is String)
                }
                value.toString()
            }
            Entry(title, subject, string(obj, "notes", 1000000), created, duration, transcript, saved)
        }
        return Archive(subjects, lectures)
    }
    fun encode(archive: Archive): String {
        val records = JSONArray()
        archive.lectures.forEach { entry ->
            val segments = JSONArray(); entry.segments.forEach { segments.put(JSONObject().put("start", it.startMs).put("end", it.endMs).put("text", it.text)) }
            val materials = JSONObject(); entry.materials.forEach { (kind, value) -> if (value.isNotEmpty()) materials.put(kind, JSONObject(value)) }
            records.put(JSONObject().put("title", entry.title).put("subject", entry.subject).put("notes", entry.notes)
                .put("created", entry.created).put("duration", entry.duration).put("segments", segments).put("materials", materials))
        }
        val result = JSONObject().put("format", "lectograph").put("version", 1).put("subjects", JSONArray(archive.subjects)).put("lectures", records).toString()
        require(result.toByteArray(Charsets.UTF_8).size <= LIMIT) { "Копия превышает 32 МБ" }
        decode(result)
        return result
    }
}
