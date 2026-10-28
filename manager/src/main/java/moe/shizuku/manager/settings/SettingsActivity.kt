package moe.shizuku.manager.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.magisk317.uikit.surface.AppIcon
import moe.shizuku.manager.app.AppPageScaffold
import moe.shizuku.manager.app.ShizukuTheme
import moe.shizuku.manager.ktx.activityTitle

/**
 * ComponentActivity, not Shizuku's AppActivity/MaterialActivity chain: the miuix
 * overlay popups used by the settings rows need the navigation event dispatcher
 * that activity-compose installs for a plain ComponentActivity, and an
 * AppCompatActivity (rikka's MaterialActivity) does not provide it.
 */
class SettingsActivity : ComponentActivity() {

    private val repository by lazy { SettingsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val repo = remember { repository }
            DisposableEffect(repo) {
                repo.start()
                onDispose { repo.stop() }
            }
            val snapshot by repo.snapshot.collectAsState()
            ShizukuTheme(
                nightMode = snapshot.nightMode,
                blackNightTheme = snapshot.blackNightTheme,
                useSystemColor = snapshot.useSystemColor,
            ) {
                val navController = rememberNavController()
                AppPageScaffold(
                    title = this@SettingsActivity.activityTitle(),
                    onBack = { this@SettingsActivity.finish() },
                ) { contentPadding, scrollModifier ->
                    NavHost(
                        navController = navController,
                        startDestination = "settings",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .then(scrollModifier),
                    ) {
                        composable("settings") {
                            SettingsScreen(
                                repository = repo,
                                onBugReport = {
                                    startActivity(
                                        Intent(this@SettingsActivity, BugReportDialogActivity::class.java),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
