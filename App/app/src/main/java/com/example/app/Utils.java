package com.example.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class Utils {
    private static final String PAIRED_PCS_KEY = "pairedPcs";
    private static final String PC_UUIDS_KEY = "pcUuids";
    private static final String FIRST_RUN_KEY = "firstRun";
    private static final String UUID_KEY = "uuid";

    private static SharedPreferences sharedPreferences;
    private static Utils utilsInstance = null;
    private static CopyOnWriteArrayList<PairedPC> pairedPCS;
    private static String uuid;


    public static Utils getInstance(Context context) {
        sharedPreferences = context.getSharedPreferences(context.getString(R.string.app_name),
                Context.MODE_PRIVATE);

        if (utilsInstance == null) {
            utilsInstance = new Utils();
            initValues();
        }

        return utilsInstance;
    }

    private static void initValues() {
        String pairedPCSValue = sharedPreferences.getString(PAIRED_PCS_KEY, null);
        if (pairedPCSValue != null) {
            Gson gson = new Gson();
            pairedPCS = gson.fromJson(pairedPCSValue,
                    new TypeToken<CopyOnWriteArrayList<PairedPC>>() {
                    }.getType());
        } else {
            pairedPCS = new CopyOnWriteArrayList<>();
        }

        uuid = sharedPreferences.getString(UUID_KEY, null);
        if (uuid == null) {
            uuid = java.util.UUID.randomUUID().toString();
            sharedPreferences.edit().putString(UUID_KEY, uuid).apply();
        }

    }

    public boolean isFirstRun() {
        if (sharedPreferences.getBoolean(FIRST_RUN_KEY, true)) {
            sharedPreferences.edit().putBoolean(FIRST_RUN_KEY, false).apply();
            return true;
        } else {
            return false;
        }
    }

    public CopyOnWriteArrayList<PairedPC> getPairedPCS() {
        return pairedPCS;
    }

    public PairedPC getPairedPC(String address) {
        for (PairedPC pairedPC : pairedPCS) {
            if (pairedPC.getAddress().equals(address)) {
                return pairedPC;
            }
        }
        return null;
    }

    public boolean inPairedPCS(String address) {
        for (PairedPC pairedPC : pairedPCS) {
            if (pairedPC.getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    public void addToPairedPCS(PairedPC pairedPC) {
        pairedPCS.add(pairedPC);
        savePairedPCSToDevice();
    }

    public void removeFromPairedPCS(String address) {
        for (PairedPC pairedPC : pairedPCS) {
            if (pairedPC.getAddress().equals(address)) {
                pairedPCS.remove(pairedPC);
                break;
            }
        }
        savePairedPCSToDevice();
    }

    public void savePairedPCSToDevice() {
        SharedPreferences.Editor prefsEditor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(pairedPCS);
        prefsEditor.putString(PAIRED_PCS_KEY, json);
        prefsEditor.apply();
    }

    public UUID getUuid() {
        return java.util.UUID.fromString(uuid);
    }

    public boolean isConnected(String address) {
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
}
