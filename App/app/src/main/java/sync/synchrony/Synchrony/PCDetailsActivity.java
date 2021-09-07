package sync.synchrony.Synchrony;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;

import com.example.Synchrony.R;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

/**
 * This class shows all of the PC's details and also contains additional
 * options for the user to use to sync with the client PC.
 */
public class PCDetailsActivity extends AppCompatActivity {
    // Key variables.
    public static final String PC_ADDRESS_KEY = "pcAddressKey";

    // Constant used to tell BluetoothConnectionThread to send the clipboard to the client device.
    public static final String SEND_CLIPBOARD_ACTION = "sendClipboardAction";

    // Constant used to tell BluetoothConnectionThread to do sync with client device.
    public static final String START_SYNC_ACTION = "startSyncAction";

    // Constant used to obtain clipboard text from PCDetailsActivity.
    public static final String CLIPBOARD_KEY = "clipboardKey";

    // Associated PairedPC variable.
    private PairedPC mPairedPC;

    // UI Variables.
    private Button mSendClipboardBtn;
    private Button mSyncBtn;
    private Button mSendFileBtn;
    private SwitchCompat mPCConnectingSwitch;

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
                        case Utils.CONNECTION_CHANGED_ACTION: {
                            if (recipientAddress.equals(mPairedPC.getAddress())) {
                                mPairedPC = Utils.getPairedPC(recipientAddress);
                                mPCConnectingSwitch.setChecked(Objects.requireNonNull(mPairedPC)
                                        .isConnecting());
                                mPCConnectingSwitch.setEnabled(Utils.isConnected(recipientAddress));
                                break;
                            }
                        }

                        case BluetoothConnectionThread.SYNC_SOCKET_CONNECTION_CHANGE_ACTION: {
                            mPairedPC = Utils.getPairedPC(recipientAddress);
                            mSyncBtn.setEnabled(Objects.requireNonNull(mPairedPC)
                                    .isSyncSocketConnected());
                            mSendClipboardBtn.setEnabled(mPairedPC.isSyncSocketConnected());
                            mSendFileBtn.setEnabled(mPairedPC.isSyncSocketConnected());
                            break;
                        }

                        case Syncer.SYNC_ACTIVITY_CHANGE_ACTION: {
                            boolean currentlySyncing = Objects.requireNonNull(
                                    Utils.getPairedPC(recipientAddress)).isCurrentlySyncing();
                            if (!currentlySyncing) {
                                setLastSyncText(recipientAddress);
                            }

                            if (!Objects.requireNonNull(mPairedPC)
                                    .isSyncSocketConnected()) {
                                mSyncBtn.setEnabled(false);
                            } else {
                                mSyncBtn.setEnabled(!currentlySyncing);
                            }
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.CONNECTION_CHANGED_ACTION);
        filter.addAction(BluetoothConnectionThread.SYNC_SOCKET_CONNECTION_CHANGE_ACTION);
        filter.addAction(Syncer.SYNC_ACTIVITY_CHANGE_ACTION);
        getApplicationContext().registerReceiver(mainActivityReceiver, filter);
    }

    private void setLastSyncText(String pcAddress) {
        TextView pcSyncText = findViewById(R.id.pcSyncText);
        Date lastSync = Objects.requireNonNull(
                Utils.getPairedPC(pcAddress)).getLastSync();
        if (lastSync != null) {
            DateFormat formatter = SimpleDateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT);
            String lastSyncString = formatter.format(lastSync);
            pcSyncText.setText(lastSyncString);
        } else {
            pcSyncText.setText("Never");
        }
    }

    public static void broadcastClipboard(Context context, String pcAddress, String clipboard) {
        Intent sendClipboardIntent = new Intent();
        sendClipboardIntent.setAction(SEND_CLIPBOARD_ACTION);
        sendClipboardIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, pcAddress);
        sendClipboardIntent.putExtra(CLIPBOARD_KEY, clipboard);
        context.sendBroadcast(sendClipboardIntent);
    }

    private void initializeUi() {
        TextView pcNameText = findViewById(R.id.pcNameText);
        TextView pcAddressText = findViewById(R.id.pcAddressText);
        TextView pcTypeText = findViewById(R.id.pcTypeText);

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

        // Set the pc connecting switch to mark the PC as connecting or not
        // from the details activity.
        mPCConnectingSwitch = findViewById(R.id.pcConnectingSwitch);
        mPCConnectingSwitch.setChecked(mPairedPC.isConnecting());
        mPCConnectingSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                mPairedPC.setConnecting(isChecked));
        mPCConnectingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Utils.broadcastConnectionChange(this.getApplicationContext(), mPairedPC.getAddress());

            if (!isChecked) {
                for (BluetoothConnectionThread bluetoothConnectionThread :
                        Utils.getCurrentlyRunningThreads()) {
                    if (mPairedPC.getAddress()
                            .equals(bluetoothConnectionThread.getDeviceAddress())) {
                        if (bluetoothConnectionThread.isAlive()) {
                            new Thread(() -> {
                                runOnUiThread(() -> mPCConnectingSwitch.setEnabled(false));
                                while (bluetoothConnectionThread.isAlive()) {
                                    SystemClock.sleep(10);
                                }
                                runOnUiThread(() -> mPCConnectingSwitch.setEnabled(true));
                            }).start();
                        }
                    }
                }
            }

            Objects.requireNonNull(
                    Utils.getPairedPC(mPairedPC.getAddress())).setConnecting(isChecked);
        });

        SwitchCompat pcConnectAutomaticallySwitch = findViewById(R.id.PCConnectAutomaticallySwitch);
        pcConnectAutomaticallySwitch.setChecked(mPairedPC.isConnectingAutomatically());
        pcConnectAutomaticallySwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                Utils.setPCConnectingAutomatically(mPairedPC.getAddress(), isChecked));

        initializeSendClipboardBtn();
        initializeSendFileBtn();

        // Set sync button to start sync with paired pc.
        mSyncBtn = findViewById(R.id.syncButton);
        mSyncBtn.setOnClickListener(v ->
                startSync(this, mPairedPC.getAddress()));
        boolean currentlySyncing = Objects.requireNonNull(
                Utils.getPairedPC(mPairedPC.getAddress())).isCurrentlySyncing();
        mSyncBtn.setEnabled(mPairedPC.isSyncSocketConnected() && !currentlySyncing);

        setLastSyncText(mPairedPC.getAddress());
    }

    private void initializeSendClipboardBtn() {
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
                    broadcastClipboard(this, mPairedPC.getAddress(), clipboard);
                }
            } else {
                Toast.makeText(this, "Clipboard is empty!", Toast.LENGTH_SHORT).show();
            }
        });
        mSendClipboardBtn.setEnabled(mPairedPC.isSyncSocketConnected());
    }

    public void initializeSendFileBtn() {
        ActivityResultLauncher<Intent> sendFileActivityResultsLauncher =
                this.registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(), result -> {
                            if (result.getResultCode() == Activity.RESULT_OK) {
                                if (result.getData() != null) {
                                    Thread fileSendThread = new Thread(() -> {
                                        Uri fileUri = result.getData().getData();

                                        Cursor cursor = getContentResolver().query(fileUri,
                                                null, null,
                                                null, null);
                                        int nameIndex = cursor.getColumnIndex(
                                                OpenableColumns.DISPLAY_NAME);
                                        cursor.moveToFirst();
                                        String fileName = cursor.getString(nameIndex);
                                        cursor.close();

                                        Intent intent = new Intent();
                                        intent.setAction(Intent.ACTION_SEND);
                                        intent.setComponent(new ComponentName(
                                                "com.android.bluetooth",
                                                "com.android.bluetooth.opp.BluetoothOppLauncherActivity"));
                                        intent.setType("*/*");
                                        File file = new File(fileUri.getPath());
                                        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                                        startActivity(intent);

                                    });
                                    fileSendThread.start();
                                }
                            }
                        }
                );

        mSendFileBtn = findViewById(R.id.sendFileButton);
        mSendFileBtn.setEnabled(mPairedPC.isSyncSocketConnected());
        mSendFileBtn.setOnClickListener(v -> {
            Intent chooseFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
            chooseFileIntent.setType("*/*");
            chooseFileIntent = Intent.createChooser(chooseFileIntent, "Choose a file to send");

            sendFileActivityResultsLauncher.launch(chooseFileIntent);
        });

    }

//    public void initializeSendFileBtn() {
//        mSendFileBtn = findViewById(R.id.sendFileButton);
//        mSendFileBtn.setEnabled(mPairedPC.isSyncSocketConnected());
//        mSendFileBtn.setOnClickListener(v -> {
//            Uri imageUri = FileProvider.getUriForFile(
//                    this,
//                    "com.example.homefolder.example.provider", //(use your app signature + ".provider" )
//                    imageFile);
//        });
//
//    }

    public static void startSync(Context context, String pcAddress) {
        Intent doSyncIntent = new Intent();
        doSyncIntent.setAction(START_SYNC_ACTION);
        doSyncIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, pcAddress);
        context.getApplicationContext().sendBroadcast(doSyncIntent);
    }
}