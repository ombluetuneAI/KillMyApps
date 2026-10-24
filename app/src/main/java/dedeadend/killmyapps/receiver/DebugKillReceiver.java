package dedeadend.killmyapps.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import dedeadend.killmyapps.App;
import dedeadend.killmyapps.BuildConfig;
import dedeadend.killmyapps.data.Killer;

/**
 * Debug-only kill trigger for Path C verification (same Killer/PrivKit path as UI).
 * adb shell am broadcast -a dedeadend.killmyapps.oneshot.ACTION_DEBUG_KILL --es pkg fun.upup.musicfree
 */
public class DebugKillReceiver extends BroadcastReceiver {
    private static final String TAG = "DebugKillReceiver";
    public static final String ACTION = BuildConfig.APPLICATION_ID + ".ACTION_DEBUG_KILL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BuildConfig.DEBUG) {
            Log.w(TAG, "ignored: not debug build");
            return;
        }
        String pkg = intent != null ? intent.getStringExtra("pkg") : null;
        if (pkg == null || pkg.isEmpty()) {
            Log.e(TAG, "missing pkg extra");
            return;
        }
        App.executorService.execute(() -> {
            boolean ok = Killer.killApp(pkg);
            Log.i(TAG, "kill " + pkg + " => " + ok);
        });
    }
}
