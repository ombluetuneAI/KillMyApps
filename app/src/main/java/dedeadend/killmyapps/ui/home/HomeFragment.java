package dedeadend.killmyapps.ui.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.List;

import dedeadend.killmyapps.App;
import dedeadend.killmyapps.MainActivity;
import dedeadend.killmyapps.R;
import dedeadend.killmyapps.databinding.FragmentHomeBinding;
import dedeadend.killmyapps.model.AppInfo;
import dedeadend.killmyapps.util.CapsuleToast;
import dedeadend.killmyapps.util.PrivilegeStatus;
import dedeadend.killmyapps.util.ShizukuUtils;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;
    private HomeRecyclerViewAdapter adapter;
    private CardView searchLayout;
    private EditText searchEditText;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.homeRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        if (App.isFirstRun) {
            // Prefer Shizuku guide over generic Root/Shizuku info when engine is Shizuku*
            int mode = App.settings.getInt(App.KILLER_MODE, 4);
            PrivilegeStatus.State state = PrivilegeStatus.resolve(requireContext(), mode);
            if (mode != 1 && state != PrivilegeStatus.State.READY) {
                showGuide(state);
            } else {
                InfoDialog infoDialog = new InfoDialog(getContext());
                infoDialog.show();
            }
            App.isFirstRun = false;
        }
        binding.privilegeActionBtn.setOnClickListener(v -> onPrivilegeActionClicked());
        binding.privilegeStatusCard.setOnClickListener(v -> onPrivilegeActionClicked());
    }

    @Override
    public void onStart() {
        super.onStart();
        searchLayout = ((MainActivity) requireActivity()).getSearchLayout();
        searchEditText = ((MainActivity) requireActivity()).getSearchEditText();
        setObservers();
        setListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        homeViewModel.refreshList();
        searchEditText.setText("");
        refreshPrivilegeStatus();
    }

    @Override
    public void onStop() {
        super.onStop();
        homeViewModel.clearList();
        searchLayout.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        homeViewModel.getAppsList().removeObservers(getViewLifecycleOwner());
        binding = null;
    }

    private void refreshPrivilegeStatus() {
        if (binding == null || getContext() == null) return;
        int mode = App.settings.getInt(App.KILLER_MODE, 4);
        PrivilegeStatus.State state = PrivilegeStatus.resolve(requireContext(), mode);
        int colorRes;
        String title;
        String subtitle;
        String action;
        boolean actionEnabled = true;

        switch (state) {
            case NOT_INSTALLED:
                colorRes = R.color.status_red;
                title = getString(R.string.privilege_title_not_installed);
                subtitle = getString(R.string.privilege_sub_not_installed);
                action = getString(R.string.privilege_action_install);
                break;
            case NOT_RUNNING:
                colorRes = R.color.status_red;
                title = mode == 1
                        ? getString(R.string.privilege_title_root_missing)
                        : getString(R.string.privilege_title_not_running);
                subtitle = mode == 1
                        ? getString(R.string.privilege_sub_root_missing)
                        : getString(R.string.privilege_sub_not_running);
                action = mode == 1
                        ? getString(R.string.privilege_action_guide)
                        : getString(R.string.privilege_action_start);
                break;
            case NOT_AUTHORIZED:
                colorRes = R.color.status_yellow;
                title = getString(R.string.privilege_title_not_authorized);
                subtitle = getString(R.string.privilege_sub_not_authorized);
                action = getString(R.string.privilege_action_authorize);
                break;
            case READY:
                colorRes = R.color.status_green;
                title = getString(R.string.privilege_title_ready);
                subtitle = getString(R.string.privilege_sub_ready);
                action = getString(R.string.privilege_action_ready);
                actionEnabled = false;
                break;
            case ROOT_MODE:
                colorRes = R.color.status_green;
                title = getString(R.string.privilege_title_root_ok);
                subtitle = getString(R.string.privilege_sub_root_ok);
                action = getString(R.string.privilege_action_ready);
                actionEnabled = false;
                break;
            default:
                colorRes = R.color.status_gray;
                title = getString(R.string.privilege_status_checking);
                subtitle = getString(R.string.privilege_status_subtitle_default);
                action = getString(R.string.privilege_action_guide);
                break;
        }

        String engineLabel = PrivilegeStatus.engineLabel(mode);
        subtitle = engineLabel + " · " + subtitle;

        binding.privilegeStatusTitle.setText(title);
        binding.privilegeStatusSubtitle.setText(subtitle);
        binding.privilegeActionBtn.setText(action);
        binding.privilegeActionBtn.setEnabled(actionEnabled);
        binding.privilegeActionBtn.setAlpha(actionEnabled ? 1f : 0.5f);

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ContextCompat.getColor(requireContext(), colorRes));
        binding.privilegeStatusDot.setBackground(dot);
    }

    private void onPrivilegeActionClicked() {
        int mode = App.settings.getInt(App.KILLER_MODE, 4);
        PrivilegeStatus.State state = PrivilegeStatus.resolve(requireContext(), mode);
        if (state == PrivilegeStatus.State.NOT_AUTHORIZED) {
            ShizukuUtils.requestPermission();
            CapsuleToast.showInfo(getActivity(), getString(R.string.shizuku_guide_permission_toast));
            binding.getRoot().postDelayed(this::refreshPrivilegeStatus, 800);
            return;
        }
        if (state == PrivilegeStatus.State.READY || state == PrivilegeStatus.State.ROOT_MODE) {
            return;
        }
        // Built-in: open guide (pair / start). Shizuku*: same.
        showGuide(state);
    }

    private void showGuide(PrivilegeStatus.State state) {
        ShizukuGuideDialog dialog = new ShizukuGuideDialog(requireContext(), state);
        dialog.setOnDismissRefresh(this::refreshPrivilegeStatus);
        dialog.show();
    }

    private void setObservers() {
        homeViewModel.getAppsList().observe(getViewLifecycleOwner(), new Observer<List<AppInfo>>() {
            @Override
            public void onChanged(List<AppInfo> appInfos) {
                setAdapter();
                if (appInfos.isEmpty()) {
                    binding.allDead.setAlpha(0f);
                    binding.allDead.animate().alpha(1f).setDuration(3000L).setStartDelay(1000L).start();
                    binding.killAllBtn.setVisibility(View.GONE);
                    searchLayout.setVisibility(View.GONE);
                    binding.allDead.setVisibility(View.VISIBLE);
                } else {
                    binding.killAllBtn.setAlpha(0f);
                    searchLayout.setAlpha(0f);
                    binding.killAllBtn.animate().alpha(1f).setDuration(400L).start();
                    searchLayout.animate().alpha(1f).setDuration(400L).start();
                    binding.allDead.setVisibility(View.GONE);
                    binding.killAllBtn.setVisibility(View.VISIBLE);
                    searchLayout.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void setListeners() {
        binding.refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                homeViewModel.refreshList();
                refreshPrivilegeStatus();
                binding.refreshLayout.setRefreshing(false);
            }
        });
        binding.killAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int mode = App.settings.getInt(App.KILLER_MODE, 4);
                if (!PrivilegeStatus.canKill(requireContext(), mode)) {
                    PrivilegeStatus.State state = PrivilegeStatus.resolve(requireContext(), mode);
                    showGuide(state);
                    return;
                }
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(200L).withEndAction(() ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(200L).start()
                ).start();
                CapsuleToast capsuleToast = CapsuleToast.showLoading(getActivity(), "Killing...");
                homeViewModel.onKillAllAppsClicked(new HomeViewModel.OnResultListener() {
                    @Override
                    public void onKillSuccessfully(int count) {
                        if (count == 1)
                            capsuleToast.updateSuccess("1 app is dead");
                        else
                            capsuleToast.updateSuccess(count + " apps are dead");
                    }

                    @Override
                    public void onKillFailed() {
                        capsuleToast.updateError("Failed");
                        PrivilegeStatus.State state = PrivilegeStatus.resolve(requireContext(),
                                App.settings.getInt(App.KILLER_MODE, 4));
                        showGuide(state);
                    }
                });
            }
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null)
                    adapter.filterList(s.toString());
            }
        });
    }


    private void setAdapter() {
        adapter = new HomeRecyclerViewAdapter(homeViewModel.getAppsList().getValue(), new HomeRecyclerViewAdapter.onItemClickListener() {
            @Override
            public void onKillButtonClicked(String pkgName, String name) {
                int mode = App.settings.getInt(App.KILLER_MODE, 4);
                if (!PrivilegeStatus.canKill(requireContext(), mode)) {
                    showGuide(PrivilegeStatus.resolve(requireContext(), mode));
                    return;
                }
                CapsuleToast capsuleToast = CapsuleToast.showLoading(getActivity(), "Killing...");
                homeViewModel.onKillSingleAppClicked(pkgName, new HomeViewModel.OnResultListener() {
                    @Override
                    public void onKillSuccessfully(int count) {
                        adapter.itemKilled(pkgName);
                        capsuleToast.updateSuccess("\"" + name + "\" is dead");
                        homeViewModel.checkForRefresh();
                    }

                    @Override
                    public void onKillFailed() {
                        capsuleToast.updateError("Failed");
                        showGuide(PrivilegeStatus.resolve(requireContext(),
                                App.settings.getInt(App.KILLER_MODE, 4)));
                    }
                });
            }

            @Override
            public void onAppInfoLongClicked(View v, String pkgName) {
                ClipboardManager clipboardManager = (ClipboardManager) App.context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("pkgName", pkgName);
                clipboardManager.setPrimaryClip(clipData);
                CapsuleToast.showInfo(getActivity(), "package name copied to clipboard");
            }
        });
        binding.homeRecyclerView.swapAdapter(adapter, true);
    }
}
