package moe.shizuku.manager.management

import android.content.pm.PackageInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppScaffold
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.text.AppText
import java.util.Objects
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.ShizukuTheme
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.ktx.activityTitle
import moe.shizuku.manager.ktx.rememberHtmlText
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.lifecycle.Status
import rikka.shizuku.Shizuku

class ApplicationManagementActivity : ComponentActivity() {

    private val viewModel: AppsViewModel by viewModels()

    private var revision by mutableIntStateOf(0)

    // Set when a grant/revoke is rejected because the service is running as adb rather than
    // root; the explanation is a Compose dialog now, so the flag drives it directly.
    private var showAdbLimited by mutableStateOf(false)

    private val stateListener: (ShizukuStateMachine.State) -> Unit = {
        if (ShizukuStateMachine.isDead() && !isFinishing)
            finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ShizukuStateMachine.isRunning()) {
            finish()
            return
        }

        viewModel.packages.observe(this) {
            if (it.status == Status.ERROR) {
                finish()
                val tr = it.error
                Toast.makeText(this, Objects.toString(tr, "unknown"), Toast.LENGTH_SHORT).show()
                tr.printStackTrace()
            }
        }
        viewModel.load()

        setContent {
            val packages by viewModel.packages.asFlow()
                .collectAsStateWithLifecycle(initialValue = null)

            ShizukuTheme(
                nightMode = ShizukuSettings.getNightMode(),
                blackNightTheme = ThemeHelper.isBlackNightTheme(this@ApplicationManagementActivity),
                useSystemColor = ThemeHelper.isUsingSystemColor(),
            ) {
                val navController = rememberNavController()
                AppScaffold(
                    topBar = {
                        AppTopBar(
                            title = this@ApplicationManagementActivity.activityTitle(),
                            navigationIcon = {
                                AppIconButton(onClick = { this@ApplicationManagementActivity.finish() }) {
                                    AppIcon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(
                                            androidx.appcompat.R.string.abc_action_bar_up_description,
                                        ),
                                    )
                                }
                            },
                        )
                    },
                ) { contentPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "apps",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    ) {
                        composable("apps") {
                            AppsScreen(
                                packages = packages?.data,
                                revision = revision,
                                onToggle = { pi -> toggle(pi) },
                                onToggleAll = { toggleAll() },
                            )
                        }
                    }
                }

                if (showAdbLimited) {
                    AppAdbLimitedDialog(onDismiss = { showAdbLimited = false })
                }
            }
        }

        ShizukuStateMachine.addListener(stateListener)
    }

    @androidx.compose.runtime.Composable
    private fun AppAdbLimitedDialog(onDismiss: () -> Unit) {
        val message = rememberHtmlText(
            R.string.app_management_dialog_adb_is_limited_message,
            Helps.ADB.get(),
        )
        AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { AppText(stringResource(R.string.app_management_dialog_adb_is_limited_title)) },
            text = { AppText(message) },
            confirmButton = {
                AppTextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = onDismiss,
                )
            },
        )
    }

    private fun toggle(pi: PackageInfo) {
        val uid = pi.applicationInfo?.uid ?: return
        try {
            if (AuthorizationManager.granted(pi.packageName, uid)) {
                AuthorizationManager.revoke(pi.packageName, uid)
            } else {
                AuthorizationManager.grant(pi.packageName, uid)
            }
        } catch (e: SecurityException) {
            val uid0 = try {
                Shizuku.getUid()
            } catch (ex: Throwable) {
                null
            }
            if (uid0 != null && uid0 != 0) {
                showAdbLimited = true
            }
        }
        viewModel.load(true)
        revision++
    }

    private fun toggleAll() {
        val packages = runCatching { AuthorizationManager.getPackages() }.getOrNull() ?: return
        val enable = packages.isEmpty() || packages.any { pi ->
            runCatching { !AuthorizationManager.granted(pi.packageName, pi.applicationInfo!!.uid) }.getOrDefault(false)
        }
        for (pi in packages) {
            try {
                if (enable) {
                    AuthorizationManager.grant(pi.packageName, pi.applicationInfo!!.uid)
                } else {
                    AuthorizationManager.revoke(pi.packageName, pi.applicationInfo!!.uid)
                }
            } catch (ignored: Exception) {
            }
        }
        viewModel.load(true)
        revision++
    }

    override fun onDestroy() {
        ShizukuStateMachine.removeListener(stateListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        revision++
    }
}
