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
 
String getVersionNum() { return "6.2.0" }
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
            input "roombaStartTime", "time", title: "Start Time", required: true
            input "minimumMinutes", "number", title: "Minimum Duration (in minutes)", required: true
            input "roombaResetTime", "time", title: "Reset Time", required: true
            input "pauseButton", "capability.pushableButton", title: "Pause/Resume Button", multiple: false, required: false
        }
        section("Work from Home") {
            input "workFromHomePerson", "capability.presenceSensor", title: "Person", multiple: false, required: true
            input "workStartTime", "time", title: "Start Time", multiple: false, required: true
            input "workEndTime", "time", title: "End Time", multiple: false, required: true
            input "workAwaySwitch", "device.GCalSwitch", title: "Away Switch", multiple: false, required: true
            input "workBusySwitch", "device.GCalSwitch", title: "Busy Switch", multiple: false, required: true
        }
        section {
            input "additionalPeople", "capability.presenceSensor", title: "Additional People", multiple: true, required: false
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
    
    // Clean Status
    subscribe(workFromHomePerson, "presence", workFromHomePersonHandler)
    subscribe(workAwaySwitch, "switch", calendarSwitchHandler)
    subscribe(workBusySwitch, "switch", calendarSwitchHandler)
    if (additionalPeople) {
        for (additionalPerson in additionalPeople) {
            subscribe(additionalPerson, "presence", additionalPersonHandler)
        }
    }
    subscribe(roomba, "phase.charge", chargeHandler)
    if (pauseButton) {
        subscribe(pauseButton, "pushed", buttonPushedHandler)
        subscribe(pauseButton, "doubleTapped", buttonDoubleTappedHandler)
        subscribe(pauseButton, "held", buttonHeldHandler)
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

def workFromHomePersonHandler(evt) {
    logDebug("workFromHomePersonHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "present") {
        if (duringWorkHours()) {
            if (busyWithWork()) {
                pauseCycle()
            }
        } else {
            cancelCycle()
        }
    } else {
        startCycle()
    }
}

def calendarSwitchHandler(evt) {
    logDebug("calendarSwitchHandler: ${evt.device} changed to ${evt.value}")
    
    if (workFromHomePerson.currentValue("presence") == "present" && duringWorkHours()) {
        if (busyWithWork()) {
            pauseCycle()
        } else {
            startCycle()
        }
    }
}

def additionalPersonHandler(evt) {
    logDebug("additionalPersonHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "present") {
        cancelCycle()
    } else {
        startCycle()
    }
}

def chargeHandler(evt) {
    logDebug("chargeHandler: ${evt.device} changed to ${evt.value}")
    
    log.debug "device: ${evt.device}"
    log.debug "value: ${evt.value}"
    log.debug "cycle: ${roomba.currentValue('cycle')}"
    log.debug "rechrgTm: ${roomba.currentValue('rechrgTm')}"

    if (roomba.currentValue("cycle") != "none") {
        rechrgTm = roomba.currentValue("rechrgTm").toLong()*1000
        if (rechrgTm > 0) {
            initialTm = rechrgTm - 15*1000
            if (initialTm > now()) {
                runOnce(new Date(initialTm), initialCheck)
                person.deviceNotification("$roomba will be checked at ${new Date(initialTm)}!")
            }
            
            finalTm = rechrgTm + 2*1000
            if (finalTm > now()) {
                runOnce(new Date(finalTm), finalCheck)
            } else {
                finalCheck()
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

def duringWorkHours() {
    def date = new Date()
    def day = date[Calendar.DAY_OF_WEEK]
    
    return day >= 2 && day <= 6 && timeOfDayIsBetween(timeToday(workStartTime), timeToday(workEndTime), date, location.timeZone)
}

def busyWithWork() {
    return workAwaySwitch.currentValue("switch") == "off" && workBusySwitch.currentValue("switch") == "on"
}

def startCycle() {
    def everyoneAway = true
    if (additionalPeople) {
        for (additionalPerson in additionalPeople) {
            if (additionalPerson.currentValue("presence") == "present") {
                everyoneAway = false
                break
            }
        }
    }

     if (timeOfDayIsBetween(timeToday(roombaStartTime), location.sunset, new Date(), location.timeZone) && everyoneAway && (workFromHomePerson.currentValue("presence") == "not present" || (duringWorkHours() && !busyWithWork()))) {
        if (roomba.currentValue("cycle") == "none" && state.durationMinutes < minimumMinutes) {
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
        unschedule("initialCheck")
        unschedule("finalCheck")
        roomba.dock()
    }
}

def initialCheck() {
    checkCycle(false)
}

def finalCheck() {
    checkCycle(true)
}

def checkCycle(lastCheck) {
    def everyoneAway = true
    if (additionalPeople) {
        for (additionalPerson in additionalPeople) {
            if (additionalPerson.currentValue("presence") == "present") {
                everyoneAway = false
                break
            }
        }
    }
    
    if (everyoneAway) {
        if (workFromHomePerson.currentValue("presence") == "present") {
            if (duringWorkHours()) {
                if (lastCheck) {
                    if (busyWithWork()) {
                        roomba.pause()
                    }
                }
            } else {
                unschedule("initialCheck")
                unschedule("finalCheck")
                roomba.stop()
            }
        }
    } else {
        unschedule("initialCheck")
        unschedule("finalCheck")
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
    
    unschedule("initialCheck")
    unschedule("finalCheck")
    
    if (evt.value == "none") {
        if (state.durationMinutes >= 0.5) {
            person.deviceNotification("$roomba has cleaned for ${Math.round(state.durationMinutes)} minutes today!")
        }
    } else {
        if (location.mode == "Away") {
            person.deviceNotification("$roomba has started!")
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