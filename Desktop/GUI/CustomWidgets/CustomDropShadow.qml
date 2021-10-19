import QtQuick 2.0
import QtGraphicalEffects 1.15

DropShadow {
    id: dropShadow
    anchors.fill: source
    verticalOffset: 3
    radius: 8.0
    samples: 17
    color: "#80000000"
    source: undefined
}
