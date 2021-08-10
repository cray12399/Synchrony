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
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Objects;

/**
 * Thread manages the connections for all paired PC's.
 * This is where the majority of the app's functionality takes place.
 */
class BluetoothConnectionThread extends Thread {
    // Key and action variables.
    public static final String SEND_CLIPBOARD_ACTION = "sendClipboardAction";
    public static final String SOCKET_CONNECTION_CHANGE_ACTION = "socketConnectionChangeAction";
    public static final String DO_SYNC_ACTION = "doSyncAction";
    public static final String UPDATE_HEARTBEAT_TIMING_ACTION = "updateHeartbeatTimingAction";
    public static final String STOP_CONNECTION_ACTION = "stopConnectionAction";
    public static final String CLIPBOARD_KEY = "clipboardKey";

    // Logging tag variables.
    private static final String TAG = "BluetoothConnectionThread";
    private final String mDeviceTag;

    // Constructor variables.
    private final Context mContext;
    private final BluetoothDevice mDevice;
    private final int mNotificationID;

    // Receiver for the bluetoothConnectionThread. Mainly receives user interactions.
    private BroadcastReceiver mBluetoothConnectionThreadReceiver;

    // Bluetooth socket and timing variables. Timing used to track connection status.
    private final BluetoothSocket mBluetoothSocket;
    private long mSocketConnectedTime = -1;
    private long mLastServerHeartBeat = -1;
    private long mLastClientHeartBeat = -1;

    // Syncer variable used to sync information to the client device in the background.
    private Syncer mSyncer;

    public BluetoothConnectionThread(Context context, BluetoothDevice device,
                                     BluetoothSocket bluetoothSocket) {
        this.mContext = context;
        this.mDevice = device;
        this.mBluetoothSocket = bluetoothSocket;

        mNotificationID = (int) ((Math.random() * (999999999 - 100000000)) + 100000000);

        this.mDeviceTag = String.format("%s (%s)", mDevice.getName(), mDevice.getAddress());

        registerBluetoothConnectionThreadReceiver();
    }

    /**
     * Method used to receive commands from client. Static so that other classes can use it.
     */
    public static String receiveCommand(Context context, BluetoothSocket bluetoothSocket) {
        BluetoothDevice device = bluetoothSocket.getRemoteDevice();
        final String DEVICE_TAG = String.format("%s (%s)", device.getName(), device.getAddress());
        try {
            // If there are bytes in the input stream, try to receive them.
            InputStream inputStream = bluetoothSocket.getInputStream();
            if (inputStream.available() > 0) {
                int available = inputStream.available();
                byte[] bytes = new byte[available];
                int bytesRead = inputStream.read(bytes);
                Log.d(TAG, String.format("receiveCommand: " +
                                "%d bytes successfully received from client for device: %s!",
                        bytesRead, DEVICE_TAG));

                updateHeartbeatTiming(context, bluetoothSocket);

                return new String(bytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, String.format("receiveCommand: " +
                            "Error receiving incoming bytes from client for device: %s!",
                    DEVICE_TAG), e);
            return "Receive Failure";
        }

        return null;
    }

    /**
     * Method used to send commands to the client. Static so that other functions can use it.
     */
    public static void sendCommand(BluetoothSocket bluetoothSocket, byte[] command) {
        BluetoothDevice device = bluetoothSocket.getRemoteDevice();
        final String DEVICE_TAG = String.format("%s (%s)", device.getName(), device.getAddress());
        try {
            Log.d(TAG, String.format("sendCommand: " +
                            "Sending command to client for device: %s: %s...", DEVICE_TAG,
                    new String(command)));
            OutputStream outputStream = bluetoothSocket.getOutputStream();
            outputStream.write(command);
            Log.d(TAG, String.format("sendCommand: " +
                            "Successfully sent command to client for device: %s: %s!",
                    DEVICE_TAG, new String(command)));
        } catch (IOException e) {
            Log.e(TAG, String.format("sendCommand: " +
                    "Couldn't send command to client for device: %s!", DEVICE_TAG), e);
            try {
                bluetoothSocket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    /**
     * Static method is used to update the heartbeat timing of a PC connection from within
     * the BluetoothConnectionThread or a separate class
     */
    private static void updateHeartbeatTiming(Context context, BluetoothSocket bluetoothSocket) {
        Intent updateHeartbeatIntent = new Intent();
        updateHeartbeatIntent.setAction(BluetoothConnectionThread.UPDATE_HEARTBEAT_TIMING_ACTION);
        updateHeartbeatIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY,
                bluetoothSocket.getRemoteDevice().getAddress());
        context.getApplicationContext().sendBroadcast(updateHeartbeatIntent);

        // Update socket connection because sometimes the PCDetailsUI fails to recognize
        // a socket connection change when the socket is rapidly restarted.
        BluetoothDevice bluetoothDevice = bluetoothSocket.getRemoteDevice();
        if (!Objects.requireNonNull(
                Utils.getPairedPC(bluetoothDevice.getAddress())).isSocketConnected()) {
            Objects.requireNonNull(Utils.getPairedPC(bluetoothDevice.getAddress()))
                    .setSocketConnected(true);
            notifySocketConnectionChange(context, bluetoothDevice);
        }
    }

    public static void sendClipboard(Context context, String pcAddress, String clipboard) {
        Intent sendClipboardIntent = new Intent();
        sendClipboardIntent.setAction(BluetoothConnectionThread.SEND_CLIPBOARD_ACTION);
        sendClipboardIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, pcAddress);
        sendClipboardIntent.putExtra(BluetoothConnectionThread.CLIPBOARD_KEY, clipboard);
        context.sendBroadcast(sendClipboardIntent);
    }

    public static void doSync(Context context, String pcAddress) {
        Intent doSyncIntent = new Intent();
        doSyncIntent.setAction(BluetoothConnectionThread.DO_SYNC_ACTION);
        doSyncIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, pcAddress);
        context.getApplicationContext().sendBroadcast(doSyncIntent);
    }

    private static void notifySocketConnectionChange(Context context,
                                                     BluetoothDevice bluetoothDevice) {
        Intent socketConnectedIntent = new Intent();
        socketConnectedIntent.setAction(SOCKET_CONNECTION_CHANGE_ACTION);
        socketConnectedIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, bluetoothDevice.getAddress());
        context.getApplicationContext().sendBroadcast(socketConnectedIntent);
    }

    @Override
    public void run() {
        // Since the socketConnected field is transient, the thread needs to
        // set it as false so it isn't null
        Objects.requireNonNull(Utils.getPairedPC(mDevice.getAddress())).setSocketConnected(true);
        notifySocketConnectionChange(mContext, mDevice);

        mSocketConnectedTime = System.currentTimeMillis();

        // Create a connected notification, and begin managing the connection.
        createNotification();

        while (!interrupted()) {
            manageConnectedSocket();
        }

        closeBluetoothSocket();

        // If the thread is interrupted, remove the notification.
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
        notificationManager.cancel(mDevice.getAddress(), mNotificationID);

        // Unregister the BluetoothConnectionThreadReceiver since the thread is stopping.
        mContext.getApplicationContext().unregisterReceiver(mBluetoothConnectionThreadReceiver);

        Log.d(TAG, String.format("run: Stopped connection thread for device %s!", mDeviceTag));
    }

    private void registerBluetoothConnectionThreadReceiver() {
        mBluetoothConnectionThreadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String broadcastRecipient = intent.getStringExtra(Utils.RECIPIENT_ADDRESS_KEY);
                System.out.println(broadcastRecipient);
                if (broadcastRecipient.equals(mDevice.getAddress())) {
                    switch (intent.getAction()) {
                        // If the user sent their clipboard from the PCDetailActivity.
                        case SEND_CLIPBOARD_ACTION: {
                            String clipboard = intent.getStringExtra(CLIPBOARD_KEY);
                            sendCommand(mBluetoothSocket,
                                    String.format("incoming_clipboard: %s;", clipboard).getBytes());
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
                            Utils.notifyConnectChange(mContext.getApplicationContext(),
                                    mDevice.getAddress());
                            break;
                        }

                        // If the user chooses to sync the device manually.
                        case DO_SYNC_ACTION: {
                            // Just in case there are any running syncs, cancel them.
                            if (mSyncer != null) {
                                if (mSyncer.isAlive()) {
                                    mSyncer.interrupt();
                                }
                            }

                            mSyncer = new Syncer(mContext, mBluetoothSocket);
                            mSyncer.start();
                            break;
                        }

                        case UPDATE_HEARTBEAT_TIMING_ACTION: {
                            mLastClientHeartBeat = System.currentTimeMillis();
                            break;
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(SEND_CLIPBOARD_ACTION);
        filter.addAction(STOP_CONNECTION_ACTION);
        filter.addAction(DO_SYNC_ACTION);
        filter.addAction(UPDATE_HEARTBEAT_TIMING_ACTION);
        mContext.getApplicationContext()
                .registerReceiver(mBluetoothConnectionThreadReceiver, filter);
    }

    private void closeBluetoothSocket() {
        if (mBluetoothSocket != null) {
            try {
                Log.d(TAG, String.format("closeSockets: " +
                        "Closing bluetooth socket for device: %s...", mDeviceTag));
                mBluetoothSocket.close();
                interrupt();
                Objects.requireNonNull(
                        Utils.getPairedPC(mDevice.getAddress())).setSocketConnected(false);
                notifySocketConnectionChange(mContext, mDevice);
                Log.d(TAG, String.format("closeSockets: " +
                        "Bluetooth socket closed for device: %s!", mDeviceTag));
            } catch (IOException e) {
                Log.e(TAG, String.format("closeSockets: " +
                        "Error closing bluetooth socket thread for device: %s!", mDeviceTag), e);
            }
        }
    }

    /**
     * Creates the notification for the thread. Connected boolean changes the content
     * of the notification to reflect the connection status.
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

        if (clientCommand.contains("have_contacts:")) {
            if (mSyncer != null) {
                Gson gson = new Gson();
                String jsonString = clientCommand.split(": ")[1];
                HashMap<Integer, Integer> clientContacts = gson.fromJson(jsonString,
                        new TypeToken<HashMap<Integer, Integer>>() {
                        }.getType());
                mSyncer.setClientContacts(clientContacts);
            }
        } else if (clientCommand.contains("have_photos:")) {
            Gson gson = new Gson();
            String jsonString = clientCommand.split(": ")[1];
            HashMap<Integer, Integer> clientPhotos = gson.fromJson(jsonString,
                    new TypeToken<HashMap<Integer, Integer>>() {
                    }.getType());
            mSyncer.setClientContactsPhotos(clientPhotos);
        }
    }

    public String getDeviceAddress() {
        return mDevice.getAddress();
    }

    /** Sends a heartbeat to the client to track connection status. */
    public void sendHeartbeat() {
        // Timing between heartbeats.
        final int SEND_HEARTBEAT_TIMING = 5000;

        if (mLastServerHeartBeat == -1 ||
                System.currentTimeMillis() - mLastServerHeartBeat > SEND_HEARTBEAT_TIMING) {
            sendCommand(mBluetoothSocket, "server_heartbeat;".getBytes());

            mLastServerHeartBeat = System.currentTimeMillis();
        }
    }

    /**
     * Main function which manages the PC's connection. Handles client command
     * and tracks the connection status using heartbeats
     */
    private void manageConnectedSocket() {
        String incomingCommands = receiveCommand(mContext, mBluetoothSocket);
        if (incomingCommands != null) {
            String[] clientCommands = incomingCommands.split(";");
            for (String clientCommand : clientCommands) {
                handleCommand(clientCommand);
            }
        }

        sendHeartbeat();

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

        // Sleep the thread so it isn't draining the phone's resources.
        SystemClock.sleep(500);
    }
}
