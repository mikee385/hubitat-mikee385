/**
 *  Pantry Automation
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
 
String getVersionNum() { return "6.0.0" }
String getVersionLabel() { return "Pantry Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.away-alert-library
#include mikee385.device-check-library

definition(
    name: "Pantry Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns the pantry light on/off based on motion.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/pantry-automation.groovy"
)

preferences {
    page(name: "settings", title: "Pantry Automation", install: true, uninstall: true) {
        section {
            input "lights", "capability.switch", title: "Lights", multiple: true, required: true
            input "motionSensor", "capability.motionSensor", title: "Motion Sensor", multiple: false, required: true
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input "deviceChecker", "device.DeviceChecker", title: "Device Checker", multiple: false, required: true
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
    // Light Switch
    subscribe(location, "mode", modeHandler_LightSwitch)
    
    // Away Alert
    subscribe(motionSensor, "motion.active", handler_AwayAlert)
    for (light in lights) {
        subscribe(light, "switch.on", handler_AwayAlert)
    }
    
    // Device Checks
    initializeDeviceChecks()
}

def getInactiveThresholds() {
    def thresholds = [
        [device: motionSensor, inactiveHours: 24]
    ]
    for (light in lights) {
        thresholds.add([device: light, inactiveHours: 24])
    }
    return thresholds
}

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "Home") {
        for (light in lights) {
            light.off()
        }
    }
}