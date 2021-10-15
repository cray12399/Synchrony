import datetime
from configparser import ConfigParser
import subprocess

"""Utility class holds all constants and general functions for the program."""

# CONSTANTS
SERVER_PORT = 8
LOGS_TO_KEEP = 20
SOCKET_TIMEOUT = 1
COMMAND_DELIMITER = '``;'
BASE_DIR = ''


def initialize_app_config():
    config = ConfigParser()

    with open('app_config.ini', 'w') as config_file:
        config.write(config_file)
