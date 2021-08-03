package com.example.app;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import static com.example.app.App.connectedDevicesChannelID;

/**
 * Thread manages the connections for all paired PC's.
 * This is where the majority of the app's functionality takes place.
 */
class BluetoothConnectionThread extends Thread {
    // Key and action variables.
    public static final String SEND_CLIPBOARD_ACTION = "sendClipboardAction";
    public static final String SOCKET_CONNECTION_CHANGE_ACTION = "socketConnectionChangeAction";
    public static final String DO_SYNC_ACTION = "doSyncAction";
    public static final String UPDATE_HEARTBEAT_ACTION = "updateHeartbeatAction";
    public static final String STOP_SYNC_ACTION = "stopSyncAction";
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
    private long mSocketConnectedTime = -1;
    private long mLastServerHeartBeat = -1;
    private long mLastClientHeartBeat = -1;
    private BluetoothServerSocket mBluetoothServerSocket;
    private BluetoothSocket mBluetoothSocket;

    public BluetoothConnectionThread(Context context, BluetoothDevice device) {
        this.mContext = context;
        this.mDevice = device;

        mNotificationID = (int) ((Math.random() * (999999999 - 100000000)) + 100000000);

        this.mDeviceTag = String.format("%s (%s)", mDevice.getName(), mDevice.getAddress());

        registerBluetoothConnectionThreadReceiver();
    }

    /** Method used to receive commands from client. Static so that other classes can use it. */
    public static String receiveCommand(BluetoothSocket bluetoothSocket) {
        BluetoothDevice device = bluetoothSocket.getRemoteDevice();
        final String DEVICE_TAG = String.format("%s (%s)", device.getName(), device.getAddress());
        try {
            // If there are bytes in the input stream, try to receive them.
            InputStream inputStream = bluetoothSocket.getInputStream();
            if (inputStream.available() > 0) {
                Log.d(TAG, String.format("receiveCommand: " +
                        "Incoming bytes from client for device: %s...", DEVICE_TAG));
                int available = inputStream.available();
                byte[] bytes = new byte[available];
                int bytesRead = inputStream.read(bytes);
                Log.d(TAG, String.format("receiveCommand: " +
                                "%d bytes successfully received from client for device: %s!",
                        bytesRead, DEVICE_TAG));
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

    /** Method used to send commands to the client. Static so that other functions can use it. */
    public static boolean sendCommand(BluetoothSocket bluetoothSocket, byte[] command) {
        BluetoothDevice device = bluetoothSocket.getRemoteDevice();
        final String DEVICE_TAG = String.format("%s (%s)", device.getName(), device.getAddress());
        boolean sendSuccessful = true;
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

            sendSuccessful = false;
        }

        return sendSuccessful;
    }

    @Override
    public void run() {
        // Since the socketConnected field is transient, the thread needs to
        // set it as false so it isn't null
        Objects.requireNonNull(Utils.getPairedPC(mDevice.getAddress())).setSocketConnected(false);

        boolean runThread = true;
        while (runThread) {
            // The thread starts by creating a listening notification and trying to create
            // a BluetoothServerSocket
            createNotification(false);
            mBluetoothServerSocket = null;
            while (runThread && mBluetoothServerSocket == null) {
                mBluetoothServerSocket = getBluetoothServerSocket();

                runThread = !interrupted();
            }

            // Once the BluetoothServerSocket is created, try to create a
            // BluetoothSocket connection.
            while (runThread && !Objects.requireNonNull(
                    Utils.getPairedPC(mDevice.getAddress())).isSocketConnected()) {
                Log.d(TAG, String.format("run: Creating socket for device: %s...", mDeviceTag));
                mBluetoothSocket = getBluetoothSocket();

                // If a socket connection was created, set the socketConnected
                // field of the PairedPC to true.
                if (mBluetoothSocket != null) {
                    Log.d(TAG, String.format("run: Socket created for device: %s!",
                            mDeviceTag));
                    Objects.requireNonNull(Utils.getPairedPC(
                            mDevice.getAddress())).setSocketConnected(true);
                }

                runThread = !interrupted();
            }

            // Now that the BluetoothSocket is connected create a connected notification,
            // and begin managing the connection.
            if (Objects.requireNonNull(
                    Utils.getPairedPC(mDevice.getAddress())).isSocketConnected()) {
                createNotification(true);
            }
            while (runThread && Objects.requireNonNull(
                    Utils.getPairedPC(mDevice.getAddress())).isSocketConnected()) {
                manageConnectedSocket();

                runThread = !interrupted();
            }

            // If the socket is disconnected or the thread is interrupted, close
            // the sockets and reset the socket timings
            closeSockets();
            resetTimings();
        }

        closeSockets();
        resetTimings();

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
                            boolean sendSuccessful = sendCommand(mBluetoothSocket,
                                    String.format("incoming_clipboard: %s", clipboard).getBytes());
                            Objects.requireNonNull(Utils.getPairedPC(
                                    mDevice.getAddress())).setSocketConnected(sendSuccessful);

                            if (sendSuccessful) {
                                mLastClientHeartBeat = System.currentTimeMillis();
                            }
                            break;
                        }

                        // If the user opts to stop the sync via the thread's notification.
                        case STOP_SYNC_ACTION: {
                            Log.d(TAG, String.format("onReceive: " +
                                    "Stopping sync for device: %s...", mDeviceTag));

                            // Cancel the notification.
                            NotificationManagerCompat notificationManager =
                                    NotificationManagerCompat.from(context);
                            notificationManager.cancel(mDevice.getAddress(), mNotificationID);

                            // Set the PC as non-syncing
                            Objects.requireNonNull(Utils.getPairedPC(
                                    mDevice.getAddress())).setSyncing(false);

                            // Broadcast sync stop to MainActivity.
                            Utils.notifySyncChanged(mContext.getApplicationContext(),
                                    mDevice.getAddress());
                            break;
                        }

                        case DO_SYNC_ACTION: {
                            Syncer syncer = new Syncer(mContext, mBluetoothSocket);
                            syncer.doSync();
                            break;
                        }

                        case UPDATE_HEARTBEAT_ACTION: {
                            mLastClientHeartBeat = System.currentTimeMillis();
                            break;
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(SEND_CLIPBOARD_ACTION);
        filter.addAction(STOP_SYNC_ACTION);
        filter.addAction(DO_SYNC_ACTION);
        filter.addAction(UPDATE_HEARTBEAT_ACTION);
        mContext.getApplicationContext()
                .registerReceiver(mBluetoothConnectionThreadReceiver, filter);
    }

    /** Used to reset the bluetooth timings in case a connection is closed. */
    private void resetTimings() {
        mSocketConnectedTime = -1;
        mLastServerHeartBeat = -1;
        mLastClientHeartBeat = -1;
    }

    /** Main function which manages the PC's connection. Handles client command
     * and tracks the connection status using heartbeats */
    private void manageConnectedSocket() {
        String clientCommand = receiveCommand(mBluetoothSocket);
        if (clientCommand != null) {
            if (!clientCommand.equals("Receive Failure")) {
                handleCommand(clientCommand);
            } else {
                Objects.requireNonNull(Utils.getPairedPC(mDevice.getAddress()))
                        .setSocketConnected(false);
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
                    Objects.requireNonNull(Utils.getPairedPC(
                            mDevice.getAddress())).setSocketConnected(false);
                    Log.d(TAG, "manageConnectedSocket: " +
                            "No response from client. Socket connection timeout.");
                }
                // If the client hasn't sent a heart beat, check the connection against the initial
                // connection time. If it is greater than 10 seconds, assume the socket
                // connection is closed.
            } else if (System.currentTimeMillis() - mSocketConnectedTime > socketTimeout) {
                Log.d(TAG, "manageConnectedSocket: " +
                        "No response from client. Socket connection timeout.");
                Objects.requireNonNull(
                        Utils.getPairedPC(mDevice.getAddress())).setSocketConnected(false);
            }
        }

        // Sleep the thread so it isn't draining the phone's resources.
        SystemClock.sleep(500);
    }

    /** Method closes the BluetoothServerSocket and BluetoothSocket when they aren't needed. */
    private void closeSockets() {
        if (mBluetoothSocket != null) {
            try {
                Log.d(TAG, String.format("closeSockets: " +
                        "Closing bluetooth socket for device: %s...", mDeviceTag));
                mBluetoothSocket.close();
                Objects.requireNonNull(Utils.getPairedPC(
                        mDevice.getAddress())).setSocketConnected(false);
                notifySocketConnectionChange();
                Log.d(TAG, String.format("closeSockets: " +
                        "Bluetooth socket closed for device: %s!", mDeviceTag));
            } catch (IOException e) {
                Log.e(TAG, String.format("closeSockets: " +
                        "Error closing bluetooth socket thread for device: %s!", mDeviceTag), e);
            }
        }

        if (mBluetoothServerSocket != null) {
            try {
                Log.d(TAG, String.format("closeSockets: " +
                        "Closing bluetooth server socket for device %s!", mDeviceTag));
                mBluetoothServerSocket.close();
                Log.d(TAG, String.format("closeSockets: " +
                        "Bluetooth server socket closed for device %s!", mDeviceTag));
            } catch (IOException e) {
                Log.e(TAG, String.format("closeSockets: " +
                        "Error closing bluetooth server socket for device: %s!", mDeviceTag), e);
            }
        }
    }

    public BluetoothServerSocket getBluetoothServerSocket() {
        BluetoothServerSocket tmp = null;
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            Log.d(TAG, String.format("run: " +
                    "Creating bluetooth server socket for device: %s...", mDeviceTag));
            tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    mContext.getString(R.string.app_name), Utils.getUuid());
            Log.d(TAG, String.format("run: Bluetooth server socket " +
                    "created for device: %s...", mDeviceTag));

            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();
        } catch (IOException e) {
            Log.e(TAG, String.format("run: " +
                            "Creation of bluetooth server socket for device: %s FAILED! ",
                    mDeviceTag), e);
            // Timeout in case of failure to create BluetoothServerSocket.
            SystemClock.sleep(5000);
        }
        mBluetoothServerSocket = tmp;

        return mBluetoothServerSocket;
    }

    public BluetoothSocket getBluetoothSocket() {
        BluetoothSocket bluetoothSocket = null;
        try {
            if (!interrupted() && mBluetoothServerSocket != null) {
                Log.d(TAG, String.format("getBluetoothSocket: " +
                        "Listening for client from device: %s...", mDeviceTag));
                if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                    bluetoothSocket = mBluetoothServerSocket.accept(2000);
                }
            }
        } catch (IOException e) {
            // If statement so that the log is not constantly getting timeout exceptions.
            if (!e.toString().contains("Try again")) {
                Log.e(TAG, String.format("getBluetoothSocket: " +
                        "Error connecting to client for device: %s! ", mDeviceTag), e);
            }
        }

        if (bluetoothSocket != null) {
            // If the socket is not the target device, disconnect it and try again.
            if (bluetoothSocket.getRemoteDevice().getAddress().equals(mDevice.getAddress())) {
                Log.d(TAG, String.format("getBluetoothSocket: " +
                        "Socket created for device: %s!", mDeviceTag));
                mSocketConnectedTime = System.currentTimeMillis();
                notifySocketConnectionChange();
                return bluetoothSocket;
            } else {
                try {
                    bluetoothSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, String.format("getBluetoothSocket: " +
                            "Error closing socket for device: %s!", mDeviceTag), e);
                }
            }
        }

        return null;
    }

    /**
     * Creates the notification for the thread. Connected boolean changes the content
     * of the notification to reflect the connection status.
     */
    private void createNotification(boolean connected) {
        // This intent is used to allow the user to set the PC as inactive from the notification
        Intent stopSyncIntent = new Intent();
        stopSyncIntent.setAction(STOP_SYNC_ACTION);
        stopSyncIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, mDevice.getAddress());
        PendingIntent stopSyncPendingIntent = PendingIntent.getBroadcast(
                mContext.getApplicationContext(), mNotificationID, stopSyncIntent, 0);

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
                        .setGroup(CONNECTED_PC_GROUP)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOngoing(true)
                        .setContentIntent(detailsPendingIntent)
                        .addAction(R.drawable.ic_pc_disconnected,
                                "Stop Sync", stopSyncPendingIntent);

        // If the device is connected, reflect that in the notification content.
        if (connected) {
            connectedNotification.setContentText("Connected to device!");
        } else {
            connectedNotification.setContentText("Listening for device...");
        }

        notificationManager.notify(mDevice.getAddress(),
                mNotificationID, connectedNotification.build());
    }

    /** Handles all incoming commands from the client and runs the relevant functions. */
    public void handleCommand(String clientCommand) {
        Log.d(TAG, String.format("handleCommand: " +
                "Command obtained from client for device: %s: %s!", mDeviceTag, clientCommand));

        switch (clientCommand) {
            case ("client_heartbeat"):
                mLastClientHeartBeat = System.currentTimeMillis();
            default:
                break;
        }
    }

    /** Sends a heartbeat to the client to track connection status. */
    public void sendHeartbeat() {
        // Timing between heartbeats.
        final int SEND_HEARTBEAT_TIMING = 5000;

        if (mLastServerHeartBeat == -1 ||
                System.currentTimeMillis() - mLastServerHeartBeat > SEND_HEARTBEAT_TIMING) {
            try {
                // This doesn't use the sendCommand method because I don't want
                // the heartbeat to constantly show in the logs.
                OutputStream outputStream = mBluetoothSocket.getOutputStream();
                outputStream.write("server_heartbeat".getBytes());
            } catch (IOException e) {
                Log.e(TAG, String.format("sendHeartbeat: " +
                        "Couldn't send heartbeat to client for device: %s!", mDeviceTag), e);
                // If a heartbeat could not be sent, assume the connection no longer exists.
                Objects.requireNonNull(Utils.getPairedPC(
                        mDevice.getAddress())).setSocketConnected(false);
            }
            // Set the last heartbeat time to keep track of timing.
            mLastServerHeartBeat = System.currentTimeMillis();
        }
    }

    public String getDeviceAddress() {
        return mDevice.getAddress();
    }

    public int getNotificationID() {
        return mNotificationID;
    }

    private void notifySocketConnectionChange() {
        Intent socketConnectedIntent = new Intent();
        socketConnectedIntent.setAction(SOCKET_CONNECTION_CHANGE_ACTION);
        socketConnectedIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY, mDevice.getAddress());
        mContext.getApplicationContext().sendBroadcast(socketConnectedIntent);
    }
}
