import logging
import os
import sys
import utils
from datetime import datetime
from tendo import singleton
from backend import Backend


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


def initialize_directory(logger):
    """Initializes the program's main directory with key folders."""

    logger.info("Initializing directories...")

    if not os.path.isdir("Phones/"):
        os.mkdir("Phones/")
        logger.debug(f"Successfully created directory: {os.path.abspath}/Phones/!")

    if not os.path.isfile('app_config.ini'):
        utils.initialize_app_config()

    logger.info("Directory successfully initialized!")


def main():
    single_instance = singleton.SingleInstance()
    logger = initialize_logging()
    initialize_directory(logger)

    backend_script = Backend(logger)


if __name__ == '__main__':
    main()
