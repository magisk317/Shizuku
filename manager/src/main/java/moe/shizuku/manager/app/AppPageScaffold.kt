package moe.shizuku.manager.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magisk317.uikit.surface.PageScaffoldExpressive
import io.github.magisk317.uikit.surface.PageScaffoldMiuix
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle

/**
 * One entry over the kit's two page scaffolds.
 *
 * The kit ships PageScaffoldMiuix and PageScaffoldExpressive without a
 * dispatching wrapper, so every caller would otherwise repeat the
 * when-over-style block. This keeps that choice in one place; everything
 * else is forwarded untouched, including the content lambda that hands
 * back the list padding and the scroll modifier the page body must apply
 * to its own scrollable container.
 */
@Composable
fun AppPageScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues, Modifier) -> Unit,
) {
    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> PageScaffoldMiuix(
            title = title,
            onBack = onBack,
            actions = actions,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            content = content,
        )

        UiKitStyle.Expressive -> PageScaffoldExpressive(
            title = title,
            onBack = onBack,
            actions = actions,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            content = content,
        )
    }
}
