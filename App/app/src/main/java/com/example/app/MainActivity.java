package com.example.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private Utils utils;

    private DrawerLayout menuDrawer;
    private TextView noPairText;
    private Button pairBtn;
    private RelativeLayout pcListLayout;
    private RelativeLayout unpairedLayout;
    private RecyclerView pcRecView;
    private PCRecViewAdapter pcRecViewAdapter;
    private LinearLayoutManager pcRecViewLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        // Thread used to check for bluetooth capabilities and handle devices.
        BluetoothThread bluetoothHandler = new BluetoothThread(this);
        bluetoothHandler.start();

        // Initialize the pcRecView
        pcRecView = findViewById(R.id.pcRecView);
        pcRecViewLayoutManager = new LinearLayoutManager(
                MainActivity.this, LinearLayoutManager.VERTICAL,
                false);
        pcRecViewAdapter = new PCRecViewAdapter(MainActivity.this, pcRecViewLayoutManager,
                utils.getConnectedPCS());
        pcRecView.setLayoutManager(pcRecViewLayoutManager);
        pcRecView.setAdapter(pcRecViewAdapter);
    }

    @Override
    public void onBackPressed() {
        menuDrawer.closeDrawer(GravityCompat.START);
    }

    public class BluetoothThread extends Thread {

        private BluetoothAdapter bluetoothAdapter;
        private BluetoothManager bluetoothManager;
        private Context context;

        // This boolean is used so that the thread isn't constantly showing the SnackBar.
        private boolean showSnackBar = true;

        public BluetoothThread(Context context) {
            this.context = context;
            bluetoothManager = (BluetoothManager) context
                    .getSystemService(Context.BLUETOOTH_SERVICE);
        }

        @Override
        public void run() {
            // While loop used to cause thread to continuously update UI and look for devices.
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

            // Enable pair button if it is disabled.
            if (!pairBtn.isEnabled()) {
                runOnUiThread(() -> pairBtn.setEnabled(true));
            }

            handleConnectedPCS();
            // Toggle pair views based on whether there are connected PCs
            togglePairViews(utils.getConnectedPCS().size() <= 0);
        }

        public void handleConnectedPCS() {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            ArrayList<ConnectedPC> currentlyConnectedPCS = new ArrayList<>();

            // Check for devices and see if they are connected. Then check if they are PCS.
            for (BluetoothDevice device : pairedDevices) {
                Method method = null;
                try {
                    method = device.getClass().getMethod("isConnected", (Class[]) null);
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                }

                boolean connected = false;
                try {
                    connected = (boolean) method.invoke(device, (Object[]) null);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }

                if (connected) {
                    int deviceClass = device.getBluetoothClass().getDeviceClass();

                    // If it is a desktop or laptop PC, check if in ConnectedPCS list.
                    if (deviceClass == 268 || deviceClass == 260) {
                        currentlyConnectedPCS.add(new ConnectedPC(device.getName(),
                                device.getAddress()));

                        boolean deviceNotInConnectedPCS = true;
                        for (ConnectedPC connectedPC : utils.getConnectedPCS()) {
                            /*
                            If the mac address matches a device in the ConnectPCS list,
                            mark it as such
                            */
                            if (device.getAddress().equals(connectedPC.getAddress())) {
                                deviceNotInConnectedPCS = false;
                            }
                        }
                        // If the device isn't in ConnectedPCS, add it.
                        if (deviceNotInConnectedPCS) {
                            utils.addToConnectedPCS(new ConnectedPC(device.getName(),
                                    device.getAddress()));
                            runOnUiThread(() -> {
                                pcRecViewAdapter.notifyDataSetChanged();
                            });
                        }
                    }
                }
            }

            /*
            If there are more previously connected PC's than connected,
            check which one is missing.
            */
            if (currentlyConnectedPCS.size() < utils.getConnectedPCS().size()) {
                for (ConnectedPC previouslyConnectedPC : utils.getConnectedPCS()) {
                    boolean noLongerConnected = true;
                    for (ConnectedPC currentlyConnectedPC : currentlyConnectedPCS) {
                        if (previouslyConnectedPC.getAddress()
                                .equals(currentlyConnectedPC.getAddress())) {
                            noLongerConnected = false;
                        }
                    }

                    // If PC is no longer connected, remove it from pcRecView.
                    if (noLongerConnected) {
                        utils.removeFromConnectedPCS(MainActivity.this,
                                previouslyConnectedPC);
                        runOnUiThread(() -> {pcRecViewAdapter.notifyDataSetChanged();});
                    }
                }
            }
        }

        public void togglePairViews(boolean noPair) {
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