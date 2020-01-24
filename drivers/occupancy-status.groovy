/**
 *  Occupancy Status Device Handler
 *
 *  Copyright 2019 Michael Pierce
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
metadata {
    definition (name: "Occupancy Status", namespace: "mikee385", author: "Michael Pierce", importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/occupancy-status.groovy") {
        capability "Actuator"
        capability "Sensor"

        attribute "state", "enum", ["occupied", "vacant", "checking", "blind"]

        command "occupied"
        command "vacant"
        command "checking"
    }
    
    preferences {
        input "checkingPeriod", "number", title: "Checking Period in Seconds\nHow long (in seconds) should zone stay in the 'checking' state (including the 'blind' period) before transitioning to the 'vacant' state?", range: "0..*", defaultValue: 240, required: true, displayDuringSetup: false
        input "blindPeriod", "number", title: "Blind Period in Seconds\nHow long (in seconds) at the beginning of the 'checking' state should zone ignore certain events?", range: "0..*", defaultValue: 0, required: true, displayDuringSetup: false
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
    if (!device.currentValue("state")) {
        vacant()
    }
}

def occupied() {
    setStateToOccupied()
}

def vacant() {
    setStateToVacant()
}

def checking() {
    if (blindPeriod > 0) {
        setStateToBlind()
        runIn(blindPeriod, resumeFromBlind)
    } else if (checkingPeriod > 0) {
        setStateToChecking()
        runIn(checkingPeriod, resumeFromChecking)
    } else {
        setStateToVacant()
    }
}

def resumeFromBlind() {
    def remainingTime = checkingPeriod - blindPeriod
    if (remainingTime > 0) {
        setStateToChecking()
        runIn(remainingTime, resumeFromChecking)
    } else {
        setStateToVacant()
    }
}

def resumeFromChecking() {
    setStateToVacant()
}

private def setStateToOccupied() {
    sendEvent(name: "state", value: "occupied", descriptionText: "$device.displayName changed to occupied", displayed: true)    
    unschedule()
}

private def setStateToVacant() {
    sendEvent(name: "state", value: "vacant", descriptionText: "$device.displayName changed to vacant", displayed: true)    
    unschedule()
}

private def setStateToChecking() {
    sendEvent(name: "state", value: "checking", descriptionText: "$device.displayName changed to checking", displayed: true)
}

private def setStateToBlind() {
    sendEvent(name: "state", value: "blind", descriptionText: "$device.displayName changed to blind", displayed: true)
}