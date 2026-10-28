package moe.shizuku.manager.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rikka.html.text.HtmlCompat
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppListItem
import moe.shizuku.manager.R
import moe.shizuku.manager.ktx.rememberHtmlText
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.surface.AppPrimaryButton

/**
 * Compose replacement for the home card cluster. The card set and visibility
 * rules are the ones HomeAdapter.updateData used to derive; presentation moved
 * to ui-kit's status hero card and list rows. Behaviour (dialogs, activities,
 * the automation sheet) stays in HomeActivity and arrives as callbacks.
 */
@Composable
fun HomeScreen(
    status: ServiceStatus?,
    grantedCount: Int,
    suiUrl: String,
    adbHelpUrl: String,
    adbPermissionHelpUrl: String,
    wadbGuideUrl: String,
    onManageApps: () -> Unit,
    onTerminal: () -> Unit,
    onStartRoot: () -> Unit,
    onStartWirelessAdb: () -> Unit,
    onPair: () -> Unit,
    onViewWadbGuide: () -> Unit,
    onViewAdbCommand: () -> Unit,
    onAutomationSheet: () -> Unit,
    onStealth: () -> Unit,
    onLearnMore: () -> Unit,
    onAdbPermissionHelp: () -> Unit,
) {
    if (status == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    val running = status.isRunning
    val adbPermission = status.permission
    val isPrimaryUser = remember { UserHandleCompat.myUserId() == 0 }
    val rooted = remember { EnvironmentUtils.isRooted() }
    val isTelevision = remember { EnvironmentUtils.isTelevision() }
    val tlsSupported = remember { EnvironmentUtils.isTlsSupported() }
    val tcpPort = remember(status) { EnvironmentUtils.getAdbTcpPort() }
    val showWadb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || isTelevision || tcpPort > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(status)

        if (adbPermission) {
            ManageAppsCard(running, grantedCount, onManageApps)
            TerminalCard(running, onTerminal)
        }

        if (running && !adbPermission) {
            AdbPermissionLimitedCard(onAdbPermissionHelp, adbPermissionHelpUrl)
        }

        if (isPrimaryUser) {
            if (rooted) {
                StartRootCard(running && status.uid == 0, suiUrl, onStartRoot)
            }
            if (showWadb) {
                StartWirelessAdbCard(tlsSupported, wadbGuideUrl, onPair, onStartWirelessAdb, onViewWadbGuide)
            }
            StartAdbCard(adbHelpUrl, onViewAdbCommand)
        }

        AutomationCard(isTelevision, rooted, onAutomationSheet)
        StealthCard(onStealth)
        LearnMoreCard(onLearnMore)
    }
}

@Composable
private fun StatusCard(status: ServiceStatus) {
    val appName = stringResource(R.string.app_name)
    val running = status.isRunning
    val user = if (status.uid == 0) "root" else "adb"

    var badge = ""
    var summary: String? = null
    if (running) {
        badge = stringResource(R.string.home_status_service_version, user, "${status.apiVersion}.${status.patchVersion}")
        val stale = status.apiVersion != Shizuku.getLatestServiceVersion() ||
            status.patchVersion != ShizukuApiConstants.SERVER_PATCH_VERSION
        if (stale) {
            summary = HtmlCompat.fromHtml(
                stringResource(
                    R.string.home_status_service_version_update,
                    user,
                    "${status.apiVersion}.${status.patchVersion}",
                    "${Shizuku.getLatestServiceVersion()}.${ShizukuApiConstants.SERVER_PATCH_VERSION}",
                ),
                HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE,
            ).toString()
        }
    }

    val surface = appColor(AppColorRole.Background)
    val dark = surface.luminance() < 0.5f
    val accent = if (running) STATUS_RUNNING_ACCENT else STATUS_WARNING_ACCENT
    val container = accent
        .copy(alpha = if (dark) 0.34f else 0.14f)
        .compositeOver(
            if (dark) appColor(AppColorRole.SurfaceContainerHigh) else appColor(AppColorRole.SurfaceContainerLow),
        )

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        color = container,
        contentColor = appColor(AppColorRole.OnSurface),
    ) {
        Box {
            // Oversized corner mark, kept from the KernelSU-derived card the kit
            // shipped, offset past the bounds so it is clipped into the corner.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                AppIcon(
                    modifier = Modifier.size(110.dp),
                    imageVector = if (running) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
                    tint = accent,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppText(
                    text = stringResource(
                        if (running) R.string.home_status_service_is_running else R.string.home_status_service_not_running,
                        appName,
                    ),
                    role = AppTextRole.Title,
                    fontWeight = FontWeight.SemiBold,
                )
                AppText(
                    text = badge,
                    role = AppTextRole.BodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (!summary.isNullOrBlank()) {
                    AppText(
                        text = summary,
                        role = AppTextRole.Body,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                    )
                }
            }
        }
    }
}

// Semantic status accents. Only the tint is fixed; the container is a low-alpha
// wash of it over a themed surface, so light and dark both stay legible and the
// text keeps reading the scheme's onSurface.
private val STATUS_RUNNING_ACCENT = Color(0xFF36D167)
private val STATUS_WARNING_ACCENT = Color(0xFFF5A623)

@Composable
private fun ManageAppsCard(running: Boolean, grantedCount: Int, onManageApps: () -> Unit) {
    val title = if (running) {
        pluralStringResource(R.plurals.home_app_management_authorized_apps_count, grantedCount, grantedCount)
    } else {
        stringResource(R.string.home_app_management_title)
    }
    val summary = if (running) {
        stringResource(R.string.home_app_management_view_authorized_apps)
    } else {
        stringResource(R.string.home_status_service_not_running, stringResource(R.string.app_name))
    }
    AppCard(Modifier.fillMaxWidth()) {
        AppListItem(
            title = title,
            summary = summary,
            enabled = running,
            leadingContent = { CardIcon(R.drawable.ic_settings_outline_24dp) },
            onClick = onManageApps,
        )
    }
}

@Composable
private fun TerminalCard(running: Boolean, onTerminal: () -> Unit) {
    val summary = if (running) {
        stringResource(R.string.home_terminal_description)
    } else {
        stringResource(R.string.home_status_service_not_running, stringResource(R.string.app_name))
    }
    AppCard(Modifier.fillMaxWidth()) {
        AppListItem(
            title = stringResource(R.string.home_terminal_title),
            summary = summary,
            enabled = running,
            leadingContent = { CardIcon(R.drawable.ic_terminal_24) },
            onClick = onTerminal,
        )
    }
}

@Composable
private fun AdbPermissionLimitedCard(onAdbPermissionHelp: () -> Unit, helpUrl: String) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardIcon(R.drawable.ic_warning_24, container = appColor(AppColorRole.ErrorContainer))
                AppText(
                    stringResource(R.string.home_adb_is_limited_title),
                    role = AppTextRole.Subtitle,
                )
            }
            AppText(
                stringResource(R.string.home_adb_is_limited_description),
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            CardButton(R.string.home_adb_button_view_help, R.drawable.ic_outline_open_in_new_24, onAdbPermissionHelp)
        }
    }
}

@Composable
private fun StartRootCard(restart: Boolean, suiUrl: String, onStartRoot: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardIcon(R.drawable.ic_root_24dp)
                AppText(stringResource(R.string.home_root_title), role = AppTextRole.Subtitle)
            }
            AppText(
                rememberHtmlText(R.string.home_root_description, suiUrl, "Sui"),
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            if (restart) {
                CardButton(R.string.home_root_button_restart, R.drawable.ic_server_restart, onStartRoot)
            } else {
                CardButton(R.string.home_root_button_start, R.drawable.ic_server_start_24dp, onStartRoot)
            }
        }
    }
}

@Composable
private fun StartWirelessAdbCard(
    tlsSupported: Boolean,
    guideUrl: String,
    onPair: () -> Unit,
    onStartWirelessAdb: () -> Unit,
    onViewWadbGuide: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardIcon(R.drawable.ic_wadb_24)
                AppText(stringResource(R.string.home_wireless_adb_title), role = AppTextRole.Subtitle)
            }
            AppText(
                rememberHtmlText(
                    if (tlsSupported) R.string.home_wireless_adb_description
                    else R.string.home_wireless_adb_description_pre_11
                ),
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tlsSupported) {
                    CardButton(R.string.home_wireless_adb_view_guide_button, R.drawable.ic_outline_open_in_new_24, onViewWadbGuide)
                    CardButton(R.string.adb_pairing, R.drawable.ic_baseline_link_24, onPair)
                }
                CardButton(R.string.home_root_button_start, R.drawable.ic_server_start_24dp, onStartWirelessAdb)
            }
        }
    }
}

@Composable
private fun StartAdbCard(helpUrl: String, onViewAdbCommand: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardIcon(R.drawable.ic_adb_24dp)
                AppText(stringResource(R.string.home_adb_title), role = AppTextRole.Subtitle)
            }
            AppText(
                rememberHtmlText(R.string.home_adb_description, helpUrl),
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            CardButton(R.string.home_adb_button_view_command, R.drawable.ic_code_24dp, onViewAdbCommand)
        }
    }
}

@Composable
private fun AutomationCard(
    isTelevision: Boolean,
    rooted: Boolean,
    onAutomationSheet: () -> Unit,
) {
    val restricted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !isTelevision && !rooted
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardIcon(R.drawable.ic_integration_instructions_24)
                AppText(stringResource(R.string.home_automation_title), role = AppTextRole.Subtitle)
            }
            AppText(
                stringResource(R.string.home_automation_description),
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            if (restricted) {
                AppText(
                    rememberHtmlText(R.string.home_automation_description_device_restriction, "adb tcpip 5555"),
                    role = AppTextRole.Body,
                    color = appColor(AppColorRole.OnSurfaceVariant),
                )
            }
            CardButton(R.string.home_automation_button_view_intents, R.drawable.ic_code_24dp, onAutomationSheet)
        }
    }
}

@Composable
private fun StealthCard(onStealth: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        AppListItem(
            title = stringResource(R.string.home_stealth_title),
            summary = stringResource(R.string.home_stealth_description),
            leadingContent = { CardIcon(R.drawable.ic_visibility_off_outline_24) },
            onClick = onStealth,
        )
    }
}

@Composable
private fun LearnMoreCard(onLearnMore: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        AppListItem(
            title = stringResource(R.string.home_learn_more_title),
            summary = stringResource(R.string.home_learn_more_description),
            leadingContent = { CardIcon(R.drawable.ic_learn_more_24dp) },
            onClick = onLearnMore,
        )
    }
}

/** The circular icon chip every View-based home card carried. */
@Composable
private fun CardIcon(res: Int, container: Color = appColor(AppColorRole.PrimaryContainer)) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            painter = painterResource(res),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = appColor(AppColorRole.OnPrimaryContainer),
        )
    }
}

@Composable
private fun CardButton(textRes: Int, iconRes: Int, onClick: () -> Unit) {
    AppPrimaryButton(onClick = onClick) {
        AppIcon(painterResource(iconRes), contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        AppText(stringResource(textRes))
    }
}


