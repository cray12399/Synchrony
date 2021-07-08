package com.example.app;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.CancellationException;

public class BluetoothSyncWorker extends Worker {

    public static final String BLUETOOTH_ADDRESS_KEY = "bluetoothDevice";

    private final BluetoothAdapter BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();

    public BluetoothSyncWorker(@NonNull Context context,
                               @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        String bluetoothAddress = inputData.getString(BLUETOOTH_ADDRESS_KEY);

        do {
            System.out.println(bluetoothAddress);
            SystemClock.sleep(3000);

        } while (!isStopped() && isConnected(bluetoothAddress));

        System.out.println("Canceled!");

        return Result.failure();
    }

    private boolean isConnected(String address) {
        Set<BluetoothDevice> pairedDevices = BLUETOOTH_ADAPTER.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getAddress().equals(address)) {
                Method method = null;
                try {
                    method = device.getClass().getMethod("isConnected", (Class[]) null);
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }

                boolean connected = false;
                try {
                    connected = (boolean) method.invoke(device, (Object[]) null);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }

                return connected;
            }
        }
        return false;
    }
}
