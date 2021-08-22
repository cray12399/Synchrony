import json
import os
import sqlite3

import utils


class Contacts:
    @staticmethod
    def send_contact_info_hashes(phone_dir, bluetooth_socket):
        """Check which contact infos are currently in the contacts database and send their hash to the server."""

        Contacts.create_contact_tables(phone_dir)

        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        cursor.execute("SELECT contact_id, hash FROM people")

        bluetooth_socket.send(f"have_contact_hashes: {json.dumps(cursor.fetchall())}" + utils.COMMAND_DELIMITER)

        cursor.close()

    @staticmethod
    def send_contact_photo_hashes(phone_dir, bluetooth_socket):
        """Check which contact photos are currently in the contacts database and send their hash to the server."""

        Contacts.create_contact_tables(phone_dir)

        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        cursor.execute("SELECT contact_id, photo_hash FROM contact_photos")

        bluetooth_socket.send(f"have_contact_photo_hashes: {json.dumps(cursor.fetchall())}" + utils.COMMAND_DELIMITER)

        cursor.close()

    @staticmethod
    def delete_contact_from_database(phone_dir, contact_id):
        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        cursor.execute("DELETE FROM people WHERE contact_id = ?", (contact_id,))
        cursor.execute("DELETE FROM contact_emails WHERE contact_id = ?", (contact_id,))
        cursor.execute("DELETE FROM contact_phones WHERE contact_id = ?", (contact_id,))
        cursor.execute("DELETE FROM contact_photos WHERE contact_id = ?", (contact_id,))

        connection.commit()
        cursor.close()

    @staticmethod
    def write_contact_to_database(phone_dir, contact):
        Contacts.create_contact_tables(phone_dir)

        contact = json.loads(contact)

        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        # If the contact already exists in the people table, insert it. Otherwise, update it.
        cursor.execute("SELECT * FROM people WHERE contact_id = ?", (contact['mPrimaryKey'],))
        if len(cursor.fetchall()) == 0:
            cursor.execute("INSERT INTO people VALUES (?, ?, ?)",
                           (contact['mName'], contact['mHash'], contact["mPrimaryKey"]))
        else:
            cursor.execute("UPDATE people SET name = ?, hash = ? WHERE contact_id = ?",
                           (contact['mName'], contact['mHash'], contact['mPrimaryKey']))

        # If the contact has emails in the emails table, insert them. Otherwise update them.
        cursor.execute("SELECT * FROM contact_emails WHERE contact_id = ?", (contact['mPrimaryKey'],))
        if len(cursor.fetchall()) == 0:
            for email_type, address in contact['mEmails'].items():
                cursor.execute("INSERT INTO contact_emails VALUES (?, ?, ?)",
                               (email_type, address, contact['mPrimaryKey']))
        else:
            Contacts.update_contact_correspondences(cursor, contact, 'contact_emails')

        # If the contact has phones in the phones table, insert them. Otherwise update them.
        cursor.execute("SELECT * FROM contact_phones WHERE contact_id = ?", (contact['mPrimaryKey'],))
        if len(cursor.fetchall()) == 0:
            for phone_type, number in contact['mPhones'].items():
                cursor.execute("INSERT INTO contact_phones VALUES (?, ?, ?)",
                               (phone_type, number, contact['mPrimaryKey']))
        else:
            cursor.execute("SELECT * FROM contact_phones WHERE contact_id = ?", (contact['mPrimaryKey'],))
            Contacts.update_contact_correspondences(cursor, contact, 'contact_phones')

        connection.commit()
        cursor.close()

    @staticmethod
    def write_contact_photo_to_database(phone_dir, contact_photo):
        """Writes a contact photo to the database. Either by inserting it or updating a current entry."""

        Contacts.create_contact_tables(phone_dir)

        connection = sqlite3.connect(f'{phone_dir}/data/contacts')
        cursor = connection.cursor()

        # If the photo does not exist in the photos table, insert it. Otherwise, update it.
        cursor.execute("SELECT * FROM contact_photos WHERE contact_id = ?", (contact_photo['contact_id'],))
        if len(cursor.fetchall()) == 0:
            cursor.execute("INSERT INTO contact_photos VALUES (?, ?, ?)",
                           (contact_photo['base64'], contact_photo['hash'], contact_photo['contact_id']))
        else:
            cursor.execute("UPDATE contact_photos SET base64 = ?, photo_hash = ? WHERE contact_id = ?",
                           (contact_photo['base64'], contact_photo['hash'], contact_photo['contact_id']))

        connection.commit()
        cursor.close()

    @staticmethod
    def update_contact_correspondences(cursor, contact, correspondence_table):
        """Updates the contact's correspondences, or contact methods.
        General use function since most of the code is identical"""

        # Get the currently synced correspondences for the contact.
        cursor.execute("SELECT * FROM {} WHERE contact_id = ?".format(correspondence_table), (contact['mPrimaryKey'],))
        client_correspondences = {corr[0]: corr[1] for corr in cursor.fetchall()}

        # Check if correspondences have changed. If so, update them.
        if client_correspondences != contact[correspondence_table.replace("contact_", "")]:
            cursor.execute("DELETE FROM {} WHERE contact_id = ?".format(correspondence_table),
                           (contact['mPrimaryKey'],))
            for correspondence_type, correspondence in contact[correspondence_table.replace("contact_", "")].items():
                cursor.execute("INSERT INTO {} VALUES (?, ?, ?)".format(correspondence_table),
                               (correspondence_type, correspondence, contact['mPrimaryKey']))

    @staticmethod
    def create_contact_tables(phone_dir):
        if not os.path.isdir(f"{phone_dir}/data/"):
            os.mkdir(f"{phone_dir}/data/")

        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        cursor.execute("""CREATE TABLE IF NOT EXISTS people(
                name TEXT,
                hash INTEGER,
                contact_id INTEGER
                )""")

        cursor.execute("""CREATE TABLE IF NOT EXISTS contact_emails(
                    type TEXT,
                    address TEXT,
                    contact_id INTEGER,
                    FOREIGN KEY(contact_id) REFERENCES people(contact_id)
                    )""")

        cursor.execute("""CREATE TABLE IF NOT EXISTS contact_phones(
                        type TEXT,
                        number TEXT,
                        contact_id INTEGER,
                        FOREIGN KEY(contact_id) REFERENCES people(contact_id)
                        )""")

        cursor.execute("""CREATE TABLE IF NOT EXISTS contact_photos(
                            base64 BLOB,
                            photo_hash INTEGER,
                            contact_id INTEGER,
                            FOREIGN KEY(contact_id) REFERENCES people(contact_id)
                            )""")

        connection.commit()
        cursor.close()


class Messages:
    @staticmethod
    def send_message_ids(phone_dir, bluetooth_socket):
        """Check which message ids are currently in the messages database and send their ids to the server."""

        Messages.create_messages_table(phone_dir)

        connection = sqlite3.connect(f"{phone_dir}/data/messages")
        cursor = connection.cursor()

        cursor.execute("SELECT id FROM sms")
        bluetooth_socket.send(
            f"have_message_ids: {json.dumps([i[0] for i in cursor.fetchall()])}" + utils.COMMAND_DELIMITER)

        cursor.close()

    @staticmethod
    def write_message_to_database(phone_dir, message):
        Messages.create_messages_table(phone_dir)

        message = json.loads(message)

        connection = sqlite3.connect(f"{phone_dir}/data/messages")
        cursor = connection.cursor()

        cursor.execute("INSERT INTO sms VALUES (?, ?, ?, ?, ?, ?, ?)",
                       (message['mId'], message['mThreadId'], message['mNumber'], message['mDateSent'],
                        message['mType'], message['mRead'], message['mBody']))

        connection.commit()
        cursor.close()

    @staticmethod
    def delete_message_from_database(phone_dir, message_id):
        connection = sqlite3.connect(f"{phone_dir}/data/messages")
        cursor = connection.cursor()

        cursor.execute("DELETE FROM sms WHERE id = ?", (message_id,))

        connection.commit()
        cursor.close()

    @staticmethod
    def create_messages_table(phone_dir):
        if not os.path.isdir(f"{phone_dir}/data/"):
            os.mkdir(f"{phone_dir}/data/")

        connection = sqlite3.connect(f"{phone_dir}/data/messages")
        cursor = connection.cursor()

        cursor.execute("""CREATE TABLE IF NOT EXISTS sms(
                            id INTEGER,
                            thread_id INTEGER,
                            number TEXT,
                            date_sent TEXT,
                            type TEXT,
                            read INTEGER,
                            body TEXT
                            )""")

        connection.commit()
        cursor.close()


class Calls:
    @staticmethod
    def write_call_to_database(phone_dir, call):
        Calls.create_calls_table(phone_dir)

        call = json.loads(call)

        connection = sqlite3.connect(f"{phone_dir}/data/calls")
        cursor = connection.cursor()

        cursor.execute("INSERT INTO calls VALUES (?, ?, ?, ?, ?)",
                       (call['mId'], call['mNumber'], call['mType'], call['mDate'], call['mDuration']))

        connection.commit()
        cursor.close()

    @staticmethod
    def send_call_ids(phone_dir, bluetooth_socket):
        """Check which call ids are currently in the calls database and send their ids to the server."""
        Calls.create_calls_table(phone_dir)

        Messages.create_messages_table(phone_dir)

        connection = sqlite3.connect(f"{phone_dir}/data/calls")
        cursor = connection.cursor()

        cursor.execute("SELECT id FROM calls")
        bluetooth_socket.send(
            f"have_call_ids: {json.dumps([i[0] for i in cursor.fetchall()])}" + utils.COMMAND_DELIMITER)

    @staticmethod
    def delete_call_from_database(phone_dir, call_id):
        connection = sqlite3.connect(f"{phone_dir}/data/calls")
        cursor = connection.cursor()

        cursor.execute("DELETE FROM calls WHERE id = ?", (call_id,))

        connection.commit()
        cursor.close()

    @staticmethod
    def create_calls_table(phone_dir):
        if not os.path.isdir(f"{phone_dir}/data/"):
            os.mkdir(f"{phone_dir}/data/")

        connection = sqlite3.connect(f"{phone_dir}/data/calls")
        cursor = connection.cursor()

        cursor.execute("""CREATE TABLE IF NOT EXISTS calls(
                                id INTEGER,
                                number TEXT,
                                type TEXT,
                                date TEXT,
                                duration TEXT
                                )""")

        connection.commit()
        cursor.close()


def notify_pc(notification):
    notification = json.loads(notification)

    if 'appName' in notification.keys():
        title = notification['appName']

        if notification['title'] is not None:
            title += ': ' + notification['title']
    elif notification['title'] is not None:
        title = notification['title']
    else:
        title = "No Title"

    text = "No Description" if notification['text'] is None else str(notification['text'])

    os.system(f"notify-send \"{title}\" \"{text}\"")
