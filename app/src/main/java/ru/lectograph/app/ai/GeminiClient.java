package ru.lectograph.app.ai;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import ru.lectograph.app.BuildConfig;

public final class GeminiClient {
    public static boolean configured() { return !BuildConfig.API_BASE_URL.isEmpty(); }
    public static String request(Context context, String action, String text, String question) throws Exception {
        if (!configured()) throw new IOException("Сервер обработки пока не подключён.");
        var preferences = context.getSharedPreferences("installation", 0);
        String id = preferences.getString("id", "");
        if (id.isEmpty()) { id = UUID.randomUUID().toString(); preferences.edit().putString("id", id).commit(); }
        JSONObject input = new JSONObject().put("text", text);
        if (action.equals("question")) input.put("question", question);
        HttpURLConnection connection = (HttpURLConnection) URI.create(BuildConfig.API_BASE_URL.replaceAll("/+$", "") + "/api/" + action).toURL().openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(105000); connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json"); connection.setRequestProperty("X-Installation-Id", id);
        connection.setInstanceFollowRedirects(false); connection.setDoOutput(true);
        try {
            byte[] bytes = input.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream stream = connection.getOutputStream()) { stream.write(bytes); }
            int code = connection.getResponseCode();
            InputStream stream = code == 200 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new IOException("Сервер обработки недоступен.");
            String body;
            try (stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (output.size() + count > 512000) throw new IOException("Ответ сервера слишком большой.");
                    output.write(buffer, 0, count);
                }
                body = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            JSONObject result = new JSONObject(body);
            if (code != 200) throw new IOException(result.optString("error", "Сервер обработки недоступен."));
            if (action.equals("study")) { result.getString("detailed"); result.getString("short"); result.getJSONArray("clarifications"); result.getJSONArray("studentQuestions"); }
            else if (action.equals("test")) result.getJSONArray("questions");
            else result.getString("answer");
            return result.toString();
        } finally { connection.disconnect(); }
    }
    private GeminiClient() {}
}
