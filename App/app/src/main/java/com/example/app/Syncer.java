package com.example.app;

import android.Manifest;
import android.bluetooth.BluetoothSocket;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.ContactsContract;

import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.google.common.base.Splitter;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Syncer class is a thread that syncs information with the client device in the background. Is
 * run by BluetoothConnectionThread upon syncing.
 */
public class Syncer extends Thread {
    // Constructor variables
    private final Context mContext;
    private final BluetoothSocket mBluetoothSocket;

    // Stores a simple readout of the client's contacts and contact photos that contains
    // key value pairs of the contact's ID and the hash of its information or photo.
    private HashMap<Integer, Integer> mClientContacts;
    private HashMap<Integer, Integer> mClientContactsPhotos;

    // Used to determine whether the thread should stop syncing.
    private boolean stopSync = false;

    public Syncer(Context mContext, BluetoothSocket mBluetoothSocket) {
        this.mContext = mContext;
        this.mBluetoothSocket = mBluetoothSocket;
    }

    @Override
    public void run() {
        // If contact permissions are given, sync contacts with the device.
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CONTACTS) ==
                PermissionChecker.PERMISSION_GRANTED) {
            syncContacts();
        }
    }

    private void syncContacts() {
        BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                "check_contacts_info_sync;".getBytes());
        ArrayList<Contact> serverContacts = getServerContacts();

        while (mClientContacts == null) {
            SystemClock.sleep(10);
            if (stopSync) {
                break;
            }
        }
        if (!stopSync) {
            sendContactsInfo(serverContacts);
        }

        BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                "check_contacts_photos_sync;".getBytes());
        while (mClientContactsPhotos == null) {
            SystemClock.sleep(10);
            if (stopSync) {
                break;
            }
        }

        if (!stopSync) {
            sendContactsPhotos(getServerContacts());
        }

        deleteOldContacts(serverContacts);
    }

    private ArrayList<Contact> getServerContacts() {
        ArrayList<Contact> serverContacts = new ArrayList<>();
        Cursor cursor = mContext.getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null);

        while (cursor.moveToNext() && !stopSync) {
            String name = cursor.getString(
                    cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            Contact currentContact = new Contact(name);

            long primaryKey = cursor.getLong(
                    cursor.getColumnIndex(ContactsContract.Contacts._ID));
            currentContact.setPrimaryKey(primaryKey);

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

            currentContact.generateHashID();
            serverContacts.add(currentContact);
        }
        cursor.close();

        return serverContacts;
    }

    private void sendContactsInfo(ArrayList<Contact> serverContacts) {
        for (Contact serverContact : serverContacts) {
            if (!stopSync) {
                boolean syncContact = true;
                for (Map.Entry<Integer, Integer> clientContact : mClientContacts.entrySet()) {
                    long clientContactId = Long.valueOf(clientContact.getKey());
                    int clientContactHash = clientContact.getValue();
                    if (clientContactId == serverContact.getPrimaryKey() &&
                            clientContactHash == serverContact.getHash()) {
                        syncContact = false;
                    }
                }

                if (syncContact) {
                    BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                            String.format("incoming_contact: %s;",
                                    serverContact.toJson()).getBytes());
                }
            }
        }
    }

    private void deleteOldContacts(ArrayList<Contact> serverContacts) {
        for (Map.Entry<Integer, Integer> clientContact : mClientContacts.entrySet()) {
            if (!stopSync) {
                boolean removeClientContact = true;
                for (Contact serverContact : serverContacts) {
                    if (serverContact.getPrimaryKey() == clientContact.getKey()) {
                        removeClientContact = false;
                    }
                }

                if (removeClientContact) {
                    BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                            ("delete_contact: " + clientContact.getKey() + ";").getBytes());
                }
            }
        }
    }

    private void sendContactsPhotos(ArrayList<Contact> serverContacts) {
        for (Contact serverContact : serverContacts) {
            boolean syncPhoto = true;
            for (Map.Entry<Integer, Integer> clientContactPhoto :
                    mClientContactsPhotos.entrySet()) {
                long clientContactId = Long.valueOf(clientContactPhoto.getKey());
                int clientContactPhotoHash = clientContactPhoto.getValue();
                if (clientContactId == serverContact.getPrimaryKey() &&
                        clientContactPhotoHash == serverContact.getPhotoHash()) {
                    syncPhoto = false;
                }
            }

            if (syncPhoto && serverContact.hasPhoto()) {
                BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                        ("incoming_contact_photo_part: "
                                + serverContact.getPrimaryKey()
                                + " | "
                                + "START "
                                + serverContact.getPhotoHash()
                                + ";").getBytes());
                SystemClock.sleep(100);
                for (String photoPart : serverContact.getPhoto()) {
                    BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                            ("incoming_contact_photo_part: "
                                    + serverContact.getPrimaryKey()
                                    + " | "
                                    + photoPart
                                    + ";").getBytes());
                    SystemClock.sleep(100);
                }
                BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                        ("incoming_contact_photo_part: "
                                + serverContact.getPrimaryKey()
                                + " | END;").getBytes());
            }
        }
    }

    private void syncMessages() {
    }

    private void syncCalls() {
    }

    public void setClientContacts(HashMap<Integer, Integer> mClientContacts) {
        this.mClientContacts = mClientContacts;
    }

    public void setClientContactsPhotos(HashMap<Integer, Integer> mClientContactsPhotos) {
        this.mClientContactsPhotos = mClientContactsPhotos;
    }

    @Override
    public void interrupt() {
        stopSync = true;
        super.interrupt();
    }

    private static class Contact {
        private transient Iterable<String> photo;
        private final HashMap<String, String> phones = new HashMap<>();
        private final String name;
        private final HashMap<String, String> emails = new HashMap<>();
        private boolean hasPhoto = false;
        private int hash;
        private transient int photoHash;
        private long primaryKey;

        public Contact(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void generateHashID() {
            StringBuilder stringToHash = new StringBuilder();

            stringToHash.append(name);

            for (Map.Entry<String, String> phone : phones.entrySet()) {
                stringToHash.append(phone.getKey()).append(phone.getValue());
            }

            for (Map.Entry<String, String> email : emails.entrySet()) {
                stringToHash.append(email.getKey()).append(email.getValue());
            }

            hash = stringToHash.toString().hashCode();
        }

        public void setPhoto(byte[] photoData) {
            String encodedPhoto = Base64.getEncoder().encodeToString(photoData);
            hasPhoto = true;
            photoHash = encodedPhoto.hashCode();
            this.photo = Splitter.fixedLength(768).split(encodedPhoto);
        }

        public Iterable<String> getPhoto() {
            return photo;
        }

        public int getPhotoHash() {
            return photoHash;
        }

        public boolean hasPhoto() {
            return hasPhoto;
        }

        public void addPhone(String phoneTypeName, String phoneNumber) {
            phones.put(phoneTypeName, phoneNumber);
        }

        public void addEmail(String emailTypeName, String email) {
            emails.put(emailTypeName, email);
        }

        public int getHash() {
            return hash;
        }

        public long getPrimaryKey() {
            return primaryKey;
        }

        public void setPrimaryKey(long primaryKey) {
            this.primaryKey = primaryKey;
        }

        public String toJson() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }
}