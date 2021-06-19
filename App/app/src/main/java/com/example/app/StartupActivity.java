package com.example.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;


public class StartupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);

        String[] neededPermissions = {
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS
        };
        
        ArrayList<String> permissionsNotGranted = new ArrayList<>();
        for (String permission: neededPermissions) {
            if (ContextCompat.checkSelfPermission(StartupActivity.this, permission) == -1) {
                permissionsNotGranted.add(permission);
            }
        }
        
        if (permissionsNotGranted.size() == 0) {
            Intent mainActivityIntent= new Intent(StartupActivity.this,
                    MainActivity.class);
            startActivity(mainActivityIntent);
        } else {
            Intent permissionIntent = new Intent(StartupActivity.this,
                    PermissionsActivity.class);
            startActivity(permissionIntent);
        }


    }
}