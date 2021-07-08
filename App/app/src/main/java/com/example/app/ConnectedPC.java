package com.example.app;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import static com.example.app.BluetoothSyncWorker.BLUETOOTH_ADDRESS_KEY;

public class ConnectedPC {
    private final String PC_NAME;
    private final String PC_ADDRESS;
    private final String WORK_TAG;
    private boolean isActivePC;

    public ConnectedPC(String pcName, String pcAddress) {
        this.PC_NAME = pcName;
        this.PC_ADDRESS = pcAddress;

        this.WORK_TAG = String.format("SyncService (%s) - %s", PC_ADDRESS, PC_NAME);
    }

    public String getName() {
        return PC_NAME;
    }

    public String getAddress() {
        return PC_ADDRESS;
    }

    public boolean isActivePC() {
        return isActivePC;
    }

    public void setActivePC(boolean activeDevice) {
        isActivePC = activeDevice;
    }

    public void initWork(Context context) {
        Data data = new Data.Builder()
                .putString(BLUETOOTH_ADDRESS_KEY, PC_ADDRESS)
                .build();

        Constraints constraints = new Constraints.Builder().build();

        OneTimeWorkRequest bluetoothWorkRequest = new OneTimeWorkRequest
                .Builder(BluetoothSyncWorker.class)
                .setInputData(data)
                .addTag(WORK_TAG)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueue(bluetoothWorkRequest);
    }

    public void cancelWork(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG);
    }

    public String getWorkTag() {
        return WORK_TAG;
    }
}
