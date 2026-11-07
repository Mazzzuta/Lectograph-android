package ru.lectograph.app.ui.lecture;

import android.app.AlertDialog;
import android.media.MediaPlayer;
import android.widget.Button;
import java.io.File;
import ru.lectograph.app.MainActivity;
import ru.lectograph.app.database.LectureStore;
import ru.lectograph.app.models.Lecture;
import ru.lectograph.app.ui.components.ScreenUi;
import ru.lectograph.app.ui.components.LectureNotes;
import ru.lectograph.app.utils.LectureFormat;

public final class LectureScreen implements AutoCloseable {
    private MediaPlayer player;
    public LectureScreen(MainActivity activity, ScreenUi ui, LectureStore store, Lecture l) {
        ui.button("← Назад", activity::closeLecture);
        ui.label(l.subject().isEmpty() ? "ЧЕРНОВИК" : l.subject(), 12, ui.theme.muted()); ui.label(l.title(), 30, ui.theme.foreground());
        ui.label(LectureFormat.date(l.created()) + " · " + LectureFormat.time(l.duration()) + " · " + LectureFormat.status(l.status()), 14, ui.theme.muted());
        ui.button("Изменить название и предмет", () -> activity.showEditDialog(l));
        Button play = ui.button("▶ Прослушать запись", () -> {});
        play.setOnClickListener(v -> {
            if (player != null) { stopPlayback(); play.setText("▶ Прослушать запись"); return; }
            try {
                player = new MediaPlayer(); player.setDataSource(new File(new File(ui.context.getFilesDir(), "audio"), l.audio()).getAbsolutePath());
                player.prepare(); player.setOnCompletionListener(p -> { stopPlayback(); play.setText("▶ Прослушать запись"); });
                player.start(); play.setText("■ Остановить воспроизведение");
            } catch (Exception e) { stopPlayback(); ui.toast("Аудиофайл недоступен или повреждён"); }
        });
        ui.label("Материалы лекции", 21, ui.theme.foreground());
        ui.label("Расшифровка, конспекты и тесты пока недоступны: Whisper base и Gemini ещё не подключены.", 15, ui.theme.muted());
        LectureNotes.show(ui, store, l);
        ui.button("Удалить лекцию", () -> new AlertDialog.Builder(ui.context).setTitle("Удалить лекцию?")
                .setMessage("Аудио и заметки будут удалены без возможности восстановления.")
                .setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d, w) -> {
                    stopPlayback();
                    File file = new File(new File(ui.context.getFilesDir(), "audio"), l.audio());
                    if (file.exists() && !file.delete()) { ui.toast("Не удалось удалить аудиофайл"); return; }
                    store.delete(l.id()); activity.closeLecture();
                }).show());
    }

    private void stopPlayback() { if (player != null) { player.release(); player = null; } }
    @Override public void close() { stopPlayback(); }
}
