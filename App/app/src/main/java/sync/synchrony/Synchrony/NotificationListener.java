package sync.synchrony.Synchrony;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;

public class NotificationListener extends NotificationListenerService {
    // List to ensure that no notifications are sent to the PC twice.
    private final ArrayList<Integer> postedNotificationHashes = new ArrayList<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!postedNotificationHashes.contains(sbn.getId()) && !sbn.isOngoing()) {
            postedNotificationHashes.add(sbn.getNotification().extras.toString().hashCode());

            HashMap<String, Object> notificationDetails = getNotificationDetails(sbn);

            Gson gson = new Gson();
            broadcastNotificationDetails(gson.toJson(notificationDetails));
        }
    }

    /**
     * Gets the pertinent details of the notification for the app and puts them into
     * a hash map.
     */
    public HashMap<String, Object> getNotificationDetails(StatusBarNotification sbn) {
        HashMap<String, Object> notificationDetails = new HashMap<>();
        PackageManager packageManager = getApplicationContext().getPackageManager();
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(
                    sbn.getPackageName(), 0);
            String appName = packageManager.getApplicationLabel(appInfo).toString();
            notificationDetails.put("appName", appName);
        } catch (PackageManager.NameNotFoundException e) {
            notificationDetails.put("appName", "Unknown");
        }

        notificationDetails.put("title", sbn.getNotification().extras.get("android.title"));
        notificationDetails.put("subText", sbn.getNotification().extras.get("android.subText"));
        notificationDetails.put("text", sbn.getNotification().extras.get("android.text"));
        notificationDetails.put("infoText", sbn.getNotification().extras.get("android.infoText"));

        return notificationDetails;
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        postedNotificationHashes.remove((Integer) sbn.toString().hashCode());
    }

    /**
     * Broadcasts the notification to the rest of the app to potentially be picked up by a
     * BluetoothConnectionThread so that it can be sent to connected PC's.
     */
    public void broadcastNotificationDetails(String notification) {
        Intent newNotificationIntent = new Intent();
        newNotificationIntent.setAction(BluetoothConnectionThread.NEW_NOTIFICATION_ACTION);
        newNotificationIntent.putExtra(BluetoothConnectionThread.NEW_NOTIFICATION_KEY,
                notification);
        this.sendBroadcast(newNotificationIntent);
    }
}
