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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import rikka.compatibility.DeviceCompatibility
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.surface.AppPrimaryButton

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
                container = appColor(AppColorRole.SecondaryContainer),
                contentColor = appColor(AppColorRole.OnSecondaryContainer),
                iconRes = R.drawable.ic_outline_notifications_active_24,
            ) {
                AppText(
                    stringResource(R.string.adb_pairing_tutorial_content_notification),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnSecondaryContainer),
                )
            }
        } else {
            TutorialCard(
                container = appColor(AppColorRole.ErrorContainer),
                contentColor = appColor(AppColorRole.OnErrorContainer),
                iconRes = R.drawable.ic_outline_info_24,
            ) {
                AppText(
                    stringResource(R.string.adb_pairing_tutorial_content_notification_blocked),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnErrorContainer),
                )
                AppPrimaryButton(
                    onClick = onNotificationSettings,
                    containerColor = appColor(AppColorRole.Error),
                    contentColor = appColor(AppColorRole.OnError),
                ) {
                    AppIcon(
                        painterResource(R.drawable.ic_outline_open_in_new_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    AppText(
                        stringResource(R.string.notification_settings),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        TutorialCard(
            container = appColor(AppColorRole.SecondaryContainer),
            contentColor = appColor(AppColorRole.OnSecondaryContainer),
            iconRes = R.drawable.ic_help_outline_24dp,
        ) {
            AppText(
                stringResource(R.string.adb_pairing_tutorial_content_network),
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSecondaryContainer),
            )
            AppText(
                stringResource(R.string.adb_pairing_tutorial_content_network_limation_not_foreground),
                role = AppTextRole.Body,
                fontWeight = FontWeight.Medium,
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
                    stringResource(R.string.adb_pairing_tutorial_content_miui),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnErrorContainer),
                )
                AppText(
                    stringResource(R.string.adb_pairing_tutorial_content_miui_2),
                    role = AppTextRole.Body,
                    fontWeight = FontWeight.Medium,
                    color = appColor(AppColorRole.OnErrorContainer),
                )
            }
        }

        if (notificationEnabled) {
            StepRow(stepIcon = R.drawable.ic_numeric_1_circle_outline_24) {
                AppText(
                    stringResource(R.string.adb_pairing_tutorial_content_steps),
                    role = AppTextRole.BodyLarge,
                )
                AppPrimaryButton(onClick = onDeveloperOptions) {
                    AppIcon(
                        painterResource(R.drawable.ic_outline_open_in_new_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    AppText(
                        stringResource(R.string.development_settings),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            StepRow(stepIcon = R.drawable.ic_numeric_2_circle_outline_24) {
                AppText(
                    stringResource(R.string.adb_pairing_tutorial_content_enter_pairing_code),
                    role = AppTextRole.BodyLarge,
                )
            }
            StepRow(stepIcon = R.drawable.ic_numeric_3_circle_outline_24) {
                AppText(
                    stringResource(R.string.adb_pairing_tutorial_content_finish),
                    role = AppTextRole.BodyLarge,
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
