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
import ru.lectograph.app.transcription.TranscriptionService;

public final class LectureScreen implements AutoCloseable {
    private MediaPlayer player;
    private final TranscriptPanel transcript;
    private final ScreenUi ui;
    private final LectureStore store;
    private final Lecture lecture;
    public LectureScreen(MainActivity activity, ScreenUi ui, LectureStore store, Lecture l) {
        this.ui = ui; this.store = store; this.lecture = l;
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
        LectureNotes.show(ui, store, l);
        transcript = new TranscriptPanel(ui, store, l, this::playFrom);
        ui.button("Удалить лекцию", () -> new AlertDialog.Builder(ui.context).setTitle("Удалить лекцию?")
                .setMessage("Аудио и заметки будут удалены без возможности восстановления.")
                .setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d, w) -> {
                    if (TranscriptionService.running && TranscriptionService.lectureId == l.id()) {
                        ui.toast("Сначала отмените распознавание и дождитесь его остановки"); return;
                    }
                    stopPlayback();
                    File file = new File(new File(ui.context.getFilesDir(), "audio"), l.audio());
                    if (file.exists() && !file.delete()) { ui.toast("Не удалось удалить аудиофайл"); return; }
                    store.delete(l.id()); activity.closeLecture();
                }).show());
    }
    public void refreshProcessing() { transcript.refresh(ui, store, lecture.id()); }
    private void playFrom(long milliseconds) {
        stopPlayback();
        try {
            player = new MediaPlayer();
            player.setDataSource(new File(new File(ui.context.getFilesDir(), "audio"), lecture.audio()).getAbsolutePath());
            player.prepare(); player.seekTo(milliseconds, MediaPlayer.SEEK_CLOSEST);
            player.setOnCompletionListener(p -> stopPlayback()); player.start();
        } catch (Exception e) { stopPlayback(); ui.toast("Аудиофайл недоступен или повреждён"); }
    }

    private void stopPlayback() { if (player != null) { player.release(); player = null; } }
    @Override public void close() { stopPlayback(); }
}
