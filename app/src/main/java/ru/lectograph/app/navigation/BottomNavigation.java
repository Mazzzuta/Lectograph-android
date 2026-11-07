package ru.lectograph.app.navigation;

import android.widget.Button;
import android.widget.LinearLayout;
import java.util.function.Consumer;
import ru.lectograph.app.ui.components.ScreenUi;
import ru.lectograph.app.ui.theme.GlassStyle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

public final class BottomNavigation {
    public static LinearLayout create(ScreenUi ui, String selected, Consumer<String> navigate) {
        LinearLayout nav = new LinearLayout(ui.context);
        nav.setPadding(ui.dp(7), ui.dp(7), ui.dp(7), ui.dp(7));
        nav.setBackground(GlassStyle.surface(nav, ui.theme, 38));
        nav.setElevation(ui.dp(12));
        for (String[] item : new String[][]{{"library", "Библиотека"}, {"drafts", "Черновики"}, {"settings", "Настройки"}}) {
            Button button = new Button(ui.context);
            button.setText(item[1]); button.setTextSize(12); button.setAllCaps(false);
            boolean active = selected.equals(item[0]);
            button.setSelected(active);
            button.setMinHeight(ui.dp(56));
            button.setTextColor(active ? 0xFF191B1E : ui.theme.foreground());
            button.setBackgroundTintList(null);
            button.setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(8));
            button.setMinWidth(0); button.setMinimumWidth(0); button.setStateListAnimator(null);
            if (active) {
                GradientDrawable face = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.WHITE, 0xFFE1E3E6});
                face.setCornerRadius(ui.dp(30));
                button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000), face, null));
            } else {
                GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(ui.dp(30));
                button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x25FFFFFF), null, mask));
            }
            button.setOnClickListener(v -> navigate.accept(item[0]));
            nav.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return nav;
    }
}
