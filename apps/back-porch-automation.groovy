/**
 *  Back Porch Automation
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
 
String getVersionNum() { return "6.1.0" }
String getVersionLabel() { return "Back Porch Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.away-alert-library
#include mikee385.battery-alert-library
#include mikee385.inactive-alert-library

definition(
    name: "Back Porch Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Automation and alerts for the door and lights associated with the back porch.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/back-porch-automation.groovy"
)

preferences {
    page(name: "settings", title: "Back Porch Automation", install: true, uninstall: true) {
        section {
            input "zone", "device.OccupancyStatus", title: "Zone", multiple: false, required: true
            input "door", "capability.contactSensor", title: "Door", multiple: false, required: true
            input "lock", "capability.contactSensor", title: "Door Lock", multiple: false, required: true
            input "lights", "capability.switch", title: "Lights", multiple: true, required: true
        }
        section("Outdoor Sensors") {
            input "sunlight", "capability.switch", title: "Sunlight", multiple: false, required: true
            input "cameraNotification", "capability.switch", title: "Camera Notifications", multiple: false, required: false
        }
        section("Sprinklers") {
            input "sprinklerController", "device.RachioController", title: "Sprinkler Controller", multiple: false, required: false
            input "sprinklerZones", "device.RachioZone", title: "Sprinkler Zones", multiple: true, required: false
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
    state.sprinklersPaused = false
    
    // Occupancy
    subscribe(door, "contact", doorHandler_Occupancy)
    subscribe(location, "mode", modeHandler_Occupancy)
    
    // Light Switch
    subscribe(zone, "occupancy", zoneHandler_LightSwitch)
    
    // Camera Notification
    subscribe(zone, "occupancy", zoneHandler_CameraNotification)
    
    // Sprinkler Zones
    subscribe(zone, "occupancy", zoneHandler_SprinklerZones)
    
    // Light Alert
    for (light in lights) {
        subscribe(light, "switch", lightHandler_LightAlert)
    }
    subscribe(personToNotify, "sleeping", personHandler_LightAlert)
    
    // Door Alert
    subscribe(door, "contact", doorHandler_DoorAlert)
    subscribe(personToNotify, "presence", personHandler_DoorAlert)
    subscribe(personToNotify, "sleeping", personHandler_DoorAlert)
    
    // Lock Alert
    subscribe(zone, "occupancy", zoneHandler_LockAlert)
    subscribe(personToNotify, "presence", personHandler_LockAlert)
    subscribe(personToNotify, "sleeping", personHandler_LockAlert)
    
    // Away Alert
    subscribe(door, "contact", handler_AwayAlert)
    subscribe(lock, "contact", handler_AwayAlert)
    for (light in lights) {
        subscribe(light, "switch.on", handler_AwayAlert)
    }
    
    def currentTime = new Date()
    
    // Battery Alert
    def batteryAlertTime = timeToday("20:00")
    schedule("$currentTime.seconds $batteryAlertTime.minutes $batteryAlertTime.hours * * ? *", handler_BatteryAlert)
    
    // Inactive Alert
    def inactiveAlertTime = timeToday("20:00")
    schedule("$currentTime.seconds $inactiveAlertTime.minutes $inactiveAlertTime.hours * * ? *", handler_InactiveAlert)
}

def getBatteryThresholds() {
    return [
        [device: door, lowBattery: 10],
        [device: lock, lowBattery: 10]
    ]
}

def getInactiveThresholds() {
    def thresholds = [
        [device: door, inactiveHours: 2],
        [device: lock, inactiveHours: 24]
    ]
    for (light in lights) {
        thresholds.add([device: light, inactiveHours: 24])
    }
    return thresholds
}

def doorHandler_Occupancy(evt) {
    logDebug("doorHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        zone.occupied()
    } else {
        subscribe(lock, "contact", lockHandler_Occupancy)
    }
}

def lockHandler_Occupancy(evt) {
    logDebug("lockHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    unsubscribe("lockHandler_Occupancy")
    zone.vacant()
}

def modeHandler_Occupancy(evt) {
    logDebug("modeHandler_Occupancy: ${evt.device} changed to ${evt.value}")

    if (evt.value != "Home") {
        unsubscribe("lockHandler_Occupancy")
        zone.vacant()
    }
}

def zoneHandler_LightSwitch(evt) {
    logDebug("zoneHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "vacant") {
        if (sunlight.currentValue("switch") == "off") {
            for (light in lights) {
                light.on()
            }
        }
    } else {
        for (light in lights) {
            light.off()
        }
    }
}

def zoneHandler_CameraNotification(evt) {
    logDebug("zoneHandler_CameraNotification: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "vacant") {
        cameraNotification.off()
    } else {
        runIn(15, turnOn_CameraNotification)
    }
}

def turnOn_CameraNotification() {
    cameraNotification.on()
}

def zoneHandler_SprinklerZones(evt) {
    logDebug("zoneHandler_SprinklerZones: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "vacant") {
        for (sprinklerZone in sprinklerZones) {
            if (sprinklerZone.currentValue("switch") == "on") {
                state.sprinklersPaused = true
                //sprinklerController.pauseZoneRun(1800)
                personToNotify.deviceNotification("Pausing sprinklers!")
                break
            }
        }
    } else {
        if (state.sprinklersPaused) {
            state.sprinklersPaused = false
            //sprinklerController.resumeZoneRun()
            personToNotify.deviceNotification("Resuming sprinklers!")
        }
    }
}

def lightHandler_LightAlert(evt) {
    logDebug("lightHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        if (personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, lightAlert, [data: [device: "${evt.device}"]])
        }
    } else {
        unschedule("lightAlert")
    }
}

def personHandler_LightAlert(evt) {
    logDebug("personHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleeping") {
        unschedule("lightAlert")
        
        for (light in lights) {
            if (light.currentValue("switch") == "on") {
                personToNotify.deviceNotification("$light is still on!")
            }
        }
    }
}

def lightAlert(evt) {
    personToNotify.deviceNotification("Should the ${evt.device} still be on?")
    runIn(60*30, lightAlert, [data: [device: "${evt.device}"]])
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

def zoneHandler_LockAlert(evt) {
    logDebug("zoneHandler_LockAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
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
        
        if (zone.currentValue("occupancy") == "on") {
            personToNotify.deviceNotification("$lock is still unlocked!")
        }
    }
}

def lockAlert() {
    personToNotify.deviceNotification("Should the $lock still be unlocked?")
    runIn(60*30, lockAlert)
}