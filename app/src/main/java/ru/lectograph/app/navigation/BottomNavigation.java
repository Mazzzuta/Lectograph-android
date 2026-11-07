package ru.lectograph.app.navigation;

import android.widget.Button;
import android.widget.LinearLayout;
import java.util.function.Consumer;
import ru.lectograph.app.ui.components.ScreenUi;

public final class BottomNavigation {
    public static LinearLayout create(ScreenUi ui, String selected, Consumer<String> navigate) {
        LinearLayout nav = new LinearLayout(ui.context);
        nav.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(8));
        for (String[] item : new String[][]{{"library", "Библиотека"}, {"drafts", "Черновики"}, {"settings", "Настройки"}}) {
            Button button = new Button(ui.context);
            button.setText(item[1]); button.setTextSize(12); button.setAllCaps(false);
            button.setTextColor(selected.equals(item[0]) ? ui.theme.foreground() : ui.theme.muted());
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.theme.surface()));
            button.setOnClickListener(v -> navigate.accept(item[0]));
            nav.addView(button, new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        }
        return nav;
    }
}
