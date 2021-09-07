package sync.synchrony.Synchrony;

import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;

public class TelephoneStateListener extends PhoneStateListener {
    public static final String PHONE_STATE_CHANGED_ACTION = "phoneStateChanged";
    public static final String PHONE_STATE_KEY = "phoneStateKey";
    public static final String PHONE_NUMBER_KEY = "phoneNumberKey";

    private final Context mContext;

    public TelephoneStateListener(Context context) {
        this.mContext = context;
    }

    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        super.onCallStateChanged(state, incomingNumber);

        Intent sendCallStateIntent = new Intent();
        sendCallStateIntent.setAction(PHONE_STATE_CHANGED_ACTION);
        sendCallStateIntent.putExtra(PHONE_STATE_KEY, state);
        sendCallStateIntent.putExtra(PHONE_NUMBER_KEY, incomingNumber);
        mContext.getApplicationContext().sendBroadcast(sendCallStateIntent);

    }
}
