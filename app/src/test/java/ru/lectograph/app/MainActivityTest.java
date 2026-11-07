package ru.lectograph.app;

import ru.lectograph.app.database.LectureStore;

import android.widget.Button;
import android.widget.EditText;
import android.view.View;
import android.view.ViewGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
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
        if (view instanceof EditText e) return e;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            EditText result = edit(group.getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }
}
