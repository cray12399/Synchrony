import QtQuick 2.0
import QtQuick.Controls 2.15
import "../Buttons/"
import "../"

Rectangle {
    property string placeholderText: "Edit Text"
    property string currentText: textInput.text
    property color bgColor: "#FFFFFF"
    property color textColor: "#000000"

    color: bgColor

    MouseArea {
        anchors.fill: parent
        hoverEnabled: true
        z: 3

        onContainsMouseChanged: {
            if (containsMouse) {
                cursorShape = Qt.IBeamCursor
            } else {
                cursorShape = Qt.ArrowCursor
            }
        }

        onPressed: mouse.accepted = false
    }

    Label {
        text: placeholderText
        anchors.fill: parent
        anchors.leftMargin: 10
        anchors.rightMargin: 10
        color: textColor
        visible: !textInput.focus && textInput.text == ""
        z: 1
    }

    IconButton {
        id: clearTextBtn
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        anchors.margins: 5
        iconMargin: 0
        width: height
        iconSource: "../Images/icons/clear.svg"
        bgVisible: false
        iconColor: Style.primaryVariant
        z: 4
        visible: textInput.text != ""

        onClicked: textInput.text = ""
    }

    TextInput {
        id: textInput
        anchors.left: parent.left
        anchors.right: clearTextBtn
        anchors.leftMargin: 10
        anchors.rightMargin: 10
        anchors.verticalCenter: parent.verticalCenter
        width: parent.width
        clip: true
        color: textColor
        z: 0
    }
}

/*##^##
Designer {
    D{i:0;autoSize:true;height:480;width:640}
}
##^##*/
