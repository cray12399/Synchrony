import json
import logging

import syncing
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

    def __init__(self, name, address, service_matches, logger):
        self.__name = name
        self.__address = address
        self.__service_matches = service_matches
        self.__directory = initialize_directory(name, address)
        self.__logger = logger

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

        self.__logger.info(f"Trying to connect to device: {self.__name} ({self.__address})...")

        bluetooth_socket = self.try_connect()
        while getattr(threading.currentThread(), 'do_run', True):
            # If a connection has been established, handle commands and check timings.
            if bluetooth_socket is not None:
                self.handle_commands(bluetooth_socket)
                bluetooth_socket = self.check_socket_timing(bluetooth_socket)
            else:
                time.sleep(5)
                bluetooth_socket = self.try_connect()

        if bluetooth_socket is not None:
            bluetooth_socket.close()

    def try_connect(self):
        """Tries return a connected bluetooth socket. If a connection wasn't established, return None."""

        first_match = self.__service_matches[0]
        host = first_match["host"]

        socket = bluetooth.BluetoothSocket(bluetooth.RFCOMM)

        try:
            socket.connect((host, utils.SERVER_PORT))
            socket.settimeout(10)
            self.__connection_time = datetime.now()

            self.__logger.info(f"Connected to device: {self.__name} ({self.__address})!")
        except bluetooth.btcommon.BluetoothError as e:
            if e.errno != 111:
                self.__logger.exception(f"Error connecting to device: {self.__name} ({self.__address})! \n {e}")

            return None

        return socket

    def handle_commands(self, bluetooth_socket):
        """Handles incoming commands from the device's server."""

        try:
            # Receive commands from device server. Input stream buffer size is 8192 to ensure that it does
            # not get completely filled by fast incoming commands.
            server_commands = bluetooth_socket.recv(8192).decode('utf-8')

            # To ensure that no commands are broken, wait until the end of the input stream
            # buffer is the command delimiter
            while server_commands[-len(utils.COMMAND_DELIMITER):] != utils.COMMAND_DELIMITER:
                server_commands += bluetooth_socket.recv(8192).decode('utf-8')

            # Once a complete stream of commands is obtained, split it by the command delimiter
            # and iterate over commands
            server_commands = server_commands.split(utils.COMMAND_DELIMITER)

            for server_command in server_commands:
                # If a server heartbeat is received, acknowledge it with a client heartbeat.
                if server_command == 'server_heartbeat':
                    bluetooth_socket.send("client_heartbeat" + utils.COMMAND_DELIMITER)

                # If the device server requests the contact infos that are currently synced on the current device,
                # return a list of the hashes for each currently synced contact's info.
                elif server_command == 'check_contact_info_hashes':
                    self.__logger.info(f"Sending contact info hashes to device: {self.__name} ({self.__address})...")
                    syncing.Contacts.send_contact_info_hashes(self.__directory, bluetooth_socket, self.__logger)

                # If the device server requests the contact photos that are currently synced on the current device,
                # return a list of the hashes for each currently synced contact's photo.
                elif server_command == 'check_contact_photo_hashes':
                    self.__logger.info(f"Sending contact photo hashes to device: {self.__name} ({self.__address})...")
                    syncing.Contacts.send_contact_photo_hashes(self.__directory, bluetooth_socket, self.__logger)

                elif server_command == 'check_message_ids':
                    self.__logger.info(f"Sending message ids to device: {self.__name} ({self.__address})...")
                    # threading.Thread(target=syncer.Messages.send_message_ids,
                    #                  args=(self.__directory, bluetooth_socket, self.__logger)).start()
                    syncing.Messages.send_message_ids(self.__directory, bluetooth_socket, self.__logger)

                # If the server sends the clipboard from the target device, copy it to current device.
                elif 'incoming_clipboard:' in server_command:
                    pyclip.copy(server_command.replace("incoming_clipboard: ", ""))
                    self.__logger.info(f"Copied clipboard from device: {self.__name} ({self.__address})!")

                # If the server sends a contact, try to write it to the database of the current device.
                elif 'incoming_contact:' in server_command:
                    contact = server_command.replace("incoming_contact: ", "")

                    self.__logger.info(f"Writing contact: {json.loads(contact)['mName']} from device: "
                                       f"{self.__name} ({self.__address}) to contacts...")
                    syncing.Contacts.write_contact_to_database(self.__directory, contact, self.__logger)

                # If the server sends part of a contact photo, write it to the contact photo buffer, or write it
                # to the database if it is complete.
                elif 'incoming_contact_photo_part:' in server_command:
                    self.handle_incoming_photo_part_command(server_command)

                elif 'incoming_message:' in server_command:
                    message = server_command.replace("incoming_message: ", "")
                    syncing.Messages.write_message_to_database(self.__directory, message, self.__logger)

                # If the server tells the current device to delete a currently synced contact, delete it.
                elif 'delete_contact:' in server_command:
                    contact_id = int(server_command.replace("delete_contact: ", ""))
                    self.__logger.info(f"Deleting entries for contact id: {contact_id} from contacts...")
                    syncing.Contacts.remove_contact_from_database(self.__directory, contact_id, self.__logger)

                # If the server command isn't blank, update the heartbeat time, since
                # a command is equivalent to a heartbeat.
                if server_command != "":
                    self.__logger.debug(f"Command received from device: {self.__name} ({self.__address}):"
                                        f" {server_command}!")
                    self.__last_heartbeat = datetime.now()
        except bluetooth.btcommon.BluetoothError as e:
            self.__logger.exception("Error receiving incoming command from server!")

    def handle_incoming_photo_part_command(self, server_command):
        """Takes in server commands pertaining to the syncing of contact photos and either writes them
        to the contact photo buffer, or to the database in the case of completion"""

        # The part of the command that isn't the command itself.
        extra_part = server_command.replace("incoming_contact_photo_part: ", "")
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
            logging.debug(f"Wrote part of contact photo for contact id: {contact_id} from device:"
                          f" {self.__name} ({self.__address}) to photo buffer!")

        # If the END of the photo data is reached, combine the data in the photo buffer into one large
        # byte variable and write it to the database.
        else:
            photo_64_bytes = bytes(''.join(self.__contact_photo_buffer[contact_id]['base64']), 'utf-8')
            self.__contact_photo_buffer[contact_id]['base64'] = photo_64_bytes

            logging.info(f"Writing contact photo for contact id: {contact_id} from device:"
                         f" {self.__name} ({self.__address}) to contacts...")
            threading.Thread(target=syncing.Contacts.write_contact_photo_to_database,
                             args=(self.__directory, self.__contact_photo_buffer[contact_id], self.__logger)).start()

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
            logging.info(f"Bluetooth socket timed out for device: {self.__name} ({self.__address})!")

            bluetooth_socket.close()
            self.__connection_time = -1
            self.__last_heartbeat = -1
            bluetooth_socket = None

            logging.info(f"Bluetooth socket disconnected for device: {self.__name} ({self.__address})!")

        return bluetooth_socket

    def get_name(self):
        return self.__name

    def get_address(self):
        return self.__address


def initialize_directory(name, address):
    """Initializes the phone's data directory."""

    phone_directory = f'Phones/{name} ({address})'

    if not os.path.isdir(phone_directory):
        os.mkdir(phone_directory)
        logging.debug(f"Directory created for device: {name} ({address})!")

    if not os.path.isfile(f'{phone_directory}/config.ini'):
        create_phone_ini(phone_directory)
        logging.debug(f"Config file created for device: {name} ({address})!")

    return phone_directory


def create_phone_ini(phone_directory):
    """Creates the ini file responsible for holding the phone's configuration data."""

    config = ConfigParser()
    config['vars'] = {'initial_connection': 'True'}

    with open(f'{phone_directory}/config.ini', 'w') as config_file:
        config.write(config_file)
