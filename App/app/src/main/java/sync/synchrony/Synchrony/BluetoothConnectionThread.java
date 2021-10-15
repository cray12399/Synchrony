package sync.synchrony.Synchrony;

import static sync.synchrony.Synchrony.App.connectedDevicesChannelID;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Switch;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.Synchrony.R;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

/**
 * Thread manages the connections for all paired PC's.
 * This is where the majority of the app's functionality takes place.
 */
class BluetoothConnectionThread extends Thread {
    private static final String TAG = "BluetoothConnectionThread";
    private final String mDeviceTag;

    private final Context mContext;
    private final BluetoothDevice mDevice;
    private final int mNotificationID;

    private final BluetoothSocket mBluetoothSocket;

    private BroadcastReceiver mBluetoothConnectionThreadReceiver;

    // Constant used to notify the rest of the app that mBluetoothSocket
    // was connected or disconnected.
    public static final String SYNC_SOCKET_CONNECTION_CHANGE_ACTION = "socketConnectionChangeAction";

    // Constant used by BluetoothConnectionThread's notification to stop the
    // connection from the notification.
    private static final String sStopConnectionAction = "stopConnectionAction";

    // Constant used to receive new notification info from NotificationListener.
    public static final String NEW_NOTIFICATION_ACTION = "newNotificationAction";

    // Constant used to get value of new notification from NotificationListener.
    public static final String NEW_NOTIFICATION_KEY = "newNotificationKey";

    // Heartbeat timing variable. Timing used to track connection status.
    private long mLastServerHeartBeat = -1;

    // Syncer object used to sync information to the client device in the background.
    private Syncer mSyncer;

    public BluetoothConnectionThread(Context context, BluetoothDevice device,
                                     BluetoothSocket bluetoothSocket) {
        this.mContext = context;
        this.mDevice = device;
        this.mBluetoothSocket = bluetoothSocket;

        // Generate a random notification ID for the BluetoothConnectionThread.
        mNotificationID = (int) ((Math.random() * (999999999 - 100000000)) + 100000000);

        this.mDeviceTag = String.format("%s (%s)", mDevice.getName(), mDevice.getAddress());

        registerBluetoothConnectionThreadReceiver();
    }

    @Override
    public void run() {
        broadcastSyncSocketConnectionChange(true);

        createNotification();

        while (!interrupted()) {
            manageConnectedSyncSocket();

            if (!Objects.requireNonNull(Utils.getPairedPC(mDevice.getAddress())).isConnecting()) {
                break;
            }
        }

        if (mSyncer != null && mSyncer.isAlive()) {
            mSyncer.interrupt();
        }

        closeBluetoothSocket();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
        notificationManager.cancel(mDevice.getAddress(), mNotificationID);

        mContext.getApplicationContext().unregisterReceiver(mBluetoothConnectionThreadReceiver);

        Log.d(TAG, String.format("run: Stopped connection thread for device %s!", mDeviceTag));
    }

    public String receiveCommand() {
        BluetoothDevice device = mBluetoothSocket.getRemoteDevice();
        final String deviceTag = String.format("%s (%s)", device.getName(), device.getAddress());

        try {
            InputStream inputStream = mBluetoothSocket.getInputStream();
            if (inputStream.available() > 0) {
                int available = inputStream.available();
                byte[] bytes = new byte[available];
                int bytesRead = inputStream.read(bytes);
                Log.d(TAG, String.format("receiveCommand: " +
                                "%d bytes successfully received from client for device: %s!",
                        bytesRead, deviceTag));

                return new String(bytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, String.format("receiveCommand: " +
                            "Error receiving incoming bytes from client for device: %s!",
                    deviceTag), e);
            return "Receive Failure";
        }

        return null;
    }

    public static boolean sendCommand(BluetoothSocket bluetoothSocket, String stringCommand) {
        BluetoothDevice device = bluetoothSocket.getRemoteDevice();
        final String deviceTag = String.format("%s (%s)", device.getName(), device.getAddress());

        try {
            byte[] command = (stringCommand + Utils.COMMAND_DELIMITER).getBytes();
            OutputStream outputStream = bluetoothSocket.getOutputStream();
            outputStream.write(command);

            Log.d(TAG, String.format("sendCommand: " +
                            "Successfully sent command to client for device: %s: %s!",
                    deviceTag, new String(command)));

            return true;
        } catch (IOException e) {
            Log.e(TAG, String.format("sendCommand: " +
                    "Couldn't send command to client for device: %s!", deviceTag), e);

            return false;
        }
    }

    private void broadcastSyncSocketConnectionChange(boolean socketConnected) {
        Objects.requireNonNull(
                Utils.getPairedPC(mDevice.getAddress())).setSyncSocketConnected(socketConnected);

        Intent syncSocketConnectionIntent = new Intent();
        syncSocketConnectionIntent.setAction(SYNC_SOCKET_CONNECTION_CHANGE_ACTION);
        syncSocketConnectionIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, mDevice.getAddress());
        mContext.getApplicationContext().sendBroadcast(syncSocketConnectionIntent);
    }

    private void registerBluetoothConnectionThreadReceiver() {
        mBluetoothConnectionThreadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String broadcastRecipient = intent.getStringExtra(Utils.RECIPIENT_ADDRESS_KEY);
                if (broadcastRecipient != null && broadcastRecipient.equals(mDevice.getAddress())) {
                    switch (intent.getAction()) {
                        case PCDetailsActivity.SEND_CLIPBOARD_ACTION: {
                            String clipboard = intent.getStringExtra(
                                    PCDetailsActivity.CLIPBOARD_KEY);

                            sendCommand(mBluetoothSocket,
                                    String.format("incoming_clipboard: %s", clipboard));

                            Toast.makeText(mContext.getApplicationContext(),
                                    String.format("Clipboard sent to %s!", mDevice.getName()),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        }

                        case sStopConnectionAction: {
                            NotificationManagerCompat notificationManager =
                                    NotificationManagerCompat.from(context);
                            notificationManager.cancel(mDevice.getAddress(), mNotificationID);

                            Objects.requireNonNull(Utils.getPairedPC(
                                    mDevice.getAddress())).setConnecting(false);

                            Utils.broadcastConnectionChange(mContext.getApplicationContext(),
                                    mDevice.getAddress());
                            break;
                        }

                        case PCDetailsActivity.START_SYNC_ACTION: {
                            startSync(Syncer.SYNC_ALL);
                            break;
                        }
                    }
                } else {
                    switch (intent.getAction()) {
                        case Telephony.Sms.Intents.SMS_RECEIVED_ACTION: {
                            startSync(Syncer.SYNC_MESSAGES);
                            break;
                        }

                        case NEW_NOTIFICATION_ACTION: {
                            boolean socketConnected = Objects.requireNonNull(
                                    Utils.getPairedPC(mDevice.getAddress())).isSyncSocketConnected();
                            if (socketConnected) {
                                String notification = intent.getStringExtra(NEW_NOTIFICATION_KEY);
                                sendCommand(mBluetoothSocket,
                                        "incoming_notification: " + notification);
                            }
                        }

                        case TelephoneStateListener.PHONE_STATE_CHANGED_ACTION: {
                            int state = intent.getIntExtra(TelephoneStateListener.PHONE_STATE_KEY,
                                    -1);

                            switch (state) {
                                case TelephonyManager.CALL_STATE_RINGING:
                                    System.out.println("RINGING!!!!!!!!!!!!");
                                    break;
                                case TelephonyManager.CALL_STATE_OFFHOOK:
                                    System.out.println("OFFHOOK!!!!!!!!!!!!!!!");
                                    break;
                                case TelephonyManager.CALL_STATE_IDLE:
                                    System.out.println("IDLE!!!!!!!!!!!!");
                                    break;
                            }
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(PCDetailsActivity.SEND_CLIPBOARD_ACTION);
        filter.addAction(PCDetailsActivity.START_SYNC_ACTION);
        filter.addAction(sStopConnectionAction);
        filter.addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        filter.addAction(NEW_NOTIFICATION_ACTION);
        filter.addAction(TelephoneStateListener.PHONE_STATE_CHANGED_ACTION);
        mContext.getApplicationContext()
                .registerReceiver(mBluetoothConnectionThreadReceiver, filter);
    }

    private void startSync(int toSync) {
        if (mSyncer != null) {
            if (mSyncer.isAlive()) {
                mSyncer.interrupt();
            }
        }

        mSyncer = new Syncer(mContext, mBluetoothSocket, toSync);
        mSyncer.start();
    }

    private void closeBluetoothSocket() {
        if (mBluetoothSocket != null) {
            try {
                mBluetoothSocket.close();
                broadcastSyncSocketConnectionChange(false);
                Log.d(TAG, String.format("closeSockets: " +
                        "Bluetooth sync socket closed for device: %s!", mDeviceTag));
            } catch (IOException e) {
                Log.e(TAG, String.format("closeSockets: " +
                        "Error closing bluetooth sync socket for device: %s!", mDeviceTag), e);
            }
        }
    }

    /**
     * Create the notification for the thread.
     */
    private void createNotification() {
        // This intent is used to allow the user to set the PC as non-connecting
        // from the notification
        Intent stopConnectionIntent = new Intent();
        stopConnectionIntent.setAction(sStopConnectionAction);
        stopConnectionIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, mDevice.getAddress());
        PendingIntent stopConnectionPendingIntent = PendingIntent.getBroadcast(
                mContext.getApplicationContext(), mNotificationID, stopConnectionIntent, 0);

        // Intent used to navigate the user to the details activity if the notification is pressed
        Intent detailsIntent = new Intent(mContext, PCDetailsActivity.class);
        detailsIntent.putExtra(PCDetailsActivity.PC_ADDRESS_KEY, mDevice.getAddress());
        PendingIntent detailsPendingIntent = TaskStackBuilder.create(mContext)
                .addNextIntentWithParentStack(detailsIntent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        // Create the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
        final String CONNECTED_PC_GROUP = "connectedPCS";
        NotificationCompat.Builder connectedNotification =
                new NotificationCompat.Builder(
                        mContext, connectedDevicesChannelID)
                        .setSmallIcon(R.drawable.ic_placeholder_logo)
                        .setContentTitle(mDevice.getName())
                        .setContentText("Connected to device!")
                        .setGroup(CONNECTED_PC_GROUP)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOngoing(true)
                        .setContentIntent(detailsPendingIntent)
                        .addAction(R.drawable.ic_pc_disconnected,
                                "Stop Connection", stopConnectionPendingIntent);

        notificationManager.notify(mDevice.getAddress(),
                mNotificationID, connectedNotification.build());
    }

    /** Handles all incoming commands from the client and runs the relevant functions. */
    public void handleCommand(String clientCommand) {
        Log.d(TAG, String.format("handleCommand: " +
                "Command obtained from client for device: %s: %s!", mDeviceTag, clientCommand));

        // If the client sends a list of its currently synced contacts, pass them to the syncer.
        if (clientCommand.contains("have_contact_hashes:")) {
            if (mSyncer != null) {
                Gson gson = new Gson();
                String jsonString = clientCommand.split(": ")[1];
                HashMap<Long, Long> clientContacts = gson.fromJson(jsonString,
                        new TypeToken<HashMap<Long, Long>>() {}.getType());
                mSyncer.setClientContactHashes(clientContacts);
            }

        // If the client sends a list of its currently synced photos, pass them to the syncer.
        } else if (clientCommand.contains("have_contact_photo_hashes:")) {
            if (mSyncer != null) {
                Gson gson = new Gson();
                String jsonString = clientCommand.split(": ")[1];
                HashMap<Long, Long> clientPhotos = gson.fromJson(jsonString,
                        new TypeToken<HashMap<Long, Long>>() {}.getType());
                mSyncer.setClientContactPhotoHashes(clientPhotos);
            }

        // If the client sends a list of its currently synced messages, pass them to the syncer.
        } else if (clientCommand.contains("have_message_ids:")) {
            if (mSyncer != null) {
                Gson gson = new Gson();
                String jsonString = clientCommand.split(": ")[1];
                ArrayList<Long> clientMessageIDs = gson.fromJson(jsonString,
                        new TypeToken<ArrayList<Long>>() {}.getType());
                mSyncer.setClientMessageIDs(clientMessageIDs);
            }

        // If the client sends a list of its currently synced calls, pass them to the syncer.
        }  else if (clientCommand.contains("have_call_ids:")) {
            if (mSyncer != null) {
                Gson gson = new Gson();
                String jsonString = clientCommand.split(": ")[1];
                ArrayList<Long> clientCallIDs = gson.fromJson(jsonString,
                        new TypeToken<ArrayList<Long>>() {}.getType());
                mSyncer.setClientCallIds(clientCallIDs);
            }
        } else if (clientCommand.contains("incoming_clipboard:")) {
            System.out.println(clientCommand);

            ClipboardManager clipboard = (ClipboardManager)
                    mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            String clip_text = clientCommand.split(": ")[1];
            ClipData clip = ClipData.newPlainText("simple_text", clip_text);
            clipboard.setPrimaryClip(clip);
        } else if (clientCommand.contains("do_sync")) {
            startSync(Syncer.SYNC_ALL);
        }
    }

    /** Sends a heartbeat to the client at a defined interval to track connection status. */
    public void sendHeartbeat() {
        // Timing between heartbeats.
        final int SEND_HEARTBEAT_TIMING = 5000;

        if (mLastServerHeartBeat == -1 ||
                System.currentTimeMillis() - mLastServerHeartBeat > SEND_HEARTBEAT_TIMING) {
            if (!sendCommand(mBluetoothSocket, "server_heartbeat")) {
                interrupt();
            }

            mLastServerHeartBeat = System.currentTimeMillis();
        }
    }

    /**
     * Main function which manages the PC's connection. Handles client command
     * and tracks the connection status using heartbeats
     */
    private void manageConnectedSyncSocket() {
        String incomingCommands = receiveCommand();
        if (incomingCommands != null) {
            String[] clientCommands = incomingCommands.split(Utils.COMMAND_DELIMITER);
            for (String clientCommand : clientCommands) {

                // If a command was successfully retrieved, handle it.
                // Otherwise, assume the socket was closed stop the thread.
                if (!clientCommand.equals("Receive Failure")) {
                    handleCommand(clientCommand);
                } else {
                    interrupt();
                }
            }
        }

        // Check if a sync has not been done yet, or if the last sync time
        // is beyond the sync interval. If either is true, sync data with device.
        final long syncInterval = 1000 * 120;
        Date lastSync = Objects.requireNonNull(
                Utils.getPairedPC(mDevice.getAddress())).getLastSync();
        if (lastSync == null || System.currentTimeMillis() - lastSync.getTime() > syncInterval) {
            if (mSyncer == null || !mSyncer.isAlive()) {
                startSync(Syncer.SYNC_ALL);
            }
        }

        sendHeartbeat();

        // Sleep the thread so it isn't draining the phone's resources.
        SystemClock.sleep(500);
    }

    public int getNotificationID() {
        return mNotificationID;
    }

    public String getDeviceAddress() {
        return mDevice.getAddress();
    }
}
