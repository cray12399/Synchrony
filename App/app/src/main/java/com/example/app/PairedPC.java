package com.example.app;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

// This class notates all current and previously Paired PC's and keeps track of their data.
public class PairedPC {
    private final String PC_NAME;
    private final String PC_ADDRESS;
    private final String PC_TYPE;
    private int notificationID;
    private boolean isActive;
    private long lastSync;
    private transient Thread bluetoothConnectedThread;
    private transient String sendClipboard;

    public PairedPC(Context context, String pcName, String pcAddress, BluetoothDevice device) {
        this.PC_NAME = pcName;
        this.PC_ADDRESS = pcAddress;

        int deviceClass = device.getBluetoothClass().getDeviceClass();
        if (deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP) {
            PC_TYPE = "Laptop";
        } else if (deviceClass == BluetoothClass.Device.COMPUTER_DESKTOP) {
            PC_TYPE = "Desktop";
        } else {
            PC_TYPE = "Other";
        }

        Utils utils = Utils.getInstance(context);
        while (true) {
            notificationID = (int) ((Math.random() * (999999 - 100000)) + 100000);
            boolean foundCopyOfNotificationID = false;
            for (PairedPC pairedPC : utils.getPairedPCS()) {
                if (!pairedPC.getAddress().equals(pcAddress) &&
                        pairedPC.getNotificationID() == notificationID) {
                    foundCopyOfNotificationID = true;
                    break;
                }
            }
            if (!foundCopyOfNotificationID) {
                break;
            }
        }
    }

    public String getName() {
        return PC_NAME;
    }

    public String getAddress() {
        return PC_ADDRESS;
    }

    public String getPCType() {
        return PC_TYPE;
    }

    // Boolean to set the PC as actively trying to sync. The user can set the PC as
    // inactive if they do not wish to sync with it.
    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean activeDevice) {
        isActive = activeDevice;
    }

    // BluetoothConnectedThread used to connect and sync to the Paired PC.
    public Thread getBluetoothConnectedThread() {
        return bluetoothConnectedThread;
    }

    public void setBluetoothConnectedThread(Thread bluetoothCommThread) {
        this.bluetoothConnectedThread = bluetoothCommThread;
        bluetoothCommThread.start();
    }

    public void interruptBluetoothConnectionThread() {
        if (bluetoothConnectedThread != null) {
            bluetoothConnectedThread.interrupt();
        }
    }

    public int getNotificationID() {
        return notificationID;
    }

    // SendClipboard variable is used to notate the user's clipboard so that it can be
    // sent to the client PC.
    public void setSendClipboard(String sendClipboard) {
        this.sendClipboard = sendClipboard;
    }

    public String getSendClipboard() {
        return sendClipboard;
    }
}
