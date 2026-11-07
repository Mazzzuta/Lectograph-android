package ru.lectograph.app.ui.components;

import android.app.AlertDialog;
import android.widget.*;
import ru.lectograph.app.MainActivity;
import ru.lectograph.app.database.LectureStore;
import ru.lectograph.app.models.Lecture;

public final class LectureDialogs {
    private final MainActivity activity;
    private final ScreenUi ui;
    private final LectureStore store;
    public LectureDialogs(MainActivity activity, ScreenUi ui, LectureStore store) {
        this.activity = activity; this.ui = ui; this.store = store;
    }
    public void record() {
        LinearLayout form = ui.form(); EditText title = ui.field(form, "Название лекции", ""); AutoCompleteTextView sub = ui.subjectField(form, "", store.subjects());
        AlertDialog dialog = new AlertDialog.Builder(ui.context).setTitle("Новая запись").setView(form).setNegativeButton("Отмена", null).setPositiveButton("Начать", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty() || sub.getText().toString().trim().isEmpty()) { ui.toast("Укажите название и предмет либо используйте быструю запись"); return; }
            dialog.dismiss(); activity.startRecording(title.getText().toString().trim(), sub.getText().toString().trim());
        })); dialog.show();
    }
    public void edit(Lecture l) {
        LinearLayout form = ui.form(); EditText title = ui.field(form, "Название лекции", l.title()); AutoCompleteTextView sub = ui.subjectField(form, l.subject(), store.subjects());
        AlertDialog dialog = new AlertDialog.Builder(ui.context).setTitle("Оформить лекцию").setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty()) { title.setError("Укажите название"); return; }
            store.edit(l.id(), title.getText().toString(), sub.getText().toString(), store.get(l.id()).notes());
            dialog.dismiss(); activity.openLecture(l.id());
        })); dialog.show();
    }
    public void subject() {
        LinearLayout form = ui.form(); EditText name = ui.field(form, "Название предмета", "");
        AlertDialog dialog = new AlertDialog.Builder(ui.context).setTitle("Новый предмет").setView(form).setNegativeButton("Отмена", null).setPositiveButton("Создать", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Укажите название"); return; }
            store.addSubject(name.getText().toString()); dialog.dismiss(); activity.refresh();
        })); dialog.show();
    }
}
