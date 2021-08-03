package com.example.app;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Objects;

import static com.example.app.App.connectedDevicesChannelID;

/**
 * Service class manages all of the background syncing activity. It manages the
 * connection threads for Paired PC's
 */
public class BluetoothSyncService extends Service {
    // Logging tag variables
    private static final String TAG = "BluetoothSyncService";

    // Receiver variable
    private BroadcastReceiver mBluetoothSyncReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Summary notification which holds notifications for BluetoothConnectionThreads
        String CONNECTED_PC_GROUP = "connectedPCS";
        NotificationCompat.Builder summaryNotificationBuilder =
                new NotificationCompat.Builder(this,
                        connectedDevicesChannelID)
                        .setSmallIcon(R.drawable.ic_placeholder_logo)
                        .setContentTitle("Sync Service Background")
                        .setGroup(CONNECTED_PC_GROUP)
                        .setGroupSummary(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOngoing(true);
        int summaryNotificationID = 69;

        startForeground(summaryNotificationID, summaryNotificationBuilder.build());
        Log.d(TAG, "onStartCommand: BluetoothSyncService started in the foreground!");

        searchForCompatibleDevices();

        Utils.setForegroundRunning(true);

        registerReceiver();

        return START_STICKY_COMPATIBILITY;
    }

    /** Method search for all compatible bonded devices and add them to pairedPCS. Then,
     *  populate the pcRecViewAdapter in MainActivity*/
    private void searchForCompatibleDevices() {
        // Iterate all bonded bluetooth devices to find compatible PC's
        for (BluetoothDevice bluetoothDevice :
                BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
            String deviceTag = String.format("%s (%s)", bluetoothDevice.getName(),
                    bluetoothDevice.getAddress());

            // If a device is not in pairedPCS, try to add it.
            if (!Utils.inPairedPCS(bluetoothDevice.getAddress())) {
                Utils.addToPairedPCS(bluetoothDevice);
            }

            // If a PC is currently connected and is in pairedPCS.
            if (Utils.isConnected(bluetoothDevice.getAddress()) &&
                    Utils.inPairedPCS(bluetoothDevice.getAddress())) {
                // Add it to the MainActivity
                MainActivity.addToMainActivity(getApplicationContext(), bluetoothDevice);

                // If the paired PC is set to sync automatically, automatically start syncing.
                if (Objects.requireNonNull(
                        Utils.getPairedPC(bluetoothDevice.getAddress())).isSyncingAutomatically()) {
                    startSyncing(this, bluetoothDevice.getAddress());
                    Log.d(TAG, String.format("searchForCompatibleDevices: " +
                            "Automatically syncing device: %s!", deviceTag));

                    // Notify the MainActivity that it is syncing.
                    Utils.notifySyncChanged(getApplicationContext(), bluetoothDevice.getAddress());
                    Log.d(TAG, String.format("searchForCompatibleDevices: " +
                            "Notified main activity of syncing for device: %s!", deviceTag));
                }
            }
        }
    }

    private void registerReceiver() {
        mBluetoothSyncReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case BluetoothDevice.ACTION_ACL_CONNECTED: {
                        BluetoothDevice bluetoothDevice = intent
                                .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        // If a newly connected device is not in pairedPCS, see if it is
                        // compatible and add it.
                        if (!Utils.inPairedPCS(bluetoothDevice.getAddress())) {
                            Utils.addToPairedPCS(bluetoothDevice);
                        }

                        // If the device is in pairedPCS, add it to pcRecView in MainActivity.
                        if (Utils.inPairedPCS(bluetoothDevice.getAddress())) {
                            MainActivity.addToMainActivity(getApplicationContext(),
                                    bluetoothDevice);
                        }

                        // If the device is set to sync automatically, then sync it automatically.
                        if (Objects.requireNonNull(Utils.getPairedPC(bluetoothDevice.getAddress()))
                                .isSyncingAutomatically()) {
                            startSyncing(context, bluetoothDevice.getAddress());

                            Utils.notifySyncChanged(getApplicationContext(),
                                    bluetoothDevice.getAddress());
                        }
                        break;
                    }

                    case BluetoothDevice.ACTION_ACL_DISCONNECTED: {
                        BluetoothDevice bluetoothDevice = intent
                                .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        stopSyncing(bluetoothDevice.getAddress());

                        Utils.notifySyncChanged(getApplicationContext(),
                                bluetoothDevice.getAddress());
                        break;
                    }

                    case Utils.SYNC_CHANGED_ACTION: {
                        String pcAddress = intent.getStringExtra(Utils.RECIPIENT_ADDRESS_KEY);
                        if (Objects.requireNonNull(Utils.getPairedPC(pcAddress)).isSyncing()) {
                            if (Utils.inPairedPCS(pcAddress)) {
                                startSyncing(context, pcAddress);
                            }
                        } else {
                            stopSyncing(pcAddress);
                        }
                        break;
                    }

                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        int extraState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                -1);
                        if (extraState == BluetoothAdapter.STATE_OFF) {
                            for (BluetoothConnectionThread bluetoothConnectionThread :
                                    Utils.getCurrentlyRunningThreads()) {
                                bluetoothConnectionThread.interrupt();
                            }
                        }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(Utils.SYNC_CHANGED_ACTION);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        getApplicationContext().registerReceiver(mBluetoothSyncReceiver, filter);
    }

    /** Interrupts all associated BluetoothConnectionThreads for device and
     *  stops background syncing. */
    private void stopSyncing(String pcAddress) {
        for (BluetoothConnectionThread bluetoothConnectionThread :
                Utils.getCurrentlyRunningThreads()) {
            if (bluetoothConnectionThread.getDeviceAddress()
                    .equals(pcAddress)) {
                bluetoothConnectionThread.interrupt();
                NotificationManagerCompat notificationManager =
                        NotificationManagerCompat.from(getApplicationContext());
                notificationManager.cancel(pcAddress,
                        bluetoothConnectionThread.getNotificationID());

                Objects.requireNonNull(Utils.getPairedPC(pcAddress)).setSyncing(false);
            }
        }
    }

    /** Creates a new BluetoothConnectionThread for a device and starts syncing in the background */
    private void startSyncing(Context context, String pcAddress) {
        // Just in case there are any currently running threads for the device, interrupt them.
        for (BluetoothConnectionThread bluetoothConnectionThread :
                Utils.getCurrentlyRunningThreads()) {
            if (bluetoothConnectionThread.getDeviceAddress()
                    .equals(pcAddress)) {
                bluetoothConnectionThread.interrupt();
            }
        }

        // Create a bluetoothConnectionThread for the device.
        for (BluetoothDevice bluetoothDevice :
                BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
            if (bluetoothDevice.getAddress().equals(pcAddress)) {
                Utils.addToCurrentlyRunningThreads(
                        new BluetoothConnectionThread(context,
                                bluetoothDevice));
                break;
            }
        }

        Objects.requireNonNull(Utils.getPairedPC(pcAddress)).setSyncing(true);
    }

    @Override
    public void onDestroy() {
        // If this service is destroyed, cancel all the current BluetoothConnectionThreads
        // and stop the foreground service.
        Log.d(TAG, "onDestroy: " +
                "BluetoothSyncService destroyed! Stopping BluetoothConnected threads.");
        for (BluetoothConnectionThread bluetoothConnectionThread :
                Utils.getCurrentlyRunningThreads()) {
            bluetoothConnectionThread.interrupt();
        }

        Utils.setForegroundRunning(false);
        unregisterReceiver(mBluetoothSyncReceiver);
        stopForeground(true);
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
