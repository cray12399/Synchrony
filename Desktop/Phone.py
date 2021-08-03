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

    def do_sync(self):
        while True:
            bluetooth_socket = self.try_connect()
            while getattr(threading.currentThread(), "do_run", True):
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
            socket.setblocking(0)
            self.__connection_time = datetime.now()
            print("Connected to socket!")
        except bluetooth.btcommon.BluetoothError as e:
            print(e)
            return None

        return socket

    def handle_commands(self, socket):
        server_command = ""
        try:
            server_command = socket.recv(1024).decode('utf-8')
        except bluetooth.btcommon.BluetoothError:
            pass

        if server_command == "server_heartbeat":
            print(server_command)
            socket.send("client_heartbeat")
        elif "incoming_clipboard:" in server_command:
            pyclip.copy(server_command.replace("incoming_clipboard: ", ""))
        elif "incoming_contact:" in server_command:
            print(server_command)
            socket.send("sync_done")

        if server_command != "":
            self.__last_heartbeat = datetime.now()

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

    sub_dirs = ['Messages', 'Contacts']
    for sub_dir in sub_dirs:
        if not os.path.isdir(f'{phone_directory}/{sub_dir}'):
            os.mkdir(f'{phone_directory}/{sub_dir}')

    return phone_directory


def create_phone_ini(phone_directory):
    config = ConfigParser()
    config['vars'] = {'initial_connection': 'True'}

    with open(f'{phone_directory}/config.ini', 'w') as config_file:
        config.write(config_file)
