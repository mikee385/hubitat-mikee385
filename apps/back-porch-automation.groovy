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
 
String getVersionNum() { return "13.1.0" }
String getVersionLabel() { return "Back Porch Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library

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
        section("Fans") {
            input "fans", "capability.switch", title: "Fans", multiple: true, required: false
            input "fanTemperatureSensor", "capability.temperatureMeasurement", title: "Temperature Sensor", multiple: false, required: false
            input "fanTemperatureValue", "decimal", title: "Turn on fan when temperature is above (°F):", required: false, defaultValue: 80
            
        }
        section("Outdoor Sensors") {
            input "sunlight", "capability.switch", title: "Sunlight", multiple: false, required: true
            input "cameras", "capability.switch", title: "Cameras", multiple: true, required: false
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
    // Zone
    subscribe(zone, "occupancy.occupied", zoneHandler_Occupied)
    subscribe(zone, "occupancy.vacant", zoneHandler_Vacant)
    
    // Occupancy
    subscribe(door, "contact", doorHandler_Occupancy)
    subscribe(lock, "contact", lockHandler_Occupancy)
    subscribe(location, "mode", modeHandler_Occupancy)
    
    // Light Alert
    for (light in lights) {
        subscribe(light, "switch", lightHandler_LightAlert)
    }
    subscribe(personToNotify, "sleeping", personHandler_LightAlert)
    
    // Fan Alert
    for (fan in fans) {
        subscribe(fan, "switch", fanHandler_FanAlert)
    }
    subscribe(personToNotify, "sleeping", personHandler_FanAlert)
    
    // Door Alert
    subscribe(door, "contact", doorHandler_DoorAlert)
    subscribe(personToNotify, "presence", personHandler_DoorAlert)
    subscribe(personToNotify, "sleeping", personHandler_DoorAlert)
    
    // Lock Alert
    subscribe(door, "contact", doorHandler_LockAlert)
    subscribe(lock, "contact", lockHandler_LockAlert)
    subscribe(personToNotify, "presence", personHandler_LockAlert)
    subscribe(personToNotify, "sleeping", personHandler_LockAlert)
    
    // Device Checks
    initializeDeviceChecks()
}

def zoneHandler_Occupied(evt) {
    logDebug("zoneHandler_Occupied: ${evt.device} changed to ${evt.value}")
    
    // Lights
    if (sunlight.currentValue("switch") == "off") {
        for (light in lights) {
            light.on()
        }
    }
    
    // Fans
    if (fanTemperatureSensor && fanTemperatureValue) {
        if (fanTemperatureSensor.currentValue("temperature") >= fanTemperatureValue) {
            for (fan in fans) {
                fan.on()
            }
        }
    } else {
        for (fan in fans) {
            fan.on()
        }
    }
    
    // Cameras
    unschedule("turnOn_Cameras")
    for (camera in cameras) {
        camera.off()
    }
}

def zoneHandler_Vacant(evt) {
    logDebug("zoneHandler_Vacant: ${evt.device} changed to ${evt.value}")
    
    // Lights
    for (light in lights) {
        light.off()
    }
    
    // Fans
    for (fan in fans) {
        fan.off()
    }
    
    // Cameras
    runIn(15, turnOn_Cameras)
}

def turnOn_Cameras() {
    for (camera in cameras) {
        camera.on()
    } 
}

def doorHandler_Occupancy(evt) {
    logDebug("doorHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        zone.occupied()
    }
}

def lockHandler_Occupancy(evt) {
    logDebug("lockHandler_Occupancy: ${evt.device} changed to ${evt.value}")
    
    zone.vacant()
}

def modeHandler_Occupancy(evt) {
    logDebug("modeHandler_Occupancy: ${evt.device} changed to ${evt.value}")

    if (evt.value != "Home") {
        zone.vacant()
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

def fanHandler_FanAlert(evt) {
    logDebug("fanHandler_FanAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        if (personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, fanAlert, [data: [device: "${evt.device}"]])
        }
    } else {
        unschedule("fanAlert")
    }
}

def personHandler_FanAlert(evt) {
    logDebug("personHandler_FanAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleeping") {
        unschedule("fanAlert")
        
        for (fan in fans) {
            if (fan.currentValue("switch") == "on") {
                personToNotify.deviceNotification("$fan is still on!")
            }
        }
    }
}

def fanAlert(evt) {
    personToNotify.deviceNotification("Should the ${evt.device} still be on?")
    runIn(60*30, fanAlert, [data: [device: "${evt.device}"]])
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
    if (door.currentValue("contact") == "open") {
        personToNotify.deviceNotification("Should the $door still be open?")
        runIn(60*30, doorAlert)
    } 
}

def doorHandler_LockAlert(evt) {
    logDebug("doorHandler_LockAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        unschedule("lockAlert")
    } else {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, lockAlert)
        }
    }
}

def lockHandler_LockAlert(evt) {
    logDebug("lockHandler_LockAlert: ${evt.device} changed to ${evt.value}")
    
    unschedule("lockAlert")
}

def personHandler_LockAlert(evt) {
    logDebug("personHandler_LockAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "not present" || personToNotify.currentValue("sleeping") == "sleeping") {
        unschedule("lockAlert")
    }
}

def lockAlert() {
    if (zone.currentValue("occupancy") == "occupied") {
        personToNotify.deviceNotification("Should the $lock still be unlocked?")
        runIn(60*30, lockAlert)
    } 
}