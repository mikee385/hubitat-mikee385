/**
 *  Roomba Automation
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
 
String getVersionNum() { return "5.0.0" }
String getVersionLabel() { return "Roomba Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Roomba Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Starts and stops the Roomba based on a schedule and presence.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/roomba-automation.groovy")

preferences {
    page(name: "settings", title: "Roomba Automation", install: true, uninstall: true) {
        section {
            input "roomba", "device.Roomba", title: "Roomba", multiple: false, required: true
        }
        section {
            input "startTime", "time", title: "Start Time", required: true
            input "minimumMinutes", "number", title: "Minimum Duration (in minutes)", required: true
            input "resetTime", "time", title: "Reset Time", required: true
        }
        section {
            input "person", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
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

    // Roomba Status
    subscribe(location, "mode", modeHandler)
    subscribe(roomba, "phase", phaseHandler)
    subscribe(roomba, "cycle", cycleHandler)
    
    def currentTime = new Date()
    
    def startToday = timeToday(startTime)
    schedule("$currentTime.seconds $startToday.minutes $startToday.hours * * ? *", dailyStart)
    
    def resetToday = timeToday(resetTime)
    schedule("$currentTime.seconds $resetToday.minutes $resetToday.hours * * ? *", dailyReset)
    
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

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def modeHandler(evt) {
    logDebug("modeHandler: ${evt.device} changed to ${evt.value}")

    if (evt.value == "Sleep") {
        if (roomba.currentValue("cycle") != "none") {
            roomba.stop()
        }
    } else if (evt.value == "Away") {
        if (roomba.currentValue("cycle") == "none" && timeOfDayIsBetween(timeToday(startTime), location.sunset, new Date(), location.timeZone) && state.durationMinutes < minimumMinutes) {
            roomba.start()
        }
    } else {
        if (roomba.currentValue("cycle") != "none") {
            roomba.pause()
            roomba.dock()
        }
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
    
    if (evt.value == "none") {
        if (location.mode == "Away") {
            person.deviceNotification("$roomba has started!")
        }
    } else {
        if (state.durationMinutes >= 0.5) {
            person.deviceNotification("$roomba has cleaned for ${Math.round(state.durationMinutes)} minutes today!")
        }
    }
}

def dailyStart() {
    logDebug("dailyStart")
    
    if (location.mode == "Away" && roomba.currentValue("cycle") == "none" && state.durationMinutes < minimumMinutes) {
        roomba.start()
    }
}

def dailyReset() {
    logDebug("dailyReset")
    
    state.durationMinutes = 0
}