package ru.lectograph.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LectureFormat {
    fun time(ms: Long): String {
        val seconds = ms / 1000
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
    fun date(value: Long): String = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(value))
    fun status(value: String): String = when (value) {
        "recording" -> "Идёт запись"
        "recorded" -> "Аудио сохранено"
        "interrupted" -> "Запись прервана — проверьте аудио"
        else -> "Ошибка записи — проверьте аудио"
    }
}
