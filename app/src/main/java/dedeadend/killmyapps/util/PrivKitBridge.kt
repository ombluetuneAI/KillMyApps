package dedeadend.killmyapps.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import priv.kit.core.Privilege
import priv.kit.core.command.PrivilegeCommand
import java.io.File

/**
 * Java-callable bridge to Priv Kit (Path C self-contained privilege).
 * GPL-3.0 host; Priv Kit is Apache-2.0 (see NOTICE).
 */
object PrivKitBridge {
    private const val TAG = "PrivKitBridge"
    private const val START_TIMEOUT_MS = 90_000L

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

    /** Blocking ADB wireless start. Call off the main thread. */
    @JvmStatic
    fun startAdbBlocking(): String? {
        return try {
            runBlocking {
                withTimeout(START_TIMEOUT_MS) {
                    Privilege.startAdb()
                }
            }
            null
        } catch (t: Throwable) {
            Log.e(TAG, "startAdb failed", t)
            t.message ?: t.javaClass.simpleName
        }
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
            t.message ?: t.javaClass.simpleName
        }
    }

    @JvmStatic
    fun checkPairedBlocking(): Boolean {
        return try {
            runBlocking {
                Privilege.createAdbManager().checkPairing().paired
            }
        } catch (t: Throwable) {
            false
        }
    }

    @JvmStatic
    fun nativeStarterCommandBlocking(): String {
        return try {
            runBlocking {
                // first access may inspect APKs
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
