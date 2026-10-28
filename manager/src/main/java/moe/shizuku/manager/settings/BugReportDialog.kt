package moe.shizuku.manager.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppHorizontalDivider
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.worker.AdbStartWorker

@Composable
fun BugReportContent() {
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
