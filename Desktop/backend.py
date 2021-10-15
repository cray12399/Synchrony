import socket
import utils
import pydbus
import json
import time
import select
import bluetooth
import subprocess
from phone import Phone


class Backend:
    """Backend class performs all of the program's background work and communicates with the GUI."""

    def __init__(self, logger):
        self.__logger = logger

        self.__backend_socket = initialize_backend_socket()
        self.__gui_client_socket = None
        self.__gui_client_address = None
        self.__phone_connections = {}

        self.__run()

    def __run(self):
        while True:
            self.__handle_devices()
            self.__handle_connections()

    def __handle_devices(self):
        """Tries to find connected devices that are compatible with the program."""

        # Iterate over every device connected by bluetooth.
        for connected_device in list_connected_devices():
            device_name = connected_device['name']
            device_address = connected_device['address']
            service_matches = bluetooth.find_service(address=device_address)

            # Check if the device is compatible with the program.
            is_phone = False
            for service_match in service_matches:
                if bluetooth.GENERIC_TELEPHONY_CLASS in service_match['service-classes']:
                    is_phone = True

            # If it is compatible and it is not being connected, connect it.
            if is_phone and device_address not in self.__phone_connections.keys():
                self.__phone_connections[device_address] = Phone(device_name,
                                                                 device_address,
                                                                 service_matches,
                                                                 self.__logger)
                self.__send_phone_connection_list()

        # Check for devices that are no longer connected and stop their connection background services.
        connections_to_remove = []
        for device_address in self.__phone_connections.keys():
            if device_address not in [connected_device['address'] for connected_device in list_connected_devices()]:
                phone = self.__phone_connections[device_address]
                phone.stop_thread()
                self.__logger.info(f"Stopped connection for device: {phone.get_name()} ({phone.get_address()})!")

                connections_to_remove.append(device_address)

        # Remove non-existent connections from the phone connections list.
        if len(connections_to_remove):
            for connection in connections_to_remove:
                self.__phone_connections.pop(connection)
            self.__send_phone_connection_list()

    def __handle_connections(self):
        """Handles connections with GUI."""

        if self.__gui_client_socket is not None:
            ready = select.select([self.__gui_client_socket], [], [], utils.SOCKET_TIMEOUT)
            if ready[0]:
                reset_connection = self.__receive_gui_command()

                if reset_connection:
                    self.__gui_client_socket = None
                    self.__gui_client_address = None
        else:
            self.__make_gui_connection()

    def __receive_gui_command(self):
        test_connection = False

        try:
            gui_commands = self.__gui_client_socket.recv(4092).decode('utf-8')

            if gui_commands != '':
                while gui_commands[-len(utils.COMMAND_DELIMITER):] != utils.COMMAND_DELIMITER:
                    gui_commands += self.__gui_client_socket.recv(4092).decode('utf-8')

                gui_commands = [i for i in gui_commands.split(utils.COMMAND_DELIMITER) if i != '']

                if len(gui_commands):
                    for command in gui_commands:
                        self.__handle_gui_command(command)
            else:
                test_connection = True
        except socket.timeout:
            test_connection = True

        if test_connection:
            try:
                self.__send_backend_command("connection_test")
            except BrokenPipeError:
                return True

        return False

    def __do_sync(self, command_data):
        """Sends a command to the target phone to initialize syncing."""

        phone_name = command_data['phone_name']
        phone_address = command_data['phone_address']

        phone = self.__phone_connections.get(phone_address)
        phone.get_bluetooth_socket().send(f"do_sync{utils.COMMAND_DELIMITER}")

    def __send_clipboard(self, command_data):
        """Sends a command to the target phone to share clipboard."""

        clipboard = command_data['clipboard']
        phone_name = command_data['phone_name']
        phone_address = command_data['phone_address']

        phone = self.__phone_connections.get(phone_address)
        phone.get_bluetooth_socket().send(f"incoming_clipboard: {clipboard}{utils.COMMAND_DELIMITER}")

    def __send_backend_command(self, command):
        """Sends a backend command to the GUI."""

        self.__gui_client_socket.send(bytes(f"{command}{utils.COMMAND_DELIMITER}", 'utf-8'))

    def __make_gui_connection(self):
        """Attempts to make a connection with GUI. If no connection is established, the connection is set to None."""

        try:
            client_socket_tmp, client_address_tmp = self.__backend_socket.accept()
            self.__logger.debug(f"Successfully connected with GUI at address {client_address_tmp}")

            self.__gui_client_socket = client_socket_tmp
            self.__gui_client_address = client_address_tmp

            self.__gui_client_socket.setblocking(False)
        except socket.timeout:
            self.__gui_client_socket = None
            self.__gui_client_address = None

    def __send_phone_connection_list(self):
        """Sends list of phone connections to the GUI."""

        phone_connections = json.dumps(
            {phone.get_name(): phone.get_address() for phone in self.__phone_connections.values()})
        if self.__gui_client_socket is not None:
            try:
                self.__send_backend_command(f"incoming_phone_list: {phone_connections}{utils.COMMAND_DELIMITER}")
            except Exception as e:
                print(e)

    def __gui_log(self, level, message, stacktrace):
        """Since the logger cannot be shared with GUI, logs messages received from GUI log command."""
        if level == 'i':
            self.__logger.info(message)
        if level == 'd':
            self.__logger.debug(message)
        if level == 'e':
            stacktrace = '' if stacktrace is None else '\n' + stacktrace
            self.__logger.error_signal(f"{message}{stacktrace}")

    def __handle_gui_command(self, command):
        """Handles all incoming GUI commands and passes their data (if applicable) to the relevant function."""

        if 'log' not in command:
            self.__logger.debug(f"GUI ({self.__gui_client_address}): {command}")

        if 'log' in command:
            log = json.loads(command[len('log: '):])
            self.__gui_log(log[0], log[1], log[2])
        elif 'get_phone_connection_list' in command:
            self.__send_phone_connection_list()
        elif 'send_file' in command:
            command_data = json.loads(command[len("send_file: "):])
            send_file(command_data)
        elif 'send_clipboard' in command:
            command_data = json.loads(command[len("send_clipboard: "):])
            self.__send_clipboard(command_data)
        elif 'do_sync' in command:
            command_data = json.loads(command[len("do_sync: "):])
            self.__do_sync(command_data)


def send_file(command_data):
    """Sends a file to a phone using OBEX."""

    file_name = command_data['file_name']
    phone_name = command_data['phone_name']
    phone_address = command_data['phone_address']

    output = subprocess.check_output(['bt-obex', '-p', phone_address, file_name])


def list_connected_devices():
    """Gets a list of all connected bluetooth devices."""

    bus = pydbus.SystemBus()
    manager = bus.get('org.bluez', '/')

    managed_objects = manager.GetManagedObjects()
    connected_devices = []
    for path in managed_objects:
        con_state = managed_objects[path].get('org.bluez.Device1', {}).get('Connected', False)
        if con_state:
            address = managed_objects[path].get('org.bluez.Device1', {}).get('Address')
            name = managed_objects[path].get('org.bluez.Device1', {}).get('Name')
            connected_devices.append({'name': name, 'address': address})

    return connected_devices


def initialize_backend_socket():
    backend_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    backend_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    backend_socket.bind((socket.gethostname(), 5100))
    backend_socket.listen()
    backend_socket.settimeout(utils.SOCKET_TIMEOUT)
    return backend_socket
