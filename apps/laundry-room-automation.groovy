/**
 *  Laundry Room Automation
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
 
String getVersionNum() { return "2.6.0" }
String getVersionLabel() { return "Laundry Room Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Laundry Room Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Automation and alerts for the devices associated with the laundry room.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/laundry-room-automation.groovy")

preferences {
    page(name: "settings", title: "Laundry Room Automation", install: true, uninstall: true) {
        section {
            input "light", "device.GEZ-WavePlusMotionSwitch", title: "Light", multiple: false, required: true
            input "door", "capability.contactSensor", title: "Door", multiple: false, required: true
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
    if (state.firstTime == null) {
        state.firstTime = false
    }

    // Light Switch
    subscribe(door, "contact.open", doorHandler_LightSwitch)
    subscribe(location, "mode", modeHandler_LightSwitch)
    
    // Light Timeout
    subscribe(door, "contact.open", doorHandler_LightTimeout)
    subscribe(light, "switch.off", lightHandler_LightTimeout)
    
    // Laundry Status
    subscribe(washer, "currentState", washerHandler_LaundryStatus)
    subscribe(dryer, "currentState", dryerHandler_LaundryStatus)
    subscribe(light, "motion.active", lightHandler_LaundryStatus)

    // Bedtime Routine
    subscribe(door, "contact.closed", doorHandler_BedtimeRoutine)
    
    // Light Alert
    subscribe(door, "contact", deviceHandler_LightAlert)
    subscribe(light, "motion", deviceHandler_LightAlert)
    subscribe(light, "switch", deviceHandler_LightAlert)
    subscribe(person, "status", personHandler_LightAlert)
    
    // Laundry Alert
    subscribe(laundry, "status", laundryHandler_LaundryAlert)
    subscribe(person, "status", personHandler_LaundryAlert)
    
    // Washer Pause Alert
    subscribe(washer, "currentState", washerHandler_WasherPauseAlert)
    subscribe(person, "status", personHandler_WasherPauseAlert)
    
    // Dryer Pause Alert
    subscribe(dryer, "currentState", dryerHandler_DryerPauseAlert)
    subscribe(person, "status", personHandler_DryerPauseAlert)
    
    // Away Alert
    subscribe(light, "switch.on", handler_AwayAlert)
    subscribe(door, "contact", handler_AwayAlert)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def doorHandler_LightSwitch(evt) {
    logDebug("doorHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    light.on()
}

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value != "Home") {
        light.off()
    }
}

def doorHandler_LightTimeout(evt) {
    logDebug("doorHandler_LightTimeout: ${evt.device} changed to ${evt.value}")
    
    state.firstTime = true
    light.setLightTimeout("5 minutes (default)")
}

def lightHandler_LightTimeout(evt) {
    logDebug("lightHandler_LightTimeout: ${evt.device} changed to ${evt.value}")
    
    if (state.firstTime) {
        state.firstTime = false
        light.setLightTimeout("1 minute")
    }
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
        person.deviceNotification("Error: Unknown status for $washer: ${evt.value}")
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
        person.deviceNotification("Error: Unknown status for $dryer: ${evt.value}")
    }
}

def lightHandler_LaundryStatus(evt) {
    logDebug("lightHandler_LaundryStatus: ${evt.device} changed to ${evt.value}")
    
    if (laundry.currentValue("status") == "finished") {
        laundry.reset()
    }
}

def doorHandler_BedtimeRoutine(evt) {
    logDebug("doorHandler_BedtimeRoutine: ${evt.device} changed to ${evt.value}")
    
    if (location.mode != "Away" && timeOfDayIsBetween(timeToday(startTime), timeToday(endTime), new Date(), location.timeZone)) {
        routine.on()
        light.off()
    }
}

def deviceHandler_LightAlert(evt) {
    logDebug("deviceHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    unschedule("lightAlert")
    if (light.currentValue("switch") == "on" && light.currentValue("motion") == "inactive") {
        if (person.currentValue("status") != "sleep") {
            if (state.firstTime) {
                runIn(60*10, lightAlert)
            } else {
                runIn(60*5, lightAlert)
            }
        }
    }
}

def personHandler_LightAlert(evt) {
    logDebug("personHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleep") {
        unschedule("lightAlert")
        
        if (light.currentValue("switch") == "on") {
            person.deviceNotification("$light is still on!")
        }
    }
}

def lightAlert(evt) {
    person.deviceNotification("Should the $light still be on?")
    runIn(60*30, lightAlert)
}

def laundryHandler_LaundryAlert(evt) {
    logDebug("laundryHandler_LaundryAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "running") {
        unschedule("reminderAlert")
        
        if (alertStarted) {
            person.deviceNotification("Laundry has started.")
        }
    } else if (evt.value == "finished") {
        if (alertFinished) {
            person.deviceNotification("Laundry has finished.")
        }
        
        if (alertReminder && person.currentValue("status") == "home") {
            runIn(60*5, reminderAlert)
        }
    } else if (evt.value == "idle") {
        unschedule("reminderAlert")
        
        if (alertReset) {
            person.deviceNotification("Laundry has reset.")
        }
    }
}

def personHandler_LaundryAlert(evt) {
    logDebug("personHandler_LaundryAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "home") {
        if (alertReminder && laundry.currentValue("status") == "finished") {
            reminderAlert()
        }
    } else {
        unschedule("reminderAlert")
        
        if (alertReminder && laundry.currentValue("status") == "finished") {
            person.deviceNotification("Laundry has not been moved!")
        }
    }
}

def reminderAlert() {
    person.deviceNotification("Move the laundry!")
    runIn(60*5, reminderAlert)
}

def washerHandler_WasherPauseAlert(evt) {
    logDebug("washerHandler_WasherPauseAlert: ${evt.device} changed to ${evt.value}")
    if (evt.value == "pause") {
        if (person.currentValue("status") == "home") {
            runIn(60*5, washerPauseAlert)
        }
    } else {
        unschedule("washerPauseAlert")
    }
}

def personHandler_WasherPauseAlert(evt) {
    logDebug("personHandler_WasherPauseAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "home") {
        if (washer.currentValue("currentState") == "pause") {
            washerPauseAlert()
        }
    } else {
        unschedule("washerPauseAlert")
        
        if (washer.currentValue("currentState") == "pause") {
            person.deviceNotification("$washerPauseAlert is still paused!")
        }
    }
}

def washerPauseAlert(evt) {
    person.deviceNotification("Should the $washer still be paused?")
    runIn(60*5, washerPauseAlert)
}

def dryerHandler_DryerPauseAlert(evt) {
    logDebug("dryerHandler_DryerPauseAlert: ${evt.device} changed to ${evt.value}")
    if (evt.value == "pause") {
        if (person.currentValue("status") == "home") {
            runIn(60*5, dryerPauseAlert)
        }
    } else {
        unschedule("dryerPauseAlert")
    }
}

def personHandler_DryerPauseAlert(evt) {
    logDebug("personHandler_DryerPauseAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "home") {
        if (dryer.currentValue("currentState") == "pause") {
            dryerPauseAlert()
        }
    } else {
        unschedule("dryerPauseAlert")
        
        if (dryer.currentValue("currentState") == "pause") {
            person.deviceNotification("$dryerPauseAlert is still paused!")
        }
    }
}

def dryerPauseAlert(evt) {
    person.deviceNotification("Should the $dryer still be paused?")
    runIn(60*5, dryerPauseAlert)
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        person.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}