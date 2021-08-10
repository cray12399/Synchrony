import sqlite3
import os
import json


class Contacts:
    @staticmethod
    def check_contacts_info_sync(phone_dir, bluetooth_socket):
        create_tables(phone_dir)

        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        cursor.execute("SELECT contact_id, hash FROM people")

        bluetooth_socket.send(f"have_contacts: {json.dumps(cursor.fetchall())}")

        cursor.close()

    @staticmethod
    def check_contacts_photos_sync(phone_dir, bluetooth_socket):
        create_tables(phone_dir)

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
        create_tables(phone_dir)

        contact = json.loads(contact)

        connection = sqlite3.connect(f"{phone_dir}/data/contacts")
        cursor = connection.cursor()

        cursor.execute("SELECT * FROM people WHERE contact_id = ?", (contact['primaryKey'], ))
        if len(cursor.fetchall()) == 0:
            cursor.execute("INSERT INTO people VALUES (?, ?, ?)",
                           (contact['name'], contact['hash'], contact["primaryKey"]))
        else:
            cursor.execute("UPDATE people SET name = ?, hash = ? WHERE contact_id = ?",
                           (contact['name'], contact['hash'], contact['primaryKey']))

        cursor.execute("SELECT * FROM contact_emails WHERE contact_id = ?", (contact['primaryKey'], ))
        if len(cursor.fetchall()) == 0:
            for email_type, address in contact['emails'].items():
                cursor.execute("INSERT INTO contact_emails VALUES (?, ?, ?)",
                               (email_type, address, contact['primaryKey']))
        else:
            update_contact_correspondences(cursor, contact, 'contact_' + 'emails')

        cursor.execute("SELECT * FROM contact_phones WHERE contact_id = ?", (contact['primaryKey'], ))
        if len(cursor.fetchall()) == 0:
            for phone_type, number in contact['phones'].items():
                cursor.execute("INSERT INTO contact_phones VALUES (?, ?, ?)",
                               (phone_type, number, contact['primaryKey']))
        else:
            cursor.execute("SELECT * FROM contact_phones WHERE contact_id = ?", (contact['primaryKey'], ))
            update_contact_correspondences(cursor, contact, 'contact_' + 'phones')

        connection.commit()
        cursor.close()

    @staticmethod
    def write_contact_photo_to_database(phone_dir, contact_photo):
        connection = sqlite3.connect(f'{phone_dir}/data/contacts')
        cursor = connection.cursor()

        cursor.execute("SELECT * FROM contact_photos WHERE contact_id = ?", (contact_photo['contact_id'],))
        if len(cursor.fetchall()) == 0:
            cursor.execute("INSERT INTO contact_photos VALUES (?, ?, ?)",
                           (contact_photo['base64'], contact_photo['hash'], contact_photo['contact_id']))
        else:
            cursor.execute("UPDATE contact_photos SET base64 = ?, photo_hash = ? WHERE contact_id = ?",
                           (contact_photo['base64'], contact_photo['hash'], contact_photo['contact_id']))

        connection.commit()
        cursor.close()


def update_contact_correspondences(cursor, contact, correspondence_table):
    cursor.execute("SELECT * FROM ? WHERE contact_id = ?", (correspondence_table, contact['primaryKey']))
    client_correspondences = {correspondences[0]: correspondences[1] for correspondences in cursor.fetchall()}

    if correspondence_table == 'phones':
        correspondence_identifier_type = 'number'
    else:
        correspondence_identifier_type = 'address'

    for correspondence_type, correspondence in client_correspondences.items():
        if (correspondence_type, correspondence) not in contact[correspondence_table].items():
            cursor.execute("DELETE FROM ? WHERE type = ? AND ? = ? AND contact_id = ?",
                           (correspondence_table, correspondence_type, correspondence_identifier_type,
                            correspondence, contact['primaryKey']))
    for correspondence_type, correspondence in contact[correspondence_table].items():
        if (correspondence_type, correspondence) not in client_correspondences.items():
            cursor.execute("INSERT INTO ? VALUES (?, ?, ?)",
                           (correspondence_table, correspondence_type, correspondence, contact['primaryKey']))


def create_tables(phone_dir):
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
