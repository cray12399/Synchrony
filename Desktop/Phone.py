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
    def __init__(self, name, address, service_matches):
        self.__name = name
        self.__address = address
        self.__service_matches = service_matches
        self.__directory = initialize_directory(name, address)

        self.__last_heartbeat = -1
        self.__connection_time = -1

        self.__sync_thread = threading.Thread(target=self.do_sync)
        self.__sync_thread.do_run = True
        self.__sync_thread.start()

        self.__photo_sync_threads = {}
        self.__contact_photo_buffer = {}

    def do_sync(self):
        while True:
            bluetooth_socket = self.try_connect()
            while getattr(threading.currentThread(), 'do_run', True):
                if bluetooth_socket is not None:
                    self.handle_commands(bluetooth_socket)
                    bluetooth_socket = self.check_socket_timing(bluetooth_socket)
                else:
                    time.sleep(5)
                    bluetooth_socket = self.try_connect()
            print(f"{self.__name}: Thread Stopped.")

    def try_connect(self):
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
        server_commands = []
        try:
            server_commands = bluetooth_socket.recv(1024).decode('utf-8').split(";")
        except bluetooth.btcommon.BluetoothError:
            pass

        for server_command in server_commands:
            if server_command == "server_heartbeat":
                bluetooth_socket.send("client_heartbeat;")
            if server_command == "check_contacts_info_sync":
                threading.Thread(target=syncer.Contacts.check_contacts_info_sync,
                                 args=(self.__directory, bluetooth_socket)).start()
            if server_command == "check_contacts_photos_sync":
                threading.Thread(target=syncer.Contacts.check_contacts_photos_sync,
                                 args=(self.__directory, bluetooth_socket)).start()
            elif "incoming_clipboard:" in server_command:
                pyclip.copy(server_command.split(": ")[1])
            elif "incoming_contact:" in server_command:
                contact = server_command.split(": ")[1]
                threading.Thread(target=syncer.Contacts.write_contact_to_database,
                                 args=(self.__directory, contact)).start()
            elif "incoming_contact_photo_part:" in server_command:
                self.handle_incoming_photo_part_command(server_command)
            elif "delete_contact:" in server_command:
                contact_id = int(server_command.replace("delete_contact: ", ""))
                threading.Thread(target=syncer.Contacts.remove_contact_from_database,
                                 args=(self.__directory, contact_id)).start()

            if server_command != "":
                self.__last_heartbeat = datetime.now()

    def handle_incoming_photo_part_command(self, server_command):
        extra_part = server_command.split(": ")[1]
        contact_id = extra_part.split(' | ')[0]
        info_part = extra_part.split(' | ')[1]

        if 'START' in info_part:
            if contact_id not in self.__contact_photo_buffer.keys():
                self.__contact_photo_buffer[contact_id] = {}
                self.__contact_photo_buffer[contact_id]['base64'] = []
                self.__contact_photo_buffer[contact_id]['contact_id'] = contact_id
                self.__contact_photo_buffer[contact_id]['hash'] = int(info_part.replace("START ", ""))
        elif info_part != 'END':
            self.__contact_photo_buffer[contact_id]['base64'].append(info_part)
        else:
            photo_64_bytes = bytes(''.join(self.__contact_photo_buffer[contact_id]['base64']), 'utf-8')
            self.__contact_photo_buffer[contact_id]['base64'] = photo_64_bytes

            threading.Thread(target=syncer.Contacts.write_contact_photo_to_database,
                             args=(self.__directory, self.__contact_photo_buffer[contact_id])).start()
            self.__contact_photo_buffer.pop(contact_id)

    def sync_thread_alive(self):
        return self.__sync_thread.isAlive()

    def stop_thread(self):
        self.__sync_thread.do_run = False

    def check_socket_timing(self, bluetooth_socket):
        socket_disconnected = False
        if self.__last_heartbeat != -1 and isinstance(self.__last_heartbeat, datetime):
            socket_disconnected = datetime.now() - self.__last_heartbeat > timedelta(seconds=utils.SOCKET_TIMEOUT)
        else:
            if isinstance(self.__connection_time, datetime):
                socket_disconnected = datetime.now() - self.__connection_time > timedelta(seconds=utils.SOCKET_TIMEOUT)

        if socket_disconnected:
            bluetooth_socket.close()
            self.__connection_time = -1
            self.__last_heartbeat = -1
            print("Socket disconnected")
            bluetooth_socket = None

        return bluetooth_socket


def initialize_directory(name, address):
    phone_directory = f'{utils.BASE_DIR}Phones/{name} ({address})'

    if not os.path.isdir(phone_directory):
        os.mkdir(phone_directory)

    if not os.path.isfile(f'{phone_directory}/config.ini'):
        create_phone_ini(phone_directory)

    return phone_directory


def create_phone_ini(phone_directory):
    config = ConfigParser()
    config['vars'] = {'initial_connection': 'True'}

    with open(f'{phone_directory}/config.ini', 'w') as config_file:
        config.write(config_file)
