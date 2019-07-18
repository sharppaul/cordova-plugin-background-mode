package com.etrailer.autostart;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.etrailer.backgroundmode.BackgroundMode;
import com.etrailer.backgroundmode.ForegroundService;

import static android.content.Context.BIND_AUTO_CREATE;

public class MyBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "MyBroadcastReceiver";
    public static boolean killAutoStart = false;
    @Override
    public void onReceive(Context context, Intent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("Action: ").append(intent.getAction()).append("\n");
        sb.append("URI: ").append(intent.toUri(Intent.URI_INTENT_SCHEME)).append("\n");
        String log = sb.toString();

        Log.d(TAG, log);

        if(intent.getAction() != null && intent.getAction().equals(AutoStartService.ACTION_STOP_WATCHING)){
            killAutoStart = true;
        }
    }
}
