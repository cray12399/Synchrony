package com.example.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


// Activity used to request permissions from user on startup.
public class PermissionsActivity extends AppCompatActivity {

    /*
    Constant used to determine needed permissions. The key-value pair is used within
    PermissionsActivity to obtain details of a permission. Permissions are defined this
    way so that they can be coded in dynamically as needed.
    */
    private final HashMap<String, Permission> NEEDED_PERMISSIONS = new HashMap<String,
            Permission>() {{
        put("SMS", new Permission(
                Manifest.permission.READ_SMS,
                "SMS permission is needed to access contacts from PC.",
                100));
        put("Contacts", new Permission(
                Manifest.permission.READ_CONTACTS,
                "Contacts permission needed to access contacts from PC.",
                101));
        put("Call Logs", new Permission(
                Manifest.permission.READ_CALL_LOG,
                "Call Logs permission needed to access call history from PC.",
                102));
        put("Telephony", new Permission(
                Manifest.permission.CALL_PHONE,
                "Telephony permission needed to make calls from PC",
                103));
    }};

    RecyclerView permissionsRecView;
    PermissionsRecViewAdapter permissionsRecViewAdapter;
    LinearLayoutManager permissionsRecViewLayoutManager;
    TextView skipBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        // Used to display permissions in a card view layout with switches the user can press.
        permissionsRecView = findViewById(R.id.permissionsRecView);

        // Check which permissions have not been granted
        HashMap<String, Permission> permissionsNotGranted = new HashMap<>();
        for (Map.Entry<String, Permission> permission : NEEDED_PERMISSIONS.entrySet()) {
            if (ContextCompat.checkSelfPermission(PermissionsActivity.this,
                    permission.getValue().getManifestPermission()) == -1) {
                permissionsNotGranted.put(permission.getKey(), permission.getValue());
            }
        }

        // Display permissions in a linear card view layout.
        permissionsRecViewAdapter = new PermissionsRecViewAdapter(this,
                permissionsNotGranted);
        permissionsRecViewLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false);
        permissionsRecView.setLayoutManager(permissionsRecViewLayoutManager);
        permissionsRecView.setAdapter(permissionsRecViewAdapter);

        // Allows user to skip permissions screen if they choose.
        skipBtn = findViewById(R.id.skipBtn);
        skipBtn.setOnClickListener(v -> {
            // Confirm just in case they accidentally pressed the button.
            AlertDialog.Builder builder = new AlertDialog
                    .Builder(PermissionsActivity.this);
            builder.setTitle("Skip Permissions?");
            builder.setMessage("Are you sure you wish to skip giving permissions? You will " +
                    "need to give permissions later!");
            builder.setNegativeButton("Cancel", null);
            builder.setPositiveButton("Skip", (dialog, which) -> continueToMainActivity());

            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // If there are permission request results, handle them.
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Indices ArrayList used to figure out position of permission card for removal.
                ArrayList<String> indices = new ArrayList<>(
                        permissionsRecViewAdapter.permissionsNotGranted.keySet());
                for (Map.Entry<String, Permission> entry :
                        permissionsRecViewAdapter.permissionsNotGranted.entrySet()) {
                    // Check which request code was provided and match it to proper permission
                    if (entry.getValue().getRequestCode() == requestCode) {
                        View view = permissionsRecViewLayoutManager
                                .findViewByPosition(indices.indexOf(entry.getKey()));
                        if (view != null) {
                            // Check the switch and remove the CardView
                            SwitchCompat permissionGrantedSwitch = view
                                    .findViewById(R.id.permissionGrantedSwitch);
                            permissionGrantedSwitch.setChecked(true);
                            permissionsRecViewAdapter.permissionsNotGranted.remove(entry.getKey());
                            permissionsRecViewAdapter.notifyItemRemoved(
                                    indices.indexOf(entry.getKey()));
                        }
                        break;
                    }
                }
            }
        }
        // If all permissions are granted, continue to MainActivity
        if (permissionsRecViewAdapter.getItemCount() == 0) {
            continueToMainActivity();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        /*
        Clone of permissionsNotGranted list to avoid exceptions when removing items
        from within foreach iterator.
        */
        HashMap<String, Permission> permissionsToCheck = new HashMap<>(
                permissionsRecViewAdapter.permissionsNotGranted);
        // Iterate over permissions upon resuming of PermissionsActivity and update list accordingly
        for (Map.Entry<String, Permission> entry :
                permissionsToCheck.entrySet()) {
            if (ContextCompat.checkSelfPermission(this,
                    entry.getValue().getManifestPermission())
                    == PackageManager.PERMISSION_GRANTED) {

                permissionsRecViewAdapter.permissionsNotGranted.remove(entry.getKey());
                permissionsRecViewAdapter.notifyDataSetChanged();
            }
        }

        // If all permissions are granted, continue to MainActivity
        if (permissionsRecViewAdapter.getItemCount() == 0) {
            continueToMainActivity();
        }
    }

    @Override
    public void onBackPressed() {
        // Send the user home instead of to StartupActivity on back press
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);
    }

    private void continueToMainActivity() {
        // Made a separate function so that I am not reusing a code block.
        Intent mainActivityIntent = new Intent(PermissionsActivity.this,
                MainActivity.class);
        startActivity(mainActivityIntent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);


    }
}