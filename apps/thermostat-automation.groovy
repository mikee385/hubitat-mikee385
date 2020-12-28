/**
 *  Thermostat Automation
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
 
String getVersionNum() { return "2.0.0-beta.1" }
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
            input "thermostats", "capability.thermostat", title: "Thermostats", multiple: true, required: true
        }
        section {
            input "workdayTime", "time", title: "Workday Resume Time", required: false
            
            input "sleepTime", "time", title: "Sleep Resume Time", required: false
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
    state.mode = location.mode
    
    subscribe(location, "mode", modeHandler)
    
    def currentTime = new Date()
    
    if (workdayTime) {
        def workdayToday = timeToday(workdayTime)
        schedule("$currentTime.seconds $workdayToday.minutes $workdayToday.hours ? * 2-6 *", resumeForWorkday)
    }
    
    if (sleepTime) {
        def sleepToday = timeToday(sleepTime)
        schedule("$currentTime.seconds $sleepToday.minutes $sleepToday.hours * * ? *", resumeForSleep)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def modeHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
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

def resumeForWorkday(evt) {
    logDebug("Received workday time event")
    
    if (location.mode == "Away") {
        for (thermostat in thermostats) {
            thermostat.resumeProgram()
        }
    }
}

def resumeForSleep(evt) {
    logDebug("Received sleep time event")
    
    if (location.mode == "Away") {
        for (thermostat in thermostats) {
            thermostat.resumeProgram()
        }
    }
}