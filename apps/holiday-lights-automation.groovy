/**
 *  Holiday Lights Automation
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
 
String getVersionNum() { return "1.0.0-beta.2" }
String getVersionLabel() { return "Holiday Lights Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Holiday Lights Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns the holiday light on and off based on routines and sunrise/sunset.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/holiday-lights-automation.groovy")

preferences {
    page(name: "settings", title: "Holiday Lights Automation", install: true, uninstall: true) {
        section("") {
            
            input "holidayLights", "capability.switch", title: "Holiday Lights", multiple: true, required: true

            input "onRoutines", "capability.switch", title: "On Routines", multiple: true, required: false
            
            input name: "onlyAfterSunset", type: "bool", title: "Only on after sunset?", defaultValue: false

            input "offRoutines", "capability.switch", title: "Off Routines", multiple: true, required: false

            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
            
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/on") {
        action: [
            GET: "onUrlHandler"
        ]
    }
    path("/off") {
        action: [
            GET: "offUrlHandler"
        ]
    }
    path("/toggle") {
        action: [
            GET: "toggleUrlHandler"
        ]
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
    if (onlyOnAfterSunset) {
        subscribe(location, "sunrise", sunriseHandler)
        subscribe(location, "sunset", sunsetHandler)
    }

    for (routine in onRoutines) {
        subscribe(routine, "switch.on", onRoutineHandler)
    }
    
    for (routine in offRoutines) {
        subscribe(routine, "switch.on", offRoutineHandler)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def sunriseHandler(evt) {
    logDebug("Received sunrise event")
    
    for (light in holidayLights) {
        light.off()
    }
}

def sunsetHandler(evt) {
    logDebug("Received sunset event")
    
    if (location.mode == "Home") {
        for (light in holidayLights) {
            light.on()
        }
    }
}

def onRoutineHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")

    if (!onlyOnAfterSunset || !timeOfDayIsBetween(location.sunrise, location.sunset, new Date(), location.timeZone)) {
        for (light in holidayLights) {
            light.on()
        }
    }
}

def offRoutineHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")

    for (light in holidayLights) {
        light.off()
    }
}

def onUrlHandler() {
    logDebug("On URL called")
    
    for (light in holidayLights) {
        light.on()
    }
}

def offUrlHandler() {
    logDebug("Off URL called")
    
    for (light in holidayLights) {
        light.off()
    }
}

def toggleUrlHandler() {
    logDebug("Toggle URL called")
    
    def anyLightOn = false
    for (light in holidayLights) {
        if (light.value == "on") {
            anyLightOn = true
            break 
        }
    }
    if (anyLightOn) {
        for (light in holidayLights) {
            light.off()
        }
    } else {
        for (light in holidayLights) {
            light.on()
        }
    }
}