import logging
import os
import sys
import utils
from datetime import datetime
from tendo import singleton
from backend import Backend


def initialize_logging():
    """Initializes the program's logging."""

    if not os.path.isdir(f'{utils.BASE_DIR}Logs/'):
        os.mkdir(f'{utils.BASE_DIR}Logs/')

    while len([file for file in os.listdir('Logs/')]) > utils.LOGS_TO_KEEP:
        os.remove(f"{utils.BASE_DIR}Logs/{[file for file in os.listdir(f'{utils.BASE_DIR}Logs/')][0]}")

    file_handler = logging.FileHandler(
        filename=f"{utils.BASE_DIR}Logs/Log - ({datetime.now().strftime('%m.%d.%y-%H.%M.%S')})")
    stream_handler = logging.StreamHandler(sys.stdout)
    handlers = [file_handler, stream_handler]

    logging.basicConfig(format='%(name)s :: %(levelname)-8s :: %(message)s',
                        level=logging.DEBUG,
                        handlers=handlers)

    logger = logging.getLogger()
    logger.setLevel(logging.DEBUG)

    return logger


def initialize_directory(logger):
    """Initializes the program's main directory with key folders."""

    logger.info("Initializing directories...")

    if not os.path.isdir(f'{utils.BASE_DIR}Phones/'):
        os.mkdir(f'{utils.BASE_DIR}Phones/')
        logger.debug(f"Successfully created directory: {utils.BASE_DIR}/Phones/!")

    if not os.path.isfile('app_config.ini'):
        utils.initialize_app_config()

    logger.info("Directory successfully initialized!")


def main():
    single_instance = singleton.SingleInstance()
    logger = initialize_logging()
    initialize_directory(logger)

    Backend(logger)


if __name__ == '__main__':
    main()
