package com.example.app;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.SystemClock;

class BluetoothCommThread extends Thread {
    private final Context CONTEXT;
    private final BluetoothDevice DEVICE;

    public BluetoothCommThread(Context context, BluetoothDevice device) {
        this.CONTEXT = context;
        this.DEVICE = device;
    }

    @Override
    public void run() {
        while (!interrupted()) {
            System.out.println("Working...");
            SystemClock.sleep(3000);
        }
        System.out.println("Canceled.");
    }
}
