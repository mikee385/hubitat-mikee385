/**
 *  Bathroom Fan Automation
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
 
String getVersionNum() { return "1.0.0-beta4" }
String getVersionLabel() { return "Bathroom Fan Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Bathroom Fan Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns a bathroom exhaust fan on/off using a nearby humidity sensor.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/bathroom-fan-automation.groovy")

preferences {
    page(name: "settings", title: "Bathroom Fan Automation", install: true, uninstall: true) {
        section {
            input "bathroomSensor", "capability.relativeHumidityMeasurement", title: "Bathroom Humidity Sensor", multiple: false, required: true
        }
        section {
            input "bathroomFan", "capability.switch", title: "Bathroom Fan", multiple: false, required: true
            input "manualRuntime", "number", title: "Manual runtime (in minutes)", required: true
            input "maximumRuntime", "number", title: "Maximum runtime (in minutes)", required: true
        }
        section("Rapid Change") {
            input "rapidIncrease", "number", title: "Humidity increase for shower start", required: true
            input "rapidDecrease", "number", title: "Humidity decrease for shower end", required: true
            input "rapidRuntime", "number", title: "Time to run fan after shower end (in minutes)", required: true
        }
        section("Baseline") {
            input "baselineSensor", "capability.relativeHumidityMeasurement", title: "Baseline Humidity Sensor", multiple: false, required: true
            input "baselineIncrease", "number", title: "Humidity above baseline for fan on", required: true
            input "baselineDecrease", "number", title: "Humidity above baseline for fan off", required: true
        }
        section("Threshold") {
            input "thresholdIncrease", "number", title: "Humidity threshold for fan on", required: true
            input "thresholdDecrease", "number", title: "Humidity threshold for fan off", required: true
        }
        section ("Notifications") {
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
            input "prefix", "text", title: "Message Prefix", multiple: false, required: true
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
    state.runningState = "off"
    
    state.rapidState = "off"
    state.baselineState = "below"
    state.thresholdState = "below"

    state.previousHumidity = bathroomSensor.currentValue("humidity")
    state.currentHumidity = bathroomSensor.currentValue("humidity")

    subscribe(bathroomSensor, "humidity", humidityHandler)
    subscribe(baselineSensor, "humidity", baselineHandler)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def humidityHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    state.previousHumidity = state.currentHumidity
    state.currentHumidity = bathroomSensor.currentValue("humidity")
    
    checkRapidChange()
    checkBaseline()
    checkThreshold()
}

def baselineHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    state.baselineHumidity = baselineSensor.currentValue("humidity")
    
    checkBaseline()
}   

def checkRapidChange() {
    logDebug("Checking rapid change...")

    if (state.currentHumidity >= state.previousHumidity + rapidIncrease) {
        if (state.rapidState != "rising") {
            state.rapidState = "rising"
            notifier.deviceNotification(prefix + " - Rapid Increase")
            logDebug("Rapid Increase")
            
            turnOnHumidity()
        }
    } else if (state.currentHumidity <= state.previousHumidity - rapidDecrease) {
        if (state.rapidState == "rising") {
            state.rapidState = "falling"
            notifier.deviceNotification(prefix + " - Rapid Decrease")
            logDebug("Rapid Decrease")
        }
    } else {
        if (state.rapidState == "falling") {
            state.rapidState = "off"
            notifier.deviceNotification(prefix + " - Rapid Finished")
            logDebug("Rapid Finished")
            
            turnOffHumidity()
        }
    }
}

def checkBaseline() {
    logDebug("Checking baseline...")
    
    if (state.currentHumidity >= state.baselineHumidity + baselineIncrease) {
        if (state.baselineState == "below") {
            state.baselineState = "above"
            notifier.deviceNotification(prefix + " - Baseline Increase")
            logDebug("Baseline Increase")
            
            turnOnHumidity()
        }
    } else if (state.currentHumidity <= state.baselineHumidity + baselineDecrease) {
        if (state.baselineState == "above") {
            state.baselineState = "below"
            notifier.deviceNotification(prefix + " - Baseline Decrease")
            logDebug("Baseline Decrease")
            
            turnOffHumidity()
        }
    }
}

def checkThreshold() {
    logDebug("Checking threshold...")

    if (state.currentHumidity >= thresholdIncrease) {
        if (state.thresholdState == "below") {
            state.thresholdState = "above"
            notifier.deviceNotification(prefix + " - Threshold Increase")
            logDebug("Threshold Increase")
            
            turnOnHumidity()
        }
    } else if (state.currentHumidity <= thresholdDecrease) {
        if (state.thresholdState == "above") {
            state.thresholdState = "below"
            notifier.deviceNotification(prefix + " - Threshold Decrease")
            logDebug("Threshold Decrease")
            
            turnOffHumidity()
        }
    }
}

def turnOnHumidity() {
    if (state.runningState != "humidity") {
        state.runningState = "humidity"
        notifier.deviceNotification(prefix + " - Fan Turned On")
        logDebug("Fan Turned On")
    }
}

def turnOffHumidity() {
    if (state.runningState == "humidity") {
        state.runningState = "off"
        notifier.deviceNotification(prefix + " - Fan Turned Off")
        logDebug("Fan Turned Off")
    }
}   