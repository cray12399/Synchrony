package com.example.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;


public class StartupActivity extends AppCompatActivity {

    private final HashMap<String, String> NEEDED_PERMISSIONS = new HashMap<String, String>() {{
        put("Read SMS", Manifest.permission.READ_SMS);
        put("Receive SMS", Manifest.permission.RECEIVE_SMS);
        put("Send SMS", Manifest.permission.SEND_SMS);
        put("Access Notifications", Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE);
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);

        HashMap<String, String> permissionsNotGranted = new HashMap<>();
        for (Map.Entry<String, String> permission: NEEDED_PERMISSIONS.entrySet()) {
            if (ContextCompat.checkSelfPermission(StartupActivity.this,
                    permission.getValue()) == -1) {
                permissionsNotGranted.put(permission.getKey(), permission.getValue());
            }
        }

        if (permissionsNotGranted.size() == 0) {
            Intent mainActivityIntent= new Intent(StartupActivity.this,
                    MainActivity.class);
            startActivity(mainActivityIntent);
        } else {
            Intent permissionIntent = new Intent(StartupActivity.this,
                    PermissionsActivity.class);
            permissionIntent.putExtra("permissionsNotGranted", permissionsNotGranted);
            startActivity(permissionIntent);
        }
    }
}