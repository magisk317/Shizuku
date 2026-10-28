package moe.shizuku.manager.authorization

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.shizuku.manager.R
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor

@Composable
fun PermissionConfirmationContent(
    title: AnnotatedString,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_system_icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        AppText(
            text = title,
            color = appColor(AppColorRole.OnSurface),
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
        AppTextButton(
            text = stringResource(R.string.grant_dialog_button_allow_always),
            onClick = onAllow, modifier = Modifier.fillMaxWidth(),
        )
        AppTextButton(
            text = stringResource(R.string.grant_dialog_button_deny),
            onClick = onDeny, modifier = Modifier.fillMaxWidth(),
        )
    }
}
