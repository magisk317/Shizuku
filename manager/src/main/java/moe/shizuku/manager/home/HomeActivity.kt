package moe.shizuku.manager.home

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.AppSnackbarResult
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppBottomSheet
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppListPopup
import io.github.magisk317.uikit.surface.AppListPopupItem
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.ConfirmActionDialog
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbPairingService
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.app.AppPageScaffold
import moe.shizuku.manager.app.ShizukuTheme
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.home.AdbPairDialogContent
import moe.shizuku.manager.ktx.activityTitle
import moe.shizuku.manager.ktx.rememberHtmlText
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.management.ApplicationManagementActivity
import moe.shizuku.manager.management.AppsViewModel
import moe.shizuku.manager.receiver.NotifCancelReceiver
import moe.shizuku.manager.settings.SettingsActivity
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.stealth.StealthTutorialActivity
import moe.shizuku.manager.utils.AppIconCache
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.SettingsPage
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.UpdateHelper
import rikka.core.content.asActivity
import rikka.core.util.ClipboardUtils
import rikka.html.text.HtmlCompat
import rikka.lifecycle.Status
import rikka.shizuku.Shizuku

abstract class HomeActivity : ComponentActivity() {

    private val homeModel: HomeViewModel by viewModels()
    private val adbPairModel: AdbPairViewModel by viewModels()
    private val appsModel: AppsViewModel by viewModels()

    private var wadbEnableUsbDebugging by mutableStateOf(false)
    private var wadbNotEnabled by mutableStateOf(false)
    private var adbDiscovery by mutableStateOf(false)
    private var discoveredPort by mutableIntStateOf(0)
    private var aboutDialog by mutableStateOf(false)
    private var stopConfirm by mutableStateOf(false)
    private var adbCommandDialog by mutableStateOf(false)
    private var automationSheet by mutableStateOf(false)
    private var exitDialog by mutableStateOf<Pair<String, String>?>(null)
    private var batterySnackbar by mutableStateOf(false)
    private var updateSnackbar by mutableStateOf(false)
    private val snackbarHostState = AppSnackbarHostState()
    private var overflowMenu by mutableStateOf(false)
    private var adbPairing by mutableStateOf(false)

    private val stateListener: (ShizukuStateMachine.State) -> Unit = {
        if (ShizukuStateMachine.isRunning()) {
            checkServerStatus()
            appsModel.load()
        } else if (ShizukuStateMachine.isDead()) {
            checkServerStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val status by homeModel.serviceStatus.asFlow()
                .collectAsStateWithLifecycle(initialValue = null)
            val grantedCount by appsModel.grantedCount.asFlow()
                .collectAsStateWithLifecycle(initialValue = null)

            ShizukuTheme(
                nightMode = ShizukuSettings.getNightMode(),
                useSystemColor = ThemeHelper.isUsingSystemColor(),
            ) {
            AppPageScaffold(
                title = this@HomeActivity.activityTitle(),
                onBack = { this@HomeActivity.finish() },
                snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
                actions = {
                    AppIconButton(
                        onClick = {
                            startActivity(Intent(this@HomeActivity, SettingsActivity::class.java))
                        },
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                    AppIconButton(onClick = { overflowMenu = true }) {
                        AppIcon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_about),
                        )
                    }
                },
            ) { contentPadding, scrollModifier ->
            HomeScreen(
                modifier = Modifier
                    .padding(contentPadding)
                    .then(scrollModifier),
                status = status?.data,
                grantedCount = grantedCount?.data ?: 0,
                suiUrl = Helps.SUI.get(),
                adbHelpUrl = Helps.ADB.get(),
                adbPermissionHelpUrl = Helps.ADB_PERMISSION.get(),
                wadbGuideUrl = Helps.ADB_ANDROID11.get(),
                onManageApps = {
                    startActivity(Intent(this@HomeActivity, ApplicationManagementActivity::class.java))
                },
                onTerminal = {
                    startActivity(Intent(this@HomeActivity, ShellTutorialActivity::class.java))
                },
                onStartRoot = {
                    val intent = Intent(this@HomeActivity, StarterActivity::class.java)
                    intent.putExtra(StarterActivity.EXTRA_IS_ROOT, true)
                    startActivity(intent)
                },
                onStartWirelessAdb = { startViaWirelessAdb() },
                onPair = { onPairClicked() },
                onViewWadbGuide = {
                    CustomTabsHelper.launchUrlOrCopy(this@HomeActivity, Helps.ADB_ANDROID11.get())
                },
                onViewAdbCommand = { showAdbCommandDialog() },
                onAutomationSheet = { showAutomationSheet() },
                onStealth = {
                    startActivity(Intent(this@HomeActivity, StealthTutorialActivity::class.java))
                },
                onLearnMore = {
                    CustomTabsHelper.launchUrlOrCopy(this@HomeActivity, Helps.HOME.get())
                },
                onAdbPermissionHelp = {
                    CustomTabsHelper.launchUrlOrCopy(this@HomeActivity, Helps.ADB_PERMISSION.get())
                },
            )
            }

            if (aboutDialog) {
                val icon = AppIconCache.getOrLoadBitmap(
                    this@HomeActivity,
                    applicationInfo,
                    Process.myUid() / 100000,
                    resources.getDimensionPixelOffset(R.dimen.default_app_icon_size),
                )
                val versionName = packageManager
                    .getPackageInfo(packageName, 0).versionName ?: ""
                AppAlertDialog(
                    onDismissRequest = { aboutDialog = false },
                    text = {
                        AboutDialogContent(
                            icon = icon,
                            versionName = versionName,
                            onClose = { aboutDialog = false },
                            onUpdate = {
                                lifecycleScope.launch {
                                    UpdateHelper.checkAndInstallUpdates()
                                }
                            },
                        )
                    },
                )
            }

            if (stopConfirm) {
                AppAlertDialog(
                    onDismissRequest = { stopConfirm = false },
                    text = { AppText(stringResource(R.string.dialog_stop_message)) },
                    confirmButton = {
                        AppTextButton(
                            text = stringResource(android.R.string.ok),
                            onClick = {
                                stopConfirm = false
                                ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
                                runCatching { Shizuku.exit() }
                                    .onFailure { Log.w(AppConstants.TAG, "Failed to stop Shizuku", it) }
                            },
                        )
                    },
                    dismissButton = {
                        AppTextButton(
                            text = stringResource(android.R.string.cancel),
                            onClick = { stopConfirm = false },
                        )
                    },
                )
            }

            if (adbCommandDialog) {
                val message = rememberHtmlText(
                    R.string.home_adb_dialog_view_command_message,
                    Starter.adbCommand,
                )
                AppAlertDialog(
                    onDismissRequest = { adbCommandDialog = false },
                    title = { AppText(stringResource(R.string.home_adb_button_view_command)) },
                    text = { AppText(message) },
                    confirmButton = {
                        AppTextButton(
                            text = stringResource(R.string.home_adb_dialog_view_command_copy_button),
                            onClick = {
                                adbCommandDialog = false
                                if (ClipboardUtils.put(this@HomeActivity, Starter.adbCommand)) {
                                    Toast.makeText(
                                        this@HomeActivity,
                                        getString(R.string.toast_copied_to_clipboard),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    },
                    dismissButton = {
                        AppTextButton(
                            text = stringResource(R.string.home_adb_dialog_view_command_button_send),
                            onClick = {
                                adbCommandDialog = false
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, Starter.adbCommand)
                                }
                                startActivity(
                                    Intent.createChooser(
                                        intent,
                                        getString(R.string.home_adb_dialog_view_command_button_send),
                                    ),
                                )
                            },
                        )
                    },
                )
            }

            if (automationSheet) {
                AppBottomSheet(
                    show = true,
                    onDismissRequest = { automationSheet = false },
                    title = stringResource(R.string.home_automation_bottom_sheet_intents),
                ) {
                    AutomationSheetContent(
                        packageName = packageName,
                        onDismiss = { automationSheet = false },
                    )
                }
            }

            exitDialog?.let { (title, message) ->
                ConfirmActionDialog(
                    title = title,
                    message = message,
                    confirmText = stringResource(R.string.home_dialog_button_exit),
                    onDismissRequest = { exitDialog = null },
                    onConfirm = {
                        exitDialog = null
                        finishAffinity()
                    },
                )
            }

            if (wadbEnableUsbDebugging) {
                AppAlertDialog(
                    onDismissRequest = { wadbEnableUsbDebugging = false },
                    text = {
                        AppText(
                            stringResource(R.string.dialog_usb_debugging_not_enabled),
                            role = AppTextRole.Body,
                        )
                    },
                    confirmButton = {
                        AppTextButton(
                            text = stringResource(R.string.development_settings),
                            onClick = {
                                wadbEnableUsbDebugging = false
                                SettingsPage.Developer.HighlightUsbDebugging.launch(this@HomeActivity)
                            },
                        )
                    },
                    dismissButton = {
                        AppTextButton(
                            text = stringResource(android.R.string.cancel),
                            onClick = { wadbEnableUsbDebugging = false },
                        )
                    },
                )
            }

            if (wadbNotEnabled) {
                AppAlertDialog(
                    onDismissRequest = { wadbNotEnabled = false },
                    text = {
                        AppText(
                            stringResource(R.string.dialog_wireless_adb_not_enabled),
                            role = AppTextRole.Body,
                        )
                    },
                    confirmButton = {
                        AppTextButton(
                            text = stringResource(android.R.string.ok),
                            onClick = { wadbNotEnabled = false },
                        )
                    },
                )
            }

            if (overflowMenu) {
                AppListPopup(
                    show = true,
                    onDismissRequest = { overflowMenu = false },
                    items = listOf(
                        AppListPopupItem(
                            label = stringResource(R.string.action_stop),
                            onClick = {
                                overflowMenu = false
                                if (ShizukuStateMachine.isRunning()) stopConfirm = true
                            },
                        ),
                        AppListPopupItem(
                            label = stringResource(R.string.action_about),
                            onClick = {
                                overflowMenu = false
                                aboutDialog = true
                            },
                        ),
                    ),
                )
            }

            AppSnackbarHost(hostState = snackbarHostState)

            LaunchedEffect(batterySnackbar) {
                if (!batterySnackbar) return@LaunchedEffect
                val result = snackbarHostState.showLatestSnackbar(
                    message = getString(R.string.snackbar_battery_optimization_home),
                    actionLabel = getString(R.string.snackbar_action_fix),
                    duration = AppSnackbarDuration.Indefinite,
                )
                if (result == AppSnackbarResult.ActionPerformed) {
                    SettingsHelper.requestIgnoreBatteryOptimizations(this@HomeActivity, null)
                }
                batterySnackbar = false
            }

            LaunchedEffect(updateSnackbar) {
                if (!updateSnackbar) return@LaunchedEffect
                val result = snackbarHostState.showLatestSnackbar(
                    message = getString(R.string.snackbar_update_available),
                    actionLabel = getString(R.string.snackbar_action_update),
                    duration = AppSnackbarDuration.Indefinite,
                )
                if (result == AppSnackbarResult.ActionPerformed) {
                    lifecycleScope.launch { UpdateHelper.update() }
                }
                UpdateHelper.updateLastPromptedVersion()
                updateSnackbar = false
            }

            if (adbPairing) {
                val multiWindow = isInMultiWindowMode ||
                    (window.decorView.display?.displayId ?: -1) > 0
                AppAlertDialog(
                    onDismissRequest = { adbPairing = false },
                    text = {
                        AdbPairDialogContent(
                            viewModel = adbPairModel,
                            multiWindow = multiWindow,
                            onDismiss = { adbPairing = false },
                            onOpenDevelopmentSettings = {
                                SettingsHelper.launchOrHighlightWirelessDebugging(this@HomeActivity)
                            },
                        )
                    },
                )
            }

            if (adbDiscovery) {
                AdbDiscoveryDialog(onDismiss = { adbDiscovery = false })
            }

            LaunchedEffect(discoveredPort) {
                if (discoveredPort !in 1..65535) return@LaunchedEffect
                val port = discoveredPort
                discoveredPort = 0
                adbDiscovery = false
                startActivity(
                    Intent(this@HomeActivity, StarterActivity::class.java).apply {
                        putExtra(StarterActivity.EXTRA_PORT, port)
                    },
                )
            }
            }
        }

        homeModel.serviceStatus.observe(this) {
            if (it.status == Status.SUCCESS) {
                val status = homeModel.serviceStatus.value?.data ?: return@observe
                ShizukuSettings.setLastLaunchMode(if (status.uid == 0) ShizukuSettings.LaunchMethod.ROOT else ShizukuSettings.LaunchMethod.ADB)
            }
        }

        homeModel.shouldShowRebootDialog.observe(this) { shouldShow ->
            if (shouldShow) showExitDialog(
                getString(R.string.home_dialog_reboot_required_title),
                getString(R.string.home_dialog_reboot_required_message)
            )
        }

        homeModel.shouldShowUninstallDialog.observe(this) { shouldShow ->
            if (shouldShow) showExitDialog(
                getString(R.string.home_dialog_duplicate_app_detected_title),
                getString(R.string.home_dialog_duplicate_app_detected_message)
            )
        }

        homeModel.shouldShowBatteryOptimizationSnackbar.observe(this) { shouldShow ->
            if (shouldShow) batterySnackbar = true
        }
        homeModel.checkBatteryOptimization()

        lifecycleScope.launch {
            if (UpdateHelper.isCheckForUpdatesEnabled() && UpdateHelper.isNewUpdateAvailable()) {
                updateSnackbar = true
            }
        }

        ShizukuStateMachine.addListener(stateListener)
    }

    // activity 1.13 types the parameter as non-null, so the override cannot widen it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val showDialog = intent.getBooleanExtra(HomeActivity.EXTRA_SHOW_PAIRING_DIALOG, false)
        if (showDialog) showAccessibilityDialog()

        val startWadb = intent.getBooleanExtra(HomeActivity.EXTRA_START_SERVICE_VIA_WADB, false)
        if (startWadb) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(AdbPairingService.NOTIFICATION_ID)
            startViaWirelessAdb()
        }
    }

    override fun onResume() {
        super.onResume()
        checkServerStatus()
        appsModel.load()
    }

    override fun onPause() {
        super.onPause()
    }

    private fun showExitDialog(title: String, message: String) {
        exitDialog = title to message
    }
    private fun checkServerStatus() {
        homeModel.reload()
    }

    private fun startViaWirelessAdb() {
        if (ShizukuStateMachine.get() == ShizukuStateMachine.State.STARTING) {
            Toast.makeText(this, getString(R.string.toast_shizuku_already_starting), Toast.LENGTH_SHORT).show()
            return
        }

        sendBroadcast(Intent(this, NotifCancelReceiver::class.java))

        val cr = contentResolver
        if (checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
        }

        val adbEnabled = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0)
        if (adbEnabled == 0) {
            wadbEnableUsbDebugging = true
            return
        }

        val tcpPort = EnvironmentUtils.getAdbTcpPort()
        val tcpMode = ShizukuSettings.getTcpMode()

        // If ADB is NOT listening to a TCP port and the device doesn't support TLS, inform the user
        if (tcpPort <= 0 && !EnvironmentUtils.isTlsSupported()) {
            wadbNotEnabled = true
        // If ADB IS NOT listening to a TCP port but the device supports TLS, start mDns discovery
        } else if (tcpPort <= 0) {
            adbDiscovery = true
        // If ADB IS listening to a TCP port but the user wants to close it and use TLS instead, close the TCP port and start mDns discovery
        } else if (!tcpMode) {
            lifecycleScope.launch(Dispatchers.IO) {
                AdbStarter.stopTcp(this@HomeActivity, tcpPort)
            }
            adbDiscovery = true
        // Otherwise ADB IS listening to a TCP port and the user wants to keep it open. Start Shizuku via TCP
        } else {
            val intent = Intent(this, StarterActivity::class.java).apply {
                putExtra(StarterActivity.EXTRA_PORT, tcpPort)
            }
            startActivity(intent)
        }
    }

    private fun onPairClicked() {
        if (EnvironmentUtils.isTelevision()) {
            showAccessibilityDialog()
        } else if (display.displayId > 0 || ShizukuSettings.getLegacyPairing()) {
            // Running in a multi-display environment (e.g., Windows Subsystem for Android),
            // pairing dialog can be displayed simultaneously with Shizuku.
            // Input from notification is harder to use under this situation.
            adbPairing = true
        } else {
            startActivity(Intent(this, AdbPairingTutorialActivity::class.java))
        }
    }

    private fun showAdbCommandDialog() {
        adbCommandDialog = true
    }
    private fun showAutomationSheet() {
        automationSheet = true
    }
    override fun onDestroy() {
        ShizukuStateMachine.removeListener(stateListener)
        super.onDestroy()
    }


    @Composable
    private fun AdbDiscoveryDialog(onDismiss: () -> Unit) {
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            if (checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
            }
            val mdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { discoveredPort = it.second }
            mdns.start()
            try {
                awaitCancellation()
            } finally {
                mdns.stop()
            }
        }

        AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { AppText(stringResource(R.string.dialog_adb_discovery)) },
            text = {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    AppText(
                        stringResource(R.string.dialog_adb_discovery_message),
                        role = AppTextRole.Body,
                    )
                    Spacer(Modifier.height(12.dp))
                    AppText(
                        stringResource(R.string.dialog_adb_discovery_message_toggle_wireless_debugging),
                        role = AppTextRole.Body,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            confirmButton = {
                AppTextButton(
                    text = stringResource(R.string.development_settings),
                    onClick = {
                        SettingsPage.Developer.HighlightWirelessDebugging.launch(context)
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                )
            },
        )
    }

    companion object {
        const val EXTRA_SHOW_PAIRING_DIALOG = "show_pairing_dialog"
        const val EXTRA_START_SERVICE_VIA_WADB = "start_service_via_wadb"
    }

}
