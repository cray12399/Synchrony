import os
import socket
from pathlib import Path
import sys
from PySide2.QtWidgets import QFileDialog, QApplication
from PySide2.QtCore import QObject, Slot, Signal, QThread, QRunnable, QThreadPool
from PySide2.QtQml import QQmlApplicationEngine
from tendo import singleton
import json
import select
import traceback
import pyclip

import utils


class MainWindow(QObject):
    """GUI frontend for the program. Handles all user interaction."""

    setPhones = Signal(list)  # Signal used to update phoneSelector in GUI with list of phones.
    setNumNewMessages = Signal(int)  # Signal used to update conversationsBtn in GUI with number of new messages.
    setNumNewCalls = Signal(int)  # Signal used to callsBtn in GUI with number of new calls.

    def __init__(self, backend_socket):
        super().__init__(None)

        self.__phone_connections = {}
        self.__backend_socket = backend_socket
        self.__thread_pool = QThreadPool(self)

        self.__initialize_looper_thread(backend_socket)

        self.log = lambda level, message, exception=False: \
            log(self.__backend_socket, level, message, exception)

        send_gui_command(self.__backend_socket, "get_phone_connection_list")

        self.log('d', "GUI started successfully!")

    def __initialize_looper_thread(self, backend_socket):
        self.__looper_thread = LooperThread(backend_socket, self)
        self.__looper_thread.phone_list_signal.connect(lambda phone_list: self.update_phone_connections(phone_list))
        self.__looper_thread.start()

    @Slot(list)
    def doSync(self, selected_phone):
        """Sends a command to backend to initialize syncing with selected phone."""

        command_data = {'phone_name': selected_phone[0],
                        'phone_address': selected_phone[1]}
        self.run_in_background(send_gui_command, self.__backend_socket,
                               f"do_sync: {json.dumps(command_data)}")

    @Slot(list)
    def sendClipboard(self, selected_phone):
        """Sends a command to backend to send clipboard to selected phone."""

        command_data = {'clipboard': pyclip.paste().decode('utf-8'),
                        'phone_name': selected_phone[0],
                        'phone_address': selected_phone[1]}
        self.run_in_background(send_gui_command, self.__backend_socket,
                               f"send_clipboard: {json.dumps(command_data)}")

    @Slot(list)
    def sendFile(self, selected_phone):
        """Sends a command to backend to send a file to selected phone."""

        filename, _ = QFileDialog.getOpenFileName(None, 'Choose file to send...', '.')
        if filename != '':
            command_data = {'file_name': filename,
                            'phone_name': selected_phone[0],
                            'phone_address': selected_phone[1]}
            self.run_in_background(send_gui_command, self.__backend_socket,
                                   f"send_file: {json.dumps(command_data)}")

    def update_phone_connections(self, phone_connections):
        """Updates the phone connections list with the one provided by the backend."""

        self.__phone_connections = phone_connections
        # print(phone_connections)
        print(phone_connections)
        self.setPhones.emit([[name, address] for name, address in phone_connections.items()])
        # self.setPhones.emit([[f"Phone {i+1}", f"1000{i}"] for i in range(5)])

    def update_num_new_messages(self, num_new_messages):
        self.setNumNewMessages.emit(num_new_messages)

    def update_num_new_calls(self, num_new_calls):
        self.setNumNewCalls.emit(num_new_calls)

    def run_in_background(self, process, *args, **kwargs):
        worker = Worker(process, *args, **kwargs)
        self.__thread_pool.start(worker)


class LooperThread(QThread):
    """Looper thread provides all of the continuous background work of the GUI."""

    phone_list_signal = Signal(dict)  # Signal sends list of phone connections to GUI.

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
        if 'incoming_phone_list' in command:
            phone_connections = json.loads(command[len('incoming_phone_list: '):])
            self.phone_list_signal.emit(phone_connections)
        else:
            print(command)


class Worker(QRunnable):
    """Performs general one-time work for the GUI."""

    def __init__(self, fn, *args, **kwargs):
        super(Worker, self).__init__()
        self.__fn = fn
        self.__args = args
        self.__kwargs = kwargs

    @Slot()
    def run(self):
        self.__fn(*self.__args,)


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
