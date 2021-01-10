/**
 *  Weather Alerts
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
 
String getVersionNum() { return "1.0.0-beta.1" }
String getVersionLabel() { return "Weather Alerts, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Weather Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Alerts for various weather conditions.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/weather-alerts.groovy")

preferences {
    page(name: "settings", title: "Weather Alerts", install: true, uninstall: true) {
        section {
            input "weatherStation", "device.AmbientWeatherStation", title: "Weather Station", multiple: false, required: true
        }
        section("Alerts") {
            input "rainStartedAlert", "bool", title: "Alert when Rain Started?", required: true, defaultValue: false
            input "rainStoppedAlert", "bool", title: "Alert when Rain Stopped?", required: true, defaultValue: false
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
    state.previousRainRate = weatherStation.currentValue("hourlyrainin_real")

    subscribe(weatherStation, "hourlyrainin_real", rainRateHandler)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def rainRateHandler(evt) {
    logDebug("rainRateHandler: ${evt.device} changed to ${evt.value}")
    
    def currentRainRate = weatherStation.currentValue("hourlyrainin_real")
    if (currentRainRate > 0.0 && state.previousRainRate == 0.0) {
        if (rainStartedAlert) {
            notifier.deviceNotification("It's raining! ($currentRainRate in/hr)")
        }
    } else if (currentRainRate == 0.0 && state.previousRainRate > 0.0) {
        if (rainStoppedAlert) {
            notifier.deviceNotification("Rain has stopped!")
        }
    }
    state.previousRainRate = currentRainRate
}