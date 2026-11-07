package ru.lectograph.app.ui.components;

import android.content.Context;
import android.widget.*;
import ru.lectograph.app.ui.theme.Palette;
import ru.lectograph.app.ui.theme.GlassStyle;
import android.graphics.Typeface;
import android.view.Gravity;

public final class ScreenUi {
    public final Context context;
    public final LinearLayout page;
    public final Palette theme;
    public ScreenUi(Context context, LinearLayout page, Palette theme) {
        this.context = context; this.page = page; this.theme = theme;
    }
    public TextView label(String text, int size, int color) {
        TextView view = new TextView(context); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(size >= 21 ? 22 : 8), 0, dp(10));
        view.setLineSpacing(dp(3), 1f);
        if (size >= 28) view.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        if (size <= 12) view.setLetterSpacing(0.12f);
        page.addView(view); return view;
    }
    public Button button(String text, Runnable click) {
        Button b = new Button(context); b.setText(text); b.setAllCaps(false); b.setTextColor(theme.foreground());
        styleButton(b);
        b.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5)); page.addView(b, params); return b;
    }
    public void styleButton(Button button) {
        button.setBackgroundTintList(null); GlassStyle.apply(button, theme, 24);
        button.setMinHeight(dp(56)); button.setMinimumHeight(dp(56));
        button.setTextSize(15); button.setPadding(dp(20), dp(15), dp(20), dp(15));
        button.setStateListAnimator(null); button.setElevation(dp(2));
    }
    public Button primaryButton(String text, Runnable click) {
        Button button = button(text, click);
        android.graphics.drawable.GradientDrawable face = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFFFFFFF, 0xFFDDE0E4});
        face.setCornerRadius(dp(28)); face.setStroke(dp(1), 0xFFFFFFFF);
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000), face, null));
        button.setTextColor(0xFF17191C); button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(64)); return button;
    }
    public EditText input(String hint, String value) { return field(page, hint, value); }
    public EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(context); e.setHint(hint); e.setText(value); styleField(e);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(6), 0, dp(6));
        parent.addView(e, params); return e;
    }
    public AutoCompleteTextView subjectField(LinearLayout parent, String value, java.util.List<String> subjects) {
        AutoCompleteTextView e = new AutoCompleteTextView(context); e.setHint("Предмет (пусто — черновик)"); e.setText(value); styleField(e); e.setThreshold(0);
        e.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, subjects));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(6), 0, dp(6)); parent.addView(e, params); return e;
    }
    private void styleField(EditText field) {
        field.setTextColor(theme.foreground()); field.setHintTextColor(theme.muted()); field.setTextSize(15);
        field.setMinHeight(dp(56)); field.setPadding(dp(18), dp(16), dp(18), dp(16));
        field.setBackgroundTintList(null); field.setBackground(GlassStyle.surface(field, theme, 20));
        field.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    }
    public LinearLayout form() { LinearLayout layout = new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(24), dp(8), dp(24), 0); layout.setBackgroundColor(theme.background()); return layout; }

    public int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    public void toast(String text) { Toast.makeText(context, text, Toast.LENGTH_LONG).show(); }
}
