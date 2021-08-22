package sync.synchrony.Synchrony;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Synchrony.R;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

/**
 * This is the main activity for the app. This activity contains the drawer menu for the app and
 * lists all of the PairedPCs for the user to interact with.
 */
public class MainActivity extends AppCompatActivity {
    // Logging tag variables.
    private static final String TAG = "MainActivity";

    // Used to list paired PC's in pcRecViewAdapter
    private final ArrayList<PairedPC> mListedPCS = new ArrayList<>();

    // UI Variables
    private DrawerLayout mMenuDrawer;
    private Button mPairBtn;
    private RelativeLayout mPCListLayout;
    private RelativeLayout mUnpairedLayout;
    private PCRecViewAdapter mPCRecViewAdapter;
    private Snackbar mBTDisconnectedSnackBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        initializePCListing();
        registerReceiver();
    }

    /**
     * Initializes the main ui elements of MainActivity
     */
    private void initializeUI() {
        // Set up the hamburger menu button and set it to rotate when pressed.
        mMenuDrawer = findViewById(R.id.drawerLayout);
        ImageButton menuOpenBtn = findViewById(R.id.menuOpenBtn);
        menuOpenBtn.setOnClickListener(v -> {
            Animation rotate = AnimationUtils.loadAnimation(MainActivity.this,
                    R.anim.rotate);
            menuOpenBtn.startAnimation(rotate);

            // Used a handler to delay the opening of the drawer so that the animation can be shown.
            Handler handler = new Handler();
            handler.postDelayed(() -> mMenuDrawer.openDrawer(GravityCompat.START), 100);
            Log.d(TAG, "onCreate: Menu drawer opened!");
        });

        // Set up the pair button to open the bluetooth menu when clicked.
        mPairBtn = findViewById(R.id.pairBtn);
        mPairBtn.setOnClickListener(v -> {
            Log.d(TAG, "onCreate: Opening bluetooth settings...");
            Intent intentOpenBluetoothSettings = new Intent();
            intentOpenBluetoothSettings.setAction(
                    android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            startActivity(intentOpenBluetoothSettings);
        });

        // Initialize the pcRecView.
        RecyclerView pcRecView = findViewById(R.id.pcRecView);
        LinearLayoutManager pcRecViewLayoutManager = new LinearLayoutManager(
                MainActivity.this, LinearLayoutManager.VERTICAL,
                false);
        mPCRecViewAdapter = new PCRecViewAdapter(MainActivity.this, mListedPCS);
        pcRecView.setLayoutManager(pcRecViewLayoutManager);
        pcRecView.setAdapter(mPCRecViewAdapter);

        mUnpairedLayout = findViewById(R.id.unpairedLayout);

        // btDisconnectSnackBar used to prompt the user to re-enable bluetooth.
        mBTDisconnectedSnackBar = Snackbar.make(mUnpairedLayout,
                "Bluetooth not enabled!",
                Snackbar.LENGTH_INDEFINITE);
        mBTDisconnectedSnackBar.setAction("Enable", v -> {
            BluetoothAdapter.getDefaultAdapter().enable();
            Log.d(TAG, "onCreate: Bluetooth enabled by user!");
        });

        mPCListLayout = findViewById(R.id.pcListLayout);
    }

    /**
     * Tries to start the BluetoothConnectService and initializes listing of PC's in pcRecView
     */
    private void initializePCListing() {
        // Start the main service, BluetoothConnectService, if it isn't running.
        if (!Utils.isForegroundRunning()) {
            Intent connectServiceIntent =
                    new Intent(this, BluetoothConnectService.class);
            startForegroundService(connectServiceIntent);
            Log.d(TAG, "initializePCListing: BluetoothConnectService started in foreground!");

            // If it is running, try to populate the pcRecViewAdapter with connected Paired PC's.
        } else {
            for (PairedPC pairedPC : Utils.getPairedPCS()) {
                String deviceTag = String.format("%s (%s)", pairedPC.getName(),
                        pairedPC.getAddress());

                boolean listed = false;
                for (PairedPC listedPC : mListedPCS) {
                    if (listedPC.getAddress().equals(pairedPC.getAddress())) {
                        listed = true;
                        break;
                    }
                }

                if (!listed && Utils.isConnected(pairedPC.getAddress())) {
                    mListedPCS.add(pairedPC);
                    mPCRecViewAdapter.notifyItemInserted(mListedPCS.size() - 1);
                    Log.d(TAG, String.format("initializePCListing: " +
                            "Added to pcRecView: Device: %s", deviceTag));
                }
            }
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If a bluetooth adapter exists, initialize the UI.
        if (bluetoothAdapter != null) {
            // If the bluetooth adapter is not enabled, hide pcListLayout,
            // show unpairedLayout, disable pairBtn, show btDisconnectedSnackbar.
            if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                mPCListLayout.setVisibility(View.GONE);
                mUnpairedLayout.setVisibility(View.VISIBLE);
                mPairBtn.setEnabled(false);
                mBTDisconnectedSnackBar.show();
                Log.d(TAG, "initializePCListing: " +
                        "Bluetooth not enabled! Prompting user to enable!");

                // If it is enabled, begin listing all connected PC's
            } else {
                // If no PC's are found, show the unpaired layout
                if (mListedPCS.size() == 0) {
                    mPCListLayout.setVisibility(View.GONE);
                    mUnpairedLayout.setVisibility(View.VISIBLE);
                    Log.d(TAG, "initializePCListing: " +
                            "No paired PC's found! Showing unpaired layout!");
                }
            }
        }
    }

    public void registerReceiver() {
        BroadcastReceiver mainActivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case BluetoothConnectService.ADD_TO_MAIN_ACTIVITY_ACTION: {
                        String pcAddress = intent.getStringExtra(
                                BluetoothConnectService.ADDED_PC_KEY);
                        addToPcRecViewAdapter(pcAddress);
                    }
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                            // If bluetooth is enabled, allow the user to pair devices
                            // using pairBtn and dismiss btDisconnectedSnackbar
                            case BluetoothAdapter.STATE_ON:
                                mPairBtn.setEnabled(true);
                                mBTDisconnectedSnackBar.dismiss();
                                break;

                            // If bluetooth is disabled, hide and clear pc list and disable
                            // pairing. Show btDisconnectedSnackbar.
                            case BluetoothAdapter.STATE_OFF:
                                mUnpairedLayout.setVisibility(View.VISIBLE);
                                mPCListLayout.setVisibility(View.GONE);
                                mPairBtn.setEnabled(false);
                                mBTDisconnectedSnackBar.show();
                                mListedPCS.clear();
                                Log.d(TAG, "onReceive: " +
                                        "Bluetooth not enabled! Prompting user to enable!");
                                break;
                        }
                        // If a device is connected, remove it from pcRecViewAdapter
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED: {
                        BluetoothDevice bluetoothDevice = intent
                                .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (bluetoothDevice != null) {
                            removeFromPcRecViewAdapter(bluetoothDevice);
                        }
                        break;
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothConnectService.ADD_TO_MAIN_ACTIVITY_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        getApplicationContext().registerReceiver(mainActivityReceiver, filter);
    }

    private void addToPcRecViewAdapter(String pcAddress) {
        mListedPCS.add(Utils.getPairedPC(pcAddress));
        mPCRecViewAdapter.notifyItemInserted(mListedPCS.size() - 1);
        mUnpairedLayout.setVisibility(View.GONE);
        mPCListLayout.setVisibility(View.VISIBLE);
    }

    private void removeFromPcRecViewAdapter(BluetoothDevice bluetoothDevice) {
        int position = 0;
        for (PairedPC pairedPC : mListedPCS) {
            if (pairedPC.getAddress().equals(bluetoothDevice.getAddress())) {
                mListedPCS.remove(pairedPC);
                mPCRecViewAdapter.notifyItemRemoved(position);
                break;
            } else {
                position += 1;
            }
        }

        // If there are no PC's paired, hide the pc list and show the unpaired layout.
        if (mListedPCS.size() == 0) {
            mUnpairedLayout.setVisibility(View.VISIBLE);
            mPCListLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        // If the back button is pressed, close the drawer, but don't navigate
        // the user to StartupActivity.
        mMenuDrawer.closeDrawer(GravityCompat.START);
    }
}