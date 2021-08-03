package com.example.app;

import android.Manifest;
import android.bluetooth.BluetoothSocket;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.ContactsContract;

import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.google.common.base.Splitter;
import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class Syncer {
    private static String sSyncDone = "sync_done";
    private Context mContext;
    private BluetoothSocket mBluetoothSocket;

    public Syncer(Context mContext, BluetoothSocket mBluetoothSocket) {
        this.mContext = mContext;
        this.mBluetoothSocket = mBluetoothSocket;
    }

    public void doSync() {
        Thread syncerThread = new Thread(() -> {
            syncContacts();
            syncMessages();
            syncCalls();
        });
        syncerThread.start();
    }

    private void syncContacts() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_CONTACTS) ==
                PermissionChecker.PERMISSION_GRANTED) {
            ArrayList<Contact> contacts = new ArrayList<>();
            Cursor cursor = mContext.getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI,
                    null, null, null, null);
            while (cursor.moveToNext()) {
                String name = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                Contact currentContact = new Contact(name);

                String id = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.Contacts.NAME_RAW_CONTACT_ID));

                Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
                        cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts._ID)));
                currentContact.setUri(contactUri);

                Cursor photoCursor = mContext.getContentResolver().query(
                        Uri.withAppendedPath(contactUri,
                                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY),
                        new String[] {ContactsContract.Contacts.Photo.PHOTO},
                        null, null, null);
                if (photoCursor != null) {
                    if (photoCursor.moveToFirst()) {
                        byte[] photoData = photoCursor.getBlob(0);
                        if (photoData != null) {
                            currentContact.setPhoto(photoData);
                        }
                    }
                }

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
                }
                contacts.add(currentContact);
            }
            cursor.close();

            final long syncTimeout = 10000;
            for (Contact contact : contacts) {
                BluetoothConnectionThread.sendCommand(mBluetoothSocket,
                        String.format("incoming_contact: %s", contact.toJson()).getBytes());
                long sentSyncTime = System.currentTimeMillis();

                do {
                    String syncDoneCommand = BluetoothConnectionThread
                            .receiveCommand(mBluetoothSocket);
                    if (syncDoneCommand != null) {
                        if (syncDoneCommand.equals(sSyncDone)) {
                            break;
                        }
                    }

                    if (System.currentTimeMillis() - sentSyncTime > syncTimeout) {
                        break;
                    }
                } while (true);
                updateHeartbeat();
            }
        }
    }

    private void updateHeartbeat() {
        Intent updateHeartbeatIntent = new Intent();
        updateHeartbeatIntent.setAction(BluetoothConnectionThread.UPDATE_HEARTBEAT_ACTION);
        updateHeartbeatIntent.putExtra(Utils.RECIPIENT_ADDRESS_KEY,
                mBluetoothSocket.getRemoteDevice().getAddress());
        mContext.getApplicationContext().sendBroadcast(updateHeartbeatIntent);
    }

    private void syncMessages() {}

    private void syncCalls() {}

    private static class Contact {
        private transient Iterable<String> photo;
        private String uri;
        private final String name;
        private final HashMap<String, String> numbers = new HashMap<>();
        private final HashMap<String, String> emails = new HashMap<>();

        public Contact(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setUri(Uri uri) {
            this.uri = uri.toString();
        }

        public void setPhoto(byte[] photoData) {
            String encodedPhoto = Base64.getEncoder().encodeToString(photoData);
            this.photo = Splitter.fixedLength(1024).split(encodedPhoto);
        }

        public Iterable<String> getPhoto() {
            return photo;
        }

        public void addPhone(String phoneTypeName, String phoneNumber) {
            numbers.put(phoneTypeName, phoneNumber);
        }

        public void addEmail(String emailTypeName, String email) {
            emails.put(emailTypeName, email);
        }

        public String toJson () {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }
}