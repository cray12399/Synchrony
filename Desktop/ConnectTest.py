import bluetooth
import pydbus


def list_connected_devices():
    bus = pydbus.SystemBus()
    manager = bus.get('org.bluez', '/')

    managed_objects = manager.GetManagedObjects()
    connected_devices = []
    for path in managed_objects:
        con_state = managed_objects[path].get('org.bluez.Device1', {}).get('Connected', False)
        if con_state:
            addr = managed_objects[path].get('org.bluez.Device1', {}).get('Address')
            name = managed_objects[path].get('org.bluez.Device1', {}).get('Name')
            connected_devices.append({'name': name, 'address': addr})

    return connected_devices


def main():
    connected_device = list_connected_devices()[0]
    device_address = connected_device['address']
    service_matches = bluetooth.find_service(address=device_address)
    first_match = service_matches[0]
    host = first_match["host"]

    sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
    sock.connect((host, 8))
    while 1:
        message = input("Enter message to send: ")
        sock.send(message)

        receipt_message = sock.recv(1024).decode("utf-8")
        while receipt_message != "Received!":
            receipt_message = sock.recv(1024).decode("utf-8")
        print(receipt_message)


if __name__ == '__main__':
    main()
