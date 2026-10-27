package moe.shizuku.manager.settings

import android.os.Bundle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.app.ShizukuTheme

class SettingsActivity : AppBarActivity() {

    private val repository by lazy { SettingsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setContentView(ComposeView(this).apply {
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
                    SettingsScreen(
                        repository = repo,
                        onBugReport = { BugReportDialog().show(supportFragmentManager, "BugReportDialog") },
                    )
                }
            }
        })
    }
}
