package ru.lectograph.app.ui.lecture;

import android.app.AlertDialog;
import android.content.Intent;
import android.widget.*;
import org.json.*;
import ru.lectograph.app.ai.GeminiClient;
import ru.lectograph.app.ai.StudyService;
import ru.lectograph.app.database.LectureStore;
import ru.lectograph.app.models.Lecture;
import ru.lectograph.app.transcription.TranscriptionService;
import ru.lectograph.app.ui.components.ScreenUi;

final class StudyPanel {
    static void show(ScreenUi ui, LectureStore store, Lecture lecture, String tab) {
        boolean test = tab.equals("test");
        String saved = store.material(lecture.id(), test ? "test" : "study");
        boolean available = GeminiClient.configured() && !StudyService.running && !TranscriptionService.running && !store.transcriptText(lecture.id()).trim().isEmpty();
        if (!GeminiClient.configured()) ui.label("Сервер обработки пока не подключён. Распознавание и сохранённые материалы доступны без него.", 14, ui.theme.muted());
        else if (!StudyService.message.isEmpty()) ui.label(StudyService.message, 14, ui.theme.muted());
        String action = test ? "test" : "study";
        Button generate = ui.primaryButton(test ? "Создать тест · до 10 вопросов" : "Создать конспекты и разбор", () -> new AlertDialog.Builder(ui.context)
                .setTitle("Обработать через Gemini?").setMessage("Текст этой лекции будет отправлен на сервер и в Gemini. Аудио останется на телефоне. На бесплатном уровне Gemini данные могут использоваться для улучшения сервисов Google.")
                .setNegativeButton("Отмена", null).setPositiveButton("Отправить текст", (d, w) -> start(ui, lecture.id(), action, "")).show());
        generate.setEnabled(available);
        ui.label(test ? "До 3 новых тестов в сутки. Готовый тест можно проходить без ограничений." : "До 3 обработок лекций в сутки. Повторное открытие материалов не расходует запросы.", 12, ui.theme.muted());
        if (saved.isEmpty()) { ui.label("Готовых материалов пока нет. Сначала распознайте запись.", 15, ui.theme.muted()); return; }
        try {
            JSONObject data = new JSONObject(saved);
            if (test) { test(ui, data.getJSONArray("questions")); return; }
            if (tab.equals("summary")) {
                ui.label("Кратко", 22, ui.theme.foreground()); ui.label(data.getString("short"), 17, ui.theme.foreground()).setTextIsSelectable(true);
                ui.label("Подробный конспект", 22, ui.theme.foreground()); ui.label(data.getString("detailed"), 17, ui.theme.foreground()).setTextIsSelectable(true);
            } else {
                ui.label("Уточнения · добавлено ИИ", 22, ui.theme.foreground());
                JSONArray clarifications = data.getJSONArray("clarifications");
                if (clarifications.length() == 0) ui.label("Уточнений не найдено.", 15, ui.theme.muted());
                for (int i = 0; i < clarifications.length(); i++) {
                    JSONObject item = clarifications.getJSONObject(i);
                    ui.label(item.getString("fragment"), 17, ui.theme.foreground()); ui.label(item.getString("explanation"), 15, ui.theme.muted());
                }
                ui.label("Вопросы из записи", 22, ui.theme.foreground());
                JSONArray questions = data.getJSONArray("studentQuestions");
                if (questions.length() == 0) ui.label("Вопросов студентов не найдено.", 15, ui.theme.muted());
                for (int i = 0; i < questions.length(); i++) {
                    JSONObject item = questions.getJSONObject(i); ui.label(item.getString("question"), 18, ui.theme.foreground());
                    if (!item.getString("teacherAnswer").isEmpty()) ui.label("Ответ преподавателя: " + item.getString("teacherAnswer"), 16, ui.theme.foreground());
                    else ui.label("Ответ преподавателя в записи не найден.", 14, ui.theme.muted());
                    if (!item.getString("aiAnswer").isEmpty()) ui.label("Пояснение ИИ по лекции: " + item.getString("aiAnswer"), 16, ui.theme.muted());
                }
                ui.label("Свой вопрос по лекции", 22, ui.theme.foreground());
                EditText input = ui.input("До 1000 символов", ""); input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
                Button ask = ui.button("Задать вопрос · до 10 в сутки", () -> {
                    if (input.getText().toString().trim().isEmpty()) { input.setError("Введите вопрос"); return; }
                    start(ui, lecture.id(), "question", input.getText().toString());
                }); ask.setEnabled(available);
                String answer = store.material(lecture.id(), "question");
                if (!answer.isEmpty()) ui.label("Ответ ИИ по лекции: " + new JSONObject(answer).getString("answer"), 17, ui.theme.foreground());
                ui.label("Интернет-поиск пока не подключён. Пояснения строятся только по лекции.", 13, ui.theme.muted());
            }
        } catch (JSONException e) { ui.toast("Не удалось открыть материалы. Создайте их повторно."); }
    }
    private static void test(ScreenUi ui, JSONArray questions) throws JSONException {
        if (questions.length() == 0) { ui.label("Недостаточно материала для вопросов.", 16, ui.theme.muted()); return; }
        RadioGroup[] groups = new RadioGroup[questions.length()];
        TextView[] feedback = new TextView[questions.length()];
        for (int i = 0; i < questions.length(); i++) {
            JSONObject question = questions.getJSONObject(i);
            ui.label((i + 1) + ". " + question.getString("question"), 19, ui.theme.foreground());
            RadioGroup group = new RadioGroup(ui.context); JSONArray options = question.getJSONArray("options");
            for (int j = 0; j < options.length(); j++) {
                RadioButton option = new RadioButton(ui.context); option.setId(android.view.View.generateViewId());
                option.setText(options.getString(j)); option.setTextColor(ui.theme.foreground()); option.setMinHeight(ui.dp(48)); group.addView(option);
            }
            groups[i] = group; ui.page.addView(group); feedback[i] = ui.label("", 15, ui.theme.muted());
        }
        ui.button("Проверить ответы", () -> {
            try {
                for (RadioGroup group : groups) if (group.getCheckedRadioButtonId() == -1) { ui.toast("Ответьте на все вопросы"); return; }
                int correct = 0;
                for (int i = 0; i < groups.length; i++) {
                    JSONObject question = questions.getJSONObject(i);
                    boolean right = groups[i].indexOfChild(groups[i].findViewById(groups[i].getCheckedRadioButtonId())) == question.getInt("correctIndex");
                    if (right) correct++;
                    feedback[i].setText((right ? "Верно. " : "Ошибка. ") + question.getString("explanation") + "\nИз лекции: «" + question.getString("sourceQuote") + "»");
                }
                ui.toast("Результат: " + correct + " из " + groups.length);
            } catch (JSONException e) { ui.toast("Не удалось проверить тест"); }
        });
    }
    private static void start(ScreenUi ui, long id, String action, String question) {
        try { ui.context.startForegroundService(new Intent(ui.context, StudyService.class).putExtra("lecture", id).putExtra("action", action).putExtra("question", question)); }
        catch (RuntimeException e) { ui.toast("Не удалось запустить обработку"); }
    }
}
