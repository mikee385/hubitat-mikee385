/**
 *  Front Porch Automation
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
 
String getVersionNum() { return "3.3.0" }
String getVersionLabel() { return "Front Porch Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.away-alert-library
#include mikee385.battery-alert-library
#include mikee385.inactive-alert-library

definition(
    name: "Front Porch Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Automation and alerts for the door and lights associated with the front porch.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/front-porch-automation.groovy"
)

preferences {
    page(name: "settings", title: "Front Porch Automation", install: true, uninstall: true) {
        section {
            input "door", "capability.contactSensor", title: "Door", multiple: false, required: true
            input "lock", "capability.lock", title: "Door Lock", multiple: false, required: false
            input "lights", "capability.switch", title: "Lights", multiple: true, required: true
        }
        section("Outdoor Sensors") {
            input "sunlight", "capability.switch", title: "Sunlight", multiple: false, required: true
            input "motionSensor", "capability.motionSensor", title: "Motion Sensor", multiple: false, required: false
        }
        section("Doorbell") {
            input "doorbell", "capability.pushableButton", title: "Doorbell", multiple: false, required: false
            input "alertDoorbellRang", "bool", title: "Alert when rang?", required: true, defaultValue: true
        }
        section("Sprinklers") {
            input "sprinklerController", "device.RachioController", title: "Sprinkler Controller", multiple: false, required: false
            input "sprinklerZones", "device.RachioZone", title: "Sprinkler Zones", multiple: true, required: false
        }
        section("Light Button") {
            input "buttonDevice", "capability.pushableButton", title: "Button Device", required: false
            input "buttonNumber", "number", title: "Button Number", required: false
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
    subscribe(sunlight, "switch", sunlightHandler_LightSwitch)
    subscribe(location, "mode", modeHandler_LightSwitch)
    if (buttonDevice) {
        subscribe(buttonDevice, "pushed", buttonHandler_LightSwitch)
    }
    
    // Motion Alert
    if (motionSensor) {
        subscribe(motionSensor, "motion", motionHandler_MotionAlert)
    }
    
    // Doorbell Alert
    if (doorbell) {
        subscribe(doorbell, "pushed", doorbellHandler_DoorbellAlert)
    }
    
    // Door Alert
    subscribe(door, "contact", doorHandler_DoorAlert)
    subscribe(personToNotify, "presence", personHandler_DoorAlert)
    subscribe(personToNotify, "sleeping", personHandler_DoorAlert)
    
    // Lock Alert
    if (lock) {
        subscribe(lock, "lock", lockHandler_LockAlert)
        subscribe(personToNotify, "presence", personHandler_LockAlert)
        subscribe(personToNotify, "sleeping", personHandler_LockAlert)
    }
    
    // Away Alert
    subscribe(door, "contact", handler_AwayAlert)
    if (lock) {
        subscribe(lock, "lock", handler_AwayAlert)
    }
    for (light in lights) {
        subscribe(light, "switch.on", handler_AwayAlert)
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
    if (lock) {
        thresholds.add([device: lock, lowBattery: 10])
    }
    return thresholds
}

def getInactiveThresholds() {
    def thresholds = [
        [device: door, inactiveHours: 2]
    ]
    if (lock) {
        thresholds.add([device: lock, inactiveHours: 2])
    }
    for (light in lights) {
        thresholds.add([device: light, inactiveHours: 24])
    }
    if (doorbell) {
        thresholds.add([device: doorbell, inactiveHours: 2])
    }
    return thresholds
}

def on() {
    for (light in lights) {
        light.on()
    }
}

def off() {
    for (light in lights) {
        light.off()
    }
}

def toggle() {
    def anyLightOn = false
    for (light in lights) {
        if (light.currentValue("switch") == "on") {
            anyLightOn = true
            break 
        }
    }
    if (anyLightOn) {
        off()
    } else {
        on()
    }
}

def sunlightHandler_LightSwitch(evt) {
    logDebug("sunlightHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        off()
    }
}

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value == "Sleep") {
        off()
    }
}

def buttonHandler_LightSwitch(evt) {
    logDebug("buttonHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (buttonNumber == null || evt.value == buttonNumber.toString()) {
        toggle()
    }
}

def motionHandler_MotionAlert(evt) {
    logDebug("motionHandler_MotionAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "active") {
        if (door.currentValue("contact") == "closed") {
            personToNotify.deviceNotification("Motion on the Front Porch!")
        }
    }
}

def doorbellHandler_DoorbellAlert(evt) {
    logDebug("doorbellHandler_DoorbellAlert: ${evt.device} changed to ${evt.value}")
    
    if (alertDoorbellRang) {
        personToNotify.deviceNotification("Someone rang the doorbell!")
    }
}

def doorHandler_DoorAlert(evt) {
    logDebug("doorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, doorAlert)
        }
    } else {
        unschedule("doorAlert")
    }
}

def personHandler_DoorAlert(evt) {
    logDebug("personHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "not present" || personToNotify.currentValue("sleeping") == "sleeping") {
        unschedule("doorAlert")
        
        if (door.currentValue("contact") == "open") {
            personToNotify.deviceNotification("$door is still open!")
        }
    }
}

def doorAlert() {
    personToNotify.deviceNotification("Should the $door still be open?")
    runIn(60*30, doorAlert)
}

def lockHandler_LockAlert(evt) {
    logDebug("lockHandler_LockAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "unlocked") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, lockAlert)
        }
    } else {
        unschedule("lockAlert")
    }
}

def personHandler_LockAlert(evt) {
    logDebug("personHandler_LockAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "not present" || personToNotify.currentValue("sleeping") == "sleeping") {
        unschedule("lockAlert")
        
        if (lock.currentValue("lock") == "unlocked") {
            personToNotify.deviceNotification("$lock is still unlocked!")
        }
    }
}

def lockAlert() {
    personToNotify.deviceNotification("Should the $lock still be unlocked?")
    runIn(60*30, lockAlert)
}