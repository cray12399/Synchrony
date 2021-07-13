package com.example.app;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Pair;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends AppCompatActivity {

    private Utils utils;

    private DrawerLayout menuDrawer;
    private TextView noPairText;
    private Button pairBtn;
    private RelativeLayout pcListLayout;
    private RelativeLayout unpairedLayout;
    private PCRecViewAdapter pcRecViewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If it is the first run, initialize BluetoothSyncService
        if (getIntent().getBooleanExtra("firstRun", false)) {
            Intent syncServiceIntent = new Intent(this, BluetoothSyncService.class);
            startForegroundService(syncServiceIntent);
        }

        setContentView(R.layout.activity_main);

        utils = Utils.getInstance(this);

        menuDrawer = findViewById(R.id.drawerLayout);
        ImageButton menuOpenBtn = findViewById(R.id.menuOpenBtn);
        pairBtn = findViewById(R.id.pairBtn);
        noPairText = findViewById(R.id.noPairText);
        pcListLayout = findViewById(R.id.pcListLayout);
        unpairedLayout = findViewById(R.id.unpairedLayout);

        menuOpenBtn.setOnClickListener(v -> {
            Animation rotate = AnimationUtils.loadAnimation(MainActivity.this,
                    R.anim.rotate);
            menuOpenBtn.startAnimation(rotate);

            // Used a handler to delay the opening of the drawer so that the animation can be shown.
            Handler handler = new Handler();
            handler.postDelayed(() -> menuDrawer.openDrawer(GravityCompat.START), 100);
        });

        // Open bluetooth window so that user can pair PC with phone.
        pairBtn.setOnClickListener(v -> {
            Intent intentOpenBluetoothSettings = new Intent();
            intentOpenBluetoothSettings.setAction(
                    android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intentOpenBluetoothSettings);
        });

        // Initialize the pcRecView
        RecyclerView pcRecView = findViewById(R.id.pcRecView);
        LinearLayoutManager pcRecViewLayoutManager = new LinearLayoutManager(
                MainActivity.this, LinearLayoutManager.VERTICAL,
                false);
        pcRecViewAdapter = new PCRecViewAdapter(MainActivity.this,
                new CopyOnWriteArrayList<PairedPC>());
        pcRecView.setLayoutManager(pcRecViewLayoutManager);
        pcRecView.setAdapter(pcRecViewAdapter);

        // Thread used to check for bluetooth capabilities and handle devices.
        btUIUpdateThread bluetoothHandler = new btUIUpdateThread();
        bluetoothHandler.start();
    }

    @Override
    public void onBackPressed() {
        menuDrawer.closeDrawer(GravityCompat.START);
    }

    public class btUIUpdateThread extends Thread {

        private BluetoothAdapter bluetoothAdapter;

        // This boolean is used so that the thread isn't constantly showing the SnackBar.
        private boolean showSnackBar = true;

        @Override
        public void run() {
            // While loop used to cause thread to continuously update UI and look for connected PCS.
            while (true) {
                bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                // If the device doesn't have a bluetooth adapter, it is not supported.
                if (bluetoothAdapter == null) {
                    runOnUiThread(() -> pairBtn.setVisibility(View.GONE));
                    runOnUiThread(() -> noPairText.setText("Sorry, phone unsupported."));
                    break;

                    // If the adapter is enabled, show SnackBar if BT turned off and check for PCS.
                } else if (bluetoothAdapter.isEnabled()) {
                    handleBTEnabled();

                    //If the adapter is not enabled, prompt user to enable.
                } else {
                    handleBTDisabled();
                }
            }
        }

        private void handleBTDisabled() {
            // Disable pair button if it is enabled.
            if (pairBtn.isEnabled()) {runOnUiThread(() -> pairBtn.setEnabled(false));}

            // If the app hasn't shown the SnackBar, show it.
            if (showSnackBar) {
                runOnUiThread(() -> pairBtn.setEnabled(false));
                Snackbar.make(menuDrawer, "Bluetooth is not enabled!",
                        Snackbar.LENGTH_INDEFINITE).setAction("Enable",
                        v -> bluetoothAdapter.enable()).show();

                // Stop showing the SnackBar since it has already been shown.
                showSnackBar = false;
            }

            // Toggle pair views so that the PC RecView is hidden and the Pair layout is shown.
            togglePairViews(true);
        }

        private void handleBTEnabled() {
            showSnackBar = true;

            CopyOnWriteArrayList<PairedPC> connectedPCS = new CopyOnWriteArrayList<>();
            for (PairedPC pairedPC : utils.getPairedPCS()) {
                if (pairedPC.isConnected()) {
                    connectedPCS.add(pairedPC);
                }
            }

            // Enable pair button if it is disabled.
            if (!pairBtn.isEnabled()) {
                runOnUiThread(() -> pairBtn.setEnabled(true));
            }

            // Toggle pair views based on whether there are connected PCs
            togglePairViews(connectedPCS.size() <= 0);

            if (connectedPCS.size() != pcRecViewAdapter.getItemCount()) {
                pcRecViewAdapter.connectedPCS = connectedPCS;
                runOnUiThread(() -> {pcRecViewAdapter.notifyDataSetChanged();});
            }
        }

        private void togglePairViews(boolean noPair) {
            if (noPair) {
                // Show the unpaired layout if no PC is paired.
                if (unpairedLayout.getVisibility() == View.GONE) {
                    runOnUiThread(() -> unpairedLayout.setVisibility(View.VISIBLE));
                }
                // Hide the PC list layout if no PC is paired
                if (pcListLayout.getVisibility() == View.VISIBLE) {
                    runOnUiThread(() -> pcListLayout.setVisibility(View.GONE));
                }
            } else {
                // Hide the unpaired layout if PC is paired.
                if (unpairedLayout.getVisibility() == View.VISIBLE) {
                    runOnUiThread(() -> unpairedLayout.setVisibility(View.GONE));
                }
                // Show the PC List layout if PC is paired.
                if (pcListLayout.getVisibility() == View.GONE) {
                    runOnUiThread(() -> pcListLayout.setVisibility(View.VISIBLE));
                }
            }
        }
    }
}