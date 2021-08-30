package sync.synchrony.Synchrony;

import android.Manifest;
import android.bluetooth.BluetoothSocket;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.google.gson.Gson;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Syncer class is a thread that syncs information with the client device in the background. Is
 * run by BluetoothConnectionThread upon syncing.
 */
public class Syncer extends Thread {
    // Logging tag variables.
    private static final String TAG = "Syncer";
    private final String mDeviceTag;

    // Action variables.
    public final static String SYNC_ACTIVITY_CHANGE_ACTION = "syncActivityChangeAction";

    // Int variables to tell the syncer what it should sync.
    public final static int SYNC_ALL = 0;
    public final static int SYNC_CONTACTS = 1;
    public final static int SYNC_MESSAGES = 2;
    public final static int SYNC_CALLS = 3;

    // Tells the syncer what it should sync.
    private final int mToSync;

    // Constructor variables
    private final Context mContext;
    private final BluetoothSocket mBluetoothSocket;

    // Stores a simple readout of the client's contacts and contact photos that contains
    // key value pairs of the contact's ID and the hash of its information or photo.
    private HashMap<Long, Long> mClientContactHashes;
    private HashMap<Long, Long> mClientContactsPhotoHashes;

    // Stores a simple readout of the ids of the messages on the client device.
    private ArrayList<Long> mClientMessageIDs;

    // Store a simple readout of the ids of the calls on the client device.
    private ArrayList<Long> mClientCallIds;

    // Used to determine whether the thread should stop syncing.
    private boolean stopSync = false;

    public Syncer(Context mContext, BluetoothSocket mBluetoothSocket, int toSync) {
        this.mContext = mContext;
        this.mBluetoothSocket = mBluetoothSocket;
        this.mToSync = toSync;

        this.mDeviceTag = String.format("%s (%s)", mBluetoothSocket.getRemoteDevice().getName(),
                mBluetoothSocket.getRemoteDevice().getAddress());
    }

    @Override
    public void run() {
        // Broadcast the starting of the sync background to the rest of the app.
        String pcAddress = mBluetoothSocket.getRemoteDevice().getAddress();
        Objects.requireNonNull(Utils.getPairedPC(pcAddress)).setCurrentlySyncing(true);
        broadcastSyncActivityChange();

        // If contact permissions are given, sync contacts with the device.
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CONTACTS) ==
                PermissionChecker.PERMISSION_GRANTED) {
            if (mToSync == SYNC_ALL || mToSync == SYNC_CONTACTS) {
                new ContactsSync().syncContacts();
            }
        }

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_SMS) ==
                PermissionChecker.PERMISSION_GRANTED) {
            if (mToSync == SYNC_ALL || mToSync == SYNC_MESSAGES) {
                new MessagesSync().syncMessages();
            }
        }

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CALL_LOG) ==
                PermissionChecker.PERMISSION_GRANTED) {
            if (mToSync == SYNC_ALL || mToSync == SYNC_CALLS) {
                new CallSync().syncCalls();
            }
        }

        // Broadcast the stopping of the sync background to the rest of the app.
        Objects.requireNonNull(Utils.getPairedPC(pcAddress)).setCurrentlySyncing(false);
        Utils.setPCLastSync(pcAddress, Calendar.getInstance().getTime());
        broadcastSyncActivityChange();

        Log.d(TAG, String.format("run: Sync background stopped for device: %s!",
                mDeviceTag));
    }

    public void setClientContactHashes(HashMap<Long, Long> clientContactHashes) {
        this.mClientContactHashes = clientContactHashes;
    }

    public void setClientContactPhotoHashes(HashMap<Long, Long> clientContactPhotoHashes) {
        this.mClientContactsPhotoHashes = clientContactPhotoHashes;
    }

    public void setClientMessageIDs(ArrayList<Long> mClientMessageIDs) {
        this.mClientMessageIDs = mClientMessageIDs;
    }

    public void setClientCallIds(ArrayList<Long> mClientCallIds) {
        this.mClientCallIds = mClientCallIds;
    }

    @Override
    public void interrupt() {
        stopSync = true;
        super.interrupt();
    }

    /** Tells the rest of the app whether there is sync background activity occurring or not. */

    private void broadcastSyncActivityChange() {
        Intent syncActivityChangeIntent = new Intent();
        syncActivityChangeIntent.setAction(SYNC_ACTIVITY_CHANGE_ACTION);
        syncActivityChangeIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY,
                mBluetoothSocket.getRemoteDevice().getAddress());
        mContext.getApplicationContext().sendBroadcast(syncActivityChangeIntent);
    }

    public static class Contact {
        private final String mName;
        private transient String mPhoto;
        private final HashMap<String, String> mPhones = new HashMap<>();
        private final HashMap<String, String> mEmails = new HashMap<>();
        private boolean mHasPhoto = false;
        private int mHash;
        private transient int mPhotoHash;
        private long mPrimaryKey;

        public Contact(String name) {
            this.mName = name;
        }

        public String getName() {
            return mName;
        }

        public void generateHash() {
            StringBuilder stringToHash = new StringBuilder();

            stringToHash.append(mName);

            for (Map.Entry<String, String> phone : mPhones.entrySet()) {
                stringToHash.append(phone.getKey()).append(phone.getValue());
            }

            for (Map.Entry<String, String> email : mEmails.entrySet()) {
                stringToHash.append(email.getKey()).append(email.getValue());
            }

            mHash = stringToHash.toString().hashCode();
        }

        public String getPhoto() {
            return mPhoto;
        }

        public void setPhoto(byte[] photoData) {
            String encodedPhoto = Base64.getEncoder().encodeToString(photoData);
            mHasPhoto = true;
            mPhotoHash = encodedPhoto.hashCode();
            this.mPhoto = encodedPhoto;
        }

        public int getPhotoHash() {
            return mPhotoHash;
        }

        public boolean hasPhoto() {
            return mHasPhoto;
        }

        public void addPhone(String phoneTypeName, String phoneNumber) {
            mPhones.put(phoneTypeName, phoneNumber);
        }

        public void addEmail(String emailTypeName, String email) {
            mEmails.put(emailTypeName, email);
        }

        public int getHash() {
            return mHash;
        }

        public long getPrimaryKey() {
            return mPrimaryKey;
        }

        public void setPrimaryKey(long mPrimaryKey) {
            this.mPrimaryKey = mPrimaryKey;
        }

        public String toJson() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }

    private class MessagesSync {
        private void syncMessages() {
            BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                    "check_message_ids");

            // Wait for message ids from client device.
            while (mClientMessageIDs == null) {
                String pcAddress = mBluetoothSocket.getRemoteDevice().getAddress();
                if (!Objects.requireNonNull(Utils.getPairedPC(pcAddress)).isSyncSocketConnected()) {
                    stopSync = true;
                }

                if (stopSync) {
                    break;
                }
            }

            if (!stopSync) {
                ArrayList<Message> phoneMessages = getPhoneMessages();
                sendMessages(phoneMessages);
                deleteOldMessages(phoneMessages);
            }
        }

        private ArrayList<Message> getPhoneMessages() {
            Cursor cursor = mContext.getContentResolver().query(Uri.parse("content://sms/"),
                    null, null, null, null);

            ArrayList<Message> phoneMessages = new ArrayList<>();
            while (cursor.moveToNext() && !stopSync) {
                Message message = new Message();

                message.setId(cursor.getLong(
                        cursor.getColumnIndexOrThrow(Telephony.Sms._ID)));
                message.setBody(cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)));
                message.setThreadId(cursor.getLong(
                        cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)));
                message.setNumber(cursor.getString(
                        cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)));
                message.setRead(cursor.getInt(
                        cursor.getColumnIndexOrThrow(Telephony.Sms.READ)));

                DateFormat formatter = SimpleDateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT);
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(cursor.getLong(
                        cursor.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)));
                message.setDateSent(formatter.format(calendar.getTime()));

                int type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                switch (type) {
                    case Telephony.Sms.MESSAGE_TYPE_INBOX:
                        message.setType("Received");
                        break;
                    case Telephony.Sms.MESSAGE_TYPE_SENT:
                        message.setType("Sent");
                        break;
                    case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                        message.setType("Outbox");
                        break;
                    default:
                        break;
                }

                phoneMessages.add(message);
            }

            cursor.close();

            return phoneMessages;
        }

        private void sendMessages(ArrayList<Message> phoneMessages) {
            // If a message's id isn't present in the client's message id's, send it to the client.
            for (Message phoneMessage : phoneMessages) {
                if (!mClientMessageIDs.contains(phoneMessage.getId())) {
                    BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                            String.format("incoming_message: %s",
                                    phoneMessage.toJson()));
                }
            }
        }

        private void deleteOldMessages(ArrayList<Message> phoneMessages) {
            for (Long clientMessageId : mClientMessageIDs) {
                if (!stopSync) {
                    boolean deleteMessage = true;
                    for (Message phoneMessage : phoneMessages) {
                        if (phoneMessage.getId() == clientMessageId) {
                            deleteMessage = false;
                            break;
                        }
                    }

                    if (deleteMessage) {
                        BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                                ("delete_message: " + clientMessageId));
                    }
                }
            }
        }
    }

    private class CallSync {
        private void syncCalls() {
            BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                    "check_call_ids");

            // Wait for call ids from client device.
            while (mClientCallIds == null) {
                String pcAddress = mBluetoothSocket.getRemoteDevice().getAddress();
                if (!Objects.requireNonNull(Utils.getPairedPC(pcAddress)).isSyncSocketConnected()) {
                    stopSync = true;
                }

                if (stopSync) {
                    break;
                }
            }

            if (!stopSync) {
                ArrayList<Call> phoneCalls = getPhoneCalls();
                sendPhoneCalls(phoneCalls);
                deleteOldPhoneCalls(phoneCalls);
            }
        }

        private ArrayList<Call> getPhoneCalls() {
            Cursor cursor = mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    null, null, null, null);

            ArrayList<Call> phoneCalls = new ArrayList<>();
            while (cursor.moveToNext() && !stopSync) {
                Call call = new Call();

                call.setId(cursor.getLong(
                        cursor.getColumnIndexOrThrow(CallLog.Calls._ID)));
                call.setNumber(cursor.getString(
                        cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));

                int durationMillis = cursor.getInt(
                        cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)) * 1000;
                String duration = String.format(Locale.getDefault(), "%02d:%02d:%02d",
                        TimeUnit.MILLISECONDS.toHours(durationMillis),
                        TimeUnit.MILLISECONDS.toMinutes(durationMillis) %
                                TimeUnit.HOURS.toMinutes(1),
                        TimeUnit.MILLISECONDS.toSeconds(durationMillis) %
                                TimeUnit.MINUTES.toSeconds(1));
                call.setDuration(duration);

                DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT);
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(cursor.getLong(
                        cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)));
                call.setDate(dateFormatter.format(calendar.getTime()));

                int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                switch (type) {
                    case CallLog.Calls.OUTGOING_TYPE:
                        call.setType("Outgoing");
                        break;
                    case CallLog.Calls.INCOMING_TYPE:
                        call.setType("Incoming");
                        break;

                    case CallLog.Calls.MISSED_TYPE:
                        call.setType("Missed");
                        break;
                }

                phoneCalls.add(call);
            }


            cursor.close();
            return phoneCalls;
        }

        private void sendPhoneCalls(ArrayList<Call> phoneCalls) {
            for (Call call : phoneCalls) {
                if (!mClientCallIds.contains(call.getId())) {
                    BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                            String.format("incoming_call: %s",
                                    call.toJson()));
                }
            }
        }

        private void deleteOldPhoneCalls(ArrayList<Call> phoneCalls) {
            for (Long clientCallId : mClientCallIds) {
                if (!stopSync) {
                    boolean deleteCall = true;
                    for (Call phoneCall : phoneCalls) {
                        if (phoneCall.getId() == clientCallId) {
                            deleteCall = false;
                            break;
                        }
                    }

                    if (deleteCall) {
                        BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                                ("delete_call: " + clientCallId));
                    }
                }
            }
        }
    }

    public static class Call {
        private long mId;
        private String mNumber;
        private String mType = "None";
        private String mDate;
        private String mDuration;

        public long getId() {
            return mId;
        }

        public void setId(long mId) {
            this.mId = mId;
        }

        public String getNumber() {
            return mNumber;
        }

        public void setNumber(String mNumber) {
            this.mNumber = mNumber;
        }

        public String getType() {
            return mType;
        }

        public void setType(String mType) {
            this.mType = mType;
        }

        public String getDate() {
            return mDate;
        }

        public void setDate(String mDate) {
            this.mDate = mDate;
        }

        public String getDuration() {
            return mDuration;
        }

        public void setDuration(String mDuration) {
            this.mDuration = mDuration;
        }

        public String toJson() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }

    public static class Message {
        private long mId;
        private long mThreadId;
        private String mNumber;
        private String mDateSent;
        private String mType;
        private String mBody;
        private int mRead;

        public String toJson() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }

        public long getId() {
            return mId;
        }

        public void setId(long mId) {
            this.mId = mId;
        }

        public long getThreadId() {
            return mThreadId;
        }

        public void setThreadId(long mThreadId) {
            this.mThreadId = mThreadId;
        }

        public String getNumber() {
            return mNumber;
        }

        public void setNumber(String mNumber) {
            this.mNumber = mNumber;
        }

        public String getDateSent() {
            return mDateSent;
        }

        public void setDateSent(String mDateSent) {
            this.mDateSent = mDateSent;
        }

        public String getType() {
            return mType;
        }

        public void setType(String mType) {
            this.mType = mType;
        }

        public int getRead() {
            return mRead;
        }

        public void setRead(int mRead) {
            this.mRead = mRead;
        }

        public String getBody() {
            return mBody;
        }

        public void setBody(String mBody) {
            this.mBody = mBody;
        }
    }

    private class ContactsSync {
        private void syncContacts() {
            Log.d(TAG, String.format("syncContacts: Starting contacts sync for device: %s...",
                    mDeviceTag));

            Log.d(TAG, String.format("syncContacts: Requesting contact hashes from device: %s...",
                    mDeviceTag));
            BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                    "check_contact_info_hashes");

            // Wait for contact hashes from client device.
            while (mClientContactHashes == null) {
                String pcAddress = mBluetoothSocket.getRemoteDevice().getAddress();
                if (!Objects.requireNonNull(Utils.getPairedPC(pcAddress)).isSyncSocketConnected()) {
                    stopSync = true;
                }

                if (stopSync) {
                    break;
                }
            }

            ArrayList<Contact> phoneContacts = getPhoneContacts();

            // Once client contact hashes are received send any updated contact info to client and
            // delete any old contacts on the client device.
            if (!stopSync) {
                sendContactsInfo(phoneContacts);
                deleteOldContacts(phoneContacts);
            }

            BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                    "check_contact_photo_hashes");
            while (mClientContactsPhotoHashes == null) {
                String pcAddress = mBluetoothSocket.getRemoteDevice().getAddress();
                if (!Objects.requireNonNull(Utils.getPairedPC(pcAddress)).isSyncSocketConnected()) {
                    stopSync = true;
                }

                if (stopSync) {
                    break;
                }
            }

            if (!stopSync) {
                sendContactPhotos(phoneContacts);
            }
        }

        /** Gets all of the relevant data from the phone's contact database
         *  and puts them into an array list */

        private ArrayList<Contact> getPhoneContacts() {
            ArrayList<Contact> phoneContacts = new ArrayList<>();
            Cursor cursor = mContext.getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI,
                    null, null, null, null);

            while (cursor.moveToNext() && !stopSync) {
                // Get the name of the contact and create a new Contact object for the contact.
                String name = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                Contact currentContact = new Contact(name);

                // Get their primary key, which will be used as an identifier
                // for the contact when syncing.
                long primaryKey = cursor.getLong(
                        cursor.getColumnIndex(ContactsContract.Contacts._ID));
                currentContact.setPrimaryKey(primaryKey);

                // Get the contact's photo, if one exists.
                Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
                        cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts._ID)));
                Cursor photoCursor = mContext.getContentResolver().query(
                        Uri.withAppendedPath(contactUri,
                                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY),
                        new String[]{ContactsContract.Contacts.Photo.PHOTO},
                        null, null, null);
                if (photoCursor != null) {
                    if (photoCursor.moveToFirst()) {
                        byte[] photoData = photoCursor.getBlob(0);
                        if (photoData != null) {
                            currentContact.setPhoto(photoData);
                        }
                    }
                    photoCursor.close();
                }

                // Get the contact's phone numbers.
                String id = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.Contacts.NAME_RAW_CONTACT_ID));
                Cursor phones = mContext.getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID +
                                " = " + id,
                        null,
                        null);
                if (phones != null) {
                    while (phones.moveToNext()) {
                        String phoneLabel = phones.getString(phones.
                                getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL));
                        int phoneType = phones.getInt(phones.
                                getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE));
                        String phoneTypeName = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                mContext.getResources(), phoneType, phoneLabel).toString();
                        String phoneNumber = phones.getString(phones.getColumnIndex(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));

                        currentContact.addPhone(phoneTypeName, phoneNumber);
                    }
                    phones.close();
                }

                // Get the contact's emails.
                Cursor emails = mContext.getContentResolver().query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Email.RAW_CONTACT_ID + " = " + id,
                        null,
                        null);
                if (emails != null) {
                    while (emails.moveToNext()) {
                        String emailLabel = emails.getString(emails.
                                getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL));
                        int emailType = emails.getInt(emails.
                                getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE));
                        String emailTypeName = ContactsContract.CommonDataKinds.Email.getTypeLabel(
                                mContext.getResources(), emailType, emailLabel).toString();
                        String emailAddress = emails.getString(emails.getColumnIndex(
                                ContactsContract.CommonDataKinds.Email.ADDRESS));

                        currentContact.addEmail(emailTypeName, emailAddress);
                    }
                    emails.close();
                }

                // Generate a hash for the client and add it to the list of phone contacts.
                currentContact.generateHash();
                phoneContacts.add(currentContact);
            }

            cursor.close();

            return phoneContacts;
        }

        /** Checks which contacts need their info updated on the client device
         *  and sends their updated info to the client. */

        private void sendContactsInfo(ArrayList<Contact> phoneContacts) {
            Log.d(TAG, String.format("sendContactsInfo: " +
                    "Checking for outdated or missing contacts on device: %s...", mDeviceTag));
            // Iterate over all of the contacts on the phone and check them against
            // the hashes provided by the client device.
            for (Contact phoneContact : phoneContacts) {
                if (!stopSync) {
                    boolean syncContact = true;
                    for (Map.Entry<Long, Long> clientContact : mClientContactHashes.entrySet()) {
                        // If the hashes for both the client and phone match, then don't sync them.
                        long clientContactId = clientContact.getKey();
                        long clientContactHash = clientContact.getValue();
                        if (clientContactId == phoneContact.getPrimaryKey() &&
                                clientContactHash == phoneContact.getHash()) {
                            syncContact = false;
                        }
                    }

                    // If the hashes for the client and phone don't match,
                    // send the updated contact info to the client.
                    if (syncContact) {
                        Log.d(TAG, String.format("sendContactsInfo: " +
                                        "Sending info for contact: %s to device: %s!",
                                phoneContact.getName(), mDeviceTag));

                        BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                                String.format("incoming_contact: %s",
                                        phoneContact.toJson()));
                    }
                }
            }
        }

        /** Checks for old contacts on the client device and prompts the client to delete them */

        private void deleteOldContacts(ArrayList<Contact> phoneContacts) {
            Log.d(TAG, String.format("deleteOldContacts: " +
                    "Checking for old contacts on device: %s...", mDeviceTag));
            for (Map.Entry<Long, Long> clientContact : mClientContactHashes.entrySet()) {
                if (!stopSync) {
                    // Iterate over client contact id's and check for old contacts.
                    boolean deleteClientContact = true;
                    for (Contact phoneContact : phoneContacts) {
                        if (phoneContact.getPrimaryKey() == clientContact.getKey()) {
                            deleteClientContact = false;
                            break;
                        }
                    }

                    // If a contact from the client doesn't exist on the phone,
                    // tell the client to delete it.
                    if (deleteClientContact) {
                        Log.d(TAG, String.format("deleteOldContacts: " +
                                        "Telling client to delete entry for contact id: %s " +
                                        "from device: %s!",
                                clientContact.getKey(), mDeviceTag));

                        BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                                ("delete_contact: " + clientContact.getKey()));
                    }
                }
            }
        }

        /** Checks for outdated or missing client contact photos and sends updated
         *  contact photos. */

        private void sendContactPhotos(ArrayList<Contact> phoneContacts) {
            Log.d(TAG, String.format("sendContactPhotos: " +
                    "Checking for outdated or missing contact photos on device: %s...", mDeviceTag));
            if (!stopSync) {
                // Iterate over client contact photo hashes and check if any photos
                // are missing or outdated.
                for (Contact phoneContact : phoneContacts) {
                    boolean syncPhoto = true;
                    for (Map.Entry<Long, Long> clientContactPhoto :
                            mClientContactsPhotoHashes.entrySet()) {
                        long clientContactId = clientContactPhoto.getKey();
                        long clientContactPhotoHash = clientContactPhoto.getValue();
                        if (clientContactId == phoneContact.getPrimaryKey() &&
                                clientContactPhotoHash == phoneContact.getPhotoHash()) {
                            syncPhoto = false;
                        }
                    }

                    // If a photo is missing from the client, then sync it with the client.
                    if (syncPhoto && phoneContact.hasPhoto()) {
                        Log.d(TAG, String.format("sendContactPhotos: " +
                                        "Sending photo for contact: %s to device: %s!",
                                phoneContact.getName(), mDeviceTag));

                        BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                                String.format("incoming_contact_photo: %s | %s | %s",
                                        phoneContact.getPrimaryKey(),
                                        phoneContact.getPhotoHash(),
                                        phoneContact.getPhoto()));
                    }
                }
            }
        }
    }
}