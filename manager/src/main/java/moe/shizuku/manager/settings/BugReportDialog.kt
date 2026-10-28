package moe.shizuku.manager.settings

import android.app.Dialog
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.worker.AdbStartWorker
import io.github.magisk317.uikit.surface.AppHorizontalDivider
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

class BugReportDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val content = ComposeView(context).apply {
            setContent { BugReportContent() }
        }

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_report_bug)
            .setView(content)
            .setPositiveButton("GitHub") { _, _ ->
                CustomTabsHelper.launchUrlOrCopy(context, "https://github.com/magisk317/Shizuku/issues/new")
            }
            .setNegativeButton(R.string.bug_report_dialog_button_email) { _, _ ->
                val plainBody = """
                    Please describe the bug. Include steps to reproduce if possible, as well as any relevant images/logs.

                    Device: ${Build.MANUFACTURER} ${Build.MODEL}
                    Android Version: ${Build.VERSION.RELEASE}
                    Shizuku Version: ${BuildConfig.VERSION_NAME}
                """.trimIndent()

                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(
                    "mailto:" + context.getString(R.string.support_email) +
                    "?subject=" + Uri.encode("[ISSUE TITLE]") +
                    "&body=" + Uri.encode(plainBody)
                ))
                try {
                    context.startActivity(intent)
                    dismiss()
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, context.getString(R.string.toast_no_email_app), Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(AdbStartWorker.NOTIFICATION_ID)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (activity is BugReportDialogActivity) activity?.finish()
    }
}

@Composable
private fun BugReportContent() {
    val context = LocalContext.current
    val updateLink = templateWithLink(
        templateRes = R.string.bug_report_dialog_update,
        linkTextRes = R.string.bug_report_dialog_link_update,
        url = "https://github.com/magisk317/Shizuku/releases/latest",
    )
    val wikiLink = templateWithLink(
        templateRes = R.string.bug_report_dialog_wiki,
        linkTextRes = R.string.bug_report_dialog_link_wiki,
        url = "https://github.com/magisk317/Shizuku/wiki#troubleshooting",
    )
    val issuesLink = templateWithLink(
        templateRes = R.string.bug_report_dialog_issues,
        linkTextRes = R.string.bug_report_dialog_link_issues,
        url = "https://github.com/magisk317/Shizuku/issues",
    )
    val methodText = stringResource(R.string.bug_report_dialog_method).replace("^1", "GitHub")

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        AppText(
            text = stringResource(R.string.bug_report_dialog_before_reporting),
            role = AppTextRole.Subtitle,
        )
        AppText(text = updateLink, role = AppTextRole.Body, modifier = Modifier.padding(top = 8.dp))
        AppText(text = wikiLink, role = AppTextRole.Body, modifier = Modifier.padding(top = 8.dp))
        AppText(text = issuesLink, role = AppTextRole.Body, modifier = Modifier.padding(top = 8.dp))
        AppHorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        AppText(
            text = methodText,
            role = AppTextRole.Body,
            fontWeight = FontWeight.Bold,
        )
        AppText(
            text = stringResource(R.string.bug_report_dialog_method_2),
            role = AppTextRole.Body,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Expands the `^1` template the strings use with a clickable link.
 *
 * The view version replaced `^1` with a ClickableSpan; Compose renders links
 * through [LinkAnnotation], so the span becomes a clickable range that still
 * hands the URL to [CustomTabsHelper] the way the rest of the app does.
 */
@Composable
private fun templateWithLink(templateRes: Int, linkTextRes: Int, url: String): AnnotatedString {
    val context = LocalContext.current
    val template = stringResource(templateRes)
    val linkText = stringResource(linkTextRes)
    return remember(template, linkText, url) {
        buildAnnotatedString {
            template.split("^1").forEachIndexed { index, part ->
                append(part)
                if (index == 0) {
                    pushLink(
                        LinkAnnotation.Clickable(
                            tag = url,
                            linkInteractionListener = {
                                CustomTabsHelper.launchUrlOrCopy(context, url)
                            },
                        )
                    )
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(linkText)
                    }
                    pop()
                }
            }
        }
    }
}
