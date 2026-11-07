package ru.lectograph.app.ui.lecture;

import android.content.Intent;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.function.LongConsumer;
import ru.lectograph.app.database.LectureStore;
import ru.lectograph.app.models.Lecture;
import ru.lectograph.app.transcription.TranscriptionService;
import ru.lectograph.app.transcription.WhisperModel;
import ru.lectograph.app.ui.components.ScreenUi;
import ru.lectograph.app.utils.LectureFormat;

final class TranscriptPanel {
    private final TextView state;
    private final ProgressBar progress;
    TranscriptPanel(ScreenUi ui, LectureStore store, Lecture lecture, LongConsumer playFrom) {
        ui.label("Полный текст", 21, ui.theme.foreground());
        state = ui.label("", 14, ui.theme.muted());
        progress = new ProgressBar(ui.context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); ui.page.addView(progress);
        boolean busy = TranscriptionService.running;
        if (!WhisperModel.ready(ui.context)) {
            ui.label("Для распознавания один раз скачайте модель — 148 МБ. Затем аудио обрабатывается на телефоне без интернета. Язык — русский.", 14, ui.theme.muted());
            Button download = ui.primaryButton("Скачать Whisper base · 148 МБ", () -> start(ui, "download", -1));
            download.setEnabled(!busy);
        } else {
            Button transcribe = ui.primaryButton(store.job(lecture.id()).status().equals("done") ? "Распознать повторно" : "Распознать запись", () -> start(ui, "transcribe", lecture.id()));
            transcribe.setEnabled(!busy && !lecture.status().equals("recording"));
        }
        if (busy) ui.button("Отменить обработку", () -> ui.context.startService(new Intent(ui.context, TranscriptionService.class).setAction("cancel")));
        refresh(ui, store, lecture.id());
        var segments = store.transcript(lecture.id());
        if (store.job(lecture.id()).status().equals("done") && segments.isEmpty()) ui.label("Речь в записи не найдена.", 15, ui.theme.muted());
        if (!segments.isEmpty()) {
            ui.label("Текст распознан автоматически. Ошибки возможны; нажмите время, чтобы проверить фрагмент записи.", 13, ui.theme.muted());
            for (var segment : segments) {
                ui.button(LectureFormat.time(segment.startMs()), () -> playFrom.accept(segment.startMs()));
                ui.label(segment.text(), 17, ui.theme.foreground()).setTextIsSelectable(true);
            }
        }
        ui.label("Конспекты и тесты появятся после подключения Gemini.", 14, ui.theme.muted());
    }
    void refresh(ScreenUi ui, LectureStore store, long id) {
        boolean running = TranscriptionService.running;
        progress.setVisibility(running ? android.view.View.VISIBLE : android.view.View.GONE);
        progress.setProgress(TranscriptionService.progress);
        var job = store.job(id);
        if (running) state.setText(TranscriptionService.message + " · " + TranscriptionService.progress + "%");
        else if (!job.error().isEmpty()) state.setText(job.error());
        else if (!WhisperModel.ready(ui.context) && !TranscriptionService.message.isEmpty()) state.setText(TranscriptionService.message);
        else state.setText(job.status().equals("done") ? "Текст сохранён на устройстве" : "Аудио останется доступно после обработки");
    }
    private void start(ScreenUi ui, String action, long id) {
        try { ui.context.startForegroundService(new Intent(ui.context, TranscriptionService.class).setAction(action).putExtra("lecture", id)); }
        catch (RuntimeException e) { ui.toast("Не удалось запустить обработку. Повторите попытку."); }
    }
}
