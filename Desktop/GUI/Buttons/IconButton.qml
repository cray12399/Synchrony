import QtQuick 2.0
import QtQuick.Controls 2.15
import QtGraphicalEffects 1.0

RoundButton {
    property url iconSource: "../Images/icons/plus.svg"
    property color bgColor: systemPalette.highlight
    property bool bgVisible: true
    property bool showIconShadow: false
    property color iconColor: "#FFFFFF"
    property color iconColorSelected: "#000000"
    property color indicatorColor: "#000000"
    property color indicatorTextColor: "#ff0000"
    property bool selected: false
    property int animationDuration: 300
    property int numNewContent: 0
    property int iconMargin: 7

    id: iconBtn

    implicitHeight: 100
    implicitWidth: 100

    SystemPalette {
        id: systemPalette
        colorGroup: SystemPalette.Active
    }

    MouseArea {
        id: mouseArea
        hoverEnabled: true
        anchors.fill: parent
        z: 1

        onContainsMouseChanged: {
            if (containsMouse) {
                cursorShape = Qt.PointingHandCursor
            } else {
                cursorShape = Qt.ArrowCursor
            }
        }

        onPressed: mouse.accepted = false
    }

    onPressedChanged: {
        if (pressed) {
            highlight.visible = true
            highlight.height = iconBtn.height
            iconShadow.radius = 15
            colorBGShadow.radius = 15
        } else {
            highlight.visible = false
            highlight.height = iconBtn.height * .3
            iconShadow.radius = 10
            colorBGShadow.radius = 10
        }
    }

    Label {
        id: newContentIndicator
        visible: numNewContent > 0
        anchors.right: parent.right
        anchors.rightMargin: numNewContent > 99 ? -5 : 0
        anchors.top: parent.top
        horizontalAlignment: HorizontalAlignment.CENTER
        text: numNewContent < 100 ? numNewContent : "99+"
        font.bold: true
        font.pixelSize: 12
        color: indicatorTextColor
        leftPadding: 3
        rightPadding: 3

        background: Rectangle {
            color: indicatorColor
            radius: 360
        }
    }

    Rectangle {
        id: highlight
        radius: 360
        anchors.verticalCenter: parent.verticalCenter
        anchors.horizontalCenter: parent.horizontalCenter
        opacity: .5
        color: "white"
        visible: false
        height: iconBtn.height * .3
        width: height
        antialiasing: true
        z: 2

        Behavior on height {
            PropertyAnimation {
                duration: animationDuration
                easing {type: Easing.OutExpo}
        }}
    }

    background: Rectangle{
        id: btnBG
        color: "transparent"
        z: 0

        Rectangle {
            id: colorBG
            anchors.fill: parent
            radius: 360
            visible: bgVisible
            color: bgColor
            antialiasing: true
        }

        Image {
            id: btnIcon
            source: iconSource
            anchors.verticalCenter: parent.verticalCenter
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.fill: parent
            anchors.margins: iconMargin
            sourceSize.height: height
            sourceSize.width: width
            fillMode: Image.PreserveAspectFit
            visible: false
            smooth: true
            antialiasing: true
        }

        ColorOverlay {
            z: 1
            anchors.fill: btnIcon
            source: btnIcon
            color: selected ? iconColorSelected : iconColor
            antialiasing: true
            smooth: true
        }

        DropShadow {
            id: iconShadow
            anchors.fill: btnIcon
            radius: 10
            samples: 9
            color: "#bb000000"
            source: btnIcon
            visible: !bgVisible && showIconShadow

            Behavior on radius {
                PropertyAnimation {
                    duration: animationDuration
                    easing {type: Easing.OutExpo}
            }}
        }

        DropShadow {
            id: colorBGShadow
            anchors.fill: colorBG
            radius: 10
            samples: 20
            color: "#bb000000"
            source: colorBG
            visible: bgVisible

            Behavior on radius {
                PropertyAnimation {
                    duration: animationDuration
                    easing {type: Easing.OutExpo}
            }}
        }
    }
}







/*##^##
Designer {
    D{i:0;autoSize:true;height:480;width:640}
}
##^##*/
