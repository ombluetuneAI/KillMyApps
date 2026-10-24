package dedeadend.killmyapps.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Deep-links and reads system Wireless debugging state (Path C).
 */
public final class WirelessDebuggingHelper {

    /** Settings extra: scroll to Wireless debugging row (AOSP / many OEMs). */
    public static final String FRAGMENT_ARGS_KEY = ":settings:fragment_args_key";
    public static final String FRAGMENT_TOGGLE_ADB_WIRELESS = "toggle_adb_wireless";

    private static final String SETTING_ADB_WIFI_ENABLED = "adb_wifi_enabled";

    private WirelessDebuggingHelper() {
    }

    public static boolean isWirelessDebuggingEnabled(@NonNull Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    SETTING_ADB_WIFI_ENABLED,
                    0
            ) == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NonNull
    public static Intent wirelessDebuggingSettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(FRAGMENT_ARGS_KEY, FRAGMENT_TOGGLE_ADB_WIRELESS);
        return intent;
    }

    @NonNull
    public static Intent developerOptionsIntent() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static void openWirelessDebugging(@NonNull Context context) {
        try {
            context.startActivity(wirelessDebuggingSettingsIntent());
        } catch (ActivityNotFoundException e) {
            try {
                context.startActivity(developerOptionsIntent());
            } catch (ActivityNotFoundException ignored) {
                // caller shows toast
            }
        }
    }

    /**
     * Android 16+ needs runtime local-network access for mDNS ADB port discovery (Priv Kit).
     */
    public static boolean requiresLocalNetworkPermission() {
        return Build.VERSION.SDK_INT >= 36;
    }

    @Nullable
    public static String summarizeWirelessState(@NonNull Context context, boolean paired) {
        boolean on = isWirelessDebuggingEnabled(context);
        StringBuilder sb = new StringBuilder();
        sb.append(on ? "Wireless debugging: ON" : "Wireless debugging: OFF");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sb.append(" · Pairing: ").append(paired ? "OK" : "needed");
        }
        return sb.toString();
    }
}
