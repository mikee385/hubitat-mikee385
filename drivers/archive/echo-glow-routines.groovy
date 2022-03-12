/**
 *  Echo Glow Routines Driver
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
String getVersionLabel() { return "Echo Glow Routines, version ${getVersionNum()} on ${getPlatform()}" }

 metadata {
    definition (
		name: "Echo Glow Routines", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/echo-glow-routines.groovy"
	) {
        capability "Actuator"
        
        command "bedtimeAnnouncement"
        command "bedtimeNow"
        command "bedtimeSoon"
        command "bedtimeTimer"
        command "wakeUp"
        command "glowsOff"
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
    def bedtimeAnnouncement = childDevice("Bedtime Announcement")
    def bedtimeNow = childDevice("Bedtime Now")
    def bedtimeSoon = childDevice("Bedtime Soon")
    def bedtimeTimer = childDevice("Bedtime Timer")
    def wakeUp = childDevice("Wake Up")
    def glowsOff = childDevice("Glows Off")
}

def childDevice(name, deviceType) {
    def childID = "glow:${device.getId()}:$name"
    def child = getChildDevice(childID)
    if (!child) {
        def deviceType = "Alexa Button"
        if (name == "Bedtime Announcement") {
            deviceType = "Alexa Trigger"
        }
        child = addChildDevice("mikee385", deviceType, childID, [label: name, isComponent: false])
    }
    return child
}

def bedtimeAnnouncement() {
    childDevice("Bedtime Announcement").trigger()
}

def bedtimeNow() {
    childDevice("Bedtime Now").trigger()
}

def bedtimeSoon() {
    childDevice("Bedtime Soon").trigger()
}

def bedtimeTimer() {
    childDevice("Bedtime Timer").trigger()
}

def wakeUp() {
    childDevice("Wake Up").trigger()
}

def glowsOff() {
    childDevice("Glows Off").trigger()
}