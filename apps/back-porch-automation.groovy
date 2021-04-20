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
 
String getVersionNum() { return "3.1.0" }
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
            input "occupancy", "device.OccupancyStatus", title: "Occupancy Status", multiple: false, required: true
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
    state.sprinklersPaused = false
    
    // Occupancy
    subscribe(door, "contact", doorHandler_Occupancy)
    subscribe(location, "mode", modeHandler_Occupancy)
    
    // Light Switch
    subscribe(occupancy, "occupancy", occupancyHandler_LightSwitch)
    
    // Camera Notification
    subscribe(occupancy, "occupancy", occupancyHandler_CameraNotification)
    
    // Sprinkler Zones
    subscribe(occupancy, "occupancy", occupancyHandler_SprinklerZones)
    
    // Light Alert
    for (light in lights) {
        subscribe(light, "switch", lightHandler_LightAlert)
    }
    subscribe(person, "status", personHandler_LightAlert)
    
    // Door Alert
    subscribe(door, "contact", doorHandler_DoorAlert)
    subscribe(person, "status", personHandler_DoorAlert)
    
    // Lock Alert
    subscribe(occupancy, "occupancy", occupancyHandler_LockAlert)
    subscribe(person, "status", personHandler_LockAlert)
    
    // Away Alert
    subscribe(door, "contact", handler_AwayAlert)
    subscribe(lock, "contact", handler_AwayAlert)
    for (light in lights) {
        subscribe(light, "switch.on", handler_AwayAlert)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def doorHandler_Occupancy(evt) {
    logDebug("doorHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        occupancy.occupied()
    } else {
        subscribe(lock, "contact", lockHandler_Occupancy)
    }
}

def lockHandler_Occupancy(evt) {
    logDebug("lockHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    unsubscribe("lockHandler_Occupancy")
    occupancy.vacant()
}

def modeHandler_Occupancy(evt) {
    logDebug("modeHandler_Occupancy: ${evt.device} changed to ${evt.value}")

    if (evt.value != "Home") {
        unsubscribe("lockHandler_Occupancy")
        occupancy.vacant()
    }
}

def occupancyHandler_LightSwitch(evt) {
    logDebug("occupancyHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "occupied") {
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

def occupancyHandler_CameraNotification(evt) {
    logDebug("occupancyHandler_CameraNotification: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "occupied") {
        cameraNotification.off()
    } else {
        runIn(15, turnOn_CameraNotification)
    }
}

def turnOn_CameraNotification() {
    cameraNotification.on()
}

def occupancyHandler_SprinklerZones(evt) {
    logDebug("occupancyHandler_SprinklerZones: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "occupied") {
        for (sprinklerZone in sprinklerZones) {
            if (sprinklerZone.currentValue("switch") == "on") {
                state.sprinklersPaused = true
                //sprinklerController.pauseZoneRun(1800)
                person.deviceNotification("Pausing sprinklers!")
                break
            }
        }
    } else {
        if (state.sprinklersPaused) {
            state.sprinklersPaused = false
            //sprinklerController.resumeZoneRun()
            person.deviceNotification("Resuming sprinklers!")
        }
    }
}

def lightHandler_LightAlert(evt) {
    logDebug("lightHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        if (person.currentValue("status") != "sleep") {
            runIn(60*5, lightAlert, [data: [device: "${evt.device}"]])
        }
    } else {
        unschedule("lightAlert")
    }
}

def personHandler_LightAlert(evt) {
    logDebug("personHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleep") {
        unschedule("lightAlert")
        
        for (light in lights) {
            if (light.currentValue("switch") == "on") {
                person.deviceNotification("$light is still on!")
            }
        }
    }
}

def lightAlert(evt) {
    person.deviceNotification("Should the ${evt.device} still be on?")
    runIn(60*30, lightAlert, [data: [device: "${evt.device}"]])
}

def doorHandler_DoorAlert(evt) {
    logDebug("doorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (person.currentValue("status") == "home") {
            runIn(60*5, doorAlert)
        }
    } else {
        unschedule("doorAlert")
    }
}

def personHandler_DoorAlert(evt) {
    logDebug("personHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "home") {
        unschedule("doorAlert")
        
        if (door.currentValue("contact") == "open") {
            person.deviceNotification("$door is still open!")
        }
    }
}

def doorAlert() {
    person.deviceNotification("Should the $door still be open?")
    runIn(60*30, doorAlert)
}

def occupancyHandler_LockAlert(evt) {
    logDebug("occupancyHandler_LockAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "occupied") {
        if (person.currentValue("status") == "home") {
            runIn(60*5, lockAlert)
        }
    } else {
        unschedule("lockAlert")
    }
}

def personHandler_LockAlert(evt) {
    logDebug("personHandler_LockAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "home") {
        unschedule("lockAlert")
        
        if (occupancy.currentValue("occupancy") == "occupied") {
            person.deviceNotification("$lock is still unlocked!")
        }
    }
}

def lockAlert() {
    person.deviceNotification("Should the $lock still be unlocked?")
    runIn(60*30, lockAlert)
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        person.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}