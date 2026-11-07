package ru.lectograph.app;

import ru.lectograph.app.database.LectureStore;

import android.widget.Button;
import android.widget.EditText;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivityTest {
    @Test public void draftNotesPersistWhenReturningToLibrary() {
        var context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("lectograph.db");
        try (var store = new LectureStore(context)) {
            long id = store.create("Проверочная лекция", "", "test.m4a");
            store.finish(id, 15000, "recorded");
            var controller = Robolectric.buildActivity(MainActivity.class).setup();
            MainActivity activity = controller.get();
            ViewGroup root = activity.findViewById(android.R.id.content);
            button(root, "Черновики").performClick();
            button(root, "Проверочная лекция").performClick();
            EditText notes = edit(root);
            assertNotNull(notes);
            notes.setText("Проверить определение перед экзаменом");
            button(root, "Библиотека").performClick();
            assertEquals("Проверить определение перед экзаменом", store.get(id).notes());
            controller.pause().stop().destroy();
        }
    }
    @Test public void editingDraftPreservesLatestNotesAndMovesItToLibrary() {
        var context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("lectograph.db");
        try (var store = new LectureStore(context)) {
            long id = store.create("Черновик", "", "test.m4a");
            store.finish(id, 15000, "recorded");
            var controller = Robolectric.buildActivity(MainActivity.class).setup();
            MainActivity activity = controller.get();
            ViewGroup root = activity.findViewById(android.R.id.content);
            button(root, "Черновики").performClick();
            button(root, "Черновик\n").performClick();
            edit(root).setText("Моя заметка");
            button(root, "Изменить название").performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            View form = dialog.findViewById(android.R.id.content);
            edit(form, "Название лекции").setText("Законы Ньютона");
            edit(form, "Предмет (пусто — черновик)").setText("Физика");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals("Законы Ньютона", store.get(id).title());
            assertEquals("Физика", store.get(id).subject());
            assertEquals("Моя заметка", store.get(id).notes());
            button(root, "Библиотека").performClick();
            assertNotNull(findButton(root, "Законы Ньютона"));
            controller.pause().stop().destroy();
        }
    }
    private Button button(View view, String prefix) {
        Button result = findButton(view, prefix);
        assertNotNull("Missing button: " + prefix, result);
        return result;
    }
    private Button findButton(View view, String prefix) {
        if (view instanceof Button b && b.getText().toString().startsWith(prefix)) return b;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            Button result = findButton(group.getChildAt(i), prefix);
            if (result != null) return result;
        }
        return null;
    }
    private EditText edit(View view) {
        return edit(view, null);
    }
    private EditText edit(View view, String hint) {
        if (view instanceof EditText e && (hint == null || hint.contentEquals(e.getHint()))) return e;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            EditText result = edit(group.getChildAt(i), hint);
            if (result != null) return result;
        }
        return null;
    }
}
