package ru.lectograph.app;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

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
}
