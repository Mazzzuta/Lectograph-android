package ru.lectograph.app.transcription;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.lectograph.app.MainActivity;
import ru.lectograph.app.R;
import ru.lectograph.app.database.LectureStore;
import ru.lectograph.app.models.TranscriptSegment;

public class TranscriptionService extends Service {
    public static volatile boolean running;
    public static volatile long lectureId = -1, generation;
    public static volatile String message = "";
    public static volatile int progress;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile WhisperEngine engine;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int lastProgress = -1;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("transcription", "Распознавание лекций", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if ("cancel".equals(intent.getAction())) { cancel(); if (!running) stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        boolean download = "download".equals(intent.getAction());
        lectureId = download ? -1 : intent.getLongExtra("lecture", -1);
        progress = 0; lastProgress = -1; cancelled.set(false); running = true; generation++;
        message = download ? "Загрузка Whisper base" : "Подготовка распознавания";
        try {
            int type = Build.VERSION.SDK_INT >= 35 && !download ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING : ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            if (Build.VERSION.SDK_INT >= 29) startForeground(2, notification(), type); else startForeground(2, notification());
            wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lectograph:transcription");
            wakeLock.acquire(6 * 60 * 60 * 1000L);
        } catch (RuntimeException e) {
            message = "Не удалось запустить фоновую обработку"; running = false; generation++; stopSelf(); return START_NOT_STICKY;
        }
        long id = lectureId;
        new Thread(() -> work(download, id), "Whisper-worker").start();
        return START_NOT_STICKY;
    }
    private void work(boolean download, long id) {
        try (LectureStore store = new LectureStore(this)) {
            try {
                if (download) {
                    WhisperModel.download(this, cancelled::get, percent -> update(percent, "Загрузка Whisper base", null, -1));
                    message = "Whisper base готова. Можно распознать запись.";
                } else {
                    if (!WhisperModel.ready(this)) throw new IllegalStateException("Сначала скачайте Whisper base");
                    var lecture = store.get(id);
                    if (lecture == null || lecture.status().equals("recording")) throw new IllegalStateException("Запись недоступна для обработки");
                    File audio = new File(new File(getFilesDir(), "audio"), lecture.audio());
                    if (!audio.isFile()) throw new IllegalStateException("Аудиофайл не найден");
                    store.setJob(id, "running", 0, "");
                    ArrayList<TranscriptSegment> segments = new ArrayList<>();
                    try (WhisperEngine loaded = new WhisperEngine(WhisperModel.file(this))) {
                        engine = loaded;
                        if (cancelled.get()) loaded.cancel();
                        AudioDecoder.decode(audio, cancelled::get, (samples, startMs, durationMs) -> {
                            long total = Math.max(durationMs, startMs + samples.length / 16L);
                            loaded.transcribe(samples, new WhisperEngine.Listener() {
                                public void onProgress(int percent) {
                                    int overall = (int) Math.min(99, 100 * (startMs + samples.length / 16L * percent / 100) / Math.max(1, total));
                                    update(overall, "Распознавание лекции", store, id);
                                }
                                public void onSegment(long start, long end, byte[] utf8) {
                                    String text = new String(utf8, StandardCharsets.UTF_8).trim();
                                    if (!text.isEmpty()) segments.add(new TranscriptSegment(startMs + start, startMs + end, text));
                                }
                            });
                        });
                    } finally { engine = null; }
                    if (cancelled.get()) throw new CancellationException();
                    store.saveTranscript(id, segments); progress = 100;
                    message = segments.isEmpty() ? "Речь в записи не найдена" : "Текст лекции сохранён";
                }
            } catch (CancellationException e) {
                message = "Обработка отменена. Аудио сохранено.";
                if (!download) store.setJob(id, "cancelled", progress, message);
            } catch (Exception | LinkageError | OutOfMemoryError e) {
                message = e instanceof OutOfMemoryError ? "Недостаточно оперативной памяти. Закройте другие приложения и повторите попытку." :
                        e.getMessage() == null ? "Не удалось обработать запись. Повторите попытку." : e.getMessage();
                if (!download) store.setJob(id, "error", progress, message);
            }
        } finally {
            main.post(() -> {
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                running = false; generation++; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
            });
        }
    }
    private void update(int percent, String text, LectureStore store, long id) {
        if (percent == lastProgress) return;
        lastProgress = percent; progress = percent; message = text;
        if (store != null) store.setJob(id, "running", percent, "");
        main.post(() -> {
            if (running && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED))
                getSystemService(NotificationManager.class).notify(2, notification());
        });
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel = PendingIntent.getService(this, 2, new Intent(this, TranscriptionService.class).setAction("cancel"), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "transcription").setSmallIcon(R.drawable.ic_mic).setContentTitle(message)
                .setContentText(progress + "%").setProgress(100, progress, progress == 0).setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Отменить", cancel).build()).build();
    }
    private void cancel() { cancelled.set(true); WhisperEngine current = engine; if (current != null) current.cancel(); }
    @Override public void onTimeout(int startId, int type) { cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    @Override public void onDestroy() { cancel(); if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
