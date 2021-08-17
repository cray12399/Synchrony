package com.example.app;

import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashMap;

public class NotificationListener extends NotificationListenerService {
    private final ArrayList<Integer> postedIds = new ArrayList<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        ArrayList<Integer> connectedThreadNotifications = new ArrayList<>();
        for (BluetoothConnectionThread bluetoothConnectionThread :
                Utils.getCurrentlyRunningThreads()) {
            connectedThreadNotifications.add(bluetoothConnectionThread.getNotificationID());
        }

        if (sbn.getId() != 69 && !connectedThreadNotifications.contains(sbn.getId())) {
            if (!postedIds.contains(sbn.getId())) {
                postedIds.add(sbn.getId());

                System.out.println("--------------------------");
                System.out.println("Id: " + sbn.getId());
                System.out.println("Package Name: " + sbn.getPackageName());
                for (String key : sbn.getNotification().extras.keySet()) {
                    System.out.println(key + ": " + sbn.getNotification().extras.get(key));
                }
                System.out.println("--------------------------");
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (postedIds.contains(sbn.getId())) {
            postedIds.remove((Integer) sbn.getId());
        }
    }

    public void broadcastNotification() {

    }
}
