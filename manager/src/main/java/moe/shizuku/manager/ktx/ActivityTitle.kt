package moe.shizuku.manager.ktx

import android.app.Activity
import android.content.pm.ComponentInfo

/**
 * The Compose top bar needs the same text the framework would have taken from
 * `android:label`, so the manifest stays the single source of screen titles
 * instead of every activity repeating a string resource.
 */
fun Activity.activityTitle(): String {
    val info: ComponentInfo? = runCatching {
        packageManager.getActivityInfo(componentName, 0)
    }.getOrNull()
    return info?.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() } ?: ""
}
