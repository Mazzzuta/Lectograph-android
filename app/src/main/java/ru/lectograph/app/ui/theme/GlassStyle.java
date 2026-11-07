package ru.lectograph.app.ui.theme;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

public final class GlassStyle {
    public static Drawable background(View view, Palette palette) {
        boolean dark = Color.luminance(palette.background()) < 0.5;
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0xFF121416, 0xFF292D32, 0xFF141618}
                     : new int[]{0xFFF5F0E5, 0xFFE1DDD4, 0xFFF5F0E5});
        GradientDrawable glow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{dark ? 0x506F7883 : 0xAAFFFFFF, Color.TRANSPARENT});
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.1f, 0.05f);
        glow.setGradientRadius(420 * view.getResources().getDisplayMetrics().density);
        return new LayerDrawable(new Drawable[]{base, glow});
    }

    public static GradientDrawable surface(View view, Palette palette, int radius) {
        boolean dark = Color.luminance(palette.background()) < 0.5;
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0x60494E54, 0xA02B2E32, 0x70282B30}
                     : new int[]{0xDFFFFFFF, 0xA6E4DFD5, 0xBFFFFFFF});
        float density = view.getResources().getDisplayMetrics().density;
        glass.setCornerRadius(radius * density);
        glass.setStroke(Math.max(1, Math.round(density)), dark ? 0x38FFFFFF : 0x55FFFFFF);
        return glass;
    }

    public static void apply(View view, Palette palette, int radius) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x25FFFFFF), surface(view, palette, radius), null));
        view.setClipToOutline(true);
    }

    private GlassStyle() {}
}
