package com.example.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This is the main utility class of the app. Its main purpose is to manage the app's
 * Shared Preferences, manage the app's PairedPC's, and provide important app-wide functions.
 */
public class Utils {
    // Action and Key variables.
    public static final String SYNC_CHANGED_ACTION = "syncChangedAction";
    public static final String RECIPIENT_ADDRESS_KEY = "recipientAddressKey";
    // Logging tag variables.
    private static final String TAG = "Utils";
    private static final String sPairedPCsKey = "pairedPcs";
    private static final String sFirstRunKey = "firstRun";
    private static final String sUUIDKey = "uuid";
    // Data variables.
    private static final ArrayList<BluetoothConnectionThread> currentlyRunningThreads =
            new ArrayList<>();
    // Shared preferences variable.
    private static SharedPreferences sSharedPreferences;
    private static CopyOnWriteArrayList<PairedPC> sPairedPCS;
    private static String sUUID;
    private static boolean sForegroundRunning = false;

    // Initializes all the values and loads them into the Utils class.
    public static void initValues(Context context) {
        sSharedPreferences = context.getSharedPreferences(context.getString(R.string.app_name),
                Context.MODE_PRIVATE);

        // If a list of Paired PC's list exists in the app's Shared Preferences,
        // load it into the Utils class.
        String pairedPCSValue = sSharedPreferences.getString(sPairedPCsKey, null);
        if (pairedPCSValue != null) {
            Gson gson = new Gson();
            sPairedPCS = gson.fromJson(pairedPCSValue,
                    new TypeToken<CopyOnWriteArrayList<PairedPC>>() {
                    }.getType());
            // If not, make a new Paired PC's list.
        } else {
            sPairedPCS = new CopyOnWriteArrayList<>();
        }

        // Check if the app has already created a UUID. If not, create one.
        sUUID = sSharedPreferences.getString(sUUIDKey, null);
        if (sUUID == null) {
            sUUID = java.util.UUID.randomUUID().toString();
            sSharedPreferences.edit().putString(sUUIDKey, sUUID).apply();
        }

        Log.d(TAG, "initValues: successfully initialized utils values.");
    }

    // Used to check if the app is on its first run.
    public static boolean isFirstRun() {
        if (sSharedPreferences.getBoolean(sFirstRunKey, true)) {
            sSharedPreferences.edit().putBoolean(sFirstRunKey, false).apply();
            return true;
        } else {
            return false;
        }
    }

    public static CopyOnWriteArrayList<PairedPC> getPairedPCS() {
        return sPairedPCS;
    }

    public static PairedPC getPairedPC(String address) {
        for (PairedPC pairedPC : sPairedPCS) {
            if (pairedPC.getAddress().equals(address)) {
                return pairedPC;
            }
        }
        return null;
    }

    public static boolean inPairedPCS(String address) {
        for (PairedPC pairedPC : sPairedPCS) {
            if (pairedPC.getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    public static void addToPairedPCS(BluetoothDevice bluetoothDevice) {
        int deviceClass = bluetoothDevice.getBluetoothClass().getDeviceClass();
        if (deviceClass == BluetoothClass.Device.COMPUTER_DESKTOP ||
                deviceClass == BluetoothClass.Device.COMPUTER_LAPTOP || deviceClass == 0) {
            sPairedPCS.add(new PairedPC(bluetoothDevice.getName(), bluetoothDevice.getAddress(),
                    bluetoothDevice));
            savePairedPCSToSharePreferences();
        }
    }

    public static void removeFromPairedPCS(String address) {
        for (PairedPC pairedPC : sPairedPCS) {
            if (pairedPC.getAddress().equals(address)) {
                sPairedPCS.remove(pairedPC);
                break;
            }
        }
        savePairedPCSToSharePreferences();
    }

    private static void savePairedPCSToSharePreferences() {
        SharedPreferences.Editor prefsEditor = sSharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(sPairedPCS);
        prefsEditor.putString(sPairedPCsKey, json);
        prefsEditor.apply();
    }

    public static UUID getUuid() {
        return java.util.UUID.fromString(sUUID);
    }

    // This method checks if bluetooth devices are actually connected or not.
    public static boolean isConnected(String address) {
        Set<BluetoothDevice> pairedDevices = BluetoothAdapter.getDefaultAdapter()
                .getBondedDevices();

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
                    assert method != null;
                    Object methodInvocation = method.invoke(device, (Object[]) null);
                    if (methodInvocation != null) {
                        connected = (boolean) methodInvocation;
                    } else {
                        connected = false;
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }

                return connected;
            }
        }
        return false;
    }

    public static ArrayList<BluetoothConnectionThread> getCurrentlyRunningThreads() {
        return currentlyRunningThreads;
    }

    public static void addToCurrentlyRunningThreads(
            BluetoothConnectionThread bluetoothConnectionThread) {
        bluetoothConnectionThread.start();
        currentlyRunningThreads.add(bluetoothConnectionThread);
    }

    public static boolean isForegroundRunning() {
        return sForegroundRunning;
    }

    public static void setForegroundRunning(boolean foregroundRunning) {
        sForegroundRunning = foregroundRunning;
    }

    /**
     * Static method that allows other classes to notify the app of a device's syncing status.
     */
    public static void notifySyncChanged(Context applicationContext, String pcAddress) {
        Intent syncChangedIntent = new Intent();
        syncChangedIntent.setAction(Utils.SYNC_CHANGED_ACTION);
        syncChangedIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, pcAddress);
        applicationContext.sendBroadcast(syncChangedIntent);
    }
}
