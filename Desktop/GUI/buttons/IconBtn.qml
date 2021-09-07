import QtQuick 2.15
import QtQuick.Controls 2.15
import QtGraphicalEffects 1.15
import "../customWidgets"

Button{
    id: iconBtn
    property url btnIconSource: ""
    property color iconColorDefault: "#ffffff"
    property int btnNumNewContent: 0
    property string pageName: ""
    property int iconBig: iconBtn.height * .8
    property int iconSmall: iconBtn.height * .6
    property int iconSize: iconSmall
    property color iconColor: "white"
    property bool doAnimation: true

    MouseArea {
        id: mouseArea
        hoverEnabled: true
        anchors.fill: parent
        onContainsMouseChanged: {
            if (containsMouse) {
                cursorShape = Qt.PointingHandCursor
                iconSize = doAnimation ? iconBig : iconSize
            } else {
                cursorShape = Qt.ArrowCursor
                iconSize = doAnimation ? iconSmall : iconSize
            }
        }

        onPressed: mouse.accepted = false
    }

    onPressedChanged:  {
        if (pressed) {
            iconSize = doAnimation ? iconSmall : iconSize
        } else {
            iconSize = doAnimation ? iconBig : iconSize
        }
    }

    implicitWidth: 40
    implicitHeight: 40

    background: Rectangle{
        id: btnBG
        color: "transparent"

        Image {
            id: btnIcon
            source: btnIconSource
            anchors.verticalCenter: parent.verticalCenter
            anchors.horizontalCenter: parent.horizontalCenter
            height: iconSize
            width: iconSize
            sourceSize.height: height
            sourceSize.width: width
            fillMode: Image.PreserveAspectFit
            visible: false
            smooth: true
        }

        DropShadow {
            anchors.fill: btnIcon
            horizontalOffset: 2
            verticalOffset: 2
            radius: 4.0
            samples: 17
            color: "#5f000000"
            source: btnIcon
            visible: iconBtn.hovered && doAnimation
        }

        ColorOverlay {
            anchors.fill: btnIcon
            source: btnIcon
            color: iconColor
            antialiasing: true
        }
    }
}

/*##^##
Designer {
    D{i:0;autoSize:true;height:480;width:640}
}
##^##*/
