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
 
String getVersionNum() { return "1.0.0-beta.1" }
String getVersionLabel() { return "Holiday Lights Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Holiday Lights Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns the holiday light on and off based on the time of day and mode.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/holiday-lights-automation.groovy")

preferences {
    page(name: "settings", title: "Holiday Lights Automation", install: true, uninstall: true) {
        section("") {
            
            input "holidayLights", "capability.switch", title: "Holiday Lights", multiple: true, required: true

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
    subscribe(location, "mode", modeHandler)
    
    subscribe(location, "sunrise", sunriseHandler)
    subscribe(location, "sunset", sunsetHandler)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def modeHandler(evt) {
    logDebug("Mode changed to ${evt.value}")
    
    if (evt.value == "Home") {
        if (timeOfDayIsBetween(location.sunset, timeToday("23:59"), new Date(), location.timeZone)) {
            for (light in holidayLights) {
                light.on()
            }
        }
    } else if (evt.value == "Sleep") {
        for (light in holidayLights) {
            light.off()
        }
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
    
    if (evt.value == "Home") {
        for (light in holidayLights) {
            light.on()
        }
    }
}