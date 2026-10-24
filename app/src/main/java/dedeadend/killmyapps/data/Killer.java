package dedeadend.killmyapps.data;

import android.app.ActivityManager;
import android.content.Context;

import java.util.List;

import dedeadend.killmyapps.App;
import dedeadend.killmyapps.model.AppInfo;
import dedeadend.killmyapps.util.PrivilegeStatus;
import dedeadend.killmyapps.util.PrivKitUtils;
import dedeadend.killmyapps.util.ShizukuPlusUtils;
import dedeadend.killmyapps.util.ShizukuUtils;
import dedeadend.killmyapps.util.SuUtils;

public class Killer {

    private static int killerMode = PrivilegeStatus.MODE_BUILTIN;
    private static int killLevel = 1;

    private static boolean checkAccess() {
        killerMode = App.settings.getInt(App.KILLER_MODE, PrivilegeStatus.MODE_BUILTIN);
        killLevel = App.settings.getInt(App.KILLER_LEVEL, 1);
        if (killerMode == PrivilegeStatus.MODE_ROOT)
            return SuUtils.checkSU();
        else if (killerMode == PrivilegeStatus.MODE_BUILTIN)
            return PrivKitUtils.checkReady();
        else if (killerMode == PrivilegeStatus.MODE_SHIZUKU) {
            if (!ShizukuUtils.checkShizuku()) {
                ShizukuUtils.requestPermission();
                return false;
            }
            return true;
        } else if (killerMode == PrivilegeStatus.MODE_SHIZUKU_PLUS) {
            if (!ShizukuPlusUtils.checkShizuku()) {
                ShizukuPlusUtils.requestPermission();
                return false;
            }
            return true;
        } else
            return false;
    }

    public static boolean killListOfApps(List<AppInfo> appList) {
        if (!checkAccess())
            return false;

        int result;
        if (killerMode == PrivilegeStatus.MODE_ROOT)
            result = SuUtils.killListOfApps(appList, killLevel);
        else if (killerMode == PrivilegeStatus.MODE_BUILTIN)
            result = PrivKitUtils.killListOfApps(appList, killLevel);
        else if (killerMode == PrivilegeStatus.MODE_SHIZUKU)
            result = ShizukuUtils.killListOfApps(appList, killLevel);
        else if (killerMode == PrivilegeStatus.MODE_SHIZUKU_PLUS)
            result = ShizukuPlusUtils.killListOfApps(appList, killLevel);
        else
            return false;

        if (result == 0)
            return true;
        else if (result == 1) {
            selfDestruct();
            return true;
        } else
            return false;
    }

    public static boolean killApp(String pkgName) {
        if (pkgName.equals(App.context.getPackageName()))
            selfDestruct();

        if (!checkAccess())
            return false;

        if (killerMode == PrivilegeStatus.MODE_ROOT)
            return SuUtils.killApp(pkgName, killLevel);
        else if (killerMode == PrivilegeStatus.MODE_BUILTIN)
            return PrivKitUtils.killApp(pkgName, killLevel);
        else if (killerMode == PrivilegeStatus.MODE_SHIZUKU)
            return ShizukuUtils.killApp(pkgName, killLevel);
        else if (killerMode == PrivilegeStatus.MODE_SHIZUKU_PLUS)
            return ShizukuPlusUtils.killApp(pkgName, killLevel);
        else
            return false;
    }

    private static void selfDestruct() {
        try {
            ActivityManager activityManager = (ActivityManager) App.context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                List<ActivityManager.AppTask> appTasks = activityManager.getAppTasks();
                if (appTasks != null) {
                    for (ActivityManager.AppTask task : appTasks) {
                        task.finishAndRemoveTask();
                    }
                }
            }
        } finally {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
