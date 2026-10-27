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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
                Text(
                    stringResource(R.string.stealth_warning_compatibility),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(R.string.stealth_warning_compatibility_2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(R.string.stealth_warning_compatibility_3),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            WarningCard {
                Text(
                    stringResource(R.string.stealth_warning_detection),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            InfoCard {
                Text(
                    stringResource(R.string.stealth_info_reconfigure),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.stealth_info_rish),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.stealth_info_intents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            InfoCard {
                Text(
                    stringResource(R.string.stealth_info_update),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        if (showInput) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.stealth_package_name)) },
                    supportingText = {
                        val text = when {
                            packageName.isNotEmpty() && validationError != null -> stringResource(validationError)
                            else -> stringResource(R.string.stealth_package_name_helper_text)
                        }
                        Text(text)
                    },
                    isError = packageName.isNotEmpty() && validationError != null,
                    shape = RoundedCornerShape(12.dp),
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
            ExtendedFloatingActionButton(
                text = { Text(label) },
                icon = { Icon(icon, contentDescription = null) },
                onClick = {
                    if (validationError == null) onAction(fabAction, packageName.ifEmpty { null })
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (showInput) 96.dp else 24.dp),
            )
        } else if (busy) {
            ExtendedFloatingActionButton(
                text = { Text("") },
                icon = { CircularProgressIndicator(Modifier.size(20.dp)) },
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = if (showInput) 96.dp else 24.dp),
            )
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
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
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
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(R.drawable.ic_help_outline_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
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
