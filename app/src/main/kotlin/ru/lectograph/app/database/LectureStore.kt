package ru.lectograph.app.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ru.lectograph.app.models.Lecture
import ru.lectograph.app.models.TranscriptSegment

class LectureStore(context: Context) : SQLiteOpenHelper(context, "lectograph.db", null, 3) {
    data class Job(val status: String, val progress: Int, val error: String)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE subjects (name TEXT PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE lectures (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, subject TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', audio TEXT NOT NULL, created INTEGER NOT NULL, duration INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL)")
        createTranscripts(db)
        createMaterials(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1) createTranscripts(db)
        if (oldVersion < 3) createMaterials(db)
    }
    private fun createTranscripts(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE transcripts (lecture_id INTEGER NOT NULL, position INTEGER NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(lecture_id,position))")
        db.execSQL("CREATE TABLE transcription_jobs (lecture_id INTEGER PRIMARY KEY, status TEXT NOT NULL, progress INTEGER NOT NULL DEFAULT 0, error TEXT NOT NULL DEFAULT '')")
    }
    private fun createMaterials(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE lecture_materials (lecture_id INTEGER NOT NULL, kind TEXT NOT NULL, json TEXT NOT NULL, PRIMARY KEY(lecture_id,kind))")
    }
    private inline fun transaction(block: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try { block(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    fun create(title: String, subject: String, audio: String): Long = writableDatabase.insertOrThrow("lectures", null, ContentValues().apply {
        put("title", title); put("subject", subject); put("audio", audio)
        put("created", System.currentTimeMillis()); put("status", "recording")
    })
    fun finish(id: Long, duration: Long, status: String) {
        writableDatabase.update("lectures", ContentValues().apply { put("duration", duration); put("status", status) }, "id=?", arrayOf(id.toString()))
    }
    fun recoverInterrupted() { writableDatabase.execSQL("UPDATE lectures SET status='interrupted' WHERE status='recording'") }
    fun recoverTranscriptions() {
        writableDatabase.execSQL("UPDATE transcription_jobs SET status='interrupted',error='Обработка прервана. Можно запустить повторно.' WHERE status='running'")
    }
    fun edit(id: Long, title: String, subject: String, notes: String) {
        require(title.isNotBlank()) { "Укажите название" }
        transaction { db ->
            if (subject.isNotBlank()) addSubject(subject)
            db.update("lectures", ContentValues().apply {
                put("title", title.trim()); put("subject", subject.trim()); put("notes", notes)
            }, "id=?", arrayOf(id.toString()))
        }
    }
    fun addSubject(name: String) {
        require(name.isNotBlank()) { "Укажите предмет" }
        writableDatabase.insertWithOnConflict("subjects", null, ContentValues().apply { put("name", name.trim()) }, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun subjects(): List<String> = readableDatabase.rawQuery("SELECT name FROM subjects ORDER BY name COLLATE NOCASE", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }
    fun all(): List<Lecture> = readableDatabase.rawQuery("SELECT id,title,subject,notes,audio,created,duration,status FROM lectures ORDER BY created DESC,id DESC", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(Lecture(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getLong(5), cursor.getLong(6), cursor.getString(7))) }
    }
    fun get(id: Long): Lecture? = all().firstOrNull { it.id == id }
    fun job(id: Long): Job = readableDatabase.rawQuery("SELECT status,progress,error FROM transcription_jobs WHERE lecture_id=?", arrayOf(id.toString())).use {
        if (it.moveToFirst()) Job(it.getString(0), it.getInt(1), it.getString(2)) else Job("idle", 0, "")
    }
    fun setJob(id: Long, status: String, progress: Int, error: String) {
        writableDatabase.insertWithOnConflict("transcription_jobs", null, ContentValues().apply {
            put("lecture_id", id); put("status", status); put("progress", progress); put("error", error)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun transcript(id: Long): List<TranscriptSegment> = readableDatabase.rawQuery("SELECT start_ms,end_ms,text FROM transcripts WHERE lecture_id=? ORDER BY position", arrayOf(id.toString())).use {
        buildList { while (it.moveToNext()) add(TranscriptSegment(it.getLong(0), it.getLong(1), it.getString(2))) }
    }
    fun transcriptText(id: Long): String = transcript(id).joinToString("") { it.text + "\n" }
    fun saveTranscript(id: Long, segments: List<TranscriptSegment>) = transaction { db ->
        check(get(id) != null) { "Лекция удалена" }
        db.delete("transcripts", "lecture_id=?", arrayOf(id.toString()))
        db.delete("lecture_materials", "lecture_id=?", arrayOf(id.toString()))
        segments.forEachIndexed { index, segment ->
            db.insertOrThrow("transcripts", null, ContentValues().apply {
                put("lecture_id", id); put("position", index); put("start_ms", segment.startMs); put("end_ms", segment.endMs); put("text", segment.text)
            })
        }
        setJob(id, "done", 100, "")
    }
    fun material(id: Long, kind: String): String = readableDatabase.rawQuery("SELECT json FROM lecture_materials WHERE lecture_id=? AND kind=?", arrayOf(id.toString(), kind)).use {
        if (it.moveToFirst()) it.getString(0) else ""
    }
    fun saveMaterial(id: Long, kind: String, json: String, sourceText: String) = transaction { db ->
        check(get(id) != null && transcriptText(id) == sourceText) { "Текст лекции изменился. Запросите обработку повторно." }
        db.insertWithOnConflict("lecture_materials", null, ContentValues().apply { put("lecture_id", id); put("kind", kind); put("json", json) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun delete(id: Long) = transaction { db ->
        val args = arrayOf(id.toString())
        db.delete("transcripts", "lecture_id=?", args); db.delete("transcription_jobs", "lecture_id=?", args)
        db.delete("lecture_materials", "lecture_id=?", args); db.delete("lectures", "id=?", args)
    }
}
