package moe.shizuku.manager.stealth

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.appbar.AppBarLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import android.util.Log
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.app.ShizukuTheme
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.ApkUtils.*
import androidx.lifecycle.asFlow

class StealthTutorialActivity : AppBarActivity() {
    private val viewModel: StealthTutorialViewModel by viewModels()

    private lateinit var outDir: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, backCallback)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // The content must carry the scrolling-view behaviour like every View-based
        // child of the CoordinatorLayout root, or it renders from under the toolbar.
        val params = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply { behavior = AppBarLayout.ScrollingViewBehavior() }

        setContentView(ComposeView(this).apply {
            setContent {
                val state by viewModel.uiState.asFlow()
                    .collectAsStateWithLifecycle(initialValue = null)

                ShizukuTheme(
                    nightMode = ShizukuSettings.getNightMode(),
                    blackNightTheme = ThemeHelper.isBlackNightTheme(this@StealthTutorialActivity),
                    useSystemColor = ThemeHelper.isUsingSystemColor(),
                ) {
                    StealthTutorialScreen(
                        state = state,
                        onAction = { action, packageNameInput ->
                            when (action) {
                                Action.HIDE -> {
                                    viewModel.setPackageName(packageNameInput)
                                    showChooseFolderDialog()
                                }
                                Action.UNHIDE -> viewModel.createApk(ApkType.STUB)
                                Action.REHIDE -> uninstallPackage(ORIGINAL_PACKAGE_NAME) { isSuccess, msg ->
                                    handleInstallerResult(isSuccess, msg)
                                }
                            }
                        },
                    )
                }
            }
        }, params)

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is UiState.Idle -> if (state.action == Action.HIDE) makeNavBarTransparent()
                is UiState.Pending -> {
                    try {
                        val apk = state.apk
                        when (state.apkType) {
                            ApkType.CLONE -> {
                                export(apk)
                                viewModel.refresh()
                                showUninstallDialog()
                            }

                            ApkType.STUB -> {
                                installPackage(apk) { isSuccess, msg -> handleInstallerResult(isSuccess, msg) }
                            }
                        }
                    } catch (e: Exception) {
                        showErrorDialog(e)
                    }
                }
                is UiState.Error -> showErrorDialog(state.error)
                is UiState.Loading, null -> Unit
            }
        }
    }

    override fun onDestroy() {
        runCatching {
            unregisterReceiver(installerReceiver)
        }.onFailure { Log.w(AppConstants.TAG, "Failed to unregister the install receiver", it) }
        super.onDestroy()
    }

    private fun showChooseFolderDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_folder)
            .setMessage(R.string.stealth_choose_folder_message)
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setPositiveButton(R.string.choose_folder) { _, _ ->
                pickFolderLauncher.launch(null)
            }.show()
    }

    private val pickFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
            if (folder == null) return@registerForActivityResult
            outDir = folder

            viewModel.createApk(ApkType.CLONE)
        }

    private fun export(apk: File) {
        val docUri =
            DocumentsContract.buildDocumentUriUsingTree(
                outDir,
                DocumentsContract.getTreeDocumentId(outDir),
            )

        val doc =
            DocumentsContract.createDocument(
                contentResolver,
                docUri,
                "application/vnd.android.package-archive",
                buildApkFilename()
            )

        if (doc == null) throw Exception("Could not create file in selected folder")

        contentResolver.openOutputStream(doc)?.use { output ->
            apk.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }

    private fun showUninstallDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.stealth_uninstall_required)
            .setMessage(R.string.stealth_uninstall_message)
            .setPositiveButton(R.string.stealth_uninstall) { _, _ ->
                uninstallPackage(packageName)
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun handleInstallerResult(isSuccess: Boolean, msg: String?) {
        viewModel.refresh()
        if (isSuccess) {
            Toast.makeText(
                this,
                getString(R.string.success),
                Toast.LENGTH_SHORT,
            ).show()
        } else showErrorDialog(Exception(msg ?: "Unknown error"))
    }

    private fun showErrorDialog(error: Exception) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error)
            .setMessage(error.message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private val backCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val uiState = viewModel.uiState.value
                if (uiState is UiState.Idle || uiState is UiState.Error || uiState == null) {
                    finish()
                    return
                }
                MaterialAlertDialogBuilder(this@StealthTutorialActivity)
                    .setTitle("${getString(android.R.string.cancel)}?")
                    .setPositiveButton(android.R.string.yes) { _, _ -> finish() }
                    .setNegativeButton(android.R.string.no, null)
                    .show()
            }
        }

    override fun onSupportNavigateUp(): Boolean {
        backCallback.handleOnBackPressed()
        return true
    }

    // Window.navigationBarColor is deprecated with no replacement below API 35.
    @Suppress("DEPRECATION")
    private fun makeNavBarTransparent() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            window.isNavigationBarContrastEnforced = false
    }
}
