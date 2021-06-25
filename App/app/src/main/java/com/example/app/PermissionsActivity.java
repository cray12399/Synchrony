package com.example.app;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PermissionsActivity extends AppCompatActivity {

    RecyclerView permissionsRecView;
    PermissionsRecViewAdapter permissionsRecViewAdapter;
    LinearLayoutManager permissionsRecViewLayoutManager;

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

//        Used to display permissions in a card view layout with switches the user can press.
        permissionsRecView = findViewById(R.id.permissionsRecView);

//        Grabbing the hashmap of permissions are not granted from MainActivity.
        Intent previousIntent = getIntent();
        HashMap<String, Permission> permissionsNotGranted = (HashMap<String, Permission>)
                previousIntent.getSerializableExtra("permissionsNotGranted");

//        Display permissions in a linear card view layout.
        permissionsRecViewAdapter = new PermissionsRecViewAdapter(this,
                permissionsNotGranted);
        permissionsRecViewLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false);
        permissionsRecView.setLayoutManager(permissionsRecViewLayoutManager);
        permissionsRecView.setAdapter(permissionsRecViewAdapter);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Position will be used to determine which CardView the app needs to modify
                int position = 0;
                for (Map.Entry<String, Permission> entry:
                        permissionsRecViewAdapter.permissionsNotGranted.entrySet()) {
                    // Checking which request code was provided and matching it to proper permission
                    if (entry.getValue().getRequestCode() == requestCode) {
                        View view = permissionsRecViewLayoutManager
                                .findViewByPosition(position);
                        if (view != null) {
                            // Check the switch and remove the CardView
                            SwitchCompat permissionGrantedSwitch = view
                                    .findViewById(R.id.permissionGrantedSwitch);
                            permissionGrantedSwitch.setChecked(true);
                            permissionsRecViewAdapter.permissionsNotGranted.remove(entry.getKey());
                            permissionsRecViewAdapter.notifyItemRemoved(position);
                        }
                        break;
                    } else {
                        position += 1;
                    }
                }
            }
        }
    }
}