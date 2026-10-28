package moe.shizuku.manager.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.magisk317.uikit.theme.MagiskThemeMode
import io.github.magisk317.uikit.theme.MagiskUiKitTheme
import io.github.magisk317.uikit.theme.UiKitStyle
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.R

/**
 * The View side resolves its palette in AppActivity.onApplyUserThemeResource:
 * the dynamic-colour overlays when "use system colour" is on, the brand accent
 * otherwise, and the black palette for the black night theme. This applies the
 * same choices to Compose surfaces through ui-kit's theme, so screens on both
 * sides of the View-to-Compose migration render from one set of colours.
 *
 * The style axis is read here rather than passed by each of the six call sites:
 * the kit ships two component families and picks between them from this value,
 * so one call site renders Miuix or Expressive chrome without importing either.
 * The View chrome (appbar layouts) is style-agnostic, so only Compose moves.
 *
 * themeMode is a MagiskThemeMode ordinal, the same scale the sibling projects
 * store, so PureBlack arrives as its own mode instead of a separate flag.
 */
@Composable
fun ShizukuTheme(
    nightMode: Int,
    useSystemColor: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val mode = MagiskThemeMode.fromValue(nightMode)
    val dark = when (mode) {
        MagiskThemeMode.Light -> false
        MagiskThemeMode.Dark, MagiskThemeMode.PureBlack -> true
        MagiskThemeMode.System -> isSystemInDarkTheme()
    }
    val dynamicColor = useSystemColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val accent = when {
        dynamicColor -> 0
        dark -> ContextCompat.getColor(context, R.color.app_color_dark)
        else -> ContextCompat.getColor(context, R.color.app_color_light)
    }
    MagiskUiKitTheme(
        themeMode = mode.value,
        dynamicColor = dynamicColor,
        accentColor = accent,
        uiKitStyle = UiKitStyle.fromValue(ShizukuSettings.getUiKitStyle()),
        content = content,
    )
}
