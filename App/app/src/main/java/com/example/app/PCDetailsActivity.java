package com.example.app;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.Objects;

/**
 * This class shows all of the PC's details and also contains additional
 * options for the user to use to sync with the client PC.
 */
public class PCDetailsActivity extends AppCompatActivity {
    // Key variables.
    public static final String PC_ADDRESS_KEY = "pcAddressKey";

    // Associated PairedPC variable.
    private PairedPC mPairedPC;

    // UI Variables.
    private Button mSendClipboardBtn;
    private Button mSyncBtn;
    private Button mSendFileBtn;
    private SwitchCompat mPCSyncingSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pc_detail);

        // Get the relevant Paired PC so that the activity can get its
        // current information.
        Intent previousIntent = getIntent();
        mPairedPC = Utils.getPairedPC(previousIntent.getStringExtra(PC_ADDRESS_KEY));

        initializeUi();
        registerReceiver();
    }

    public void registerReceiver() {
        BroadcastReceiver mainActivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String recipientAddress = intent.getStringExtra(
                        Utils.RECIPIENT_ADDRESS_KEY);
                if (recipientAddress.equals(mPairedPC.getAddress())) {
                    switch (intent.getAction()) {
                        case Utils.SYNC_CHANGED_ACTION: {
                            if (recipientAddress.equals(mPairedPC.getAddress())) {
                                mPairedPC = Utils.getPairedPC(recipientAddress);
                                assert mPairedPC != null;
                                mPCSyncingSwitch.setChecked(mPairedPC.isSyncing());
                            }
                        }

                        case BluetoothConnectionThread.SOCKET_CONNECTION_CHANGE_ACTION:
                            mPairedPC = Utils.getPairedPC(recipientAddress);
                            mSyncBtn.setEnabled(mPairedPC.isSocketConnected());
                            mSendClipboardBtn.setEnabled(mPairedPC.isSocketConnected());
                            mSendFileBtn.setEnabled(mPairedPC.isSocketConnected());
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.SYNC_CHANGED_ACTION);
        filter.addAction(BluetoothConnectionThread.SOCKET_CONNECTION_CHANGE_ACTION);
        getApplicationContext().registerReceiver(mainActivityReceiver, filter);
    }

    private void initializeUi() {
        TextView pcNameText = findViewById(R.id.pcNameText);
        TextView pcAddressText = findViewById(R.id.pcAddressText);
        TextView pcTypeText = findViewById(R.id.pcTypeText);
        TextView pcSyncText = findViewById(R.id.pcSyncText);

        // Show the Paired PC's information to the user.
        pcNameText.setText(mPairedPC.getName());
        pcAddressText.setText(mPairedPC.getAddress());
        pcTypeText.setText(mPairedPC.getPCType());

        // Set the back button to go back to the MainActivity.
        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> {
            Intent mainActivityIntent = new Intent(this, MainActivity.class);
            // Set flags to create a new MainActivity just to make sure we are not running its
            // pcRecViewUpdateThread more than once.
            mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(mainActivityIntent);
        });

        // Set the pcActiveSwitch to mark the PC as active or inactive from the details activity.
        mPCSyncingSwitch = findViewById(R.id.pcSyncingSwitch);
        mPCSyncingSwitch.setChecked(mPairedPC.isSyncing());
        mPCSyncingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPairedPC.setSyncing(isChecked);
        });

        mPCSyncingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Utils.notifySyncChanged(this.getApplicationContext(), mPairedPC.getAddress());

            if (!isChecked) {
                for (BluetoothConnectionThread bluetoothConnectionThread :
                        Utils.getCurrentlyRunningThreads()) {
                    if (mPairedPC.getAddress()
                            .equals(bluetoothConnectionThread.getDeviceAddress())) {
                        if (bluetoothConnectionThread.isAlive()) {
                            new Thread() {
                                @Override
                                public void run() {
                                    runOnUiThread(() -> {
                                        mPCSyncingSwitch.setEnabled(false);
                                    });
                                    while (bluetoothConnectionThread.isAlive()) {
                                        SystemClock.sleep(10);
                                    }
                                    runOnUiThread(() -> {
                                        mPCSyncingSwitch.setEnabled(true);
                                    });
                                }
                            }.start();
                        }
                    }
                }
            }

            Objects.requireNonNull(
                    Utils.getPairedPC(mPairedPC.getAddress())).setSyncing(isChecked);
        });

        initializeSendClipboardBtn();

        // UI variables.
        mSyncBtn = findViewById(R.id.syncButton);
        mSyncBtn.setOnClickListener(v -> {
            Intent doSyncIntent = new Intent();
            doSyncIntent.setAction(BluetoothConnectionThread.DO_SYNC_ACTION);
            doSyncIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, mPairedPC.getAddress());
            sendBroadcast(doSyncIntent);
        });
        mSendFileBtn = findViewById(R.id.sendFileButton);

        mSyncBtn.setEnabled(mPairedPC.isSocketConnected());
        mSendClipboardBtn.setEnabled(mPairedPC.isSocketConnected());
        mSendFileBtn.setEnabled(mPairedPC.isSocketConnected());
    }

    private void initializeSendClipboardBtn() {
        // Button is used to copy the user's clipboard and send it to the client PC.
        mSendClipboardBtn = findViewById(R.id.sendClipboardBtn);
        mSendClipboardBtn.setOnClickListener(v -> {
            String clipboard = null;
            ClipboardManager clipboardManager =
                    (ClipboardManager) this.getSystemService(CLIPBOARD_SERVICE);
            ClipData primaryClip = clipboardManager.getPrimaryClip();
            if (primaryClip != null) {
                if (primaryClip.getItemCount() > 0) {
                    ClipData.Item item = primaryClip.getItemAt(0);
                    if (item != null) {
                        clipboard = item.getText().toString();
                    }
                }

                if (clipboard != null) {
                    Intent sendClipboardIntent = new Intent();
                    sendClipboardIntent.setAction(BluetoothConnectionThread.SEND_CLIPBOARD_ACTION);
                    sendClipboardIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY,
                            mPairedPC.getAddress());
                    sendClipboardIntent.putExtra(BluetoothConnectionThread.CLIPBOARD_KEY,
                            clipboard);
                    sendBroadcast(sendClipboardIntent);

                    Toast.makeText(this, "Clipboard sent to PC!",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Clipboard is empty!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}