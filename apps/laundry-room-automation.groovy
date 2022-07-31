/**
 *  Laundry Room Automation
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
 
import groovy.time.TimeCategory
 
String getVersionNum() { return "7.5.0" }
String getVersionLabel() { return "Laundry Room Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.away-alert-library
#include mikee385.battery-alert-library
#include mikee385.inactive-alert-library

definition(
    name: "Laundry Room Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Automation and alerts for the devices associated with the laundry room.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/laundry-room-automation.groovy"
)

preferences {
    page(name: "settings", title: "Laundry Room Automation", install: true, uninstall: true) {
        section {
            input "light", "device.GEZ-WavePlusMotionSwitchComboDriver", title: "Light", multiple: false, required: true
            input "door", "capability.contactSensor", title: "Door", multiple: false, required: true
            input "gate", "capability.contactSensor", title: "Gate", multiple: false, required: false
        }
        section("Laundry") {
            input "laundry", "device.ApplianceStatus", title: "Laundry", multiple: false, required: true
            input "washer", "device.LGThinQWasher", title: "Washer", required: true
            input "dryer", "device.LGThinQDryer", title: "Dryer", required: true
        }
        section("Bedtime") {
            input "routine", "capability.switch", title: "Routine", multiple: false, required: true
            input "startTime", "time", title: "Start Time", required: true
            input "endTime", "time", title: "End Time", required: true
        }
        section("Alerts") {
            input "alertReminder", "bool", title: "Reminder when Laundry Not Moved?", required: true, defaultValue: false
            input "alertStarted", "bool", title: "Alert when Laundry Started?", required: true, defaultValue: false
            input "alertFinished", "bool", title: "Alert when Laundry Finished?", required: true, defaultValue: false
            input "alertReset", "bool", title: "Alert when Laundry Reset?", required: true, defaultValue: false
        }
        section {
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
    // Light Switch
    subscribe(door, "contact.open", doorHandler_LightSwitch)
    if (gate) {
        subscribe(gate, "contact.open", doorHandler_LightSwitch)
    }
    subscribe(light, "switch", switchHandler_LightSwitch)
    subscribe(light, "motion", motionHandler_LightSwitch)
    subscribe(routine, "switch.on", routineHandler_LightSwitch)
    subscribe(location, "mode", modeHandler_LightSwitch)

    // Light Timeout
    subscribe(door, "contact", doorHandler_LightTimeout)
    
    // Laundry Status
    subscribe(washer, "currentState", washerHandler_LaundryStatus)
    subscribe(dryer, "currentState", dryerHandler_LaundryStatus)
    subscribe(light, "motion.active", switchHandler_LaundryStatus)

    // Bedtime Routine
    subscribe(door, "contact.closed", doorHandler_BedtimeRoutine)
    
    // Light Alert
    subscribe(door, "contact", deviceHandler_LightAlert)
    if (gate) {
        subscribe(gate, "contact", deviceHandler_LightAlert)
    }
    subscribe(light, "motion", deviceHandler_LightAlert)
    subscribe(light, "switch", deviceHandler_LightAlert)
    subscribe(personToNotify, "sleeping", personHandler_LightAlert)
    
    // Laundry Alert
    subscribe(laundry, "status", laundryHandler_LaundryAlert)
    subscribe(personToNotify, "presence", personHandler_LaundryAlert)
    subscribe(personToNotify, "sleeping", personHandler_LaundryAlert)
    
    // Washer Pause Alert
    subscribe(washer, "currentState", washerHandler_WasherPauseAlert)
    subscribe(personToNotify, "presence", personHandler_WasherPauseAlert)
    subscribe(personToNotify, "sleeping", personHandler_WasherPauseAlert)
    
    // Washer Error Alert
    subscribe(washer, "error", washerHandler_WasherErrorAlert)
    subscribe(personToNotify, "presence", personHandler_WasherErrorAlert)
    subscribe(personToNotify, "sleeping", personHandler_WasherErrorAlert)
    
    // Dryer Pause Alert
    subscribe(dryer, "currentState", dryerHandler_DryerPauseAlert)
    subscribe(personToNotify, "presence", personHandler_DryerPauseAlert)
    subscribe(personToNotify, "sleeping", personHandler_DryerPauseAlert)
    
    // Dryer Error Alert
    subscribe(dryer, "error", dryerHandler_DryerErrorAlert)
    subscribe(personToNotify, "presence", personHandler_DryerErrorAlert)
    subscribe(personToNotify, "sleeping", personHandler_DryerErrorAlert)
    
    // Away Alert
    subscribe(light, "switch.on", handler_AwayAlert)
    subscribe(door, "contact", handler_AwayAlert)
    if (gate) {
        subscribe(gate, "contact", handler_AwayAlert)
    }
    
    // Battery Alert
    scheduleBatteryCheck()
    
    // Inactive Alert
    scheduleInactiveCheck()
}

def getBatteryThresholds() {
    def thresholds = [
        [device: door, lowBattery: 10]
    ]
    if (gate) {
        thresholds.add([device: gate, lowBattery: 10])
    }
    return thresholds
}

def getInactiveThresholds() {
    def thresholds = [
        [device: light, inactiveHours: 24],
        [device: door, inactiveHours: 24],
        [device: washer, inactiveHours: 24*8],
        [device: dryer, inactiveHours: 24*8]
    ]
    if (gate) {
        thresholds.add([device: gate, inactiveHours: 24])
    }
    return thresholds
}

def getUnchangedThresholds() {
    return [
        [device: washer, attribute: "currentState", inactiveHours: 24*8],
        [device: dryer, attribute: "currentState", inactiveHours: 24*8]
    ]
}

def doorHandler_LightSwitch(evt) {
    logDebug("doorHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    light.on()
}

def switchHandler_LightSwitch(evt) {
    logDebug("switchHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value == "on") {
        if (light.currentValue("motion") != "active") {
            runIn(60, lightOff)
        }
    } else {
        unschedule("lightOff")
    } 
}

def motionHandler_LightSwitch(evt) {
    logDebug("motionHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value == "active") {
        unschedule("lightOff")
    }
}

def routineHandler_LightSwitch(evt) {
    logDebug("routineHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    light.off()
}

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value != "Home") {
        light.off()
    }
}

def lightOff() {
    logDebug("lightOff")
    
    if (light.currentValue("motion") != "active") {
        light.off()
    } 
}

def doorHandler_LightTimeout(evt) {
    logDebug("doorHandler_LightTimeout: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "closed") {
        unsubscribe("switchHandler_LightTimeout")
        light.setLightTimeout("5 minutes")
    } else {
        subscribe(light, "switch.off", switchHandler_LightTimeout)
    }
}

def switchHandler_LightTimeout(evt) {
    logDebug("switchHandler_LightTimeout: ${evt.device} changed to ${evt.value}")
    
    unsubscribe("switchHandler_LightTimeout")
    light.setLightTimeout("1 minute")
}

def washerRunning(currentState) {
    return currentState == "detecting" || currentState == "running" || currentState == "rinsing" || currentState == "spinning" || currentState == "pause"
}

def washerFinished(currentState) {
    return currentState == "end" || currentState == "power off"
}

def washerOther(currentState) {
    return currentState == "initial"
}

def washerHandler_LaundryStatus(evt) {
    logDebug("washerHandler_LaundryStatus: ${evt.device} changed to ${evt.value}")
    
    if (washerRunning(evt.value)) {
        if (laundry.currentValue("status") != "running") {
            laundry.start()
        }
    } else if (washerFinished(evt.value)) {
        if (!dryerRunning(dryer.currentValue("currentState")) && laundry.currentValue("status") == "running") {
            laundry.finish()
        }
    } else if (!washerOther(evt.value)) {
        personToNotify.deviceNotification("Error: Unknown status for $washer: ${evt.value}")
    }
}

def dryerRunning(currentState) {
    return currentState == "drying" || currentState == "cooling" || currentState == "pause"
}

def dryerFinished(currentState) {
    return currentState == "end" || currentState == "power off"
}

def dryerOther(currentState) {
    return currentState == "initial"
}

def dryerHandler_LaundryStatus(evt) {
    logDebug("dryerHandler_LaundryStatus: ${evt.device} changed to ${evt.value}")
    
    if (dryerRunning(evt.value)) {
        if (laundry.currentValue("status") != "running") {
            laundry.start()
        }
    } else if (dryerFinished(evt.value)) {
        if (!washerRunning(washer.currentValue("currentState")) && laundry.currentValue("status") == "running") {
            laundry.finish()
        }
    } else if (!dryerOther(evt.value)) {
        personToNotify.deviceNotification("Error: Unknown status for $dryer: ${evt.value}")
    }
}

def switchHandler_LaundryStatus(evt) {
    logDebug("switchHandler_LaundryStatus: ${evt.device} changed to ${evt.value}")
    
    if (laundry.currentValue("status") == "finished") {
        laundry.reset()
    }
}

def doorHandler_BedtimeRoutine(evt) {
    logDebug("doorHandler_BedtimeRoutine: ${evt.device} changed to ${evt.value}")
    
    def startToday = timeToday(startTime)
    def endToday = timeToday(endTime)
    if (endToday <= startToday) {
        use (TimeCategory) {
            endToday = endToday + 1.day
        }
    }
    
    if (location.mode != "Away" && timeOfDayIsBetween(startToday, endToday, new Date(), location.timeZone)) {
        routine.on()
    }
}

def deviceHandler_LightAlert(evt) {
    logDebug("deviceHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    unschedule("lightAlert")
    if (light.currentValue("switch") == "on" && light.currentValue("motion") == "inactive") {
        if (personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*6, lightAlert)
        }
    }
}

def personHandler_LightAlert(evt) {
    logDebug("personHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleeping") {
        unschedule("lightAlert")
        
        if (light.currentValue("switch") == "on") {
            personToNotify.deviceNotification("$light is still on!")
        }
    }
}

def lightAlert(evt) {
    personToNotify.deviceNotification("Should the $light still be on?")
    runIn(60*30, lightAlert)
}

def laundryHandler_LaundryAlert(evt) {
    logDebug("laundryHandler_LaundryAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "running") {
        unschedule("reminderAlert")
        
        if (alertStarted) {
            personToNotify.deviceNotification("Laundry has started.")
        }
    } else if (evt.value == "finished") {
        if (alertFinished) {
            personToNotify.deviceNotification("Laundry has finished.")
        }
        
        if (alertReminder && personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, reminderAlert)
        }
    } else if (evt.value == "idle") {
        unschedule("reminderAlert")
        
        if (alertReset) {
            personToNotify.deviceNotification("Laundry has reset.")
        }
    }
}

def personHandler_LaundryAlert(evt) {
    logDebug("personHandler_LaundryAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        if (alertReminder && laundry.currentValue("status") == "finished") {
            reminderAlert()
        }
    } else {
        unschedule("reminderAlert")
        
        if (alertReminder && laundry.currentValue("status") == "finished") {
            personToNotify.deviceNotification("Laundry has not been moved!")
        }
    }
}

def reminderAlert() {
    personToNotify.deviceNotification("Move the laundry!")
    runIn(60*30, reminderAlert)
}

def washerHandler_WasherPauseAlert(evt) {
    logDebug("washerHandler_WasherPauseAlert: ${evt.device} changed to ${evt.value}")
    if (evt.value == "pause") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, washerPauseAlert)
        }
    } else {
        unschedule("washerPauseAlert")
    }
}

def personHandler_WasherPauseAlert(evt) {
    logDebug("personHandler_WasherPauseAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        if (washer.currentValue("currentState") == "pause") {
            washerPauseAlert()
        }
    } else {
        unschedule("washerPauseAlert")
        
        if (washer.currentValue("currentState") == "pause") {
            personToNotify.deviceNotification("$washer is still paused!")
        }
    }
}

def washerPauseAlert(evt) {
    personToNotify.deviceNotification("Should the $washer still be paused?")
    runIn(60*30, washerPauseAlert)
}

def washerHandler_WasherErrorAlert(evt) {
    logDebug("washerHandler_WasherErrorAlert: ${evt.device} changed to ${evt.value}")
    if (evt.value != "error_noerror") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            washerErrorAlert()
        }
    } else {
        unschedule("washerErrorAlert")
    }
}

def personHandler_WasherErrorAlert(evt) {
    logDebug("personHandler_WasherErrorAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        if (washer.currentValue("error") != "error_noerror") {
            washerErrorAlert()
        }
    } else {
        unschedule("washerErrorAlert")
        
        if (washer.currentValue("error") != "error_noerror") {
            personToNotify.deviceNotification("$washer still has an error!")
        }
    }
}

def washerErrorAlert(evt) {
    personToNotify.deviceNotification("$washer has an error!")
    runIn(60*30, washerErrorAlert)
}

def dryerHandler_DryerPauseAlert(evt) {
    logDebug("dryerHandler_DryerPauseAlert: ${evt.device} changed to ${evt.value}")
    if (evt.value == "pause") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, dryerPauseAlert)
        }
    } else {
        unschedule("dryerPauseAlert")
    }
}

def personHandler_DryerPauseAlert(evt) {
    logDebug("personHandler_DryerPauseAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        if (dryer.currentValue("currentState") == "pause") {
            dryerPauseAlert()
        }
    } else {
        unschedule("dryerPauseAlert")
        
        if (dryer.currentValue("currentState") == "pause") {
            personToNotify.deviceNotification("$dryer is still paused!")
        }
    }
}

def dryerPauseAlert(evt) {
    personToNotify.deviceNotification("Should the $dryer still be paused?")
    runIn(60*30, dryerPauseAlert)
}

def dryerHandler_DryerErrorAlert(evt) {
    logDebug("dryerHandler_WasherErrorAlert: ${evt.device} changed to ${evt.value}")
    if (evt.value != "error_noerror") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            dryerErrorAlert()
        }
    } else {
        unschedule("dryerErrorAlert")
    }
}

def personHandler_DryerErrorAlert(evt) {
    logDebug("personHandler_DryerErrorAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        if (dryer.currentValue("error") != "error_noerror") {
            dryerErrorAlert()
        }
    } else {
        unschedule("dryerErrorAlert")
        
        if (dryer.currentValue("error") != "error_noerror") {
            personToNotify.deviceNotification("$dryer still has an error!")
        }
    }
}

def dryerErrorAlert(evt) {
    personToNotify.deviceNotification("$dryer has an error!")
    runIn(60*30, dryerErrorAlert)
}