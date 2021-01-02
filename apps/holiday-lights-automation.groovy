/**
 *  Holiday Lights Automation
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
 
String getVersionNum() { return "1.1.0-beta.2" }
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
        section {
            input "lights", "capability.switch", title: "Holiday Lights", multiple: true, required: true
            input "onRoutines", "capability.switch", title: "On Routines", multiple: true, required: false
            input "offRoutines", "capability.switch", title: "Off Routines", multiple: true, required: false
        }
        section("Alerts") {
            input "person", "device.PersonStatus", title: "Person", multiple: false, required: true
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
        }
        section {
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
            
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/on") {
        action: [
            GET: "urlHandler_on"
        ]
    }
    path("/off") {
        action: [
            GET: "urlHandler_off"
        ]
    }
    path("/toggle") {
        action: [
            GET: "urlHandler_toggle"
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
    // Light Switch
    for (routine in onRoutines) {
        subscribe(routine, "switch.on", onRoutineHandler_LightSwitch)
    }
    for (routine in offRoutines) {
        subscribe(routine, "switch.on", offRoutineHandler_LightSwitch)
    }
    subscribe(location, "sunrise", sunriseHandler_LightSwitch)
    subscribe(location, "sunset", sunsetHandler_LightSwitch)
    subscribe(location, "mode", modeHandler_LightSwitch)
    
    // Away Alert
    for (light in lights) {
        subscribe(light, "switch.on", handler_AwayAlert)
    }
    
    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.onUrl = "${getFullLocalApiServerUrl()}/on?access_token=$state.accessToken"
    state.offUrl = "${getFullLocalApiServerUrl()}/off?access_token=$state.accessToken"
    state.toggleUrl = "${getFullLocalApiServerUrl()}/toggle?access_token=$state.accessToken"
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def onRoutineHandler_LightSwitch(evt) {
    logDebug("onRoutineHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    for (light in lights) {
        light.on()
    }
}

def offRoutineHandler_LightSwitch(evt) {
    logDebug("offRoutineHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    for (light in lights) {
        light.off()
    }
}

def sunriseHandler_LightSwitch(evt) {
    logDebug("sunriseHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    for (light in lights) {
        light.off()
    }
}

def sunsetHandler_LightSwitch(evt) {
    logDebug("sunsetHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Home") {
        for (light in lights) {
            light.on()
        }
    }
}

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Home") {
        if (timeOfDayIsBetween(location.sunset, timeToday("23:59"), new Date(), location.timeZone)) {
            for (light in lights) {
                light.on()
            }
        }
    } else if (evt.value == "Sleep") {
        for (light in lights) {
            light.off()
        }
    }
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        notifier.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}

def urlHandler_on() {
    logDebug("urlHandler_on")
    
    for (light in lights) {
        light.on()
    }
}

def urlHandler_off() {
    logDebug("urlHandler_off")
    
    for (light in lights) {
        light.off()
    }
}

def urlHandler_toggle() {
    logDebug("urlHandler_toggle")
    
    def anyLightOn = false
    for (light in lights) {
        if (light.currentValue("switch") == "on") {
            anyLightOn = true
            break 
        }
    }
    if (anyLightOn) {
        for (light in lights) {
            light.off()
        }
    } else {
        for (light in lights) {
            light.on()
        }
    }
}