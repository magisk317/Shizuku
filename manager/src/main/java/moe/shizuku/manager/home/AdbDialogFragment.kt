package moe.shizuku.manager.home

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.utils.SettingsPage
import moe.shizuku.manager.adb.AdbMdns
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

@RequiresApi(Build.VERSION_CODES.R)
class AdbDialogFragment : DialogFragment() {

    private lateinit var adbMdns: AdbMdns
    private val port = MutableLiveData<Int>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        adbMdns = AdbMdns(context, AdbMdns.TLS_CONNECT) {
            port.postValue(it.second)
        }

        val content = ComposeView(context).apply {
            setContent {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    AppText(
                        text = stringResource(R.string.dialog_adb_discovery_message),
                        role = AppTextRole.Body,
                    )
                    Spacer(Modifier.height(12.dp))
                    AppText(
                        text = stringResource(R.string.dialog_adb_discovery_message_toggle_wireless_debugging),
                        role = AppTextRole.Body,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_adb_discovery)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.development_settings, null)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener { onDialogShow(dialog) }
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        adbMdns.stop()
    }

    private fun onDialogShow(dialog: AlertDialog) {
        adbMdns.start()
        val context = dialog.context
        if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
            Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            SettingsPage.Developer.HighlightWirelessDebugging.launch(context)
        }

        port.observe(this) {
            if (it > 65535 || it < 1) return@observe
            port.removeObservers(this)
            startAndDismiss(it)
        }
    }

    private fun startAndDismiss(port: Int) {
        val intent = Intent(context, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_PORT, port)
        }
        requireContext().startActivity(intent)

        dismissAllowingStateLoss()
    }

    fun show(fragmentManager: FragmentManager) {
        if (fragmentManager.isStateSaved) return
        show(fragmentManager, javaClass.simpleName)
    }
}
