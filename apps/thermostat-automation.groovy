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
 
String getVersionNum() { return "4.1.1" }
String getVersionLabel() { return "Thermostat Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.away-alert-library
#include mikee385.inactive-alert-library

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
            input "downstairsBaseline", "device.EcobeeSensor", title: "Baseline Sensor", multiple: false, required: false
            input "downstairsSensors", "device.EcobeeSensor", title: "Additional Sensors", multiple: true, required: false
            input "downstairsThreshold", "decimal", title: "Temperature Difference for Alert (°)", required: true, defaultValue: 3
        }
        section("Upstairs") {
            input "upstairsThermostat", "device.EcobeeThermostat", title: "Thermostat", multiple: false, required: false
            input "upstairsBaseline", "device.EcobeeSensor", title: "Baseline Sensor", multiple: false, required: false
            input "upstairsSensors", "device.EcobeeSensor", title: "Additional Sensors", multiple: true, required: false
            input "upstairsThreshold", "decimal", title: "Temperature Difference for Alert (°)", required: true, defaultValue: 3
        }
        section {
            input "workdayTime", "time", title: "Workday Resume Time", required: false
            input "sleepTime", "time", title: "Sleep Resume Time", required: false
        }
        section {
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
    if (downstairsBaseline && downstairsSensors) {
        subscribe(downstairsBaseline, "temperature", temperatureHandler_DownstairsTemperatureAlert)
        for (sensor in downstairsSensors) {
            subscribe(sensor, "temperature", temperatureHandler_DownstairsTemperatureAlert)
        }
    }
    
    if (upstairsBaseline && upstairsSensors) {
        subscribe(upstairsBaseline, "temperature", temperatureHandler_UpstairsTemperatureAlert)
        for (sensor in upstairsSensors) {
            subscribe(sensor, "temperature", temperatureHandler_UpstairsTemperatureAlert)
        }
    }
    
    // Away Alert
    if (downstairsThermostat) {
        subscribe(downstairsThermostat, "motion.active", handler_AwayAlert)
    }
    if (downstairsBaseline) {
        subscribe(downstairsBaseline, "motion.active", handler_AwayAlert)
    } 
    for (sensor in downstairsSensors) {
        subscribe(sensor, "motion.active", handler_AwayAlert)
    }
    
    if (upstairsThermostat) {
        subscribe(upstairsThermostat, "motion.active", handler_AwayAlert)
    }
    if (upstairsBaseline) {
        subscribe(upstairsBaseline, "motion.active", handler_AwayAlert)
    } 
    for (sensor in upstairsSensors) {
        subscribe(sensor, "motion.active", handler_AwayAlert)
    }
    
    // Inactive Alert
    scheduleInactiveCheck()
}

def getInactiveThresholds() {
    def thresholds = []
    
    if (downstairsThermostat) {
        thresholds.add([device: downstairsThermostat, inactiveHours: 1])
    }
    if (downstairsBaseline) {
        thresholds.add([device: downstairsBaseline, inactiveHours: 1])
    }
    for (sensor in downstairsSensors) {
        thresholds.add([device: sensor, inactiveHours: 1])
    }
    
    if (upstairsThermostat) {
        thresholds.add([device: upstairsThermostat, inactiveHours: 1])
    }
    if (upstairsBaseline) {
        thresholds.add([device: upstairsBaseline, inactiveHours: 1])
    }
    for (sensor in upstairsSensors) {
        thresholds.add([device: sensor, inactiveHours: 1])
    }
    
    return thresholds
}

def getUnchangedThresholds() {
    def thresholds = []
    
    if (downstairsThermostat) {
        thresholds.add([device: downstairsThermostat, attribute: "temperature", inactiveHours: 6])
    }
    if (downstairsBaseline) {
        thresholds.add([device: downstairsBaseline, attribute: "temperature", inactiveHours: 6])
    }
    for (sensor in downstairsSensors) {
        thresholds.add([device: sensor, attribute: "temperature", inactiveHours: 6])
    }
    
    if (upstairsThermostat) {
        thresholds.add([device: upstairsThermostat, attribute: "temperature", inactiveHours: 6])
    }
    if (upstairsBaseline) {
        thresholds.add([device: upstairsBaseline, attribute: "temperature", inactiveHours: 6])
    }
    for (sensor in upstairsSensors) {
        thresholds.add([device: sensor, attribute: "temperature", inactiveHours: 6])
    }
    
    return thresholds
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
    for (sensor in downstairsSensors) {
        checkTemperature(downstairsBaseline, sensor, downstairsThreshold)
    }
}

def temperatureHandler_UpstairsTemperatureAlert(evt) {
    logDebug("temperatureHandler_UpstairsTemperatureAlert: ${evt.device} changed to ${evt.value}")
    
    runIn(5, checkUpstairsTemperatures)
}

def checkUpstairsTemperatures() {
    for (sensor in upstairsSensors) {
        checkTemperature(upstairsBaseline, sensor, upstairsThreshold)
    }
}

def checkTemperature(baseline, sensor, threshold) {
    def temperatureDifference = sensor.currentValue("temperature") - baseline.currentValue("temperature")
    //log.info "$sensor: ${sensor.currentValue('temperature')} - ${baseline.currentValue('temperature')} = $temperatureDifference"
    if (temperatureDifference >= threshold) {
        temperatureAlert(sensor, "${sensor} is too hot! (${temperatureDifference}°)")
    } else if (temperatureDifference <= -threshold) {
        temperatureAlert(sensor, "${sensor} is too cold! (${temperatureDifference}°)")
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