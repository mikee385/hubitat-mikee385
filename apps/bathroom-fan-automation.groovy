/**
 *  Bathroom Fan Automation
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
 
import java.math.RoundingMode
 
String getVersionNum() { return "1.0.8" }
String getVersionLabel() { return "Bathroom Fan Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Bathroom Fan Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Controls a bathroom exhaust fan based on the humidity level.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/bathroom-fan-automation.groovy")

preferences {
    page(name: "settings", title: "Bathroom Fan Automation", install: true, uninstall: true) {
        section("Fan") {
            input "bathroomFan", "capability.switch", title: "Bathroom Fan", multiple: false, required: true
            input "maximumRuntime", "number", title: "Maximum runtime (minutes)", required: true
        }
        section("Sensor") {
            input "bathroomSensor", "capability.relativeHumidityMeasurement", title: "Bathroom Sensor", multiple: false, required: true
            input "reportInterval", "number", title: "Reporting Interval (minutes)", required: true
            input "reportHumidityChange", "decimal", title: "Humidity Change Reported (%)", required: true
        }
        section("Levels") {
            input "risingRate", "decimal", title: "Rising Humidity Rate (% per minute)", required: true
            input "fallingRate", "decimal", title: "Falling Humidity Rate (% per minute)", required: true
        }
        section("Notifications") {
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
    if (state.currentHumidity == null) {
        state.currentHumidity = bathroomSensor.currentValue("humidity")
    }
    if (state.currentTime == null) {
        state.currentTime = now()
    }
    if (state.status == null) {
        state.status = "normal"
    }
    if (state.fan == null) {
        state.fan = "off"
    }
    
    state.risingMinutesToWait = Math.round(reportHumidityChange / Math.abs(risingRate))
    state.fallingMinutesToWait = Math.round(reportHumidityChange / Math.abs(fallingRate))
    
    subscribe(bathroomSensor, "humidity", humidityHandler)
    subscribe(bathroomFan, "switch", fanHandler)
    
    humidity = bathroomSensor.currentValue("humidity")
    process(humidity)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def humidityHandler(evt) {
    //logDebug("humidityHandler: ${evt.device} changed to ${evt.value}")
    
    humidity = bathroomSensor.currentValue("humidity")
    process(humidity)
}

def process(humidity) {
    unschedule("risingTimeout")
    unschedule("fallingTimeout")
    
    state.previousHumidity = state.currentHumidity
    state.previousTime = state.currentTime
     
    state.currentHumidity = humidity
    state.currentTime = now()
     
    state.deltaHumidity = state.currentHumidity - state.previousHumidity
    state.deltaTime = (state.currentTime - state.previousTime)/1000.0/60.0
     
    state.rate = state.deltaHumidity/state.deltaTime
     
    if (state.status == "normal") {
        if (state.rate >= risingRate) {
            rising()
            bathroomFan.on()
            
            message = "Fan has started! Humidity rising!"
            logDebug(message)
            notifier.deviceNotification(message)
        }
    } else if (state.status == "rising") {
        if (state.rate <= fallingRate) {
            falling()
        } else if (state.rate < risingRate) {
            peak()
        }
    } else if (state.status == "peak") {
        if (state.rate >= risingRate) {
            rising()
        } else if (state.rate <= fallingRate) {
            falling()
        }
    } else if (state.status == "falling") {
        if (state.rate >= risingRate) {
            rising()
        } else if (state.rate > fallingRate) {
            normal()
            
            message = "Fan has finished! Rate no longer falling!"
            logDebug(message)
            notifier.deviceNotification(message)
        }
    }
     
    logDebug("${state.status}: ${state.currentHumidity}%, ${state.deltaHumidity}%, ${state.deltaTime.setScale(2, RoundingMode.HALF_UP)} min, ${state.rate.setScale(2, RoundingMode.HALF_UP)} %/min")
    
    if (state.status != "normal") {
        notifier.deviceNotification(
"""${state.status}! ${state.currentHumidity}%, ${state.deltaHumidity}%
${state.deltaTime.setScale(2, RoundingMode.HALF_UP)} min
${state.rate.setScale(2, RoundingMode.HALF_UP)} %/min"""
        )
    }
}

def rising() {
    state.status = "rising"
    runIn(60*state.risingMinutesToWait, risingTimeout)
}

def risingTimeout() {
    peak()
    
    message = "Humidity has peaked! Too long for rising rate!"
    logDebug(message)
    notifier.deviceNotification(message)
}

def peak() {
    state.status = "peak"
}

def falling() {
    state.status = "falling"
    runIn(60*state.fallingMinutesToWait, fallingTimeout)
}

def fallingTimeout() {
    normal()
    
    message = "Fan has finished! Too long for falling rate!"
    logDebug(message)
    notifier.deviceNotification(message)
}

def normal(message) {
    state.status = "normal"
    bathroomFan.off()
}

def fanHandler(evt) {
    logDebug("fanHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        runIn(60*maximumRuntime, totalTimeout)
    } else {
        unschedule("risingTimeout")
        unschedule("fallingTimeout")
        unschedule("totalTimeout")
    }
}

def totalTimeout() {
    normal()
    
    message = "Fan has finished! Total runtime exceeded!"
    logDebug(message)
    notifier.deviceNotification(message)
}