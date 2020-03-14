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
 
String getVersionNum() { return "1.0.0-beta2" }
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
            input "awayRoutines", "capability.switch", title: "Routines for Away", multiple: true, required: false
            input "resumeRoutines", "capability.switch", title: "Routines for Resume", multiple: true, required: false
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
    if (awayRoutines) {
        for (routine in awayRoutines) {
            subscribe(routine, "switch.on", awayHandler)
        }
    }
        
    if (resumeRoutines) {
        for (routine in resumeRoutines) {
            subscribe(routine, "switch.on", resumeHandler)
        }
    }
    
    def currentTime = new Date()
    
    if (workdayTime) {
        def workdayToday = timeToday(workdayTime)
        schedule("$currentTime.seconds $workdayToday.minutes $workdayToday.hours ? * 1-5 *", resumeForWorkday)
    }
    
    if (sleepTime) {
        def sleepToday = timeToday(sleepTime)
        schedule("$currentTime.seconds $sleepToday.minutes $sleepToday.hours * * ? *", resumeForSleep)
    }
    
    //if (logEnable) {
    //    log.warn "Debug logging enabled for 30 minutes"
    //    runIn(1800, logsOff)
    //}
}

def logsOff(){
    log.warn "Debug logging disabled"
    app.updateSetting("logEnable", [value: "false", type: "bool"])
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def awayHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    for (thermostat in thermostats) {
        thermostat.setAway()
    }
}

def resumeHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    for (thermostat in thermostats) {
        thermostat.resumeProgram()
    }
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