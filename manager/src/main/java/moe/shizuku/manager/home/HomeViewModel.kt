package moe.shizuku.manager.home

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.Manifest
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.utils.EnvironmentUtils
import moe.shizuku.manager.utils.Logger.LOGGER
import moe.shizuku.manager.utils.SettingsHelper
import moe.shizuku.manager.utils.ShizukuStateMachine
import moe.shizuku.manager.utils.ShizukuSystemApis
import rikka.lifecycle.Resource
import rikka.shizuku.Shizuku

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = getApplication<Application>().applicationContext

    private val _serviceStatus = MutableStateFlow<Resource<ServiceStatus>?>(null)
    val serviceStatus: StateFlow<Resource<ServiceStatus>?> = _serviceStatus

    private val _shouldShowBatteryOptimizationSnackbar = MutableStateFlow(false)
    val shouldShowBatteryOptimizationSnackbar: StateFlow<Boolean> = _shouldShowBatteryOptimizationSnackbar

    private val _shouldShowRebootDialog = MutableStateFlow(false)
    val shouldShowRebootDialog: StateFlow<Boolean> = _shouldShowRebootDialog

    private val _shouldShowUninstallDialog = MutableStateFlow(false)
    val shouldShowUninstallDialog: StateFlow<Boolean> = _shouldShowUninstallDialog

    private fun load(): ServiceStatus {
        // In certain cases when user re-installs Shizuku with different package name (e.g., when using stealth mode), the system doesn't recognize the Shizuku permission.
        // As a result, all permission operations (check/grant/revoke) will fail.
        // This is fixed by rebooting the device.
        // Run getPermissionGroupInfo() to trigger the exception. Then catch it and show a dialog prompting the user to reboot their device.
        try {
            val permissionGroup = appContext.packageManager.getPermissionGroupInfo(Manifest.permission_group.API, 0)
            val permission = appContext.packageManager.getPermissionInfo(Manifest.permission.API_V23, 0)
            if (permission.packageName != appContext.packageName) {
                _shouldShowUninstallDialog.value = true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            _shouldShowRebootDialog.value = true
        }

        if (!ShizukuStateMachine.isRunning()) {
            return ServiceStatus()
        }

        val uid = Shizuku.getUid()
        val apiVersion = Shizuku.getVersion()
        val patchVersion = Shizuku.getServerPatchVersion().let { if (it < 0) 0 else it }
        val seContext = if (apiVersion >= 6) {
            try {
                Shizuku.getSELinuxContext()
            } catch (tr: Throwable) {
                LOGGER.w(tr, "getSELinuxContext")
                null
            }
        } else null
        val permissionTest =
            Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED

        // Before a526d6bb, server will not exit on uninstall, manager installed later will get not permission
        // Run a random remote transaction here, report no permission as not running
        ShizukuSystemApis.checkPermission(Manifest.permission.API_V23, appContext.packageName, 0)
        return ServiceStatus(uid, apiVersion, patchVersion, seContext, permissionTest)
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = load()
                _serviceStatus.value = Resource.success(status)
            } catch (e: CancellationException) {

            } catch (e: Throwable) {
                _serviceStatus.value = Resource.error(e, ServiceStatus())
            }
        }
    }

    fun checkBatteryOptimization() {
        if (EnvironmentUtils.isTelevision()) return
        if (!ShizukuSettings.getStartOnBoot(appContext) && !ShizukuSettings.getWatchdog()) return
        _shouldShowBatteryOptimizationSnackbar.value =
            !SettingsHelper.isIgnoringBatteryOptimizations(appContext)
    }

}
