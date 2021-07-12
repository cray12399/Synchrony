package com.example.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.concurrent.CopyOnWriteArrayList;

public class Utils {
    private static final String PAIRED_PCS_KEY = "pairedPcs";
    private static final String FIRST_RUN_KEY = "firstRun";

    private static SharedPreferences sharedPreferences;

    private static Utils utilsInstance = null;

    private static CopyOnWriteArrayList<PairedPC> pairedPCS;

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
                    new TypeToken<CopyOnWriteArrayList<PairedPC>>(){}.getType());
        } else {
            pairedPCS = new CopyOnWriteArrayList<>();
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

    public PairedPC getPairedPCByAddress(String address) {
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
}
