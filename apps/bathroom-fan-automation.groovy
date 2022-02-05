/**
 *  Bathroom Fan Automation
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
 
import java.math.RoundingMode
 
String getVersionNum() { return "3.0.0" }
String getVersionLabel() { return "Bathroom Fan Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Bathroom Fan Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Controls a bathroom exhaust fan based on the humidity level.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/bathroom-fan-automation.groovy"
)

#include mikee385.debug-library
#include mikee385.away-alert-library
#include mikee385.battery-alert-library
#include mikee385.inactive-alert-library

preferences {
    page(name: "settings", title: "Bathroom Fan Automation", install: true, uninstall: true) {
        section("Fan") {
            input "fan", "capability.switch", title: "Bathroom Fan", multiple: false, required: true
        }
        section("Sensor") {
            input "sensor", "capability.relativeHumidityMeasurement", title: "Bathroom Sensor", multiple: false, required: true
            input "reportTimeInterval", "number", title: "Reporting Interval (minutes)", required: true
            input "reportHumidityChange", "decimal", title: "Humidity Change Reported (%)", required: true
        }
        section("Turn Fan On") {
            input "rapidRiseRate", "decimal", title: "Rapid Rising Humidity (% per minute)", required: true
            input "excessiveIncrease", "decimal", title: "Excessive Increase Between Readings (%)", required: true
            input "maximumThreshold", "decimal", title: "Above Maximum Humidty (%)", required: true
        }
        section("Turn Fan Off") {
            input "rapidFallRate", "decimal", title: "Rapid Falling Humidity (% per minute)", required: true
            input "percentAboveStart", "decimal", title: "Percent Above Starting Humidity (%)", required: true
            input "maximumRuntime", "number", title: "Maximum runtime (minutes)", required: true
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "enableInfoLog", type: "bool", title: "Enable info logging?", defaultValue: false
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
    // Create state
    if (state.currentTime == null) {
        state.currentTime = now()
    }
    if (state.startTime == null) {
        state.startTime = now()
    }
    if (state.endTime == null) {
        state.endTime = now()
    }
    if (state.durationMinutes == null) {
        state.durationMinutes = 0
    }
    
    humidity = sensor.currentValue("humidity")
    if (state.currentHumidity == null) {
        state.currentHumidity = humidity
    }
    if (state.startHumidity == null) {
        state.startHumidity = humidity
    }
    if (state.peakHumidity == null) {
        state.peakHumidity = humidity
    }
    updateTargetHumidity()
    
    if (state.status == null) {
        state.status = "normal"
    }
    
    state.minimumRiseRate = reportHumidityChange / reportTimeInterval
    state.risingMinutesToWait = Math.round(reportHumidityChange / Math.abs(rapidRiseRate))
    state.fallingMinutesToWait = Math.round(reportHumidityChange / Math.abs(rapidFallRate))
    
    // Fan Switch
    subscribe(sensor, "humidity", humidityHandler_FanSwitch)
    subscribe(fan, "switch", fanHandler_FanSwitch)
    subscribe(location, "mode", modeHandler_FanSwitch)
    
    // Away Alert
    subscribe(fan, "switch.on", handler_AwayAlert)
    
    def currentTime = new Date()
    
    // Battery Alert
    def batteryAlertTime = timeToday("20:00")
    schedule("$currentTime.seconds $batteryAlertTime.minutes $batteryAlertTime.hours * * ? *", handler_BatteryAlert)
    
    // Inactive Alert
    def inactiveAlertTime = timeToday("20:00")
    schedule("$currentTime.seconds $inactiveAlertTime.minutes $inactiveAlertTime.hours * * ? *", handler_InactiveAlert)
    
    // Initialize state
    handleHumidity(humidity)
}

def getBatteryThresholds() {
    return [
        //[device: sensor, lowBattery: 10] // Disabled since Konke sensors are very unreliable for battery reporting
    ]
}

def getInactiveThresholds() {
    return [
        [device: sensor, inactiveHours: 1]
    ]
}

def getUnchangedThresholds() {
    return [
        [device: sensor, attribute: "humidity", inactiveHours: 1]
    ]
}

def logInfo(msg) {
    if (enableInfoLog) {
        log.info msg
        personToNotify.deviceNotification(msg)
    }
}

def humidityHandler_FanSwitch(evt) {
    //logDebug("humidityHandler_FanSwitch: ${evt.device} changed to ${evt.value}")
    
    humidity = sensor.currentValue("humidity")
    handleHumidity(humidity)
}

def handleHumidity(humidity) {
    unschedule("risingRateTimeout")
    unschedule("fallingRateTimeout")
    
    state.previousTime = state.currentTime
    state.previousHumidity = state.currentHumidity
    
    state.currentTime = now()
    state.currentHumidity = humidity
    
    if (humidity > state.peakHumidity) {
        state.peakHumidity = humidity
        updateTargetHumidity()
    }
    
    state.deltaTime = (state.currentTime - state.previousTime)/1000.0/60.0
    state.deltaHumidity = state.currentHumidity - state.previousHumidity
     
    state.rate = state.deltaHumidity/state.deltaTime
    
    logDebug("Previous humidity: ${state.previousHumidity}%")
    logDebug("Current humidity: ${state.currentHumidity}%")
    logDebug("Delta humidity: ${state.deltaHumidity}%")
    logDebug("Delta time: ${state.deltaTime} min")
    logDebug("Rate: ${state.rate}%/min")
     
    if (state.status == "normal") {
        if (state.rate >= rapidRiseRate) {
            state.status = "rising"
            smartFanOn()
            logInfo("$fan turned on due to rapid rise: ${state.rate.setScale(2, RoundingMode.HALF_UP)}%/min")
        } else if (state.deltaHumidity >= excessiveIncrease) {
            state.status = "rising"
            smartFanOn()
            logInfo("$fan turned on due to excessive increase: ${state.deltaHumidity.setScale(1, RoundingMode.HALF_UP)}%")
        } else if (state.currentHumidity >= maximumThreshold) {
            state.status = "peak"
            smartFanOn()
            logInfo("$fan turned on due to maximum threshold: ${state.currentHumidity.setScale(1, RoundingMode.HALF_UP)}%")
        }
        
    } else if (state.status == "rising") {
        if (state.rate <= rapidFallRate) {
            state.status = "falling"
            logInfo("$fan falling due to rapid rate: ${state.rate.setScale(2, RoundingMode.HALF_UP)}%/min")
        } else if (state.rate < rapidRiseRate) {
            state.status = "peak"
            logInfo("$fan peak due to non-rapid rate: ${state.rate.setScale(2, RoundingMode.HALF_UP)}%/min")
        }
        
    } else if (state.status == "peak") {
        if (state.rate >= rapidRiseRate) {
            state.status = "rising"
            logInfo("$fan rising due to rapid rate: ${state.rate.setScale(2, RoundingMode.HALF_UP)}%/min")
        } else if (state.currentHumidity <= state.targetHumidity && state.currentHumidity < maximumThreshold) {
            state.status = "normal"
            smartFanOff()
            logInfo(
"""$fan turned off due to dropping below target: ${state.currentHumidity.setScale(1, RoundingMode.HALF_UP)}% < ${state.targetHumidity.setScale(1, RoundingMode.HALF_UP)}%. 
Total runtime: ${state.durationMinutes.setScale(0, RoundingMode.HALF_UP)} min."""
            )
        } else if (state.rate <= rapidFallRate) {
            state.status = "falling"
            logInfo("$fan falling due to rapid rate: ${state.rate.setScale(2, RoundingMode.HALF_UP)}%/min")
        }
        
    } else if (state.status == "falling") {
        if (state.rate >= rapidRiseRate) {
            state.status = "rising"
            logInfo("$fan rising due to rapid rate: ${state.rate.setScale(2, RoundingMode.HALF_UP)}%/min")
        } else if (state.rate >= state.minimumRiseRate) {
            state.status = "rising"
            logInfo("$fan rising due to minimum rate: ${state.rate.setScale(2, RoundingMode.HALF_UP)}%/min")
        } else if (state.currentHumidity <= state.targetHumidity && state.currentHumidity < maximumThreshold) {
            state.status = "normal"
            smartFanOff()
            logInfo(
"""$fan turned off due to dropping below target: ${state.currentHumidity.setScale(1, RoundingMode.HALF_UP)}% < ${state.targetHumidity.setScale(1, RoundingMode.HALF_UP)}%.
Total runtime: ${state.durationMinutes.setScale(0, RoundingMode.HALF_UP)} min."""
            )
        } else if (state.rate > rapidFallRate && state.currentHumidity < maximumThreshold) {
            state.status = "normal"
            smartFanOff()
            logInfo(
"""$fan turned off due to non-rapid rate: ${state.rate.setScale(2, RoundingMode.HALF_UP)}%/min. 
Total runtime: ${state.durationMinutes.setScale(0, RoundingMode.HALF_UP)} min."""
            )
        }
    }
    
    if (state.status == "rising") {
        runIn(60*state.risingMinutesToWait, risingRateTimeout)
    } else if (state.status == "falling") {
        runIn(60*state.fallingMinutesToWait, fallingRateTimeout)
    }
    
    logDebug("Status: ${state.status}")
    if (state.status != "normal") {
        logDebug("Start humidity: ${state.startHumidity}%")
        logDebug("Peak humidity: ${state.peakHumidity}%")
        logDebug("Target humidity: ${state.targetHumidity}%")
    }
}

def updateTargetHumidity() {
    state.targetHumidity = state.startHumidity + (state.peakHumidity - state.startHumidity)*(percentAboveStart/100.0)
}

def smartFanOn() {
    fan.on()
    
    state.startTime = now()
    state.startHumidity = state.previousHumidity
    state.peakHumidity = state.currentHumidity
    updateTargetHumidity()
}

def smartFanOff() {
    fan.off()
    
    state.endTime = now()
    state.durationMinutes = (state.endTime - state.startTime)/1000.0/60.0
}

def risingRateTimeout() {
    state.status = "peak"
    logInfo("$fan peak due to exceeding time for rapid rising rate: ${state.risingMinutesToWait} min")
}

def fallingRateTimeout() {
    if (state.currentHumidity < maximumThreshold) {
        state.status = "normal"
        smartFanOff()
        logInfo(
"""$fan turned off due to exceeding time for rapid falling rate: ${state.fallingMinutesToWait} min. 
Total runtime: ${state.durationMinutes.setScale(0, RoundingMode.HALF_UP)} min."""
        )
    }
}

def fanHandler_FanSwitch(evt) {
    logDebug("fanHandler_FanSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        runIn(60*maximumRuntime, totalRuntimeExceeded)
    } else {
        unschedule("risingRateTimeout")
        unschedule("fallingRateTimeout")
        unschedule("totalRuntimeExceeded")
    }
}

def totalRuntimeExceeded() {
    state.status = "normal"
    smartFanOff()
    logInfo(
"""$fan turned off due to exceeding total time: ${maximumRuntime} min. 
Total runtime: ${state.durationMinutes.setScale(0, RoundingMode.HALF_UP)} min."""
    )
}

def modeHandler_FanSwitch(evt) {
    logDebug("modeHandler_FanSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "Home") {
        fan.off()
    }
}