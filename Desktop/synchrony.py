import logging
import os
import sys
import time
from configparser import ConfigParser
from datetime import datetime

import bluetooth
import pydbus

from Phone import Phone


def initialize_logging():
    if not os.path.isdir("Logs/"):
        os.mkdir("Logs/")

    while len([file for file in os.listdir('Logs/')]) > 20:
        os.remove(f"Logs/{[file for file in os.listdir('Logs/')][0]}")

    file_handler = logging.FileHandler(filename=f"Logs/Log - ({datetime.now().strftime('%m.%d.%y-%H.%M.%S')})")
    stream_handler = logging.StreamHandler(sys.stdout)
    handlers = [file_handler, stream_handler]

    logging.basicConfig(format=' %(name)s :: %(levelname)-8s :: %(message)s',
                        level=logging.DEBUG,
                        handlers=handlers)

    logger = logging.getLogger()
    logger.setLevel(logging.DEBUG)

    return logging.getLogger()


def initialize_config(logger):
    config = ConfigParser()

    with open('main_config.ini', 'w') as config_file:
        config.write(config_file)


def initialize_directory(logger):
    """Initializes the program's main directory with key folders."""

    logger.info("Initializing directories...")

    if not os.path.isdir("Phones/"):
        os.mkdir("Phones/")
        logger.debug(f"Successfully created directory: {os.path.abspath}/Phones/!")

    logger.info("Directory successfully initialized!")


def handle_devices(logger):
    """Tries to find connected devices that are compatible with the program."""

    logger.info("Starting device handling process!")

    phone_connections = {}

    while True:
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
            if is_phone and device_address not in phone_connections.keys():
                phone_connections[device_address] = Phone(device_name, device_address, service_matches, logger)

        # Check for devices that are no longer connected and stop their connection background services.
        connections_to_remove = []
        for device_address in phone_connections.keys():
            if device_address not in [connected_device['address'] for connected_device in list_connected_devices()]:
                phone = phone_connections[device_address]
                phone.stop_thread()
                logger.info(f"Stopped connection for device: {phone.get_name()} ({phone.get_address()})!")

                connections_to_remove.append(device_address)
        for connection in connections_to_remove:
            phone_connections.pop(connection)

        time.sleep(.5)


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


def main():
    logger = initialize_logging()
    initialize_directory(logger)
    handle_devices(logger)


if __name__ == '__main__':
    main()
