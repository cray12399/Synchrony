import QtQuick 2.0
import QtQuick.Controls 2.15
import QtGraphicalEffects 1.0

ComboBox {
    property int listHeight: 300
    property color comboBgColor: "#FFFFFF"
    property color itemBgColor: "#cccccc"
    property color itemTextColor: "#000000"
    property color downArrowColor: "#FF0000"

    id: phoneSelector

    model: ListModel {
        id: model
    }

    background: Rectangle {
        radius: height / 2
        color: comboBgColor
    }

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

    indicator: Image {
        id: downArrow
        height: 15
        width: height
        sourceSize: height
        anchors.verticalCenter: parent.verticalCenter
        anchors.right: parent.right
        anchors.rightMargin: 10
        source: "../Images/icons/down_arrow.svg"
        visible: false
    }

    ColorOverlay {
        z: 1
        anchors.fill: downArrow
        source: downArrow
        color: downArrowColor
        antialiasing: true
        smooth: true
        rotation: popup.visible ? -180 : 0

        Behavior on rotation {
            PropertyAnimation {
                duration: 100
                easing {type: Easing.InExpo}
        }}
    }

    delegate: ItemDelegate {
          id: itemDelegate
          width: phoneSelector.width
          highlighted: false
          padding: 0
          height: phoneSelector.height

          background: Rectangle {
            color: "transparent"
          }

          onPressedChanged: {
              if (pressed) {
                  highlight.visible = true
                  highlight.width = itemDelegate.width
              } else {
                  highlight.visible = false
                  highlight.width = itemDelegate.width * .75
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
              height: parent.height
              width: parent.width * .75
              antialiasing: true
              z: 2

              Behavior on width {
                  PropertyAnimation {
                      duration: animationDuration
                      easing {type: Easing.OutExpo}
              }}
          }

          MouseArea {
              id: delegateMouseArea
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

          contentItem: Rectangle{
              id: itemBG
              width: parent.width
              radius: height / 2
              color: itemBgColor

              Text {
                  id: textItem
                  text: modelData
                  color: itemTextColor
                  elide: Text.ElideRight
                  verticalAlignment: Text.AlignVCenter
                  horizontalAlignment: Text.AlignLeft
                  anchors.verticalCenter: parent.verticalCenter
                  anchors.left: parent.left
                  anchors.right: parent.right
                  anchors.leftMargin: 10
                  anchors.rightMargin: 10
              }
           }
    }

    popup.contentItem: ListView {
        id:listView
        implicitHeight: listHeight
        model: phoneSelector.popup.visible ? phoneSelector.delegateModel : null
        spacing: 10
        clip: true
    }

    popup.background: Rectangle {
        border.width: 0
        color: "transparent"
    }

    popup.verticalPadding: 15
}

/*##^##
Designer {
    D{i:0;autoSize:true;formeditorZoom:0.66;height:480;width:640}
}
##^##*/
