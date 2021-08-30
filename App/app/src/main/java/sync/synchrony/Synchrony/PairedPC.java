package sync.synchrony.Synchrony;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

import java.util.Date;

/**
 * Class notates all current and previously Paired PC's and keeps track of their
 * data and configuration.
 */
public class PairedPC {
    private final String mPCName;
    private final String mPCAddress;
    private final String mPCType;

    // Tracks whether the app should try to connect to the paired PC or not.
    private transient boolean mConnecting;

    // Tracks whether the app should try to connect to the paired PC automatically or not.
    private boolean connectAutomatically = true;

    // Tracks the last time the PC was synced.
    private Date mLastSync;

    // Bluetooth connection variables.
    private transient boolean mSocketConnected = false;

    private boolean mCurrentlySyncing = false;

    public PairedPC(String pcName, String pcAddress, BluetoothDevice bluetoothDevice) {
        // PCName and PCAddress are assigned this way because they become null upon
        // reboot of the app when assigned directly from the device.
        this.mPCName = pcName;
        this.mPCAddress = pcAddress;

        int deviceClass = bluetoothDevice.getBluetoothClass().getDeviceClass();
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

    public boolean isConnecting() {
        return mConnecting;
    }

    public void setConnecting(boolean connecting) {
        mConnecting = connecting;
    }

    public boolean isConnectingAutomatically() {
        return connectAutomatically;
    }

    public void setConnectionAutomatically(boolean connectAutomatically) {
        this.connectAutomatically = connectAutomatically;
    }

    public boolean isSyncSocketConnected() {
        return mSocketConnected;
    }

    public void setSyncSocketConnected(boolean mSocketConnected) {
        this.mSocketConnected = mSocketConnected;
    }

    public Date getLastSync() {
        return mLastSync;
    }

    public void setLastSync(Date lastSync) {
        this.mLastSync = lastSync;
    }

    public boolean isCurrentlySyncing() {
        return mCurrentlySyncing;
    }

    public void setCurrentlySyncing(boolean mCurrentlySyncing) {
        this.mCurrentlySyncing = mCurrentlySyncing;
    }
}
