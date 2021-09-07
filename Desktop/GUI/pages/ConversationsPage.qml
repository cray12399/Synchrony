import QtQuick 2.0
import QtQuick.Controls 2.15
import QtQuick.Layouts 1.11
import QtGraphicalEffects 1.15
import "../buttons"
import "../customWidgets"

Rectangle {
    id: conversationsPage
    anchors.fill: parent
    height: 455
    width: 800

    SplitView {
        id: splitView
        anchors.fill: parent
        orientation: Qt.Horizontal

        handle: Rectangle {
                implicitWidth: 8
                implicitHeight: 5
                color: "#050633"

                Image {
                    id: dots
                    anchors.verticalCenter: parent.verticalCenter
                    width: parent.width
                    height: 10
                    source: "../images/icons/three_dots_vertical.svg"
                }

                ColorOverlay {
                    anchors.fill: dots
                    source: dots
                    color: "white"
                    antialiasing: true
                }
            }

        Rectangle {
            id: sideBar
            width: 300
            SplitView.minimumWidth: 225
            SplitView.preferredWidth: 300
            color: "#cbcbcb"
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 0
            anchors.topMargin: 0
            clip: true

            Rectangle {
                id: sideBarTopBar
                y: 0
                z: 2
                height: 44
                color: "#0e1052"
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.leftMargin: 0
                anchors.rightMargin: 0

                SearchBar {
                    id: conversationSearchBar
                    color: "#ffffff"
                    anchors.left: parent.left
                    anchors.right: newConversationBtn.left
                    anchors.top: parent.top
                    anchors.bottom: parent.bottom
                    textColor: "#000000"
                    clearTxtBtnColor: "#000000"
                    anchors.bottomMargin: 10
                    anchors.topMargin: 10
                    anchors.leftMargin: 10
                    anchors.rightMargin: 10
                }

                IconBtn {
                    id: newConversationBtn
                    width: 35
                    height: 35
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.right: parent.right
                    anchors.rightMargin: 10
                    btnIconSource: "../images/icons/plus.svg"

                }
            }

            ScrollView {
                id: conversationsListScroll
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.top: sideBarTopBar.bottom
                anchors.bottom: parent.bottom
                z: 1
                anchors.topMargin: 0

                ColumnLayout {
                    id: conversationListLayout
                    x: 0
                    y: 0
                    width: conversationsListScroll.width
                    spacing: 2
                    clip: true

                    ConversationTab {
                        contactPhotoSource: "/home/chris/Downloads/person.jpg"
                        Layout.fillWidth: true

                    }

                    ConversationTab {
                        contactPhotoSource: "/home/chris/Downloads/person2.jpg"
                        Layout.fillWidth: true
                    }

                    ConversationTab {
                        Layout.fillWidth: true
                    }

                    ConversationTab {
                        Layout.fillWidth: true
                    }

                    ConversationTab {
                        Layout.fillWidth: true
                    }

                    ConversationTab {
                        Layout.fillWidth: true
                    }

                    ConversationTab {
                        Layout.fillWidth: true
                    }
                }
            }
        }

        Rectangle {
            id: conversationView
            SplitView.fillWidth: false
            SplitView.minimumWidth: 400
            color: "#ffffff"
            anchors.left: sideBar.right
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            anchors.leftMargin: 0
            anchors.rightMargin: 0
            anchors.topMargin: 0
            anchors.bottomMargin: 0

            Rectangle {
                id: conversationTopBar
                y: 0
                height: 43
                color: "#cbcbcb"
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.leftMargin: 0
                anchors.rightMargin: 0

                Label {
                    id: contactLabel
                    y: 14
                    width: 250
                    color: "#000000"
                    text: qsTr("Contact Name")
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.left: parent.left
                    font.pointSize: 13
                    anchors.leftMargin: 20
                    font.bold: true
                }

                IconBtn {
                    x: 452
                    y: 3
                    height: contactLabel.height * 1.25
                    width: height
                    anchors.verticalCenter: parent.verticalCenter
                    anchors.right: parent.right
                    iconBig: 20
                    doAnimation: false
                    iconColor: "#000000"
                    btnIconSource: "../images/icons/three_dots_vertical.svg"
                    anchors.rightMargin: 10

                }
            }

            SplitView {
                id: conversationSplitView
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.top: conversationTopBar.bottom
                anchors.bottom: parent.bottom
                anchors.leftMargin: 5
                orientation: Qt.Vertical
                anchors.topMargin: 0

                Rectangle {
                    id: messagesView
                    height: 200
                    color: "#ff0000"
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.top: parent.top
                    anchors.topMargin: 0
                    anchors.leftMargin: 0
                    anchors.rightMargin: 0
                }

                Rectangle {
                    id: rectangle
                    height: 200
                    color: "#0900ff"
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.bottom: parent.bottom
                    anchors.bottomMargin: 0
                    anchors.leftMargin: 0
                    anchors.rightMargin: 0
                }

            }
        }

    }

}


