package com.example.app;

import android.bluetooth.BluetoothDevice;

import java.util.UUID;

public class PairedPC {
    private final String PC_NAME;
    private final String PC_ADDRESS;
    private BluetoothDevice associateDevice;
    private boolean isActive;
    private boolean isNotified;
    private boolean initialSyncDone;
    private UUID uuid;
    private transient Thread bluetoothCommThread;

    public PairedPC(String pcName, String pcAddress, BluetoothDevice associateDevice) {
        this.PC_NAME = pcName;
        this.PC_ADDRESS = pcAddress;
        this.associateDevice = associateDevice;
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

    public void setActive(boolean activeDevice) {
        isActive = activeDevice;

        if (!isActive && bluetoothCommThread != null) {
            bluetoothCommThread.interrupt();
            bluetoothCommThread = null;
        }
    }

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
        bluetoothCommThread.start();
    }

    public void stopBluetoothCommThread() {
        if (bluetoothCommThread != null) {
            bluetoothCommThread.interrupt();
        }
    }

    public BluetoothDevice getAssociateDevice() {
        return associateDevice;
    }

    public void setAssociateDevice(BluetoothDevice associateDevice) {
        this.associateDevice = associateDevice;
    }
}
