/**
 *  Garage Light Automation
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
 
String getVersionNum() { return "6.9.0" }
String getVersionLabel() { return "Garage Light Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.away-alert-library
#include mikee385.tamper-alert-library
#include mikee385.battery-alert-library
#include mikee385.inactive-alert-library

definition(
    name: "Garage Light Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns the garage light on and off based on the occupancy of the garage and the status of the doors.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/garage-light-automation.groovy"
)

preferences {
    page(name: "settings", title: "Garage Light Automation", install: true, uninstall: true) {
        section {
            input "zone", "device.OccupancyStatus", title: "Zone", multiple: false, required: true
            input "entryDoor", "capability.contactSensor", title: "Entry Door", multiple: false, required: true
            input "sideDoor", "capability.contactSensor", title: "Side Door", multiple: false, required: true
            input "motionSensor", "capability.motionSensor", title: "Motion Sensor", multiple: false, required: true
            input "garageLight", "capability.switch", title: "Garage Light", multiple: false, required: true
            input "sunlight", "capability.switch", title: "Sunlight", multiple: false, required: true
        }
        section {
            input "overheadControllers", "capability.garageDoorControl", title: "Overhead Controllers", multiple: true, required: false
            input "overheadSensors", "capability.contactSensor", title: "Overhead Sensors", multiple: true, required: false
            input "alertInconsistent", "bool", title: "Alert when Sensors are Inconsistent?", required: true, defaultValue: true
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
    // Initialize State
    state.previousOccupancy = zone.currentValue("occupancy")
    
    if (state.overheadDoorContact == null) {
        state.overheadDoorContact = "closed"
        for (overheadController in overheadControllers) {
            if (overheadController.currentValue("door") == "open") {
                state.overheadDoorContact = "open"
            }
        }
        for (overheadSensor in overheadSensors) {
            if (overheadController.currentValue("contact") == "open") {
                state.overheadDoorContact = "open"
            }
        }
    }
    for (overheadController in overheadControllers) {
        subscribe(overheadController, "door", overheadDoorHandler_State)
    }
    for (overheadSensor in overheadSensors) {
        subscribe(overheadController, "contact", overheadDoorHandler_State)
    }
    
    state.lightSwitch = garageLight.currentValue("switch")
    subscribe(garageLight, "switch.off", lightHandler_State)

    // Occupancy
    subscribe(entryDoor, "contact", entryDoorHandler_Occupancy)
    subscribe(sideDoor, "contact", sideDoorHandler_Occupancy)
    subscribe(motionSensor, "motion.active", motionHandler_Occupancy)
    subscribe(location, "mode", modeHandler_Occupancy)

    // Light Switch
    subscribe(zone, "occupancy", zoneHandler_LightSwitch)
    subscribe(sunlight, "switch", sunlightHandler_LightSwitch)
    
    // Light Alert
    subscribe(entryDoor, "contact", deviceHandler_LightAlert)
    subscribe(sideDoor, "contact", deviceHandler_LightAlert)
    subscribe(motionSensor, "motion.active", deviceHandler_LightAlert)
    subscribe(garageLight, "switch", deviceHandler_LightAlert)
    subscribe(personToNotify, "sleeping", personHandler_LightAlert)
    
    // Door Alert
    subscribe(entryDoor, "contact", entryDoorHandler_DoorAlert)
    subscribe(sideDoor, "contact", sideDoorHandler_DoorAlert)
    subscribe(personToNotify, "presence", personHandler_DoorAlert)
    subscribe(personToNotify, "sleeping", personHandler_DoorAlert)
    
    // Away Alert
    subscribe(entryDoor, "contact", handler_AwayAlert)
    subscribe(sideDoor, "contact", handler_AwayAlert)
    subscribe(motionSensor, "motion.active", handler_AwayAlert)
    subscribe(garageLight, "switch.on", handler_AwayAlert)
    
    // Tamper Alert
    subscribe(entryDoor, "tamper.detected", handler_TamperAlert)
    
    // Battery Alert
    scheduleBatteryCheck()
    
    // Inactive Alert
    scheduleInactiveCheck()
}

def getBatteryThresholds() {
    return [
        [device: overheadDoor, lowBattery: 10],
        [device: entryDoor, lowBattery: 10],
        [device: motionSensor, lowBattery: 10]
    ]
}

def getInactiveThresholds() {
    return [
        [device: overheadDoor, inactiveHours: 24],
        [device: entryDoor, inactiveHours: 2],
        [device: motionSensor, inactiveHours: 24],
        [device: garageLight, inactiveHours: 24]
    ]
}

def overheadDoorHandler_State(evt) {
    logDebug("overheadDoorHandler_State: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != state.overheadDoorContact) {
        state.overheadDoorContact = evt.value
        
        overheadDoorHandler_Occupancy(evt)
        overheadDoorHandler_LightSwitch(evt)
        deviceHandler_LightAlert(evt)
        overheadDoorHandler_DoorAlert(evt)
        
        if (alertInconsistent && overheadSensors) {
            handler_InconsistencyCheck(evt)
        }
        handler_AwayAlert(evt)
    } 
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
    if (state.overheadDoorContact == "closed" && entryDoor.currentValue("contact") == "closed" && sideDoor.currentValue("contact") == "closed") {
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
        if (state.overheadDoorContact == "closed") {
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
    
    if (state.overheadDoorContact == "open") {
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
    personToNotify.deviceNotification("Should the $garageLight still be on?")
    runIn(60*30, lightAlert)
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
    if (state.overheadDoorContact == "open") {
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
        
        if (state.overheadDoorContact == "open") {
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

def handler_InconsistencyCheck(evt) {
    logDebug("handler_InconsistencyCheck: ${evt.device} changed to ${evt.value}")
    
    runIn(5*60, inconsistencyCheck)
}

def inconsistencyCheck() {
    for (overheadController in overheadControllers) {
        def sensorValue = overheadController.currentValue("door")
        if (sensorValue != state.overheadDoorContact) {
            def message = "WARNING: $overheadController ($sensorValue) does not match ${state.overheadDoorContact}!"
            log.warn(message)
            personToNotify.deviceNotification(message)
        }
    }
    for (overheadSensor in overheadSensors) {
        def sensorValue = overheadSensor.currentValue("contact")
        if (sensorValue != state.overheadDoorContact) {
            def message = "WARNING: $overheadSensor ($sensorValue) does not match ${state.overheadDoorContact}!"
            log.warn(message)
            personToNotify.deviceNotification(message)
        }
    }
}