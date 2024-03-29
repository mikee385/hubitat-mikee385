/**
 *  Thermostat Automation
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
 
String getVersionNum() { return "7.1.0" }
String getVersionLabel() { return "Thermostat Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library

definition(
    name: "Thermostat Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Sets thermostats to home/away based on the mode.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/thermostat-automation.groovy"
)

preferences {
    page(name: "settings", title: "Thermostat Automation", install: true, uninstall: true) {
        section("Downstairs") {
            input "downstairsThermostat", "device.EcobeeThermostat", title: "Thermostat", multiple: false, required: false
            input "downstairsSensors", "device.EcobeeSensor", title: "Sensors", multiple: true, required: false
            input "downstairsThreshold", "decimal", title: "Temperature Difference for Alert (°)", required: true, defaultValue: 3
        }
        section("Upstairs") {
            input "upstairsThermostat", "device.EcobeeThermostat", title: "Thermostat", multiple: false, required: false
            input "upstairsSensors", "device.EcobeeSensor", title: "Sensors", multiple: true, required: false
            input "upstairsThreshold", "decimal", title: "Temperature Difference for Alert (°)", required: true, defaultValue: 3
        }
        section {
            input "workdayTime", "time", title: "Workday Resume Time", required: false
            input "sleepTime", "time", title: "Sleep Resume Time", required: false
        }
        section {
            input "alertTooHot", "bool", title: "Alert when Too Hot?", required: true, defaultValue: true
            input "alertTooCold", "bool", title: "Alert when Too Cold?", required: true, defaultValue: true
        }
        section {
            input "deviceMonitor", "device.DeviceMonitor", title: "Device Monitor", multiple: false, required: true
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
    state.mode = location.mode
    if (state.lastAlertTime == null) {
        state.lastAlertTime = [:]
    }
    
    // Thermostat
    subscribe(location, "mode", modeHandler_Thermostat)
    
    def currentTime = new Date()
    
    if (workdayTime) {
        def workdayToday = timeToday(workdayTime)
        schedule("$currentTime.seconds $workdayToday.minutes $workdayToday.hours ? * 2-6 *", workdayTimeHandler_Thermostat)
    }
    
    if (sleepTime) {
        def sleepToday = timeToday(sleepTime)
        schedule("$currentTime.seconds $sleepToday.minutes $sleepToday.hours * * ? *", sleepTimeHandler_Thermostat)
    }
    
    // Temperature Alert
    for (sensor in downstairsSensors) {
        subscribe(sensor, "temperature", temperatureHandler_DownstairsTemperatureAlert)
    }
    
    for (sensor in upstairsSensors) {
        subscribe(sensor, "temperature", temperatureHandler_UpstairsTemperatureAlert)
    }
    
    // Device Checks
    initializeDeviceChecks()
}

def modeHandler_Thermostat(evt) {
    logDebug("modeHandler_Thermostat: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        def currentTime = new Date()
        def sleepToday = timeToday(sleepTime)
        if (currentTime < sleepToday) {
            if (downstairsThermostat) {
                downstairsThermostat.setAway()
            }
            if (upstairsThermostat) {
                upstairsThermostat.setAway()
            }
        }
    } else if (state.mode == "Away") {
        if (downstairsThermostat) {
            downstairsThermostat.resumeProgram()
        }
        if (upstairsThermostat) {
            upstairsThermostat.resumeProgram()
        }
    }
    state.mode = evt.value
}

def workdayTimeHandler_Thermostat(evt) {
    logDebug("workdayTimeHandler_Thermostat")
    
    if (location.mode == "Away") {
        if (downstairsThermostat) {
            downstairsThermostat.resumeProgram()
        }
        if (upstairsThermostat) {
            upstairsThermostat.resumeProgram()
        }
    }
}

def sleepTimeHandler_Thermostat(evt) {
    logDebug("sleepTimeHandler_Thermostat")
    
    if (location.mode == "Away") {
        if (downstairsThermostat) {
            downstairsThermostat.resumeProgram()
        }
        if (upstairsThermostat) {
            upstairsThermostat.resumeProgram()
        }
    }
}

def temperatureHandler_DownstairsTemperatureAlert(evt) {
    logDebug("temperatureHandler_DownstairsTemperatureAlert: ${evt.device} changed to ${evt.value}")
    
    runIn(5, checkDownstairsTemperatures)
}

def checkDownstairsTemperatures() {
    checkTemperatures(downstairsSensors, downstairsThreshold)
}

def temperatureHandler_UpstairsTemperatureAlert(evt) {
    logDebug("temperatureHandler_UpstairsTemperatureAlert: ${evt.device} changed to ${evt.value}")
    
    runIn(5, checkUpstairsTemperatures)
}

def checkUpstairsTemperatures() {
    checkTemperatures(upstairsSensors, upstairsThreshold)
}

def checkTemperatures(sensors, threshold) {
    def temperatureSum = 0.0
    def temperatureCount = 0
    for (sensor in sensors) {
        temperatureSum += sensor.currentValue("temperature")
        temperatureCount += 1
    }
    def averageTemperature = temperatureSum / temperatureCount
    
    for (sensor in sensors) {
        def temperatureDifference = sensor.currentValue("temperature") - averageTemperature
        //log.info "$sensor: ${sensor.currentValue('temperature')} - ${averageTemperature} = $temperatureDifference"
        if (alertTooHot && temperatureDifference >= threshold) {
            temperatureAlert(sensor, "${sensor} is too hot! (${temperatureDifference}°)")
        } else if (alertTooCold && temperatureDifference <= -threshold) {
            temperatureAlert(sensor, "${sensor} is too cold! (${temperatureDifference}°)")
        }
    }
}

def temperatureAlert(sensor, message) {
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        def minutesSincePreviousAlert = Double.MAX_VALUE
        def previousAlertTime = state.lastAlertTime.get(sensor.id)
        if (previousAlertTime != null) {
            minutesSincePreviousAlert = (now() - previousAlertTime)/1000.0/60.0
            //log.info "$sensor: Previous alert was $minutesSincePreviousAlert minutes ago"
        } else {
            //log.info "$sensor: No previous alert"
        }
            
        if (minutesSincePreviousAlert >= 60) {
            state.lastAlertTime[sensor.id] = now()
            personToNotify.deviceNotification(message)
        } 
    }
}