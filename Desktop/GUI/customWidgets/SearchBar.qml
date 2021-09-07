import QtQuick 2.15
import "../buttons"
import QtQuick.Controls 2.15

Rectangle {
    property int borderWidth: 0
    property color textColor: "white"
    property color bgColor: "white"
    property color clearTxtBtnColor: "white"
    property color borderColor: "#0054ff"

    id: rectangle
    color: bgColor
    radius: height
    border.color: borderColor
    border.width: borderWidth
    implicitWidth: 300
    implicitHeight: 40

    MouseArea {
        hoverEnabled: true
        anchors.fill: parent
        onContainsMouseChanged: {
            if (containsMouse) {
                cursorShape = Qt.IBeamCursor
                borderWidth = 2
            } else {
                cursorShape = Qt.ArrowCursor
                borderWidth = textEdit.focus ? 2 : 0
            }
        }
    }

    TextInput {
        id: textEdit
        width: 15
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        horizontalAlignment: Text.AlignLeft
        verticalAlignment: Text.AlignVCenter
        anchors.bottomMargin: 0
        anchors.topMargin: 0
        anchors.leftMargin: 0
        anchors.rightMargin: 0
        clip: true
        color: textColor
        leftPadding: 10
        rightPadding: 25

        onFocusChanged: {
            if (focus) {
                borderWidth = 2
            } else {
                borderWidth = 0
            }
        }

        Label {
            id: placeholderTxt
            y: 17
            text: qsTr("Search Conversations...")
            anchors.verticalCenter: parent.verticalCenter
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.rightMargin: 223
            anchors.leftMargin: 10
            visible: textEdit.length == 0 && !textEdit.activeFocus
            color: "#a3a3a3"
        }
    }

    IconBtn {
        id: clearTextBtn
        x: 352
        y: 0
        width: 15
        height: clearTextBtn.width
        anchors.verticalCenter: parent.verticalCenter
        anchors.right: parent.right
        anchors.rightMargin: 7
        btnIconSource: "../images/icons/clear.svg"
        visible: textEdit.length > 0
        iconColor: clearTxtBtnColor


        onClicked: {
            textEdit.text = ""
        }
    }
}

/*##^##
Designer {
    D{i:0;autoSize:true;height:480;width:640}
}
##^##*/
