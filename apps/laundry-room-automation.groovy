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
 
String getVersionNum() { return "2.0.2" }
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
    subscribe(location, "mode", modeHandler_LightSwitch)
    
    // Light Timeout
    subscribe(door, "contact.open", doorHandler_LightTimeout)
    subscribe(light, "switch.off", lightHandler_LightTimeout)
    
    // Laundry Status
    subscribe(washer, "currentState", washerHandler_LaundryStatus)
    subscribe(dryer, "currentState", dryerHandler_LaundryStatus)
    subscribe(light, "motion.active", lightHandler_LaundryStatus)
    
    // Laundry Alert
    subscribe(laundry, "status", laundryHandler_LaundryAlert)
    subscribe(person, "status", personHandler_LaundryAlert)

    // Bedtime Routine
    subscribe(door, "contact.closed", doorHandler_BedtimeRoutine)
    
    // Light Alert
    subscribe(door, "contact", deviceHandler_LightAlert)
    subscribe(light, "motion.active", deviceHandler_LightAlert)
    subscribe(light, "switch", deviceHandler_LightAlert)
    subscribe(person, "status", personHandler_LightAlert)
    
    // Away Alert
    subscribe(light, "switch.on", handler_AwayAlert)
    subscribe(door, "contact", handler_AwayAlert)
    subscribe(washer, "currentState", handler_AwayAlert)
    subscribe(dryer, "currentState", handler_AwayAlert)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
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

def washerRunning(currentStatus) {
    return currentStatus == "detecting" || currentStatus == "running" || currentStatus == "rinsing" || currentStatus == "spinning"
}

def washerFinished(currentStatus) {
    return currentStatus == "end" || currentStatus == "power off"
}

def washerOther(currentStatus) {
    return currentStatus == "initial"
}

def washerHandler_LaundryStatus(evt) {
    logDebug("washerHandler_LaundryStatus: ${evt.device} changed to ${evt.value}")
    
    if (washerRunning(evt.value)) {
        if (laundry.currentValue("status") != "running") {
            laundry.start()
        }
    } else if (washerFinished(evt.value)) {
        if (!dryerRunning(dryer.currentValue("currentStatus")) && laundry.currentValue("status") != "finished") {
            laundry.finish()
        }
    } else if (!washerOther(evt.value)) {
        person.deviceNotification("Error: Unknown status for $washer: ${evt.value}")
    }
}

def dryerRunning(currentStatus) {
    return currentStatus == "drying" || currentStatus == "cooling"
}

def dryerFinished(currentStatus) {
    return currentStatus == "end" || currentStatus == "power off"
}

def dryerOther(currentStatus) {
    return currentStatus == "initial"
}

def dryerHandler_LaundryStatus(evt) {
    logDebug("dryerHandler_LaundryStatus: ${evt.device} changed to ${evt.value}")
    
    if (dryerRunning(evt.value)) {
        if (laundry.currentValue("status") != "running") {
            laundry.start()
        }
    } else if (dryerFinished(evt.value)) {
        if (!washerRunning(washer.currentValue("currentStatus")) && laundry.currentValue("status") != "finished") {
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
            reminderAlert()
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
    if (light.currentValue("switch") == "on") {
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

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        person.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}