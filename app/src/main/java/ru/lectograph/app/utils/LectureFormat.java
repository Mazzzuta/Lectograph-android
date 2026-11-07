package ru.lectograph.app.utils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class LectureFormat {
    public static String time(long ms) { long seconds = ms / 1000; return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60); }
    public static String date(long value) { return new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date(value)); }
    public static String status(String value) {
        return switch (value) { case "recording" -> "Идёт запись"; case "recorded" -> "Аудио сохранено"; case "interrupted" -> "Запись прервана — проверьте аудио"; default -> "Ошибка записи — проверьте аудио"; };
    }
    private LectureFormat() {}
}
