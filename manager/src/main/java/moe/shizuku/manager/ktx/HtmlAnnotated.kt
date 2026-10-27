package moe.shizuku.manager.ktx

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.TypefaceSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.text.HtmlCompat
import androidx.core.text.getSpans
import rikka.html.text.HtmlCompat as RikkaHtmlCompat

/**
 * The View screens rendered formatted strings through HtmlCompat so link, bold
 * and monospace spans survived. This is the Compose counterpart: the same
 * HtmlCompat pass, with its spans mapped onto an [AnnotatedString].
 */
@Composable
fun rememberHtmlText(res: Int, vararg formatArgs: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val raw = stringResource(res, *formatArgs)
    return remember(raw, linkColor) { raw.toHtmlAnnotated(linkColor) }
}

fun String.toHtmlAnnotated(linkColor: Color): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(this, RikkaHtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE) as Spanned
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
    spanned.getSpans<StyleSpan>(0, spanned.length).forEach { span ->
        val style = when (span.style) {
            Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            else -> null
        } ?: return@forEach
        builder.addStyle(style, spanned.getSpanStart(span), spanned.getSpanEnd(span))
    }
    spanned.getSpans<TypefaceSpan>(0, spanned.length).forEach { span ->
        if (span.family == "monospace") {
            builder.addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace),
                spanned.getSpanStart(span),
                spanned.getSpanEnd(span),
            )
        }
    }
    return builder.toAnnotatedString()
}
