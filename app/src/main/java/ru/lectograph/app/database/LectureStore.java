package ru.lectograph.app.database;

import ru.lectograph.app.models.Lecture;
import ru.lectograph.app.models.TranscriptSegment;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class LectureStore extends SQLiteOpenHelper {
    public LectureStore(Context context) { super(context, "lectograph.db", null, 2); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE subjects (name TEXT PRIMARY KEY NOT NULL)");
        db.execSQL("CREATE TABLE lectures (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, subject TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', audio TEXT NOT NULL, created INTEGER NOT NULL, duration INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL)");
        createTranscripts(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) createTranscripts(db);
    }
    private void createTranscripts(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE transcripts (lecture_id INTEGER NOT NULL, position INTEGER NOT NULL, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(lecture_id,position))");
        db.execSQL("CREATE TABLE transcription_jobs (lecture_id INTEGER PRIMARY KEY, status TEXT NOT NULL, progress INTEGER NOT NULL DEFAULT 0, error TEXT NOT NULL DEFAULT '')");
    }
    public record Job(String status, int progress, String error) {}
    public Job job(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT status,progress,error FROM transcription_jobs WHERE lecture_id=?", new String[]{Long.toString(id)})) {
            return c.moveToFirst() ? new Job(c.getString(0), c.getInt(1), c.getString(2)) : new Job("idle", 0, "");
        }
    }
    public void setJob(long id, String status, int progress, String error) {
        ContentValues values = new ContentValues(); values.put("lecture_id", id); values.put("status", status);
        values.put("progress", progress); values.put("error", error);
        getWritableDatabase().insertWithOnConflict("transcription_jobs", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void recoverTranscriptions() {
        getWritableDatabase().execSQL("UPDATE transcription_jobs SET status='interrupted',error='Обработка прервана. Можно запустить повторно.' WHERE status='running'");
    }
    public List<TranscriptSegment> transcript(long id) {
        List<TranscriptSegment> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT start_ms,end_ms,text FROM transcripts WHERE lecture_id=? ORDER BY position", new String[]{Long.toString(id)})) {
            while (c.moveToNext()) result.add(new TranscriptSegment(c.getLong(0), c.getLong(1), c.getString(2)));
        }
        return result;
    }
    public void saveTranscript(long id, List<TranscriptSegment> segments) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            if (get(id) == null) throw new IllegalStateException("Лекция удалена");
            db.delete("transcripts", "lecture_id=?", new String[]{Long.toString(id)});
            for (int i = 0; i < segments.size(); i++) {
                TranscriptSegment s = segments.get(i);
                ContentValues values = new ContentValues(); values.put("lecture_id", id); values.put("position", i);
                values.put("start_ms", s.startMs()); values.put("end_ms", s.endMs()); values.put("text", s.text());
                db.insertOrThrow("transcripts", null, values);
            }
            setJob(id, "done", 100, ""); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public long create(String title, String subject, String audio) {
        ContentValues values = new ContentValues();
        values.put("title", title); values.put("subject", subject); values.put("audio", audio);
        values.put("created", System.currentTimeMillis()); values.put("status", "recording");
        return getWritableDatabase().insertOrThrow("lectures", null, values);
    }
    public void finish(long id, long duration, String status) {
        ContentValues values = new ContentValues();
        values.put("duration", duration); values.put("status", status);
        getWritableDatabase().update("lectures", values, "id=?", new String[]{Long.toString(id)});
    }
    public void recoverInterrupted() {
        getWritableDatabase().execSQL("UPDATE lectures SET status='interrupted' WHERE status='recording'");
    }
    public void edit(long id, String title, String subject, String notes) {
        if (title.trim().isEmpty()) throw new IllegalArgumentException("Укажите название");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (!subject.trim().isEmpty()) addSubject(subject);
            ContentValues values = new ContentValues();
            values.put("title", title.trim()); values.put("subject", subject.trim()); values.put("notes", notes);
            db.update("lectures", values, "id=?", new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void addSubject(String name) {
        if (name.trim().isEmpty()) throw new IllegalArgumentException("Укажите предмет");
        ContentValues values = new ContentValues(); values.put("name", name.trim());
        getWritableDatabase().insertWithOnConflict("subjects", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }
    public List<String> subjects() {
        List<String> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM subjects ORDER BY name COLLATE NOCASE", null)) {
            while (c.moveToNext()) result.add(c.getString(0));
        }
        return result;
    }
    public List<Lecture> all() {
        List<Lecture> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,subject,notes,audio,created,duration,status FROM lectures ORDER BY created DESC,id DESC", null)) {
            while (c.moveToNext()) result.add(new Lecture(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5), c.getLong(6), c.getString(7)));
        }
        return result;
    }
    public Lecture get(long id) {
        for (Lecture lecture : all()) if (lecture.id() == id) return lecture;
        return null;
    }
    public void delete(long id) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            String[] args = {Long.toString(id)};
            db.delete("transcripts", "lecture_id=?", args); db.delete("transcription_jobs", "lecture_id=?", args);
            db.delete("lectures", "id=?", args); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
