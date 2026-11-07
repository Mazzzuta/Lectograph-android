package ru.lectograph.app.ui.library;

import android.view.Gravity;
import android.widget.*;
import java.util.Locale;
import ru.lectograph.app.MainActivity;
import ru.lectograph.app.database.LectureStore;
import ru.lectograph.app.models.Lecture;
import ru.lectograph.app.ui.components.ScreenUi;
import ru.lectograph.app.utils.LectureFormat;

public final class LibraryScreen {
    public static void show(MainActivity activity, ScreenUi ui, LectureStore store, boolean drafts, String subject) {
        ui.label("ЛЕКТОГРАФ", 12, ui.theme.muted());
        ui.label(drafts ? "Черновики" : subject.isEmpty() ? "Твои лекции" : subject, 32, ui.theme.foreground());
        ui.label(drafts ? "Быстрые записи, которые можно оформить позже" : "Записывай. Разбирайся. Запоминай.", 15, ui.theme.muted());
        ui.button("●  Быстрая запись", () -> activity.startRecording("", ""));
        ui.button("Запись с названием и предметом", activity::showRecordDialog);
        if (!drafts && subject.isEmpty()) {
            ui.label("Предметы", 21, ui.theme.foreground());
            for (String name : store.subjects()) ui.button(name, () -> activity.openSubject(name));
            ui.button("+ Создать предмет", activity::showSubjectDialog);
        } else if (!subject.isEmpty()) ui.button("← Все предметы", () -> activity.openSubject(""));
        ui.label(drafts ? "Без предмета" : "Последние записи", 21, ui.theme.foreground());
        EditText search = ui.input("Поиск по названию и заметкам", "");
        LinearLayout results = new LinearLayout(ui.context); results.setOrientation(LinearLayout.VERTICAL); ui.page.addView(results);
        Runnable filter = () -> {
            results.removeAllViews();
            String query = search.getText().toString().toLowerCase(Locale.ROOT);
            int count = 0;
            for (Lecture l : store.all()) {
                if (drafts != l.subject().isEmpty()) continue;
                if (!subject.isEmpty() && !subject.equals(l.subject())) continue;
                if (!(l.title() + " " + l.notes()).toLowerCase(Locale.ROOT).contains(query)) continue;
                Button card = new Button(ui.context); card.setAllCaps(false); card.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                card.setText(l.title() + "\n" + (l.subject().isEmpty() ? "Черновик" : l.subject()) + " · " + LectureFormat.date(l.created()) + " · " + LectureFormat.time(l.duration()) + "\n" + LectureFormat.status(l.status()));
                card.setTextColor(ui.theme.foreground()); card.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.theme.surface())); card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
                card.setOnClickListener(v -> activity.openLecture(l.id()));
                results.addView(card, new LinearLayout.LayoutParams(-1, -2)); count++;
            }
            if (count == 0) { TextView empty = new TextView(ui.context); empty.setText("Пока нет лекций. Начни с записи."); empty.setTextColor(ui.theme.muted()); empty.setPadding(0, ui.dp(24), 0, 0); results.addView(empty); }
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter.run(); }
            public void afterTextChanged(android.text.Editable e) {}
        }); filter.run();
    }
}
