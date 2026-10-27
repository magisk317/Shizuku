package moe.shizuku.manager.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.Keys.*
import moe.shizuku.manager.app.ThemeHelper
import android.text.TextUtils
import moe.shizuku.manager.R
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.shizuku.manager.ShizukuLocales
import java.util.Locale

/**
 * Everything the settings screen persists, in one immutable value.
 *
 * Mirrors the snapshot shape the rest of the chain uses (xinyi's
 * GeneralSettingsSnapshot, MiPush's IslandSettingsSnapshot): the UI reads one
 * object and never reaches into SharedPreferences itself.
 */
data class SettingsSnapshot(
    val startOnBoot: Boolean = false,
    val watchdog: Boolean = false,
    val tcpMode: Boolean = true,
    val tcpPort: String = "",
    val autoDisableUsbDebugging: Boolean = false,
    val languageTag: String = "",
    val nightMode: Int = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    val blackNightTheme: Boolean = false,
    val useSystemColor: Boolean = false,
    // ShizukuSettings.UpdateMode.STABLE, by value rather than through the nested
    // class: the Kotlin compiler resolves ShizukuSettings.Keys but not
    // ShizukuSettings.UpdateMode from the Java sources of this module.
    val updateMode: Int = 1,
    val legacyPairing: Boolean = false,
)

/** The language picker's display model, built once per language change. */
class LocaleEntries(
    val options: List<String>,
    val summary: String,
    val selectedIndex: Int,
)

/**
 * Single write path for the settings screen, exposing the current state as a
 * [StateFlow] the way the chain's preference repositories do.
 *
 * Everything that touches [ShizukuSettings] lives here on purpose: a Kotlin file
 * added to this module that references it directly fails to resolve the class
 * under AGP 9's built-in Kotlin, and keeping the access in one file both works
 * around that and keeps the screen reading a snapshot instead of raw prefs.
 *
 * Storage stays where it has always been: device-protected SharedPreferences,
 * because BootCompleteReceiver reads these values before the user unlocks. A
 * DataStore move would have to keep that file in device-protected storage and
 * convert the 45 synchronous ShizukuSettings accessors, which is a change of its
 * own -- this class is the seam that makes it possible without touching the UI
 * again.
 */
class SettingsRepository(private val context: Context) {

    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<SettingsSnapshot> = _snapshot.asStateFlow()

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _snapshot.value = read()
    }

    fun start() {
        ShizukuSettings.getPreferences().registerOnSharedPreferenceChangeListener(changeListener)
    }

    fun stop() {
        ShizukuSettings.getPreferences().unregisterOnSharedPreferenceChangeListener(changeListener)
    }

    private fun read() = SettingsSnapshot(
        startOnBoot = ShizukuSettings.getStartOnBoot(context),
        watchdog = ShizukuSettings.isWatchdogRunning(),
        tcpMode = ShizukuSettings.getTcpMode(),
        tcpPort = ShizukuSettings.getTcpPort().toString(),
        autoDisableUsbDebugging = ShizukuSettings.getAutoDisableUsbDebugging(),
        languageTag = ShizukuSettings.getPreferences().getString(KEY_LANGUAGE, null) ?: "",
        nightMode = ShizukuSettings.getNightMode(),
        blackNightTheme = ThemeHelper.isBlackNightTheme(context),
        useSystemColor = ThemeHelper.isUsingSystemColor(),
        updateMode = ShizukuSettings.getUpdateMode(),
        legacyPairing = ShizukuSettings.getLegacyPairing(),
    )

    fun setStartOnBoot(enabled: Boolean) {
        ShizukuSettings.setStartOnBoot(context, enabled)
    }

    fun setWatchdog(enabled: Boolean) {
        ShizukuSettings.setWatchdog(context, enabled)
    }

    fun setTcpMode(enabled: Boolean) {
        ShizukuSettings.setTcpMode(enabled)
    }

    /** Null restores the default port, which is what an empty field means here. */
    fun setTcpPort(port: Int?) {
        ShizukuSettings.setTcpPort(port)
    }

    fun setAutoDisableUsbDebugging(enabled: Boolean) {
        putBoolean(KEY_AUTO_DISABLE_USB_DEBUGGING, enabled)
    }

    fun setLanguageTag(tag: String) {
        putString(KEY_LANGUAGE, tag)
    }

    fun setNightMode(mode: Int) {
        putInt(KEY_NIGHT_MODE, mode)
    }

    fun setBlackNightTheme(enabled: Boolean) {
        putBoolean(KEY_BLACK_NIGHT_THEME, enabled)
    }

    fun setUseSystemColor(enabled: Boolean) {
        putBoolean(KEY_USE_SYSTEM_COLOR, enabled)
    }

    fun setUpdateMode(mode: Int) {
        putInt(KEY_UPDATE_MODE, mode)
    }

    fun setLegacyPairing(enabled: Boolean) {
        putBoolean(KEY_LEGACY_PAIRING, enabled)
    }

    /**
     * A TCP change only takes effect on the next start, so the settings marked
     * those rows with a restart icon while the running server still used the old
     * value. Same signal, computed from the repository's own view of the world.
     */
    fun needsTcpModeRestart(newValue: Boolean?): Boolean {
        val currentPort = EnvironmentUtils.getAdbTcpPort()
        val newMode = newValue ?: ShizukuSettings.getTcpMode()
        return (currentPort > 0) != newMode
    }

    fun needsTcpPortRestart(newValue: Int?): Boolean {
        val currentPort = EnvironmentUtils.getAdbTcpPort()
        val newPort = newValue ?: ShizukuSettings.getTcpPort()
        return (currentPort > 0) && (currentPort != newPort)
    }


    fun buildLocaleEntries(): LocaleEntries {
        val localeTags = ShizukuLocales.LOCALES
        val displayLocaleTags = ShizukuLocales.DISPLAY_LOCALES
        val currentTag = ShizukuSettings.getPreferences().getString(KEY_LANGUAGE, null) ?: ""
        val currentLocaleIndex = localeTags.indexOf(currentTag)
        val currentLocale = ShizukuSettings.getLocale() ?: Locale.getDefault()

        val options = mutableListOf<String>()
        for ((index, displayLocale) in displayLocaleTags.withIndex()) {
            if (index == 0) {
                options.add(context.getString(R.string.follow_system))
                continue
            }
            val locale = Locale.forLanguageTag(displayLocale.toString())
            val hasScript = !TextUtils.isEmpty(locale.script)
            val native = if (hasScript) locale.getDisplayScript(locale) else locale.getDisplayName(locale)
            val localized = if (hasScript) locale.getDisplayScript(currentLocale) else locale.getDisplayName(currentLocale)
            options.add(if (index == currentLocaleIndex) localized else "$native\n$localized")
        }

        val summary = when {
            TextUtils.isEmpty(currentTag) || "SYSTEM" == currentTag -> context.getString(R.string.follow_system)
            currentLocaleIndex != -1 -> options[currentLocaleIndex].substringBefore('\n')
            else -> ""
        }
        return LocaleEntries(options, summary, if (currentLocaleIndex >= 0) currentLocaleIndex else 0)
    }

    /** The language picker's display model, built once per language change. */
    private fun putBoolean(key: String, value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(key, value).apply()
    }

    private fun putInt(key: String, value: Int) {
        ShizukuSettings.getPreferences().edit().putInt(key, value).apply()
    }

    private fun putString(key: String, value: String) {
        ShizukuSettings.getPreferences().edit().putString(key, value).apply()
    }
}
