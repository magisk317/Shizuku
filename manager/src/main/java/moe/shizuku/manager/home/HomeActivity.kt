package moe.shizuku.manager.home

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import rikka.html.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbPairingService
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.app.SnackbarHelper
import moe.shizuku.manager.app.ShizukuTheme
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.databinding.AboutDialogBinding
import moe.shizuku.manager.databinding.HomeAutomationBottomSheetBinding
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
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.UpdateHelper
import rikka.core.content.asActivity
import rikka.core.util.ClipboardUtils
import rikka.lifecycle.Status
import rikka.shizuku.Shizuku

abstract class HomeActivity : AppBarActivity() {

    private val homeModel: HomeViewModel by viewModels()
    private val appsModel: AppsViewModel by viewModels()

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

        // The content must carry the scrolling-view behaviour like every View-based
        // child of the CoordinatorLayout root, or it renders from under the toolbar.
        val params = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply { behavior = AppBarLayout.ScrollingViewBehavior() }

        setContentView(ComposeView(this).apply {
            setContent {
                val status by homeModel.serviceStatus.asFlow()
                    .collectAsStateWithLifecycle(initialValue = null)
                val grantedCount by appsModel.grantedCount.asFlow()
                    .collectAsStateWithLifecycle(initialValue = null)

                ShizukuTheme(
                    nightMode = ShizukuSettings.getNightMode(),
                    blackNightTheme = ThemeHelper.isBlackNightTheme(this@HomeActivity),
                    useSystemColor = ThemeHelper.isUsingSystemColor(),
                ) {
                    HomeScreen(
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
            }
        }, params)

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
            if (shouldShow) SnackbarHelper.show(
                this,
                window.decorView,
                msg = getString(R.string.snackbar_battery_optimization_home),
                duration = Snackbar.LENGTH_INDEFINITE,
                actionText = getString(R.string.snackbar_action_fix),
                action = { SettingsHelper.requestIgnoreBatteryOptimizations(this, null) }
            )
        }
        homeModel.checkBatteryOptimization()

        lifecycleScope.launch {
            if (UpdateHelper.isCheckForUpdatesEnabled() && UpdateHelper.isNewUpdateAvailable()) {
                SnackbarHelper.show(
                    this@HomeActivity,
                    window.decorView,
                    msg = getString(R.string.snackbar_update_available),
                    duration = Snackbar.LENGTH_INDEFINITE,
                    actionText = getString(R.string.snackbar_action_update),
                    action = {
                        lifecycleScope.launch {
                            UpdateHelper.update()
                        }
                    }
                )
                UpdateHelper.updateLastPromptedVersion()
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
        SnackbarHelper.dismiss()
    }

    private fun showExitDialog(title: String, message: String) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.home_dialog_button_exit, null)
            .setOnDismissListener {
                this.finishAffinity()
            }
            .create()

        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
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
            WadbEnableUsbDebuggingDialogFragment().show(supportFragmentManager)
            return
        }

        val tcpPort = EnvironmentUtils.getAdbTcpPort()
        val tcpMode = ShizukuSettings.getTcpMode()

        // If ADB is NOT listening to a TCP port and the device doesn't support TLS, inform the user
        if (tcpPort <= 0 && !EnvironmentUtils.isTlsSupported()) {
            WadbNotEnabledDialogFragment().show(supportFragmentManager)
        // If ADB IS NOT listening to a TCP port but the device supports TLS, start mDns discovery
        } else if (tcpPort <= 0) {
            AdbDialogFragment().show(supportFragmentManager)
        // If ADB IS listening to a TCP port but the user wants to close it and use TLS instead, close the TCP port and start mDns discovery
        } else if (!tcpMode) {
            lifecycleScope.launch(Dispatchers.IO) {
                AdbStarter.stopTcp(this@HomeActivity, tcpPort)
            }
            AdbDialogFragment().show(supportFragmentManager)
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
            AdbPairDialogFragment().show(supportFragmentManager)
        } else {
            startActivity(Intent(this, AdbPairingTutorialActivity::class.java))
        }
    }

    private fun showAdbCommandDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.home_adb_button_view_command)
            .setMessage(
                HtmlCompat.fromHtml(
                    getString(R.string.home_adb_dialog_view_command_message, Starter.adbCommand),
                    HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE,
                )
            )
            .setPositiveButton(R.string.home_adb_dialog_view_command_copy_button) { _, _ ->
                if (ClipboardUtils.put(this, Starter.adbCommand)) {
                    Toast.makeText(this, getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.home_adb_dialog_view_command_button_send) { _, _ ->
                var intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, Starter.adbCommand)
                intent = Intent.createChooser(intent, getString(R.string.home_adb_dialog_view_command_button_send))
                startActivity(intent)
            }
            .show()
    }

    private fun showAutomationSheet() {
        val authToken = ShizukuSettings.getAuthToken()

        val sheetBinding = HomeAutomationBottomSheetBinding.inflate(layoutInflater)

        sheetBinding.apply {
            val action = getIntentAction(buttonGroup.checkedButtonId)
            val fields = listOf(
                Field(actionLayout, actionEditText, action),
                Field(packageLayout, packageEditText, packageName),
                Field(targetLayout, targetEditText, "Broadcast Receiver"),
                Field(extrasLayout, extrasEditText, authToken)
            )

            fields.forEach { (layout, input, initText) ->
                input.setText(initText)
                input.setKeyListener(null)

                layout.setEndIconOnClickListener { v ->
                    val context = v.context
                    if (
                        ClipboardUtils.put(context, input.text) &&
                        android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2
                    ) {
                        Toast.makeText(context, context.getString(R.string.toast_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            buttonGroup.addOnButtonCheckedListener { _, buttonId, isChecked ->
                if (isChecked) {
                    val action = getIntentAction(buttonId)
                    actionEditText.setText(action)
                }
            }
            extrasLayout.setStartIconOnClickListener {
                MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle(R.string.home_automation_regenerate_token)
                    .setMessage(R.string.home_automation_regenerate_token_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val authToken = ShizukuSettings.generateAuthToken()
                        extrasEditText.setText(authToken)
                    }
                    .show()
            }
        }

        BottomSheetDialog(this).apply {
            setContentView(sheetBinding.root)
            show()
        }
    }

    private fun getIntentAction(buttonId: Int): String =
        when (buttonId) {
            R.id.buttonStart -> "${BuildConfig.APPLICATION_ID}.START"
            R.id.buttonStop -> "${BuildConfig.APPLICATION_ID}.STOP"
            else -> ""
        }

    private data class Field(
        val layout: TextInputLayout,
        val input: TextInputEditText,
        val initText: String
    )

    override fun onDestroy() {
        ShizukuStateMachine.removeListener(stateListener)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                val binding = AboutDialogBinding.inflate(LayoutInflater.from(this), null, false)
                binding.sourceCode.movementMethod = LinkMovementMethod.getInstance()
                binding.sourceCode.text = getString(
                    R.string.about_view_source_code,
                    "<b><a href=\"https://github.com/magisk317/Shizuku\">GitHub</a></b>"
                ).toHtml()
                binding.icon.setImageBitmap(
                    AppIconCache.getOrLoadBitmap(
                        this,
                        applicationInfo,
                        Process.myUid() / 100000,
                        resources.getDimensionPixelOffset(R.dimen.default_app_icon_size)
                    )
                )
                binding.versionName.text = packageManager.getPackageInfo(packageName, 0).versionName

                binding.btnUpdate.setOnClickListener {
                    lifecycleScope.launch {
                        UpdateHelper.checkAndInstallUpdates()
                    }
                }

                val dialog = MaterialAlertDialogBuilder(this)
                    .setView(binding.root)
                    .create()

                binding.btnClose.setOnClickListener {
                    dialog.dismiss()
                }
                binding.btnDonate.visibility = android.view.View.GONE

                dialog.show()
                true
            }
            R.id.action_stop -> {
                if (ShizukuStateMachine.isRunning()) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.dialog_stop_message)
                        .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                            ShizukuStateMachine.set(ShizukuStateMachine.State.STOPPING)
                            runCatching { Shizuku.exit() }
                                .onFailure { Log.w(AppConstants.TAG, "Failed to stop Shizuku", it) }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_SHOW_PAIRING_DIALOG = "show_pairing_dialog"
        const val EXTRA_START_SERVICE_VIA_WADB = "start_service_via_wadb"
    }

}
