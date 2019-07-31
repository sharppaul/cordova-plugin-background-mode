package com.etrailer.autostart;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import static android.content.Context.BIND_AUTO_CREATE;

public class AutoStart extends CordovaPlugin {
    private static final String TAG = "AutoStart";

    private boolean autoStartServiceIsBind = false;
    private AutoStartService startService;
    private SharedPreferences mPrefs;
    private String address = "88:6B:0F:07:05:3C";

    public static boolean isActive = false;

    @Override
    protected void pluginInitialize() {
        mPrefs = cordova.getActivity().getSharedPreferences("autoStart", 0);
        AutoStart.isActive = mPrefs.getBoolean("enabled", false);
        stopService("pluginInitialize()");
    }

    /**
     * Executes the request.
     *
     * @param action   The action to execute.
     * @param args     The exec() arguments.
     * @param callback The callback context used when calling back into JavaScript.
     * @return Returning false results in a "MethodNotFound" error.
     * @throws JSONException The exception thrown when arguments are incorrect and
     *                       not caught.
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callback) throws JSONException {
        if (action.equalsIgnoreCase("start")) {
            Log.d(TAG, "start()");
            switchAutoStart(true);
            callback.success();
            return true;
        }

        if (action.equalsIgnoreCase("stop")) {
            Log.d(TAG, "stop");
            switchAutoStart(false);
            callback.success();
            return true;
        }

        if (action.equalsIgnoreCase("address")) {
            Log.d(TAG, "address");
            try {
                this.address = args.getString(0);
                callback.success();
                return true;

            } catch (Exception e) {
                callback.error("invalid argument");
                Log.e(TAG, "Error setting address: ", e);
                return false;
            }
        }

        if (action.equalsIgnoreCase("supportsBle")) {
            Log.d(TAG, "supportsBle()");
            if (isBleSupported()) {
                callback.success("BLE supported");
                return true;
            } else {
                callback.error("BLE not supported");
                return false;
            }
        }

        return true;
    }

    @Override
    public void onPause(boolean multitasking) {
        // startService("onPause");
    }

    @Override
    public void onResume(boolean multitasking) {
        stopService("onResume");
    }

    @Override
    public void onDestroy() {
        startService("onDestroy");
    }

    private void switchAutoStart(boolean onOrOff) {
        SharedPreferences.Editor mEditor = mPrefs.edit();
        mEditor.putBoolean("enabled", onOrOff).apply();
        AutoStart.isActive = onOrOff;
    }

    private void startService(String log) {
        Log.d(TAG, "startService() - " + log);
        if (this.address != null && AutoStart.isActive) {
            Activity context = cordova.getActivity();
            this.startAutoStartService(context, this.address);
        }
    }

    private void stopService(String log) {
        Log.d(TAG, "stopService() - " + log);
        if (this.address != null && AutoStart.isActive) {
            Activity context = cordova.getActivity();
            this.stopAutoStartService(context);
        }
    }

    /**
     * Start bluetooth autostart service
     */
    private void startAutoStartService(Activity context, String address) {
        if (autoStartServiceIsBind)
            return;

        Intent intent = new Intent(context, AutoStartService.class);

        intent.putExtra("address", address);
        try {
            context.bindService(intent, autoStartConnection, BIND_AUTO_CREATE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }

        } catch (Exception e) {
            Log.e("AutoStart", "Exception", e);
        }

        autoStartServiceIsBind = true;
    }

    /**
     * Stop bluetooth autostart service
     */
    private void stopAutoStartService(Activity context) {
        Intent intent = new Intent(context, AutoStartService.class);

        if (!autoStartServiceIsBind)
            return;

        context.unbindService(autoStartConnection);
        context.stopService(intent);

        autoStartServiceIsBind = false;

        stopAutoStartService(context);
    }

    private boolean isBleSupported() {
        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        Activity context = cordova.getActivity();
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private final ServiceConnection autoStartConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AutoStartService.AutoStartBinder binder = (AutoStartService.AutoStartBinder) service;
            AutoStart.this.startService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("AutoStart", "Service disconnected");
        }
    };
}
