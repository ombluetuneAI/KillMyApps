package dedeadend.killmyapps.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import rikka.shizuku.Shizuku;

/**
 * Privilege state machine for Path C (built-in Priv Kit) with optional Shizuku fallback.
 * Source: local fork of KillMyApps (GPL-3.0).
 */
public final class PrivilegeStatus {

    public enum State {
        /** Built-in server not running (or Root missing). */
        NOT_RUNNING,
        /** Official Shizuku package missing (only for engine Shizuku/Shizuku+). */
        NOT_INSTALLED,
        /** Shizuku binder alive but not authorized. */
        NOT_AUTHORIZED,
        /** Ready to kill. */
        READY,
        ROOT_MODE
    }

    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    public static final String SHIZUKU_DOWNLOAD_URL =
            "https://github.com/RikkaApps/Shizuku/releases";

    /** Killer engine: 1 Root, 2 Shizuku, 3 Shizuku+, 4 Built-in (Priv Kit). */
    public static final int MODE_ROOT = 1;
    public static final int MODE_SHIZUKU = 2;
    public static final int MODE_SHIZUKU_PLUS = 3;
    public static final int MODE_BUILTIN = 4;

    private PrivilegeStatus() {
    }

    public static boolean isBuiltinMode(int killerMode) {
        return killerMode == MODE_BUILTIN;
    }

    public static boolean isShizukuInstalled(@NonNull Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isShizukuBinderAlive() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isShizukuAuthorized() {
        try {
            return isShizukuBinderAlive()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isBuiltinReady() {
        return PrivKitUtils.checkReady();
    }

    /**
     * Resolve status for current killer engine.
     */
    @NonNull
    public static State resolve(@NonNull Context context, int killerMode) {
        if (killerMode == MODE_ROOT) {
            return SuUtils.checkSU() ? State.ROOT_MODE : State.NOT_RUNNING;
        }
        if (killerMode == MODE_BUILTIN) {
            return isBuiltinReady() ? State.READY : State.NOT_RUNNING;
        }
        // Shizuku / Shizuku+
        if (!isShizukuInstalled(context)) {
            // Optional: if built-in happens to be up, still not for this engine — keep honest
            return State.NOT_INSTALLED;
        }
        if (!isShizukuBinderAlive()) {
            return State.NOT_RUNNING;
        }
        if (!isShizukuAuthorized()) {
            return State.NOT_AUTHORIZED;
        }
        return State.READY;
    }

    public static boolean canKill(@NonNull Context context, int killerMode) {
        State s = resolve(context, killerMode);
        return s == State.READY || s == State.ROOT_MODE;
    }

    @Nullable
    public static Intent openShizukuAppIntent(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return launch;
        }
        return null;
    }

    @NonNull
    public static Intent downloadShizukuIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @NonNull
    public static Intent developerOptionsIntent() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    @NonNull
    public static String engineLabel(int mode) {
        switch (mode) {
            case MODE_ROOT:
                return "Root";
            case MODE_SHIZUKU_PLUS:
                return "Shizuku+";
            case MODE_BUILTIN:
                return "Built-in";
            case MODE_SHIZUKU:
            default:
                return "Shizuku";
        }
    }
}
