package com.example.app;

import android.Manifest;
import android.bluetooth.BluetoothSocket;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.google.common.base.Splitter;
import com.google.gson.Gson;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Syncer class is a thread that syncs information with the client device in the background. Is
 * run by BluetoothConnectionThread upon syncing.
 */
public class Syncer extends Thread {
    // Logging tag variables.
    private static final String TAG = "Syncer";
    private final String mDeviceTag;

    // Action and key variables.
    public final static String SYNC_ACTIVITY_CHANGE_ACTION = "syncActivityChangeAction";

    // Constructor variables
    private final Context mContext;
    private final BluetoothSocket mBluetoothSocket;

    // Stores a simple readout of the client's contacts and contact photos that contains
    // key value pairs of the contact's ID and the hash of its information or photo.
    private HashMap<Long, Long> mClientContactHashes;
    private HashMap<Long, Long> mClientContactsPhotoHashes;

    // Stores a simple readout of the ids of the messages on the client device.
    private ArrayList<Long> mClientMessageIDs;

    // Used to determine whether the thread should stop syncing.
    private boolean stopSync = false;

    public Syncer(Context mContext, BluetoothSocket mBluetoothSocket) {
        this.mContext = mContext;
        this.mBluetoothSocket = mBluetoothSocket;

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
            new ContactsSync().syncContacts();
        }

        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_SMS) ==
                PermissionChecker.PERMISSION_GRANTED) {
            new MessagesSync().syncMessages();
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
                if (!Objects.requireNonNull(Utils.getPairedPC(pcAddress)).isSocketConnected()) {
                    stopSync = true;
                }

                if (stopSync) {
                    break;
                }
            }

            // Once client contact hashes are received send any updated contact info to client and
            // delete any old contacts on the client device.
            if (!stopSync) {
                sendContactsInfo(getPhoneContacts());
                deleteOldContacts(getPhoneContacts());
            }

            BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                    "check_contact_photo_hashes");
            while (mClientContactsPhotoHashes == null) {
                String pcAddress = mBluetoothSocket.getRemoteDevice().getAddress();
                if (!Objects.requireNonNull(Utils.getPairedPC(pcAddress)).isSocketConnected()) {
                    stopSync = true;
                }

                if (stopSync) {
                    break;
                }
            }

            if (!stopSync) {
                sendContactPhotos(getPhoneContacts());
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
                    boolean removeClientContact = true;
                    for (Contact phoneContact : phoneContacts) {
                        if (phoneContact.getPrimaryKey() == clientContact.getKey()) {
                            removeClientContact = false;
                        }
                    }

                    // If a contact from the client doesn't exist on the phone,
                    // tell the client to delete it.
                    if (removeClientContact) {
                        Log.d(TAG, String.format("deleteOldContacts: " +
                                        "Telling client to delete entry for contact id: %s " +
                                        "from device: %s!",
                                clientContact.getKey(), mDeviceTag));

                        BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                                ("delete_contact: " + clientContact.getKey() + ""));
                    }
                }
            }
        }

        /** Checks for outdated or missing client contact photos and sends updated contact photos. */

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

                    // If a photo is missing or outdated, begin sending parts of it to the client.
                    if (syncPhoto && phoneContact.hasPhoto()) {
                        Log.d(TAG, String.format("sendContactPhotos: " +
                                        "Sending photo for contact: %s to device: %s!",
                                phoneContact.getName(), mDeviceTag));

                        // Start by sending client a start command that contains the hash of the photo.
                        BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                                ("incoming_contact_photo_part: "
                                        + phoneContact.getPrimaryKey()
                                        + " | "
                                        + "START "
                                        + phoneContact.getPhotoHash()));
                        SystemClock.sleep(100);
                        // Then, iterate over the photo's parts and send them to the client.
                        for (String photoPart : phoneContact.getmPhoto()) {
                            BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                                    ("incoming_contact_photo_part: "
                                            + phoneContact.getPrimaryKey()
                                            + " | "
                                            + photoPart));
                        }
                        // Finally, send the client an end command so it knows when
                        // to write the photo to its database.
                        BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                                ("incoming_contact_photo_part: "
                                        + phoneContact.getPrimaryKey()
                                        + " | END"));
                    }
                }
            }
        }
    }

    private class MessagesSync {
        private void syncMessages() {
            BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                    "check_message_ids");

            // Wait for message ids from client device.
            while (mClientMessageIDs == null) {
                String pcAddress = mBluetoothSocket.getRemoteDevice().getAddress();
                if (!Objects.requireNonNull(Utils.getPairedPC(pcAddress)).isSocketConnected()) {
                    stopSync = true;
                }

                if (stopSync) {
                    break;
                }
            }

            sendMessages();
        }

        private ArrayList<Message> getMessages() {
            Cursor cursor = mContext.getContentResolver().query(Uri.parse("content://sms/inbox"),
                    null, null, null, null);

            ArrayList<Message> phoneMessages = new ArrayList<>();
            if (cursor.moveToFirst()) {
                do {
                    Message message = new Message();
                    for(int id = 0; id < cursor.getColumnCount(); id++) {

                        switch (cursor.getColumnName(id)) {
                            case "_id": {
                                message.setId(Integer.parseInt(cursor.getString(id)));
                                break;
                            }

                            case "body": {
                                message.setBody(cursor.getString(id));
                                break;
                            }

                            case "thread_id": {
                                message.setThreadId(Integer.parseInt(cursor.getString(id)));
                                break;
                            }

                            case "address": {
                                message.setNumber(cursor.getString(id));
                                break;
                            }

                            case "date_sent": {
                                DateFormat formatter = SimpleDateFormat.getDateTimeInstance(
                                        DateFormat.SHORT, DateFormat.SHORT);
                                Calendar calendar = Calendar.getInstance();
                                calendar.setTimeInMillis(Long.parseLong(cursor.getString(id)));
                                message.setDateSent(formatter.format(calendar.getTime()));
                                break;
                            }

                            case "type": {
                                int type = Integer.parseInt(cursor.getString(id));
                                switch (type) {
                                    case Telephony.Sms.MESSAGE_TYPE_INBOX:
                                        message.setType("receivedMessage");
                                        break;
                                    case Telephony.Sms.MESSAGE_TYPE_SENT:
                                        message.setType("sentMessage");
                                        break;
                                    case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                                        message.setType("outboxMessage");
                                        break;
                                    default:
                                        break;
                                }
                                break;
                            }

                            case "read": {
                                message.setRead(Integer.parseInt(cursor.getString(id)));
                                break;
                            }
                        }
                    }
                    phoneMessages.add(message);
                } while (cursor.moveToNext());
            }

            cursor.close();

            return phoneMessages;
        }

        private void sendMessages() {
            // If a message's id isn't present in the client's message id's, send it to the client.
            for (Message phoneMessage : getMessages()) {
                if (!mClientMessageIDs.contains(phoneMessage.getId())) {
                    BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                            String.format("incoming_message: %s",
                                    phoneMessage.toJson()));
                }
            }
        }
    }

    private class TelephonySync {}

    private static class Message {
        private long mId;
        private int mThreadId;
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

        public void setId(int mId) {
            this.mId = mId;
        }

        public int getThreadId() {
            return mThreadId;
        }

        public void setThreadId(int mThreadId) {
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

    private static class Contact {
        private final String mName;
        private transient Iterable<String> mPhoto;
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

        public void setPhoto(byte[] photoData) {
            String encodedPhoto = Base64.getEncoder().encodeToString(photoData);
            mHasPhoto = true;
            mPhotoHash = encodedPhoto.hashCode();
            this.mPhoto = Splitter.fixedLength(768).split(encodedPhoto);
        }

        public Iterable<String> getmPhoto() {
            return mPhoto;
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
}