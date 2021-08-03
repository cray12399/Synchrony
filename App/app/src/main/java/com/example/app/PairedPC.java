package com.example.app;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

/**
 * Class notates all current and previously Paired PC's and keeps track of their
 * data and configuration.
 */
public class PairedPC {
    // Constructor variables
    private final String mPCName;
    private final String mPCAddress;
    private final String mPCType;

    // Sync variables. Used track syncing status of PC.
    private transient boolean mIsSyncing;
    private boolean mSyncingAutomatically = true;
    private long mLastSync;

    // Bluetooth connection variables.
    private transient boolean mSocketConnected = false;
    private transient Thread mBluetoothConnectionThread;

    public PairedPC(String pcName, String pcAddress, BluetoothDevice device) {
        // PCName and PCAddress are assigned this way because they become null upon
        // reboot of the app when assigned directly from the device.
        this.mPCName = pcName;
        this.mPCAddress = pcAddress;

        int deviceClass = device.getBluetoothClass().getDeviceClass();
        if (deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP) {
            mPCType = "Laptop";
        } else if (deviceClass == BluetoothClass.Device.COMPUTER_DESKTOP) {
            mPCType = "Desktop";
        } else {
            mPCType = "Other";
        }
    }

    public String getName() {
        return mPCName;
    }

    public String getAddress() {
        return mPCAddress;
    }

    public String getPCType() {
        return mPCType;
    }

    public boolean isSyncing() {
        return mIsSyncing;
    }

    public void setSyncing(boolean syncing) {
        mIsSyncing = syncing;
    }

    public boolean isSyncingAutomatically() {
        return mSyncingAutomatically;
    }

    public void setSyncingAutomatically(boolean mSyncingAutomatically) {
        this.mSyncingAutomatically = mSyncingAutomatically;
    }

    public boolean isSocketConnected() {
        return mSocketConnected;
    }

    public void setSocketConnected(boolean mSocketConnected) {
        this.mSocketConnected = mSocketConnected;
    }
}
