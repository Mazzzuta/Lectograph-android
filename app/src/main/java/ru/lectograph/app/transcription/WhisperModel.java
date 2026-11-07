package ru.lectograph.app.transcription;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public final class WhisperModel {
    public static final long SIZE = 147951465L;
    private static final String SHA256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe";
    private static final String URL_BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin";
    public static File file(Context context) { return new File(context.getFilesDir(), "models/ggml-base.bin"); }
    public static boolean ready(Context context) { return file(context).length() == SIZE; }

    public static void download(Context context, BooleanSupplier cancelled, IntConsumer progress) throws Exception {
        File target = file(context);
        File dir = target.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Не удалось создать папку модели");
        if (dir.getUsableSpace() < SIZE + 32 * 1024 * 1024L) throw new IOException("Для модели нужно не менее 180 МБ свободного места");
        HttpURLConnection connection = (HttpURLConnection) new URL(URL_BASE).openConnection();
        connection.setConnectTimeout(20000); connection.setReadTimeout(20000);
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream output = null;
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Сервер модели недоступен. Повторите загрузку позже.");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            output = atomic.startWrite();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[65536]; long total = 0; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelled.getAsBoolean()) throw new CancellationException();
                    total += count;
                    if (total > SIZE) throw new IOException("Размер модели не совпадает");
                    output.write(buffer, 0, count); digest.update(buffer, 0, count);
                    progress.accept((int) (100 * total / SIZE));
                }
                StringBuilder hash = new StringBuilder();
                for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                if (total != SIZE || !SHA256.contentEquals(hash)) throw new IOException("Модель загружена с ошибкой. Повторите загрузку.");
            }
            if (cancelled.getAsBoolean()) throw new CancellationException();
            atomic.finishWrite(output); output = null;
        } finally {
            if (output != null) atomic.failWrite(output);
            connection.disconnect();
        }
    }
    private WhisperModel() {}
}
