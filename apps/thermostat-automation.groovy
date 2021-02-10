/**
 *  Thermostat Automation
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
 
String getVersionNum() { return "2.1.0" }
String getVersionLabel() { return "Thermostat Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Thermostat Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Sets thermostats to home/away based on the mode.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/thermostat-automation.groovy")

preferences {
    page(name: "settings", title: "Thermostat Automation", install: true, uninstall: true) {
        section {
            input "thermostats", "device.EcobeeThermostat", title: "Thermostats", multiple: true, required: true
            input "sensors", "device.EcobeeSensor", title: "Remote Sensors", multiple: true, required: false
        }
        section {
            input "workdayTime", "time", title: "Workday Resume Time", required: false
            input "sleepTime", "time", title: "Sleep Resume Time", required: false
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
    state.mode = location.mode
    
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
    
    // Away Alert
    for (thermostat in thermostats) {
        subscribe(thermostat, "motion.active", handler_AwayAlert)
    }
    for (sensor in sensors) {
        subscribe(sensor, "motion.active", handler_AwayAlert)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def modeHandler_Thermostat(evt) {
    logDebug("modeHandler_Thermostat: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        def currentTime = new Date()
        def sleepToday = timeToday(sleepTime)
        if (currentTime < sleepToday) {
            for (thermostat in thermostats) {
                thermostat.setAway()
            }
        }
    } else if (state.mode == "Away") {
        for (thermostat in thermostats) {
            thermostat.resumeProgram()
        }
    }
    state.mode = evt.value
}

def workdayTimeHandler_Thermostat(evt) {
    logDebug("workdayTimeHandler_Thermostat")
    
    if (location.mode == "Away") {
        for (thermostat in thermostats) {
            thermostat.resumeProgram()
        }
    }
}

def sleepTimeHandler_Thermostat(evt) {
    logDebug("sleepTimeHandler_Thermostat")
    
    if (location.mode == "Away") {
        for (thermostat in thermostats) {
            thermostat.resumeProgram()
        }
    }
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        person.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}