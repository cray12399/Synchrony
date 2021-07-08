package com.example.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.WorkManager;

import java.util.concurrent.CopyOnWriteArrayList;

public class Utils {
    private static final int MODE_PRIVATE = Context.MODE_PRIVATE;
    private static SharedPreferences sharedPreferences;

    private static Utils utilsInstance = null;

    private static CopyOnWriteArrayList<ConnectedPC> connectedPCS;

    public static Utils getInstance(Context context) {
        if (utilsInstance == null) {
            utilsInstance = new Utils();
            initValues();
        }
        sharedPreferences = context.getSharedPreferences("Droid-Communicator", MODE_PRIVATE);
        return utilsInstance;
    }

    private static void initValues() {
        connectedPCS = new CopyOnWriteArrayList<>();
    }

    public boolean isFirstRun() {
        if (sharedPreferences.getBoolean("firstRun", true)) {
            sharedPreferences.edit().putBoolean("firstRun", false).apply();
            return true;
        } else {
            return false;
        }
    }

    public CopyOnWriteArrayList<ConnectedPC> getConnectedPCS() {
        return connectedPCS;
    }

    public void addToConnectedPCS(ConnectedPC connectedPC) {
        connectedPCS.add(connectedPC);
    }

    public void removeFromConnectedPCS(Context context, ConnectedPC connectedPC) {
        connectedPCS.remove(connectedPC);
    }
}
