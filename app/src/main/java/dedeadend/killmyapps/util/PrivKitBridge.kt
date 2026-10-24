package dedeadend.killmyapps.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import priv.kit.core.Privilege
import priv.kit.core.PrivilegeServerLaunchUncertainException
import priv.kit.core.command.PrivilegeCommand
import priv.kit.core.adb.PrivilegeAdbConnectionOptions
import priv.kit.core.adb.PrivilegeAdbPairingCheckStatus
import priv.kit.core.adb.PrivilegeAdbWirelessDebuggingControl
import priv.kit.core.adb.PrivilegeAdbWirelessDebuggingControlStatus
import java.io.File

/**
 * Java-callable bridge to Priv Kit (Path C self-contained privilege).
 * GPL-3.0 host; Priv Kit is Apache-2.0 (see NOTICE).
 */
object PrivKitBridge {
    private const val TAG = "PrivKitBridge"
    private const val START_TIMEOUT_MS = 120_000L
    private const val PORT_DISCOVERY_TIMEOUT_MS = 30_000L

    /** Result of [startBuiltinServiceBlocking] and related checks. */
    data class StartOutcome(
        val success: Boolean,
        val userMessage: String,
        val needsPairing: Boolean = false,
        val needsWirelessDebugging: Boolean = false,
        val needsLocalNetworkPermission: Boolean = false,
    )

    data class WirelessStatus(
        val enabled: Boolean,
        val paired: Boolean,
        val canManageWireless: Boolean,
        val writeSecureGranted: Boolean,
    )

    @JvmStatic
    fun initializeRuntime() {
        try {
            // Triggers Priv Kit runtime listener setup (public API; internal init runs here).
            Privilege.serverState.value
        } catch (t: Throwable) {
            Log.w(TAG, "initializeRuntime failed", t)
        }
    }

    @JvmStatic
    fun isServerAlive(): Boolean {
        return try {
            Privilege.pingServer()
        } catch (t: Throwable) {
            Log.w(TAG, "pingServer failed", t)
            false
        }
    }

    @JvmStatic
    fun tryConnectExisting(): Boolean {
        return try {
            Privilege.connectReadyServer()
            Privilege.pingServer()
        } catch (t: Throwable) {
            false
        }
    }

    @JvmStatic
    fun queryWirelessStatus(context: Context): WirelessStatus {
        val enabled = WirelessDebuggingHelper.isWirelessDebuggingEnabled(context)
        val writeSecure = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED
        var canManage = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val status = Privilege.createAdbManager().getWirelessDebuggingControlStatus()
                canManage = status.canManage
                return WirelessStatus(
                    enabled = enabled || status.wirelessDebuggingEnabled,
                    paired = checkPairedBlocking(),
                    canManageWireless = canManage,
                    writeSecureGranted = writeSecure,
                )
            }
        }
        return WirelessStatus(
            enabled = enabled,
            paired = checkPairedBlocking(),
            canManageWireless = canManage,
            writeSecureGranted = writeSecure,
        )
    }

    /**
     * Turn on Wireless debugging when [Manifest.permission.WRITE_SECURE_SETTINGS] is granted
     * (auto-granted by Priv Kit after the first successful server handshake).
     */
    @JvmStatic
    fun enableWirelessDebuggingIfPermitted(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                "adb_wifi_enabled",
                1,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "enableWirelessDebuggingIfPermitted failed", t)
            false
        }
    }

    /**
     * Full on-device start: connect → optional wireless enable → pairing check → startAdb.
     * Call off the main thread. [localNetworkGranted] must be true on Android 16+ before mDNS.
     */
    @JvmStatic
    fun startBuiltinServiceBlocking(
        context: Context,
        localNetworkGranted: Boolean,
    ): StartOutcome {
        initializeRuntime()
        if (isServerAlive()) {
            return StartOutcome(true, "already running")
        }
        if (tryConnectExisting()) {
            return StartOutcome(true, "connected")
        }
        if (WirelessDebuggingHelper.requiresLocalNetworkPermission() && !localNetworkGranted) {
            return StartOutcome(
                success = false,
                userMessage = "local_network_permission",
                needsLocalNetworkPermission = true,
            )
        }

        val wireless = queryWirelessStatus(context)
        if (!wireless.enabled) {
            val toggled = enableWirelessDebuggingIfPermitted(context)
            if (!toggled && !WirelessDebuggingHelper.isWirelessDebuggingEnabled(context)) {
                return StartOutcome(
                    success = false,
                    userMessage = "wireless_off",
                    needsWirelessDebugging = true,
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pairingState = runCatching {
                runBlocking { Privilege.createAdbManager().checkPairing().status }
            }.getOrNull()
            if (pairingState == PrivilegeAdbPairingCheckStatus.UNPAIRED) {
                return StartOutcome(
                    success = false,
                    userMessage = "pairing_required",
                    needsPairing = true,
                )
            }
        }

        val err = startAdbBlocking()
        if (err == null && isServerAlive()) {
            enableWirelessDebuggingIfPermitted(context)
            return StartOutcome(true, "started")
        }
        if (err == null && tryConnectExisting() && isServerAlive()) {
            enableWirelessDebuggingIfPermitted(context)
            return StartOutcome(true, "started")
        }
        val friendly = mapStartFailure(err)
        return StartOutcome(
            success = false,
            userMessage = friendly,
            needsPairing = friendly == "pairing_required" || friendly == "adb_not_authorized",
            needsWirelessDebugging = friendly == "wireless_off" || friendly == "discover_port",
        )
    }

    /** Blocking ADB wireless start. Call off the main thread. */
    @JvmStatic
    fun startAdbBlocking(): String? {
        return try {
            runBlocking {
                withTimeout(START_TIMEOUT_MS) {
                    Privilege.startAdb(startOptions())
                }
            }
            null
        } catch (t: Throwable) {
            Log.e(TAG, "startAdb failed", t)
            if (t is PrivilegeServerLaunchUncertainException) {
                runCatching {
                    if (tryConnectExisting() && isServerAlive()) return null
                }
            }
            t.message ?: t.javaClass.simpleName
        }
    }

    private fun startOptions(): PrivilegeAdbConnectionOptions =
        PrivilegeAdbConnectionOptions(
            wirelessDebuggingControl = PrivilegeAdbWirelessDebuggingControl.IF_AVAILABLE,
            disableWirelessDebuggingAfterUse = false,
            portDiscoveryTimeoutMillis = PORT_DISCOVERY_TIMEOUT_MS,
            connectRetryCount = 8,
        )

    @JvmStatic
    fun mapStartFailure(raw: String?): String {
        if (raw == null) return "unknown"
        val chain = raw
        if (chain.contains("ADB key is not authorized", ignoreCase = true) ||
            chain.contains("CERTIFICATE_UNKNOWN", ignoreCase = true) ||
            chain.contains("certificate unknown", ignoreCase = true)
        ) {
            return "adb_not_authorized"
        }
        if (chain.contains("pair") && chain.contains("fail")) return "pairing_required"
        if (chain.contains("discover") && chain.contains("port")) return "discover_port"
        if (chain.contains("wireless") && chain.contains("debug")) return "wireless_off"
        if (chain.contains("write_secure_settings")) return "wireless_off"
        return raw.take(240)
    }

    /** Pair with Wireless debugging 6-digit code. Off main thread. */
    @JvmStatic
    fun pairBlocking(code: String): String? {
        return try {
            runBlocking {
                withTimeout(60_000L) {
                    Privilege.createAdbManager().pair(pairingCode = code.trim())
                }
            }
            null
        } catch (t: Throwable) {
            Log.e(TAG, "pair failed", t)
            mapStartFailure(t.message) + ": " + (t.message ?: t.javaClass.simpleName)
        }
    }

    @JvmStatic
    fun checkPairedBlocking(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return try {
            runBlocking {
                val result = Privilege.createAdbManager().checkPairing()
                when (result.status) {
                    PrivilegeAdbPairingCheckStatus.PAIRED -> true
                    PrivilegeAdbPairingCheckStatus.UNPAIRED -> false
                    else -> result.paired
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "checkPairing failed", t)
            true
        }
    }

    @JvmStatic
    fun wirelessControlStatusBlocking(): PrivilegeAdbWirelessDebuggingControlStatus? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            Privilege.createAdbManager().getWirelessDebuggingControlStatus()
        } catch (t: Throwable) {
            Log.w(TAG, "getWirelessDebuggingControlStatus failed", t)
            null
        }
    }

    @JvmStatic
    fun nativeStarterCommandBlocking(): String {
        return try {
            runBlocking {
                Privilege.nativeStarterCommand
            }
        } catch (t: Throwable) {
            Log.e(TAG, "nativeStarterCommand failed", t)
            ""
        }
    }

    /** Persist starter command for Manual / PC-adb verification. */
    @JvmStatic
    fun dumpStarterCommand(context: Context): File? {
        return try {
            val cmd = nativeStarterCommandBlocking()
            if (cmd.isBlank()) return null
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, "privkit-starter.txt")
            file.writeText(cmd)
            file
        } catch (t: Throwable) {
            Log.e(TAG, "dumpStarterCommand failed", t)
            null
        }
    }

    /**
     * Run a shell script fragment as shell uid via Privilege.startCommand.
     * @return exit code, or -1 on transport failure
     */
    @JvmStatic
    fun runShellScriptBlocking(script: String): Int {
        return try {
            runBlocking {
                val process = Privilege.startCommand(
                    PrivilegeCommand(
                        arguments = listOf("/system/bin/sh", "-c", script),
                    ),
                )
                process.use {
                    val result = it.awaitResult()
                    if (result.stderr.isNotEmpty()) {
                        Log.d(TAG, "stderr: ${result.stderr.toString(Charsets.UTF_8)}")
                    }
                    result.exitCode
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "runShellScript failed", t)
            -1
        }
    }
}
