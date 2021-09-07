import QtQuick 2.0
import QtGraphicalEffects 1.12

Rectangle {
    id: background
    property string contactName: "John Doe"
    property string contactPhotoSource: ""
    property string lastText: "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Hac habitasse platea dictumst quisque sagittis purus sit. Enim nunc faucibus a pellentesque."
    property int lastTextMaxLen: 50
    property bool groupChat: false
    property color contactBGColor: "#4ca6ec"

    implicitWidth: 300
    implicitHeight: implicitWidth * .25

    width: 300

    Item {
        id: contactPhotoHolder
        width: height
        anchors.left: parent.left
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        anchors.leftMargin: 15
        anchors.bottomMargin: 15
        anchors.topMargin: 15

        Rectangle {
            color: contactBGColor
            anchors.fill: parent
            radius: height / 2
            z: 1
            visible: contactPhoto.source == ""

            Image {
                id: contactIcon
                source: !groupChat ? "../images/icons/contact.svg" : "../images/icons/group.svg"
                anchors.fill: parent
                anchors.verticalCenter: parent.verticalCenter
                anchors.horizontalCenter: parent.horizontalCenter
                anchors.margins: 10
                sourceSize.height: height
                sourceSize.width: width
                fillMode: Image.PreserveAspectFit
                smooth: true
            }

            ColorOverlay {
                anchors.fill: contactIcon
                source: contactIcon
                color: "white"
                antialiasing: true
            }
        }

        Image {
            id: contactPhoto
            anchors.fill: parent
            source: contactPhotoSource
            sourceSize.width: width
            sourceSize.height: height
            fillMode: Image.PreserveAspectCrop
            smooth: true
            visible: false
        }

        Rectangle {
            id: mask
            anchors.fill: parent
            radius: height / 2
            smooth: true
            visible: false
        }

        OpacityMask {
            anchors.fill: contactPhoto
            source: contactPhoto
            maskSource: mask
        }
    }

    Rectangle {
        id: contactInfo
        y: 25
        height: 42
        color: "#ffffff"
        anchors.verticalCenter: parent.verticalCenter
        anchors.left: contactPhotoHolder.right
        anchors.right: parent.right
        anchors.rightMargin: 15
        anchors.leftMargin: 15

        Text {
            id: contactNameTxt
            text: contactName
            anchors.left: parent.left
            anchors.top: parent.top
            anchors.leftMargin: 0
            anchors.topMargin: 3
            font.pixelSize: contactPhoto.height * .3
            font.bold: true
        }

        Text {
            id: lastTextPreview
            height: 13
            text: lastText
            anchors.top: contactNameTxt.bottom
            wrapMode: Text.NoWrap
            anchors.leftMargin: 0
            anchors.rightMargin: 0
            anchors.topMargin: 5
            font.pixelSize: contactPhoto.height * .25
            elide: Text.ElideRight
            anchors.left: parent.left
            anchors.right: parent.right
        }
    }
}

/*##^##
Designer {
    D{i:0;formeditorZoom:1.75}
}
##^##*/
