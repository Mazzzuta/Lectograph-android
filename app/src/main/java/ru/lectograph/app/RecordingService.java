package ru.lectograph.app;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordingService extends Service {
    public static volatile long activeId = -1;
    public static volatile long startedAt;
    public static volatile long pausedAt;
    public static volatile long pausedMillis;
    public static volatile String error;
    private MediaRecorder recorder;
    private LectureStore store;
    private File audio;

    @Override public void onCreate() {
        super.onCreate();
        store = new LectureStore(this);
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("recording", "Запись лекций", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        if ("stop".equals(action)) { finish(); return START_NOT_STICKY; }
        if ("pause".equals(action) && recorder != null) {
            try {
                if (pausedAt == 0) { recorder.pause(); pausedAt = SystemClock.elapsedRealtime(); }
                else { recorder.resume(); pausedMillis += SystemClock.elapsedRealtime() - pausedAt; pausedAt = 0; }
                notifyRecording();
            } catch (RuntimeException e) { error = "Не удалось изменить состояние записи"; }
            return START_NOT_STICKY;
        }
        if (recorder != null) return START_NOT_STICKY;
        error = null;
        try {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("Microphone permission required");
            store.recoverInterrupted();
            File dir = new File(getFilesDir(), "audio");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Не удалось создать папку записи");
            if (dir.getUsableSpace() < 20L * 1024 * 1024) throw new IllegalStateException("Недостаточно места для записи");
            audio = new File(dir, java.util.UUID.randomUUID() + ".m4a");
            String title = intent.getStringExtra("title");
            if (title == null || title.trim().isEmpty()) title = "Запись · " + new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(new Date());
            String subject = intent.getStringExtra("subject");
            if (subject == null) subject = "";
            if (!subject.trim().isEmpty()) store.addSubject(subject);
            activeId = store.create(title, subject, audio.getName());
            startedAt = SystemClock.elapsedRealtime(); pausedAt = 0; pausedMillis = 0;
            Notification notification = notification();
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(1, notification);
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setOutputFile(audio.getAbsolutePath());
            recorder.setOnErrorListener((r, what, extra) -> { error = "Запись прервалась. Проверьте сохранённый файл."; finish(); });
            recorder.prepare(); recorder.start();
        } catch (Exception e) {
            error = "Не удалось начать запись. Проверьте микрофон и свободное место.";
            release();
            if (activeId != -1) store.finish(activeId, 0, "error");
            activeId = -1; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
        return START_NOT_STICKY;
    }
    public static long elapsed() {
        return activeId == -1 ? 0 : Math.max(0, (pausedAt == 0 ? SystemClock.elapsedRealtime() : pausedAt) - startedAt - pausedMillis);
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, RecordingService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "recording").setSmallIcon(R.drawable.ic_mic)
                .setContentTitle(pausedAt == 0 ? "Идёт запись лекции" : "Запись на паузе")
                .setContentText("Нажмите, чтобы открыть диктофон").setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Завершить", stop).build()).build();
    }
    private void notifyRecording() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(1, notification());
    }
    private void finish() {
        if (activeId == -1) { stopSelf(); return; }
        long duration = elapsed();
        String status = error == null ? "recorded" : "interrupted";
        try { if (recorder != null) recorder.stop(); }
        catch (RuntimeException e) { status = "error"; error = "Не удалось завершить аудиофайл. Запись могла быть слишком короткой."; }
        finally { release(); }
        store.finish(activeId, duration, status);
        activeId = -1; pausedAt = 0;
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    private void release() { if (recorder != null) { recorder.release(); recorder = null; } }
    @Override public void onDestroy() {
        if (activeId != -1) { error = "Запись прервана системой"; finish(); }
        store.close(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
