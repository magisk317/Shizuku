package moe.shizuku.manager.management

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.authorization.AuthorizationManager
import rikka.lifecycle.Resource

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext

    private val _packages = MutableStateFlow<Resource<List<PackageInfo>>?>(null)
    val packages: StateFlow<Resource<List<PackageInfo>>?> = _packages

    private val _grantedCount = MutableStateFlow<Resource<Int>?>(null)
    val grantedCount: StateFlow<Resource<Int>?> = _grantedCount

    fun load(onlyCount: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list: MutableList<PackageInfo> = ArrayList()
                var count = 0
                for (pi in AuthorizationManager.getPackages(
                    exclude = listOf(appContext.packageName)
                )) {
                    list.add(pi)
                    if (AuthorizationManager.granted(pi.packageName, pi.applicationInfo!!.uid)) count++
                }
                if (!onlyCount) _packages.value = Resource.success(list)
                _grantedCount.value = Resource.success(count)
            } catch (e: CancellationException) {

            } catch (e: Throwable) {
                _packages.value = Resource.error(e, null)
                _grantedCount.value = Resource.error(e, 0)
            }
        }
    }
    
}
