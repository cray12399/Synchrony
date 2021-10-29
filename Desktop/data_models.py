from datetime import datetime
import time
import utils
from PySide2.QtCore import Qt, QAbstractListModel, Signal, Slot, Property, QModelIndex, QObject
from PySide2.QtSql import QSqlDatabase, QSqlQueryModel


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


class ContactsSqlModel(QSqlQueryModel):
    def __init__(self, phone_dir=None, parent=None):
        super().__init__(parent)

        self.__phone_dir = phone_dir

        if phone_dir is not None:
            self.set_database(phone_dir)

    def set_phone_dir(self, phone_dir):
        self.__phone_dir = phone_dir

    def get_person_by_number(self, number):
        db = self.__get_db()

        self.setQuery(f"SELECT contact_id FROM contact_phones WHERE number LIKE {number}", db=db)
        contact_id = self.record(0).value("contact_id")
        self.setQuery(f"SELECT * FROM people WHERE contact_id = {contact_id}", db=db)
        record = self.record(0)

        person = {
            'name': record.value('name'),
            'contact_id': record.value('contact_id')
        }

        db.close()

        return person

    def get_person_by_id(self, contact_id):
        db = self.__get_db()

        self.setQuery(f"SELECT * FROM people WHERE contact_id = {contact_id}", db=db)
        record = self.record(0)

        person = {
            'name': record.value('name'),
            'contact_id': record.value('contact_id')
        }

        db.close()

        return person

    def __get_db(self):
        db = QSqlDatabase.addDatabase("QSQLITE")
        db.setDatabaseName(f"{self.__phone_dir}/data/contacts")
        db.open()

        return db


class MessagesSqlModel(QSqlQueryModel):
    def __init__(self, phone_dir=None, parent=None):
        super().__init__(parent)

        self.__phone_dir = phone_dir

        if phone_dir is not None:
            self.set_phone_dir(phone_dir)

    def set_phone_dir(self, phone_dir):
        self.__phone_dir = phone_dir

    def get_row(self, row_id):
        db = QSqlDatabase.addDatabase("QSQLITE")
        db.setDatabaseName(f"{self.__phone_dir}/data/messages")
        db.open()

        self.setQuery(f"SELECT * FROM sms WHERE id = {row_id}", db=db)
        record = self.record(0)
        row = {
            'id': record.value('id'),
            'thread_id': record.value('thread_id'),
            'number': record.value('number'),
            'date_sent': record.value('date_sent'),
            'type': record.value('type'),
            'read': record.value('read'),
            'body': record.value('body')
        }

        db.close()

        return row

    def get_last_messages(self):
        db = self.__get_db()

        last_messages = []

        unique_thread_ids = self.__get_unique_thread_ids(db)
        for thread_id in unique_thread_ids:
            last_messages.append(self.__get_last_message_data(db, thread_id))
        last_messages = sorted(last_messages, key=lambda i: i['dateSent'])

        db.close()
        return last_messages

    def __get_db(self):
        db = QSqlDatabase.addDatabase("QSQLITE")
        db.setDatabaseName(f"{self.__phone_dir}/data/messages")
        db.open()

        return db

    def __get_unique_thread_ids(self, db):
        unique_thread_ids = []

        self.setQuery(f"SELECT DISTINCT thread_id FROM sms", db=db)
        row = 0
        while self.record(row).value('thread_id') is not None:
            unique_thread_ids.append(self.record(row).value('thread_id'))
            row += 1

        return unique_thread_ids

    def __get_last_message_data(self, db, thread_id):
        self.setQuery(f"SELECT DISTINCT * FROM sms WHERE thread_id = {thread_id} "
                      f"ORDER BY date_sent", db=db)

        last_message_data = {
            'name': self.record(0).value('number'),
            'lastMessage': self.record(0).value('body').replace('\n', ''),
            'dateSent': self.record(0).value('date_sent'),
            'threadId': thread_id,
            'type': self.record(0).value('type'),
            'read': bool(self.record(0).value('read')),
            'number': self.record(0).value('number')
        }

        return last_message_data


class CallsSqlModel(QSqlQueryModel):
    def __init__(self, phone_dir=None, parent=None):
        super().__init__(parent)

        self.__phone_dir = phone_dir

        if phone_dir is not None:
            self.set_database(phone_dir)

    def set_phone_dir(self, phone_dir):
        self.__phone_dir = phone_dir

    def get_row(self, row_id):
        db = self.__get_db()

        self.setQuery(f"SELECT * FROM calls WHERE id = {row_id}", db=db)
        record = self.record(0)
        row = {'id': record.value('id'),
               'number': record.value('number'),
               'type': record.value('type'),
               'date': record.value('date'),
               'duration': record.value('duration')
               }

        db.close()

        return row

    def __get_db(self):
        db = QSqlDatabase.addDatabase("QSQLITE")
        db.setDatabaseName(f"{self.__phone_dir}/data/calls")
        db.open()

        return db


class SqlModelHandler(QObject):
    """QObject handles all SQL databases for a given phone."""

    dataUpdated = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)

        self.__phone_dir = ''

        self.__calls_model = CallsSqlModel()
        self.__messages_model = MessagesSqlModel()
        self.__contacts_model = ContactsSqlModel()

        self.__conversations_list_model = ConversationsListModel()

    def get_conversations_list_model(self):
        return self.__conversations_list_model

    conversationsListModel = Property(QObject, fget=get_conversations_list_model, constant=True)

    def set_phone_dir(self, phone_dir):
        self.__phone_dir = phone_dir

        self.__calls_model.set_phone_dir(phone_dir)
        self.__messages_model.set_phone_dir(phone_dir)
        self.__contacts_model.set_phone_dir(phone_dir)

        last_messages = self.__messages_model.get_last_messages()
        conversation_data = self.__fill_in_last_message_names(last_messages)
        self.__conversations_list_model.setConversationData(conversation_data)

    def __fill_in_last_message_names(self, last_messages):
        for message_index in range(len(last_messages)):
            message = last_messages[message_index]
            person = self.__contacts_model.get_person_by_number(message['name'])
            if person['name'] is not None:
                last_messages[message_index]['name'] = person['name']

        return last_messages

    def handle_selected_phone_change(self, selected_phone_data):
        name = selected_phone_data['phone_name']
        address = selected_phone_data['phone_address']
        self.set_phone_dir(f'{utils.BASE_DIR}Phones/{name} ({address})')

    def get_num_unread_conversations(self):
        return self.__conversations_list_model.getNumUnread()


class ConversationsListModel(QAbstractListModel):
    NameRole = Qt.UserRole + 1100
    LastMessageRole = Qt.UserRole + 1101
    DateSentRole = Qt.UserRole + 1102
    ThreadIdRole = Qt.UserRole + 1103
    NumberRole = Qt.UserRole + 1104
    TypeRole = Qt.UserRole + 1105
    ReadRole = Qt.UserRole + 1106
    properties = ['name', 'lastMessage', 'dateSent', 'threadId', 'number', 'type', 'read']

    dataChanged = Signal()

    def __init__(self, phone_data=None, parent=None):
        super(ConversationsListModel, self).__init__(parent)
        if phone_data is None:
            self.__conversation_data = []
        else:
            self.__conversation_data = phone_data

    @Property(int)
    def count(self):
        return len(self.__conversation_data)

    def rowCount(self, parent=QModelIndex()):
        return len(self.__conversation_data)

    def data(self, index, role=Qt.DisplayRole):
        if 0 <= index.row() < self.rowCount() and index.isValid():
            if role == ConversationsListModel.NameRole:
                return self.__conversation_data[index.row()]["name"]
            elif role == ConversationsListModel.LastMessageRole:
                return self.__conversation_data[index.row()]["lastMessage"]
            elif role == ConversationsListModel.DateSentRole:
                return self.__conversation_data[index.row()]["dateSent"]
            elif role == ConversationsListModel.ThreadIdRole:
                return self.__conversation_data[index.row()]["threadId"]
            elif role == ConversationsListModel.NumberRole:
                return self.__conversation_data[index.row()]["number"]
            elif role == ConversationsListModel.TypeRole:
                return self.__conversation_data[index.row()]["type"]
            elif role == ConversationsListModel.ReadRole:
                return self.__conversation_data[index.row()]["read"]

    @Slot(int, result='QVariant')
    def get(self, row):
        if 0 <= row < self.rowCount():
            return self.__conversation_data[row]

    def roleNames(self):
        roles = super().roleNames()
        roles[ConversationsListModel.NameRole] = b"name"
        roles[ConversationsListModel.LastMessageRole] = b"lastMessage"
        roles[ConversationsListModel.DateSentRole] = b"dateSent"
        roles[ConversationsListModel.ThreadIdRole] = b"threadId"
        roles[ConversationsListModel.NumberRole] = b"number"
        roles[ConversationsListModel.TypeRole] = b"type"
        roles[ConversationsListModel.ReadRole] = b"read"
        return roles

    def appendRow(self, name, last_message, date_sent, thread_id, number, _type, read):
        self.beginInsertRows(QModelIndex(), self.rowCount(), self.rowCount())
        self.__conversation_data.append({'name': name, 'lastMessage': last_message, 'dateSent': date_sent,
                                         'threadId': thread_id, 'number': number, 'type': _type, 'read': read})
        self.endInsertRows()
        self.dataChanged.emit()

    def removeRow(self, row, parent=None, *args, **kwargs):
        self.beginRemoveRows(QModelIndex(), row, row)
        self.__conversation_data.pop(row)
        self.endRemoveRows()
        self.dataChanged.emit()

    def setData(self, index, value, role=Qt.EditRole):
        if not index.isValid():
            return False
        if role == Qt.EditRole:
            item = index.internalPointer()
            item.set_name(value)
        return True

    def setConversationData(self, conversation_data):
        self.beginRemoveRows(QModelIndex(), 0, len(self.__conversation_data) - 1)
        self.__conversation_data = []
        self.endRemoveRows()

        for conversation in conversation_data:
            self.appendRow(conversation['name'], conversation['lastMessage'], conversation['dateSent'],
                           conversation['threadId'], conversation['number'], conversation['type'], conversation['read'])
        self.dataChanged.emit()

    def getNumUnread(self):
        num_unread = 0
        for conversation in self.__conversation_data:
            if not conversation['read']:
                num_unread += 1

        return num_unread


