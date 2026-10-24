package dedeadend.killmyapps.util;

import java.util.List;

import dedeadend.killmyapps.App;
import dedeadend.killmyapps.model.AppInfo;

/**
 * Kill apps through the in-process Priv Kit privileged server (Path C).
 * Does not require moe.shizuku.privileged.api.
 */
public final class PrivKitUtils {

    private PrivKitUtils() {
    }

    public static boolean checkReady() {
        PrivKitBridge.initializeRuntime();
        if (PrivKitBridge.isServerAlive()) {
            return true;
        }
        return PrivKitBridge.tryConnectExisting();
    }

    public static boolean killApp(String pkgName, int level) {
        if (!checkReady()) return false;
        String script = buildKillScript(pkgName, level);
        return PrivKitBridge.runShellScriptBlocking(script) == 0;
    }

    public static int killListOfApps(List<AppInfo> appList, int level) {
        if (!checkReady()) return -1;
        StringBuilder sb = new StringBuilder();
        boolean killSelf = false;
        for (AppInfo app : appList) {
            String pkg = app.getPkgName();
            if (pkg.equals(App.context.getPackageName())) {
                killSelf = true;
                continue;
            }
            sb.append(buildKillScript(pkg, level));
        }
        int code = PrivKitBridge.runShellScriptBlocking(sb.toString());
        if (code < 0) return -1;
        return killSelf ? 1 : 0;
    }

    private static String buildKillScript(String pkgName, int level) {
        switch (level) {
            case 0:
                return "am kill " + pkgName + "\n"
                        + "am set-inactive " + pkgName + " true\n";
            case 2:
                return "am force-stop " + pkgName + "\n"
                        + "cmd package suspend " + pkgName
                        + " 2>/dev/null && cmd package unsuspend " + pkgName + " 2>/dev/null\n";
            case 1:
            default:
                return "am force-stop " + pkgName + "\n";
        }
    }
}
