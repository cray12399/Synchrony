package com.example.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

/*
 * This class is used to display the loading page and determine what the app should do on startup.
 */
public class StartupActivity extends AppCompatActivity {

    String TAG = "StartupActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);


        // Remove handler when app finished. I just like looking at the logo screen.
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                // Check for first run to determine if app should show PermissionsActivity, etc.
                SharedPreferences sharedPreferences = StartupActivity.this.getSharedPreferences(
                        "Droid-Communicator", MODE_PRIVATE);

                if (sharedPreferences.getBoolean("firstRun", true)) {
                    sharedPreferences.edit().putBoolean("firstRun", false).apply();

                    // If first run, go to PermissionsActivity.
                    Log.d(TAG, "Not First startup, navigating to PermissionsActivity");
                    Intent permissionIntent = new Intent(StartupActivity.this,
                            PermissionsActivity.class);
                    startActivity(permissionIntent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

                } else {
                    // Else, go to MainActivity.
                    Log.d(TAG, "Subsequent startup, navigating to MainActivity");
                    Intent mainActivityIntent = new Intent(StartupActivity.this,
                            MainActivity.class);
                    startActivity(mainActivityIntent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }
            }
        }, 1500);


    }
}