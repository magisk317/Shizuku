package moe.shizuku.manager.worker

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.io.EOFException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.AdbStarter
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.receiver.ShizukuReceiverStarter
import moe.shizuku.manager.receiver.ShizukuReceiverStarter.WorkerState
import moe.shizuku.manager.receiver.ShizukuReceiverStarter.updateNotification
import moe.shizuku.manager.receiver.UserPresentRestartReceiver
import moe.shizuku.manager.settings.BugReportDialogActivity
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.UserHandleCompat

class AdbStartWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val adbWirelessHelper = AdbWirelessHelper()

    override suspend fun doWork(): Result {
        try {
            updateNotification(applicationContext, WorkerState.RUNNING)

            if (UserHandleCompat.myUserId() > 0 || ShizukuStateMachine.isRunning()) {
                UserPresentRestartReceiver.setEnabled(applicationContext, false)
                return Result.success()
            }

            // A locked device cannot authorize the wireless debugging network, so
            // park the start until the unlock instead of spending the retries on an
            // authorization dialog nobody can see.
            val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (km.isDeviceLocked || km.isKeyguardLocked) {
                Log.i(AppConstants.TAG, "Wireless boot start waiting for unlock")
                UserPresentRestartReceiver.setEnabled(applicationContext, true)
                updateNotification(applicationContext, WorkerState.AWAITING_UNLOCK)
                return Result.success()
            }
            UserPresentRestartReceiver.setEnabled(applicationContext, false)

            val cr = applicationContext.contentResolver

            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)

            val tcpPort = EnvironmentUtils.getAdbTcpPort()
            if (tcpPort > 0 && !ShizukuSettings.getTcpMode()) {
                AdbStarter.stopTcp(applicationContext, tcpPort)
            }

            // A port that is already known skips both mDNS discovery and the
            // UNMETERED constraint, which is what makes a repeat boot start finish
            // before the user notices anything happened.
            val port = adbWirelessHelper.getStartableAdbPort() ?: callbackFlow {
                val adbMdns = AdbMdns(applicationContext, AdbMdns.TLS_CONNECT) { p ->
                    if (p.second > 0) trySend(p.second)
                }

                var awaitingAuth = false
                var timeoutJob: Job? = null
                var unlockReceiver: BroadcastReceiver? = null

                fun startDiscoveryWithTimeout() {
                    adbMdns.start()
                    timeoutJob?.cancel()
                    timeoutJob = launch {
                        delay(15_000)
                        close(TimeoutException("Timed out during mDNS port discovery"))
                    }
                }

                fun handleAuth() {
                    val km = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (km.isKeyguardLocked) {
                        val notification = ShizukuReceiverStarter.buildNotification(
                            applicationContext,
                            null
                        )
                        val foregroundInfo = ForegroundInfo(
                            ShizukuReceiverStarter.NOTIFICATION_ID,
                            notification
                        )
                        setForegroundAsync(foregroundInfo)

                        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
                        unlockReceiver = object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                if (intent.action == Intent.ACTION_USER_PRESENT) {
                                    context.unregisterReceiver(this)
                                    unlockReceiver = null
                                    Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                                }
                            }
                        }
                        applicationContext.registerReceiver(unlockReceiver, filter)
                    } else awaitingAuth = true
                    timeoutJob?.cancel()
                    adbMdns.stop()
                }

                val observer = object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        when (Settings.Global.getInt(cr, "adb_wifi_enabled", 0)) {
                            0 -> if (awaitingAuth) {
                                close(SecurityException("Network is not authorized for wireless debugging"))
                            } else handleAuth()
                            1 -> startDiscoveryWithTimeout()
                        }
                    }
                }

                Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                cr.registerContentObserver(Settings.Global.getUriFor("adb_wifi_enabled"), false, observer)
                startDiscoveryWithTimeout()

                awaitClose {
                    adbMdns.stop()
                    timeoutJob?.cancel()
                    cr.unregisterContentObserver(observer)
                    unlockReceiver?.let { applicationContext.unregisterReceiver(it) }
                }
            }.first()

            AdbStarter.startAdb(applicationContext, port)
            Starter.waitForBinder()

            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(ShizukuReceiverStarter.NOTIFICATION_ID)

            return Result.success()
        } catch (e: CancellationException) {
            val state = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                WorkerState.AWAITING_RETRY
            } else {
                when (stopReason) {
                    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> WorkerState.AWAITING_WIFI
                    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> WorkerState.STOPPED
                    else -> WorkerState.AWAITING_RETRY
                }
            }
            updateNotification(applicationContext, state)

            throw e
        } catch (e: Exception) {
            val ignored = listOf(
                EOFException::class,
                SecurityException::class,
                TimeoutException::class
            )
            if (ignored.none { it.isInstance(e) }) showErrorNotification(applicationContext, e)

            if (ShizukuStateMachine.update() == ShizukuStateMachine.State.RUNNING) {
                return Result.success()
            }

            // Retrying forever keeps a device that can never start (no Wi-Fi, no
            // WRITE_SECURE_SETTINGS, adbd refusing the port) awake for nothing, so
            // give up and let the notification carry the reason.
            if (runAttemptCount >= MAX_ATTEMPTS) {
                Log.w(AppConstants.TAG, "Wireless boot start gave up after $runAttemptCount attempts")
                updateNotification(applicationContext, WorkerState.STOPPED)
                return Result.failure()
            }

            updateNotification(applicationContext, WorkerState.AWAITING_RETRY)
            return Result.retry()
        }
    }

    private fun showErrorNotification(context: Context, e: Exception) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wadb_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val nb = NotificationCompat.Builder(context, CHANNEL_ID)

        val msgNotif = "$e. ${context.getString(R.string.wadb_error_notify_dev)}"

        val intent = Intent(context, BugReportDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = nb
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(context.getString(R.string.wadb_error_title))
            .setContentText(msgNotif)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msgNotif))
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val MAX_ATTEMPTS = 5

        fun enqueue(context: Context) {
            // Boot, the unlock receiver and the notification's retry action can all
            // arrive together. REPLACE would cancel an ADB start that is already in
            // flight and begin it from scratch, so leave a running worker alone.
            if (isRunning(context)) {
                Log.i(AppConstants.TAG, "Wireless boot start already running, skip enqueue")
                return
            }

            // No known port means discovery is needed, and discovery needs a
            // network the system will not meter.
            val constraints = if (AdbWirelessHelper().getStartableAdbPort() == null) {
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            } else {
                Constraints.NONE
            }

            val request = OneTimeWorkRequestBuilder<AdbStartWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "adb_start_worker",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun isRunning(context: Context): Boolean = try {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("adb_start_worker")
                .get(1, TimeUnit.SECONDS)
                .any { it.state == WorkInfo.State.RUNNING }
        } catch (_: Exception) {
            false
        }

        const val CHANNEL_ID = "AdbStartWorker"
        const val NOTIFICATION_ID = 1448
    }
}
