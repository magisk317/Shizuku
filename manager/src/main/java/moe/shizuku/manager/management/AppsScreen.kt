package moe.shizuku.manager.management

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.utils.AppIconCache
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import io.github.magisk317.uikit.preference.AppSwitch
import io.github.magisk317.uikit.surface.AppHorizontalDivider
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor

/**
 * Compose replacement for the authorized-apps list: the toggle-all header, the
 * app rows (icon, label, package, root-requirement tag, switch) and the empty
 * state. Grant/revoke and the adb-limited dialog stay in
 * [ApplicationManagementActivity]; [revision] bumps whenever the caller flips
 * an authorization so the switches re-read their state.
 */
@Composable
fun AppsScreen(
    packages: List<PackageInfo>?,
    revision: Int,
    onToggle: (PackageInfo) -> Unit,
    onToggleAll: () -> Unit,
) {
    when {
        packages == null -> Box(Modifier.fillMaxSize())
        packages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppText(
                stringResource(R.string.home_app_management_empty),
                role = AppTextRole.Body,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            item(key = "toggle_all") {
                ToggleAllRow(revision, onToggleAll)
            }
            items(packages, key = { it.packageName }) { pi ->
                AppRow(pi, revision, onToggle)
                AppHorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = appColor(AppColorRole.OutlineVariant).copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun ToggleAllRow(revision: Int, onToggleAll: () -> Unit) {
    val allEnabled = remember(revision) { areAllEnabled() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            stringResource(R.string.app_management_toggle_all),
            role = AppTextRole.BodyLarge,
            modifier = Modifier.weight(1f),
        )
        AppSwitch(checked = allEnabled, onCheckedChange = { onToggleAll() })
    }
}

@Composable
private fun AppRow(pi: PackageInfo, revision: Int, onToggle: (PackageInfo) -> Unit) {
    val ai = pi.applicationInfo ?: return
    val checked = remember(pi.packageName, revision) { grantedOrFalse(pi) }
    val requiresRoot = remember(pi.packageName) {
        ai.metaData?.getBoolean("moe.shizuku.client.V3_REQUIRES_ROOT") == true
    }
    val context = LocalContext.current
    val title = remember(pi.packageName, revision) { appLabel(ai, context) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onToggle(pi) })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(ai)
        Column(Modifier.weight(1f)) {
            AppText(title, role = AppTextRole.BodyLarge)
            AppText(
                ai.packageName,
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            if (requiresRoot) {
                AppText(
                    stringResource(R.string.app_management_item_summary_requires_root),
                    role = AppTextRole.BodySmall,
                    color = appColor(AppColorRole.Primary),
                )
            }
        }
        AppSwitch(checked = checked, onCheckedChange = { onToggle(pi) })
    }
}

/** The View list used AppIconCache's async loader; same cache, now into state. */
@Composable
private fun AppIcon(ai: ApplicationInfo) {
    val context = LocalContext.current
    val size = remember { context.resources.getDimensionPixelSize(R.dimen.default_app_icon_size) }
    var bitmap by remember(ai.packageName) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(ai.packageName) {
        withContext(Dispatchers.IO) {
            bitmap = AppIconCache.getOrLoadBitmap(context, ai, UserHandleCompat.getUserId(ai.uid), size)
        }
    }
    val image = bitmap?.asImageBitmap()
    if (image != null) {
        Image(
            image,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(Modifier.size(40.dp))
    }
}

private fun grantedOrFalse(pi: PackageInfo): Boolean =
    runCatching { AuthorizationManager.granted(pi.packageName, pi.applicationInfo!!.uid) }
        .getOrDefault(false)

private fun areAllEnabled(): Boolean =
    runCatching {
        val packages = AuthorizationManager.getPackages()
        packages.isNotEmpty() && packages.all { pi -> grantedOrFalse(pi) }
    }.getOrDefault(false)

private fun appLabel(ai: ApplicationInfo, context: Context): String {
    val pm = context.packageManager
    val userId = UserHandleCompat.getUserId(ai.uid)
    return if (userId != UserHandleCompat.myUserId()) {
        val userInfo = ShizukuSystemApis.getUserInfo(userId)
        "${ai.loadLabel(pm)} - ${userInfo.name} ($userId)"
    } else {
        ai.loadLabel(pm).toString()
    }
}
