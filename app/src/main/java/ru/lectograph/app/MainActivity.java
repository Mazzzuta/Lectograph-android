package ru.lectograph.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private LectureStore store;
    private LinearLayout page;
    private TextView timer;
    private Button pause;
    private String section = "library", subject = "";
    private long openedId = -1, shownRecording = -1;
    private int background, foreground, muted, surface;
    private EditText noteEditor;
    private String savedNotes;
    private MediaPlayer player;
    private String pendingTitle = "", pendingSubject = "";
    private boolean starting;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (shownRecording != RecordingService.activeId && !starting) {
                if (shownRecording != -1 && RecordingService.activeId == -1) {
                    openedId = shownRecording;
                    LectureStore.Lecture saved = store.get(openedId);
                    section = saved != null && saved.subject().isEmpty() ? "drafts" : "library";
                }
                render();
            }
            if (RecordingService.activeId != -1) starting = false;
            if (starting && RecordingService.error != null) { starting = false; render(); }
            if (timer != null) timer.setText(time(RecordingService.elapsed()));
            if (pause != null) pause.setText(RecordingService.pausedAt == 0 ? "Пауза" : "Продолжить");
            if (RecordingService.error != null) { toast(RecordingService.error); RecordingService.error = null; }
            handler.postDelayed(this, 500);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new LectureStore(this);
        if (RecordingService.activeId == -1) store.recoverInterrupted();
        if (state != null) { section = state.getString("section", "library"); subject = state.getString("subject", ""); openedId = state.getLong("lecture", -1); }
        render();
        if (getIntent().getBooleanExtra("quickRecord", false) && state == null) {
            getIntent().removeExtra("quickRecord");
            getWindow().getDecorView().post(() -> startRecording("", ""));
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        saveNotes(); out.putString("section", section); out.putString("subject", subject); out.putLong("lecture", openedId);
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { saveNotes(); handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { stopPlayback(); store.close(); super.onDestroy(); }
    private void render() {
        saveNotes(); stopPlayback(); noteEditor = null; timer = null; pause = null;
        shownRecording = RecordingService.activeId;
        String theme = getPreferences(0).getString("theme", "system");
        boolean dark = theme.equals("dark") || (theme.equals("system") && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        background = Color.parseColor(dark ? "#191B1E" : "#F5F0E5");
        foreground = Color.parseColor(dark ? "#F5F0E5" : "#202226");
        muted = Color.parseColor(dark ? "#B9B5AC" : "#5C5D60");
        surface = Color.parseColor(dark ? "#282B30" : "#E8E1D4");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(background);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        setContentView(root);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(22), dp(20), dp(22), dp(24));
        scroll.addView(page); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (RecordingService.activeId != -1) recording();
        else if (openedId != -1 && store.get(openedId) != null) lecture();
        else if (section.equals("settings")) settings();
        else library();
        LinearLayout nav = new LinearLayout(this); nav.setPadding(dp(8), dp(4), dp(8), dp(8));
        for (String[] item : new String[][]{{"library", "Библиотека"}, {"drafts", "Черновики"}, {"settings", "Настройки"}}) {
            Button b = new Button(this); b.setText(item[1]); b.setTextSize(12); b.setAllCaps(false);
            b.setTextColor(section.equals(item[0]) ? foreground : muted); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(surface));
            b.setOnClickListener(v -> { section = item[0]; subject = ""; openedId = -1; render(); });
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        root.addView(nav);
    }
    private void library() {
        boolean drafts = section.equals("drafts");
        label("ЛЕКТОГРАФ", 12, muted);
        label(drafts ? "Черновики" : subject.isEmpty() ? "Твои лекции" : subject, 32, foreground);
        label(drafts ? "Быстрые записи, которые можно оформить позже" : "Записывай. Разбирайся. Запоминай.", 15, muted);
        button("●  Быстрая запись", () -> startRecording("", ""));
        button("Запись с названием и предметом", this::recordDialog);
        if (!drafts && subject.isEmpty()) {
            label("Предметы", 21, foreground);
            for (String name : store.subjects()) button(name, () -> { subject = name; render(); });
            button("+ Создать предмет", this::subjectDialog);
        } else if (!subject.isEmpty()) button("← Все предметы", () -> { subject = ""; render(); });
        label(drafts ? "Без предмета" : "Последние записи", 21, foreground);
        EditText search = input("Поиск по названию и заметкам", "");
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); page.addView(results);
        Runnable filter = () -> {
            results.removeAllViews();
            String query = search.getText().toString().toLowerCase(Locale.ROOT);
            int count = 0;
            for (LectureStore.Lecture l : store.all()) {
                if (drafts != l.subject().isEmpty()) continue;
                if (!subject.isEmpty() && !subject.equals(l.subject())) continue;
                if (!(l.title() + " " + l.notes()).toLowerCase(Locale.ROOT).contains(query)) continue;
                Button card = new Button(this); card.setAllCaps(false); card.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                card.setText(l.title() + "\n" + (l.subject().isEmpty() ? "Черновик" : l.subject()) + " · " + date(l.created()) + " · " + time(l.duration()) + "\n" + status(l.status()));
                card.setTextColor(foreground); card.setBackgroundTintList(android.content.res.ColorStateList.valueOf(surface)); card.setPadding(dp(16), dp(14), dp(16), dp(14));
                card.setOnClickListener(v -> { openedId = l.id(); render(); });
                results.addView(card, new LinearLayout.LayoutParams(-1, -2)); count++;
            }
            if (count == 0) { TextView empty = new TextView(this); empty.setText("Пока нет лекций. Начни с записи."); empty.setTextColor(muted); empty.setPadding(0, dp(24), 0, 0); results.addView(empty); }
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter.run(); }
            public void afterTextChanged(android.text.Editable e) {}
        }); filter.run();
    }
    private void recording() {
        LectureStore.Lecture l = store.get(RecordingService.activeId);
        label("ДИКТОФОН", 12, muted); label(l == null ? "Запись лекции" : l.title(), 28, foreground);
        timer = label(time(RecordingService.elapsed()), 56, foreground);
        label("Запись продолжается при выключенном экране. После остановки она сохранится на устройстве.", 15, muted);
        pause = button("Пауза", () -> startService(new Intent(this, RecordingService.class).setAction("pause")));
        button("Завершить и сохранить", () -> {
            saveNotes(); startService(new Intent(this, RecordingService.class).setAction("stop"));
        });
        if (l != null) notes(l);
    }
    private void lecture() {
        LectureStore.Lecture l = store.get(openedId);
        button("← Назад", () -> { openedId = -1; render(); });
        label(l.subject().isEmpty() ? "ЧЕРНОВИК" : l.subject(), 12, muted); label(l.title(), 30, foreground);
        label(date(l.created()) + " · " + time(l.duration()) + " · " + status(l.status()), 14, muted);
        button("Изменить название и предмет", () -> editDialog(l));
        Button play = button("▶ Прослушать запись", () -> {});
        play.setOnClickListener(v -> {
            if (player != null) { stopPlayback(); play.setText("▶ Прослушать запись"); return; }
            try {
                player = new MediaPlayer(); player.setDataSource(new File(new File(getFilesDir(), "audio"), l.audio()).getAbsolutePath());
                player.prepare(); player.setOnCompletionListener(p -> { stopPlayback(); play.setText("▶ Прослушать запись"); });
                player.start(); play.setText("■ Остановить воспроизведение");
            } catch (Exception e) { stopPlayback(); toast("Аудиофайл недоступен или повреждён"); }
        });
        label("Материалы лекции", 21, foreground);
        label("Расшифровка, конспекты и тесты пока недоступны: Whisper base и Gemini ещё не подключены.", 15, muted);
        notes(l);
        button("Удалить лекцию", () -> new AlertDialog.Builder(this).setTitle("Удалить лекцию?")
                .setMessage("Аудио и заметки будут удалены без возможности восстановления.")
                .setNegativeButton("Отмена", null).setPositiveButton("Удалить", (d, w) -> {
                    stopPlayback(); noteEditor = null;
                    File file = new File(new File(getFilesDir(), "audio"), l.audio());
                    if (file.exists() && !file.delete()) { toast("Не удалось удалить аудиофайл"); return; }
                    store.delete(l.id()); openedId = -1; render();
                }).show());
    }
    private void notes(LectureStore.Lecture l) {
        label("Мои заметки", 21, foreground); savedNotes = l.notes();
        noteEditor = input("Что важно запомнить?", savedNotes); noteEditor.setMinLines(5); noteEditor.setGravity(Gravity.TOP);
        noteEditor.setTag(l.id());
        noteEditor.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { saveNotes(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        label("Заметки сохраняются автоматически", 12, muted);
    }
    private void saveNotes() {
        if (noteEditor == null) return;
        String text = noteEditor.getText().toString();
        if (text.equals(savedNotes)) return;
        LectureStore.Lecture l = store.get((long) noteEditor.getTag());
        if (l != null) { store.edit(l.id(), l.title(), l.subject(), text); savedNotes = text; }
    }
    private void settings() {
        label("Настройки", 32, foreground); label("Цветовая схема", 21, foreground);
        for (String[] theme : new String[][]{{"system", "Как в системе"}, {"dark", "Графитовая"}, {"light", "Светлая · цвет кости"}})
            button(theme[1], () -> { getPreferences(0).edit().putString("theme", theme[0]).apply(); render(); });
        label("Хранение", 21, foreground);
        label("Записи и заметки хранятся на этом устройстве. Облачная обработка ещё не подключена. Экспорт и импорт будут добавлены отдельно.", 15, muted);
        label("Лектограф · 0.1.0", 14, muted);
    }
    private void recordDialog() {
        LinearLayout form = form(); EditText title = field(form, "Название лекции", ""); AutoCompleteTextView sub = subjectField(form, "");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Новая запись").setView(form).setNegativeButton("Отмена", null).setPositiveButton("Начать", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty() || sub.getText().toString().trim().isEmpty()) { toast("Укажите название и предмет либо используйте быструю запись"); return; }
            dialog.dismiss(); startRecording(title.getText().toString().trim(), sub.getText().toString().trim());
        })); dialog.show();
    }
    private void editDialog(LectureStore.Lecture l) {
        LinearLayout form = form(); EditText title = field(form, "Название лекции", l.title()); AutoCompleteTextView sub = subjectField(form, l.subject());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Оформить лекцию").setView(form).setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty()) { title.setError("Укажите название"); return; }
            saveNotes(); store.edit(l.id(), title.getText().toString(), sub.getText().toString(), store.get(l.id()).notes());
            section = sub.getText().toString().trim().isEmpty() ? "drafts" : "library";
            dialog.dismiss(); render();
        })); dialog.show();
    }
    private void subjectDialog() {
        LinearLayout form = form(); EditText name = field(form, "Название предмета", "");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Новый предмет").setView(form).setNegativeButton("Отмена", null).setPositiveButton("Создать", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Укажите название"); return; }
            store.addSubject(name.getText().toString()); dialog.dismiss(); render();
        })); dialog.show();
    }
    private void startRecording(String title, String sub) {
        if (RecordingService.activeId != -1 || starting) { render(); return; }
        stopPlayback();
        pendingTitle = title; pendingSubject = sub;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        starting = true;
        try { startForegroundService(new Intent(this, RecordingService.class).putExtra("title", title).putExtra("subject", sub)); }
        catch (RuntimeException e) { starting = false; toast("Не удалось запустить диктофон"); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 10) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startRecording(pendingTitle, pendingSubject);
            else toast("Для записи нужен доступ к микрофону");
        }
    }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setPadding(0, dp(10), 0, dp(10)); page.addView(view); return view;
    }
    private Button button(String text, Runnable click) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(foreground);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(surface)); b.setMinHeight(dp(52));
        b.setOnClickListener(v -> click.run()); page.addView(b, new LinearLayout.LayoutParams(-1, -2)); return b;
    }
    private EditText input(String hint, String value) { return field(page, hint, value); }
    private EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setTextColor(foreground); e.setHintTextColor(muted); e.setTextSize(16); e.setMinHeight(dp(52)); parent.addView(e, new LinearLayout.LayoutParams(-1, -2)); return e;
    }
    private AutoCompleteTextView subjectField(LinearLayout parent, String value) {
        AutoCompleteTextView e = new AutoCompleteTextView(this); e.setHint("Предмет (пусто — черновик)"); e.setText(value); e.setTextColor(foreground); e.setHintTextColor(muted); e.setThreshold(0);
        e.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, store.subjects())); parent.addView(e); return e;
    }
    private LinearLayout form() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(24), dp(8), dp(24), 0); layout.setBackgroundColor(background); return layout; }
    private void stopPlayback() { if (player != null) { player.release(); player = null; } }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    static String time(long ms) { long seconds = ms / 1000; return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60); }
    private String date(long value) { return new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date(value)); }
    private String status(String value) {
        return switch (value) { case "recording" -> "Идёт запись"; case "recorded" -> "Аудио сохранено"; case "interrupted" -> "Запись прервана — проверьте аудио"; default -> "Ошибка записи — проверьте аудио"; };
    }
}
