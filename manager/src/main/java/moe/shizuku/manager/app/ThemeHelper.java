package moe.shizuku.manager.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import androidx.annotation.StyleRes;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.snackbar.Snackbar;
import moe.shizuku.manager.R;
import moe.shizuku.manager.ShizukuSettings;

public class ThemeHelper {

    private static final String THEME_DEFAULT = "DEFAULT";

    public static boolean isUsingSystemColor() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ShizukuSettings.getPreferences().getBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, true);
    }

    public static String getTheme(Context context) {
        return ShizukuSettings.getPreferences().getString(ShizukuSettings.Keys.KEY_LIGHT_THEME, THEME_DEFAULT);
    }

    @StyleRes
    public static int getThemeStyleRes(Context context) {
        return R.style.ThemeOverlay;
    }

    private static int resolveColor(Context context, int color) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(color, typedValue, true);
        return typedValue.data;
    }
}
