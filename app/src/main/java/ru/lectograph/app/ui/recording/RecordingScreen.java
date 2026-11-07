package ru.lectograph.app.ui.recording;

import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;
import ru.lectograph.app.database.LectureStore;
import ru.lectograph.app.models.Lecture;
import ru.lectograph.app.recording.RecordingService;
import ru.lectograph.app.ui.components.ScreenUi;
import ru.lectograph.app.ui.components.LectureNotes;
import ru.lectograph.app.utils.LectureFormat;

public final class RecordingScreen {
    private final TextView timer;
    private final Button pause;
    public RecordingScreen(ScreenUi ui, LectureStore store) {
        Lecture l = store.get(RecordingService.activeId);
        ui.label("ДИКТОФОН", 12, ui.theme.muted()); ui.label(l == null ? "Запись лекции" : l.title(), 28, ui.theme.foreground());
        timer = ui.label(LectureFormat.time(RecordingService.elapsed()), 56, ui.theme.foreground());
        ui.label("Запись продолжается при выключенном экране. После остановки она сохранится на устройстве.", 15, ui.theme.muted());
        pause = ui.button("Пауза", () -> ui.context.startService(new Intent(ui.context, RecordingService.class).setAction("pause")));
        ui.button("Завершить и сохранить", () -> {
            ui.context.startService(new Intent(ui.context, RecordingService.class).setAction("stop"));
        });
        if (l != null) LectureNotes.show(ui, store, l);
    }

    public void refresh() {
        timer.setText(LectureFormat.time(RecordingService.elapsed()));
        pause.setText(RecordingService.pausedAt == 0 ? "Пауза" : "Продолжить");
    }
}
