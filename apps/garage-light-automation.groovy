/**
 *  Garage Light Automation
 *
 *  Copyright 2020 Michael Pierce
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
 
String getVersionNum() { return "3.0.0-beta.1" }
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
        section("") {
            input "occupancy", "device.OccupancyStatus", title: "Occupancy Status", multiple: false, required: true
            
            input "overheadDoor", "capability.contactSensor", title: "Overhead Door", multiple: false, required: true
            
            input "entryDoor", "capability.contactSensor", title: "Entry Door", multiple: false, required: true
            
            input "sideDoor", "capability.contactSensor", title: "Side Door", multiple: false, required: true
            
            input "motionSensor", "capability.motionSensor", title: "Motion Sensor", multiple: false, required: true
            
            input "garageLight", "capability.switch", title: "Garage Light", multiple: false, required: true
            
            input "sunlight", "capability.switch", title: "Sunlight", multiple: false, required: true
            
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
    subscribe(occupancy, "occupancy", occupancyHandler)
    
    subscribe(overheadDoor, "contact", overheadDoorHandler)
    subscribe(entryDoor, "contact", entryDoorHandler)
    subscribe(sideDoor, "contact", sideDoorHandler)
    
    subscribe(motionSensor, "motion.active", activeHandler)
    
    subscribe(sunlight, "switch", sunlightHandler)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def sunlightHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (overheadDoor.currentValue("contact") == "open") {
        if (evt.value == "on") {
            garageLight.off()
        } else {
            garageLight.on()
        }
    }
}

def occupancyHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (overheadDoor.currentValue("contact") == "closed") {
        if (evt.value == "occupied") {
            garageLight.on()
        } else {
            garageLight.off()
        }
    }
}

def overheadDoorHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
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

def entryDoorHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
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

def sideDoorHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
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

def activeHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (occupancy.currentValue("state") == "checking") {
        occupancy.occupied()
    } else if (occupancy.currentValue("state") == "vacant") {
        occupancy.checking()
    }
}