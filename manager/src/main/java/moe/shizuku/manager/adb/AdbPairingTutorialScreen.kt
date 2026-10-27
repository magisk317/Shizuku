package moe.shizuku.manager.adb

import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import rikka.compatibility.DeviceCompatibility

/**
 * Compose replacement for the pairing tutorial scroll. Which cards show is
 * driven by the notification permission the activity tracks; the developer
 * options / notification settings targets and the pairing service stay in
 * [AdbPairingTutorialActivity].
 */
@Composable
fun AdbPairingTutorialScreen(
    notificationEnabled: Boolean,
    onDeveloperOptions: () -> Unit,
    onNotificationSettings: () -> Unit,
) {
    val isMiui = remember { DeviceCompatibility.isMiui() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (notificationEnabled) {
            TutorialCard(
                container = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                iconRes = R.drawable.ic_outline_notifications_active_24,
            ) {
                Text(
                    stringResource(R.string.adb_pairing_tutorial_content_notification),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        } else {
            TutorialCard(
                container = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                iconRes = R.drawable.ic_outline_info_24,
            ) {
                Text(
                    stringResource(R.string.adb_pairing_tutorial_content_notification_blocked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                FilledTonalButton(
                    onClick = onNotificationSettings,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_outline_open_in_new_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.notification_settings),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        TutorialCard(
            container = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconRes = R.drawable.ic_help_outline_24dp,
        ) {
            Text(
                stringResource(R.string.adb_pairing_tutorial_content_network),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(R.string.adb_pairing_tutorial_content_network_limation_not_foreground),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
                    stringResource(R.string.adb_pairing_tutorial_content_miui),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(R.string.adb_pairing_tutorial_content_miui_2),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        if (notificationEnabled) {
            StepRow(stepIcon = R.drawable.ic_numeric_1_circle_outline_24) {
                Text(
                    stringResource(R.string.adb_pairing_tutorial_content_steps),
                    style = MaterialTheme.typography.bodyLarge,
                )
                FilledTonalButton(onClick = onDeveloperOptions) {
                    Icon(
                        painterResource(R.drawable.ic_outline_open_in_new_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.development_settings),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            StepRow(stepIcon = R.drawable.ic_numeric_2_circle_outline_24) {
                Text(
                    stringResource(R.string.adb_pairing_tutorial_content_enter_pairing_code),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            StepRow(stepIcon = R.drawable.ic_numeric_3_circle_outline_24) {
                Text(
                    stringResource(R.string.adb_pairing_tutorial_content_finish),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun TutorialCard(
    container: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    iconRes: Int,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
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
