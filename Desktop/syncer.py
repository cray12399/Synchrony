import sqlite3
import os
import json


class Contacts:
    @staticmethod
    def check_contacts_info_sync(phone_dir, bluetooth_socket):
        """Check which contact infos are currently synced on the current device and send their hash to the server."""

        Contacts.create_contact_tables(phone_dir)

        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        cursor.execute("SELECT contact_id, hash FROM people")

        bluetooth_socket.send(f"have_contacts: {json.dumps(cursor.fetchall())}")

        cursor.close()

    @staticmethod
    def check_contacts_photos_sync(phone_dir, bluetooth_socket):
        """Check which contact photos are currently synced on the current device and send their hash to the server."""

        Contacts.create_contact_tables(phone_dir)

        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        cursor.execute("SELECT contact_id, photo_hash FROM contact_photos")

        bluetooth_socket.send(f"have_photos: {json.dumps(cursor.fetchall())}")

        cursor.close()

    @staticmethod
    def remove_contact_from_database(phone_dir, contact_id):
        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        cursor.execute("DELETE FROM people WHERE contact_id = ?", (contact_id, ))
        cursor.execute("DELETE FROM contact_emails WHERE contact_id = ?", (contact_id, ))
        cursor.execute("DELETE FROM contact_phones WHERE contact_id = ?", (contact_id, ))
        cursor.execute("DELETE FROM contact_photos WHERE contact_id = ?", (contact_id, ))

        connection.commit()
        cursor.close()

    @staticmethod
    def write_contact_to_database(phone_dir, contact):
        """Write contact to contacts. Either by inserting a new entry or updating a current entry."""

        Contacts.create_contact_tables(phone_dir)

        contact = json.loads(contact)

        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        # If the contact already exists in the people table, insert it. Otherwise, update it.
        cursor.execute("SELECT * FROM people WHERE contact_id = ?", (contact['primaryKey'], ))
        if len(cursor.fetchall()) == 0:
            cursor.execute("INSERT INTO people VALUES (?, ?, ?)",
                           (contact['name'], contact['hash'], contact["primaryKey"]))
        else:
            cursor.execute("UPDATE people SET name = ?, hash = ? WHERE contact_id = ?",
                           (contact['name'], contact['hash'], contact['primaryKey']))

        # If the contact has emails in the emails table, insert them. Otherwise update them.
        cursor.execute("SELECT * FROM contact_emails WHERE contact_id = ?", (contact['primaryKey'], ))
        if len(cursor.fetchall()) == 0:
            for email_type, address in contact['emails'].items():
                cursor.execute("INSERT INTO contact_emails VALUES (?, ?, ?)",
                               (email_type, address, contact['primaryKey']))
        else:
            Contacts.update_contact_correspondences(cursor, contact, 'contact_emails')

        # If the contact has phones in the phones table, insert them. Otherwise update them.
        cursor.execute("SELECT * FROM contact_phones WHERE contact_id = ?", (contact['primaryKey'], ))
        if len(cursor.fetchall()) == 0:
            for phone_type, number in contact['phones'].items():
                cursor.execute("INSERT INTO contact_phones VALUES (?, ?, ?)",
                               (phone_type, number, contact['primaryKey']))
        else:
            cursor.execute("SELECT * FROM contact_phones WHERE contact_id = ?", (contact['primaryKey'], ))
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
        cursor.execute("SELECT * FROM ? WHERE contact_id = ?", (correspondence_table, contact['primaryKey']))
        client_correspondences = {correspondences[0]: correspondences[1] for correspondences in cursor.fetchall()}

        # Update the terminology based on the given table.
        if correspondence_table == 'contact_phones':
            correspondence_identifier_type = 'number'
        else:
            correspondence_identifier_type = 'address'

        # Delete all current correspondences for the contact.
        for correspondence_type, correspondence in client_correspondences.items():
            if (correspondence_type, correspondence) not in contact[correspondence_table].items():
                cursor.execute("DELETE FROM ? WHERE type = ? AND ? = ? AND contact_id = ?",
                               (correspondence_table, correspondence_type, correspondence_identifier_type,
                                correspondence, contact['primaryKey']))

        # Write new correspondences.
        for correspondence_type, correspondence in contact[correspondence_table].items():
            if (correspondence_type, correspondence) not in client_correspondences.items():
                cursor.execute("INSERT INTO ? VALUES (?, ?, ?)",
                               (correspondence_table, correspondence_type, correspondence, contact['primaryKey']))

    @staticmethod
    def create_contact_tables(phone_dir):
        """Creates the contact database and fill it with tables."""

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

        cursor.close()
