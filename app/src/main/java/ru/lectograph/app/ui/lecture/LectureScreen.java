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
import ru.lectograph.app.ai.StudyService;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

public final class LectureScreen implements AutoCloseable {
    private MediaPlayer player;
    private final TranscriptPanel transcript;
    private final ScreenUi ui;
    private final LectureStore store;
    private final Lecture lecture;
    public LectureScreen(MainActivity activity, ScreenUi ui, LectureStore store, Lecture l, String tab) {
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
        HorizontalScrollView tabs = new HorizontalScrollView(ui.context); tabs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(ui.context); tabs.addView(row); ui.page.addView(tabs);
        for (String[] item : new String[][]{{"text", "Текст"}, {"summary", "Конспект"}, {"review", "Разбор"}, {"notes", "Заметки"}, {"test", "Тест"}}) {
            Button button = new Button(ui.context); button.setText(item[1]); button.setAllCaps(false); ui.styleButton(button);
            button.setTextColor(ui.theme.foreground()); button.setAlpha(tab.equals(item[0]) ? 1f : 0.65f);
            button.setOnClickListener(v -> activity.selectLectureTab(item[0])); row.addView(button);
        }
        transcript = tab.equals("text") ? new TranscriptPanel(ui, store, l, this::playFrom) : null;
        if (tab.equals("notes")) LectureNotes.show(ui, store, l);
        else if (!tab.equals("text")) StudyPanel.show(ui, store, l, tab);
        ui.button("Удалить лекцию", () -> new AlertDialog.Builder(ui.context).setTitle("Удалить лекцию?")
                .setMessage("Аудио и заметки будут удалены без возможности восстановления.")
                .setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d, w) -> {
                    if ((TranscriptionService.running && TranscriptionService.lectureId == l.id()) || (StudyService.running && StudyService.lectureId == l.id())) {
                        ui.toast("Сначала отмените распознавание и дождитесь его остановки"); return;
                    }
                    stopPlayback();
                    File file = new File(new File(ui.context.getFilesDir(), "audio"), l.audio());
                    if (file.exists() && !file.delete()) { ui.toast("Не удалось удалить аудиофайл"); return; }
                    store.delete(l.id()); activity.closeLecture();
                }).show());
    }
    public void refreshProcessing() { if (transcript != null) transcript.refresh(ui, store, lecture.id()); }
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
