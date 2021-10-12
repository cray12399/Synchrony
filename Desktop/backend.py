import socket
import utils
import pydbus
import json
import time
import select
import bluetooth
from phone import Phone


class Backend:
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
                self.__broadcast_phone_connection_change()

        # Check for devices that are no longer connected and stop their connection background services.
        connections_to_remove = []
        for device_address in self.__phone_connections.keys():
            if device_address not in [connected_device['address'] for connected_device in list_connected_devices()]:
                phone = self.__phone_connections[device_address]
                phone.stop_thread()
                self.__logger.info(f"Stopped connection for device: {phone.get_name()} ({phone.get_address()})!")

                connections_to_remove.append(device_address)

        if len(connections_to_remove):
            for connection in connections_to_remove:
                self.__phone_connections.pop(connection)
            self.__broadcast_phone_connection_change()

    def __handle_connections(self):
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

    def __send_backend_command(self, command):
        self.__gui_client_socket.send(bytes(f"{command}{utils.COMMAND_DELIMITER}", 'utf-8'))

    def __handle_gui_command(self, command):
        print(command)

    def __make_gui_connection(self):
        try:
            client_socket_tmp, client_address_tmp = self.__backend_socket.accept()
            self.__logger.debug(f"Successfully connected with GUI at address {client_address_tmp}")

            gui_client_socket = client_socket_tmp
            gui_client_address = client_address_tmp

            gui_client_socket.setblocking(False)

            time.sleep(.1)

            self.__initialize_gui()

            self.__gui_client_socket = client_socket_tmp
            self.__gui_client_address = client_address_tmp
        except socket.timeout:
            self.__gui_client_socket = None
            self.__gui_client_address = None

    def __initialize_gui(self):
        self.__broadcast_phone_connection_change()

    def __broadcast_phone_connection_change(self):
        phone_connections = json.dumps(
            {phone.get_name(): phone.get_address() for phone in self.__phone_connections.values()})
        if self.__gui_client_socket is not None:
            try:
                self.__gui_client_socket.send(bytes(f"incoming_phone_list: "
                                                    f"{phone_connections}{utils.COMMAND_DELIMITER}", 'utf-8'))
            except Exception as e:
                print(e)


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
