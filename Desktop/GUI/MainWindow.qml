import QtQuick 2.14
import QtQuick.Window 2.14
import QtQuick.Controls 2.15
import QtQuick.Layouts 1.11
import Qt.labs.settings 1.0
import QtQuick.Dialogs 1.3
import QtGraphicalEffects 1.0

import "."
import "Buttons"
import "CustomWidgets"
import "Pages"

Window {
    property int activePage: 1
    property var phoneData: backend.phoneDataModel
    property bool phoneAppConnection: phoneData.get(phoneSelector.currentIndex).btSocketConnected

    id: mainWindow
    visible: true
    width: 600
    height: 400
    title: "Synchrony"

    Connections {
        target: phoneData

        function onDataChanged() {
            if (phoneData.rowCount() > 0) {
                phoneAppConnection = phoneData.get(phoneSelector.currentIndex).btSocketConnected
            }
        }

        function onRowsRemoved() {
            phoneAppConnection = phoneData.rowCount() > 0
        }
    }


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
        spacing: 0

        Item {
            Layout.minimumWidth: 40
            Layout.preferredHeight: 45
            Layout.fillWidth: true
            Layout.alignment: Qt.AlignLeft | Qt.AlignTop
            z: 2

            Rectangle {
                id: topBar
                anchors.fill: parent
                color: Style.primary

                PhoneSelector {
                    id: phoneSelector
                    width: topBar.width * .3
                    anchors.right: syncRow.left
                    anchors.top: parent.top
                    anchors.bottom: parent.bottom
                    anchors.rightMargin: 10
                    anchors.topMargin: 10
                    anchors.bottomMargin: 10
                    listHeight: (mainWindow.height * .9) - topBar.height
                    itemBgColor: Style.primary
                    itemTextColor: Style.colorOnPrimary
                    comboBgColor: count > 0 ? Style.colorOnPrimary : Style.inactive
                    downArrowColor: Style.primaryVariant
                    model: mainWindow.phoneData

                    onCurrentIndexChanged: {
                        phoneAppConnection = phoneData.get(phoneSelector.currentIndex).btSocketConnected
                    }
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
                        iconColor: activePage == pageIndex ? Style.colorOnPrimary : Style.inactive

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
                        iconColor: activePage == pageIndex ? Style.colorOnPrimary : Style.inactive
                        indicatorColor: Style.secondary
                        indicatorTextColor: Style.colorOnSecondary
                        numNewContent: 0

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
                        iconColor: activePage == pageIndex ? Style.colorOnPrimary : Style.inactive
                        indicatorColor: Style.secondary
                        indicatorTextColor: Style.colorOnSecondary
                        numNewContent: 0

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
                        iconMargin: 6
                        iconSource: "Images/icons/send_file.svg"
                        iconColor: phoneSelector.count > 0 ? Style.colorOnPrimary : Style.inactive
                        enabled: phoneSelector.count > 0

                        onClicked: {
                            backend.sendFile(phoneData.get(phoneSelector.currentIndex))
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
                        iconColor: phoneAppConnection ? Style.colorOnPrimary : Style.inactive
                        enabled: phoneAppConnection

                        onClicked: {
                            backend.sendClipboard(phoneData.get(phoneSelector.currentIndex))
                        }
                    }

                    IconButton {
                        id: syncBtn
                        anchors.top: parent.top
                        anchors.bottom: parent.bottom
                        anchors.topMargin: 0
                        anchors.bottomMargin: 0
                        bgVisible: false
                        iconMargin: 4
                        width: height
                        iconSource: "Images/icons/sync.svg"
                        iconColor: phoneAppConnection > 0 ? Style.colorOnPrimary : Style.inactive
                        enabled: phoneAppConnection

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

                            backend.doSync(phoneData.get(phoneSelector.currentIndex))
                        }
                    }
                }


            }

            CustomDropShadow {
                source: topBar
            }

        }

        StackLayout {
            id: stackLayout
            width: 100
            height: 100
            currentIndex: propertyFunctions.splitViewActivePage()
            z: 0

            SettingsPage {

            }

            ConversationsPage {
                selectedPhone: mainWindow.phoneData[phoneSelector.currentIndex]
            }

            CallsPage {
                selectedPhone: mainWindow.phoneData[phoneSelector.currentIndex]
            }

            Rectangle {
                id: disconnectedPage
                anchors.fill: parent
                color: Style.background

                Rectangle {
                    id: rectangle
                    color: "transparent"
                    anchors.top: parent.top
                    anchors.bottom: parent.bottom
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.topMargin: 150
                    anchors.bottomMargin: 150
                    width:height

                    Image {
                        id: disconnectedImg
                        source: "Images/icons/disconnected.svg"
                        anchors.top: parent.top
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.bottom: disconnectedText.top
                        sourceSize.height: height
                        sourceSize.width: width
                        fillMode: Image.PreserveAspectFit
                        visible: false
                        smooth: true
                        antialiasing: true
                        width: height
                    }

                    ColorOverlay {
                        z: 1
                        source: disconnectedImg
                        anchors.bottomMargin: 0
                        color: Style.primary
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.top: parent.top
                        anchors.bottom: disconnectedText.top
                        antialiasing: true
                        smooth: true
                    }

                    Label {
                        id: disconnectedText
                        anchors.horizontalCenter: parent.horizontalCenter
                        anchors.bottom: parent.bottom
                        text: "No App Connection..."
                        color: Style.primary
                        font.bold: true
                        font.pixelSize: 20
                    }
                }
            }
        }
    }

    QtObject {
        id: propertyFunctions

        function splitViewActivePage() {
            if (phoneAppConnection) {
                return activePage
            } else if (activePage == 0) {
                return 0
            } else {
                return 3
            }
        }
    }

    Connections {
        target: backend
    }
}








