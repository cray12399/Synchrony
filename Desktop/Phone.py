import syncer
import utils
import os
import pyclip
import threading
import bluetooth
import time
from datetime import datetime, timedelta
from configparser import ConfigParser


class Phone:
    """Class used objectify a device connection and sync data from a target device."""
    
    def __init__(self, name, address, service_matches):
        self.__name = name
        self.__address = address
        self.__service_matches = service_matches
        self.__directory = initialize_directory(name, address)

        # Timing variables used to keep track of connection status.
        self.__last_heartbeat = -1
        self.__connection_time = -1
        
        # Start sync thread which is responsible for doing the background activity for the target device.
        self.__sync_thread = threading.Thread(target=self.do_sync)
        self.__sync_thread.do_run = True
        self.__sync_thread.start()

        # Contact photo buffer used to hold contact photo data as it is being transmitted from the server device.
        self.__contact_photo_buffer = {}

    def do_sync(self):
        """Main function for the background sync thread. Manages the bluetooth socket connection
        and handles incoming server commands"""

        bluetooth_socket = self.try_connect()
        while getattr(threading.currentThread(), 'do_run', True):
            # If a connection has been established, handle commands and check timings.
            if bluetooth_socket is not None:
                self.handle_commands(bluetooth_socket)
                bluetooth_socket = self.check_socket_timing(bluetooth_socket)
            else:
                time.sleep(5)
                bluetooth_socket = self.try_connect()

    def try_connect(self):
        """Tries return a connected bluetooth socket. If a connection wasn't established, return None."""

        first_match = self.__service_matches[0]
        host = first_match["host"]

        socket = bluetooth.BluetoothSocket(bluetooth.RFCOMM)

        try:
            socket.connect((host, utils.SERVER_PORT))
            socket.settimeout(10)
            self.__connection_time = datetime.now()
            print("Connected to socket!")
        except bluetooth.btcommon.BluetoothError as e:
            print(e)
            return None

        return socket

    def handle_commands(self, bluetooth_socket):
        """Handles incoming commands from the device's server."""

        server_commands = []
        try:
            # Server commands are split into a list to prevent multiple commands from being stuck together
            # if subsequent commands come in before the thread can handle the first one.
            server_commands = bluetooth_socket.recv(1024).decode('utf-8').split(";")
        except bluetooth.btcommon.BluetoothError:
            pass

        for server_command in server_commands:
            # If a server heartbeat is received, acknowledge it with a client heartbeat.
            if server_command == "server_heartbeat":
                bluetooth_socket.send("client_heartbeat;")

            # If the device server requests the contact infos that are currently synced on the current device, return
            # a list of the hashes for each currently synced contact's info.
            elif server_command == "check_contacts_info_sync":
                threading.Thread(target=syncer.Contacts.check_contacts_info_sync,
                                 args=(self.__directory, bluetooth_socket)).start()

            # If the device server requests the contact photos that are currently synced on the current device, return
            # a list of the hashes for each currently synced contact's photo.
            elif server_command == "check_contacts_photos_sync":
                threading.Thread(target=syncer.Contacts.check_contacts_photos_sync,
                                 args=(self.__directory, bluetooth_socket)).start()

            # If the server sends the clipboard from the target device, copy it to current device.
            elif "incoming_clipboard:" in server_command:
                pyclip.copy(server_command.split(": ")[1])

            # If the server sends a contact, try to write it to the database of the current device.
            elif "incoming_contact:" in server_command:
                contact = server_command.split(": ")[1]
                threading.Thread(target=syncer.Contacts.write_contact_to_database,
                                 args=(self.__directory, contact)).start()

            # If the server sends part of a contact photo, write it to the contact photo buffer, or write it
            # to the database if it is complete.
            elif "incoming_contact_photo_part:" in server_command:
                self.handle_incoming_photo_part_command(server_command)

            # If the server tells the current device to delete a currently synced contact, delete it.
            elif "delete_contact:" in server_command:
                contact_id = int(server_command.replace("delete_contact: ", ""))
                threading.Thread(target=syncer.Contacts.remove_contact_from_database,
                                 args=(self.__directory, contact_id)).start()

            # If the server command isn't blank, update the heartbeat time, since
            # a command is equivalent to a heartbeat.
            if server_command != "":
                self.__last_heartbeat = datetime.now()

    def handle_incoming_photo_part_command(self, server_command):
        """Takes in server commands pertaining to the syncing of contact photos and either writes them
        to the contact photo buffer, or to the database in the case of completion"""

        extra_part = server_command.split(": ")[1]  # The part of the command that isn't the command itself.
        contact_id = extra_part.split(' | ')[0]  # The contact id the photo belongs to.
        data_part = extra_part.split(' | ')[1]  # The part of the command that contains the data for the photo.

        # If START is in the data part, create an entry in the contact photo buffer containing
        # the contact id and photo hash
        if 'START' in data_part:
            if contact_id not in self.__contact_photo_buffer.keys():
                self.__contact_photo_buffer[contact_id] = {}
                self.__contact_photo_buffer[contact_id]['base64'] = []
                self.__contact_photo_buffer[contact_id]['contact_id'] = contact_id
                self.__contact_photo_buffer[contact_id]['hash'] = int(data_part.replace("START ", ""))

        # If a new piece of data came in for the contact photo, append it to the appropriate entry in the photo
        # buffer.
        elif data_part != 'END':
            self.__contact_photo_buffer[contact_id]['base64'].append(data_part)

        # If the END of the photo data is reached, combine the data in the photo buffer into one large
        # byte variable and write it to the database.
        else:
            photo_64_bytes = bytes(''.join(self.__contact_photo_buffer[contact_id]['base64']), 'utf-8')
            self.__contact_photo_buffer[contact_id]['base64'] = photo_64_bytes

            threading.Thread(target=syncer.Contacts.write_contact_photo_to_database,
                             args=(self.__directory, self.__contact_photo_buffer[contact_id])).start()

            # Pop the entry in the photo buffer so it isn't just hanging around in memory when it's finished.
            self.__contact_photo_buffer.pop(contact_id)

    def sync_thread_alive(self):
        return self.__sync_thread.isAlive()

    def stop_thread(self):
        self.__sync_thread.do_run = False

    def check_socket_timing(self, bluetooth_socket):
        """Checks the timing for the bluetooth socket connection and handles the connection accordingly."""

        # If the timing since the last heartbeat from the server exceeds the constant
        # time defined in utils, mark the socket as disconnected
        socket_disconnected = False
        if self.__last_heartbeat != -1 and isinstance(self.__last_heartbeat, datetime):
            socket_disconnected = datetime.now() - self.__last_heartbeat > timedelta(seconds=utils.SOCKET_TIMEOUT)
        else:
            if isinstance(self.__connection_time, datetime):
                socket_disconnected = datetime.now() - self.__connection_time > timedelta(seconds=utils.SOCKET_TIMEOUT)

        # If the socket is marked as disconnected, close it, reset the timings, and set the socket to None.
        if socket_disconnected:
            bluetooth_socket.close()
            self.__connection_time = -1
            self.__last_heartbeat = -1
            bluetooth_socket = None

        return bluetooth_socket


def initialize_directory(name, address):
    """Initializes the phone's data directory."""

    phone_directory = f'{utils.BASE_DIR}Phones/{name} ({address})'

    if not os.path.isdir(phone_directory):
        os.mkdir(phone_directory)

    if not os.path.isfile(f'{phone_directory}/config.ini'):
        create_phone_ini(phone_directory)

    return phone_directory


def create_phone_ini(phone_directory):
    """Creates the ini file responsible for holding the phone's configuration data."""

    config = ConfigParser()
    config['vars'] = {'initial_connection': 'True'}

    with open(f'{phone_directory}/config.ini', 'w') as config_file:
        config.write(config_file)