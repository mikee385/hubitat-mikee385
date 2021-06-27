/**
 *  Echo Glow Device Driver
 *
 *  Copyright 2021 Michael Pierce
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "Echo Glow Device, version ${getVersionNum()} on ${getPlatform()}" }

 metadata {
    definition (
		name: "Echo Glow Device", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/echo-glow-device.groovy"
	) {
        capability "Actuator"
        
        command "green"
        command "orange"
        command "purple"
        command "off"
    }
}

def installed() {
    initialize()
}

def uninstalled() {
    for (device in getChildDevices()) {
        deleteChildDevice(device.deviceNetworkId)
    }
}

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    def green = childDevice("Green")
    def orange = childDevice("Orange")
    def purple = childDevice("Purple")
    def off = childDevice("Off")
}

def childDevice(color) {
    def childID = "glow:${device.getId()}:$color"
    def child = getChildDevice(childID)
    if (!child) {
        def childName = "${device.label ?: device.name}"
        if (childName == "Glow Downstairs") {
            childName = "GD"
        } else if (childName == "Glow Upstairs") {
            childName = "GU"
        }
        child = addChildDevice("mikee385", "Alexa Trigger", childID, [label: "$childName $color", isComponent: true])
    }
    return child
}

def green() {
    childDevice("Green").trigger()
}

def orange() {
    childDevice("Orange").trigger()
}

def purple() {
    childDevice("Purple").trigger()
}

def off() {
    childDevice("Off").trigger()
}