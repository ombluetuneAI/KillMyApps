package dedeadend.killmyapps.util;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Runtime permissions required for in-app ADB (mDNS) on newer Android versions.
 */
public final class PrivKitPermissions {

    public static final int REQUEST_LOCAL_NETWORK = 41001;

    private PrivKitPermissions() {
    }

    public static boolean hasLocalNetworkPermission(@NonNull Activity activity) {
        if (!WirelessDebuggingHelper.requiresLocalNetworkPermission()) {
            return true;
        }
        return ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean shouldRequestLocalNetwork(@NonNull Activity activity) {
        if (!WirelessDebuggingHelper.requiresLocalNetworkPermission()) {
            return false;
        }
        return !hasLocalNetworkPermission(activity);
    }

    public static void requestLocalNetwork(@NonNull Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK},
                REQUEST_LOCAL_NETWORK
        );
    }
}
