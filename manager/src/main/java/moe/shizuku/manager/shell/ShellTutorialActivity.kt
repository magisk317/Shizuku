package moe.shizuku.manager.shell

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.appbar.AppBarLayout
import java.io.File
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.app.ShizukuTheme
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.utils.CustomTabsHelper

class ShellTutorialActivity : AppBarActivity() {

    companion object {

        private val SH_NAME = "rish"

        /**
         * An unminified build splits this module across rish_shizuku2.dex and beyond, and the
         * rish script loads every part as a ":"-separated classpath, so the export has to ship
         * all of them - a lone rish_shizuku.dex no longer runs.
         */
        private val DEX_PREFIX = "rish_shizuku"
        private val DEX_SUFFIX = ".dex"
    }

    private val openDocumentsTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
            if (tree == null) return@registerForActivityResult

            val cr = contentResolver
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val child =
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

            cr.query(
                child,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use {
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val name = it.getString(1)
                    if (name == SH_NAME || (name.startsWith(DEX_PREFIX) && name.endsWith(DEX_SUFFIX))) {
                        DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                }
            }

            fun writeToDocument(name: String) {
                val uri = DocumentsContract.createDocument(contentResolver, doc, "application/octet-stream", name)
                    ?: return
                runCatching {
                    cr.openOutputStream(uri)?.let { output ->
                        assets.open(name).use { input ->
                            if (name == SH_NAME) {
                                input.bufferedReader().use {
                                    val text = it.readText()
                                        .replace("MANAGER_PKG", applicationContext.packageName)
                                    output.write(text.toByteArray())
                                }
                            } else {
                                input.copyTo(output)
                            }
                        }
                    }
                }.onFailure { Log.w(AppConstants.TAG, "Failed to write $name to the picked document", it) }
            }

            assets.list("").orEmpty()
                .filter { it.startsWith(DEX_PREFIX) && it.endsWith(DEX_SUFFIX) }
                .sorted()
                .forEach { writeToDocument(it) }
            writeToDocument(SH_NAME)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // The content must carry the scrolling-view behaviour like every View-based
        // child of the CoordinatorLayout root, or it renders from under the toolbar.
        val params = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply { behavior = AppBarLayout.ScrollingViewBehavior() }

        setContentView(ComposeView(this).apply {
            setContent {
                ShizukuTheme(
                    nightMode = ShizukuSettings.getNightMode(),
                    blackNightTheme = ThemeHelper.isBlackNightTheme(this@ShellTutorialActivity),
                    useSystemColor = ThemeHelper.isUsingSystemColor(),
                ) {
                    ShellTutorialScreen(
                        onExportFiles = { openDocumentsTree.launch(null) },
                        onOpenRishDocs = {
                            CustomTabsHelper.launchUrlOrCopy(this@ShellTutorialActivity, Helps.RISH.get())
                        },
                    )
                }
            }
        }, params)
    }
}
