package ru.lectograph.app.ui.theme;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

public record Palette(int background, int foreground, int muted, int surface) {
    public static Palette resolve(Context context, String theme) {
        boolean dark = theme.equals("dark") || (theme.equals("system") &&
                (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        return new Palette(Color.parseColor(dark ? "#191B1E" : "#F5F0E5"),
                Color.parseColor(dark ? "#F5F0E5" : "#202226"),
                Color.parseColor(dark ? "#B9B5AC" : "#5C5D60"),
                Color.parseColor(dark ? "#282B30" : "#E8E1D4"));
    }
}
