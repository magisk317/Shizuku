package moe.shizuku.manager.home

import android.graphics.Typeface
import android.os.Build
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.text.getSpans
import rikka.html.text.HtmlCompat
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppListItem
import io.github.magisk317.uikit.surface.MiuixStatusCheckCard
import moe.shizuku.manager.R
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants

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

    MiuixStatusCheckCard(
        passed = running,
        title = stringResource(
            if (running) R.string.home_status_service_is_running else R.string.home_status_service_not_running,
            appName,
        ),
        badge = badge,
        summary = summary,
    )
}

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
                CardIcon(R.drawable.ic_warning_24, container = MaterialTheme.colorScheme.errorContainer)
                Text(
                    stringResource(R.string.home_adb_is_limited_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(R.string.home_adb_is_limited_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(stringResource(R.string.home_root_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                rememberHtmlText(R.string.home_root_description, suiUrl, "Sui"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(stringResource(R.string.home_wireless_adb_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                rememberHtmlText(
                    if (tlsSupported) R.string.home_wireless_adb_description
                    else R.string.home_wireless_adb_description_pre_11
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(stringResource(R.string.home_adb_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                rememberHtmlText(R.string.home_adb_description, helpUrl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(stringResource(R.string.home_automation_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.home_automation_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (restricted) {
                Text(
                    rememberHtmlText(R.string.home_automation_description_device_restriction, "adb tcpip 5555"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun CardIcon(res: Int, container: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(res),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CardButton(textRes: Int, iconRes: Int, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, colors = ButtonDefaults.filledTonalButtonColors()) {
        Icon(painterResource(iconRes), contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(textRes))
    }
}

/**
 * The View cards rendered these strings through HtmlCompat so <a href> spans
 * became clickable links. LinkAnnotation gives Compose the same result, with
 * the URL format argument already substituted by the resource lookup.
 */
@Composable
private fun rememberHtmlText(res: Int, vararg formatArgs: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val raw = stringResource(res, *formatArgs)
    return remember(raw, linkColor) { raw.toHtmlAnnotated(linkColor) }
}

private fun String.toHtmlAnnotated(linkColor: Color): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE) as Spanned
    val builder = AnnotatedString.Builder(spanned.toString())
    spanned.getSpans<URLSpan>(0, spanned.length).forEach { span ->
        builder.addLink(
            LinkAnnotation.Url(
                span.url,
                TextLinkStyles(style = SpanStyle(color = linkColor)),
            ),
            spanned.getSpanStart(span),
            spanned.getSpanEnd(span),
        )
    }
    spanned.getSpans<StyleSpan>(0, spanned.length)
        .filter { it.style == Typeface.BOLD }
        .forEach { span ->
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                spanned.getSpanStart(span),
                spanned.getSpanEnd(span),
            )
        }
    return builder.toAnnotatedString()
}
