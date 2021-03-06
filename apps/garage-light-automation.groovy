/**
 *  Garage Light Automation
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
 
String getVersionNum() { return "3.5.0" }
String getVersionLabel() { return "Garage Light Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Garage Light Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns the garage light on and off based on the occupancy of the garage and the status of the doors.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/garage-light-automation.groovy")

preferences {
    page(name: "settings", title: "Garage Light Automation", install: true, uninstall: true) {
        section {
            input "occupancy", "device.OccupancyStatus", title: "Occupancy Status", multiple: false, required: true
            input "overheadDoor", "capability.contactSensor", title: "Overhead Door", multiple: false, required: true
            input "entryDoor", "capability.contactSensor", title: "Entry Door", multiple: false, required: true
            input "sideDoor", "capability.contactSensor", title: "Side Door", multiple: false, required: true
            input "motionSensor", "capability.motionSensor", title: "Motion Sensor", multiple: false, required: true
            input "garageLight", "capability.switch", title: "Garage Light", multiple: false, required: true
            input "sunlight", "capability.switch", title: "Sunlight", multiple: false, required: true
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
    // Occupancy
    subscribe(overheadDoor, "contact", overheadDoorHandler_Occupancy)
    subscribe(entryDoor, "contact", entryDoorHandler_Occupancy)
    subscribe(sideDoor, "contact", sideDoorHandler_Occupancy)
    subscribe(motionSensor, "motion.active", motionHandler_Occupancy)
    
    // Light Switch
    subscribe(occupancy, "occupancy", occupancyHandler_LightSwitch)
    subscribe(sunlight, "switch", sunlightHandler_LightSwitch)
    subscribe(location, "mode", modeHandler_LightSwitch)
    
    // Light Alert
    subscribe(overheadDoor, "contact", deviceHandler_LightAlert)
    subscribe(entryDoor, "contact", deviceHandler_LightAlert)
    subscribe(sideDoor, "contact", deviceHandler_LightAlert)
    subscribe(motionSensor, "motion.active", deviceHandler_LightAlert)
    subscribe(garageLight, "switch", deviceHandler_LightAlert)
    subscribe(person, "sleeping", personHandler_LightAlert)
    
    // Door Alert
    subscribe(overheadDoor, "contact", overheadDoorHandler_DoorAlert)
    subscribe(entryDoor, "contact", entryDoorHandler_DoorAlert)
    subscribe(sideDoor, "contact", sideDoorHandler_DoorAlert)
    subscribe(person, "presence", personHandler_DoorAlert)
    subscribe(person, "sleeping", personHandler_DoorAlert)
    
    // Away Alert
    subscribe(overheadDoor, "contact", handler_AwayAlert)
    subscribe(entryDoor, "contact", handler_AwayAlert)
    subscribe(sideDoor, "contact", handler_AwayAlert)
    subscribe(motionSensor, "motion.active", handler_AwayAlert)
    subscribe(garageLight, "switch.on", handler_AwayAlert)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def overheadDoorHandler_Occupancy(evt) {
    logDebug("overheadDoorHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (sunlight.currentValue("switch") == "on") {
            garageLight.off()
        } else {
            garageLight.on()
        }
        occupancy.occupied()
    } else {
        garageLight.on()
        if (entryDoor.currentValue("contact") == "closed" && sideDoor.currentValue("contact") == "closed") {
            occupancy.checking()
        }
    }
}

def entryDoorHandler_Occupancy(evt) {
    logDebug("entryDoorHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        occupancy.occupied()
    } else {
        if (overheadDoor.currentValue("contact") == "closed" && sideDoor.currentValue("contact") == "closed") {
            if (garageLight.currentValue("switch") == "on") {
                occupancy.checking()
            } else {
                occupancy.vacant()
            }
        }
    }
}

def sideDoorHandler_Occupancy(evt) {
    logDebug("sideDoorHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        occupancy.occupied()
    } else {
        if (overheadDoor.currentValue("contact") == "closed" && entryDoor.currentValue("contact") == "closed") {
            if (garageLight.currentValue("switch") == "on") {
                occupancy.checking()
            } else {
                occupancy.vacant()
            }
        }
    }
}

def motionHandler_Occupancy(evt) {
    logDebug("motionHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (occupancy.currentValue("occupancy") == "checking") {
        occupancy.occupied()
    } else if (occupancy.currentValue("occupancy") == "vacant") {
        occupancy.checking()
    }
}

def occupancyHandler_LightSwitch(evt) {
    logDebug("occupancyHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (overheadDoor.currentValue("contact") == "closed") {
        if (evt.value != "vacant") {
            garageLight.on()
        } else {
            garageLight.off()
        }
    }
}

def sunlightHandler_LightSwitch(evt) {
    logDebug("sunlightHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (overheadDoor.currentValue("contact") == "open") {
        if (evt.value == "on") {
            garageLight.off()
        } else {
            garageLight.on()
        }
    }
}

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "Home") {
        garageLight.off()
    }
}

def deviceHandler_LightAlert(evt) {
    logDebug("deviceHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    unschedule("lightAlert")
    if (garageLight.currentValue("switch") == "on") {
        if (person.currentValue("sleeping") == "not sleeping") {
            runIn(60*10, lightAlert)
        }
    }
}

def personHandler_LightAlert(evt) {
    logDebug("personHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleeping") {
        unschedule("lightAlert")
        
        if (garageLight.currentValue("switch") == "on") {
            person.deviceNotification("$garageLight is still on!")
        }
    }
}

def lightAlert() {
    person.deviceNotification("Should the $garageLight still be on?")
    runIn(60*30, lightAlert)
}

def overheadDoorHandler_DoorAlert(evt) {
    logDebug("overheadDoorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (person.currentValue("presence") == "present" && person.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, overheadDoorAlert)
        }
    } else {
        unschedule("overheadDoorAlert")
    }
}

def overheadDoorAlert() {
    person.deviceNotification("Should the $overheadDoor still be open?")
    runIn(60*30, overheadDoorAlert)
}

def entryDoorHandler_DoorAlert(evt) {
    logDebug("entryDoorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (person.currentValue("presence") == "present" && person.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, entryDoorAlert)
        }
    } else {
        unschedule("entryDoorAlert")
    }
}

def entryDoorAlert() {
    person.deviceNotification("Should the $entryDoor still be open?")
    runIn(60*30, entryDoorAlert)
}

def sideDoorHandler_DoorAlert(evt) {
    logDebug("sideDoorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (person.currentValue("presence") == "present" && person.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, sideDoorAlert)
        }
    } else {
        unschedule("sideDoorAlert")
    }
}

def sideDoorAlert() {
    person.deviceNotification("Should the $sideDoor still be open?")
    runIn(60*30, sideDoorAlert)
}

def personHandler_DoorAlert(evt) {
    logDebug("personHandler_DoorAlert: ${evt.device} changed to ${evt.value}")

    if (person.currentValue("presence") == "not present" || person.currentValue("sleeping") == "sleeping") {
        unschedule("overheadDoorAlert")
        unschedule("entryDoorAlert")
        unschedule("sideDoorAlert")
        
        if (overheadDoor.currentValue("contact") == "open") {
            person.deviceNotification("$overheadDoor is still open!")
        }
        if (entryDoor.currentValue("contact") == "open") {
            person.deviceNotification("$entryDoor is still open!")
        }
        if (sideDoor.currentValue("contact") == "open") {
            person.deviceNotification("$sideDoor is still open!")
        }
    }
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        person.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}