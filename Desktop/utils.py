from configparser import ConfigParser
import subprocess

"""Utility class holds all constants and general functions for the program."""

# CONSTANTS
SERVER_PORT = 8
SOCKET_TIMEOUT = 10
COMMAND_DELIMITER = "``;"


def initialize_app_config():
    config = ConfigParser()

    config['paths'] = {'base_path': subprocess.check_output(
        ['xdg-user-dir']).decode('utf-8').replace('\n', '') + "/.synchrony"}

    config['paths'] = {'file_transfer_path': subprocess.check_output(
        ['xdg-user-dir', 'DOWNLOAD']).decode('utf-8').replace('\n', '')}

    with open('app_config.ini', 'w') as config_file:
        config.write(config_file)


def get_file_transfer_path():
    config = ConfigParser()
    config.read('app_config.ini')

    return config.get('paths', 'file_transfer_path')
