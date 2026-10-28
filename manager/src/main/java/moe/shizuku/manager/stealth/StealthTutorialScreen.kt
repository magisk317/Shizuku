package moe.shizuku.manager.stealth

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import rikka.core.util.ClipboardUtils
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppExtendedFloatingActionButton
import io.github.magisk317.uikit.surface.AppFloatingActionButton
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppTextField
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor

/**
 * Compose replacement for the stealth tutorial layout: the warning/info cards
 * plus the bottom bar that carries the package-name input and the action FAB.
 * Everything that needs Activity APIs -- the folder picker, the APK export and
 * the installer flows -- stays in [StealthTutorialActivity] behind callbacks.
 */
@Composable
fun StealthTutorialScreen(
    state: UiState?,
    onAction: (Action, String?) -> Unit,
) {
    val context = LocalContext.current
    var packageName by remember { mutableStateOf("") }
    val validationError = remember(packageName) { packageName.validatePackageName() }
    val busy = state is UiState.Loading || state is UiState.Pending
    val action = (state as? UiState.Idle)?.action
    val showInput = action == Action.HIDE

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (showInput) 180.dp else 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WarningCard(
                onClick = {
                    if (
                        ClipboardUtils.put(context, codeSnippet) &&
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                    ) {
                        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                AppText(
                    stringResource(R.string.stealth_warning_compatibility),
                    role = AppTextRole.Body,
                    fontWeight = FontWeight.Bold,
                    color = appColor(AppColorRole.OnErrorContainer),
                )
                AppText(
                    stringResource(R.string.stealth_warning_compatibility_2),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnErrorContainer),
                )
                AppText(
                    stringResource(R.string.stealth_warning_compatibility_3),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnErrorContainer),
                )
            }

            WarningCard {
                AppText(
                    stringResource(R.string.stealth_warning_detection),
                    role = AppTextRole.Body,
                    fontWeight = FontWeight.Bold,
                    color = appColor(AppColorRole.OnErrorContainer),
                )
            }

            InfoCard {
                AppText(
                    stringResource(R.string.stealth_info_reconfigure),
                    role = AppTextRole.Body,
                    fontWeight = FontWeight.Bold,
                    color = appColor(AppColorRole.OnSecondaryContainer),
                )
                AppText(
                    stringResource(R.string.stealth_info_rish),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnSecondaryContainer),
                )
                AppText(
                    stringResource(R.string.stealth_info_intents),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnSecondaryContainer),
                )
            }

            InfoCard {
                AppText(
                    stringResource(R.string.stealth_info_update),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnSecondaryContainer),
                )
            }
        }

        if (showInput) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(appColor(AppColorRole.SurfaceContainerLow))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AppTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = stringResource(R.string.stealth_package_name),
                    supportingText = {
                        val text = when {
                            packageName.isNotEmpty() && validationError != null -> stringResource(validationError)
                            else -> stringResource(R.string.stealth_package_name_helper_text)
                        }
                        AppText(text)
                    },
                    isError = packageName.isNotEmpty() && validationError != null,
                )
            }
        }

        val fabAction = action
        if (!busy && fabAction != null) {
            val label = stringResource(
                if (fabAction == Action.UNHIDE) R.string.stealth_unhide else R.string.stealth_hide
            )
            val icon = painterResource(
                if (fabAction == Action.UNHIDE) R.drawable.ic_visibility_on_filled_24
                else R.drawable.ic_visibility_off_filled_24
            )
            AppExtendedFloatingActionButton(
                text = label,
                icon = icon,
                onClick = {
                    if (validationError == null) onAction(fabAction, packageName.ifEmpty { null })
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (showInput) 96.dp else 24.dp),
            )
        } else if (busy) {
            AppFloatingActionButton(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (showInput) 96.dp else 24.dp),
            ) {
                AppCircularProgressIndicator(Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun WarningCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColor(AppColorRole.ErrorContainer))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = appColor(AppColorRole.OnErrorContainer),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColor(AppColorRole.SecondaryContainer))
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(
                painterResource(R.drawable.ic_help_outline_24dp),
                contentDescription = null,
                tint = appColor(AppColorRole.OnSecondaryContainer),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

private const val codeSnippet = """
import android.content.Context
import rikka.shizuku.ShizukuProvider

private fun Context.shizukuPermission() =
    runCatching {
        packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0)
    }.getOrNull()

fun Context.isShizukuInstalled() =
    shizukuPermission() != null

fun Context.getShizukuPackageName() =
    shizukuPermission()?.packageName
"""
