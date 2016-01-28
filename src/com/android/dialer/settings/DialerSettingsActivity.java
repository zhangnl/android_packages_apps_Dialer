package com.android.dialer.settings;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import com.android.dialer.DialerApplication;
import com.android.phone.common.ambient.AmbientConnection;
import com.android.phone.common.incall.CallMethodHelper;
import com.android.phone.common.incall.CallMethodInfo;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.callerinfo.results.IsAuthenticatedResult;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.util.ProviderInfo;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.PendingResult;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.plugin.PluginStatus;
import com.cyanogen.ambient.incall.util.InCallHelper;
import com.google.common.collect.Lists;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceActivity.Header;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.dialer.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DialerSettingsActivity extends PreferenceActivity {

    private static final String TAG = DialerSettingsActivity.class.getSimpleName();

    protected SharedPreferences mPreferences;
    private HeaderAdapter mHeaderAdapter;

    private static final String COMPONENT_STATUS = "component_status";
    private static final String COMPONENT_NAME = "component_name";
    private static final String SETTINGS_INTENT = "settings_intent";

    private static final int OWNER_HANDLE_ID = 0;
    private CallerInfoHelper.ResolvedProvider mSelectProvider;
    private ProviderInfo mSelectedProviderInfo;
    private boolean mPickerResult;

    PreferenceCategory category;
    PreferenceScreen mPreferenceScreen;
    List<CallMethodInfo> mCallProviders = new ArrayList<>();
    List<Header> mCurrentHeaders = Lists.newArrayList();

    private static final String AMBIENT_SUBSCRIPTION_ID = "DialerSettingsActivity";

    CallMethodHelper.CallMethodReceiver pluginsUpdatedReceiver =
            new CallMethodHelper.CallMethodReceiver() {
                @Override
                public void onChanged(HashMap<ComponentName, CallMethodInfo> callMethodInfos) {
                    providersUpdated(callMethodInfos);
                }
            };

    private void providersUpdated(HashMap<ComponentName, CallMethodInfo> callMethodInfos) {
        mCallProviders.clear();
        mCallProviders.addAll(callMethodInfos.values());
        invalidateHeaders();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Needs to be done prior to super's onCreate
        if(CallMethodHelper.subscribe(AMBIENT_SUBSCRIPTION_ID, pluginsUpdatedReceiver)) {
            providersUpdated(CallMethodHelper.getAllCallMethods());
        }
        if (CallerInfoHelper.getInstalledProviders(this).length > 0) {
            CallerInfoHelper.ResolvedProvider[] providers =
                    CallerInfoHelper.getInstalledProviders(this);
            mSelectProvider = providers[0];
            if (mSelectProvider != null ) {
                mSelectedProviderInfo =
                        CallerInfoHelper.getProviderInfo(this, mSelectProvider.getComponent());
            }
        }
        super.onCreate(savedInstanceState);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        Header displayOptionsHeader = new Header();
        displayOptionsHeader.titleRes = R.string.display_options_title;
        displayOptionsHeader.fragment = DisplayOptionsSettingsFragment.class.getName();
        target.add(displayOptionsHeader);

        Header soundSettingsHeader = new Header();
        soundSettingsHeader.titleRes = R.string.sounds_and_vibration_title;
        soundSettingsHeader.fragment = SoundSettingsFragment.class.getName();
        soundSettingsHeader.id = R.id.settings_header_sounds_and_vibration;
        target.add(soundSettingsHeader);

        Header quickResponseSettingsHeader = new Header();
        Intent quickResponseSettingsIntent =
                new Intent(TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS);
        quickResponseSettingsHeader.titleRes = R.string.respond_via_sms_setting_title;
        quickResponseSettingsHeader.intent = quickResponseSettingsIntent;
        target.add(quickResponseSettingsHeader);

        final Header lookupSettingsHeader = new Header();
        lookupSettingsHeader.titleRes = R.string.lookup_settings_label;
        lookupSettingsHeader.summaryRes = R.string.lookup_settings_description;
        lookupSettingsHeader.fragment = LookupSettingsFragment.class.getName();
        target.add(lookupSettingsHeader);

        // Only add the call settings header if the current user is the primary/owner user.
        if (isPrimaryUser()) {
            final TelephonyManager telephonyManager =
                    (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            // Show "Call Settings" if there is one SIM and "Phone Accounts" if there are more.
            if (telephonyManager.getPhoneCount() <= 1) {
                final Header callSettingsHeader = new Header();
                Intent callSettingsIntent = new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS);
                callSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                callSettingsHeader.titleRes = R.string.call_settings_label;
                callSettingsHeader.intent = callSettingsIntent;
                target.add(callSettingsHeader);
            } else {
                final Header phoneAccountSettingsHeader = new Header();
                Intent phoneAccountSettingsIntent =
                        new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                phoneAccountSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                phoneAccountSettingsHeader.titleRes = R.string.phone_account_settings_label;
                phoneAccountSettingsHeader.intent = phoneAccountSettingsIntent;
                target.add(phoneAccountSettingsHeader);
            }
        }

        Header speedDialHeader = new Header();
        Intent speedDialIntent = new Intent("com.android.phone.action.SPEED_DIAL_SETTINGS");
        speedDialHeader.titleRes = R.string.speed_dial_settings;
        speedDialHeader.intent = speedDialIntent;
        target.add(speedDialHeader);

        if (mCallProviders != null) {
            for (CallMethodInfo cmi : mCallProviders) {
                if (cmi.mStatus == PluginStatus.ENABLED || cmi.mStatus ==
                        PluginStatus.DISABLED) {
                    Header thing = new Header();
                    Bundle b = new Bundle();
                    b.putString(COMPONENT_NAME, cmi.mComponent.flattenToShortString());
                    b.putInt(COMPONENT_STATUS, cmi.mStatus);
                    thing.extras = b;
                    thing.title = cmi.mName;
                    thing.summary = cmi.mSummary;
                    target.add(thing);

                    if (cmi.mStatus == PluginStatus.ENABLED && cmi.mSettingsIntent != null) {
                        thing = new Header();

                        thing.title = getResources().getString(R.string.incall_plugin_settings, cmi
                                .mName);
                        b = new Bundle();
                        b.putParcelable(SETTINGS_INTENT, cmi.mSettingsIntent);
                        thing.extras = b;
                        target.add(thing);
                    }
                }
            }
        }



        // invalidateHeaders does not rebuild
        // the list properly, so if an adapter is present already
        // then we've invalidated and need make sure we notify the list.
        if (mCurrentHeaders != target) {
            mCurrentHeaders.clear();
            mCurrentHeaders.addAll(target);
            if (mHeaderAdapter != null) {
                mHeaderAdapter.notifyDataSetChanged();
            }
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true;
    }


    @Override
    public void setListAdapter(ListAdapter adapter) {
        if (adapter == null) {
            super.setListAdapter(null);
        } else {
            // We don't have access to the hidden getHeaders() method, so grab the headers from
            // the intended adapter and then replace it with our own.
            mHeaderAdapter = new HeaderAdapter(this, mCurrentHeaders, mSelectProvider);
            super.setListAdapter(mHeaderAdapter);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mPickerResult = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mHeaderAdapter != null) {
            mPickerResult = false;
            mHeaderAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        CallMethodHelper.unsubscribe(AMBIENT_SUBSCRIPTION_ID);
    }

    /**
     * Whether a user handle associated with the current user is that of the primary owner. That is,
     * whether there is a user handle which has an id which matches the owner's handle.
     * @return Whether the current user is the primary user.
     */
    private boolean isPrimaryUser() {
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        List<UserHandle> userHandles = userManager.getUserProfiles();
        for (int i = 0; i < userHandles.size(); i++){
            if (userHandles.get(i).myUserId() == OWNER_HANDLE_ID) {
                return true;
            }
        }

        return false;
    }

    /**
     * This custom {@code ArrayAdapter} is mostly identical to the equivalent one in
     * {@code PreferenceActivity}, except with a local layout resource.
     */
    private static class HeaderAdapter extends ArrayAdapter<Header>
            implements CompoundButton.OnCheckedChangeListener {

        private final CallerInfoHelper.ResolvedProvider mSelectProvider;

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Header header = ((Header) buttonView.getTag());
            if (header.extras != null && header.extras.containsKey(COMPONENT_NAME)) {
                providerStateChanged(header.extras, isChecked, getContext());
            }
        }

        private void setCheckedStateWithoutTriggeringListener(CompoundButton button,
                                                              boolean state) {
            button.setOnCheckedChangeListener(null);
            button.setChecked(state);
            button.setOnCheckedChangeListener(HeaderAdapter.this);
        }

        private void updateDefault(Switch switchButton) {
            Header header = ((Header) switchButton.getTag());
            if (header.extras != null && header.extras.containsKey(COMPONENT_NAME)) {
                setCheckedStateWithoutTriggeringListener(switchButton,
                        header.extras.getInt(COMPONENT_STATUS) == PluginStatus.ENABLED);

            }
        }

        static class HeaderViewHolder {
            TextView title;
            TextView summary;
            Switch switchButton;
        }

        static final int HEADER_TYPE_NORMAL = 0;
        static final int HEADER_TYPE_SWITCH = 1;

        private LayoutInflater mInflater;

        public HeaderAdapter(Context context, List<Header> objects,
                             CallerInfoHelper.ResolvedProvider selectProvider) {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mSelectProvider = selectProvider;
        }

        public static int getHeaderType(Header header) {
            if (header.extras != null && header.extras.containsKey(COMPONENT_NAME)) {
                return HEADER_TYPE_SWITCH;
            } else {
                return HEADER_TYPE_NORMAL;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            Header header = getItem(position);
            return getHeaderType(header);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            HeaderViewHolder holder;
            View view;
            Header header = getItem(position);
            int headerType = getHeaderType(header);

            switch (headerType) {
                case HEADER_TYPE_SWITCH:
                    if (convertView == null) {
                        view = mInflater.inflate(R.layout.preference_header_switch, parent, false);
                        ((TextView) view.findViewById(R.id.title)).setText(
                                header.getTitle(getContext().getResources()));
                        ((TextView) view.findViewById(R.id.summary)).setText(header
                                .getSummary(getContext().getResources()));
                        final Switch switchButton = ((Switch) view.findViewById(R.id.switchWidget));
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                switchButton.toggle();
                            }
                        });
                        holder = new HeaderViewHolder();
                        holder.title = (TextView) view.findViewById(R.id.title);
                        holder.summary = (TextView) view.findViewById(R.id.summary);
                        holder.switchButton = switchButton;
                        view.setTag(holder);
                    } else {
                        view = convertView;
                        holder = (HeaderViewHolder) view.getTag();
                    }

                    holder.switchButton.setTag(header);
                    updateDefault(holder.switchButton);
                    break;

                default:
                    if (convertView == null) {
                        view = mInflater.inflate(R.layout.dialer_preferences, parent, false);
                        holder = new HeaderViewHolder();
                        holder.title = (TextView) view.findViewById(R.id.title);
                        holder.summary = (TextView) view.findViewById(R.id.summary);
                        view.setTag(holder);
                    } else {
                        view = convertView;
                        holder = (HeaderViewHolder) view.getTag();
                    }

                    if (header.extras != null && header.extras.containsKey(SETTINGS_INTENT)) {
                        final PendingIntent settingsIntent =
                                (PendingIntent) header.extras.get(SETTINGS_INTENT);
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    settingsIntent.send();
                                } catch (PendingIntent.CanceledException e) {
                                    // if the pending intent was cancelled, nothing to do
                                    Log.e(TAG, "Unable to fire intent for plugin settings, "
                                            + "because PendingIntent was canceled", e);
                                }
                            }
                        });
                    }
                    break;
            }

            // All view fields must be updated every time, because the view may be recycled
            holder.title.setText(header.getTitle(getContext().getResources()));
            CharSequence summary = header.getSummary(getContext().getResources());
            if (!TextUtils.isEmpty(summary)) {
                holder.summary.setVisibility(View.VISIBLE);
                holder.summary.setText(summary);
            } else {
                holder.summary.setVisibility(View.GONE);
            }
            return view;
        }
    }

    private static void providerStateChanged(Bundle extras, boolean isEnabled, Context c) {
        String componentString = extras.getString(COMPONENT_NAME);
        ComponentName componentName = ComponentName.unflattenFromString(componentString);

        InCallHelper.ChangeInCallProviderStateAsyncTask asyncTask =
                new InCallHelper.ChangeInCallProviderStateAsyncTask(c,
                        new InCallHelper.IInCallEnabledStateCallback() {
                            @Override
                            public void onChanged(Integer newStatus, ComponentName componentName) {
                                // Note: Deletion of call log  entries are handled
                                // by the ambient core package
                            }
                        }, componentName);

        int status = isEnabled ? PluginStatus.ENABLED : PluginStatus.DISABLED;
        asyncTask.execute(status);
        extras.putInt(COMPONENT_STATUS, status);
    }
}
