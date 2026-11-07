package ru.lectograph.app.ai;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.*;
import ru.lectograph.app.MainActivity;
import ru.lectograph.app.R;
import ru.lectograph.app.database.LectureStore;
import ru.lectograph.app.transcription.TranscriptionService;

public class StudyService extends Service {
    public static volatile boolean running;
    public static volatile long lectureId = -1, generation;
    public static volatile String message = "";
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        String action = intent.getStringExtra("action");
        if (!"study".equals(action) && !"test".equals(action) && !"question".equals(action)) { stopSelf(); return START_NOT_STICKY; }
        lectureId = intent.getLongExtra("lecture", -1); long id = lectureId;
        if (TranscriptionService.running && TranscriptionService.lectureId == id) { stopSelf(); return START_NOT_STICKY; }
        message = "Обработка через Gemini…"; running = true; generation++;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("study", "Обработка конспектов", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "study").setSmallIcon(R.drawable.ic_mic).setContentTitle(message).setContentIntent(open).setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC); else startForeground(3, notification);
        } catch (RuntimeException e) { message = "Не удалось запустить обработку"; running = false; generation++; stopSelf(); return START_NOT_STICKY; }
        new Thread(() -> {
            try (LectureStore store = new LectureStore(this)) {
                String text = store.transcriptText(id);
                String result = GeminiClient.request(this, action, text, intent.getStringExtra("question"));
                store.saveMaterial(id, action, result, text); message = "Материалы сохранены на устройстве";
            } catch (Exception e) { message = e.getMessage() == null ? "Не удалось обработать лекцию" : e.getMessage(); }
            finally { new Handler(Looper.getMainLooper()).post(() -> { running = false; generation++; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }); }
        }, "Gemini-worker").start();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
