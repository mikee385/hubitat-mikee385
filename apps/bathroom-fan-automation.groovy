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
 
String getVersionNum() { return "1.0.0-beta9" }
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
            input "rapidTime", "number", title: "Time period to check for rapid change (in minutes)", required: true
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
    atomicState.runningState = "off"
    
    atomicState.humidityActive =  false
    atomicState.rapidState = "off"
    atomicState.baselineState = "below"
    atomicState.thresholdState = "below"

    atomicState.previousHumidity = bathroomSensor.currentValue("humidity")
    atomicState.currentHumidity = bathroomSensor.currentValue("humidity")

    subscribe(bathroomSensor, "humidity", humidityHandler)
    subscribe(bathroomFan, "switch", switchHandler)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def switchHandler(evt) {
    if (evt.value == "on") {
        turnOn()
    } else {
        turnOff()
    }
}

def humidityHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    atomicState.previousHumidity = atomicState.currentHumidity
    atomicState.currentHumidity = bathroomSensor.currentValue("humidity")
    
    checkRapidChange()
    checkBaseline()
    checkThreshold()
}

def checkRapidChange() {
    if (atomicState.currentHumidity >= atomicState.previousHumidity + rapidIncrease) {
        if (atomicState.rapidState != "rising") {
            atomicState.rapidState = "rising"
            
            notifier.deviceNotification(prefix + " - Rapid Increase")
            logDebug("Rapid Increase")
            
            turnOnHumidity()
        }
    } else if (atomicState.currentHumidity < atomicState.previousHumidity - rapidDecrease) {
        if (atomicState.rapidState == "rising") {
            atomicState.rapidState = "falling"
            
            notifier.deviceNotification(prefix + " - Rapid Decrease")
            logDebug("Rapid Decrease")
        }
    } else {
        if (atomicState.rapidState == "falling") {
            atomicState.rapidState = "off"
            
            notifier.deviceNotification(prefix + " - Rapid Finished")
            logDebug("Rapid Finished")
            
            turnOffHumidity()
        }
    }
}

def checkBaseline() {
    def baselineHumidity = baselineSensor.currentValue("humidity")

    if (atomicState.currentHumidity > baselineHumidity + baselineIncrease) {
        if (atomicState.baselineState == "below") {
            atomicState.baselineState = "above"
            
            notifier.deviceNotification(prefix + " - Baseline Increase")
            logDebug("Baseline Increase")
            
            turnOnHumidity()
        }
    } else if (atomicState.currentHumidity < baselineHumidity + baselineDecrease) {
        if (atomicState.baselineState == "above") {
            atomicState.baselineState = "below"
            
            notifier.deviceNotification(prefix + " - Baseline Decrease")
            logDebug("Baseline Decrease")
            
            turnOffHumidity()
        }
    }
}

def checkThreshold() {
    if (atomicState.currentHumidity > thresholdIncrease) {
        if (atomicState.thresholdState == "below") {
            atomicState.thresholdState = "above"
            
            notifier.deviceNotification(prefix + " - Threshold Increase")
            logDebug("Threshold Increase")
            
            turnOnHumidity()
        }
    } else if (atomicState.currentHumidity < thresholdDecrease) {
        if (atomicState.thresholdState == "above") {
            atomicState.thresholdState = "below"
            
            notifier.deviceNotification(prefix + " - Threshold Decrease")
            logDebug("Threshold Decrease")
            
            turnOffHumidity()
        }
    }
}

def turnOnHumidity() {
    if (atomicState.humidityActive == false) {
        atomicState.humidityActive = true
        
        bathroomFan.on()
        turnOn()
    }
}

def turnOffHumidity() {
    if (atomicState.humidityActive == true) {
        atomicState.humidityActive = false
        
        bathroomFan.off()
        turnOff()
    }
}

def turnOn() {
    if (atomicState.humidityActive == true) {
        if (atomicState.runningState != "humidity") {
            unschedule("turnOff")
            atomicState.runningState = "humidity"
            runIn(60*maximumRuntime, turnOff)
            
            notifier.deviceNotification(prefix + " - Fan Turned On - Humidity")
            logDebug("Fan Turned On - Humidity")
        }
    } else {
        if (atomicState.runningState == "off") {
            atomicState.runningState = "manual"
            runIn(60*manualRuntime, turnOff)
            
            notifier.deviceNotification(prefix + " - Fan Turned On - Manual")
            logDebug("Fan Turned On - Manual")
        }
    }
}

def turnOff() {
    if (atomicState.runningState != "off") {
        unschedule("turnOff")
        atomicState.runningState = "off"
    
        notifier.deviceNotification(prefix + " - Fan Turned Off")
        logDebug("Fan Turned Off")
    }
}