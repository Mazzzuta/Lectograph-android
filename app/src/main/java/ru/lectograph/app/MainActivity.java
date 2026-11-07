package ru.lectograph.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import ru.lectograph.app.database.LectureStore;
import ru.lectograph.app.models.Lecture;
import ru.lectograph.app.recording.RecordingService;
import ru.lectograph.app.navigation.BottomNavigation;
import ru.lectograph.app.ui.components.*;
import ru.lectograph.app.ui.library.LibraryScreen;
import ru.lectograph.app.ui.lecture.LectureScreen;
import ru.lectograph.app.ui.recording.RecordingScreen;
import ru.lectograph.app.ui.settings.SettingsScreen;
import ru.lectograph.app.ui.theme.Palette;
import ru.lectograph.app.ui.theme.GlassStyle;
import android.view.Gravity;

public class MainActivity extends Activity {
    private LectureStore store;
    private RecordingScreen recordingScreen;
    private LectureScreen lectureScreen;
    private LectureDialogs dialogs;
    private String section = "library", subject = "";
    private long openedId = -1, shownRecording = -1;
    private String pendingTitle = "", pendingSubject = "";
    private boolean starting;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (shownRecording != RecordingService.activeId && !starting) {
                if (shownRecording != -1 && RecordingService.activeId == -1) {
                    openedId = shownRecording;
                    Lecture saved = store.get(openedId);
                    section = saved != null && saved.subject().isEmpty() ? "drafts" : "library";
                }
                render();
            }
            if (RecordingService.activeId != -1) starting = false;
            if (starting && RecordingService.error != null) { starting = false; render(); }
            if (recordingScreen != null) recordingScreen.refresh();
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
        out.putString("section", section); out.putString("subject", subject); out.putLong("lecture", openedId);
        super.onSaveInstanceState(out);
    }
    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
    @Override protected void onDestroy() { stopPlayback(); store.close(); super.onDestroy(); }

    private void render() {
        stopPlayback(); recordingScreen = null;
        shownRecording = RecordingService.activeId;
        Palette theme = Palette.resolve(this, getPreferences(0).getString("theme", "dark"));
        FrameLayout root = new FrameLayout(this);
        root.setBackground(GlassStyle.background(root, theme));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        ScreenUi ui = new ScreenUi(this, page, theme);
        page.setPadding(ui.dp(22), ui.dp(20), ui.dp(22), ui.dp(24));
        scroll.setClipToPadding(false); scroll.setPadding(0, 0, 0, ui.dp(112));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(page); root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        dialogs = new LectureDialogs(this, ui, store);
        Lecture opened = openedId == -1 ? null : store.get(openedId);
        if (RecordingService.activeId != -1) recordingScreen = new RecordingScreen(ui, store);
        else if (opened != null) lectureScreen = new LectureScreen(this, ui, store, opened);
        else if (section.equals("settings")) SettingsScreen.show(ui, getPreferences(0), this::render);
        else LibraryScreen.show(this, ui, store, section.equals("drafts"), subject);
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        navParams.setMargins(ui.dp(22), 0, ui.dp(22), ui.dp(18));
        root.addView(BottomNavigation.create(ui, section, destination -> {
            section = destination; subject = ""; openedId = -1; render();
        }), navParams);
    }
    public void openSubject(String name) { subject = name; render(); }
    public void openLecture(long id) {
        openedId = id;
        Lecture lecture = store.get(id);
        if (lecture != null) section = lecture.subject().isEmpty() ? "drafts" : "library";
        render();
    }
    public void closeLecture() { openedId = -1; render(); }
    public void refresh() { render(); }
    public void showRecordDialog() { dialogs.record(); }
    public void showSubjectDialog() { dialogs.subject(); }
    public void showEditDialog(Lecture lecture) { dialogs.edit(lecture); }
    public void startRecording(String title, String sub) {
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

    private void stopPlayback() { if (lectureScreen != null) { lectureScreen.close(); lectureScreen = null; } }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
