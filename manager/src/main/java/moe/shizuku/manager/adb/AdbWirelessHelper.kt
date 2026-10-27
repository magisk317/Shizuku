package moe.shizuku.manager.adb

import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.EnvironmentUtils

/**
 * Resolves which ADB port a wireless start should use.
 *
 * Ported from HSSkyBoy's boot-start line. Knowing the port before the worker
 * starts lets [moe.shizuku.manager.worker.AdbStartWorker] skip mDNS discovery
 * and drop the UNMETERED network constraint, so a device that was already
 * paired finishes its boot start without waiting for a network callback.
 */
class AdbWirelessHelper {

    /**
     * The port the user explicitly configured, or null when they never set one.
     *
     * [ShizukuSettings.getTcpPort] answers 5555 when the key is absent, which is
     * not the same thing as a deliberate choice, so the raw preference is read
     * here instead.
     */
    fun getConfiguredTcpipPort(): Int? {
        val raw = ShizukuSettings.getPreferences()
            .getString(ShizukuSettings.Keys.KEY_TCP_PORT, null)
            ?.trim()
            ?: return null
        val port = raw.toIntOrNull() ?: return null
        return port.takeIf { it in 1..65535 }
    }

    /**
     * The port a wireless start can connect to without discovery, or null when
     * mDNS is still needed.
     *
     * The configured port is only a usable entry point while TCP mode is on:
     * with it off the flow stops the TCP listener after starting, so nothing
     * would be listening there next boot.
     */
    fun getStartableAdbPort(): Int? {
        val systemPort = EnvironmentUtils.getAdbTcpPort()
        if (systemPort in 1..65535) return systemPort
        if (ShizukuSettings.getTcpMode()) return getConfiguredTcpipPort()
        return null
    }
}
