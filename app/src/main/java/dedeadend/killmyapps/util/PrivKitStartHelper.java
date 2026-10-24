package dedeadend.killmyapps.util;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import dedeadend.killmyapps.App;
import dedeadend.killmyapps.MainActivity;
import dedeadend.killmyapps.R;

/**
 * Coordinates built-in Priv Kit start from UI (permissions + background work).
 */
public final class PrivKitStartHelper {

    public interface Callback {
        void onFinished(boolean success, @NonNull String message);
    }

    private PrivKitStartHelper() {
    }

    public static void startFromActivity(
            @NonNull Activity activity,
            @Nullable Callback callback
    ) {
        Runnable work = () -> runStartBlocking(activity, callback);
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).runWithLocalNetworkPermission(work);
        } else {
            work.run();
        }
    }

    private static void runStartBlocking(@NonNull Activity activity, @Nullable Callback callback) {
        boolean localOk = !(activity instanceof MainActivity)
                || PrivKitPermissions.hasLocalNetworkPermission(activity);
        App.executorService.execute(() -> {
            PrivKitBridge.StartOutcome outcome = PrivKitBridge.startBuiltinServiceBlocking(
                    activity.getApplicationContext(),
                    localOk
            );
            String msg = resolveMessage(activity, outcome);
            App.handler.post(() -> {
                if (callback != null) {
                    callback.onFinished(outcome.getSuccess(), msg);
                }
            });
        });
    }

    @NonNull
    public static String resolveMessage(
            @NonNull Activity activity,
            @NonNull PrivKitBridge.StartOutcome outcome
    ) {
        String code = outcome.getUserMessage();
        if ("already running".contentEquals(code) || "connected".contentEquals(code)
                || "started".contentEquals(code)) {
            return activity.getString(R.string.guide_start_ok);
        }
        if (outcome.getNeedsLocalNetworkPermission()) {
            return activity.getString(R.string.guide_local_network_required);
        }
        if (outcome.getNeedsWirelessDebugging()) {
            if ("wireless_off".contentEquals(code)) {
                return activity.getString(R.string.guide_wireless_off);
            }
            if ("discover_port".contentEquals(code)) {
                return activity.getString(R.string.guide_discover_port_fail);
            }
            return activity.getString(R.string.guide_wireless_off);
        }
        if (outcome.getNeedsPairing() || "pairing_required".contentEquals(code)
                || "adb_not_authorized".contentEquals(code)) {
            return activity.getString(R.string.guide_pairing_required);
        }
        if ("unknown".contentEquals(code)) {
            return activity.getString(R.string.guide_start_fail, "unknown");
        }
        return activity.getString(R.string.guide_start_fail, code);
    }

    @StringRes
    public static int pairingHintRes() {
        return R.string.guide_pairing_code_honest;
    }
}
