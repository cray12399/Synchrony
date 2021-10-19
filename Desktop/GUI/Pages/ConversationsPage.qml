import QtQuick 2.0
import QtQuick.Controls 2.15
import QtGraphicalEffects 1.0

import "../"
import "../CustomWidgets"
import QtQuick.Layouts 1.11

Rectangle {
    property var selectedPhone
    property var conversationsListModel: [["John", "Hello dude"], ["Mary", "I just picked up the groceries"], ["Zack", "The quick brown fox jumps over the lazy dog"]]
    property int conversationIndex
    property var selectedConversation: conversationsListModel[conversationIndex]

    anchors.fill: parent
    color: Style.background

    SplitView {
        id: splitView
        anchors.fill: parent

        handle: Item {
            id: splitterHandle
            implicitWidth: 5
        }

        Rectangle {
            id: conversationsListPanel
            SplitView.preferredWidth: 300
            SplitView.minimumWidth: 150
            color: Style.surface

            ColumnLayout {
                id: columnLayout
                spacing: 0
                anchors.fill: parent

                Item {
                    height: 50
                    Layout.alignment: Qt.AlignLeft | Qt.AlignTop
                    Layout.fillWidth: true

                    Rectangle {
                        id: conversationSearchPanel
                        color: Style.primaryVariant
                        anchors.fill: parent

                        LineEdit {
                            id: conversationsSearchBar
                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.verticalCenter: parent.verticalCenter
                            anchors.leftMargin: 10
                            anchors.rightMargin: 10
                            height: 20
                            radius: height / 2
                            placeholderText: "Search Conversations..."

                        }
                    }

                    CustomDropShadow {
                        source: conversationSearchPanel
                    }
                }

                ListView {
                    id: conversationListView
                    Layout.fillHeight: true
                    Layout.fillWidth: true
                    clip: true
                    model: conversationsListModel
                    delegate: Rectangle {
                        id: conversationListDelegate
                        property bool selected: conversationIndex == index
                        color: selected ? Style.secondary : "transparent"
                        anchors.right: parent.right
                        anchors.left: parent.left
                        height: textContainer.allText.includes(conversationsSearchBar.currentText.toLowerCase()) ? 60 : 0
                        visible: textContainer.allText.includes(conversationsSearchBar.currentText.toLowerCase())

                        MouseArea {
                            hoverEnabled: true
                            anchors.fill: parent
                            z:1

                            onContainsMouseChanged: {
                                if (containsMouse) {
                                    cursorShape = Qt.PointingHandCursor
                                    imageContainer.anchors.leftMargin = 20
                                } else {
                                    cursorShape = Qt.ArrowCursor
                                    imageContainer.anchors.leftMargin = 10
                                }
                            }

                            onPressedChanged: {
                                if (pressed) {
                                    highlight.visible = true
                                    highlight.width = parent.width
                                    forceActiveFocus()
                                } else {
                                    conversationIndex = index
                                    highlight.visible = false
                                    highlight.width = parent.width * .5
                                }
                            }
                        }

                        Rectangle {
                            id: highlight
                            anchors.top: parent.top
                            anchors.bottom: parent.bottom
                            anchors.horizontalCenter: parent.horizontalCenter
                            opacity: .5
                            color: "white"
                            visible: false
                            width: parent.width * .5
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

                        Item {
                            id: imageContainer
                            anchors.left: parent.left
                            anchors.top: parent.top
                            anchors.bottom: parent.bottom
                            anchors.topMargin: 10
                            anchors.bottomMargin: 10
                            anchors.leftMargin: 10
                            width: height

                            Rectangle {
                                id: placeHolderImage
                                anchors.fill: parent
                                radius: 360
                                color: Style.primary
                                visible: contactImage.source == ""

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
                                source: ""
                                anchors.fill: parent
                                fillMode: Image.PreserveAspectCrop
                                visible: true
                                smooth: true
                                antialiasing: true
                                layer.enabled: true
                                layer.effect: OpacityMask {
                                    maskSource: mask
                                }
                            }

                            Rectangle {
                                id: mask
                                anchors.fill: parent
                                radius: 360
                                visible: false
                            }
                        }

                        Item {
                            id: textContainer
                            anchors.left: imageContainer.right
                            anchors.right: parent.right
                            height: contactNameText.height + lastMessageText.height
                            anchors.verticalCenter: imageContainer.verticalCenter
                            anchors.margins: 10
                            property string allText: (contactNameText.text + lastMessageText.text).toLowerCase()
                            property color textColor: conversationListDelegate.selected ? Style.colorOnSecondary : Style.colorOnSurface

                            Label {
                                id: contactNameText
                                anchors.right: parent.right
                                anchors.left: parent.left
                                text: conversationListView.model[index][0]
                                font.bold: true
                                color: parent.textColor
                                font.pixelSize: 15
                                elide: Text.ElideRight
                            }

                            Label {
                                id: lastMessageText
                                text: conversationListView.model[index][1]
                                anchors.top: contactNameText.bottom
                                anchors.right: parent.right
                                anchors.left: parent.left
                                color: parent.textColor
                                elide: Text.ElideRight
                            }
                        }
                    }
                }

            }
        }

        Rectangle {
            id: currentConversationPanel
            SplitView.preferredWidth: 500
            SplitView.minimumWidth: 300
            color: "transparent"
        }
    }
}

/*##^##
Designer {
    D{i:0;autoSize:true;height:480;width:640}
}
##^##*/
