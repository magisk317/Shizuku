package moe.shizuku.manager.starter

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppScaffold
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLProtocolException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import moe.shizuku.manager.AppConstants.EXTRA
import moe.shizuku.manager.R
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.ktx.activityTitle
import moe.shizuku.manager.utils.ShizukuStateMachine
import rikka.lifecycle.Resource
import rikka.lifecycle.Status

private class NotRootedException: Exception()

class StarterActivity : ComponentActivity() {

    private val viewModel: ViewModel by viewModels()
    private val outputState = mutableStateOf("")

    // Resource id of the failure explanation; non-zero means the dialog is open.
    private val errorMessageRes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val output by outputState
            val messageRes by errorMessageRes

            AppScaffold(
                topBar = {
                    AppTopBar(
                        title = this@StarterActivity.activityTitle(),
                        navigationIcon = {
                            AppIconButton(onClick = { this@StarterActivity.finish() }) {
                                AppIcon(
                                    painter = painterResource(R.drawable.ic_close_24),
                                    contentDescription = stringResource(
                                        androidx.appcompat.R.string.abc_action_bar_up_description,
                                    ),
                                )
                            }
                        },
                    )
                },
            ) {
                NavHost(navController = rememberNavController(), startDestination = "starter") {
                    composable("starter") {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                AppText(
                                    text = output,
                                    role = AppTextRole.BodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = appColor(AppColorRole.OnSurface),
                                )
                            }
                        }
                    }
                }
            }

            if (messageRes != 0) {
                AppAlertDialog(
                    onDismissRequest = { errorMessageRes.intValue = 0 },
                    text = { AppText(stringResource(messageRes)) },
                    confirmButton = {
                        AppTextButton(
                            text = stringResource(android.R.string.ok),
                            onClick = { errorMessageRes.intValue = 0 },
                        )
                    },
                )
            }
        }

        lifecycleScope.launch {
            viewModel.output.collect { resource ->
                val output = resource?.data?.toString()?.trim().orEmpty()
                if (output.endsWith(Starter.serviceStartedMessage)) {
                    window?.decorView?.postDelayed({
                        if (!isFinishing) finish()
                    }, 3000)
                } else if (resource?.status == Status.ERROR) {
                    var message = 0
                    when (resource.error) {
                is AdbKeyException -> {
                    message = R.string.adb_error_key_store
                }
                is NotRootedException -> {
                    message = R.string.start_with_root_failed
                }
                is SocketTimeoutException -> {
                    message = R.string.cannot_connect_port
                }
                is ConnectException -> {
                    message = R.string.cannot_connect_port
                }
                is SSLProtocolException -> {
                    message = R.string.adb_pair_required
                }
            }

                if (message != 0) {
                    errorMessageRes.intValue = message
                }
            }
            outputState.value = output
            }
        }
    }

    private var hasStarted = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasStarted) {
            hasStarted = true
            viewModel.start(
                intent.getBooleanExtra(EXTRA_IS_ROOT, false),
                intent.getIntExtra(EXTRA_PORT, 0)
            )
        }
    }

    companion object {

        const val EXTRA_IS_ROOT = "$EXTRA.IS_ROOT"
        const val EXTRA_PORT = "$EXTRA.PORT"
    }
}

class ViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext

    private val sb = StringBuilder()
    private val _output = MutableStateFlow<Resource<StringBuilder>?>(null)
    val output: StateFlow<Resource<StringBuilder>?> = _output

    private val handler = CoroutineExceptionHandler { _, throwable ->
        ShizukuStateMachine.update()
        log(error = throwable)
    }

    private var started = false

    fun start(root: Boolean, port: Int) {
        if (started) return
        started = true

        viewModelScope.launch(handler) {
            if (root) startRoot()
            else AdbStarter.startAdb(appContext, port, { log(it) })
            Starter.waitForBinder({ log(it) })
        }
    }

    private fun log(line: String? = null, error: Throwable? = null) {
        line?.let { sb.appendLine(it) }
        error?.let { sb.appendLine().appendLine(Log.getStackTraceString(it)) }

        if (error == null) _output.value = Resource.success(sb)
        else _output.value = Resource.error(error, sb)
    }

    private suspend fun startRoot() {
        log("Starting with root...\n")

        return withContext(Dispatchers.IO) {
            if (!Shell.getShell().isRoot) {
                // Try again just in case
                Shell.getCachedShell()?.close()

                if (!Shell.getShell().isRoot) {
                    Shell.getCachedShell()?.close()
                    throw NotRootedException()
                }
            }

            ShizukuStateMachine.set(ShizukuStateMachine.State.STARTING)
            suspendCancellableCoroutine { cont ->
                Shell.cmd(Starter.internalCommand)
                    .to(object : CallbackList<String?>() {
                        override fun onAddElement(s: String?) { s?.let { log(it) } }
                    })
                    .submit {
                        if (it.isSuccess) {
                            cont.resume(Unit)
                        } else {
                            cont.resumeWithException(Exception("Failed to start with root"))
                        }
                    }
            }
        }
    }
    
}
