/**
 *  Occupancy Status Device Handler
 *
 *  Copyright 2022 Michael Pierce
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
 
String getVersionNum() { return "6.0.0" }
String getVersionLabel() { return "Occupancy Status, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Occupancy Status", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/occupancy-status.groovy"
	) {
        capability "Actuator"
        capability "Sensor"

        attribute "occupancy", "enum", ["occupied", "vacant", "checking", "blind"]

        command "occupied"
        command "vacant"
        command "checking"
    }
    
    preferences {
        input "checkingPeriod", "number", title: "Checking Period in Seconds\nHow long (in seconds) should zone stay in the 'checking' status (including the 'blind' period) before transitioning to the 'vacant' status?", range: "0..*", defaultValue: 240, required: true, displayDuringSetup: false
        input "blindPeriod", "number", title: "Blind Period in Seconds\nHow long (in seconds) at the beginning of the 'checking' status should zone ignore certain events?", range: "0..*", defaultValue: 0, required: true, displayDuringSetup: false
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
    def occupiedButton = childDevice("Occupied")
    def checkingButton = childDevice("Checking")
    def vacantButton = childDevice("Vacant")

    if (!device.currentValue("occupancy")) {
        vacant()
    }
}

def childDevice(name) {
    def childID = "appliance:${device.getId()}:$name"
    def child = getChildDevice(childID)
    if (!child) {
        def childName = "${device.label ?: device.name}"
        child = addChildDevice("mikee385", "Child Button", childID, [label: "$childName $name", isComponent: true])
        child.setCommand(name)
    }
    return child
}

def runCommand(name) {
    if (name == "Occupied") {
        occupied()
    } else if (name == "Checking") {
        checking()
    } else if (name == "Vacant") {
        vacant()
    } else {
        log.error "Unknown command name: $name"
    }
}

def occupied() {
    setStatusToOccupied()
}

def vacant() {
    setStatusToVacant()
}

def checking() {
    if (blindPeriod > 0) {
        setStatusToBlind()
        runIn(blindPeriod, resumeFromBlind)
    } else if (checkingPeriod > 0) {
        setStatusToChecking()
        runIn(checkingPeriod, resumeFromChecking)
    } else {
        setStatusToVacant()
    }
}

def resumeFromBlind() {
    def remainingTime = checkingPeriod - blindPeriod
    if (remainingTime > 0) {
        setStatusToChecking()
        runIn(remainingTime, resumeFromChecking)
    } else {
        setStatusToVacant()
    }
}

def resumeFromChecking() {
    setStatusToVacant()
}

private def setStatusToOccupied() {
    sendEvent(name: "occupancy", value: "occupied")
    unschedule()
}

private def setStatusToVacant() {
    sendEvent(name: "occupancy", value: "vacant")
    unschedule()
}

private def setStatusToChecking() {
    sendEvent(name: "occupancy", value: "checking")
}

private def setStatusToBlind() {
    sendEvent(name: "occupancy", value: "blind")
}