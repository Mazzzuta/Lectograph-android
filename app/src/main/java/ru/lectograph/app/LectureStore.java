package ru.lectograph.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class LectureStore extends SQLiteOpenHelper {
    public record Lecture(long id, String title, String subject, String notes, String audio,
                          long created, long duration, String status) {}

    public LectureStore(Context context) { super(context, "lectograph.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE subjects (name TEXT PRIMARY KEY NOT NULL)");
        db.execSQL("CREATE TABLE lectures (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, subject TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', audio TEXT NOT NULL, created INTEGER NOT NULL, duration INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported database upgrade");
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
    public void delete(long id) { getWritableDatabase().delete("lectures", "id=?", new String[]{Long.toString(id)}); }
}
