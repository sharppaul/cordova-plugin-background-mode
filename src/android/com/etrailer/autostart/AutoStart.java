package com.etrailer.autostart;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
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

    private String macAddress = "88:6B:0F:07:05:3C";

    public static boolean isActive = false;

    @Override
    protected void pluginInitialize() {
        // TODO: stuff
    }

    /**
     * Executes the request.
     *
     * @param action   The action to execute.
     * @param args     The exec() arguments.
     * @param callback The callback context used when calling back into JavaScript.
     *
     * @return Returning false results in a "MethodNotFound" error.
     *
     * @throws JSONException
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callback) throws JSONException {
        if (action.equalsIgnoreCase("start")) {
            callback.success();
            return true;
        }

        if (action.equalsIgnoreCase("update")) {
            Log.d("AutoStart", "started");
            callback.success();
            return true;
        }

        return true;
    }

    @Override
    public void onPause(boolean multitasking) {
        Log.d(TAG, "onPause");
        AutoStart.isActive = false;
        if(this.macAddress != null){
            Activity context = cordova.getActivity();
            this.startAutoStartService(context, this.macAddress);
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        Log.d(TAG, "onResume");
        AutoStart.isActive = true;
        Activity context = cordova.getActivity();
        this.stopAutoStartService(context);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        AutoStart.isActive = false;
        if(this.macAddress != null){
            Activity context = cordova.getActivity();
            this.startAutoStartService(context, this.macAddress);
        }
    }

    /**
     * Start bluetooth autostart service
     */
    private void startAutoStartService(Activity context, String macAddress) {
        if (autoStartServiceIsBind)
            return;

        Intent intent = new Intent(context, AutoStartService.class);

        intent.putExtra("mac", macAddress);
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
    public void stopAutoStartService(Activity context) {
        Intent intent = new Intent(context, AutoStartService.class);

        if (!autoStartServiceIsBind)
            return;

        context.unbindService(autoStartConnection);
        context.stopService(intent);

        autoStartServiceIsBind = false;

        stopAutoStartService(context);
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
