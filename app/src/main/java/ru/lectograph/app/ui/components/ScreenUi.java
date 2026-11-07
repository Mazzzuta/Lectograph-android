package ru.lectograph.app.ui.components;

import android.content.Context;
import android.widget.*;
import ru.lectograph.app.ui.theme.Palette;

public final class ScreenUi {
    public final Context context;
    public final LinearLayout page;
    public final Palette theme;
    public ScreenUi(Context context, LinearLayout page, Palette theme) {
        this.context = context; this.page = page; this.theme = theme;
    }
    public TextView label(String text, int size, int color) {
        TextView view = new TextView(context); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setPadding(0, dp(10), 0, dp(10)); page.addView(view); return view;
    }
    public Button button(String text, Runnable click) {
        Button b = new Button(context); b.setText(text); b.setAllCaps(false); b.setTextColor(theme.foreground());
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.surface())); b.setMinHeight(dp(52));
        b.setOnClickListener(v -> click.run()); page.addView(b, new LinearLayout.LayoutParams(-1, -2)); return b;
    }
    public EditText input(String hint, String value) { return field(page, hint, value); }
    public EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(context); e.setHint(hint); e.setText(value); e.setTextColor(theme.foreground()); e.setHintTextColor(theme.muted()); e.setTextSize(16); e.setMinHeight(dp(52)); parent.addView(e, new LinearLayout.LayoutParams(-1, -2)); return e;
    }
    public AutoCompleteTextView subjectField(LinearLayout parent, String value, java.util.List<String> subjects) {
        AutoCompleteTextView e = new AutoCompleteTextView(context); e.setHint("Предмет (пусто — черновик)"); e.setText(value); e.setTextColor(theme.foreground()); e.setHintTextColor(theme.muted()); e.setThreshold(0);
        e.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, subjects)); parent.addView(e); return e;
    }
    public LinearLayout form() { LinearLayout layout = new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(24), dp(8), dp(24), 0); layout.setBackgroundColor(theme.background()); return layout; }

    public int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    public void toast(String text) { Toast.makeText(context, text, Toast.LENGTH_LONG).show(); }
}
