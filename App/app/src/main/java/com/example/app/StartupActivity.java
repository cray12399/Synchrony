package com.example.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/*
 * This class is used to display the loading page and determine what the app should do on startup.
 */
public class StartupActivity extends AppCompatActivity {

    /*
     * Constant used to determine needed permissions. The key-value pair is used within
     * PermissionsActivity to obtain details of permission. Permissions are defined this
     * way so that they can be coded in dynamically.
     */
    private final HashMap<String, Permission> NEEDED_PERMISSIONS = new HashMap<String,
            Permission>() {{
        put("SMS", new Permission("SMS",
                Manifest.permission.READ_SMS,
                "SMS permission is needed to access contacts from PC.",
                100));
        put("Contacts", new Permission("Contacts",
                Manifest.permission.READ_CONTACTS,
                "Contacts permission needed to access contacts from PC.",
                101));
        put("Call Logs", new Permission("Call Logs",
                Manifest.permission.READ_CALL_LOG,
                "Call Logs permission needed to access call history from PC.",
                102));
        put("Telephony", new Permission("Telephony",
                Manifest.permission.CALL_PHONE,
                "Telephony permission needed to make calls from PC",
                103));
    }};

    String TAG = "StartupActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);

//        Check for first one to determine if app should show PermissionsActivity, etc.
        SharedPreferences sharedPreferences = this.getSharedPreferences(
                "Droid-Communicator", MODE_PRIVATE);

        if (sharedPreferences.getBoolean("firstRun", true)) {
            sharedPreferences.edit().putBoolean("firstRun", false).apply();

            HashMap<String, Permission> permissionsNotGranted = new HashMap<>();
            for (Map.Entry<String, Permission> permission: NEEDED_PERMISSIONS.entrySet()) {
                if (ContextCompat.checkSelfPermission(StartupActivity.this,
                        permission.getValue().getManifestPermission()) == -1) {
                    permissionsNotGranted.put(permission.getKey(), permission.getValue());
                }
            }

//            If first run, go to PermissionsActivity.
            Log.d(TAG, "Not First startup, navigating to PermissionsActivity");
            Intent permissionIntent = new Intent(StartupActivity.this,
                    PermissionsActivity.class);
            permissionIntent.putExtra("permissionsNotGranted", permissionsNotGranted);
            startActivity(permissionIntent);

        } else {
//            If not first run, go to MainActivity.
            Log.d(TAG, "Subsequent startup, navigating to MainActivity");
            Intent mainActivityIntent= new Intent(StartupActivity.this,
                    MainActivity.class);
            startActivity(mainActivityIntent);

        }
    }
}