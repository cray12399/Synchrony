import QtQuick 2.15

Rectangle {
    property int startPosition: 0
    property int minimumPositionLeft: 0
    property int minimumPositionRight: 0
    property int maxX: 0

    id: splitter
    width: 10
    x: startPosition
    color: "transparent"
    anchors.top: parent.top
    anchors.bottom: parent.bottom
    anchors.bottomMargin: 0
    anchors.topMargin: 0

    MouseArea {
        id: mouseArea
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        anchors.fill: parent
        anchors.bottomMargin: 0
        anchors.topMargin: 0
        hoverEnabled: true

        onContainsMouseChanged: {
            if (containsMouse) {
                cursorShape = Qt.SplitHCursor
            } else {
                cursorShape = Qt.ArrowCursor
            }
        }

        onPressed: mouse.accepted = false
    }

    DragHandler {
        id: dragHandler
        target: splitter

        xAxis.minimum: minimumPositionLeft
        xAxis.maximum: maxX - minimumPositionRight
    }
}
