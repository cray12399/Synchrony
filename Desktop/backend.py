import socket
import utils
import pydbus
import json
import select
import bluetooth
import subprocess
from phone import Phone
import gui

LOG = 'log'
SEND_FILE = 'send_file'
SEND_CLIPBOARD = 'send_clipboard'
DO_SYNC = 'do_sync'
SEND_SMS = 'send_sms'


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
            self.__handle_phones()
            self.__handle_gui_connection()

    def __handle_phones(self):
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
                phone = Phone(device_name, device_address, service_matches, self.__logger)
                self.__phone_connections[device_address] = phone

                if self.__gui_client_socket is not None:
                    self.__send_phone_data_to_gui(phone)

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
            for address in connections_to_remove:
                self.__phone_connections.pop(address)
                self.__remove_phone_data_from_gui(address)

        # Handle commands from phones
        for phone in self.__phone_connections.values():
            for command_index in range(len(phone.get_command_queue())):
                if command_index < len(phone.get_command_queue()):
                    command = phone.get_command_queue()[command_index]
                    self.__send_backend_command(command[0], command[1])
                    phone.remove_from_queue(command_index)

    def __handle_gui_connection(self):
        if self.__gui_client_socket is not None:
            ready = select.select([self.__gui_client_socket], [], [], utils.SOCKET_TIMEOUT)
            if ready[0]:
                reset_connection = self.__receive_gui_command()

                if reset_connection:
                    self.__gui_client_socket = None
                    self.__gui_client_address = None

                    self.__logger.debug("GUI disconnected!")
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
        except ConnectionResetError:
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

        self.__logger.info(f"Requested sync from device: {phone_name} ({phone_address})!")

    def __send_clipboard(self, command_data):
        """Sends a command to the target phone to share clipboard."""

        clipboard = command_data['clipboard']
        phone_name = command_data['phone_name']
        phone_address = command_data['phone_address']

        phone = self.__phone_connections.get(phone_address)
        phone.get_bluetooth_socket().send(f"incoming_clipboard: {clipboard}{utils.COMMAND_DELIMITER}")

        self.__logger.info(f"Sent clipboard to device: {phone_name} ({phone_address})!")

    def __send_backend_command(self, command, command_data=""):
        """Sends a backend command to the GUI."""

        try:
            if self.__gui_client_socket is not None:
                self.__gui_client_socket.send(bytes(f"{command}: {command_data}{utils.COMMAND_DELIMITER}", 'utf-8'))
        except BrokenPipeError:
            self.__logger.exception(f"Could not send command to GUI: {self.__gui_client_address}!")
            self.__gui_client_socket = None
            self.__gui_client_address = None

    def __make_gui_connection(self):
        """Attempts to make a connection with GUI. If no connection is established, the connection is set to None."""

        try:
            client_socket_tmp, client_address_tmp = self.__backend_socket.accept()
            self.__logger.debug(f"Successfully connected with GUI at address {client_address_tmp}")

            self.__gui_client_socket = client_socket_tmp
            self.__gui_client_address = client_address_tmp

            self.__gui_client_socket.setblocking(False)

            for phone in self.__phone_connections.values():
                self.__send_phone_data_to_gui(phone)
        except socket.timeout:
            self.__gui_client_socket = None
            self.__gui_client_address = None

    def __send_phone_data_to_gui(self, phone):
        phone_data = {'name': phone.get_name(),
                      'address': phone.get_address(),
                      'btSocketConnected': phone.get_bluetooth_socket() is not None}

        if self.__gui_client_socket is not None:
            self.__send_backend_command(gui.INCOMING_PHONE_DATA, json.dumps(phone_data))

        self.__logger.debug(f"Sent phone connection data to GUI: {self.__gui_client_address}!")

    def __remove_phone_data_from_gui(self, phone_address):
        self.__send_backend_command(gui.REMOVE_PHONE_DATA, phone_address)

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

        try:
            if LOG not in command:
                self.__logger.debug(f"GUI ({self.__gui_client_address}): {command}")

            if LOG in command:
                log_data = json.loads(command[len(f"{LOG}: "):])
                self.__gui_log(log_data[0], log_data[1], log_data[2])
            elif SEND_FILE in command:
                command_data = json.loads(command[len(f"{SEND_FILE}: "):])
                send_file(command_data, self.__logger)
            elif SEND_CLIPBOARD in command:
                command_data = json.loads(command[len(f"{SEND_CLIPBOARD}: "):])
                self.__send_clipboard(command_data)
            elif DO_SYNC in command:
                command_data = json.loads(command[len(f"{DO_SYNC}: "):])
                self.__do_sync(command_data)
            elif SEND_SMS in command:
                command_data = json.loads(command[len(f"{DO_SYNC}: "):])
                phone_address = command_data['selected_phone']['phone_address']
                number = command_data['number']
                message = command_data['message']

                phone = self.__phone_connections.get(phone_address)
                phone.send_message(number, message)

        except:
            self.__logger.exception(f"Exception raised when handling GUI command: {command}")


def send_file(command_data, logger):
    """Sends a file to a phone using OBEX."""

    file_name = command_data['file_name']
    phone_name = command_data['phone_name']
    phone_address = command_data['phone_address']

    output = subprocess.check_output(['bt-obex', '-p', phone_address, file_name])

    logger.info(f"Sent file: {file_name} to device: {phone_name} ({phone_address})")


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
