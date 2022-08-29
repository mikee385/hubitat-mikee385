/**
 *  Sunlight Automation
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
 
String getVersionNum() { return "2.2.0" }
String getVersionLabel() { return "Sunlight Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.time-library

definition(
    name: "Sunlight Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Updates a switch based on time of day and illuminance.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/sunlight-automation.groovy"
)

preferences {
    page(name: "settings", title: "Sunlight Automation", install: true, uninstall: true) {
        section {
            input "sunlightSwitch", "capability.switch", title: "Sunlight Switch", multiple: false, required: true
        }
        section {
            input "enableLightSensor", "bool", title: "Use Light Sensor?", defaultValue: true
            input "lightSensor", "capability.illuminanceMeasurement", title: "Light Sensor", multiple: false, required: false
            input "lightLevelForOn", "decimal", title: "Light Level for On", required: true, defaultValue: 8
            input "lightLevelForOff", "decimal", title: "Light Level for Off", required: true, defaultValue: 7
        }
        section("Alerts") {
            input "alertSunrise", "bool", title: "Alert on Sunrise?", required: true, defaultValue: false
            input "alertSunset", "bool", title: "Alert on Sunset?", required: true, defaultValue: false
            input "alertOn", "bool", title: "Alert when On?", required: true, defaultValue: false
            input "alertOff", "bool", title: "Alert when Off?", required: true, defaultValue: false
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
    subscribe(location, "sunrise", sunriseHandler)
    subscribe(location, "sunset", sunsetHandler)
    
    if (enableLightSensor && lightSensor) {
        subscribe(lightSensor, "illuminance", lightHandler)
    }
}

def sunriseHandler(evt) {
    logDebug("Received sunrise event")
    
    if (enableLightSensor && lightSensor) {
        if (alertSunrise) {
            def lightValue = lightSensor.currentValue("illuminance")
            personToNotify.deviceNotification("Sunrise! ($lightValue)")
        }
    } else {
        sunlightSwitch.on()
        
        if (alertSunrise) {
            personToNotify.deviceNotification("Sunrise!")
        }
    }
}

def sunsetHandler(evt) {
    logDebug("Received sunset event")
    
    sunlightSwitch.off()
    
    if (enableLightSensor && lightSensor) {
        if (alertSunset) {
            def lightValue = lightSensor.currentValue("illuminance")
            personToNotify.deviceNotification("Sunset! ($lightValue)")
        }
    } else {
        if (alertSunset) {
            personToNotify.deviceNotification("Sunset!")
        }
    }
    
}

def lightHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (currentTimeIsBetween(location.sunrise, location.sunset)) {
        if (lightSensor.currentValue("illuminance") <= lightLevelForOff) {
            if (sunlightSwitch.currentValue("switch") == "on") {
                sunlightSwitch.off()
                if (alertOff) {
                    personToNotify.deviceNotification("It's dark! (${evt.value})")
                }
            }
        } else if (lightSensor.currentValue("illuminance") >= lightLevelForOn) {
            if (sunlightSwitch.currentValue("switch") == "off") {
                sunlightSwitch.on()
                if (alertOn) {
                    personToNotify.deviceNotification("It's light! (${evt.value})")
                }
            }
        }
    }
}