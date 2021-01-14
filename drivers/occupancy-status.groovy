/**
 *  Occupancy Status Device Handler
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
 
String getVersionNum() { return "3.0.0" }
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

        attribute "status", "enum", ["occupied", "vacant", "checking", "blind"]
        attribute "occupancy", "enum", ["occupied", "unoccupied"]

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

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    if (!device.currentValue("status")) {
        vacant()
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
    sendEvent(name: "status", value: "occupied", descriptionText: "$device.displayName changed to occupied", displayed: true)
    sendEvent(name: "occupancy", value: "occupied", displayed: false)
    unschedule()
}

private def setStatusToVacant() {
    sendEvent(name: "status", value: "vacant", descriptionText: "$device.displayName changed to vacant", displayed: true)
    sendEvent(name: "occupancy", value: "unoccupied", displayed: false)
    unschedule()
}

private def setStatusToChecking() {
    sendEvent(name: "status", value: "checking", descriptionText: "$device.displayName changed to checking", displayed: true)
    sendEvent(name: "occupancy", value: "occupied", displayed: false)
}

private def setStatusToBlind() {
    sendEvent(name: "status", value: "blind", descriptionText: "$device.displayName changed to blind", displayed: true)
    sendEvent(name: "occupancy", value: "occupied", displayed: false)
}