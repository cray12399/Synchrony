from Phone import Phone
import time
import bluetooth
import pydbus
import os

# TODO: Create connection directly using QR code with UUID.


def init_directory():
    """Initializes the program's main directory with key folders."""

    if not os.path.isdir("Config/"):
        os.mkdir("Config/")

    if not os.path.isdir("Phones/"):
        os.mkdir("Phones/")


def handle_devices():
    """Tries to find connected devices that are compatible with the program."""

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
                phone_connections[device_address] = Phone(device_name, device_address, service_matches)

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
    init_directory()
    handle_devices()


if __name__ == '__main__':
    main()
