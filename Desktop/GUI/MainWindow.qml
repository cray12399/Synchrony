import QtQuick 2.14
import QtQuick.Window 2.14
import QtQuick.Controls 2.15
import QtQuick.Layouts 1.11
import "buttons"
import "customWidgets"
import "pages"

Window {
    property int activePage: 1

    id: window
    width: 800
    height: 500
    visible: true
    title: qsTr("Synchrony")

    Rectangle {
        id: topBarBg
        height: 58
        color: "#0054ff"
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.topMargin: 0
        anchors.leftMargin: 0
        anchors.rightMargin: 0

        RowLayout {
            id: topBarBtnLayout
            width: parent.height * 3
            anchors.left: parent.left
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            spacing: 5
            anchors.leftMargin: 15
            anchors.topMargin: 10
            anchors.bottomMargin: 5

            PageBtn {
                Layout.fillHeight: true
                Layout.fillWidth: true
                btnIconSource: "images/icons/settings.svg"
                page: 0
                active: activePage === page

                onClicked: {
                    if (activePage != page) {
                        activePage = page
                    }
                }
            }

            PageBtn {
                btnNumNewContent: 10
                Layout.fillHeight: true
                Layout.fillWidth: true
                btnIconSource: "images/icons/conversations.svg"
                page: 1
                active: activePage === page

                onClicked: {
                    if (activePage != page) {
                        activePage = page
                    }
                }
            }

            PageBtn {
                btnNumNewContent: 100
                Layout.fillHeight: true
                Layout.fillWidth: true
                btnIconSource: "images/icons/dialer.svg"
                page: 2
                active: activePage === page

                onClicked: {
                    if (activePage != page) {
                        activePage = page
                    }
                }
            }

            PageBtn {
                btnNumNewContent: 1
                Layout.fillHeight: true
                Layout.fillWidth: true
                btnIconSource: "images/icons/sync.svg"
                page: 3
                active: activePage === page

                onClicked: {
                    if (activePage != page) {
                        activePage = page
                    }
                }
            }
        }

        PhoneSelectionBox {
            id: phoneSelectionBox
            width: parent.width * .4
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 14
            anchors.topMargin: 14
            anchors.rightMargin: 15

        }
    }

    StackLayout {
        id: pageStack
        currentIndex: activePage
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: topBarBg.bottom
        anchors.bottom: parent.bottom
        anchors.topMargin: 0

        Rectangle {
            anchors.fill: parent
            color: "green"
        }

        ConversationsPage {

        }

        Rectangle {
            anchors.fill: parent
            color: "black"
        }

        Rectangle {
            anchors.fill: parent
            color: "red"
        }
    }
}
