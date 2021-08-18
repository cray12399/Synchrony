package com.example.app;

import static com.example.app.App.connectedDevicesChannelID;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.provider.Telephony;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

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
    public static final String SOCKET_CONNECTION_CHANGE_ACTION = "socketConnectionChangeAction";

    // Constant used by BluetoothConnectionThread's notification to stop the
    // connection from the notification.
    private static final String STOP_CONNECTION_ACTION = "stopConnectionAction";

    // Bluetooth timing variables. Timing used to track connection status.
    private long mSocketConnectedTime = -1;
    private long mLastServerHeartBeat = -1;
    private long mLastClientHeartBeat = -1;

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

    /**
     * Method used to receive commands from client.
     */

    public String receiveCommand() {
        BluetoothDevice device = mBluetoothSocket.getRemoteDevice();
        final String deviceTag = String.format("%s (%s)", device.getName(), device.getAddress());

        try {
            // If there are bytes in the input stream, try to receive them.
            InputStream inputStream = mBluetoothSocket.getInputStream();
            if (inputStream.available() > 0) {
                int available = inputStream.available();
                byte[] bytes = new byte[available];
                int bytesRead = inputStream.read(bytes);
                Log.d(TAG, String.format("receiveCommand: " +
                                "%d bytes successfully received from client for device: %s!",
                        bytesRead, deviceTag));

                // Update heartbeat since a regular command is equivalent to a heartbeat.
                mLastClientHeartBeat = System.currentTimeMillis();

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

    /**
     * Method used to send commands to the client. Static so that Syncer can use it.
     */
    public static void sendCommand(BluetoothSocket bluetoothSocket, String stringCommand) {
        BluetoothDevice device = bluetoothSocket.getRemoteDevice();
        final String deviceTag = String.format("%s (%s)", device.getName(), device.getAddress());

        try {
            byte[] command = (stringCommand + Utils.COMMAND_DELIMITER).getBytes();
            OutputStream outputStream = bluetoothSocket.getOutputStream();
            outputStream.write(command);

            Log.d(TAG, String.format("sendCommand: " +
                            "Successfully sent command to client for device: %s: %s!",
                    deviceTag, new String(command)));
        } catch (IOException e) {
            Log.e(TAG, String.format("sendCommand: " +
                    "Couldn't send command to client for device: %s!", deviceTag), e);
        }
    }

    private void broadcastSocketConnectionChange(boolean socketConnected) {
        Objects.requireNonNull(
                Utils.getPairedPC(mDevice.getAddress())).setSocketConnected(socketConnected);

        Intent socketConnectedIntent = new Intent();
        socketConnectedIntent.setAction(SOCKET_CONNECTION_CHANGE_ACTION);
        socketConnectedIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, mDevice.getAddress());
        mContext.getApplicationContext().sendBroadcast(socketConnectedIntent);
    }
    @Override
    public void run() {
        // Since the socketConnected field is transient, the thread needs to
        // set it as true so it isn't null.
        broadcastSocketConnectionChange(true);

        mSocketConnectedTime = System.currentTimeMillis();

        createNotification();

        while (!interrupted()) {
            manageConnectedSocket();
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

    private void registerBluetoothConnectionThreadReceiver() {
        mBluetoothConnectionThreadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String broadcastRecipient = intent.getStringExtra(Utils.RECIPIENT_ADDRESS_KEY);
                if (broadcastRecipient != null && broadcastRecipient.equals(mDevice.getAddress())) {
                    switch (intent.getAction()) {
                        // If the user sent their clipboard from the PCDetailActivity.
                        case PCDetailsActivity.SEND_CLIPBOARD_ACTION: {
                            String clipboard = intent.getStringExtra(
                                    PCDetailsActivity.CLIPBOARD_KEY);

                            sendCommand(mBluetoothSocket,
                                    String.format("incoming_clipboard: %s", clipboard));

                            Toast.makeText(mContext,
                                    String.format("Clipboard sent to %s!", mDevice.getName()),
                                    Toast.LENGTH_SHORT).show();
                            break;
                        }

                        // If the user opts to stop the connection via the thread's notification.
                        case STOP_CONNECTION_ACTION: {
                            Log.d(TAG, String.format("onReceive: " +
                                    "Stopping connection for device: %s...", mDeviceTag));

                            // Cancel the notification.
                            NotificationManagerCompat notificationManager =
                                    NotificationManagerCompat.from(context);
                            notificationManager.cancel(mDevice.getAddress(), mNotificationID);

                            // Set the PC as non-connecting
                            Objects.requireNonNull(Utils.getPairedPC(
                                    mDevice.getAddress())).setConnecting(false);

                            // Broadcast connection change to MainActivity.
                            Utils.broadcastConnectionChange(mContext.getApplicationContext(),
                                    mDevice.getAddress());
                            break;
                        }

                        // If the user chooses to sync the device manually.
                        case PCDetailsActivity.START_SYNC_ACTION: {
                            startSync(Syncer.SYNC_ALL);
                            break;
                        }
                    }
                } else {
                    switch (intent.getAction()) {
                        case (Telephony.Sms.Intents.SMS_RECEIVED_ACTION): {
                            startSync(Syncer.SYNC_MESSAGES);
                            break;
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(PCDetailsActivity.SEND_CLIPBOARD_ACTION);
        filter.addAction(STOP_CONNECTION_ACTION);
        filter.addAction(PCDetailsActivity.START_SYNC_ACTION);
        filter.addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
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
                broadcastSocketConnectionChange(false);
                Log.d(TAG, String.format("closeSockets: " +
                        "Bluetooth socket closed for device: %s!", mDeviceTag));
            } catch (IOException e) {
                Log.e(TAG, String.format("closeSockets: " +
                        "Error closing bluetooth socket thread for device: %s!", mDeviceTag), e);
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
        stopConnectionIntent.setAction(STOP_CONNECTION_ACTION);
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

        } else if (clientCommand.contains("have_message_ids:")) {
            if (mSyncer != null) {
                Gson gson = new Gson();
                String jsonString = clientCommand.split(": ")[1];
                ArrayList<Long> clientMessageIDs = gson.fromJson(jsonString,
                        new TypeToken<ArrayList<Long>>() {}.getType());
                mSyncer.setClientMessageIDs(clientMessageIDs);
            }
        }  else if (clientCommand.contains("have_call_ids:")) {
            if (mSyncer != null) {
                Gson gson = new Gson();
                String jsonString = clientCommand.split(": ")[1];
                ArrayList<Long> clientCallIDs = gson.fromJson(jsonString,
                        new TypeToken<ArrayList<Long>>() {}.getType());
                mSyncer.setClientCallIds(clientCallIDs);
            }
        }
    }

    /** Sends a heartbeat to the client at a defined interval to track connection status. */
    public void sendHeartbeat() {
        // Timing between heartbeats.
        final int SEND_HEARTBEAT_TIMING = 5000;

        if (mLastServerHeartBeat == -1 ||
                System.currentTimeMillis() - mLastServerHeartBeat > SEND_HEARTBEAT_TIMING) {
            sendCommand(mBluetoothSocket, "server_heartbeat");

            mLastServerHeartBeat = System.currentTimeMillis();
        }
    }

    public void checkHeartbeatTiming() {
        if (mLastServerHeartBeat != -1) {
            final long socketTimeout = 10000;
            // If the client has already sent a heartbeat.
            if (mLastClientHeartBeat != -1) {
                // If the difference between the last server heartbeat time and the last
                // client heart beat time is more than 10 seconds, assume the socket
                // connection is closed.
                if (mLastServerHeartBeat - mLastClientHeartBeat > socketTimeout) {
                    interrupt();
                    Log.d(TAG, "manageConnectedSocket: " +
                            "No response from client. Socket connection timeout.");
                }

                // If the client hasn't sent a heart beat, check the connection against the initial
                // connection time. If it is greater than 10 seconds, assume the socket
                // connection is closed.
            } else if (System.currentTimeMillis() - mSocketConnectedTime > socketTimeout) {
                Log.d(TAG, "manageConnectedSocket: " +
                        "No initial response from client. Socket connection timeout.");
                interrupt();
            }
        }
    }

    /**
     * Main function which manages the PC's connection. Handles client command
     * and tracks the connection status using heartbeats
     */
    private void manageConnectedSocket() {
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
        checkHeartbeatTiming();

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
