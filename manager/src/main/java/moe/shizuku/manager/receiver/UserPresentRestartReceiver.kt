package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.ktx.setComponentEnabled
import moe.shizuku.manager.worker.AdbStartWorker

/**
 * Resumes a boot start that was parked because the device was still locked.
 *
 * Declared disabled in the manifest and switched on only while a start is
 * waiting for the unlock, so a receiver that fires once per boot cannot leak:
 * [AdbStartWorker] disables it as soon as it stops waiting.
 */
class UserPresentRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_USER_PRESENT != intent.action) return

        setEnabled(context, false)
        AdbStartWorker.enqueue(context)
    }

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            val component = ComponentName(context, UserPresentRestartReceiver::class.java)
            context.packageManager.setComponentEnabled(component, enabled)
        }
    }
}
