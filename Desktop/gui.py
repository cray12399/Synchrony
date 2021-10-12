import datetime
import os
import socket
import time
from pathlib import Path
import sys
from PySide2.QtGui import QGuiApplication
from PySide2.QtCore import QObject, Slot, Signal, QThread, QUrl, QTimer
from PySide2.QtQml import QQmlApplicationEngine
from tendo import singleton
import json
import select

import utils


class MainWindow(QObject):
    setPhones = Signal(list)

    def __init__(self):
        QObject.__init__(self)

        self.phone_connections = {}

        self.__command_handler_thread = CommandListenerThread(self)
        self.__command_handler_thread.start()

    def update_phones_list(self, phone_names):
        self.setPhones.emit(phone_names)

    @Slot(str)
    def doSync(self, selected_phone):
        self.__command_handler_thread.send_gui_command(f"do_sync: {selected_phone}")

    @Slot(str)
    def sendClipboard(self, selected_phone):
        self.__command_handler_thread.send_gui_command(f"send_clipboard: {selected_phone}")

    @Slot(str)
    def sendFile(self, selected_phone):
        self.__command_handler_thread.send_gui_command(f"send_file: {selected_phone}")


class CommandListenerThread(QThread):
    def __init__(self, parent=None):
        QThread.__init__(self, parent)

        self.__backend_socket = None
        self.__parent = parent

    def run(self):
        try:
            while True:
                if self.__backend_socket is not None:
                    ready = select.select([self.__backend_socket], [], [], utils.SOCKET_TIMEOUT)
                    if ready[0]:
                        reset_connection = self.__receive_backend_commands()

                        if reset_connection:
                            self.__backend_socket = None
                else:
                    self.__make_backend_connection()
        except Exception as e:
            print(e)

    def send_gui_command(self, command):
        self.__backend_socket.send(bytes(f"{command}{utils.COMMAND_DELIMITER}", 'utf-8'))

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
                        self.__handle_command(command)
            else:
                test_connection = True
        except socket.timeout:
            test_connection = True

        if test_connection:
            try:
                self.send_gui_command("test_connection")
            except BrokenPipeError:
                return True

        return False

    def __handle_command(self, command):
        if 'incoming_phone_list' in command:
            phone_connections = json.loads(command[len('incoming_phone_list: '):])
            self.__parent.phone_connections = phone_connections
            self.__parent.update_phones_list([f"{name} ({address})" for name, address in phone_connections.items()])
        else:
            print(command)

    def __make_backend_connection(self):
        try:
            self.__backend_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.__backend_socket.connect((socket.gethostname(), 5100))
            self.__backend_socket.setblocking(0)
        except ConnectionRefusedError:
            self.__backend_socket = None


def main():
    single_instance = singleton.SingleInstance()

    app = QGuiApplication(sys.argv)
    engine = QQmlApplicationEngine()

    main_window = MainWindow()
    engine.rootContext().setContextProperty("backend", main_window)

    engine.load(os.fspath(Path(__file__).resolve().parent / "GUI/MainWindow.qml"))

    if not engine.rootObjects():
        sys.exit(-1)
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
