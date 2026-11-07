package ru.lectograph.app.ui.components;

import android.view.Gravity;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import ru.lectograph.app.database.LectureStore;
import ru.lectograph.app.models.Lecture;

public final class LectureNotes {
    public static void show(ScreenUi ui, LectureStore store, Lecture lecture) {
        ui.label("Мои заметки", 21, ui.theme.foreground());
        EditText editor = ui.input("Что важно запомнить?", lecture.notes());
        editor.setMinLines(5); editor.setGravity(Gravity.TOP);
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Lecture current = store.get(lecture.id());
                if (current != null && !current.notes().equals(s.toString()))
                    store.edit(current.id(), current.title(), current.subject(), s.toString());
            }
            public void afterTextChanged(Editable text) {}
        });
        ui.label("Заметки сохраняются автоматически", 12, ui.theme.muted());
    }
    private LectureNotes() {}
}
