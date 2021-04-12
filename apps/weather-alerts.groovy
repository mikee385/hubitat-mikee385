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
 
String getVersionNum() { return "2.6.2" }
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
        section {
            input name: "alertOffline", type: "bool", title: "Alert when offline?", defaultValue: false
            input "offlineDuration", "number", title: "Minimum time before offline (in minutes)", required: true, defaultValue: 60
        }
        section {
            input "person", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
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
    state.rate = weatherStation.currentValue("precip_1hr")
    state.today_total = weatherStation.currentValue("precip_today")
    
    if (state.event_total == null) {
        state.event_total = 0.0
        state.event_level = 0
        state.event_text = "No rain"
    }
    
    if (state.sleep_total == null) {
        state.sleep_total = 0.0
        state.sleep_level = 0
        state.sleep_text = "No rain"
    }
    
    if (state.alert_time == null) {
        state.alert_time = 0
    }

    // Rain Alert
    subscribe(weatherStation, "precip_1hr", rainRateHandler_RainAlert)
    subscribe(person, "sleeping", personHandler_RainAlert)
    
    // Heartbeat
    if (alertOffline) {
        subscribe(weatherStation, "feelsLike", heartbeat)
        heartbeat()
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def rainRateHandler_RainAlert(evt) {
    logDebug("rainRateHandler_RainAlert: ${evt.device} changed to ${evt.value}")
    
    // Get state variables
    def asleep = person.currentValue("sleeping") == "sleeping"
    
    def rate_previous = state.rate
    def total_previous = state.today_total
    
    def rate_current = weatherStation.currentValue("precip_1hr")
    def total_current = weatherStation.currentValue("precip_today")
    
    log.debug("Previous Event: ${state.event_total}")
    log.debug("Previous Today: ${state.today_total}")
    
    // Calculate values
    def delta = 0.0
    if (total_current < total_previous) {
        delta = total_current
    } else {
        delta = total_current - total_previous
    }
    
    def level = -1
    def text = ""
    if (rate_current > 2.0) {
        level = 4
        text = "Violent rain"
    } else if (rate_current > 0.3) {
        level = 3
        text = "Heavy rain"
    } else if (rate_current > 0.1) {
        level = 2
        text = "Moderate rain"
    } else if (rate_current > 0.0) {
        level = 1
        text = "Light rain"
    } else {
        level = 0
        text = "No rain"
    }
    
    // Update state values
    state.rate = rate_current
    state.today_total = total_current
    
    // Update event status
    state.event_total += delta
    
    log.debug("Delta: ${delta}")
    log.debug("Current Event: ${state.event_total}")
    log.debug("Current Today: ${state.today_total}")
    
    if (level > state.event_level) {
        state.event_level = level
        state.event_text = text
        if (!asleep) {
            sendLevelAlert()
        }
    } else if (rate_current == 0.0 && rate_previous > 0.0) {
        if (!asleep) {
            sendStoppedAlert()
        }
        state.event_total = 0.0
        state.event_level = 0
        state.event_text = "No rain"
    }
    
    // Update sleep status
    if (asleep) {
        state.sleep_total += delta
        if (level > state.sleep_level) {
            state.sleep_level = level
            state.sleep_text = text
        }
    }
}

def personHandler_RainAlert(evt) {
    logDebug("personHandler_RainAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleeping") {
        state.sleep_total = 0.0
        state.sleep_level = 0
        state.sleep_text = "No rain"
    } else {
        sendAwakeAlert()
    }
}

def sendLevelAlert() {
    def current_time = now()
    if (state.event_level == 1 && current_time < (state.alert_time + (60*60*1000))) {
        return
    }
    state.alert_time = current_time

    person.deviceNotification(
"""${state.event_text}!
Rate: ${state.rate} in./hr"""
    )
}

def sendStoppedAlert() {
    if (state.event_level > 1) {
        person.deviceNotification(
"""${state.event_text} has stopped!
Event: ${state.event_total} in.
Today: ${state.today_total} in."""
        )
    }
}

def sendAwakeAlert() {
    if (state.sleep_level > 0) {
        person.deviceNotification(
"""${state.sleep_text} during Sleep!
Total: ${state.sleep_total} in.
Now: ${state.rate} in./hr"""
        )
    }
}

def heartbeat(evt) {
    logDebug("heartbeat: ${evt.device} changed to ${evt.value}")
    
    unschedule("offlineAlert")
    state.offline = false
    runIn(60*offlineDuration, offlineAlert)
}

def offlineAlert() {
    state.offline = true
    person.deviceNotification("${weatherStation} is offline!")
}