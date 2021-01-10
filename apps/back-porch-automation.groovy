/**
 *  Back Porch Automation
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
 
String getVersionNum() { return "1.0.0-beta.6" }
String getVersionLabel() { return "Back Porch Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Back Porch Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Automation and alerts for the door and lights associated with the back porch.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/back-porch-automation.groovy")

preferences {
    page(name: "settings", title: "Back Porch Automation", install: true, uninstall: true) {
        section {
            input "lights", "capability.switch", title: "Lights", multiple: true, required: true
            input "door", "capability.contactSensor", title: "Door", multiple: false, required: true
            input "lock", "capability.contactSensor", title: "Door Lock", multiple: false, required: true
            input "sunlight", "capability.switch", title: "Sunlight", multiple: false, required: true
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
    state.waitingForLock = false
    
    // Light Switch
    for (light in lights) {
        subscribe(light, "switch", lightHandler_LightSwitch)
    }
    subscribe(door, "contact", doorHandler_LightSwitch)
    subscribe(lock, "contact", lockHandler_LightSwitch)
    subscribe(sunlight, "switch", sunlightHandler_LightSwitch)
    subscribe(location, "mode", modeHandler_LightSwitch)
    
    // Light Alert
    for (light in lights) {
        subscribe(light, "switch", lightHandler_LightAlert)
    }
    subscribe(person, "state", personHandler_LightAlert)
    
    // Door Alert
    subscribe(door, "contact", doorHandler_DoorAlert)
    subscribe(person, "state", personHandler_DoorAlert)
    
    // Away Alert
    for (light in lights) {
        subscribe(light, "switch.on", handler_AwayAlert)
    }
    subscribe(door, "contact", handler_AwayAlert)
    subscribe(lock, "contact", handler_AwayAlert)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def lightHandler_LightSwitch(evt) {
    logDebug("lightHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    state.waitingForLock = false
    unschedule("stopWaitingForLock")
}

def doorHandler_LightSwitch(evt) {
    logDebug("doorHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    state.waitingForLock = false
    unschedule("stopWaitingForLock")
    
    if (evt.value == "open") {
        if (sunlight.currentValue("switch") == "off") {
            for (light in lights) {
                light.on()
            }
        }
    } else {
        state.waitingForLock = true
        runIn(60*10, stopWaitingForLock)
    }
}

def stopWaitingForLock() {
    state.waitingForLock = false
}

def lockHandler_LightSwitch(evt) {
    logDebug("lockHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (state.waitingForLock) {
        for (light in lights) {
            light.off()
        }
    }
}

def sunlightHandler_LightSwitch(evt) {
    logDebug("sunlightHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        for (light in lights) {
            light.off()
        }
    }
}

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value != "Home") {
        for (light in lights) {
            light.off()
        }
    }
}

def lightHandler_LightAlert(evt) {
    logDebug("lightHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        if (person.currentValue("state") != "sleep") {
            runIn(60*5, lightAlert, [data: [device: "${evt.device}"]])
        }
    } else {
        unschedule("lightAlert")
    }
}

def personHandler_LightAlert(evt) {
    logDebug("personHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleep") {
        unsubscribe("lightAlert")
        
        for (light in lights) {
            if (light.currentValue("switch") == "on") {
                notifier.deviceNotification("$light is still on!")
            }
        }
    }
}

def lightAlert(evt) {
    notifier.deviceNotification("Should the ${evt.device} still be on?")
    runIn(60*30, lightAlert, [data: [device: "${evt.device}"]])
}

def doorHandler_DoorAlert(evt) {
    logDebug("doorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (person.currentValue("state") == "home") {
            runIn(60*5, doorAlert)
        }
    } else {
        unschedule("doorAlert")
    }
}

def personHandler_DoorAlert(evt) {
    logDebug("personHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "home") {
        unsubscribe("doorAlert")
        
        if (door.currentValue("contact") == "open") {
            notifier.deviceNotification("$door is still open!")
        }
    }
}

def doorAlert() {
    notifier.deviceNotification("Should the $door still be open?")
    runIn(60*30, doorAlert)
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        notifier.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}