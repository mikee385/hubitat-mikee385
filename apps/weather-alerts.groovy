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
 
String getVersionNum() { return "1.1.2" }
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
            input "weatherStation", "device.AmbientWeatherDevice", title: "Weather Station", multiple: false, required: true
        }
        section("Alerts") {
            input "rainStartedAlert", "bool", title: "Alert when Rain Started?", required: true, defaultValue: false
            input "rainStoppedAlert", "bool", title: "Alert when Rain Stopped?", required: true, defaultValue: false
            input "rainDuringSleepAlert", "bool", title: "Alert when Rain During Sleep?", required: true, defaultValue: false
            input "person", "device.PersonStatus", title: "Person", multiple: false, required: true
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
    state.previousRainRate = weatherStation.currentValue("precip_1hr")
    state.previousRainTotal = weatherStation.currentValue("precip_today")
    state.eventRainTotal = 0.0
    
    state.rainTotal_BeforeSleep = 0.0
    state.rainTotal_AfterSleep = 0.0
    state.rainTotal_DuringSleep = 0.0

    // Rain Alert
    subscribe(weatherStation, "precip_1hr", rainRateHandler_RainAlert)
    subscribe(person, "sleeping", personHandler_RainAlert)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def rainRateHandler_RainAlert(evt) {
    logDebug("rainRateHandler_RainAlert: ${evt.device} changed to ${evt.value}")
    
    def currentRainRate = weatherStation.currentValue("precip_1hr")
    def currentRainTotal = weatherStation.currentValue("precip_today")
    
    def deltaRainTotal = 0.0
    if (currentRainTotal < state.previousRainTotal) {
        deltaRainTotal = currentRainTotal
    } else {
        deltaRainTotal = currentRainTotal - state.previousRainTotal
    }
    state.eventRainTotal += deltaRainTotal
    
    if (currentRainRate > 0.0 && state.previousRainRate == 0.0) {
        if (rainStartedAlert && person.currentValue("sleeping") != "sleeping") {
            notifier.deviceNotification("It's raining! ($deltaRainTotal in.)")
        }
    } else if (currentRainRate == 0.0 && state.previousRainRate > 0.0) {
        if (rainStoppedAlert && person.currentValue("sleeping") != "sleeping") {
            notifier.deviceNotification("Rain has stopped! (${state.eventRainTotal} in.)")
        }
        state.eventRainTotal = 0.0
    }
    
    state.previousRainRate = currentRainRate
    state.previousRainTotal = currentRainTotal
}

def personHandler_RainAlert(evt) {
    logDebug("personHandler_RainAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleeping") {
        state.rainTotal_BeforeSleep = weatherStation.currentValue("precip_today")
    } else {
        state.rainTotal_AfterSleep = weatherStation.currentValue("precip_today")
        state.rainTotal_DuringSleep = state.rainTotal_AfterSleep - state.rainTotal_BeforeSleep
        if (state.rainTotal_DuringSleep > 0.0 && rainDuringSleepAlert) {
            notifier.deviceNotification("Rain occurred during Sleep! (${state.rainTotal_DuringSleep} in)")
        }
    }
}