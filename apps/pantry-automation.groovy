/**
 *  Pantry Automation
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
 
String getVersionNum() { return "1.0.0-beta.4" }
String getVersionLabel() { return "Pantry Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Pantry Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns the pantry light on/off based on motion.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/pantry-automation.groovy")

preferences {
    page(name: "settings", title: "Pantry Automation", install: true, uninstall: true) {
        section {
            input "lights", "capability.switch", title: "Lights", multiple: true, required: true
            input "motionSensor", "capability.motionSensor", title: "Motion Sensor", multiple: false, required: true
            input "minutes", "number", title: "Turn off after (minutes)", required: true
        }
        section {
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
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
    // Light Switch
    subscribe(motionSensor, "motion", motionHandler_LightSwitch)
    for (light in lights) {
        subscribe(light, "switch", switchHandler_LightSwitch)
    }
    subscribe(location, "mode", modeHandler_LightSwitch)
    
    // Away Alert
    subscribe(motionSensor, "motion.active", handler_AwayAlert)
    for (light in lights) {
        subscribe(light, "switch.on", handler_AwayAlert)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def motionHandler_LightSwitch(evt) {
    logDebug("motionHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "active") {
        unschedule()
        if (location.mode != "Away") {
            for (light in lights) {
                light.on()
            }
        }
    } else {
        runIn(60*minutes, turnOff)
    }
}

def switchHandler_LightSwitch(evt) {
    logDebug("switchHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        if (motionSensor.currentValue("motion") == "inactive") {
            runIn(60*minutes, turnOff)
        }
    } else {
        unschedule()
    }
}

def turnOff() {
    logDebug("Received turn off")
    
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

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        notifier.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}