package moe.shizuku.manager.home

import android.annotation.SuppressLint
import android.app.Application
import android.app.Dialog
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.AndroidViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.ConnectException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbInvalidPairingCodeException
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.AdbKeyException
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbPairingClient
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import moe.shizuku.manager.utils.SettingsHelper

@RequiresApi(VERSION_CODES.R)
class AdbPairDialogFragment : DialogFragment() {

    private val viewModel: ViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val multiWindow = requireActivity().isInMultiWindowMode ||
            (requireActivity().window?.decorView?.display?.displayId ?: -1) > 0

        val content = ComposeView(context).apply {
            setContent {
                AdbPairDialogContent(
                    viewModel = viewModel,
                    multiWindow = multiWindow,
                    onDismiss = { dismissAllowingStateLoss() },
                    onOpenDevelopmentSettings = {
                        SettingsHelper.launchOrHighlightWirelessDebugging(context)
                    },
                )
            }
        }

        return MaterialAlertDialogBuilder(context)
            .setView(content)
            .create()
            .apply { setCanceledOnTouchOutside(false) }
    }

    fun show(fragmentManager: FragmentManager) {
        if (fragmentManager.isStateSaved) return
        show(fragmentManager, javaClass.simpleName)
    }
}

@Composable
private fun AdbPairDialogContent(
    viewModel: ViewModel,
    multiWindow: Boolean,
    onDismiss: () -> Unit,
    onOpenDevelopmentSettings: () -> Unit,
) {
    val context = LocalContext.current
    val discoveryPort by viewModel.port.collectAsState()
    val result by viewModel.result.collectAsState()

    var pairingCode by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("") }
    var portError by remember { mutableStateOf<String?>(null) }
    var codeError by remember { mutableStateOf<String?>(null) }

    // -1 means "still searching", null means the discovery has not reported yet.
    val discovering = discoveryPort == -1
    val canPair = discoveryPort != null && discoveryPort != -1

    LaunchedEffect(discoveryPort) {
        discoveryPort?.let { portText = it.toString() }
    }

    LaunchedEffect(result) {
        when (val r = result) {
            is PairingResult.Success -> onDismiss()
            is PairingResult.Failure -> when (val error = r.error) {
                is ConnectException -> portError = context.getString(R.string.cannot_connect_port)
                is AdbInvalidPairingCodeException -> codeError = context.getString(R.string.paring_code_is_wrong)
                is AdbKeyException ->
                    Toast.makeText(context, context.getString(R.string.adb_error_key_store), Toast.LENGTH_LONG)
                        .apply { setGravity(android.view.Gravity.CENTER, 0, 0) }.show()
                else -> Unit
            }
            null -> Unit
        }
    }

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(
                if (discovering) R.string.dialog_adb_pairing_discovery else R.string.dialog_adb_pairing_title
            ),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (!discovering) {
            Text(
                text = stringResource(
                    if (multiWindow) R.string.dialog_adb_pairing_message
                    else R.string.adb_pairing_requires_multi_window
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (!multiWindow) {
                Text(
                    text = stringResource(R.string.adb_pairing_requires_multi_window_reason),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (!discovering) {
            OutlinedTextField(
                value = pairingCode,
                onValueChange = {
                    pairingCode = it.filter { ch -> ch.isDigit() }.take(6)
                    codeError = null
                },
                label = { Text(stringResource(R.string.dialog_adb_pairing_paring_code)) },
                singleLine = true,
                isError = codeError != null,
                supportingText = codeError?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }

        if (portError != null) {
            OutlinedTextField(
                value = portText,
                onValueChange = {
                    portText = it.filter { ch -> ch.isDigit() }.take(5)
                    portError = null
                },
                label = { Text(stringResource(R.string.dialog_adb_port)) },
                singleLine = true,
                isError = true,
                supportingText = { Text(portError!!) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
            if (discovering) {
                TextButton(onClick = onOpenDevelopmentSettings) {
                    Text(stringResource(R.string.development_settings))
                }
            }
            if (canPair) {
                TextButton(onClick = {
                    val port = portText.toIntOrNull()
                    if (port == null || port > 65535 || port < 1) {
                        portError = context.getString(R.string.dialog_adb_invalid_port)
                        return@TextButton
                    }
                    viewModel.run(port, pairingCode)
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

sealed interface PairingResult {
    data object Success : PairingResult
    data class Failure(val error: Throwable) : PairingResult
}

@SuppressLint("NewApi")
class ViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext

    private val _result = MutableStateFlow<PairingResult?>(null)
    val result: StateFlow<PairingResult?> = _result

    private val _port = MutableStateFlow<Int?>(null)
    val port: StateFlow<Int?> = _port

    @Volatile
    private var resolvedHost: String = "127.0.0.1"

    private val adbMdns: AdbMdns = AdbMdns(appContext, AdbMdns.TLS_PAIRING) {
        if (it.second > 0) resolvedHost = it.first
        _port.value = it.second
    }

    init {
        adbMdns.start()
    }

    // GlobalScope is deliberate here: the pairing work must outlive the dialog.
    @OptIn(DelicateCoroutinesApi::class)
    fun run(port: Int, password: String) {
        GlobalScope.launch(Dispatchers.IO) {
            val host = resolvedHost

            val key = try {
                AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")
            } catch (e: Throwable) {
                e.printStackTrace()
                _result.value = PairingResult.Failure(AdbKeyException(e))
                return@launch
            }

            AdbPairingClient(host, port, password, key).runCatching {
                start()
            }.onFailure {
                _result.value = PairingResult.Failure(it)
                it.printStackTrace()
            }.onSuccess {
                if (it) _result.value = PairingResult.Success
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        adbMdns.stop()
    }
}
