package sync.synchrony.Synchrony;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Base64;

import com.google.common.base.Splitter;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class NotificationListener extends NotificationListenerService {
    private final ArrayList<Integer> postedNotificationHashes = new ArrayList<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        ArrayList<Integer> connectedThreadNotifications = new ArrayList<>();
        for (BluetoothConnectionThread bluetoothConnectionThread :
                Utils.getCurrentlyRunningThreads()) {
            connectedThreadNotifications.add(bluetoothConnectionThread.getNotificationID());
        }

        if (!connectedThreadNotifications.contains(sbn.getId())) {
            if (!postedNotificationHashes.contains(sbn.getId()) && !sbn.isOngoing()) {
                postedNotificationHashes.add(sbn.getNotification().extras.toString().hashCode());

                HashMap<String, Object> notification = new HashMap<>();
                PackageManager packageManager = getApplicationContext().getPackageManager();
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(
                            sbn.getPackageName(), 0);
                    String appName = packageManager.getApplicationLabel(appInfo).toString();
                    notification.put("appName", appName);
                } catch (PackageManager.NameNotFoundException e) {
                    notification.put("appName", "Unknown");
                }

                notification.put("title", sbn.getNotification().extras.get("android.title"));
                notification.put("subText", sbn.getNotification().extras.get("android.subText"));
                notification.put("text", sbn.getNotification().extras.get("android.text"));
                notification.put("infoText", sbn.getNotification().extras.get("android.infoText"));

                Gson gson = new Gson();
                broadcastNotification(gson.toJson(notification));
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        postedNotificationHashes.remove((Integer) sbn.toString().hashCode());
    }

    public void broadcastNotification(String notification) {
        Intent newNotificationIntent = new Intent();
        newNotificationIntent.setAction(BluetoothConnectionThread.NEW_NOTIFICATION_ACTION);
        newNotificationIntent.putExtra(BluetoothConnectionThread.NEW_NOTIFICATION_KEY,
                notification);
        this.sendBroadcast(newNotificationIntent);
    }
}
