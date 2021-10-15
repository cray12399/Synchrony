import syncing
import utils

import json
import logging
import os
import threading
import time
from configparser import ConfigParser
import bluetooth
import pyclip


class Phone:
    """Class used objectify a device connection and sync data from a target device."""

    def __init__(self, name, address, service_matches, logger):
        self.__name = name
        self.__address = address
        self.__service_matches = service_matches
        self.__directory = initialize_directory(name, address)
        self.__logger = logger
        self.__bluetooth_socket = None

        # Start sync thread which is responsible for doing the background activity for the target device.
        self.__sync_thread = threading.Thread(target=self.__manage_connected_socket)
        self.__sync_thread.do_run = True
        self.__sync_thread.start()

        # Contact photo buffer used to hold contact photo data as it is being transmitted from the server device.
        self.__contact_photo_buffer = {}

    def __manage_connected_socket(self):
        """Manages the bluetooth sync socket connection and handles incoming server commands"""

        self.__logger.info(f"Trying to connect to device: {self.__name} ({self.__address})...")

        self.__bluetooth_socket = self.__connect_socket()
        while getattr(threading.currentThread(), 'do_run', True):
            if self.__bluetooth_socket is not None:
                self.__listen_for_bluetooth_command()
                time.sleep(.5)
            else:
                time.sleep(5)
                self.__bluetooth_socket = self.__connect_socket()

        if self.__bluetooth_socket is not None:
            self.__bluetooth_socket.close()

    def __connect_socket(self):
        """Tries return a connected bluetooth sync socket. If a connection wasn't established, returns None."""

        try:
            bluetooth_sync_socket = bluetooth.BluetoothSocket(bluetooth.RFCOMM)

            first_match = self.__service_matches[0]
            host = first_match["host"]

            bluetooth_sync_socket.connect((host, utils.SERVER_PORT))
            bluetooth_sync_socket.settimeout(10)

            self.__logger.info(f"Connected to device's sync socket: {self.__name} ({self.__address})!")
        except bluetooth.btcommon.BluetoothError as e:
            if e.errno != 111:
                self.__logger.exception(
                    f"Error connecting to device's sync socket: {self.__name} ({self.__address})! \n {e}")

            return None

        return bluetooth_sync_socket

    def __listen_for_bluetooth_command(self):
        try:
            server_commands = self.__bluetooth_socket.recv(4092).decode('utf-8')

            # To ensure that no commands are broken, wait until the end of the input stream
            # is the command delimiter
            while server_commands[-len(utils.COMMAND_DELIMITER):] != utils.COMMAND_DELIMITER:
                server_commands += self.__bluetooth_socket.recv(4092).decode('utf-8')

            # Once a complete stream of commands is obtained, split it by the command delimiter
            # and iterate over commands
            server_commands = server_commands.split(utils.COMMAND_DELIMITER)
            self.__handle_commands(server_commands)

        except bluetooth.btcommon.BluetoothError:
            self.__logger.exception("Error receiving incoming command from server!")
            self.__bluetooth_socket.close()
            self.__bluetooth_socket = None

    def __handle_commands(self, server_commands):
        for server_command in server_commands:
            # If the server command isn't blank, update the heartbeat time, since
            # a command is equivalent to a heartbeat.
            if server_command != "":
                self.__logger.debug(f"Command received from device: {self.__name} ({self.__address}):"
                                    f" {server_command.split(':')[0]}!")

            # If a server heartbeat is received, acknowledge it with a client heartbeat.
            if server_command == 'server_heartbeat':
                self.__bluetooth_socket.send("client_heartbeat" + utils.COMMAND_DELIMITER)

            # If the device server requests the hashes for the contact info on the current device,
            # return a list of the hashes for each currently synced contact's info.
            elif server_command == 'check_contact_info_hashes':
                syncing.Contacts.send_contact_info_hashes(self.__directory, self.__bluetooth_socket)
                self.__logger.info(f"Sent contact info hashes to device: {self.__name} ({self.__address})!")

            # If the device server requests the hashes for the contact photos on the current device,
            # return a list of the hashes for each currently synced contact's photo.
            elif server_command == 'check_contact_photo_hashes':
                syncing.Contacts.send_contact_photo_hashes(self.__directory, self.__bluetooth_socket)
                self.__logger.info(f"Sent contact photo hashes to device: {self.__name} ({self.__address})!")

            # If the device server requests the ids of the messages on the current device, return a list of
            # the message ids.
            elif server_command == 'check_message_ids':
                syncing.Messages.send_message_ids(self.__directory, self.__bluetooth_socket)
                self.__logger.info(f"Sent message ids to device: {self.__name} ({self.__address})!")

            # If the device server requests the ids of the messages on the current device, return a list of
            # the message ids.
            elif server_command == 'check_call_ids':
                syncing.Calls.send_call_ids(self.__directory, self.__bluetooth_socket)
                self.__logger.info(f"Sent call ids to device: {self.__name} ({self.__address})!")

            # If the server sends the clipboard from the target device, copy it to current device.
            elif 'incoming_clipboard:' in server_command:
                pyclip.copy(server_command[len("incoming_clipboard: "):])
                self.__logger.info(f"Copied clipboard from device: {self.__name} ({self.__address})!")

            # If the server sends a contact, try to write it to the database of the current device.
            elif 'incoming_contact:' in server_command:
                contact = server_command[len("incoming_contact: "):]
                syncing.Contacts.write_contact_to_database(self.__directory, contact)
                self.__logger.info(f"Wrote contact: {json.loads(contact)['mName']} from device: "
                                   f"{self.__name} ({self.__address}) to contacts!")

            # If the server sends part of a contact photo, write it to the contact photo buffer, or write it
            # to the database if it is complete.
            elif 'incoming_contact_photo:' in server_command:
                self.__handle_incoming_contact_photo_command(server_command)

            # If the server sends an sms message, write it to the database.
            elif 'incoming_message:' in server_command:
                message = server_command[len("incoming_message: "):]
                syncing.Messages.write_message_to_database(self.__directory, message)
                self.__logger.info(f"Wrote message with id: {json.loads(message)['mId']} from device: "
                                   f"{self.__name} ({self.__address}) to messages!")

            # If the server sends a call, write it to the database.
            elif 'incoming_call:' in server_command:
                call = server_command[len("incoming_call: "):]
                syncing.Calls.write_call_to_database(self.__directory, call)
                self.__logger.info(f"Wrote call with id: {json.loads(call)['mId']} from device: "
                                   f"{self.__name} ({self.__address}) to calls!")

            # If the server sends a notification, send a desktop notification..
            elif 'incoming_notification:' in server_command:
                notification = server_command[len("incoming_notification: "):]
                syncing.desktop_notify(notification)

            # If the server tells the current device to delete a currently synced contact, delete it.
            elif 'delete_contact:' in server_command:
                contact_id = int(server_command[len('delete_contact: '):])
                syncing.Contacts.delete_contact_from_database(self.__directory, contact_id)
                self.__logger.info(f"Deleted entries for contact id: {contact_id} from contacts!")

            # If the server tells the current device to delete a currently synced message, delete it.
            elif 'delete_message:' in server_command:
                call_id = int(server_command[len('delete_message: '):])
                syncing.Messages.delete_message_from_database(self.__directory, call_id)
                self.__logger.info(f"Deleted entries for message id: {call_id} from messages!")

            # If the server tells the current device to delete a currently synced message, delete it.
            elif 'delete_call:' in server_command:
                call_id = int(server_command[len('delete_call: '):])
                syncing.Calls.delete_call_from_database(self.__directory, call_id)
                self.__logger.info(f"Deleted entries for call id: {call_id} from calls!")

    def __handle_incoming_contact_photo_command(self, server_command):
        """Takes in server commands pertaining to the syncing of contact photos and either writes them
        to the database."""

        command_data = server_command[len("incoming_contact_photo: "):]

        contact_photo = {'contact_id': command_data.split(' | ')[0],
                         'hash': command_data.split(' | ')[1],
                         'base64': bytes(command_data.split(' | ')[2], 'utf-8')}

        logging.info(f"Writing contact photo for contact id: {contact_photo['contact_id']} from device:"
                     f" {self.__name} ({self.__address}) to contacts...")
        syncing.Contacts.write_contact_photo_to_database(self.__directory, contact_photo)

    def get_bluetooth_socket(self):
        return self.__bluetooth_socket

    def sync_thread_alive(self):
        return self.__sync_thread.is_alive()

    def stop_thread(self):
        self.__sync_thread.do_run = False

    def get_name(self):
        return self.__name

    def get_address(self):
        return self.__address

    def get_logger(self):
        return self.__logger


def initialize_directory(name, address):
    """Initializes the phone's data directory."""

    phone_directory = f'{utils.BASE_DIR}Phones/{name} ({address})'

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

    with open(f'{phone_directory}/config.ini', 'w') as config_file:
        config.write(config_file)
