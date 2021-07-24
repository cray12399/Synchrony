package com.example.app;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import java.util.concurrent.atomic.AtomicInteger;

public class PairedPC {
    private transient final Context CONTEXT;
    private final String PC_NAME;
    private final String PC_ADDRESS;
    private BluetoothDevice associateDevice;
    private boolean isActive;
    private transient Thread bluetoothConnectionThread;

    public PairedPC(Context context, String pcName, String pcAddress,
                    BluetoothDevice associateDevice) {
        this.CONTEXT = context;
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
    }

    public Thread getBluetoothConnectionThread() {
        return bluetoothConnectionThread;
    }

    public void setBluetoothConnectionThread(Thread bluetoothCommThread) {
        this.bluetoothConnectionThread = bluetoothCommThread;
        bluetoothCommThread.start();
    }

    public void interruptBluetoothConnectionThread() {
        if (bluetoothConnectionThread != null) {
            bluetoothConnectionThread.interrupt();
        }
    }

    public BluetoothDevice getAssociateDevice() {
        return associateDevice;
    }

    public void setAssociateDevice(BluetoothDevice associateDevice) {
        this.associateDevice = associateDevice;
    }

    public static class NotificationID {
        private final static AtomicInteger notificationID = new AtomicInteger(0);
        public static int getID() {
            return notificationID.incrementAndGet();
        }
    }
}
