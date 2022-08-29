/**
 *  Roomba Automation
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
 
String getVersionNum() { return "11.1.0" }
String getVersionLabel() { return "Roomba Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library
#include mikee385.time-library

definition(
    name: "Roomba Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Starts and stops the Roomba based on a schedule and presence.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/roomba-automation.groovy"
)

preferences {
    page(name: "settings", title: "Roomba Automation", install: true, uninstall: true) {
        section {
            input "roomba", "device.Roomba", title: "Roomba", multiple: false, required: true
            input "roombaStartTime", "time", title: "Start Time", required: true
            input "minimumMinutes", "number", title: "Minimum Duration (in minutes)", required: true
            input "roombaResetTime", "time", title: "Reset Time", required: true
        }
        section("Pause") {
            input "pauseButton", "capability.pushableButton", title: "Pause/Resume Button", multiple: false, required: false
            input "pauseDoors", "capability.contactSensor", title: "Pause when Opened", multiple: true, required: false
        }
        section {
            input "people", "capability.presenceSensor", title: "People", multiple: true, required: false
        }
        section {
            input "deviceMonitor", "device.DeviceMonitor", title: "Device Monitor", multiple: false, required: true
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    // Create state
    if (state.startTime == null) {
        state.startTime = now()
    }
    if (state.endTime == null) {
        state.endTime = now()
    }
    if (state.durationMinutes == null) {
        state.durationMinutes = 0
    }
    
    // Clean Status
    for (person in people) {
        subscribe(person, "presence", personHandler)
    }
    subscribe(roomba, "rechrgTm", rechrgTmHandler)
    if (pauseButton) {
        subscribe(pauseButton, "pushed", buttonPushedHandler)
        subscribe(pauseButton, "doubleTapped", buttonDoubleTappedHandler)
        subscribe(pauseButton, "held", buttonHeldHandler)
    }
    for (pauseDoor in pauseDoors) {
        subscribe(pauseDoor, "contact.open", doorOpenedHandler)
    }

    // Runtime Tracking
    subscribe(roomba, "phase", phaseHandler)
    subscribe(roomba, "cycle", cycleHandler)
    
    // Daily Triggers
    def currentTime = new Date()
    
    def startToday = timeToday(roombaStartTime)
    schedule("$currentTime.seconds $startToday.minutes $startToday.hours * * ? *", dailyStart)
    
    def resetToday = timeToday(roombaResetTime)
    schedule("$currentTime.seconds $resetToday.minutes $resetToday.hours * * ? *", dailyReset)
    
    // Device Checks
    initializeDeviceChecks()
    
    // Initialize state
    def deviceRunning = roomba.currentValue("phase") == "run"
    def stateRunning = state.endTime < state.startTime
    
    if (deviceRunning && !stateRunning) {
        state.startTime = now()
    } else if (!deviceRunning && stateRunning) {
        state.endTime = now()
        state.durationMinutes += (state.endTime - state.startTime)/1000.0/60.0
    }
}

def personHandler(evt) {
    logDebug("personHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "present") {
        cancelCycle()
    } else {
        startCycle()
    }
}

def rechrgTmHandler(evt) {
    logDebug("rechrgTmHandler: ${evt.device} changed to ${evt.value}")

    if (roomba.currentValue("cycle") != "none") {
        rechrgTm = roomba.currentValue("rechrgTm").toLong()*1000
        if (rechrgTm > 0) {
            initialTm = rechrgTm - 15*1000
            if (initialTm > now()) {
                runOnce(new Date(initialTm), checkCycle)
            }
            
            finalTm = rechrgTm + 5*1000
            if (finalTm > now()) {
                runOnce(new Date(finalTm), checkCycle)
            } else {
                checkCycle()
            }
        }
    }
}

def buttonPushedHandler(evt) {
    logDebug("buttonPushedHandler: ${evt.device} changed to ${evt.value}")

    roomba.pause()
}

def buttonDoubleTappedHandler(evt) {
    logDebug("buttonDoubleTappedHandler: ${evt.device} changed to ${evt.value}")

    roomba.resume()
}

def buttonHeldHandler(evt) {
    logDebug("buttonHeldHandler: ${evt.device} changed to ${evt.value}")

    roomba.start()
}

def doorOpenedHandler(evt) {
    logDebug("doorOpenedHandler: ${evt.device} changed to ${evt.value}")

    roomba.pause()
}

def startCycle() {
    def everyoneAway = true
    for (person in people) {
        if (person.currentValue("presence") == "present") {
            everyoneAway = false
            break
        }
    }

     if (currentTimeIsBetween(roombaStartTime, location.sunset) && everyoneAway) {
        if (roomba.currentValue("consumableStatus") == "maintenance_required") {
            personToNotify.deviceNotification("$roomba could not start because the bin is full!")
        } else if (roomba.currentValue("consumableStatus") == "missing") {
            personToNotify.deviceNotification("$roomba could not start because the bin is missing!")
        } else if (roomba.currentValue("consumableStatus") != "good") {
            personToNotify.deviceNotification("$roomba could not start because of an unknown error with the bin!")
        } else if (roomba.currentValue("battery") <= 10) {
            personToNotify.deviceNotification("$roomba could not start because the battery is dead!")
        } else if (roomba.currentValue("cycle") == "none" && state.durationMinutes < minimumMinutes) {
            roomba.start()
        } else if (roomba.currentValue("cycle") != "none" && roomba.currentValue("phase") == "stop") {
            roomba.resume()
        }
    }
}

def pauseCycle() {
    if (roomba.currentValue("cycle") != "none" && roomba.currentValue("phase") == "run") {
        roomba.pause()
    }
}

def cancelCycle() {
    if (roomba.currentValue("cycle") != "none" && roomba.currentValue("phase") == "run") {
        unschedule("checkCycle")
        roomba.dock()
    }
}

def checkCycle() {
    def anyoneHome = false
    for (person in people) {
        if (person.currentValue("presence") == "present") {
            anyoneHome = true
            break
        }
    }
    
    if (anyoneHome) {
        unschedule("checkCycle")
        roomba.stop()
    }
}

def phaseHandler(evt) {
    logDebug("phaseHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "run") {
        state.startTime = now()
    } else if (state.endTime < state.startTime) { // should only be true while Roomba is running
        state.endTime = now()
        state.durationMinutes += (state.endTime - state.startTime)/1000.0/60.0
    }
}

def cycleHandler(evt) {
    logDebug("cycleHandler: ${evt.device} changed to ${evt.value}")
    
    unschedule("checkCycle")
    
    if (evt.value == "none") {
        if (state.durationMinutes >= 0.5) {
            personToNotify.deviceNotification("$roomba has cleaned for ${Math.round(state.durationMinutes)} minutes today!")
        }
    } else {
        if (location.mode == "Away") {
            personToNotify.deviceNotification("$roomba has started!")
        }
    }
}

def dailyStart() {
    logDebug("dailyStart")
    
    startCycle()
}

def dailyReset() {
    logDebug("dailyReset")
    
    state.durationMinutes = 0
}