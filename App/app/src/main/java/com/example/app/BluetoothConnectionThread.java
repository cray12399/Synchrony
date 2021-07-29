package com.example.app;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.example.app.App.CONNECTED_DEVICES_CHANNEL_ID;

// This thread manages the connection between the app and a Paired PC
class BluetoothConnectionThread extends Thread {
    private final Utils UTILS;
    private static final String TAG = "BluetoothConnectionThread";
    private final int NOTIFICATION_ID;
    private final Context CONTEXT;
    private final String DEVICE_TAG;
    private final BluetoothDevice DEVICE;
    private BluetoothServerSocket bluetoothServerSocket;
    private BluetoothSocket bluetoothSocket;
    private boolean socketConnected = false;
    private long lastHeartbeat = -1;

    public BluetoothConnectionThread(Context context, BluetoothDevice device, int notificationID) {
        this.CONTEXT = context;
        this.DEVICE = device;
        this.NOTIFICATION_ID = notificationID;
        this.DEVICE_TAG = String.format("%s (%s)", DEVICE.getName(), DEVICE.getAddress());
        this.UTILS = Utils.getInstance(context);
    }

    @Override
    public void run() {
        while (!interrupted()) {
            // The thread starts by creating a listening notification and trying to create
            // a BluetoothServerSocket
            createNotification(false);
            bluetoothServerSocket = null;
            while(bluetoothServerSocket == null && !interrupted()) {
                bluetoothServerSocket = getBluetoothServerSocket();
            }

            // Once the BluetoothServerSocket is created, try to create a BluetoothSocketConnection
            while (!interrupted()) {
                Log.d(TAG, String.format("run: Creating socket for device: %s...", DEVICE_TAG));
                bluetoothSocket = getBluetoothSocket();

                if (bluetoothSocket != null) {
                    Log.d(TAG, String.format("run: Socket created for device: %s!",
                            DEVICE_TAG));
                    socketConnected = true;
                }
            }

            // Now that the BluetoothSocket is connected, create a connected notification
            // and begin managing the connection.
            createNotification(true);
            while (socketConnected && !interrupted()) {
                String clientCommand = receiveCommand(bluetoothSocket);
                if (clientCommand != null) {
                    handleCommand(clientCommand);
                }

                checkSendClipboard();
                sendHeartbeat();
            }
            // If the socket is disconnected or the thread is interrupted, close the sockets.
            closeSockets(bluetoothSocket, bluetoothServerSocket);
        }

        // If the thread is interrupted, remove the notification.
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(CONTEXT);
        notificationManager.cancel(DEVICE.getAddress(), NOTIFICATION_ID);
        Log.d(TAG, String.format("run: Stopped connection thread for device %s!", DEVICE_TAG));
    }

    // This method gets the BluetoothServerSocket
    public BluetoothServerSocket getBluetoothServerSocket() {
        BluetoothServerSocket tmp = null;
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            Log.d(TAG, String.format("run: " +
                    "Creating bluetooth server socket for device: %s...", DEVICE_TAG));
            tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    CONTEXT.getString(R.string.app_name), UTILS.getUuid());
            Log.d(TAG, String.format("run: bluetooth server socket" +
                    " created for device: %s...", DEVICE_TAG));

            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();
        } catch (IOException e) {
            Log.e(TAG, String.format("run: " +
                            "Creation of bluetooth server socket for device: %s FAILED! ",
                    DEVICE_TAG), e);
            // Timeout in case of failure to create BluetoothServerSocket.
            SystemClock.sleep(5000);
        }
        bluetoothServerSocket = tmp;

        return bluetoothServerSocket;
    }

    // This method tries to connect to the client device by creating a BluetoothSocket
    public BluetoothSocket getBluetoothSocket() {
        BluetoothSocket socket = null;
        try {
            if (!interrupted()) {
                Log.d(TAG, String.format("getBluetoothSocket: " +
                        "Listening for client from device: %s...", DEVICE_TAG));
                socket = bluetoothServerSocket.accept(5000);
                System.out.println(socket.isConnected());
            }
        } catch (IOException e) {
            // If statement so that I am not constantly getting timeout exceptions
            // in the output when trying to connect.
            if (!e.toString().contains("Try again")) {
                Log.e(TAG, String.format("getBluetoothSocket: " +
                        "Error connecting to client for device: %s! ", DEVICE_TAG), e);
            }
        }

        if (socket != null) {
            // If the socket is not the target device, disconnect it and try again.
            if (socket.getRemoteDevice().getAddress().equals(DEVICE.getAddress())) {
                Log.d(TAG, String.format("getBluetoothSocket: " +
                        "Socket created for device: %s!", DEVICE_TAG));
                return socket;
            } else {
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e(TAG, String.format("getBluetoothSocket: " +
                            "Error closing socket for device: %s!", DEVICE_TAG), e);
                }
            }
        }

        return null;
    }

    // Creates the notification for the thread. Connected boolean changes the content
    // of the notification to reflect the connection status.
    private void createNotification(boolean connected) {
        // This intent is used to allow the user to set the PC as inactive from the notification
        Intent stopSyncIntent = new Intent(CONTEXT, ConnectedPCNotificationReceiver.class);
        stopSyncIntent.setAction(ConnectedPCNotificationReceiver.STOP_SYNC_ACTION);
        stopSyncIntent.putExtra(ConnectedPCNotificationReceiver.PC_ADDRESS_KEY,
                DEVICE.getAddress());
        stopSyncIntent.putExtra(ConnectedPCNotificationReceiver.PC_NAME_KEY, DEVICE.getName());
        stopSyncIntent.putExtra(ConnectedPCNotificationReceiver.NOTIFICATION_ID_KEY,
                NOTIFICATION_ID);
        PendingIntent stopSyncPendingIntent = PendingIntent.getBroadcast(CONTEXT, NOTIFICATION_ID,
                stopSyncIntent, 0);

        // Intent used to navigate the user to the details activity if the notification is pressed
        Intent detailsIntent = new Intent(CONTEXT, PCDetailActivity.class);
        detailsIntent.putExtra(PCDetailActivity.PC_ADDRESS_KEY, DEVICE.getAddress());
        PendingIntent detailsPendingIntent = TaskStackBuilder.create(CONTEXT)
                .addNextIntentWithParentStack(detailsIntent)
                .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        // Create the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(CONTEXT);
        final String CONNECTED_PC_GROUP = "connectedPCS";
        NotificationCompat.Builder connectedNotification =
                new NotificationCompat.Builder(
                        CONTEXT, CONNECTED_DEVICES_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_placeholder_logo)
                        .setContentTitle(DEVICE.getName())
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

        notificationManager.notify(DEVICE.getAddress(),
                NOTIFICATION_ID, connectedNotification.build());
    }

    // Handles all incoming commands from the client and runs the relevant functions.
    public void handleCommand(String clientCommand) {
        Log.d(TAG, String.format("handleCommand: " +
                "Command obtained from client for device: %s: %s!", DEVICE_TAG, clientCommand));
        switch (clientCommand) {
            default:
                break;
        }
    }

    // Sends a heartbeat to the client to monitor connection.
    public void sendHeartbeat() {
        // Timing between heartbeats.
        final int HEARTBEAT_TIMING = 5000;
        if (lastHeartbeat == -1 || System.currentTimeMillis() - lastHeartbeat > HEARTBEAT_TIMING) {
            try {
                // This doesn't use the sendCommand method because I don't want
                // the heartbeat to show in the logs.
                OutputStream outputStream = bluetoothSocket.getOutputStream();
                outputStream.write("heartbeat".getBytes());
            } catch (IOException e) {
                Log.e(TAG, String.format("sendHeartbeat: " +
                        "Couldn't send heartbeat to client for device: %s!", DEVICE_TAG), e);
                // If a heartbeat could not be sent, assume the connection no longer exists.
                socketConnected = false;
            }
            // Set the last heartbeat time to keep track of timing.
            lastHeartbeat = System.currentTimeMillis();
        }
    }

    // Check if the pairedPC has a value assigned to sendClipboard.
    private void checkSendClipboard() {
        // If there is a value assigned to sendClipboard, send it to the client.
        if (UTILS.getPairedPC(DEVICE.getAddress()).getSendClipboard() != null) {
            String sendText = UTILS.getPairedPC(DEVICE.getAddress()).getSendClipboard();
            socketConnected = sendCommand(bluetoothSocket,
                    String.format("incoming_clipboard: %s", sendText).getBytes());

            UTILS.getPairedPC(DEVICE.getAddress()).setSendClipboard(null);
        }
    }

    // Method used to receive commands from client. Static so that other classes can use it.
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
        }

        return null;
    }

    // Method used to send commands to the client. Static so that other functions can use it.
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

    // This method closes the BluetoothServerSocket and BluetoothSocket when they aren't needed.
    private static void closeSockets(BluetoothSocket bluetoothSocket,
                                     BluetoothServerSocket bluetoothServerSocket) {
        BluetoothDevice device = bluetoothSocket.getRemoteDevice();
        final String DEVICE_TAG = String.format("%s (%s)", device.getName(), device.getAddress());
        try {
            Log.d(TAG, String.format("closeSockets: " +
                    "Closing bluetooth socket for device: %s...", DEVICE_TAG));
            bluetoothSocket.close();
            Log.d(TAG, String.format("closeSockets: " +
                    "Bluetooth socket closed for device: %s!", DEVICE_TAG));
        } catch (IOException e) {
            Log.e(TAG, String.format("closeSockets: " +
                    "Error closing bluetooth socket thread for device: %s!", DEVICE_TAG), e);
        }

        if (bluetoothServerSocket != null) {
            try {
                Log.d(TAG, String.format("closeSockets: " +
                        "Closing bluetooth server socket for device %s!", DEVICE_TAG));
                bluetoothServerSocket.close();
                Log.d(TAG, String.format("closeSockets: " +
                        "Bluetooth server socket closed for device %s!", DEVICE_TAG));
            } catch (IOException e) {
                Log.e(TAG, String.format("closeSockets: " +
                        "Error closing bluetooth server socket for device: %s!", DEVICE_TAG), e);
            }
        }
    }
}
