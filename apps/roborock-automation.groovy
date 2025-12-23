/**
 *  Roborock Automation
 *
 *  Copyright 2025 Michael Pierce
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
 
String getAppName() { return "Roborock Automation" }
String getAppVersion() { return "2.0.1" }
String getAppTitle() { return "${getAppName()}, version ${getAppVersion()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library
#include mikee385.time-library

definition(
    name: getAppName(),
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Starts and stops the Roborock Robot Vacuum based on a schedule and presence.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/roborock-automation.groovy"
)

preferences {
    page(name: "settings", title: getAppTitle(), install: true, uninstall: true) {
        section {
            input "vacuum", "device.RoborockRobotVacuum", title: "Roborock Robot Vacuum", multiple: false, required: true
            input "vacuumStartTime", "time", title: "Start Time", required: true
            input "vacuumEndTime", "time", title: "End Time", required: true
            input "minimumMinutes", "number", title: "Minimum Duration (in minutes)", required: true
            input "vacuumResetTime", "time", title: "Reset Time", required: true
        }
        section("Pause") {
            input "pauseButton", "capability.pushableButton", title: "Pause/Resume Button", multiple: false, required: false
            input "pauseDoors", "capability.contactSensor", title: "Pause when Opened", multiple: true, required: false
        }
        section("People") {
            input "everydayPeople", "capability.presenceSensor", title: "Every Day", multiple: true, required: false
            input "weekendPeople", "capability.presenceSensor", title: "Weekend Only", multiple: true, required: false
        }
        section("Doors") {
            input "closedDoors", "capability.contactSensor", title: "Doors that should be Closed", multiple: true, required: false
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

def uninstalled() {
    for (device in getChildDevices()) {
        deleteChildDevice(device.deviceNetworkId)
    }
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    // Child Devices
    def automationSwitch = childDevice("Automation")
    def cleaningSwitch = childDevice("Cleaning")
    def pauseSwitch = childDevice("Pause")

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
    for (person in everydayPeople) {
        subscribe(person, "presence", everydayPersonHandler)
    }
    for (person in weekendPeople) {
        subscribe(person, "presence", weekendPersonHandler)
    }
    if (pauseButton) {
        subscribe(pauseButton, "pushed", buttonPushedHandler)
        subscribe(pauseButton, "doubleTapped", buttonDoubleTappedHandler)
        subscribe(pauseButton, "held", buttonHeldHandler)
    }
    for (pauseDoor in pauseDoors) {
        subscribe(pauseDoor, "contact.open", doorOpenedHandler)
    }
    subscribe(automationSwitch, "switch", automationSwitchHandler)
    subscribe(cleaningSwitch, "switch", cleaningSwitchHandler)
    subscribe(pauseSwitch, "switch", pauseSwitchHandler)

    // Runtime Tracking
    subscribe(vacuum, "switch", vacuumSwitchHandler)
    subscribe(vacuum, "state", vacuumStateHandler)
    
    // Daily Triggers
    def currentTime = new Date()
    
    def startToday = timeToday(vacuumStartTime)
    schedule("$currentTime.seconds $startToday.minutes $startToday.hours * * ? *", dailyStart)
    
    def resetToday = timeToday(vacuumResetTime)
    schedule("$currentTime.seconds $resetToday.minutes $resetToday.hours * * ? *", dailyReset)
    
    // Pause Alert
    subscribe(vacuum, "state", vacuumHandler_PauseAlert)
    subscribe(personToNotify, "presence", personHandler_PauseAlert)
    subscribe(personToNotify, "sleeping", personHandler_PauseAlert)
    
    // Device Checks
    initializeDeviceChecks()
    
    // Initialize state
    def deviceRunning = vacuum.currentValue("state") == "cleaning"
    def stateRunning = state.endTime < state.startTime
    
    if (deviceRunning && !stateRunning) {
        state.startTime = now()
    } else if (!deviceRunning && stateRunning) {
        state.endTime = now()
        state.durationMinutes += (state.endTime - state.startTime)/1000.0/60.0
    }
}

def childDevice(name) {
    def childID = "vacuum:${app.getId()}:$name"
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("hubitat", "Virtual Switch", childID, 1234, [label: "${app.label} $name", isComponent: false])
        child.updateSetting("logEnable", [value: "false", type: "bool"])
        child.updateSetting("txtEnable", [value: "false", type: "bool"])
        child.updateDataValue("Name", name)
        child.sendEvent(name: "switch", value: "off")
    }
    return child
}

def isWeekend() {
    def date = new Date()
    def day = date[Calendar.DAY_OF_WEEK]
    
    return day == 1 || day == 7
}

def getPeople() {
    def people = everydayPeople.collect()
    if (isWeekend()) {
        people.addAll(weekendPeople)
    }
    
    return people
}

def everydayPersonHandler(evt) {
    logDebug("everydayPersonHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "present") {
        cancelCycle()
    } else {
        startCycle()
    }
}

def weekendPersonHandler(evt) {
    logDebug("weekendPersonHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "present") {
        if (isWeekend()) {
            cancelCycle()
        } 
    } else {
        startCycle()
    }
}

def buttonPushedHandler(evt) {
    logDebug("buttonPushedHandler: ${evt.device} changed to ${evt.value}")

    pauseCycle()
}

def buttonDoubleTappedHandler(evt) {
    logDebug("buttonDoubleTappedHandler: ${evt.device} changed to ${evt.value}")

    cleanCycle()
}

def buttonHeldHandler(evt) {
    logDebug("buttonHeldHandler: ${evt.device} changed to ${evt.value}")

    cleanCycle()
}

def doorOpenedHandler(evt) {
    logDebug("doorOpenedHandler: ${evt.device} changed to ${evt.value}")

    pauseCycle()
}

def automationSwitchHandler(evt) {
    logDebug("automationSwitchHandler: ${evt.device} changed to ${evt.value}")

    if (evt.value == "on") {
        startCycle()
    } else {
        pauseCycle()
    } 
}

def cleaningSwitchHandler(evt) {
    logDebug("cleaningSwitchHandler: ${evt.device} changed to ${evt.value}")

    if (evt.value == "on") {
        cleanCycle()
    } else {
        cancelCycle()
    }
}

def pauseSwitchHandler(evt) {
    logDebug("pauseSwitchHandler: ${evt.device} changed to ${evt.value}")

    if (evt.value == "on") {
        pauseCycle()
    } else {
        cleanCycle()
    } 
}

def startCycle() {
    def everyoneAway = true
    for (person in getPeople()) {
        if (person.currentValue("presence") == "present") {
            everyoneAway = false
            break
        }
    }

    if (currentTimeIsBetween(vacuumStartTime, vacuumEndTime) && everyoneAway) {
        if (vacuum.currentValue("switch") == "off" && state.durationMinutes < minimumMinutes) {
            if (isReadyToRun()) {
                cleanCycle()
            } 
        } else if (isPaused()) {
            if (isReadyToRun()) {
                cleanCycle()
            } 
        }
    }
}

def isReadyToRun() {
    def message = "Do you want to run $vacuum?"
    
    def openDoors = []
    for (door in closedDoors) {
        if (door.currentValue("contact") == "open") {
            openDoors.add(door)
        }
    }
    if (openDoors) {
        message += "\nWARNING: Doors are open!\n" + openDoors.join("\n")
    } 
    
    if (childDevice("Automation").currentValue("switch") == "off") {
        personToNotify.deviceNotification(message)
        return false
    
    } else if (vacuum.currentValue("battery") <= 10) {
        personToNotify.deviceNotification("$vacuum could not start because the battery is dead!")
        return false
    
    } else if (openDoors) {
        personToNotify.deviceNotification(message)
        return false
        
    }
    
    return true
}

def isPaused() {
    if ((vacuum.currentValue("switch") == "on") && (vacuum.currentValue("state") in ["paused", "sleeping"])) {
        return true
    }
    return false 
}

def cleanCycle() {
    if (vacuum.currentValue("state") != "cleaning") {
        vacuum.appClean()
    }
}

def pauseCycle() {
    if (vacuum.currentValue("state") == "cleaning") {
        vacuum.appPause()
    }
}

def cancelCycle() {
    if (vacuum.currentValue("state") == "cleaning") {
        vacuum.appDock()
    }
}

def vacuumStateHandler(evt) {
    logDebug("vacuumStateHandler: ${evt.device} changed to ${evt.value}")
    
    if (!(evt.value in ["cleaning", "paused", "sleeping", "returning dock", "charging", "charged", "in error"])) {
        personToNotify.deviceNotification("$vacuum has unknown state: ${evt.value}")
    }
    
    if (evt.value == "cleaning") {
        state.startTime = now()
    } else if (state.endTime < state.startTime) { // should only be true while vacuum is running
        state.endTime = now()
        state.durationMinutes += (state.endTime - state.startTime)/1000.0/60.0
    }
    
    if (isPaused()) {
        childDevice("Pause").on()
    } else {
        childDevice("Pause").off()
    }
}

def vacuumSwitchHandler(evt) {
    logDebug("vacuumSwitchHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "off") {
        if (state.durationMinutes >= 0.5) {
            personToNotify.deviceNotification("$vacuum has cleaned for ${Math.round(state.durationMinutes)} minutes today!")
        }
        childDevice("Cleaning").off()
        childDevice("Pause").off()
    } else {
        if (location.mode == "Away") {
            personToNotify.deviceNotification("$vacuum has started!")
        }
        childDevice("Cleaning").on()
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

def vacuumHandler_PauseAlert(evt) {
    logDebug("vacuumHandler_PauseAlert: ${evt.device} changed to ${evt.value}")
    if (isPaused()) {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, pauseAlert)
        }
    } else {
        unschedule("pauseAlert")
    }
}

def personHandler_PauseAlert(evt) {
    logDebug("personHandler_PauseAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        if (isPaused()) {
            pauseAlert()
        }
    } else {
        unschedule("pauseAlert")
        
        if (isPaused()) {
            personToNotify.deviceNotification("$vacuum is still paused!")
        }
    }
}

def pauseAlert(evt) {
    if (isPaused()) { 
        personToNotify.deviceNotification("Should $vacuum still be paused?")
        runIn(60*30, pauseAlert)
    }
}