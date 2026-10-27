package moe.shizuku.manager.settings

import android.os.Bundle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import moe.shizuku.manager.app.AppBarActivity

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
                SettingsScreen(
                    repository = repo,
                    onBugReport = { BugReportDialog().show(supportFragmentManager, "BugReportDialog") },
                )
            }
        })
    }
}
