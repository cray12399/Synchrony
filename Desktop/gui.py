import os
import socket
import time
from pathlib import Path
import sys
from PySide2.QtWidgets import QFileDialog, QApplication
from PySide2.QtCore import QObject, Slot, Signal, QThread, QRunnable, QThreadPool, \
    QAbstractListModel, QModelIndex, Qt, Property, QTimer
from PySide2.QtQml import QQmlApplicationEngine
from tendo import singleton
import json
import select
import traceback
import pyclip
import backend

import utils

INCOMING_PHONE_DATA = 'incoming_phone_data'
REMOVE_PHONE_DATA = 'remove_phone_data'
UPDATE_SOCKET_CONNECTION = 'update_socket_connection'


class MainWindow(QObject):
    """GUI frontend for the program. Handles all user interaction."""

    setNumNewMessages = Signal(int)  # Signal used to update conversationsBtn in GUI with number of new messages.
    setNumNewCalls = Signal(int)  # Signal used to callsBtn in GUI with number of new calls.

    def __init__(self, backend_socket):
        super().__init__(None)

        self.__phone_data_model = PhoneDataModel()
        self.__backend_socket = backend_socket
        self.__thread_pool = QThreadPool(self)

        self.__initialize_looper_thread(backend_socket)

        self.log = lambda level, message, exception=False: \
            log(self.__backend_socket, level, message, exception)

        self.log('i', "GUI started successfully!")

    def __initialize_looper_thread(self, backend_socket):
        self.__looper_thread = LooperThread(backend_socket, self)
        self.__looper_thread.add_phone_data_signal.connect(self.__add_phone_data)
        self.__looper_thread.remove_phone_data_signal.connect(self.__remove_phone_data)
        self.__looper_thread.update_bt_socket_signal.connect(self.__update_socket_connection)
        self.__looper_thread.start()

    def get_phoneDataModel(self):
        return self.__phone_data_model

    phoneDataModel = Property(QObject, fget=get_phoneDataModel, constant=True)

    @Slot('QVariant')
    def printVar(self, var):
        """Method used for easily debugging qml code."""
        print(var)

    @Slot('QVariant')
    def doSync(self, selected_phone_data):
        """Sends a command to backend to initialize syncing with selected phone."""

        command_data = {'phone_name': selected_phone_data.property('name').toString(),
                        'phone_address': selected_phone_data.property('address').toString()}
        self.run_in_background(send_gui_command, self.__backend_socket,
                               f"do_sync: {json.dumps(command_data)}")

    @Slot('QVariant')
    def sendClipboard(self, selected_phone_data):
        """Sends a command to backend to send clipboard to selected phone."""

        command_data = {'clipboard': pyclip.paste().decode('utf-8'),
                        'phone_name': selected_phone_data.property('name').toString(),
                        'phone_address': selected_phone_data.property('address').toString()}
        self.run_in_background(send_gui_command, self.__backend_socket,
                               f"send_clipboard: {json.dumps(command_data)}")

    @Slot('QVariant')
    def sendFile(self, selected_phone_data):
        """Sends a command to backend to send a file to selected phone."""

        filename, _ = QFileDialog.getOpenFileName(None, 'Choose file to send...', '.')
        if filename != '':
            command_data = {'file_name': filename,
                            'phone_name': selected_phone_data.property('name').toString(),
                            'phone_address': selected_phone_data.property('address').toString()}
            self.run_in_background(send_gui_command, self.__backend_socket,
                                   f"send_file: {json.dumps(command_data)}")

    def __add_phone_data(self, phone_data):
        """Updates the phone connection data list and updates GUI phone data."""
        self.__phone_data_model.appendRow(phone_data['name'],
                                          phone_data['address'],
                                          phone_data['btSocketConnected'])

    def __update_socket_connection(self, command_data):
        phone_address = command_data['address']
        bt_socket_connected = command_data['btSocketConnected']

        self.__phone_data_model.editPropertiesByAddress(phone_address, btSocketConnected=bt_socket_connected)

    def __remove_phone_data(self, phone_address):
        self.__phone_data_model.removeRowByAddress(phone_address)

    def update_num_new_messages(self, num_new_messages):
        self.setNumNewMessages.emit(num_new_messages)

    def update_num_new_calls(self, num_new_calls):
        self.setNumNewCalls.emit(num_new_calls)

    def run_in_background(self, process, *args, **kwargs):
        worker = Worker(process, *args, **kwargs)
        self.__thread_pool.start(worker)


class LooperThread(QThread):
    """Looper thread provides all of the continuous background work of the GUI."""

    add_phone_data_signal = Signal(dict)  # Signal sends list of phone connections to GUI.
    remove_phone_data_signal = Signal(str)  # Signal sends address of phone data to remove.
    update_bt_socket_signal = Signal(dict)

    def __init__(self, backend_socket, parent=None):
        QThread.__init__(self, parent)

        self.__backend_socket = backend_socket
        self.__parent = parent

        log(self.__backend_socket, 'd', "GUI Command Handler Thread started successfully!")

    def run(self):
        try:
            while not self.isInterruptionRequested():
                self.__handle_connection()
        except Exception as e:
            print(e)

    def __handle_connection(self):
        """Handles connection with backend."""

        if self.__backend_socket is not None:
            ready = select.select([self.__backend_socket], [], [], utils.SOCKET_TIMEOUT)
            if ready[0]:
                connection_dropped = self.__receive_backend_commands()

                if connection_dropped:
                    self.__backend_socket = None
        else:
            # If backend connection is lost, close the GUI.
            exit()

    def __receive_backend_commands(self):
        test_connection = False

        try:
            backend_commands = self.__backend_socket.recv(4092).decode('utf-8')

            if backend_commands != '':
                while backend_commands[-len(utils.COMMAND_DELIMITER):] != utils.COMMAND_DELIMITER:
                    backend_commands += self.__backend_socket.recv(4092).decode('utf-8')

                backend_commands = [i for i in backend_commands.split(utils.COMMAND_DELIMITER) if i != '']

                if len(backend_commands):
                    for command in backend_commands:
                        self.__handle_backend_command(command)
            else:
                test_connection = True
        except socket.timeout:
            test_connection = True

        if test_connection:
            try:
                send_gui_command(self.__backend_socket, "test_connection")
            except BrokenPipeError:
                return True

        return False

    def __handle_backend_command(self, command):
        print(command)
        if INCOMING_PHONE_DATA in command:
            phone_data = json.loads(command[len(f'{INCOMING_PHONE_DATA}: '):])
            self.add_phone_data_signal.emit(phone_data)
        elif REMOVE_PHONE_DATA in command:
            phone_address = command[len(f'{REMOVE_PHONE_DATA}: '):]
            self.remove_phone_data_signal.emit(phone_address)
        elif UPDATE_SOCKET_CONNECTION in command:
            command_data = json.loads(command[len(f'{UPDATE_SOCKET_CONNECTION}: '):])
            self.update_bt_socket_signal.emit(command_data)


class Worker(QRunnable):
    """Performs general one-time work for the GUI."""

    def __init__(self, fn, *args, **kwargs):
        super(Worker, self).__init__()
        self.__fn = fn
        self.__args = args
        self.__kwargs = kwargs

    @Slot()
    def run(self):
        self.__fn(*self.__args, )


class PhoneDataModel(QAbstractListModel):
    NameRole = Qt.UserRole + 1000
    AddressRole = Qt.UserRole + 1001
    BtSocketConnectedRole = Qt.UserRole + 1002
    properties = ['name', 'address', 'btSocketConnected']

    dataChanged = Signal()

    def __init__(self, phone_data=None, parent=None):
        super(PhoneDataModel, self).__init__(parent)
        if phone_data is None:
            self.__phone_data = []
        else:
            self.__phone_data = phone_data

    @Property(int)
    def count(self):
        return len(self.__phone_data)

    def rowCount(self, parent=QModelIndex()):
        return len(self.__phone_data)

    def data(self, index, role=Qt.DisplayRole):
        if 0 <= index.row() < self.rowCount() and index.isValid():
            if role == PhoneDataModel.NameRole:
                return self.__phone_data[index.row()]["name"]
            elif role == PhoneDataModel.AddressRole:
                return self.__phone_data[index.row()]["address"]
            elif role == PhoneDataModel.BtSocketConnectedRole:
                return self.__phone_data[index.row()]["btSocketConnected"]

    @Slot(int, result='QVariant')
    def get(self, row):
        if 0 <= row < self.rowCount():
            return self.__phone_data[row]

    def roleNames(self):
        roles = super().roleNames()
        roles[PhoneDataModel.NameRole] = b"name"
        roles[PhoneDataModel.AddressRole] = b"address"
        roles[PhoneDataModel.BtSocketConnectedRole] = b"btSocketConnected"
        return roles

    def appendRow(self, name, address, bt_socket_connected):
        self.beginInsertRows(QModelIndex(), self.rowCount(), self.rowCount())
        self.__phone_data.append({'name': name, 'address': address, 'btSocketConnected': bt_socket_connected})
        self.endInsertRows()
        self.dataChanged.emit()

    def removeRow(self, row, parent=None, *args, **kwargs):
        self.beginRemoveRows(QModelIndex(), row, row)
        self.__phone_data.pop(row)
        self.endRemoveRows()
        self.dataChanged.emit()

    def removeRowByAddress(self, phone_address):
        for row_index in range(self.rowCount()):
            if self.__phone_data[row_index]['address'] == phone_address:
                self.removeRow(row_index)
                self.dataChanged.emit()
                break

    def editPropertiesByAddress(self, phone_address, **kwargs):
        for row_index in range(self.rowCount()):
            if self.__phone_data[row_index]['address'] == phone_address:
                for object_property in self.properties:
                    value = kwargs.get(object_property, None)
                    if value is not None:
                        self.__phone_data[row_index][object_property] = value
                        self.dataChanged.emit()

    def setData(self, index, value, role=Qt.EditRole):
        if not index.isValid():
            return False
        if role == Qt.EditRole:
            item = index.internalPointer()
            item.set_name(value)
        return True


def log(backend_socket, level, message, exception=False):
    """Sends a log command to the backend socket so that it can log GUI events to the log file."""

    if level == 'e' and exception:
        stacktrace = traceback.format_exc()
    else:
        stacktrace = None

    log_data = (level, message, stacktrace)
    send_gui_command(backend_socket, f"log: {json.dumps(log_data)}")


def send_gui_command(backend_socket, command):
    backend_socket.send(bytes(f"{command}{utils.COMMAND_DELIMITER}", 'utf-8'))


def make_backend_connection():
    backend_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    backend_socket.connect((socket.gethostname(), 5100))
    backend_socket.setblocking(True)
    return backend_socket


def main():
    single_instance = singleton.SingleInstance()
    backend_socket = make_backend_connection()

    app = QApplication()
    engine = QQmlApplicationEngine()

    main_window = MainWindow(backend_socket)
    engine.rootContext().setContextProperty("backend", main_window)

    engine.load(os.fspath(Path(__file__).resolve().parent / "GUI/MainWindow.qml"))

    if not engine.rootObjects():
        sys.exit(-1)

    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
