package com.example.app;

import static com.example.app.App.connectedDevicesChannelID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.Objects;

/**
 * Service class manages all of the background connection activity. Assigns
 * BluetoothConnectionThreads for Paired PC's
 */
public class BluetoothConnectService extends Service {
    // Logging tag variables
    private static final String TAG = "BluetoothConnectService";

    // Receiver variable
    private BroadcastReceiver mBluetoothConnectReceiver;

    // Main thread for the service.
    private PCListenerThread mPCListenerThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Summary notification which holds notifications for BluetoothConnectionThreads
        String CONNECTED_PC_GROUP = "connectedPCS";
        NotificationCompat.Builder summaryNotificationBuilder =
                new NotificationCompat.Builder(this,
                        connectedDevicesChannelID)
                        .setSmallIcon(R.drawable.ic_placeholder_logo)
                        .setContentTitle("Connect Service Background")
                        .setGroup(CONNECTED_PC_GROUP)
                        .setGroupSummary(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOngoing(true);
        int summaryNotificationID = 69;

        startForeground(summaryNotificationID, summaryNotificationBuilder.build());
        Log.d(TAG, "onStartCommand: BluetoothConnectService started in the foreground!");

        searchForCompatibleDevices();

        Utils.setForegroundRunning(true);

        mPCListenerThread = new PCListenerThread(getApplicationContext());
        mPCListenerThread.start();

        registerReceiver();

        return START_STICKY_COMPATIBILITY;
    }

    /**
     * Search for all compatible bonded devices and add them to pairedPCS. Then,
     * populate the pcRecViewAdapter in MainActivity.
     */
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
                MainActivity.addToMainActivity(this, bluetoothDevice);

                // If the paired PC is set to connect automatically, automatically start connecting.
                if (Objects.requireNonNull(
                        Utils.getPairedPC(bluetoothDevice.getAddress()))
                        .isConnectingAutomatically()) {
                    Objects.requireNonNull(Utils.getPairedPC(bluetoothDevice.getAddress()))
                            .setConnecting(true);
                    Log.d(TAG, String.format("searchForCompatibleDevices: " +
                            "Automatically connecting device: %s!", deviceTag));

                    // Notify the MainActivity that it is connecting.
                    Utils.broadcastConnectChange(getApplicationContext(),
                            bluetoothDevice.getAddress());
                    Log.d(TAG, String.format("searchForCompatibleDevices: " +
                                    "Notified main activity of connecting status for device: %s!",
                            deviceTag));
                }
            }
        }
    }

    private void registerReceiver() {
        mBluetoothConnectReceiver = new BroadcastReceiver() {
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

                        // If the PC is set to connect automatically, then connect it automatically.
                        if (Objects.requireNonNull(Utils.getPairedPC(bluetoothDevice.getAddress()))
                                .isConnectingAutomatically()) {
                            Objects.requireNonNull(Utils.getPairedPC(bluetoothDevice.getAddress()))
                                    .setConnecting(true);

                            Utils.broadcastConnectChange(getApplicationContext(),
                                    bluetoothDevice.getAddress());
                        }
                        break;
                    }

                    // If a PC is disconnected, stop connection.
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED: {
                        BluetoothDevice bluetoothDevice = intent
                                .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        String deviceAddress = bluetoothDevice.getAddress();
                        String deviceName = bluetoothDevice.getName();
                        String deviceTag = String.format("%s (%s)", deviceName, deviceAddress);

                        Log.d(TAG, String.format("onReceive: " +
                                        "Device disconnected! Stopping connection for device: %s!",
                                deviceTag));

                        stopConnection(bluetoothDevice);

                        Utils.broadcastConnectChange(getApplicationContext(),
                                bluetoothDevice.getAddress());
                        break;
                    }

                    // If a PC has been marked as no longer connecting, stop connection.
                    case Utils.CONNECT_CHANGED_ACTION: {
                        String pcAddress = intent.getStringExtra(Utils.RECIPIENT_ADDRESS_KEY);
                        if (!Objects.requireNonNull(Utils.getPairedPC(pcAddress)).isConnecting()) {
                            BluetoothAdapter bluetoothAdapter = BluetoothAdapter
                                    .getDefaultAdapter();
                            for (BluetoothDevice bluetoothDevice : bluetoothAdapter
                                    .getBondedDevices()) {
                                if (bluetoothDevice.getAddress().equals(pcAddress)) {
                                    stopConnection(bluetoothDevice);
                                    break;
                                }
                            }
                        }
                        break;
                    }

                    // If bluetooth is disabled, stop all connection threads.
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        Log.d(TAG, "onReceive: " +
                                "Bluetooth disabled! Stopping connection threads!");

                        int extraState = intent.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE, -1);
                        if (extraState == BluetoothAdapter.STATE_OFF) {
                            for (BluetoothConnectionThread bluetoothConnectionThread :
                                    Utils.getCurrentlyRunningThreads()) {
                                bluetoothConnectionThread.interrupt();
                            }
                        }
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(Utils.CONNECT_CHANGED_ACTION);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        getApplicationContext().registerReceiver(mBluetoothConnectReceiver, filter);
    }

    /**
     * Interrupts all associated BluetoothConnectionThreads for device and
     * stops background connections.
     */
    private void stopConnection(BluetoothDevice bluetoothDevice) {
        String deviceAddress = bluetoothDevice.getAddress();
        String deviceName = bluetoothDevice.getName();

        // Interrupt all associated bluetooth connection threads for the device.
        for (BluetoothConnectionThread bluetoothConnectionThread :
                Utils.getCurrentlyRunningThreads()) {
            if (bluetoothConnectionThread.getDeviceAddress().equals(deviceAddress)) {
                bluetoothConnectionThread.interrupt();
            }

            // Mark PC as no longer connecting.
            Objects.requireNonNull(Utils.getPairedPC(deviceAddress)).setConnecting(false);

            String deviceTag = String.format("%s (%s)", deviceName, deviceAddress);
            Log.d(TAG, String.format("stopConnection: " +
                    "Connection stopped for device: %s!", deviceTag));
        }
    }

    /**
     * Creates a new BluetoothConnectionThread for a device and starts connection in the background
     */
    private void startConnection(Context context, BluetoothSocket bluetoothSocket) {
        String connectedSocketAddress = bluetoothSocket.getRemoteDevice().getAddress();
        String connectedSocketName = bluetoothSocket.getRemoteDevice().getName();

        // Just in case there are any currently running threads for the PC, interrupt them.
        for (BluetoothConnectionThread bluetoothConnectionThread :
                Utils.getCurrentlyRunningThreads()) {
            if (bluetoothConnectionThread.getDeviceAddress()
                    .equals(connectedSocketAddress)) {
                bluetoothConnectionThread.interrupt();
            }
        }

        // Create a bluetoothConnectionThread for the PC.
        for (BluetoothDevice bluetoothDevice :
                BluetoothAdapter.getDefaultAdapter().getBondedDevices()) {
            if (bluetoothDevice.getAddress().equals(connectedSocketAddress)) {
                Utils.addToCurrentlyRunningThreads(
                        new BluetoothConnectionThread(context, bluetoothDevice, bluetoothSocket));
                break;
            }
        }

        // Mark the PC as connecting.
        Objects.requireNonNull(Utils.getPairedPC(connectedSocketAddress)).setConnecting(true);

        String deviceTag = String.format("%s (%s)", connectedSocketName, connectedSocketAddress);
        Log.d(TAG, String.format("startConnection: Connection started for device: %s!", deviceTag));
    }

    @Override
    public void onDestroy() {
        // If this service is destroyed, cancel all the current BluetoothConnectionThreads
        // and stop the foreground service.
        Log.d(TAG, "onDestroy: " +
                "BluetoothConnectService destroyed! Stopping BluetoothConnected threads.");
        for (BluetoothConnectionThread bluetoothConnectionThread :
                Utils.getCurrentlyRunningThreads()) {
            bluetoothConnectionThread.interrupt();
        }

        Utils.setForegroundRunning(false);
        mPCListenerThread.interrupt();
        unregisterReceiver(mBluetoothConnectReceiver);
        stopForeground(true);
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Thread responsible for listening for compatible devices and assigning connection
     * threads as needed
     */
    private class PCListenerThread extends Thread {
        private BluetoothServerSocket mBluetoothServerSocket;
        private final Context mContext;

        public PCListenerThread(Context context) {
            mContext = context;
            mBluetoothServerSocket = getBluetoothServerSocket();
        }

        @Override
        public void run() {
            while (!interrupted()) {
                BluetoothSocket bluetoothSocket = null;
                try {
                    if (!interrupted() && mBluetoothServerSocket != null) {
                        if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                            Log.d(TAG, "run: Listening for devices...");
                            bluetoothSocket = mBluetoothServerSocket.accept();
                        }
                    } else if (!interrupted() && mBluetoothServerSocket == null) {
                        mBluetoothServerSocket = getBluetoothServerSocket();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "run: Error accepting device connection:", e);
                }

                if (bluetoothSocket != null) {
                    Log.d(TAG, "run: Established connection with a device!");

                    String connectedSocketAddress = bluetoothSocket.getRemoteDevice().getAddress();
                    String connectedSocketName = bluetoothSocket.getRemoteDevice().getName();
                    String deviceTag = String.format("%s (%s)", connectedSocketName,
                            connectedSocketAddress);

                    // Check if device is in pairedPCS and if it is connecting. If so, start the
                    // connection for it.
                    boolean closeSocket = true;
                    for (PairedPC pairedPC : Utils.getPairedPCS()) {
                        if (pairedPC.getAddress().equals(connectedSocketAddress)) {
                            if (pairedPC.isConnecting()) {
                                Log.d(TAG, String.format("run: " +
                                        "Starting connection for device: %s...", deviceTag));
                                startConnection(mContext, bluetoothSocket);
                                closeSocket = false;
                            }
                        }
                    }

                    // Close the socket if the device is not in paired PC's or the
                    // paired PC is not set as connecting.
                    if (closeSocket) {
                        try {
                            bluetoothSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        public BluetoothServerSocket getBluetoothServerSocket() {
            BluetoothServerSocket tmp = null;
            try {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                Log.d(TAG, "getBluetoothServerSocket: " +
                        "Creating bluetooth server socket for device connections.");
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        mContext.getString(R.string.app_name), Utils.getUuid());
                Log.d(TAG, "getBluetoothServerSocket: " +
                        "Created bluetooth server socket for device connections!");

            } catch (IOException e) {
                // Timeout in case of failure to create BluetoothServerSocket.
                SystemClock.sleep(5000);
            }
            mBluetoothServerSocket = tmp;

            return mBluetoothServerSocket;
        }
    }
}
