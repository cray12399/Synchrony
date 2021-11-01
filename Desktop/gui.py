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
import utils
from data_models import PhoneDataModel, SqlModelHandler
import backend

INCOMING_PHONE_DATA = 'incoming_phone_data'
REMOVE_PHONE_DATA = 'remove_phone_data'
UPDATE_SOCKET_CONNECTION = 'update_socket_connection'
MESSAGE_SYNC_COMPLETE = 'message_sync_complete'
CALLS_SYNC_COMPLETE = 'calls_sync_complete'
CONTACTS_SYNC_COMPLETE = 'contacts_sync_complete'
CONTACT_PHOTO_SYNC_COMPLETE = 'contact_photo_sync_complete'


class MainWindow(QObject):
    """GUI frontend for the program. Handles all user interaction."""

    setNumNewMessages = Signal(int)  # Signal used to update conversationsBtn in GUI with number of new messages.
    setNumNewCalls = Signal(int)  # Signal used to callsBtn in GUI with number of new calls.

    def __init__(self, backend_socket):
        super().__init__(None)

        self.__phone_data_model = PhoneDataModel()
        self.__selected_phone_data = None
        self.__sql_model_handler = SqlModelHandler()
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
        self.__looper_thread.calls_sync_complete.connect(None)
        self.__looper_thread.contacts_sync_complete.connect(None)
        self.__looper_thread.message_sync_complete.connect(lambda: self.__update_gui(
            self.__selected_phone_data['name'],
            self.__selected_phone_data['address']))
        self.__looper_thread.contact_photo_sync_complete.connect(None)
        self.__looper_thread.start()

    def __update_gui(self, name, address):
        self.__sql_model_handler.update_models(name, address)
        self.update_num_new_messages(self.__sql_model_handler.get_num_unread_conversations())

    def get_phone_data_model(self):
        return self.__phone_data_model

    phoneDataModel = Property(QObject, fget=get_phone_data_model, constant=True)

    def get_sql_model_handler(self):
        return self.__sql_model_handler

    sqlModelHandler = Property(QObject, fget=get_sql_model_handler, constant=True)

    @Slot('QVariant')
    def selectedPhoneChanged(self, selected_phone_data):
        if selected_phone_data is not None:
            selected_phone_data = {
                'name': selected_phone_data.property('name').toString(),
                'address': selected_phone_data.property('address').toString()
            }
            self.__selected_phone_data = selected_phone_data

            self.__sql_model_handler.update_models(selected_phone_data['name'],
                                                   selected_phone_data['address'])

            self.setNumNewMessages.emit(self.__sql_model_handler.get_num_unread_conversations())

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

    @Slot('QVariant', str, str)
    def sendMessage(self, selected_phone_data, message, recipient_number):
        selected_phone_data = {'phone_name': selected_phone_data.property('name').toString(),
                               'phone_address': selected_phone_data.property('address').toString()}

        command_data = {
            'selected_phone': selected_phone_data,
            'number': recipient_number,
            'message': message
        }

        self.run_in_background(send_gui_command, self.__backend_socket,
                               f"{backend.SEND_SMS}: {json.dumps(command_data)}")

    def __add_phone_data(self, phone_data):
        """Updates the phone connection data list and updates GUI phone data."""
        self.__phone_data_model.appendRow(phone_data['name'],
                                          phone_data['address'],
                                          phone_data['btSocketConnected'])
        self.__phone_data_model.appendRow('phone 1', '1001', True)

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
    calls_sync_complete = Signal()
    message_sync_complete = Signal()
    contact_photo_sync_complete = Signal()
    contacts_sync_complete = Signal()

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
        elif MESSAGE_SYNC_COMPLETE in command:
            self.message_sync_complete.emit()
        elif CONTACTS_SYNC_COMPLETE in command:
            self.contacts_sync_complete.emit()
        elif CONTACT_PHOTO_SYNC_COMPLETE in command:
            self.contact_photo_sync_complete.emit()
        elif CALLS_SYNC_COMPLETE in command:
            self.calls_sync_complete.emit()


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
