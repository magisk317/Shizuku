package moe.shizuku.manager.settings

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.ShizukuTheme
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.worker.AdbStartWorker
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.text.AppText

class BugReportDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ShizukuTheme(
                nightMode = ShizukuSettings.getNightMode(),
                blackNightTheme = ThemeHelper.isBlackNightTheme(this@BugReportDialogActivity),
                useSystemColor = ThemeHelper.isUsingSystemColor(),
            ) {
                AppAlertDialog(
                    onDismissRequest = { finish() },
                    title = { AppText(stringResource(R.string.settings_report_bug)) },
                    text = { BugReportContent() },
                    confirmButton = {
                        AppTextButton(
                            text = "GitHub",
                            onClick = {
                                CustomTabsHelper.launchUrlOrCopy(
                                    this@BugReportDialogActivity,
                                    "https://github.com/magisk317/Shizuku/issues/new",
                                )
                                finish()
                            },
                        )
                    },
                    dismissButton = {
                        AppTextButton(
                            text = stringResource(R.string.bug_report_dialog_button_email),
                            onClick = {
                                sendBugReportEmail()
                                finish()
                            },
                        )
                    },
                )
            }
        }
    }

    // The notification that opened this dialog owns its own dismissal: cancelling it here is
    // what the DialogFragment's onCancel used to do.
    override fun onStop() {
        super.onStop()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(AdbStartWorker.NOTIFICATION_ID)
    }

    private fun sendBugReportEmail() {
        val plainBody = """
            Please describe the bug. Include steps to reproduce if possible, as well as any relevant images/logs.

            Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
            Android Version: ${android.os.Build.VERSION.RELEASE}
            Shizuku Version: ${BuildConfig.VERSION_NAME}
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(
            "mailto:" + getString(R.string.support_email) +
                "?subject=" + Uri.encode("[ISSUE TITLE]") +
                "&body=" + Uri.encode(plainBody)
        ))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.toast_no_email_app), Toast.LENGTH_SHORT).show()
        }
    }
}
