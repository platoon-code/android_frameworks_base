/*
 * Copyright (C) 2012 CyanogenMod Project
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

package com.android.systemui.statusbar.quicksettings;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.internal.telephony.Phone;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class MobileNetworkModeTile extends QuickSettingsTile {

    private static final String TAG = "NetworkModeQuickSettings";

    // retrieved from Phone.apk
    private static final String ACTION_NETWORK_MODE_CHANGED = "com.android.internal.telephony.NETWORK_MODE_CHANGED";
    private static final String ACTION_MODIFY_NETWORK_MODE = "com.android.internal.telephony.MODIFY_NETWORK_MODE";
    private static final String EXTRA_NETWORK_MODE = "networkMode";

    private static final int STATE_ENABLED = 1;
    private static final int STATE_DISABLED = 2;
    private static final int STATE_TURNING_ON = 3;
    public static final int STATE_TURNING_OFF = 4;
    private static final int STATE_INTERMEDIATE = 5;
    private static final int NO_NETWORK_MODE_YET = -99;
    private static final int NETWORK_MODE_UNKNOWN = -100;

    private static final int MODE_3G2G = 0;
    private static final int MODE_3GONLY = 1;
    private static final int MODE_BOTH = 2;

    private int mMode = NO_NETWORK_MODE_YET;
    private int mIntendedMode = NO_NETWORK_MODE_YET;
    private int mInternalState = STATE_INTERMEDIATE;

    private int mState;
    private Handler mHandler;
    private NetworkTypeChangedObserver mNetworkModesChangedObserver;

    public MobileNetworkModeTile(Context context,
            LayoutInflater inflater, QuickSettingsContainerView container,
            QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        updateState();

        // Start observing for changes
        mNetworkModesChangedObserver = new NetworkTypeChangedObserver(mHandler);
        mNetworkModesChangedObserver.startObserving();

        onClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentMode = getCurrentNetworkMode();

                Intent intent = new Intent(ACTION_MODIFY_NETWORK_MODE);
                switch (mMode) {
                    case Phone.NT_MODE_WCDMA_PREF:
                    case Phone.NT_MODE_GSM_UMTS:
                        intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_GSM_ONLY);
                        mInternalState = STATE_TURNING_OFF;
                        mIntendedMode = Phone.NT_MODE_GSM_ONLY;
                        break;
                    case Phone.NT_MODE_WCDMA_ONLY:
                        if (currentMode == MODE_3GONLY) {
                            intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_GSM_ONLY);
                            mInternalState = STATE_TURNING_OFF;
                            mIntendedMode = Phone.NT_MODE_GSM_ONLY;
                        } else {
                            intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_PREF);
                            mInternalState = STATE_TURNING_ON;
                            mIntendedMode = Phone.NT_MODE_WCDMA_PREF;
                        }
                        break;
                    case Phone.NT_MODE_GSM_ONLY:
                        if (currentMode == MODE_3GONLY || currentMode == MODE_BOTH) {
                            intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_ONLY);
                            mInternalState = STATE_TURNING_ON;
                            mIntendedMode = Phone.NT_MODE_WCDMA_ONLY;
                        } else {
                            intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_PREF);
                            mInternalState = STATE_TURNING_ON;
                            mIntendedMode = Phone.NT_MODE_WCDMA_PREF;
                        }
                        break;
                }

                mMode = NETWORK_MODE_UNKNOWN;
                mContext.sendBroadcast(intent);
            }
        };

        onLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.phone", "com.android.phone.Settings");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startSettingsActivity(intent);
                return true;
            }
        };

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getExtras() != null) {
                    mMode = intent.getExtras().getInt(EXTRA_NETWORK_MODE);
                    //Update to actual state
                    mIntendedMode = mMode;
                }

                //need to clear intermediate states and update the tile
                mInternalState = networkModeToState();
                applyNetworkTypeChanges();
            }
        };

        mIntentFilter = new IntentFilter(ACTION_NETWORK_MODE_CHANGED);
    }

    private void applyNetworkTypeChanges(){
        updateState();
        updateQuickSettings();
    }

    protected void updateState() {
        mMode = get2G3G(mContext);
        mState = networkModeToState();

        mLabel = mContext.getString(R.string.quick_settings_network_mode);

        switch (mState) {
            case STATE_DISABLED:
                mDrawable = R.drawable.ic_qs_unexpected_network;
                break;
            case STATE_ENABLED:
                if (mMode == Phone.NT_MODE_WCDMA_ONLY) {
                    mDrawable = R.drawable.ic_qs_3g_on;
                } else {
                    mDrawable = R.drawable.ic_qs_2g3g_on;
                }
                break;
            case STATE_INTERMEDIATE:
                if (mInternalState == STATE_TURNING_ON) {
                    if (mIntendedMode == Phone.NT_MODE_WCDMA_ONLY) {
                        mDrawable = R.drawable.ic_qs_3g_on;
                    } else {
                        mDrawable = R.drawable.ic_qs_2g3g_on;
                    }
                } else {
                    mDrawable = R.drawable.ic_qs_2g3g_off;
                }
                break;
        }
    }

    private static int get2G3G(Context context) {
        int state = 99;
        try {
            state = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
            // Do nothing
        }
        return state;
    }

    private int networkModeToState() {
        if (mInternalState == STATE_TURNING_ON || mInternalState == STATE_TURNING_OFF) {
            return STATE_INTERMEDIATE;
        }

        switch (mMode) {
            case Phone.NT_MODE_WCDMA_PREF:
            case Phone.NT_MODE_WCDMA_ONLY:
            case Phone.NT_MODE_GSM_UMTS:
                return STATE_ENABLED;
            case Phone.NT_MODE_GSM_ONLY:
                return STATE_DISABLED;
            case Phone.NT_MODE_CDMA:
            case Phone.NT_MODE_CDMA_NO_EVDO:
            case Phone.NT_MODE_EVDO_NO_CDMA:
            case Phone.NT_MODE_GLOBAL:
                // need to check wtf is going on
                Log.d(TAG, "Unexpected network mode (" + mMode + ")");
                return STATE_DISABLED;
        }
        return STATE_INTERMEDIATE;
    }

    private int getCurrentNetworkMode() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.EXPANDED_NETWORK_MODE,
                MODE_3G2G);
    }

    /**
     *  ContentObserver to watch for Quick Settings tiles changes
     * @author dvtonder
     *
     */
    private class NetworkTypeChangedObserver extends ContentObserver {
        public NetworkTypeChangedObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            getCurrentNetworkMode();
            applyNetworkTypeChanges();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXPANDED_NETWORK_MODE),
                    false, this);
        }
    }

}
