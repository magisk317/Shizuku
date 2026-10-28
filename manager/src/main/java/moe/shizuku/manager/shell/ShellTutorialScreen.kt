package moe.shizuku.manager.shell

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ktx.rememberHtmlText
import rikka.compatibility.DeviceCompatibility
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.surface.AppPrimaryButton

private const val SH_NAME = "rish"
private const val DEX_NAME = "rish_shizuku.dex"
private const val COPY_COMMAND = "cp /sdcard/chosen-folder/* /data/data/terminal.package.name/files"
private const val RUN_COMMAND = "sh /path/to/$SH_NAME"
private const val SH_MONO = "<font face=\"monospace\">$SH_NAME</font>"
private const val DEX_MONO = "<font face=\"monospace\">$DEX_NAME</font>"

/**
 * Compose replacement for the rish tutorial scroll: the intro card (which
 * doubles as the rish documentation link), the optional MIUI warning and the
 * three numbered steps with selectable commands. The folder picker and the
 * file export stay in [ShellTutorialActivity].
 */
@Composable
fun ShellTutorialScreen(
    onExportFiles: () -> Unit,
    onOpenRishDocs: () -> Unit,
) {
    val isMiui = rememberMiui()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TutorialCard(
            container = appColor(AppColorRole.SecondaryContainer),
            contentColor = appColor(AppColorRole.OnSecondaryContainer),
            iconRes = R.drawable.ic_help_outline_24dp,
            onClick = onOpenRishDocs,
        ) {
            AppText(
                rememberHtmlText(R.string.rish_description, SH_MONO),
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSecondaryContainer),
            )
        }

        if (isMiui) {
            TutorialCard(
                container = appColor(AppColorRole.ErrorContainer),
                contentColor = appColor(AppColorRole.OnErrorContainer),
                iconRes = R.drawable.ic_warning_24,
            ) {
                AppText(
                    stringResource(R.string.terminal_tutorial_miui),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnErrorContainer),
                )
                AppText(
                    stringResource(R.string.terminal_tutorial_miui_2),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnErrorContainer),
                )
            }
        }

        StepRow(stepIcon = R.drawable.ic_numeric_1_circle_outline_24) {
            AppText(stringResource(R.string.terminal_tutorial_1), role = AppTextRole.BodyLarge)
            AppText(
                rememberHtmlText(R.string.terminal_tutorial_1_description, SH_MONO, DEX_MONO),
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            AppPrimaryButton(onClick = onExportFiles) {
                AppText(stringResource(R.string.terminal_export_files))
            }
        }

        StepRow(stepIcon = R.drawable.ic_numeric_2_circle_outline_24) {
            AppText(
                rememberHtmlText(R.string.terminal_tutorial_2, SH_MONO),
                role = AppTextRole.BodyLarge,
            )
            SelectableCommand(COPY_COMMAND)
            AppText(
                rememberHtmlText(R.string.terminal_tutorial_2_description, SH_MONO, SH_MONO, ".bashrc"),
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }

        StepRow(stepIcon = R.drawable.ic_numeric_3_circle_outline_24) {
            AppText(stringResource(R.string.terminal_tutorial_3), role = AppTextRole.BodyLarge)
            SelectableCommand(RUN_COMMAND)
        }
    }
}

/** The numbered-step row the View layouts built with a circled numeral and a column. */
@Composable
private fun StepRow(
    stepIcon: Int,
    content: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AppIcon(
            painterResource(stepIcon),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .padding(4.dp),
            tint = appColor(AppColorRole.OnSurfaceVariant),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun TutorialCard(
    container: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    iconRes: Int,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(
                painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@Composable
private fun SelectableCommand(command: String) {
    SelectionContainer {
        AppText(
            command,
            role = AppTextRole.Body,
            fontFamily = FontFamily.Monospace,
            color = appColor(AppColorRole.OnSurfaceVariant),
        )
    }
}

@Composable
private fun rememberMiui(): Boolean =
    remember { DeviceCompatibility.isMiui() }
