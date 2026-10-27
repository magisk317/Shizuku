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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
            container = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconRes = R.drawable.ic_help_outline_24dp,
            onClick = onOpenRishDocs,
        ) {
            Text(
                rememberHtmlText(R.string.rish_description, SH_MONO),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        if (isMiui) {
            TutorialCard(
                container = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                iconRes = R.drawable.ic_warning_24,
            ) {
                Text(
                    stringResource(R.string.terminal_tutorial_miui),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(R.string.terminal_tutorial_miui_2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        StepRow(stepIcon = R.drawable.ic_numeric_1_circle_outline_24) {
            Text(stringResource(R.string.terminal_tutorial_1), style = MaterialTheme.typography.bodyLarge)
            Text(
                rememberHtmlText(R.string.terminal_tutorial_1_description, SH_MONO, DEX_MONO),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onExportFiles) {
                Text(stringResource(R.string.terminal_export_files))
            }
        }

        StepRow(stepIcon = R.drawable.ic_numeric_2_circle_outline_24) {
            Text(
                rememberHtmlText(R.string.terminal_tutorial_2, SH_MONO),
                style = MaterialTheme.typography.bodyLarge,
            )
            SelectableCommand(COPY_COMMAND)
            Text(
                rememberHtmlText(R.string.terminal_tutorial_2_description, SH_MONO, SH_MONO, ".bashrc"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StepRow(stepIcon = R.drawable.ic_numeric_3_circle_outline_24) {
            Text(stringResource(R.string.terminal_tutorial_3), style = MaterialTheme.typography.bodyLarge)
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
        Icon(
            painterResource(stepIcon),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .padding(4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Icon(
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
        Text(
            command,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rememberMiui(): Boolean =
    remember { DeviceCompatibility.isMiui() }
