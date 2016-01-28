package com.android.dialer.discovery;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import com.android.dialer.DialtactsActivity;
import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.CallMethodUtils;
import com.android.phone.common.util.ImageUtils;

import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.discovery.DiscoveryManagerServices;
import com.cyanogen.ambient.discovery.NudgeServices;
import com.cyanogen.ambient.discovery.nudge.NotificationNudge;
import com.cyanogen.ambient.discovery.nudge.Nudge;
import com.cyanogen.ambient.discovery.util.NudgeKey;
import com.cyanogen.ambient.incall.InCallApi;
import com.cyanogen.ambient.incall.InCallServices;
import com.cyanogen.ambient.incall.results.InCallProviderInfoResult;
import com.cyanogen.ambient.incall.results.PluginStatusResult;
import com.cyanogen.ambient.plugin.PluginStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscoveryEventHandler {

    private static final String TAG = "DiscoveryEventHandler";
    private static final boolean DEBUG_STATUS = false;

    public static void getNudgeProvidersWithKey(final Context context, final String key) {
        // Spin up a new thread to make the needed calls to ambient.
        new Thread(new Runnable() {
            @Override
            public void run() {
                AmbientApiClient client = AmbientConnection.CLIENT.get(context);
                ArrayList<NotificationNudge> nudges = getNudges(client, context, key);
                sendNudgeRequestToDiscovery(client, nudges);
            }
        }).start();
    }

    public static void sendNudgeRequestToDiscovery(AmbientApiClient client,
                                                   ArrayList<NotificationNudge> nudges) {

        for (NotificationNudge nn : nudges) {
            DiscoveryManagerServices.DiscoveryManagerApi.publishNudge(client, nn);
        }
    }

    public static ArrayList<NotificationNudge> getNudges(AmbientApiClient client, Context context,
                                                         String key) {

        Map nudgePlugins =
                NudgeServices.NudgeApi.getAvailableNudgesForKey(client, key).await().components;

        InCallApi api = InCallServices.getInstance();

        List<ComponentName> plugins = api.getInstalledPlugins(client).await().components;

        HashMap<String, Bundle> availableNudges = new HashMap<>();

        for (Object entry : nudgePlugins.entrySet()) {
            Map.Entry<ComponentName, Bundle> theEntry = (Map.Entry<ComponentName, Bundle>) entry;
            availableNudges.put(theEntry.getKey().getPackageName(), theEntry.getValue());
        }
        ArrayList<NotificationNudge> notificationNudges = new ArrayList<>();

        if (plugins != null && plugins.size() > 0) {

            for (ComponentName component : plugins) {

                if (availableNudges.containsKey(component.getPackageName())) {

                    PluginStatusResult statusResult =
                            api.getPluginStatus(client, component).await();

                    Bundle b = availableNudges.get(component.getPackageName());

                    if (key.equals(NudgeKey.NOTIFICATION_INTERNATIONAL_CALL)) {
                        SharedPreferences preferences = context
                                .getSharedPreferences(DialtactsActivity.SHARED_PREFS_NAME,
                                        Context.MODE_PRIVATE);
                        int count = preferences.getInt(CallMethodUtils.PREF_INTERNATIONAL_CALLS, 0);
                        boolean checkCount =
                                count != b.getInt(NudgeKey.NOTIFICATION_PARAM_EVENTS_FIRST_NUDGE) ||
                                count != b.getInt(NudgeKey.NOTIFICATION_PARAM_EVENTS_SECOND_NUDGE);

                        if (checkCount) {
                            // Nudge not yet ready for this item.
                            continue;
                        }
                    }

                    if (DEBUG_STATUS || (statusResult.status != PluginStatus.DISABLED &&
                                statusResult.status != PluginStatus.UNAVAILABLE)) {

                        Bitmap notificationIcon = null;

                        InCallProviderInfoResult providerInfo =
                                api.getProviderInfo(client, component).await();

                        if (providerInfo != null && providerInfo.inCallProviderInfo != null) {
                            try {
                                Resources pluginResources = context.getPackageManager()
                                        .getResourcesForApplication(component.getPackageName());

                                Drawable d = pluginResources.getDrawable(
                                        providerInfo.inCallProviderInfo.getBrandIcon(), null);

                                notificationIcon = ImageUtils.drawableToBitmap(d);
                            } catch (Resources.NotFoundException e) {
                                Log.e(TAG, "Unable to retrieve icon for plugin: " + component);
                            } catch (PackageManager.NameNotFoundException e) {
                                Log.e(TAG, "Plugin isn't installed: " + component);
                            }
                        }

                        NotificationNudge nn = new NotificationNudge(component.getPackageName(),
                                Nudge.Type.IMMEDIATE,
                                b.getString(NudgeKey.NUDGE_PARAM_TITLE),
                                b.getString(NudgeKey.NOTIFICATION_PARAM_BODY));

                        if (notificationIcon != null) {
                            nn.setLargeIcon(notificationIcon);
                        }

                        Parcelable[] actions =
                                b.getParcelableArray(NudgeKey.NOTIFICATION_PARAM_NUDGE_ACTIONS);

                        for (Parcelable action : actions) {
                            NotificationNudge.Button button = (NotificationNudge.Button) action;
                            nn.addButton(button);
                        }

                        notificationNudges.add(nn);
                    }
                }
            }
        }
        return notificationNudges;
    }
}
