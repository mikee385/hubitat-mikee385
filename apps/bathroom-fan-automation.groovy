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
 
String getVersionNum() { return "1.0.0-beta1" }
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
            input "fanSwitch", "capability.switch", title: "Bathroom Fan", multiple: false, required: true
            input "manualRuntime", "number", title: "Manual Runtime (in minutes)", required: true
        }
        section {
            input "bathroomHumidity", "capability.relativeHumidityMeasurement", title: "Bathroom Humidity Sensor", multiple: false, required: true
            input "maximumRuntime", "number", title: "Maximum Runtime (in minutes)", required: true
        }
        section {
            input "baselineHumidity", "capability.relativeHumidityMeasurement", title: "Baseline Humidity Sensor", multiple: false, required: true
            input "baselineOffset", "number", title: "Offset from Baseline", required: true
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
    atomicState.running = "off"
    atomicState.humidityActive = true

    subscribe(bathroomHumidity, "humidity", humidityHandler)
    subscribe(baselineHumidity, "humidity", humidityHandler)
    
    subscribe(bathroomFan, "switch", switchHandler)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def humidityHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (humidityTooHigh() == true) {
        if (atomicState.humidityActive == true) {
            startHumidity()
            bathroomFan.on()
        }
    } else {
        if (atomicState.running == "humidity") {
            finishHumidity()
        }
    }
}

def switchHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        if (atomicState.running == "off") {
            if (humidityTooHigh() == true) {
                startHumidity()
            } else {
                startManual()
            }
        }
    } else {
        if (atomicState.running == "humidity") {
            cancelHumidity()
        } else if (atomicState.running == "manual") {
            cancelManual()
        }
    }
}

def humidityTooHigh() {
    return (bathroomHumidity.currentValue("humidity") > baselineHumidity.currentValue("humidity") + baselineOffset)
}

def startHumidity() {
    logDebug("Turning humidity fan on")

    unschedule()

    atomicState.running = "humidity"
    runIn(60*maximumRuntime, finishHumidity)
    
    atomicState.humidityActive = false
    runIn(60*maximumRuntime, resetHumidity)
}

def finishHumidity() {
    logDebug("Turning humidity fan off")

    atomicState.running = "off"
    bathroomFan.off()
}

def resetHumidity() {
    logDebug("Resetting humidity fan")

    atomicState.humidityActive = true
}

def cancelHumidity() {
    logDebug("Turning humidity fan off from switch")

    atomicState.running = "off"
    unschedule("finishHumidity")
}

def startManual() {
    logDebug("Turning manual fan on")

    unschedule()
    
    aromicState.running = "manual"
    runIn(60*manualRuntime, finishManual)
}

def finishManual() {
    logDebug("Turning manual fan off due to timeout")

    atomicState.running = "off"
    bathroomFan.off()
}

def cancelManual() {
    logDebug("Turning manual fan off from switch")

    atomicState.running = "off"
    unschedule("finishManual")
}