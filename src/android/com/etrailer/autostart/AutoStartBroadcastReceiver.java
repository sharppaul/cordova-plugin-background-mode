package com.etrailer.autostart;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;

public class AutoStartBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "AutoStartBrdcstReceiver";

    public static ArrayList<String> foundAddresses = new ArrayList<>();
    public static boolean killAutoStart = false;

    public static void clearFoundList() {
        AutoStartBroadcastReceiver.foundAddresses = new ArrayList<>();
    }

    public static boolean foundDevice(String address) {
        return AutoStartBroadcastReceiver.foundAddresses.contains(address);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String log = "Action: " + intent.getAction() + "\n" + "URI: " + intent.toUri(Intent.URI_INTENT_SCHEME) + "\n";

        Log.d(TAG, log);

        String action = intent.getAction();

        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            // Get the BluetoothDevice object from the Intent
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            // Add the name and address to list.
            AutoStartBroadcastReceiver.foundAddresses.add(device.getAddress());
        }

        if (action != null && action.equals(AutoStartService.ACTION_STOP_WATCHING)) {
            killAutoStart = true;
        }
    }
}
