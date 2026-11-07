package ru.lectograph.app.database

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.lectograph.app.models.TranscriptSegment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LectureStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Test fun draftSurvivesReopenAndMovesToSubject() {
        context.deleteDatabase("lectograph.db")
        val id = LectureStore(context).use {
            val id = it.create("Запись", "", "test.m4a")
            it.finish(id, 12000, "recorded"); it.edit(id, "Лекция 1", "Физика", "Важная формула"); id
        }
        LectureStore(context).use {
            assertEquals("Физика", it.get(id)?.subject); assertEquals("Важная формула", it.get(id)?.notes)
            assertEquals(12000L, it.get(id)?.duration); assertEquals(1, it.subjects().size)
            it.delete(id); assertNull(it.get(id))
        }
    }
    @Test fun interruptedRecordingIsNotMarkedReady() {
        context.deleteDatabase("lectograph.db")
        LectureStore(context).use {
            val id = it.create("Запись", "", "partial.m4a"); it.recoverInterrupted()
            assertEquals("interrupted", it.get(id)?.status)
            assertThrows(IllegalArgumentException::class.java) { it.edit(id, " ", "", "") }
            assertEquals("Запись", it.get(id)?.title)
        }
    }
    @Test fun versionOneMigratesWithoutLosingNotesOrAudio() {
        context.deleteDatabase("lectograph.db")
        context.openOrCreateDatabase("lectograph.db", 0, null).use {
            it.execSQL("CREATE TABLE subjects (name TEXT PRIMARY KEY NOT NULL)")
            it.execSQL("CREATE TABLE lectures (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, subject TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', audio TEXT NOT NULL, created INTEGER NOT NULL, duration INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL)")
            it.execSQL("INSERT INTO lectures(title,notes,audio,created,status) VALUES('Лекция','Мои заметки','original.m4a',1,'recorded')"); it.version = 1
        }
        LectureStore(context).use {
            assertEquals("Мои заметки", it.get(1)?.notes); assertEquals("original.m4a", it.get(1)?.audio)
            val text = listOf(TranscriptSegment(1000, 2000, "Первый фрагмент"))
            it.saveTranscript(1, text); it.setJob(1, "running", 30, ""); it.recoverTranscriptions()
            assertEquals("interrupted", it.job(1).status); assertEquals(text, it.transcript(1))
            it.delete(1); assertTrue(it.transcript(1).isEmpty()); assertEquals("idle", it.job(1).status)
        }
    }
    @Test fun generatedMaterialsCannotOverwriteANewerTranscript() {
        context.deleteDatabase("lectograph.db")
        LectureStore(context).use {
            val id = it.create("Лекция", "", "original.m4a")
            it.saveTranscript(id, listOf(TranscriptSegment(0, 1000, "Исходный текст")))
            val original = it.transcriptText(id)
            it.saveMaterial(id, "study", "{\"short\":\"Конспект\"}", original); assertFalse(it.material(id, "study").isEmpty())
            it.saveTranscript(id, listOf(TranscriptSegment(0, 1000, "Исправленный текст")))
            assertTrue(it.material(id, "study").isEmpty())
            assertThrows(IllegalStateException::class.java) { it.saveMaterial(id, "study", "{}", original) }
            assertTrue(it.material(id, "study").isEmpty())
        }
    }
}
