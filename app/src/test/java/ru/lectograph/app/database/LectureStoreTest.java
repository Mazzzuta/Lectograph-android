package ru.lectograph.app.database;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import android.database.sqlite.SQLiteDatabase;
import ru.lectograph.app.models.TranscriptSegment;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LectureStoreTest {
    private Context getContext() { return RuntimeEnvironment.getApplication(); }
    @Test public void testDraftSurvivesReopenAndMovesToSubject() {
        getContext().deleteDatabase("lectograph.db");
        LectureStore store = new LectureStore(getContext());
        long id = store.create("Запись", "", "test.m4a");
        store.finish(id, 12000, "recorded");
        store.edit(id, "Лекция 1", "Физика", "Важная формула");
        store.close();
        store = new LectureStore(getContext());
        assertEquals("Физика", store.get(id).subject());
        assertEquals("Важная формула", store.get(id).notes());
        assertEquals(12000L, store.get(id).duration());
        assertEquals(1, store.subjects().size());
        store.delete(id);
        assertNull(store.get(id));
        store.close();
    }
    @Test public void testInterruptedRecordingIsNotMarkedReady() {
        getContext().deleteDatabase("lectograph.db");
        LectureStore store = new LectureStore(getContext());
        long id = store.create("Запись", "", "partial.m4a");
        store.recoverInterrupted();
        assertEquals("interrupted", store.get(id).status());
        try { store.edit(id, " ", "", ""); fail("Empty title accepted"); }
        catch (IllegalArgumentException expected) { }
        assertEquals("Запись", store.get(id).title());
        store.close();
    }
    @Test public void versionOneMigratesWithoutLosingNotesOrAudio() {
        getContext().deleteDatabase("lectograph.db");
        try (SQLiteDatabase db = getContext().openOrCreateDatabase("lectograph.db", 0, null)) {
            db.execSQL("CREATE TABLE subjects (name TEXT PRIMARY KEY NOT NULL)");
            db.execSQL("CREATE TABLE lectures (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, subject TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', audio TEXT NOT NULL, created INTEGER NOT NULL, duration INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL)");
            db.execSQL("INSERT INTO lectures(title,notes,audio,created,status) VALUES('Лекция','Мои заметки','original.m4a',1,'recorded')");
            db.setVersion(1);
        }
        try (LectureStore store = new LectureStore(getContext())) {
            assertEquals("Мои заметки", store.get(1).notes()); assertEquals("original.m4a", store.get(1).audio());
            var text = List.of(new TranscriptSegment(1000, 2000, "Первый фрагмент"));
            store.saveTranscript(1, text);
            store.setJob(1, "running", 30, ""); store.recoverTranscriptions();
            assertEquals("interrupted", store.job(1).status());
            assertEquals(text, store.transcript(1));
            store.delete(1); assertTrue(store.transcript(1).isEmpty()); assertEquals("idle", store.job(1).status());
        }
    }
    @Test public void generatedMaterialsCannotOverwriteANewerTranscript() {
        getContext().deleteDatabase("lectograph.db");
        try (LectureStore store = new LectureStore(getContext())) {
            long id = store.create("Лекция", "", "original.m4a");
            store.saveTranscript(id, List.of(new TranscriptSegment(0, 1000, "Исходный текст")));
            String original = store.transcriptText(id);
            store.saveMaterial(id, "study", "{\"short\":\"Конспект\"}", original);
            assertFalse(store.material(id, "study").isEmpty());
            store.saveTranscript(id, List.of(new TranscriptSegment(0, 1000, "Исправленный текст")));
            assertTrue(store.material(id, "study").isEmpty());
            try { store.saveMaterial(id, "study", "{}", original); fail("Stale material accepted"); }
            catch (IllegalStateException expected) { }
            assertTrue(store.material(id, "study").isEmpty());
        }
    }
}
