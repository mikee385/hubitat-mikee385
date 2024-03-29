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
 
String getVersionNum() { return "12.1.0" }
String getVersionLabel() { return "Laundry Room Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library

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
            input "light", "capability.switch", title: "Light", multiple: false, required: true
            input "motionSensor", "capability.motionSensor", title: "Motion Sensor", multiple: false, required: true
            input "door", "capability.contactSensor", title: "Door", multiple: false, required: true
        }
        section("Laundry") {
            input "laundry", "device.ApplianceStatus", title: "Laundry", multiple: false, required: true
            input "washer", "device.LGThinQWasher", title: "Washer", required: true
            input "dryer", "device.LGThinQDryer", title: "Dryer", required: true
        }
        section("Alerts") {
            input "alertStarted", "bool", title: "Alert when Laundry Started?", required: true, defaultValue: false
            input "alertFinished", "bool", title: "Alert when Laundry Finished?", required: true, defaultValue: false
            input "alertReset", "bool", title: "Alert when Laundry Reset?", required: true, defaultValue: false
        }
        section("Reminder") {
            input "reminderSwitch", "capability.switch", title: "Reminder Switch", multiple: false, required: false
            input "alertReminder", "bool", title: "Alert when Laundry Not Moved?", required: true, defaultValue: false
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
    // Light Switch
    subscribe(door, "contact.open", doorHandler_LightSwitch)
    subscribe(light, "switch", switchHandler_LightSwitch)
    subscribe(motionSensor, "motion", motionHandler_LightSwitch)
    subscribe(location, "mode", modeHandler_LightSwitch)

    // Laundry Status
    subscribe(washer, "currentState", washerHandler_LaundryStatus)
    subscribe(dryer, "currentState", dryerHandler_LaundryStatus)
    subscribe(motionSensor, "motion.active", switchHandler_LaundryStatus)

    // Light Alert
    subscribe(door, "contact", deviceHandler_LightAlert)
    subscribe(motionSensor, "motion", deviceHandler_LightAlert)
    subscribe(light, "switch", deviceHandler_LightAlert)
    subscribe(personToNotify, "sleeping", personHandler_LightAlert)
    
    // Laundry Alert
    subscribe(laundry, "status", laundryHandler_LaundryAlert)
    
    // Reminder Alert
    if (reminderSwitch) {
        subscribe(laundry, "status", laundryHandler_ReminderSwitch)
        
        subscribe(reminderSwitch, "switch", switchHandler_ReminderAlert)
        subscribe(personToNotify, "presence", personHandler_ReminderAlert)
        subscribe(personToNotify, "sleeping", personHandler_ReminderAlert)
    }
    
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
    
    // Device Checks
    initializeDeviceChecks()
}

def doorHandler_LightSwitch(evt) {
    logDebug("doorHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    light.on()
}

def switchHandler_LightSwitch(evt) {
    logDebug("switchHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value == "on") {
        if (motionSensor.currentValue("motion") != "active") {
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

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value != "Home") {
        light.off()
    }
}

def lightOff() {
    logDebug("lightOff")
    
    if (motionSensor.currentValue("motion") != "active") {
        light.off()
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

def deviceHandler_LightAlert(evt) {
    logDebug("deviceHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    unschedule("lightAlert")
    if (light.currentValue("switch") == "on" && motionSensor.currentValue("motion") == "inactive") {
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
        if (alertStarted) {
            personToNotify.deviceNotification("Laundry has started.")
        }
    } else if (evt.value == "finished") {
        if (alertFinished) {
            personToNotify.deviceNotification("Laundry has finished.")
        }
    } else if (evt.value == "idle") {
        if (alertReset) {
            personToNotify.deviceNotification("Laundry has reset.")
        }
    }
}

def laundryHandler_ReminderSwitch(evt) {
    logDebug("laundryHandler_ReminderSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "finished") {
        reminderSwitch.on()
    } else {
        reminderSwitch.off()
    }
}

def switchHandler_ReminderAlert(evt) {
    logDebug("switchHandler_ReminderAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        if (alertReminder && personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, reminderAlert)
        }
    } else {
        unschedule("reminderAlert")
    }
}

def personHandler_ReminderAlert(evt) {
    logDebug("personHandler_ReminderAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        if (alertReminder && reminderSwitch.currentValue("switch") == "on") {
            reminderAlert()
        }
    } else {
        unschedule("reminderAlert")
        
        if (alertReminder && reminderSwitch.currentValue("switch") == "on") {
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