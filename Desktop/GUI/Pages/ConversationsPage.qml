import QtQuick 2.0
import QtQuick.Controls 2.15
import QtGraphicalEffects 1.0

import "../"
import "../CustomWidgets"
import "../Buttons"
import QtQuick.Layouts 1.11

Rectangle {
    property var selectedPhone
    property var sqlModelHandler: backend.sqlModelHandler
    property var conversationsListModel: sqlModelHandler.conversationsListModel
    property var currentMessagesListModel: sqlModelHandler.currentConversationListModel
    property int conversationIndex: 0
    property var selectedConversation: conversationsListModel[conversationIndex]

    id: conversationsPage

    anchors.fill: parent
    color: Style.background

    Connections {
        target: conversationsPage.conversationsListModel

        function onDataChanged() {
            conversationsPage.conversationsListModel = sqlModelHandler.conversationsListModel
        }
    }

    SplitView {
        id: splitView
        anchors.fill: parent

        handle: Item {
            id: splitterHandle
            implicitWidth: 5

            Rectangle {
                color: Style.secondaryVariant
                anchors.fill: parent
            }
        }

        Rectangle {
            id: conversationsListPanel
            SplitView.preferredWidth: 300
            SplitView.minimumWidth: 200
            color: Style.surface

            ColumnLayout {
                id: columnLayout
                spacing: 0
                anchors.fill: parent

                Item {
                    height: 50
                    Layout.alignment: Qt.AlignLeft | Qt.AlignTop
                    Layout.fillWidth: true

                    Rectangle {
                        id: conversationSearchPanel
                        color: Style.primaryVariant
                        anchors.fill: parent

                        LineEdit {
                            id: conversationsSearchBar
                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.verticalCenter: parent.verticalCenter
                            anchors.leftMargin: 10
                            anchors.rightMargin: 10
                            height: 20
                            radius: height / 2
                            placeholderText: "Search Conversations..."

                        }
                    }

                    CustomDropShadow {
                        source: conversationSearchPanel
                    }
                }

                ListView {
                    id: conversationListView
                    Layout.fillHeight: true
                    Layout.fillWidth: true
                    clip: true
                    model: conversationsListModel
                    currentIndex: 0
                    delegate: Rectangle {
                        id: conversationListDelegate
                        property bool selected: conversationIndex == index
                        color: selected ? Style.secondary : "transparent"
                        anchors.right: parent.right
                        anchors.left: parent.left
                        height: textContainer.allText.includes(conversationsSearchBar.currentText.toLowerCase()) ? 60 : 0
                        visible: textContainer.allText.includes(conversationsSearchBar.currentText.toLowerCase())

                        MouseArea {
                            hoverEnabled: true
                            anchors.fill: parent
                            z:1

                            onContainsMouseChanged: {
                                if (containsMouse) {
                                    cursorShape = Qt.PointingHandCursor
                                    imageContainer.anchors.leftMargin = 20
                                } else {
                                    cursorShape = Qt.ArrowCursor
                                    imageContainer.anchors.leftMargin = 10
                                }
                            }

                            onPressedChanged: {
                                if (pressed) {
                                    highlight.visible = true
                                    highlight.width = parent.width
                                    forceActiveFocus()
                                } else {
                                    conversationIndex = index
                                    sqlModelHandler.setConversationIndex(conversationIndex)
                                    highlight.visible = false
                                    highlight.width = parent.width * .5
                                    currentContactNameText.text = name
                                    currentContactPhoto.photoSource = photo
                                    sqlModelHandler.setCurrentMessages(number)
                                    currentMessagesListView.positionViewAtBeginning()
                                }
                            }
                        }

                        Rectangle {
                            id: highlight
                            anchors.top: parent.top
                            anchors.bottom: parent.bottom
                            anchors.horizontalCenter: parent.horizontalCenter
                            opacity: .5
                            color: "white"
                            visible: false
                            width: parent.width * .5
                            antialiasing: true
                            z: 2

                            Behavior on width {
                                PropertyAnimation {
                                    duration: animationDuration
                                    easing {
                                        type: Easing.OutExpo
                                    }
                                }
                            }
                        }

                        ContactPhoto {
                            id: contactPhoto
                            anchors.left: parent.left
                            anchors.top: parent.top
                            anchors.bottom: parent.bottom
                            anchors.topMargin: 10
                            anchors.bottomMargin: 10
                            anchors.leftMargin: 10
                            width: height
                            photoSource: photo
                        }

                        Item {
                            id: textContainer
                            anchors.left: contactPhoto.right
                            anchors.right: parent.right
                            height: contactNameText.height + lastMessageText.height
                            anchors.verticalCenter: contactPhoto.verticalCenter
                            anchors.margins: 10
                            property string allText: (contactNameText.text + lastMessageText.text).toLowerCase()
                            property color textColor: conversationListDelegate.selected ? Style.colorOnSecondary : Style.colorOnSurface

                            Label {
                                id: contactNameText
                                anchors.right: parent.right
                                anchors.left: parent.left
                                text: name
                                font.bold: true
                                color: parent.textColor
                                font.pixelSize: 15
                                elide: Text.ElideRight
                            }

                            Label {
                                id: lastMessageText
                                text: lastMessage
                                font.bold: !read
                                font.italic: type === "Sent"
                                anchors.top: contactNameText.bottom
                                anchors.right: parent.right
                                anchors.left: parent.left
                                color: parent.textColor
                                elide: Text.ElideRight
                            }
                        }
                    }
                }

            }
        }

        Rectangle {
            id: currentConversationPanel
            SplitView.preferredWidth: 500
            SplitView.minimumWidth: 300
            color: "transparent"

            ColumnLayout {
                id: columnLayout1
                anchors.fill: parent
                spacing: 0

                Item {
                    height: 50
                    Layout.alignment: Qt.AlignLeft | Qt.AlignTop
                    Layout.fillWidth: true

                    Rectangle {
                        id: currentConversationTopPanel
                        anchors.fill: parent
                        color: Style.surface

                        ContactPhoto {
                            id: currentContactPhoto
                            photoSource: ""
                            anchors.left: parent.left
                            anchors.top: parent.top
                            anchors.bottom: parent.bottom
                            anchors.topMargin: 10
                            anchors.bottomMargin: 10
                            anchors.leftMargin: 15
                            width: height
                        }

                        Label {
                            id: currentContactNameText
                            anchors.left: currentContactPhoto.right
                            anchors.leftMargin: 10
                            anchors.verticalCenter: parent.verticalCenter
                            text: conversationsListModel.get(conversationListView.currentIndex).name
                            font.bold: true
                            font.pixelSize: 15
                            color: Style.colorOnSurface
                        }
                    }

                    CustomDropShadow {
                        source: currentConversationTopPanel
                    }

                }

                SplitView {
                    orientation: Qt.Vertical
                    Layout.fillHeight: true
                    Layout.fillWidth: true

                    handle: Item {
                        id: splitterHandle2
                        implicitHeight: 5

                        Rectangle {
                            color: Style.secondaryVariant
                            anchors.fill: parent
                        }
                    }

                    Item {
                        id: messagesPanel
                        SplitView.fillHeight: true
                        clip: true

                        Column {
                            id: column
                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.top: parent.top
                            anchors.bottom: parent.bottom
                            anchors.topMargin: 15
                            anchors.bottomMargin: 15
                            anchors.rightMargin: 15
                            anchors.leftMargin: 15

                            ListView {
                                id: currentMessagesListView
                                anchors.fill: parent
                                verticalLayoutDirection: ListView.BottomToTop
                                spacing: 5
                                model: currentMessagesListModel

                                delegate: Column {
                                    id: message
                                    property bool sentByMe: type !== "Received"
                                    anchors.right: sentByMe ? currentMessagesListView.contentItem.right : undefined
                                    spacing: 6

                                    Row {
                                        id: messageRow
                                        spacing: 6
                                        anchors.right: sentByMe ? parent.right : undefined

                                        Item {
                                            id: contactPhotoContainer
                                            height: 35
                                            width: sentByMe ? 0 : height
                                            anchors.verticalCenter: parent.verticalCenter

                                            ContactPhoto {
                                                property bool contactPhotoVisible: photoVisibilityHandler.photoVisible()
                                                id: messageContactPhoto
                                                anchors.fill: parent
                                                visible: contactPhotoVisible
                                                photoSource: photo
                                            }

                                            QtObject {
                                                id: photoVisibilityHandler

                                                function photoVisible() {
                                                    if (message.sentByMe) {
                                                        return false
                                                    } else {
                                                        if (index !== 0) {
                                                            var nextMessageType = currentMessagesListModel.get(index - 1).type

                                                            if (nextMessageType === "Received") {
                                                                return false
                                                            } else {
                                                                return true
                                                            }
                                                        } else {
                                                            return true
                                                        }

                                                    }
                                                }
                                            }
                                        }

                                        Rectangle {
                                            id: messageBubble
                                            width: Math.min(messageText.implicitWidth + 24,
                                                currentMessagesListView.width - (!sentByMe ? messageContactPhoto.width + messageRow.spacing : 0))
                                            height: messageText.implicitHeight + 24
                                            color: sentByMe ? Style.primary : Style.secondary
                                            radius: 25

                                            Label {
                                                id: messageText
                                                text: body
                                                color: sentByMe ? Style.colorOnPrimary : Style.colorOnSecondary
                                                anchors.fill: parent
                                                anchors.margins: 12
                                                wrapMode: Label.Wrap
                                            }

                                            MouseArea {
                                                anchors.fill: parent

                                                hoverEnabled: true

                                                onContainsMouseChanged: {
                                                    if (containsMouse) {
                                                        cursorShape = Qt.PointingHandCursor
                                                    } else {
                                                        cursorShape = Qt.ArrowCursor
                                                    }
                                                }

                                                onClicked: {
                                                    timestampText.showTimestamp = !timestampText.showTimestamp
                                                }
                                            }
                                        }
                                    }

                                    Label {
                                        property bool showTimestamp: false
                                        id: timestampText
                                        text: time
                                        horizontalAlignment: parent.sentByMe ? Text.AlignRight : Text.AlignLeft
                                        color: Style.inactive
                                        visible: showTimestamp
                                        anchors.leftMargin: contactPhotoContainer.width + 6
                                        anchors.left: parent.left
                                        anchors.right: parent.right
                                    }
                                }
                            }
                        }

                    }

                    Item {
                        id: composePanel
                        SplitView.preferredHeight: 50
                        SplitView.minimumHeight: 50

                        Rectangle {
                            anchors.fill: parent
                            color: Style.primary

                            Item {
                                id: messageCompositionBar
                                anchors.top: parent.top
                                anchors.bottom: parent.bottom
                                anchors.right: sendMessageBtn.left
                                anchors.left: parent.left
                                anchors.leftMargin: 15
                                anchors.topMargin: 10
                                anchors.bottomMargin: 10
                                anchors.rightMargin: 10

                                Rectangle {
                                    id: compositionBG
                                    color: Style.colorOnPrimary
                                    anchors.fill: parent
                                    clip: true
                                    radius: 20

                                    Label {
                                        id: composeMessageText
                                        text: "Compose Message..."
                                        color: Style.inactive
                                        anchors.left: parent.left
                                        anchors.top: parent.top
                                        anchors.leftMargin: 10
                                        anchors.topMargin: 7

                                        visible: messageCompositionEdit.text == ""
                                    }

                                    IconButton {
                                        id: clearCompositionBtn
                                        anchors.right: parent.right
                                        anchors.verticalCenter: parent.verticalCenter
                                        anchors.rightMargin: 5
                                        iconMargin: 3
                                        height: 20
                                        width: height
                                        iconSource: "../Images/icons/clear.svg"
                                        bgVisible: false
                                        iconColor: Style.primaryVariant
                                        z: 3
                                        visible: messageCompositionEdit.text != ""

                                        onClicked: messageCompositionEdit.text = ""
                                    }

                                    Flickable {
                                        id: compositionFlickable
                                        anchors.left: parent.left
                                        anchors.top: parent.top
                                        anchors.bottom: parent.bottom
                                        anchors.right: clearCompositionBtn.right
                                        anchors.topMargin: 7
                                        anchors.rightMargin: 10
                                        anchors.leftMargin: 10
                                        contentHeight: messageCompositionEdit.height
                                        contentWidth: width

                                        function scrollToBottom() {
                                            while (!compositionFlickable.atYEnd) {
                                                compositionFlickable.contentY += .1
                                            }
                                        }


                                        TextEdit {
                                            id: messageCompositionEdit
                                            anchors.top: parent.top
                                            wrapMode: TextEdit.WordWrap
                                            width: compositionFlickable.width
                                            z: 2

                                            onHeightChanged: {
                                                compositionFlickable.scrollToBottom()
                                            }
                                        }
                                    }
                                }
                            }

                            IconButton {
                                id: sendMessageBtn
                                anchors.right: parent.right
                                anchors.top: parent.top
                                anchors.rightMargin: 15
                                anchors.topMargin: 5
                                iconMargin: 5
                                height: 40
                                width: height
                                iconSource: "../Images/icons/send.svg"
                                bgVisible: false
                                iconColor: messageCompositionEdit.text == "" ? Style.inactive : Style.colorOnPrimary
                                enabled: messageCompositionEdit.text != ""

                                onClicked: {
                                    backend.sendMessage(selectedPhone,
                                                        messageCompositionEdit.text,
                                                        conversationsListModel.get(conversationIndex).number)
                                    messageCompositionEdit.text = ""
                                }
                            }
                        }

                    }

                }
            }
        }
    }
}

/*##^##
Designer {
    D{i:0;autoSize:true;height:480;width:640}
}
##^##*/
