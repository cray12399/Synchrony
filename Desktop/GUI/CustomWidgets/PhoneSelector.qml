import QtQuick 2.0
import QtQuick.Controls 2.15
import QtGraphicalEffects 1.0

Item {
    property int listHeight: 300
    property color comboBgColor: "#FFFFFF"
    property color itemBgColor: "#cccccc"
    property color itemTextColor: "#000000"
    property color downArrowColor: "#FF0000"
    property var model: []
    property int count: model.length
    property int currentIndex: 0
    property string currentText: model[currentIndex][0]
    property bool opened: false

    id: phoneSelector

    Rectangle {
        id: phoneSelectorBG
        anchors.fill: parent
        radius: height / 2
        color: comboBgColor
    }

    MouseArea {
        id: mouseArea
        hoverEnabled: true
        anchors.fill: phoneSelectorBG
        z: 1

        onContainsMouseChanged: {
            if (containsMouse) {
                cursorShape = Qt.PointingHandCursor
            } else {
                cursorShape = Qt.ArrowCursor
            }
        }

        onClicked: opened = !opened

        enabled: phoneSelector.count > 0
    }

    Label {
        id: currentItemText
        anchors.verticalCenter: parent.verticalCenter
        anchors.left: parent.left
        anchors.right: downArrow.left
        anchors.rightMargin: 15
        anchors.leftMargin: 15
        color: "black"
        text: count > 0 ? phoneSelector.currentText : "No Phones Connected..."
        elide: Text.ElideRight
    }

    Image {
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
        rotation: opened ? -180 : 0

        Behavior on rotation {
            PropertyAnimation {
                duration: 100
                easing {type: Easing.InExpo}
            }
        }
    }

    ListView {
        width: parent.width
        anchors.top: parent.bottom
        anchors.topMargin: 15
        anchors.bottomMargin: 15
        anchors.horizontalCenter: parent.horizontalCenter
        height: listHeight
        spacing: 10
        visible: phoneSelector.opened
        clip: true

        model: parent.model

        delegate: ItemDelegate {
            width: parent.width

            background: Rectangle {
                color: "transparent"
            }

            MouseArea {
                  id: delegateMouseArea
                  hoverEnabled: true
                  anchors.fill: itemBG

                  onContainsMouseChanged: {
                      if (containsMouse) {
                          cursorShape = Qt.PointingHandCursor
                      } else {
                          cursorShape = Qt.ArrowCursor
                      }
                  }

                  onPressedChanged: {
                      if (pressed) {
                          highlight.visible = true
                          highlight.width = itemBG.width
                      } else {
                          highlight.visible = false
                          highlight.width = itemBG.width * .75
                          phoneSelector.currentIndex = index
                          phoneSelector.opened = false
                      }
                  }
            }

            Text {
                id: itemText
                text: phoneSelector.model[index][0]
                anchors.left: parent.left
                anchors.leftMargin: 15
                anchors.verticalCenter: itemBG.verticalCenter
                anchors.right: parent.right
                anchors.rightMargin: 15
                z: 1
                color: itemTextColor
            }

            Rectangle {
                id: itemBG
                width: phoneSelector.width
                height: itemText.height * 1.5
                radius: height / 2
                color: itemBgColor
                z: 0
            }

            Rectangle {
                  id: highlight
                  radius: 360
                  anchors.verticalCenter: itemBG.verticalCenter
                  anchors.horizontalCenter: itemBG.horizontalCenter
                  opacity: .5
                  color: "white"
                  visible: false
                  height: itemBG.height
                  width: itemBG.width * .75
                  antialiasing: true
                  z: 2

                  Behavior on width {
                      PropertyAnimation {
                          duration: animationDuration
                          easing {
                              type: Easing.OutExpo
                          }
                      }
                  }
              }
        }
    }


}

/*##^##
Designer {
    D{i:0;autoSize:true;height:480;width:640}
}
##^##*/
