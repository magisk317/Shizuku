package moe.shizuku.manager.management

import android.content.pm.PackageInfo
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.*
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.app.ShizukuTheme
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.html.text.HtmlCompat
import rikka.lifecycle.Status
import rikka.shizuku.Shizuku
import android.view.ViewGroup

class ApplicationManagementActivity : AppBarActivity() {

    private val viewModel: AppsViewModel by viewModels()

    private var revision by mutableIntStateOf(0)

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

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel.packages.observe(this) {
            if (it.status == Status.ERROR) {
                finish()
                val tr = it.error
                Toast.makeText(this, Objects.toString(tr, "unknown"), Toast.LENGTH_SHORT).show()
                tr.printStackTrace()
            }
        }
        viewModel.load()

        // The content must carry the scrolling-view behaviour like every View-based
        // child of the CoordinatorLayout root, or it renders from under the toolbar.
        val params = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply { behavior = AppBarLayout.ScrollingViewBehavior() }

        setContentView(ComposeView(this).apply {
            setContent {
                val packages by viewModel.packages.asFlow()
                    .collectAsStateWithLifecycle(initialValue = null)

                ShizukuTheme(
                    nightMode = ShizukuSettings.getNightMode(),
                    blackNightTheme = ThemeHelper.isBlackNightTheme(this@ApplicationManagementActivity),
                    useSystemColor = ThemeHelper.isUsingSystemColor(),
                ) {
                    AppsScreen(
                        packages = packages?.data,
                        revision = revision,
                        onToggle = { pi -> toggle(pi) },
                        onToggleAll = { toggleAll() },
                    )
                }
            }
        }, params)

        ShizukuStateMachine.addListener(stateListener)
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
                showAdbLimitedDialog()
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

    private fun showAdbLimitedDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_management_dialog_adb_is_limited_title)
            .setMessage(
                getString(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get())
                    .toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE)
            )
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            (it as AlertDialog).findViewById<TextView>(android.R.id.message)?.movementMethod =
                LinkMovementMethod.getInstance()
        }
        try {
            dialog.show()
        } catch (ignored: Throwable) {
        }
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
