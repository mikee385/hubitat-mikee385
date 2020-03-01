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
 
String getVersionNum() { return "1.0.0-beta1" }
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
        section("Devices") {
            input "occupancy", "capability.actuator", title: "Occupancy Status", multiple: false, required: true
            input "overheadDoor", "capability.contact", title: "Overhead Door", multiple: false, required: true
            input "entryDoor", "capability.contact", title: "Entry Door", multiple: false, required: true
            input "sideDoor", "capability.contact", title: "Side Door", multiple: false, required: true
            input "motionSensor", "capability.motionSensor", title: "Motion Sensor", multiple: false, required: true
            input "light", "capability.switch", title: "Light", multiple: false, required: true
        }
        section("Debugging") {
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false, submitOnChange: true
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

    if (logEnable) {
        log.warn "Debug logging enabled for 30 minutes"
        runIn(1800, logsOff)
    }
}

def logsOff(){
    log.warn "Debug logging disabled"
    app.updateSetting("logEnable", [value: "false", type: "bool"])
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def initialize() {
    subscribe(occupancy, "state.vacant", vacantHandler)
    
    subscribe(overheadDoor, "contact", overheadDoorHandler)
    subscribe(entryDoor, "contact", entryDoorHandler)
    subscribe(sideDoor, "contact", sideDoorHandler)
    
    subscribe(motionSensor, "motion.active", activeHandler)
}

def vacantHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (overheadDoor.currentValue("contact") == "closed") {
        light.off()
    }
}

def overheadDoorHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (overheadDoor.currentValue("contact") == "open") {
        def sunRiseSet = getSunriseAndSunset()
        if (timeOfDayIsBetween(sunRiseSet.sunset.time, sunRiseSet.sunrise.time, new Date(), location.timeZone)) {
            light.on()
        } else {
            light.off()
        }
        occupancy.occupied()
    } else {
        light.on()
        if (entryDoor.currentValue("contact") == "closed" && sideDoor.currentValue("contact") == "closed") {
            occupancy.checking()
        }
    }
}

def entryDoorHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (entryDoor.currentValue("contact") == "open") {
        if (occupancy.currentValue("state") == "vacant") {
            light.on()
        }
        occupancy.occupied()
    } else {
        if (overheadDoor.currentValue("contact") == "closed" && sideDoor.currentValue("contact") == "closed") {
            if (light.currentValue("switch") == "on") {
                occupancy.checking()
            } else {
                occupancy.vacant()
            }
        }
    }
}

def sideDoorHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (sideDoor.currentValue("contact") == "open") {
        if (occupancy.currentValue("state") == "vacant") {
            light.on()
        }
        occupancy.occupied()
    } else {
        if (overheadDoor.currentValue("contact") == "closed" && entryDoor.currentValue("contact") == "closed") {
            if (light.currentValue("switch") == "on") {
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
        light.on()
        occupancy.checking()
    }
}