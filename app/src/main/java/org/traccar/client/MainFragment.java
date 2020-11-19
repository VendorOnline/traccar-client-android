/*
 * Copyright 2012 - 2020 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.client;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import com.dev4evolution.vl.BuildConfig;
import com.dev4evolution.vl.R;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MainFragment extends PreferenceFragmentCompat {

    private static final String TAG = MainFragment.class.getSimpleName();

    private static final int ALARM_MANAGER_INTERVAL = 15000;

    public static final String KEY_DEVICE = "id";
    public static final String KEY_URL = "url";
    public static final String KEY_INTERVAL = "interval";
    public static final String KEY_DISTANCE = "distance";
    public static final String KEY_ANGLE = "angle";
    public static final String KEY_ACCURACY = "accuracy";
    public static final String KEY_STATUS = "status";
    public static final String KEY_BUFFER = "buffer";
    public static final String KEY_WAKELOCK = "wakelock";
    private static final int PERMISSIONS_REQUEST_LOCATION = 1;

    private SharedPreferences sharedPreferences;

    private AlarmManager alarmManager;
    private PendingIntent alarmIntent;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (BuildConfig.HIDDEN_APP && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            removeLauncherIcon();
        }

        setHasOptionsMenu(true);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        setPreferencesFromResource(R.xml.preferences, rootKey);

        AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                AdvertisingIdClient.Info idInfo = null;
                try {
                    idInfo = AdvertisingIdClient.getAdvertisingIdInfo(getContext());
                } catch (Exception e) {
                    // IGNORE
                }
                String deviceId = null;
                try{
                    deviceId = idInfo.getId();
                }catch (NullPointerException e){
                    deviceId = UUID.randomUUID().toString();
                }
                return deviceId;
            }

            @Override
            protected void onPostExecute(String deviceId) {
                // SETUP APPLICATION
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_URL, "http://143.202.96.242:3390");
                editor.putString(KEY_DEVICE, deviceId);
                editor.putString(KEY_ACCURACY, "high");
                editor.putString(KEY_INTERVAL, "60");
                editor.putString(KEY_DISTANCE, "0");
                editor.putString(KEY_ANGLE, "0");
                editor.putBoolean(KEY_BUFFER, true);
                editor.putBoolean(KEY_WAKELOCK, true);
                editor.putBoolean(KEY_STATUS, true);
                editor.apply();

                // SET STATUS AS TRUE
                TwoStatePreference preference = findPreference(KEY_STATUS);
                preference.setChecked(true);

                // SET SUMMARY
                findPreference(KEY_DEVICE).setSummary(sharedPreferences.getString(KEY_DEVICE, null));
                findPreference(KEY_URL).setSummary(sharedPreferences.getString(KEY_URL, null));
                findPreference(KEY_ACCURACY).setSummary(sharedPreferences.getString(KEY_ACCURACY, null));
                findPreference(KEY_INTERVAL).setSummary(sharedPreferences.getString(KEY_INTERVAL, null));
                findPreference(KEY_DISTANCE).setSummary(sharedPreferences.getString(KEY_DISTANCE, null));
                findPreference(KEY_ANGLE).setSummary(sharedPreferences.getString(KEY_ANGLE, null));

                // START SERVICE
                Set<String> requiredPermissions = new HashSet<>();
                if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requiredPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                }
                boolean permission = requiredPermissions.isEmpty();
                if (!permission) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(requiredPermissions.toArray(new String[requiredPermissions.size()]), PERMISSIONS_REQUEST_LOCATION);
                    }
                    return;
                }
                startService();
            }
        };
        task.execute();
    }

    private void removeLauncherIcon() {
        String className = MainActivity.class.getCanonicalName().replace(".MainActivity", ".Launcher");
        ComponentName componentName = new ComponentName(getActivity().getPackageName(), className);
        PackageManager packageManager = getActivity().getPackageManager();
        if (packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            packageManager.setComponentEnabledSetting(
                    componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.hidden_alert));
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.status) {
            startActivity(new Intent(getActivity(), StatusActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean granted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }
        if(granted) {
            if (requestCode == PERMISSIONS_REQUEST_LOCATION) {
                startService();
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void startService() {
        // START TRACKING
        alarmManager = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
        alarmIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(getActivity(), AutostartReceiver.class), 0);
        ContextCompat.startForegroundService(getContext(), new Intent(getActivity(), TrackingService.class));
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, ALARM_MANAGER_INTERVAL, ALARM_MANAGER_INTERVAL, alarmIntent);
        ((MainApplication) getActivity().getApplication()).handleRatingFlow(getActivity());
    }
}
