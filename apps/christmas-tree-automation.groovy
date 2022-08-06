/**
 *  Christmas Tree Automation
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
 
String getVersionNum() { return "4.2.0" }
String getVersionLabel() { return "Christmas Tree Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-check-library

definition(
    name: "Christmas Tree Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns the Christmas Tree on and off based on routines.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/christmas-tree-automation.groovy"
)

preferences {
    page(name: "settings", title: "Christmas Tree Automation", install: true, uninstall: true) {
        section {
            input "lights", "capability.switch", title: "Christmas Tree", multiple: true, required: true
            input "onRoutines", "capability.switch", title: "On Routines", multiple: true, required: false
            input "offRoutines", "capability.switch", title: "Off Routines", multiple: true, required: false
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input "deviceChecker", "device.DeviceChecker", title: "Device Checker", multiple: false, required: true
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
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
    subscribe(location, "mode", modeHandler_LightSwitch)
    
    // Device Checks
    initializeDeviceChecks()
    
    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.onUrl = "${getFullLocalApiServerUrl()}/on?access_token=$state.accessToken"
    state.offUrl = "${getFullLocalApiServerUrl()}/off?access_token=$state.accessToken"
    state.toggleUrl = "${getFullLocalApiServerUrl()}/toggle?access_token=$state.accessToken"
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

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "Home") {
        for (light in lights) {
            light.off()
        }
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