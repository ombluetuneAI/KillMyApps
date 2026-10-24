package dedeadend.killmyapps.ui.home;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import dedeadend.killmyapps.App;
import dedeadend.killmyapps.R;
import dedeadend.killmyapps.util.CapsuleToast;
import dedeadend.killmyapps.util.PrivKitBridge;
import dedeadend.killmyapps.util.PrivilegeStatus;
import dedeadend.killmyapps.util.ShizukuUtils;

/**
 * Privilege setup guide: Path C built-in (pair + startAdb) or Shizuku fallback.
 */
public class ShizukuGuideDialog extends Dialog {

    private final PrivilegeStatus.State initialState;
    private Runnable onDismissRefresh;

    public ShizukuGuideDialog(@NonNull Context context, PrivilegeStatus.State state) {
        super(context);
        this.initialState = state;
    }

    public void setOnDismissRefresh(Runnable r) {
        this.onDismissRefresh = r;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_shizuku_guide);
        getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        setCancelable(true);

        TextView title = findViewById(R.id.guide_title);
        TextView body = findViewById(R.id.guide_body);
        Button primary = findViewById(R.id.guide_primary_btn);
        Button secondary = findViewById(R.id.guide_secondary_btn);
        Button tertiary = findViewById(R.id.guide_tertiary_btn);
        Button pairBtn = findViewById(R.id.guide_pair_btn);
        Button close = findViewById(R.id.guide_close_btn);
        TextInputLayout pairLayout = findViewById(R.id.guide_pair_layout);
        TextInputEditText pairCode = findViewById(R.id.guide_pair_code);

        int mode = App.settings.getInt(App.KILLER_MODE, PrivilegeStatus.MODE_BUILTIN);
        PrivilegeStatus.State state = initialState;
        if (state == null) {
            state = PrivilegeStatus.resolve(getContext(), mode);
        }

        boolean builtin = PrivilegeStatus.isBuiltinMode(mode);

        switch (state) {
            case NOT_INSTALLED:
                title.setText(R.string.shizuku_guide_title_not_installed);
                body.setText(R.string.shizuku_guide_body_not_installed);
                primary.setText(R.string.shizuku_guide_download);
                primary.setOnClickListener(v -> openDownload());
                secondary.setText(R.string.shizuku_guide_open_dev_options);
                secondary.setVisibility(View.VISIBLE);
                secondary.setOnClickListener(v -> openDevOptions());
                break;
            case NOT_RUNNING:
                title.setText(R.string.shizuku_guide_title_not_running);
                body.setText(R.string.shizuku_guide_body_not_running);
                if (builtin) {
                    pairLayout.setVisibility(View.VISIBLE);
                    pairBtn.setVisibility(View.VISIBLE);
                    tertiary.setVisibility(View.VISIBLE);
                    primary.setText(R.string.guide_start_btn);
                    primary.setOnClickListener(v -> startBuiltin(primary));
                    pairBtn.setOnClickListener(v -> {
                        String code = pairCode.getText() == null ? "" : pairCode.getText().toString();
                        pairBuiltin(pairBtn, code);
                    });
                    tertiary.setOnClickListener(v -> copyStarter());
                    secondary.setText(R.string.shizuku_guide_open_dev_options);
                    secondary.setVisibility(View.VISIBLE);
                    secondary.setOnClickListener(v -> openDevOptions());
                } else {
                    primary.setText(R.string.shizuku_guide_open_shizuku);
                    primary.setOnClickListener(v -> openShizuku());
                    secondary.setText(R.string.shizuku_guide_open_dev_options);
                    secondary.setVisibility(View.VISIBLE);
                    secondary.setOnClickListener(v -> openDevOptions());
                }
                break;
            case NOT_AUTHORIZED:
                title.setText(R.string.shizuku_guide_title_not_authorized);
                body.setText(R.string.shizuku_guide_body_not_authorized);
                primary.setText(R.string.shizuku_guide_request_permission);
                primary.setOnClickListener(v -> {
                    ShizukuUtils.requestPermission();
                    android.app.Activity act = nullSafeActivity();
                    if (act != null) {
                        CapsuleToast.showInfo(act,
                                getContext().getString(R.string.shizuku_guide_permission_toast));
                    }
                    dismiss();
                });
                secondary.setVisibility(View.GONE);
                break;
            case READY:
                title.setText(R.string.shizuku_guide_title_ready);
                body.setText(R.string.shizuku_guide_body_ready);
                primary.setText(R.string.shizuku_guide_close);
                primary.setOnClickListener(v -> dismiss());
                secondary.setVisibility(View.GONE);
                break;
            case ROOT_MODE:
            default:
                title.setText(R.string.shizuku_guide_title_root);
                body.setText(R.string.shizuku_guide_body_root);
                primary.setText(R.string.shizuku_guide_close);
                primary.setOnClickListener(v -> dismiss());
                secondary.setVisibility(View.GONE);
                break;
        }

        close.setOnClickListener(v -> dismiss());
        setOnDismissListener(d -> {
            if (onDismissRefresh != null) onDismissRefresh.run();
        });
    }

    private void startBuiltin(Button primary) {
        primary.setEnabled(false);
        primary.setText(R.string.guide_starting);
        App.executorService.execute(() -> {
            String err = PrivKitBridge.startAdbBlocking();
            App.handler.post(() -> {
                android.app.Activity act = nullSafeActivity();
                if (err == null) {
                    if (act != null) CapsuleToast.showInfo(act, getContext().getString(R.string.guide_start_ok));
                    dismiss();
                } else {
                    primary.setEnabled(true);
                    primary.setText(R.string.guide_start_btn);
                    if (act != null) {
                        CapsuleToast.showInfo(act,
                                getContext().getString(R.string.guide_start_fail, err));
                    }
                }
            });
        });
    }

    private void pairBuiltin(Button pairBtn, String code) {
        if (code == null || code.trim().length() < 6) {
            android.app.Activity act = nullSafeActivity();
            if (act != null) CapsuleToast.showInfo(act, "Enter 6-digit pairing code");
            return;
        }
        pairBtn.setEnabled(false);
        pairBtn.setText(R.string.guide_pairing);
        App.executorService.execute(() -> {
            String err = PrivKitBridge.pairBlocking(code);
            App.handler.post(() -> {
                pairBtn.setEnabled(true);
                pairBtn.setText(R.string.guide_pair_btn);
                android.app.Activity act = nullSafeActivity();
                if (act == null) return;
                if (err == null) {
                    CapsuleToast.showInfo(act, getContext().getString(R.string.guide_pair_ok));
                } else {
                    CapsuleToast.showInfo(act, getContext().getString(R.string.guide_pair_fail, err));
                }
            });
        });
    }

    private void copyStarter() {
        App.executorService.execute(() -> {
            String cmd = PrivKitBridge.nativeStarterCommandBlocking();
            App.handler.post(() -> {
                android.app.Activity act = nullSafeActivity();
                if (cmd == null || cmd.isEmpty()) {
                    if (act != null) CapsuleToast.showInfo(act, "Starter unavailable");
                    return;
                }
                ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("privkit-starter", "adb shell " + cmd));
                PrivKitBridge.dumpStarterCommand(getContext());
                if (act != null) CapsuleToast.showInfo(act, getContext().getString(R.string.guide_starter_copied));
            });
        });
    }

    private android.app.Activity nullSafeActivity() {
        Context c = getContext();
        if (c instanceof android.app.Activity) return (android.app.Activity) c;
        return null;
    }

    private void openDownload() {
        try {
            getContext().startActivity(PrivilegeStatus.downloadShizukuIntent());
        } catch (ActivityNotFoundException e) {
            android.app.Activity act = nullSafeActivity();
            if (act != null) CapsuleToast.showInfo(act, "No browser found");
        }
    }

    private void openShizuku() {
        Intent intent = PrivilegeStatus.openShizukuAppIntent(getContext());
        if (intent != null) {
            try {
                getContext().startActivity(intent);
            } catch (ActivityNotFoundException e) {
                openDownload();
            }
        } else {
            openDownload();
        }
    }

    private void openDevOptions() {
        try {
            getContext().startActivity(PrivilegeStatus.developerOptionsIntent());
        } catch (ActivityNotFoundException e) {
            android.app.Activity act = nullSafeActivity();
            if (act != null) CapsuleToast.showInfo(act, "Cannot open developer options");
        }
    }
}
