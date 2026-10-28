package moe.shizuku.manager.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.snackbar.Snackbar
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.AppSnackbarResult
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.uikit.preference.SettingsChoiceRow
import io.github.magisk317.uikit.preference.StateSwitchItem
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTextField
import io.github.magisk317.uikit.text.AppText
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings.Keys.KEY_TCP_MODE
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.receiver.NotifCancelReceiver
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.core.util.ResourceUtils
import rikka.material.app.LocaleDelegate
import rikka.shizuku.manager.ShizukuLocales

/**
 * Compose replacement for SettingsFragment.
 *
 * Every preference the fragment carried is here, with the same storage keys,
 * visibility rules and side effects. What moved is the presentation: ui-kit's
 * SectionCard / StateSwitchItem / SettingsChoiceRow / AppArrowItem instead of
 * androidx.preference, and [SettingsRepository] instead of the preference
 * framework's implicit write-back.
 */
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onBugReport: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snapshot by repository.snapshot.collectAsState()

    val tlsSupported = remember { EnvironmentUtils.isTlsSupported() }
    val isTelevision = remember { EnvironmentUtils.isTelevision() }
    val startOnBootSupported = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || isTelevision || EnvironmentUtils.isRooted()
    }
    val showLegacyPairing = remember { !isTelevision }

    // A TCP change only takes effect on the next start, so the fragment marked
    // those rows with a restart icon while the running server still used the old
    // value. Keep that signal.
    var tcpRestartPending by remember { mutableStateOf(repository.needsTcpModeRestart(snapshot.tcpMode)) }
    var tcpPortRestartPending by remember {
        mutableStateOf(repository.needsTcpPortRestart(snapshot.tcpPort.toIntOrNull()))
    }

    var showRestartDialog by remember { mutableStateOf(false) }
    var showStopTcpDialog by remember { mutableStateOf(false) }
    var showStartOnBootBugDialog by remember { mutableStateOf(false) }
    var showTcpPortDialog by remember { mutableStateOf(false) }
    var tcpPortDraft by remember { mutableStateOf("") }
    var pendingTcpPort by remember { mutableStateOf<Int?>(null) }

    val snackbarHostState = remember { AppSnackbarHostState() }
    val batteryContinuation = remember { AtomicReference<CancellableContinuation<Boolean>?>(null) }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val accepted = SettingsHelper.isIgnoringBatteryOptimizations(context)
        batteryContinuation.getAndSet(null)?.resumeIfActive(accepted)
    }

    val stateListener: (ShizukuStateMachine.State) -> Unit = {
        tcpRestartPending = repository.needsTcpModeRestart(snapshot.tcpMode)
        tcpPortRestartPending = repository.needsTcpPortRestart(snapshot.tcpPort.toIntOrNull())
    }
    DisposableEffect(Unit) {
        ShizukuStateMachine.addListener(stateListener)
        onDispose { ShizukuStateMachine.removeListener(stateListener) }
    }

    suspend fun maybeToggleBatterySensitiveSetting(newValue: Boolean): Boolean {
        if (!newValue || SettingsHelper.isIgnoringBatteryOptimizations(context) || isTelevision) {
            return true
        }
        val outcome = CompletableDeferred<Boolean>()
        scope.launch {
            val result = snackbarHostState.showLatestSnackbar(
                message = context.getString(R.string.snackbar_battery_optimization_settings),
                actionLabel = context.getString(R.string.snackbar_action_fix),
                duration = AppSnackbarDuration.Indefinite,
            )
            if (result == AppSnackbarResult.ActionPerformed) {
                SettingsHelper.requestIgnoreBatteryOptimizations(context, batteryLauncher)
            }
            outcome.complete(result == AppSnackbarResult.ActionPerformed)
        }
        return outcome.await()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            title = stringResource(R.string.settings_behavior),
            accordionMode = false,
            sectionExpanded = true,
            onExpandedChange = {},
        ) {
            StateSwitchItem(
                title = stringResource(R.string.settings_start_on_boot),
                summary = if (startOnBootSupported) "" else stringResource(R.string.settings_start_on_boot_summary),
                checked = snapshot.startOnBoot,
                enabled = startOnBootSupported,
                onCheckedChange = { newValue ->
                    if (newValue && !isTelevision && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        showStartOnBootBugDialog = true
                    } else {
                        scope.launch {
                            if (maybeToggleBatterySensitiveSetting(newValue)) {
                                repository.setStartOnBoot(newValue)
                            }
                        }
                    }
                },
            )
            StateSwitchItem(
                title = stringResource(R.string.settings_watchdog),
                summary = "",
                checked = snapshot.watchdog,
                onCheckedChange = { newValue ->
                    scope.launch {
                        if (maybeToggleBatterySensitiveSetting(newValue)) {
                            repository.setWatchdog(newValue)
                        }
                    }
                },
            )
            if (tlsSupported) {
                StateSwitchItem(
                    title = stringResource(R.string.settings_tcp_mode),
                    summary = stringResource(R.string.settings_tcp_mode_summary),
                    checked = snapshot.tcpMode,
                    onCheckedChange = { newValue ->
                        when {
                            !newValue && !ShizukuStateMachine.isRunning() && repository.needsTcpModeRestart(newValue) ->
                                showStopTcpDialog = true
                            ShizukuStateMachine.isRunning() && repository.needsTcpModeRestart(newValue) ->
                                showRestartDialog = true
                            else -> repository.setTcpMode(newValue)
                        }
                    },
                )
                if (snapshot.tcpMode) {
                    AppArrowItem(
                        title = stringResource(R.string.settings_tcp_port),
                        summary = snapshot.tcpPort.ifEmpty { stringResource(R.string.settings_tcp_port_default) },
                        onClick = {
                            tcpPortDraft = snapshot.tcpPort
                            showTcpPortDialog = true
                        },
                    )
                }
            }
            StateSwitchItem(
                title = stringResource(R.string.settings_auto_disable_usb_debugging),
                summary = "",
                checked = snapshot.autoDisableUsbDebugging,
                onCheckedChange = { repository.setAutoDisableUsbDebugging(it) },
            )
        }

        SectionCard(
            title = stringResource(R.string.settings_language),
            accordionMode = false,
            sectionExpanded = true,
            onExpandedChange = {},
        ) {
            val localeEntries = remember(snapshot.languageTag) { repository.buildLocaleEntries() }
            SettingsChoiceRow(
                title = stringResource(R.string.settings_language),
                summary = localeEntries.summary,
                options = localeEntries.options,
                selectedIndex = localeEntries.selectedIndex,
                onSelect = { index ->
                    val tag = ShizukuLocales.LOCALES[index]
                    repository.setLanguageTag(tag)
                    LocaleDelegate.defaultLocale =
                        if ("SYSTEM" == tag) LocaleDelegate.systemLocale else Locale.forLanguageTag(tag)
                    activity?.recreate()
                },
            )
            AppArrowItem(
                title = stringResource(R.string.settings_translation),
                summary = stringResource(R.string.settings_translation_summary, stringResource(R.string.app_name)),
                onClick = {
                    CustomTabsHelper.launchUrlOrCopy(context, context.getString(R.string.translation_url))
                },
            )
            val contributors = remember { context.getString(R.string.translation_contributors).toHtml().toString() }
            if (contributors.isNotBlank()) {
                AppArrowItem(
                    title = stringResource(R.string.settings_translation_contributors),
                    summary = contributors,
                    onClick = {},
                )
            }
        }

        SectionCard(
            title = stringResource(R.string.settings_user_interface),
            accordionMode = false,
            sectionExpanded = true,
            onExpandedChange = {},
        ) {
            val styleOptions = remember {
                listOf(
                    context.getString(R.string.settings_ui_kit_style_expressive),
                    context.getString(R.string.settings_ui_kit_style_miuix),
                )
            }
            val styleValues = remember { listOf(0, 1) }
            val nightOptions = remember { context.resources.getStringArray(R.array.night_mode).toList() }
            val nightValues = remember { context.resources.getIntArray(R.array.night_mode_value).toList() }
            SettingsChoiceRow(
                title = stringResource(R.string.settings_ui_kit_style),
                summary = styleOptions[styleValues.indexOf(snapshot.uiKitStyle).coerceAtLeast(0)],
                options = styleOptions,
                selectedIndex = styleValues.indexOf(snapshot.uiKitStyle).coerceAtLeast(0),
                onSelect = { index ->
                    val style = styleValues[index]
                    if (style != snapshot.uiKitStyle) {
                        repository.setUiKitStyle(style)
                        activity?.recreate()
                    }
                },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.dark_theme),
                summary = nightOptions.getOrElse(nightValues.indexOf(snapshot.nightMode)) { "" },
                options = nightOptions,
                selectedIndex = nightValues.indexOf(snapshot.nightMode).coerceAtLeast(0),
                onSelect = { index ->
                    val value = nightValues[index]
                    if (snapshot.nightMode != value) {
                        repository.setNightMode(value)
                        AppCompatDelegate.setDefaultNightMode(value)
                        activity?.recreate()
                    }
                },
            )
            if (snapshot.nightMode != AppCompatDelegate.MODE_NIGHT_NO) {
                StateSwitchItem(
                    title = stringResource(R.string.settings_black_night_theme),
                    summary = "",
                    checked = snapshot.blackNightTheme,
                    onCheckedChange = { newValue ->
                        repository.setBlackNightTheme(newValue)
                        if (ResourceUtils.isNightMode(context.resources.configuration)) {
                            activity?.recreate()
                        }
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                StateSwitchItem(
                    title = stringResource(R.string.settings_use_system_color),
                    summary = "",
                    checked = snapshot.useSystemColor,
                    onCheckedChange = { newValue ->
                        if (ThemeHelper.isUsingSystemColor() != newValue) {
                            repository.setUseSystemColor(newValue)
                            activity?.recreate()
                        }
                    },
                )
            }
        }

        SectionCard(
            title = stringResource(R.string.settings_support),
            accordionMode = false,
            sectionExpanded = true,
            onExpandedChange = {},
        ) {
            val updateOptions = remember { context.resources.getStringArray(R.array.update_mode).toList() }
            val updateValues = remember { context.resources.getIntArray(R.array.update_mode_value).toList() }
            SettingsChoiceRow(
                title = stringResource(R.string.check_for_updates),
                summary = updateOptions.getOrElse(updateValues.indexOf(snapshot.updateMode)) { "" },
                options = updateOptions,
                selectedIndex = updateValues.indexOf(snapshot.updateMode).coerceAtLeast(0),
                onSelect = { index -> repository.setUpdateMode(updateValues[index]) },
            )
            AppArrowItem(
                title = stringResource(R.string.settings_help),
                summary = "",
                onClick = { CustomTabsHelper.launchUrlOrCopy(context, context.getString(R.string.help_url)) },
            )
            AppArrowItem(
                title = stringResource(R.string.settings_report_bug),
                summary = "",
                onClick = onBugReport,
            )
        }

        if (showLegacyPairing) {
            SectionCard(
                title = stringResource(R.string.settings_advanced),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                StateSwitchItem(
                    title = stringResource(R.string.settings_legacy_pairing),
                    summary = "",
                    checked = snapshot.legacyPairing,
                    onCheckedChange = { repository.setLegacyPairing(it) },
                )
            }
        }
    }

    if (showRestartDialog) {
        AppAlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { AppText(stringResource(R.string.settings_restart_dialog_title)) },
            text = {
                AppText(
                    stringResource(R.string.settings_restart_dialog_message) + " " +
                        stringResource(R.string.settings_restart_dialog_message_wifi_required)
                )
            },
            confirmButton = {
                AppTextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        showRestartDialog = false
                        pendingTcpPort?.let { repository.setTcpPort(it) }
                        pendingTcpPort = null
                        ShizukuReceiverStarter.start(context, true)
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = {
                        showRestartDialog = false
                        pendingTcpPort = null
                    },
                )
            },
        )
    }

    if (showStopTcpDialog) {
        AppAlertDialog(
            onDismissRequest = { showStopTcpDialog = false },
            title = { AppText(stringResource(android.R.string.dialog_alert_title)) },
            text = { AppText(stringResource(R.string.settings_tcp_mode_dialog_close_port)) },
            confirmButton = {
                AppTextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        showStopTcpDialog = false
                        scope.launch {
                            AdbStarter.stopTcp(context, EnvironmentUtils.getAdbTcpPort())
                            if (EnvironmentUtils.getAdbTcpPort() <= 0) repository.setTcpMode(false)
                        }
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { showStopTcpDialog = false },
                )
            },
        )
    }

    if (showStartOnBootBugDialog) {
        AppAlertDialog(
            onDismissRequest = { showStartOnBootBugDialog = false },
            title = { AppText(stringResource(android.R.string.dialog_alert_title)) },
            text = { AppText(stringResource(R.string.settings_start_on_boot_bug)) },
            confirmButton = {
                AppTextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        showStartOnBootBugDialog = false
                        scope.launch {
                            if (maybeToggleBatterySensitiveSetting(true)) {
                                repository.setStartOnBoot(true)
                            }
                        }
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { showStartOnBootBugDialog = false },
                )
            },
        )
    }

    if (showTcpPortDialog) {
        AppAlertDialog(
            onDismissRequest = { showTcpPortDialog = false },
            title = { AppText(stringResource(R.string.settings_tcp_port)) },
            text = {
                AppTextField(
                    value = tcpPortDraft,
                    onValueChange = { tcpPortDraft = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    placeholderText = stringResource(R.string.settings_tcp_port_hint),
                )
            },
            confirmButton = {
                AppTextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        showTcpPortDialog = false
                        val port = tcpPortDraft.toIntOrNull()
                        when {
                            port != null && port !in 1..65535 -> scope.launch {
                                snackbarHostState.showLatestSnackbar(
                                    context.getString(R.string.snackbar_invalid_port),
                                )
                            }
                            ShizukuStateMachine.isRunning() && repository.needsTcpPortRestart(port ?: 5555) -> {
                                pendingTcpPort = port
                                showRestartDialog = true
                            }
                            else -> {
                                repository.setTcpPort(port)
                                context.sendBroadcast(Intent(context, NotifCancelReceiver::class.java))
                            }
                        }
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { showTcpPortDialog = false },
                )
            },
        )
    }
}

private fun CancellableContinuation<Boolean>.resumeIfActive(value: Boolean) {
    if (isActive) resumeWith(Result.success(value))
}
