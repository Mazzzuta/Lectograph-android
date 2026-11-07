package ru.lectograph.app.ui.settings;

import ru.lectograph.app.ui.components.ScreenUi;

public final class SettingsScreen {
    public static void show(ScreenUi ui, android.content.SharedPreferences preferences, Runnable refresh) {
        ui.label("Настройки", 32, ui.theme.foreground()); ui.label("Цветовая схема", 21, ui.theme.foreground());
        for (String[] theme : new String[][]{{"system", "Как в системе"}, {"dark", "Графитовая"}, {"light", "Светлая · цвет кости"}})
            ui.button(theme[1], () -> { preferences.edit().putString("theme", theme[0]).apply(); refresh.run(); });
        ui.label("Хранение", 21, ui.theme.foreground());
        ui.label("Записи и заметки хранятся на этом устройстве. Облачная обработка ещё не подключена. Экспорт и импорт будут добавлены отдельно.", 15, ui.theme.muted());
        ui.label("Лектограф · 0.2.0", 14, ui.theme.muted());
    }
}
