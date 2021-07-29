package com.example.app;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.CopyOnWriteArrayList;

// This is the main activity for the app. This activity contains the drawer menu for the app and
// lists all of the PairedPCs for the user to interact with.
public class MainActivity extends AppCompatActivity {
    private DrawerLayout menuDrawer;
    private TextView noPairText;
    private Button pairBtn;
    private RelativeLayout pcListLayout;
    private RelativeLayout unpairedLayout;
    private PCRecViewAdapter pcRecViewAdapter;
    private LinearLayoutManager pcRecViewLayoutManager;
    private PCRecViewUpdateThread pcRecViewUpdateThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start the main service, BluetoothSyncService in the foreground.
        Intent syncServiceIntent = new Intent(this, BluetoothSyncService.class);
        startForegroundService(syncServiceIntent);

        // Set up the hamburger menu button and set it to rotate when pressed.
        ImageButton menuOpenBtn = findViewById(R.id.menuOpenBtn);
        menuOpenBtn.setOnClickListener(v -> {
            Animation rotate = AnimationUtils.loadAnimation(MainActivity.this,
                    R.anim.rotate);
            menuOpenBtn.startAnimation(rotate);

            // Used a handler to delay the opening of the drawer so that the animation can be shown.
            Handler handler = new Handler();
            menuDrawer = findViewById(R.id.drawerLayout);
            handler.postDelayed(() -> menuDrawer.openDrawer(GravityCompat.START), 100);
        });

        // Set up the pair button to open the bluetooth menu when clicked.
        pairBtn = findViewById(R.id.pairBtn);
        pairBtn.setOnClickListener(v -> {
            Intent intentOpenBluetoothSettings = new Intent();
            intentOpenBluetoothSettings.setAction(
                    android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intentOpenBluetoothSettings);
        });

        // Initialize the pcRecView.
        RecyclerView pcRecView = findViewById(R.id.pcRecView);
        pcRecViewLayoutManager = new LinearLayoutManager(
                MainActivity.this, LinearLayoutManager.VERTICAL,
                false);
        pcRecViewAdapter = new PCRecViewAdapter(MainActivity.this,
                new CopyOnWriteArrayList<>());
        pcRecView.setLayoutManager(pcRecViewLayoutManager);
        pcRecView.setAdapter(pcRecViewAdapter);

        // Start thread used to monitor Paired PCs and update the pcRecView accordingly.
        noPairText = findViewById(R.id.noPairText);
        pcListLayout = findViewById(R.id.pcListLayout);
        unpairedLayout = findViewById(R.id.unpairedLayout);
        pcRecViewUpdateThread = new PCRecViewUpdateThread(this, pcRecViewLayoutManager);
        pcRecViewUpdateThread.start();
    }

    // This is the main background thread of the main activity. It will be used to update the
    // pcRecView automatically.
    public class PCRecViewUpdateThread extends Thread {
        private final Context CONTEXT;
        private final BluetoothAdapter BLUETOOTH_ADAPTER;
        private final LinearLayoutManager PC_REC_VIEW_LAYOUT_MANAGER;
        private final Snackbar btDisabledSnackBar;

        // This boolean is used so that the thread isn't constantly showing the SnackBar.
        private boolean shouldShowSnackBar = true;

        public PCRecViewUpdateThread(Context context, LinearLayoutManager pcRecViewLayoutManager) {
            this.CONTEXT = context;
            this.PC_REC_VIEW_LAYOUT_MANAGER = pcRecViewLayoutManager;
            this.BLUETOOTH_ADAPTER = BluetoothAdapter.getDefaultAdapter();

            // SnackBar used to notify the user that bluetooth is not enabled and prompt them
            // to enable it.
            btDisabledSnackBar = Snackbar.make(menuDrawer,
                    "Bluetooth is not enabled!",
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction("Enable...", v -> BLUETOOTH_ADAPTER.enable());
        }

        @Override
        public void run() {
            // While loop used to cause thread to continuously update UI and look for connected PCS.
            while (!interrupted()) {
                // If the device doesn't have a bluetooth adapter, say the phone is not supported
                // and notify the user.
                if (BLUETOOTH_ADAPTER == null) {
                    runOnUiThread(() -> pairBtn.setVisibility(View.GONE));
                    runOnUiThread(() -> noPairText.setText("Sorry, phone unsupported."));
                    break;

                // If the adapter is enabled, begin working on updating the pcRecView
                } else if (BLUETOOTH_ADAPTER.isEnabled()) {
                    handleBTEnabled();

                //If the adapter is not enabled, prompt user to enable and hide the pcRecView.
                } else {
                    handleBTDisabled();
                }
                SystemClock.sleep(1000);
            }
        }

        private void handleBTDisabled() {
            // Disable pair button if it is enabled.
            if (pairBtn.isEnabled()) {runOnUiThread(() -> pairBtn.setEnabled(false));}

            // If the app hasn't shown the SnackBar, show it.
            if (shouldShowSnackBar) {
                runOnUiThread(() -> pairBtn.setEnabled(false));
                btDisabledSnackBar.show();

                // Stop showing the SnackBar since it has already been shown.
                shouldShowSnackBar = false;
            }

            // Toggle pair views so that the PC RecView is hidden and the Pair layout is shown.
            togglePairViews(true);
        }

        private void handleBTEnabled() {
            // If bluetooth is enabled, then set the SnackBar to show if it is disabled
            // and dismiss the SnackBar if it already exists.
            shouldShowSnackBar = true;
            btDisabledSnackBar.dismiss();

            // ConnectedPCs is this thread's copy of PairedPC's to compare to the
            // utils PairedPC list
            CopyOnWriteArrayList<PairedPC> connectedPCS = new CopyOnWriteArrayList<>();
            Utils utils = Utils.getInstance(CONTEXT);
            for (PairedPC pairedPC : utils.getPairedPCS()) {
                // If a Paired PC in pairedPCS is connected, add it to the thread's list.
                if (utils.isConnected(pairedPC.getAddress())) {
                    connectedPCS.add(pairedPC);
                }
            }

            // Enable pair button if it is disabled.
            if (!pairBtn.isEnabled()) {
                runOnUiThread(() -> pairBtn.setEnabled(true));
            }

            // Toggle pair views based on whether there are connected PCs
            togglePairViews(connectedPCS.size() <= 0);

            // Update the list of PC's if there is a discrepancy in the PC count between
            // pcRecView and connectedPCS
            if (connectedPCS.size() != pcRecViewAdapter.getItemCount()) {
                pcRecViewAdapter.connectedPCS = connectedPCS;
                runOnUiThread(() -> {pcRecViewAdapter.notifyDataSetChanged();});
            }

            // Update active switch in case user sets the PC as inactive from somewhere else.
            for (int viewNum = 0; viewNum < PC_REC_VIEW_LAYOUT_MANAGER.getChildCount(); viewNum++) {
                View view = PC_REC_VIEW_LAYOUT_MANAGER
                        .getChildAt(viewNum);
                if (view != null) {
                    TextView viewText = view.findViewById(R.id.pcAddressText);
                    SwitchCompat viewSwitch = view.findViewById(R.id.pcActiveSwitch);
                    String viewAddress = viewText.getText().toString();
                    boolean isChecked = viewSwitch.isChecked();
                    if (utils.getPairedPC(viewAddress) != null) {
                        if (utils.getPairedPC(viewAddress).isActive() != isChecked) {
                            runOnUiThread(() -> viewSwitch
                                    .setChecked(utils.getPairedPC(viewAddress).isActive()));
                        }
                    }
                }
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

    @Override
    protected void onStop() {
        // If  the activity is, stopped interrupt pcRecViewUpdateThread.
        if (pcRecViewUpdateThread != null) {
            pcRecViewUpdateThread.interrupt();
        }
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        // If the back button is pressed, close the drawer, but don't navigate
        // the user to StartupActivity.
        menuDrawer.closeDrawer(GravityCompat.START);
    }

    @Override
    protected void onDestroy() {
        // If  the activity is  destroyed, interrupt pcRecViewUpdateThread.
        if (pcRecViewUpdateThread != null) {
            pcRecViewUpdateThread.interrupt();
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        // Upon resume, try to restart pcRecViewUpdateThread.
        if (pcRecViewUpdateThread != null) {
            if (!pcRecViewUpdateThread.isAlive()) {
                pcRecViewUpdateThread = new PCRecViewUpdateThread(
                        this, pcRecViewLayoutManager);
                pcRecViewUpdateThread.start();
            }
        }
        super.onResume();
    }

    @Override
    protected void onRestart() {
        // Upon resume, try to restart pcRecViewUpdateThread.
        if (pcRecViewUpdateThread != null) {
            if (!pcRecViewUpdateThread.isAlive()) {
                pcRecViewUpdateThread = new PCRecViewUpdateThread(
                        this, pcRecViewLayoutManager);
                pcRecViewUpdateThread.start();
            }
        }
        super.onRestart();
    }
}