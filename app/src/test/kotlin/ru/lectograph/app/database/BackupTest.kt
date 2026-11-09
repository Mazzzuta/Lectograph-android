package ru.lectograph.app.database

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.lectograph.app.backup.BackupArchive
import ru.lectograph.app.models.TranscriptSegment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    @Test fun roundTripPreservesTextNotesEmptySubjectsAndMaterialsWithoutAudio() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("lectograph.db")
        LectureStore(context).use { store ->
            store.addSubject("Пустой предмет")
            val id = store.create("Физика", "", "secret.m4a")
            store.edit(id, "Физика", "Наука", "Личная заметка")
            store.saveTranscript(id, listOf(TranscriptSegment(10, 1000, "Сила")))
            store.saveMaterial(id, "question", "{\"answer\":\"Ньютон\"}", store.transcriptText(id))
            val encoded = BackupArchive.encode(store.backup())
            assertFalse(encoded.contains("secret.m4a"))
            val archive = BackupArchive.read(encoded.byteInputStream())
            store.restore(archive)
            val lecture = store.all().single()
            assertNotEquals(id, lecture.id)
            assertEquals("", lecture.audio)
            assertEquals("imported", lecture.status)
            assertEquals("Личная заметка", lecture.notes)
            assertEquals("Сила", store.transcript(lecture.id).single().text)
            assertTrue(store.material(lecture.id, "question").contains("Ньютон"))
            assertTrue("Пустой предмет" in store.subjects())
        }
    }
    @Test fun invalidArchiveIsRejectedAndFailedRestoreRollsBack() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("lectograph.db")
        LectureStore(context).use { store ->
            val id = store.create("Сохранить", "", "keep.m4a")
            val json = BackupArchive.encode(store.backup())
            assertThrows(Exception::class.java) { BackupArchive.decode(json.replace("\"version\":1", "\"version\":99")) }
            assertThrows(Exception::class.java) { BackupArchive.decode(json.replace("\"subject\":\"\"", "\"subject\":\"Чужой\"")) }
            store.writableDatabase.execSQL("CREATE TRIGGER reject_import BEFORE INSERT ON lectures BEGIN SELECT RAISE(ABORT, 'test'); END")
            assertThrows(Exception::class.java) { store.restore(BackupArchive.decode(json)) }
            assertEquals("keep.m4a", store.get(id)?.audio)
            assertEquals(1, store.all().size)
        }
    }
}
