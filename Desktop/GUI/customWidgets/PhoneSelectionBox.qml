import QtQuick 2.12
import QtQuick.Controls 2.12

ComboBox {
    id: phoneSelectionBox
    model: ["1", "2", "3"]

    MouseArea {
        id: mouseArea
        hoverEnabled: true
        anchors.fill: parent
        onContainsMouseChanged: {
            if (containsMouse) {
                cursorShape = Qt.PointingHandCursor
            } else {
                cursorShape = Qt.ArrowCursor
            }
        }

        onPressed: mouse.accepted = false
    }

    background: Rectangle {
    }

    indicator: Image {
        id: indicator
        height: phoneSelectionBox.height * .5
        width: height
        sourceSize.height: height
        sourceSize.width: width
        anchors.verticalCenter: parent.verticalCenter
        anchors.right: parent.right
        source: "../images/icons/downarrow.svg"
        anchors.rightMargin: 10
        smooth: true

        transform: Rotation {
            origin.x: 6
            origin.y: 6
            angle: popup.visible ? 180 : 360
        }
    }
}
/*##^##
Designer {
    D{i:0;autoSize:true;height:25;width:300}
}
##^##*/
