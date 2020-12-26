/**
 *  Sunlight Automation
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
String getVersionLabel() { return "Sunlight Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Sunlight Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Updates a switch based on time of day and illuminance.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/sunlight-automation.groovy")

preferences {
    page(name: "settings", title: "Sunlight Automation", install: true, uninstall: true) {
        section {
            input "sunlightSwitch", "capability.switch", title: "Sunlight Switch", multiple: false, required: true
            input "lightSensor", "capability.illuminanceMeasurement", title: "Light Sensor", multiple: false, required: true
            input "lightLevelForOn", "decimal", title: "Light Level for On", required: true
            input "lightLevelForOff", "decimal", title: "Light Level for Off", required: true
        }
        section("Notifications") {
            input "alertSunrise", "bool", title: "Alert on Sunrise?", required: true, defaultValue: false
            input "alertSunset", "bool", title: "Alert on Sunset?", required: true, defaultValue: false
            input "alertOn", "bool", title: "Alert when On?", required: true, defaultValue: false
            input "alertOff", "bool", title: "Alert when Off?", required: true, defaultValue: false
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
        }
        section {
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
    subscribe(location, "sunrise", sunriseHandler)
    subscribe(location, "sunset", sunsetHandler)
    
    subscribe(lightSensor, "illuminance", lightHandler)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def sunriseHandler(evt) {
    logDebug("Received sunrise event")
    
    if (alertSunrise) {
        def lightValue = lightSensor.currentValue("illuminance")
        notifier.deviceNotification("Sunrise! ($lightValue)")
    }
}

def sunsetHandler(evt) {
    logDebug("Received sunset event")
    
    if (sunlightSwitch.currentValue("switch") == "on") {
        sunlightSwitch.off()
    }
    if (alertSunset) {
        def lightValue = lightSensor.currentValue("illuminance")
        notifier.deviceNotification("Sunset! ($lightValue)")
    }
}

def lightHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (timeOfDayIsBetween(location.sunrise, location.sunset, new Date(), location.timeZone)) {
        if (evt.value <= lightLevelForOff) {
            if (sunlightSwitch.currentValue("switch") == "on") {
                sunlightSwitch.off()
                if (alertOff) {
                    notifier.deviceNotification("It's dark! (${evt.value})")
                }
            }
        } else if (evt.value >= lightLevelForOn) {
            if (sunlightSwitch.currentValue("switch") == "off") {
                sunlightSwitch.on()
                if (alertOn) {
                    notifier.deviceNotification("It's light! (${evt.value})")
                }
            }
        }
    }
}