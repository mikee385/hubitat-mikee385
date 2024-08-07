/**
 *  Garage Light Automation
 *
 *  Copyright 2024 Michael Pierce
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
 
String getAppName() { return "Garage Light Automation" }
String getAppVersion() { return "9.4.0" }
String getAppTitle() { return "${getAppName()}, version ${getAppVersion()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library

definition(
    name: getAppName(),
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns the garage light on and off based on the occupancy of the garage and the status of the doors.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/garage-light-automation.groovy"
)

preferences {
    page(name: "settings", title: getAppTitle(), install: true, uninstall: true) {
        section {
            input "zone", "device.OccupancyStatus", title: "Zone", multiple: false, required: true
        }
        section {
            input "overheadDoor", "capability.contactSensor", title: "Overhead Door", multiple: false, required: true
            input "entryDoor", "capability.contactSensor", title: "Entry Door", multiple: false, required: true
            input "sideDoor", "capability.contactSensor", title: "Side Door", multiple: false, required: true
        }
        section {
            input "additionalDoors", "capability.garageDoorControl", title: "Additional Doors", multiple: true, required: false
            input "alertInconsistent", "bool", title: "Alert when Inconsistent?", required: true, defaultValue: false
        }
        section {
            input "motionSensor", "capability.motionSensor", title: "Motion Sensor", multiple: false, required: true
            input "garageLight", "capability.switch", title: "Garage Light", multiple: false, required: true
            input "sunlight", "capability.switch", title: "Sunlight", multiple: false, required: true
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
    // Initialize State
    state.previousOccupancy = zone.currentValue("occupancy")
    
    state.lightSwitch = garageLight.currentValue("switch")
    subscribe(garageLight, "switch.off", lightHandler_State)

    // Occupancy
    subscribe(overheadDoor, "contact", overheadDoorHandler_Occupancy)
    subscribe(entryDoor, "contact", entryDoorHandler_Occupancy)
    subscribe(sideDoor, "contact", sideDoorHandler_Occupancy)
    subscribe(motionSensor, "motion.active", motionHandler_Occupancy)
    subscribe(location, "mode", modeHandler_Occupancy)

    // Light Switch
    subscribe(zone, "occupancy", zoneHandler_LightSwitch)
    subscribe(overheadDoor, "contact", overheadDoorHandler_LightSwitch)
    subscribe(sunlight, "switch", sunlightHandler_LightSwitch)
    
    // Light Alert
    subscribe(overheadDoor, "contact", deviceHandler_LightAlert)
    subscribe(entryDoor, "contact", deviceHandler_LightAlert)
    subscribe(sideDoor, "contact", deviceHandler_LightAlert)
    subscribe(motionSensor, "motion.active", deviceHandler_LightAlert)
    subscribe(garageLight, "switch", deviceHandler_LightAlert)
    subscribe(personToNotify, "sleeping", personHandler_LightAlert)
    
    // Door Alert
    subscribe(overheadDoor, "contact", overheadDoorHandler_DoorAlert)
    subscribe(entryDoor, "contact", entryDoorHandler_DoorAlert)
    subscribe(sideDoor, "contact", sideDoorHandler_DoorAlert)
    subscribe(personToNotify, "presence", personHandler_DoorAlert)
    subscribe(personToNotify, "sleeping", personHandler_DoorAlert)
    
    // Inconsistency Checks
    if (alertInconsistent) {
        subscribe(overheadDoor, "contact", deviceHandler_InconsistencyCheck)
        for (door in additionalDoors) {
            subscribe(door, "door", deviceHandler_InconsistencyCheck)
        } 
    }
    
    // Device Checks
    initializeDeviceChecks()
}

def lightHandler_State(evt) {
    logDebug("lightHandler_State: ${evt.device} changed to ${evt.value}")
    
    state.lightSwitch = evt.value
}

def overheadDoorHandler_Occupancy(evt) {
    logDebug("overheadDoorHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        zone.occupied()
    } else {
        if (entryDoor.currentValue("contact") == "closed" && sideDoor.currentValue("contact") == "closed") {
            zone.checking()
        }
    }
}

def entryDoorHandler_Occupancy(evt) {
    logDebug("entryDoorHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        zone.occupied()
    } else {
        runInMillis(250, checkForVacant)
    }
}

def sideDoorHandler_Occupancy(evt) {
    logDebug("sideDoorHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        zone.occupied()
    } else {
        runInMillis(250, checkForVacant)
    }
}

def checkForVacant() {
    if (overheadDoor.currentValue("contact") == "closed" && entryDoor.currentValue("contact") == "closed" && sideDoor.currentValue("contact") == "closed") {
        if (state.lightSwitch == "on") {
            zone.checking()
        } else {
            zone.vacant()
        }
    }
}

def motionHandler_Occupancy(evt) {
    logDebug("motionHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (zone.currentValue("occupancy") == "checking") {
        zone.occupied()
    } else if (zone.currentValue("occupancy") == "vacant") {
        zone.checking()
    }
}

def modeHandler_Occupancy(evt) {
    logDebug("modeHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "Home") {
        zone.vacant()
    }
}

def zoneHandler_LightSwitch(evt) {
    logDebug("zoneHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "vacant") {
        state.lightSwitch = "off"
        garageLight.off()
    } else if (state.previousOccupancy == "vacant") {
        if (overheadDoor.currentValue("contact") == "closed") {
            state.lightSwitch = "on"
            garageLight.on()
        }
    }
    
    state.previousOccupancy = evt.value
}

def overheadDoorHandler_LightSwitch(evt) {
    logDebug("overheadDoorHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (sunlight.currentValue("switch") == "on") {
            state.lightSwitch = "off"
            garageLight.off()
        } else {
            state.lightSwitch = "on"
            garageLight.on()
        }
    } else {
        state.lightSwitch = "on"
        garageLight.on()
    }
}

def sunlightHandler_LightSwitch(evt) {
    logDebug("sunlightHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (overheadDoor.currentValue("contact") == "open") {
        if (evt.value == "on") {
            state.lightSwitch = "off"
            garageLight.off()
        } else {
            state.lightSwitch = "on"
            garageLight.on()
        }
    }
}

def deviceHandler_LightAlert(evt) {
    logDebug("deviceHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    unschedule("lightAlert")
    if (state.lightSwitch == "on") {
        if (personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*10, lightAlert)
        }
    }
}

def personHandler_LightAlert(evt) {
    logDebug("personHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleeping") {
        unschedule("lightAlert")
        
        if (state.lightSwitch == "on") {
            personToNotify.deviceNotification("$garageLight is still on!")
        }
    }
}

def lightAlert() {
    if (state.lightSwitch == "on") {
        personToNotify.deviceNotification("Should the $garageLight still be on?")
        runIn(60*30, lightAlert)
    } 
}

def overheadDoorHandler_DoorAlert(evt) {
    logDebug("overheadDoorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, overheadDoorAlert)
        }
    } else {
        unschedule("overheadDoorAlert")
    }
}

def overheadDoorAlert() {
    if (overheadDoor.currentValue("contact") == "open") {
        personToNotify.deviceNotification("Should the $overheadDoor still be open?")
        runIn(60*30, overheadDoorAlert)
    } 
}

def entryDoorHandler_DoorAlert(evt) {
    logDebug("entryDoorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, entryDoorAlert)
        }
    } else {
        unschedule("entryDoorAlert")
    }
}

def entryDoorAlert() {
    if (entryDoor.currentValue("contact") == "open") {
        personToNotify.deviceNotification("Should the $entryDoor still be open?")
        runIn(60*30, entryDoorAlert)
    }
}

def sideDoorHandler_DoorAlert(evt) {
    logDebug("sideDoorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, sideDoorAlert)
        }
    } else {
        unschedule("sideDoorAlert")
    }
}

def sideDoorAlert() {
    if (sideDoor.currentValue("contact") == "open") {
        personToNotify.deviceNotification("Should the $sideDoor still be open?")
        runIn(60*30, sideDoorAlert)
    } 
}

def personHandler_DoorAlert(evt) {
    logDebug("personHandler_DoorAlert: ${evt.device} changed to ${evt.value}")

    if (personToNotify.currentValue("presence") == "not present" || personToNotify.currentValue("sleeping") == "sleeping") {
        unschedule("overheadDoorAlert")
        unschedule("entryDoorAlert")
        unschedule("sideDoorAlert")
        
        if (overheadDoor.currentValue("contact") == "open") {
            personToNotify.deviceNotification("$overheadDoor is still open!")
        }
        if (entryDoor.currentValue("contact") == "open") {
            personToNotify.deviceNotification("$entryDoor is still open!")
        }
        if (sideDoor.currentValue("contact") == "open") {
            personToNotify.deviceNotification("$sideDoor is still open!")
        }
    }
}

def deviceHandler_InconsistencyCheck(evt) {
    logDebug("deviceHandler_InconsistencyCheck: ${evt.device} changed to ${evt.value}")
    //log.info "${evt.device} changed to ${evt.value}"
    
    if (evt.value == "open") {
        runIn(15, inconsistencyCheck_Open)
    } else if (evt.value == "closed") {
        runIn(15, inconsistencyCheck_Closed)
    }
}

def inconsistencyCheck_Open() {
    inconsistencyCheck("open")
}

def inconsistencyCheck_Closed() {
    inconsistencyCheck("closed")
}

def inconsistencyCheck(doorValue) {
    //log.info "${overheadDoor}: expected=${doorValue}, actual=${overheadDoor.currentValue('contact')}"

    if (overheadDoor.currentValue("contact") != doorValue) {
        def message = "WARNING: $overheadDoor failed to change to $doorValue!"
        log.warn(message)
        personToNotify.deviceNotification(message)
    }
    
    for (door in additionalDoors) {
        //log.info "${door}: expected=${doorValue}, actual=${door.currentValue('door')}"

        if (door.currentValue("door") != doorValue) {
            def message = "WARNING: $door failed to change to $doorValue!"
            log.warn(message)
            personToNotify.deviceNotification(message)
        }
    }
}