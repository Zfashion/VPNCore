package com.module.vpncore.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.ArraySet;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.base.vpn.VPNConfig;
import com.module.vpncore.R;
import com.module.vpncore.util.VPNAppProxyList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AppProxyActivity extends AppCompatActivity {

    private CheckBox mCheckBoxSelectAll;
    private RecyclerView mRecyclerView;
    private AppAdapter mAdapter;
    private boolean isSelectAll;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_proxy_activity);
        isSelectAll = isSelectAll();
        mCheckBoxSelectAll = findViewById(R.id.select_all);
        mCheckBoxSelectAll.setChecked(isSelectAll);

        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, 1));
        mRecyclerView.setAdapter(mAdapter = new AppAdapter(getAppInfos(), getPackageManager()));

        mCheckBoxSelectAll.setOnClickListener((v) -> {
            isSelectAll = mCheckBoxSelectAll.isChecked();
            if (isSelectAll) {
                saveSelectAll(true);
            } else {
                saveSelectAll(false);
            }
            List<AppInfo> appInfos = mAdapter.getData();
            for (AppInfo appInfo : appInfos) {
                appInfo.isSelected = isSelectAll;
            }
            mAdapter.notifyDataSetChanged();
        });
    }


    private class AppAdapter extends RecyclerView.Adapter<Holder> {

        private List<AppInfo> mData;
        private PackageManager mPackageManager;

        AppAdapter(List<AppInfo> mData, PackageManager packageManager) {
            this.mData = mData;
            this.mPackageManager = packageManager;
        }

        List<AppInfo> getData() {
            return mData;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(View.inflate(parent.getContext(), R.layout.proxy_app_item, null));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            AppInfo appInfo = mData.get(position);
            holder.mAppCheck.setChecked(appInfo.isSelected);
            holder.mAppName.setText(appInfo.appName);
            holder.mAppIcon.setImageDrawable(appInfo.pkgInfo.applicationInfo.loadIcon(mPackageManager));
            holder.mAppCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
                mAdapter.getData().get(position).isSelected = isChecked;
                if (!isChecked) {
                    isSelectAll = false;
                } else {
                    isSelectAll = checkSelectAll();
                }
                mCheckBoxSelectAll.setChecked(isSelectAll);
                saveSelectAll(isSelectAll);
            });
        }

        private boolean checkSelectAll() {
            for (AppInfo info : mData) {
                if (!info.isSelected) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }
    }

    private class Holder extends RecyclerView.ViewHolder {

        TextView mAppName;
        ImageView mAppIcon;
        CheckBox mAppCheck;

        Holder(@NonNull View itemView) {
            super(itemView);
            mAppName = itemView.findViewById(R.id.tv_app_name);
            mAppIcon = itemView.findViewById(R.id.iv_app_icon);
            mAppCheck = itemView.findViewById(R.id.switch_proxy);
        }
    }

    private List<PackageInfo> getInstalledApps() {
        PackageManager pm = getPackageManager();
        List<PackageInfo> pkgs = null;
        try {
            pkgs = pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES);
        } catch (Exception e) {

        }
        return pkgs;
    }

    private List<AppInfo> getAppInfos() {
        List<AppInfo> appInfos = new ArrayList<>();
        List<PackageInfo> pkgs = getInstalledApps();
        PackageManager pm = getPackageManager();

        Set<String> apps = new VPNAppProxyList(VPNConfig.dataStore).getAllowedApps();

        AppInfo appInfo;
        PackageInfo packageInfo;
        for (int i = 0; i < pkgs.size(); i++) {
            appInfo = new AppInfo();
            packageInfo = pkgs.get(i);
            appInfo.pkgInfo = packageInfo;
            appInfo.appName = packageInfo.applicationInfo.loadLabel(pm).toString();
            if (isSelectAll) {
                appInfo.isSelected = true;
            } else {
                appInfo.isSelected = apps.contains(appInfo.pkgInfo.packageName);
            }
            appInfos.add(appInfo);
        }
        return appInfos;
    }


    private void saveSelectAll(boolean isSelectAll) {
        VPNConfig.dataStore.putBoolean("select_all", isSelectAll);
    }

    private boolean isSelectAll() {
        return VPNConfig.dataStore.getBoolean("select_all", true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        VPNAppProxyList vpnAppProxyList = new VPNAppProxyList(VPNConfig.dataStore);
        if (isSelectAll()) {
            vpnAppProxyList.clearAllowedApps();
        } else {
            List<AppInfo> appInfos = mAdapter.getData();
            Set<String> apps = new ArraySet<>();
            if (appInfos != null) {
                for (AppInfo appInfo : appInfos) {
                    if (appInfo.isSelected) {
                        apps.add(appInfo.pkgInfo.packageName);
                    }
                }
            }
            if (apps.size() > 0) {
                vpnAppProxyList.setAllowedApps(apps);
            }
        }
    }
}
