package moe.shizuku.manager.stealth

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppScaffold
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.ConfirmActionDialog
import io.github.magisk317.uikit.text.AppText
import java.io.File
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.ShizukuTheme
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.ktx.activityTitle
import moe.shizuku.manager.utils.ApkUtils.*

class StealthTutorialActivity : ComponentActivity() {
    private val viewModel: StealthTutorialViewModel by viewModels()

    private lateinit var outDir: Uri

    private var chooseFolder by mutableStateOf(false)
    private var uninstallRequired by mutableStateOf(false)
    private var cancelPending by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, backCallback)

        setContent {
            val state by viewModel.uiState.asFlow()
                .collectAsStateWithLifecycle(initialValue = null)

            ShizukuTheme(
                nightMode = ShizukuSettings.getNightMode(),
                blackNightTheme = ThemeHelper.isBlackNightTheme(this@StealthTutorialActivity),
                useSystemColor = ThemeHelper.isUsingSystemColor(),
            ) {
                val navController = rememberNavController()
                AppScaffold(
                    topBar = {
                        AppTopBar(
                            title = this@StealthTutorialActivity.activityTitle(),
                            navigationIcon = {
                                AppIconButton(onClick = { backCallback.handleOnBackPressed() }) {
                                    AppIcon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(
                                            androidx.appcompat.R.string.abc_action_bar_up_description,
                                        ),
                                    )
                                }
                            },
                        )
                    },
                ) { contentPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "stealth",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    ) {
                        composable("stealth") {
                            StealthTutorialScreen(
                                state = state,
                                onAction = { action, packageNameInput ->
                                    when (action) {
                                        Action.HIDE -> {
                                            viewModel.setPackageName(packageNameInput)
                                            chooseFolder = true
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
                }

                if (chooseFolder) {
                    ConfirmActionDialog(
                        title = stringResource(R.string.choose_folder),
                        message = stringResource(R.string.stealth_choose_folder_message),
                        confirmText = stringResource(R.string.choose_folder),
                        onDismissRequest = { chooseFolder = false },
                        onConfirm = {
                            chooseFolder = false
                            pickFolderLauncher.launch(null)
                        },
                    )
                }

                if (uninstallRequired) {
                    ConfirmActionDialog(
                        title = stringResource(R.string.stealth_uninstall_required),
                        message = stringResource(R.string.stealth_uninstall_message),
                        confirmText = stringResource(R.string.stealth_uninstall),
                        onDismissRequest = { uninstallRequired = false },
                        onConfirm = {
                            uninstallRequired = false
                            uninstallPackage(packageName)
                        },
                    )
                }

                if (cancelPending) {
                    ConfirmActionDialog(
                        title = "${stringResource(android.R.string.cancel)}?",
                        message = "",
                        confirmText = stringResource(android.R.string.ok),
                        onDismissRequest = { cancelPending = false },
                        onConfirm = {
                            cancelPending = false
                            finish()
                        },
                    )
                }

                error?.let { message ->
                    AppAlertDialog(
                        onDismissRequest = { error = null },
                        title = { AppText(stringResource(R.string.error)) },
                        text = { AppText(message) },
                        confirmButton = {
                            AppTextButton(
                                text = stringResource(android.R.string.ok),
                                onClick = { error = null },
                            )
                        },
                    )
                }
            }
        }

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
                                uninstallRequired = true
                            }

                            ApkType.STUB -> {
                                installPackage(apk) { isSuccess, msg -> handleInstallerResult(isSuccess, msg) }
                            }
                        }
                    } catch (e: Exception) {
                        showError(e)
                    }
                }

                is UiState.Error -> showError(state.error)
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

    private fun handleInstallerResult(isSuccess: Boolean, msg: String?) {
        viewModel.refresh()
        if (isSuccess) {
            Toast.makeText(
                this,
                getString(R.string.success),
                Toast.LENGTH_SHORT,
            ).show()
        } else showError(Exception(msg ?: "Unknown error"))
    }

    private fun showError(e: Exception) {
        error = e.message
    }

    private val backCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val uiState = viewModel.uiState.value
                if (uiState is UiState.Idle || uiState is UiState.Error || uiState == null) {
                    finish()
                    return
                }
                cancelPending = true
            }
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
