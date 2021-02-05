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
 
String getVersionNum() { return "1.1.0" }
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
        section("Bedtime") {
            input "routine", "capability.switch", title: "Routine", multiple: false, required: true
            input "startTime", "time", title: "Start Time", required: true
            input "endTime", "time", title: "End Time", required: true
        }
        section("Alerts") {
            input "person", "device.PersonStatus", title: "Person", multiple: false, required: true
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
        }
        section {
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

    // Bedtime Routine
    subscribe(door, "contact.closed", doorHandler_BedtimeRoutine)
    
    // Light Alert
    subscribe(light, "switch", lightHandler_LightAlert)
    subscribe(person, "status", personHandler_LightAlert)
    
    // Away Alert
    subscribe(light, "switch.on", handler_AwayAlert)
    subscribe(door, "contact", handler_AwayAlert)
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

def doorHandler_BedtimeRoutine(evt) {
    logDebug("doorHandler_BedtimeRoutine: ${evt.device} changed to ${evt.value}")
    
    if (location.mode != "Away" && timeOfDayIsBetween(timeToday(startTime), timeToday(endTime), new Date(), location.timeZone)) {
        routine.on()
        light.off()
    }
}

def lightHandler_LightAlert(evt) {
    logDebug("lightHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        if (person.currentValue("status") != "sleep") {
            if (state.firstTime) {
                runIn(60*10, lightAlert)
            } else {
                runIn(60*5, lightAlert)
            }
        }
    } else {
        unschedule("lightAlert")
    }
}

def personHandler_LightAlert(evt) {
    logDebug("personHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleep") {
        unschedule("lightAlert")
        
        if (light.currentValue("switch") == "on") {
            notifier.deviceNotification("$light is still on!")
        }
    }
}

def lightAlert(evt) {
    notifier.deviceNotification("Should the $light still be on?")
    runIn(60*30, lightAlert)
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        notifier.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}