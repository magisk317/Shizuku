package moe.shizuku.manager.home

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import rikka.core.util.ClipboardUtils
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.UpdateHelper

private const val SOURCE_CODE_URL = "https://github.com/magisk317/Shizuku"

@Composable
fun AboutDialogContent(
    icon: android.graphics.Bitmap?,
    versionName: String,
    onClose: () -> Unit,
    onUpdate: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            icon?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 24.dp),
            ) {
                Text(text = stringResource(R.string.app_name), fontSize = 18.sp)
                Text(text = versionName, fontSize = 14.sp)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_24),
                    contentDescription = stringResource(R.string.about_dialog_button_close),
                )
            }
        }

        Text(
            text = sourceCodeLink(context),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        OutlinedButton(
            onClick = onUpdate,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Icon(painter = painterResource(R.drawable.ic_update_24), contentDescription = null)
            Text(
                text = stringResource(R.string.check_for_updates),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun sourceCodeLink(context: android.content.Context): AnnotatedString {
    val raw = stringResource(R.string.about_view_source_code)
    return remember(raw) {
        buildAnnotatedString {
            val parts = raw.split("%1\$s")
            append(parts[0])
            pushLink(
                LinkAnnotation.Clickable(
                    tag = SOURCE_CODE_URL,
                    linkInteractionListener = {
                        CustomTabsHelper.launchUrlOrCopy(context, SOURCE_CODE_URL)
                    },
                )
            )
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                )
            ) {
                append("GitHub")
            }
            pop()
            append(parts.getOrElse(1) { "" })
        }
    }
}

@Composable
fun AutomationSheetContent(
    packageName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var startChecked by remember { mutableStateOf(true) }
    var authToken by remember { mutableStateOf(ShizukuSettings.getAuthToken()) }
    var action by remember { mutableStateOf("${BuildConfig.APPLICATION_ID}.START") }
    var showRegenerateDialog by remember { mutableStateOf(false) }

    fun copy(value: String) {
        if (
            ClipboardUtils.put(context, value) &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
        ) {
            Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = stringResource(R.string.home_automation_bottom_sheet_intents),
            style = MaterialTheme.typography.titleMedium,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 12.dp)) {
            SegmentedButton(
                selected = startChecked,
                onClick = {
                    startChecked = true
                    action = "${BuildConfig.APPLICATION_ID}.START"
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.start))
            }
            SegmentedButton(
                selected = !startChecked,
                onClick = {
                    startChecked = false
                    action = "${BuildConfig.APPLICATION_ID}.STOP"
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.stop))
            }
        }

        AutomationField(
            label = stringResource(R.string.home_automation_bottom_sheet_label_action),
            value = action,
            onCopy = { copy(action) },
            modifier = Modifier.padding(top = 16.dp),
        )
        AutomationField(
            label = stringResource(R.string.home_automation_bottom_sheet_label_package),
            value = packageName,
            onCopy = { copy(packageName) },
            modifier = Modifier.padding(top = 12.dp),
        )
        AutomationField(
            label = stringResource(R.string.home_automation_bottom_sheet_label_target),
            value = "Broadcast Receiver",
            onCopy = { copy("Broadcast Receiver") },
            modifier = Modifier.padding(top = 12.dp),
        )
        AutomationField(
            label = stringResource(R.string.home_automation_bottom_sheet_label_extras),
            value = authToken,
            onCopy = { copy(authToken) },
            onRegenerate = { showRegenerateDialog = true },
            modifier = Modifier.padding(top = 12.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text(stringResource(R.string.home_automation_regenerate_token)) },
            text = { Text(stringResource(R.string.home_automation_regenerate_token_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRegenerateDialog = false
                    authToken = ShizukuSettings.generateAuthToken()
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AutomationField(
    label: String,
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onCopy) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.toast_copied_to_clipboard),
                )
            }
        },
        leadingIcon = onRegenerate?.let {
            {
                IconButton(onClick = it) {
                    Icon(
                        painter = painterResource(R.drawable.ic_autorenew),
                        contentDescription = stringResource(R.string.home_automation_regenerate_token),
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
