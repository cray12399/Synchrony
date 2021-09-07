package sync.synchrony.Synchrony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Utils.initValues(context);

            Log.d(TAG, "onReceive: Starting BluetoothSyncThread in foreground...");
            Intent syncServiceIntent = new Intent(context, BluetoothConnectService.class);
            context.startForegroundService(syncServiceIntent);
        }
    }
}
