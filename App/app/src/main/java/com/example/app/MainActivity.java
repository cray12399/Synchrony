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

    private DrawerLayout menuDrawer;
    private TextView noPairText;
    private Button pairBtn;
    private RelativeLayout pcListLayout;
    private RelativeLayout unpairedLayout;
    private RecyclerView pcRecView;
    private PCRecViewAdapter pcRecViewAdapter;
    private LinearLayoutManager pcRecViewLayoutManager;
    private ArrayList<ConnectedPC> connectedPCS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        menuDrawer = findViewById(R.id.drawerLayout);
        ImageButton menuOpenBtn = findViewById(R.id.menuOpenBtn);
        pairBtn = findViewById(R.id.pairBtn);
        noPairText = findViewById(R.id.noPairText);
        pcListLayout = findViewById(R.id.pcListLayout);
        unpairedLayout = findViewById(R.id.unpairedLayout);
        connectedPCS = new ArrayList<>();

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
        BluetoothHandler bluetoothHandler = new BluetoothHandler(this);
        bluetoothHandler.start();

        // Initialize the pcRecView
        pcRecView = findViewById(R.id.pcRecView);
        pcRecViewAdapter = new PCRecViewAdapter(MainActivity.this, connectedPCS);
        pcRecViewLayoutManager = new LinearLayoutManager(
                MainActivity.this, LinearLayoutManager.VERTICAL,
                false);
        pcRecView.setLayoutManager(pcRecViewLayoutManager);
        pcRecView.setAdapter(pcRecViewAdapter);
    }

    @Override
    public void onBackPressed() {
        menuDrawer.closeDrawer(GravityCompat.START);
    }

    public class BluetoothHandler extends Thread {

        private BluetoothAdapter bluetoothAdapter;
        private BluetoothManager bluetoothManager;
        private Context context;

        // This boolean is used so that the thread isn't constantly showing the SnackBar.
        private boolean showSnackBar = true;

        public BluetoothHandler(Context context) {
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
            if (!pairBtn.isEnabled()) {runOnUiThread(() -> pairBtn.setEnabled(true));}

            connectedPCS = getConnectedPCS();
            if (connectedPCS.size() > 0) {
                runOnUiThread(() -> {
                    // Boolean used to determine if RecView should be updated.
                    boolean connectedPCListsDifferent = checkConnectedPCListsDifferent();

                    // If currently connected PC list different from RecView, update RecView.
                    if (connectedPCListsDifferent) {
                        pcRecViewAdapter.connectedPCS = connectedPCS;
                        pcRecViewAdapter.notifyDataSetChanged();
                    }
                });

                // Toggle pair views so that the PC RecView is shown and the Pair layout is hidden.
                togglePairViews(false);
            } else {
                // Toggle pair views so that the PC RecView is hidden and the Pair layout is shown.
                togglePairViews(true);
            }
        }

        private boolean checkConnectedPCListsDifferent() {
            boolean connectedPCListsDifferent = false;
            ArrayList<String> recViewPCAddresses = new ArrayList<>();
            ArrayList<String> connectedPCAddresses = new ArrayList<>();

            // Fill recViewPcAddresses with addresses in pcRecViewAdapter
            for (ConnectedPC connectedPC : pcRecViewAdapter.connectedPCS) {
                recViewPCAddresses.add(connectedPC.getAddress());
            }

            // Fill connectedPCAddresses with addresses in connectedPCS
            for (ConnectedPC connectedPC : connectedPCS) {
                connectedPCAddresses.add(connectedPC.getAddress());
            }
            // Check if the lists of connected devices are different.
            if (!recViewPCAddresses.toString().equals(connectedPCAddresses.toString())) {
                connectedPCListsDifferent = true;
            }

            return connectedPCListsDifferent;
        }

        public ArrayList<ConnectedPC> getConnectedPCS() {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            ArrayList<ConnectedPC> connectedPCS = new ArrayList<>();

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

                    // If it is a desktop or laptop PC, add to connectPCS list.
                    if (deviceClass == 268 || deviceClass == 260) {
                        connectedPCS.add(new ConnectedPC(device.getName(), device.getAddress(),
                                false));
                        System.out.println(device.getAddress());
                    }
                }
            }

            return connectedPCS;
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