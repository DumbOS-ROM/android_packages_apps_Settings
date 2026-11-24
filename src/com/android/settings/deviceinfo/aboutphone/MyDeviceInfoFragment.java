/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.deviceinfo.aboutphone;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserManager;
import android.view.View;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.deviceinfo.BluetoothAddressPreferenceController;
import com.android.settings.deviceinfo.BuildNumberPreferenceController;
import com.android.settings.deviceinfo.DeviceNamePreferenceController;
import com.android.settings.deviceinfo.FccEquipmentIdPreferenceController;
import com.android.settings.deviceinfo.FeedbackPreferenceController;
import com.android.settings.deviceinfo.IpAddressPreferenceController;
import com.android.settings.deviceinfo.ManualPreferenceController;
import com.android.settings.deviceinfo.RegulatoryInfoPreferenceController;
import com.android.settings.deviceinfo.SafetyInfoPreferenceController;
import com.android.settings.deviceinfo.UptimePreferenceController;
import com.android.settings.deviceinfo.WifiMacAddressPreferenceController;
import com.android.settings.deviceinfo.imei.ImeiInfoPreferenceController;
import com.android.settings.deviceinfo.simstatus.EidStatus;
import com.android.settings.deviceinfo.simstatus.SimEidPreferenceController;
import com.android.settings.deviceinfo.simstatus.SimStatusPreferenceController;
import com.android.settings.deviceinfo.simstatus.SlotSimStatus;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.EntityHeaderController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.LayoutPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@SearchIndexable
public class MyDeviceInfoFragment extends DashboardFragment
        implements DeviceNamePreferenceController.DeviceNamePreferenceHost {

    private static final String LOG_TAG = "MyDeviceInfoFragment";
    private static final String KEY_EID_INFO = "eid_info";
    private static final String KEY_MY_DEVICE_INFO_HEADER = "my_device_info_header";

    private BuildNumberPreferenceController mBuildNumberPreferenceController;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DEVICEINFO;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_uri_about;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(DeviceNamePreferenceController.class).setHost(this /* parent */);
        mBuildNumberPreferenceController = use(BuildNumberPreferenceController.class);
        mBuildNumberPreferenceController.setHost(this /* parent */);
    }

    @Override
    public void onStart() {
        super.onStart();
        initDumbOsAboutCard();
        initHeader();
    }

    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.my_device_info;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, this /* fragment */, getSettingsLifecycle());
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(
            Context context, MyDeviceInfoFragment fragment, Lifecycle lifecycle) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();

        final ExecutorService executor = (fragment == null) ? null :
                Executors.newSingleThreadExecutor();
        androidx.lifecycle.Lifecycle lifecycleObject = (fragment == null) ? null :
                fragment.getLifecycle();
        final SlotSimStatus slotSimStatus = new SlotSimStatus(context, executor, lifecycleObject);

        controllers.add(new IpAddressPreferenceController(context, lifecycle));
        controllers.add(new WifiMacAddressPreferenceController(context, lifecycle));
        controllers.add(new BluetoothAddressPreferenceController(context, lifecycle));
        controllers.add(new RegulatoryInfoPreferenceController(context));
        controllers.add(new SafetyInfoPreferenceController(context));
        controllers.add(new ManualPreferenceController(context));
        controllers.add(new FeedbackPreferenceController(fragment, context));
        controllers.add(new FccEquipmentIdPreferenceController(context));
        controllers.add(new UptimePreferenceController(context, lifecycle));

        Consumer<String> imeiInfoList = imeiKey -> {
            ImeiInfoPreferenceController imeiRecord =
                    new ImeiInfoPreferenceController(context, imeiKey);
            imeiRecord.init(fragment, slotSimStatus);
            controllers.add(imeiRecord);
        };

        if (fragment != null) {
            imeiInfoList.accept(ImeiInfoPreferenceController.DEFAULT_KEY);
        }

        for (int slotIndex = 0; slotIndex < slotSimStatus.size(); slotIndex ++) {
            SimStatusPreferenceController slotRecord =
                    new SimStatusPreferenceController(context,
                    slotSimStatus.getPreferenceKey(slotIndex));
            slotRecord.init(fragment, slotSimStatus);
            controllers.add(slotRecord);

            if (fragment != null) {
                imeiInfoList.accept(ImeiInfoPreferenceController.DEFAULT_KEY + (1 + slotIndex));
            }
        }

        EidStatus eidStatus = new EidStatus(slotSimStatus, context, executor);
        SimEidPreferenceController simEid = new SimEidPreferenceController(context, KEY_EID_INFO);
        simEid.init(slotSimStatus, eidStatus);
        controllers.add(simEid);

        if (executor != null) {
            executor.shutdown();
        }
        return controllers;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mBuildNumberPreferenceController.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void initHeader() {
        // TODO: Migrate into its own controller.
        final LayoutPreference headerPreference =
                getPreferenceScreen().findPreference(KEY_MY_DEVICE_INFO_HEADER);
        final boolean shouldDisplayHeader = getContext().getResources().getBoolean(
                R.bool.config_show_device_header_in_device_info);
        headerPreference.setVisible(shouldDisplayHeader);
        if (!shouldDisplayHeader) {
            return;
        }
        final View headerView = headerPreference.findViewById(R.id.entity_header);
        final Activity context = getActivity();
        final Bundle bundle = getArguments();
        final EntityHeaderController controller = EntityHeaderController
                .newInstance(context, this, headerView)
                .setButtonActions(EntityHeaderController.ActionType.ACTION_NONE,
                        EntityHeaderController.ActionType.ACTION_NONE);

        // TODO: There may be an avatar setting action we can use here.
        final int iconId = bundle.getInt("icon_id", 0);
        if (iconId == 0) {
            final UserManager userManager = (UserManager) getActivity().getSystemService(
                    Context.USER_SERVICE);
            final UserInfo info = Utils.getExistingUser(userManager,
                    android.os.Process.myUserHandle());
            controller.setLabel(info.name);
            controller.setIcon(
                    com.android.settingslib.Utils.getUserIcon(getActivity(), userManager, info));
        }

        controller.done(true /* rebindActions */);
    }

    @Override
    public void showDeviceNameWarningDialog(String deviceName) {
        DeviceNameWarningDialog.show(this);
    }

    public void onSetDeviceNameConfirm(boolean confirm) {
        final DeviceNamePreferenceController controller = use(DeviceNamePreferenceController.class);
        controller.updateDeviceName(confirm);
    }

    /**
     * For Search.
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.my_device_info) {

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context, null /* fragment */,
                            null /* lifecycle */);
                }
            };

    private final long[] mDumbOsHits = new long[3];

    private void initDumbOsAboutCard() {
        try {
            final LayoutPreference romCard =
                    getPreferenceScreen().findPreference("dumbos_about_rom_card");
            if (romCard == null) {
                return;
            }
            final android.widget.TextView versionPill = romCard.findViewById(R.id.dumbos_version_pill);
            final android.widget.TextView specDevice = romCard.findViewById(R.id.dumbos_spec_device);
            final android.widget.TextView specAndroid = romCard.findViewById(R.id.dumbos_spec_android);
            final android.widget.TextView specMode = romCard.findViewById(R.id.dumbos_spec_mode);
            final android.widget.TextView specKernel = romCard.findViewById(R.id.dumbos_spec_kernel);
            final android.widget.TextView specBuild = romCard.findViewById(R.id.dumbos_spec_build);
            final View cardRoot = romCard.findViewById(R.id.dumbos_about_card_root);

            String dumbVer = android.os.SystemProperties.get("ro.dumbos.version", "");
            if (dumbVer.isEmpty()) {
                dumbVer = android.os.SystemProperties.get("ro.lineage.display.version", "DumbOS");
            }
            if (versionPill != null) {
                versionPill.setText(dumbVer.toUpperCase(java.util.Locale.US));
            }
            if (specDevice != null) {
                specDevice.setText(android.os.Build.MODEL + " (" + android.os.Build.DEVICE + ")");
            }
            if (specAndroid != null) {
                specAndroid.setText("Android " + android.os.Build.VERSION.RELEASE
                        + " (API " + android.os.Build.VERSION.SDK_INT + ")");
            }
            if (specMode != null) {
                String mode = null;
                try {
                    mode = android.provider.Settings.System.getString(
                            getContext().getContentResolver(), "dumbos_mode");
                } catch (Throwable ignored) {}
                specMode.setText(mode != null && !mode.isEmpty() ? mode : "Normal");
            }
            if (specKernel != null) {
                String ramStr = "";
                try {
                    android.app.ActivityManager am = getContext().getSystemService(android.app.ActivityManager.class);
                    if (am != null) {
                        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                        am.getMemoryInfo(mi);
                        double gb = mi.totalMem / (1024.0 * 1024.0 * 1024.0);
                        ramStr = String.format(java.util.Locale.US, "%.1f GB  •  ", gb);
                    }
                } catch (Throwable ignored) {}
                String kver = System.getProperty("os.version", "Linux");
                if (kver != null && kver.contains("-")) {
                    kver = kver.split("-")[0];
                }
                specKernel.setText(ramStr + "Linux " + kver);
            }
            if (specBuild != null) {
                specBuild.setText("BUILD // " + android.os.Build.TYPE.toUpperCase(java.util.Locale.US));
            }
            if (cardRoot != null) {
                cardRoot.setOnClickListener(v -> {
                    System.arraycopy(mDumbOsHits, 1, mDumbOsHits, 0, mDumbOsHits.length - 1);
                    mDumbOsHits[mDumbOsHits.length - 1] = android.os.SystemClock.uptimeMillis();
                    if (mDumbOsHits[0] >= android.os.SystemClock.uptimeMillis() - 600) {
                        try {
                            Intent egg = new Intent(Intent.ACTION_MAIN)
                                    .setClassName("org.lineageos.lineageparts",
                                            "org.lineageos.lineageparts.logo.PlatLogoActivity");
                            startActivity(egg);
                        } catch (Throwable t) {
                            try {
                                Intent plat = new Intent(Intent.ACTION_MAIN)
                                        .setClassName("android", "com.android.internal.app.PlatLogoActivity");
                                startActivity(plat);
                            } catch (Throwable ignored) {}
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

}
