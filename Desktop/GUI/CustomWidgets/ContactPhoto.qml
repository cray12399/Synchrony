import QtQuick 2.0
import QtGraphicalEffects 1.0

import "../"

Item {
    property url photoSource: ""

    id: contactPhoto
    width: height

    Rectangle {
        id: placeHolderImage
        anchors.fill: parent
        radius: 360
        color: Style.primary
        visible: photoSource == ""

        Image {
            id: contactIcon
            source: "../Images/icons/contact.svg"
            anchors.fill: parent
            anchors.margins: 5
            sourceSize.height: height
            sourceSize.width: width
            fillMode: Image.PreserveAspectFit
            visible: false
            smooth: true
            antialiasing: true
        }

        ColorOverlay {
            z: 1
            anchors.fill: contactIcon
            source: contactIcon
            color: Style.colorOnPrimary
            antialiasing: true
            smooth: true
        }
    }

    Image {
        id: contactImage
        source: photoSource
        anchors.fill: parent
        fillMode: Image.PreserveAspectCrop
        visible: true
        smooth: true
        antialiasing: true
        layer.enabled: true
        layer.effect: OpacityMask {
            maskSource: mask2
        }
    }

    Rectangle {
        id: mask2
        anchors.fill: parent
        radius: 360
        visible: false
    }
}

/*##^##
Designer {
    D{i:0;autoSize:true;height:480;width:640}
}
##^##*/
