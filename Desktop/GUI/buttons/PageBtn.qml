import QtQuick 2.15
import QtQuick.Controls 2.15
import QtGraphicalEffects 1.15
import "../customWidgets"

Button{
    id: pageBtn
    property url btnIconSource: "../images/icons/settings.svg"
    property int btnNumNewContent: 0
    property bool active: false
    property int page: 0
    property int iconBig: pageBtn.height * .7
    property int iconSmall: pageBtn.height * .5
    property int iconSize: iconSmall
    property int notificationMargin: 3
    property MouseArea btnMouseArea: mouseArea

    MouseArea {
        id: mouseArea
        hoverEnabled: true
        anchors.fill: parent
        onContainsMouseChanged: {
            if (containsMouse) {
                cursorShape = Qt.PointingHandCursor
                iconSize = iconBig
                notificationMargin = -4
            } else {
                cursorShape = Qt.ArrowCursor
                iconSize = iconSmall
                notificationMargin = 3
            }
        }

        onPressed: mouse.accepted = false
    }

    onPressedChanged:  {
        if (pressed) {
            iconSize = iconSmall
            notificationMargin = 3
        } else {
            iconSize = iconBig
            notificationMargin = -4
        }
    }

    background: Rectangle{
        id: btnBG
        color: "transparent"
        anchors.fill: parent

        NewContentNotification {
            id: newContentNotification
            numNewContent: btnNumNewContent
            visible: btnNumNewContent > 0 ? true : false
            height: iconSize * .65
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.rightMargin: notificationMargin
            anchors.topMargin: notificationMargin
        }

        Image {
            id: btnIcon
            source: btnIconSource
            anchors.verticalCenter: parent.verticalCenter
            anchors.horizontalCenter: parent.horizontalCenter
            height: iconSize
            width: iconSize
            sourceSize.height: iconSize
            sourceSize.width: iconSize
            fillMode: Image.PreserveAspectFit
            visible: false
            smooth: true
        }

        ColorOverlay {
            anchors.fill: btnIcon
            source: btnIcon
            color: "#ffffffff"
            antialiasing: true
        }
    }

    DropShadow {
        anchors.fill: btnBG
        horizontalOffset: 2
        verticalOffset: 2
        radius: 4.0
        samples: 17
        color: "#5f000000"
        source: btnBG
        visible: pageBtn.hovered ? true : false
    }

    Rectangle {
        radius: height / 2
        width: pageBtn.height * .1
        height: width
        anchors.bottom: parent.bottom
        anchors.horizontalCenterOffset: 0
        anchors.bottomMargin: 0
        anchors.horizontalCenter: parent.horizontalCenter
        visible: active
    }
}

/*##^##
Designer {
    D{i:0;formeditorZoom:0.5;height:500;width:500}
}
##^##*/
