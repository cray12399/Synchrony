import QtQuick 2.0

Rectangle {
    property int numNewContent: 1

    id: notificationCircle
    color: "red"
    radius: height / 2
    anchors.right: parent.right
    anchors.rightMargin: 0
    antialiasing: true
    z: 1
    width: if (numNewContentTxt.text.length <= 1) {
                numNewContentTxt.font.pixelSize * 1.7
           } else {
                numNewContentTxt.text.length * numNewContentTxt.font.pixelSize * .8
           }

    Text {
        id: numNewContentTxt
        width: parent.width
        text: numNewContent < 100 ? numNewContent : "99+"
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        font.pixelSize: notificationCircle.height * .6
        horizontalAlignment: Text.AlignHCenter
        verticalAlignment: Text.AlignVCenter
        anchors.topMargin: 0
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottomMargin: 0
        color: "white"
        font.bold: true
    }
}

/*##^##
Designer {
    D{i:0;formeditorZoom:3;height:70;width:100}
}
##^##*/
