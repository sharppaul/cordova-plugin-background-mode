package com.etrailer.autostart;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Binder;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;
import android.os.Bundle;
import android.util.Log;

import com.etrailer.smarttrailer.R;

import java.util.Set;

public class AutoStartService extends Service {
    // Binder given to clients
    private static final String TAG = "AutoStartService";
    private String address = "";
    private final IBinder binder = new AutoStartBinder();

    public static final String ACTION_STOP_WATCHING = "ACTION_STOP_WATCHING";
    private static final String EXTRA_NOTIFICATION_ID = "-574542254";

    // Fixed ID for the 'foreground' notification
    private static final int NOTIFICATION_ID = -574543254;

    private static final String NOTIFICATION_CHANNEL_ID = "ETRAILER_CHANNEL_AUTOSTART";

    /**
     * Class used for the client Binder. Because we know this service always runs in
     * the same process as its clients, we don't need to deal with IPC.
     */
    public class AutoStartBinder extends Binder {
        public AutoStartService getService() {
            // Return this instance of LocalService so clients can call public methods
            return AutoStartService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        Bundle bundle = intent.getExtras();
        // Field to store bluetooth address
        assert bundle != null;
        address = bundle.getString("address");

        if (!isBleSupported()) {
            this.stopSelf();
            return START_NOT_STICKY;
        }

        this.startForeground(4201337, makeNotification());
        this.scanForBtDevices();

        return START_STICKY;
    }

    private boolean isBleSupported() {
        // Use this check to determine whether BLE is supported on the device. Then
        // you can selectively disable BLE-related features.
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private void scanForBtDevices() {
        Log.d(TAG, "Service started, looking for: " + this.address);

        new Thread(new Runnable() {
            private void stopService(String reason) {
                Log.d(TAG, "stopping self. " + reason);
                AutoStartBroadcastReceiver.killAutoStart = false;
                AutoStartService.this.stopForeground(true);
                AutoStartService.this.stopSelf();
            }

            private void stopService() {
                stopService("");
            }

            public void run() {
                BluetoothAdapter bluetoothAdapter;

                // Initializes Bluetooth adapter.
                final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(
                        Context.BLUETOOTH_SERVICE);

                if (bluetoothManager == null) {
                    stopService("Bluetooth manager not found");
                }

                bluetoothAdapter = bluetoothManager.getAdapter();

                // Ensures Bluetooth is available on the device and it is enabled. If not,
                // displays a dialog requesting user permission to enable Bluetooth.
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                    stopService("Bluetooth not enabled.");
                }

                boolean foundAddress = false;
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                for (BluetoothDevice pairedDevice : pairedDevices) {
                    if (pairedDevice.getAddress().equals(AutoStartService.this.address)) {
                        foundAddress = true;
                        break;
                    }
                }
                if (!foundAddress) {
                    stopService("Device not paired.");
                }

                long lastCleared = System.currentTimeMillis();
                AutoStartBroadcastReceiver.clearFoundList();

                bluetoothAdapter.startDiscovery();

                while (true) {
                    if (AutoStartBroadcastReceiver.foundDevice(address)) {
                        Log.d(TAG, "FOUND DEVICE!");
                        stopService();
                    }

                    if (AutoStartBroadcastReceiver.killAutoStart || AutoStart.isActive) {
                        stopService("Self destroy.");
                        return;
                    }
                }

            }
        }).start();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Service notification";
            String description = "Shows the service that the app uses to launch when there's a SMART-Trailer in the vicinity.";
            int importance = NotificationManager.IMPORTANCE_LOW;

            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification makeNotification() {
        Intent snoozeIntent = new Intent(this, AutoStartBroadcastReceiver.class);
        snoozeIntent.setAction(ACTION_STOP_WATCHING);
        snoozeIntent.putExtra(EXTRA_NOTIFICATION_ID, 0);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(this, 0, snoozeIntent, 0);

        String title = "AutoStart service";
        String text = "Waiting for SMART-Trailer connection.";

        try {
            Log.d(TAG, "Creating notification " + title);
        } catch (Exception e) {
            // nothing, probably means title is empty.
        }

        Context context = getApplicationContext();
        String pkgName = context.getPackageName();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkgName);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title).setContentText(text).setOngoing(true).setSmallIcon(R.drawable.notification_icon)
                .setPriority(Notification.PRIORITY_MIN).setOngoing(true)
                .addAction(R.drawable.ic_action_remove, "stop watching", snoozePendingIntent);

        if (text.contains("\n")) {
            notification.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
        }

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            notification.setContentIntent(contentIntent);
        }

        return notification.build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}