package com.example.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.IOException;
import java.util.UUID;

//TODO: Extend BluetoothDevice class

public class PairedPC {
    private final String PC_NAME;
    private final String PC_ADDRESS;
    private boolean isActive;
    private boolean isConnected;
    private boolean isNotified;
    private boolean initialSyncDone;
    private UUID uuid;
    private Thread bluetoothCommThread;

    public PairedPC(String pcName, String pcAddress, boolean isConnected) {
        this.PC_NAME = pcName;
        this.PC_ADDRESS = pcAddress;
        this.isConnected = isConnected;
    }

    public String getName() {
        return PC_NAME;
    }

    public String getAddress() {
        return PC_ADDRESS;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isConnected() {return isConnected;}

    public void setActive(boolean activeDevice) {
        isActive = activeDevice;
    }

    public void setConnected(boolean connected) {isConnected = connected;}

    public boolean isNotified() {
        return isNotified;
    }

    public void setNotified(boolean notified) {
        isNotified = notified;
    }

    public boolean isInitialSyncDone() {
        return initialSyncDone;
    }

    public void setInitialSyncDone(boolean initialSyncDone) {
        this.initialSyncDone = initialSyncDone;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = UUID.fromString(uuid);
    }

    public Thread getBluetoothCommThread() {
        return bluetoothCommThread;
    }

    public void setBluetoothCommThread(Thread bluetoothCommThread) {
        this.bluetoothCommThread = bluetoothCommThread;
    }

    private static class BluetoothCommThread extends Thread {
        private final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();
        private final Context CONTEXT;
        private final BluetoothDevice DEVICE;
        private final BluetoothSocket BLUETOOTH_SOCKET;
        private java.util.UUID UUID;


        public BluetoothCommThread(Context context, BluetoothDevice device) {
            this.CONTEXT = context;
            this.DEVICE = device;

            BluetoothSocket tmp = null;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            BLUETOOTH_SOCKET = tmp;

        }

        @Override
        public void run() {
            BLUETOOTH_ADAPTER.cancelDiscovery();

            try {
                BLUETOOTH_SOCKET.connect();
            } catch (IOException connectException) {
                try {
                    BLUETOOTH_SOCKET.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
                return;
            }

            manageConnectedSocket(BLUETOOTH_SOCKET);
        }

        public void manageConnectedSocket(BluetoothSocket socket) {
        }

        public void cancel() {
            try {
                BLUETOOTH_SOCKET.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
