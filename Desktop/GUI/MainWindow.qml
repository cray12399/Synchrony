import QtQuick 2.14
import QtQuick.Window 2.14
import QtQuick.Controls 2.15
import QtQuick.Layouts 1.11
import Qt.labs.settings 1.0

import "."
import "Buttons"
import "CustomWidgets"
import "Pages"

Window {
    property int activePage: 0

    id: mainWindow
    visible: true
    width: 600
    height: 400
    title: "Synchrony"

    Settings {
        property alias x: mainWindow.x
        property alias y: mainWindow.y
        property alias width: mainWindow.width
        property alias height: mainWindow.height
    }

    SystemPalette {
        id: systemPalette
        colorGroup: SystemPalette.Active
    }

    Rectangle {
        id: background
        anchors.fill: parent
        color: Style.background
    }

    ColumnLayout {
        id: mainLayout
        anchors.fill: parent

        Rectangle {
            id: topBar
            width: 200
            height: 45
            color: Style.primaryVariant
            Layout.minimumWidth: 40
            Layout.preferredHeight: 45
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignLeft | Qt.AlignTop

            PhoneSelector {
                id: phoneSelector
                width: topBar.width * .3
                anchors.right: syncRow.left
                anchors.top: parent.top
                anchors.bottom: parent.bottom
                anchors.rightMargin: 10
                anchors.topMargin: 10
                anchors.bottomMargin: 10
                listHeight: mainWindow.height - topBar.height
                itemBgColor: Style.primaryVariant
                itemTextColor: Style.colorOnPrimary
                comboBgColor: Style.colorOnPrimary
                downArrowColor: Style.primary
            }

            Row {
                id: pageRow
                anchors.left: parent.left
                anchors.top: parent.top
                anchors.bottom: parent.bottom
                spacing: 5
                anchors.leftMargin: 15
                anchors.topMargin: 5
                anchors.bottomMargin: 5

                IconButton {
                    property int pageIndex: 0

                    id: settingsBtn
                    anchors.top: parent.top
                    anchors.bottom: parent.bottom
                    anchors.bottomMargin: 0
                    anchors.topMargin: 0
                    width: height
                    iconSource: "Images/icons/settings.svg"
                    bgVisible: false
                    iconColor: activePage == pageIndex ? Style.colorOnPrimary : Style.inactivePage

                    Behavior on rotation {
                        PropertyAnimation {
                            id: settingsBtnPropertyAnimation
                            duration: 500
                            easing {type: Easing.InOutBounce}
                    }}

                    onClicked: {
                        if (!settingsBtnPropertyAnimation.running) {
                            if (activePage != pageIndex) {
                                settingsBtn.rotation += 360
                            }
                            activePage = pageIndex
                        }
                    }
                }

                IconButton {
                    property int pageIndex: 1

                    id: conversationsBtn
                    anchors.top: parent.top
                    anchors.bottom: parent.bottom
                    anchors.bottomMargin: 0
                    anchors.topMargin: 0
                    bgVisible: false
                    width: height
                    iconSource: "Images/icons/conversations.svg"
                    iconColor: activePage == pageIndex ? Style.colorOnPrimary : Style.inactivePage

                    onClicked: {
                        activePage = pageIndex
                    }
                }

                IconButton {
                    property int pageIndex: 2

                    id: callsBtn
                    anchors.top: parent.top
                    anchors.bottom: parent.bottom
                    anchors.topMargin: 0
                    anchors.bottomMargin: 0
                    bgVisible: false
                    width: height
                    iconSource: "Images/icons/dialer.svg"
                    iconColor: activePage == pageIndex ? Style.colorOnPrimary : Style.inactivePage

                    onClicked: {
                        activePage = pageIndex
                    }
                }
            }

            Row {
                id: syncRow
                anchors.right: parent.right
                anchors.top: parent.top
                anchors.bottom: parent.bottom
                anchors.topMargin: 5
                anchors.bottomMargin: 5
                anchors.rightMargin: 15

                IconButton {
                    id: sendFileBtn
                    anchors.top: parent.top
                    anchors.bottom: parent.bottom
                    anchors.topMargin: 0
                    anchors.bottomMargin: 0
                    bgVisible: false
                    width: height
                    iconSource: "Images/icons/send_file.svg"
                    iconColor: Style.colorOnPrimary

                    onClicked: {
                        backend.sendFile(phoneSelector.currentText)
                    }
                }

                IconButton {
                    id: syncClipboardBtn
                    anchors.top: parent.top
                    anchors.bottom: parent.bottom
                    anchors.topMargin: 0
                    anchors.bottomMargin: 0
                    bgVisible: false
                    width: height
                    iconSource: "Images/icons/sync_clipboard.svg"
                    iconColor: Style.colorOnPrimary

                    onClicked: {
                        backend.sendClipboard(phoneSelector.currentText)
                    }
                }

                IconButton {
                    id: syncBtn
                    anchors.top: parent.top
                    anchors.bottom: parent.bottom
                    anchors.topMargin: 0
                    anchors.bottomMargin: 0
                    bgVisible: false
                    width: height
                    iconSource: "Images/icons/sync.svg"
                    iconColor: Style.colorOnPrimary

                    Behavior on rotation {
                        PropertyAnimation {
                            id: syncBtnPropertyAnimation
                            duration: 400
                            easing {}
                    }}

                    onClicked: {
                        if (!syncBtnPropertyAnimation.running) {
                            syncBtn.rotation += 360
                        }

                        backend.doSync(phoneSelector.currentText)
                    }
                }
            }


        }
    }

    Connections {
        target: backend

        function onSetPhones(phones) {
            phoneSelector.model = phones
        }
    }
}





/*##^##
Designer {
    D{i:0;formeditorZoom:0.9}
}
##^##*/
