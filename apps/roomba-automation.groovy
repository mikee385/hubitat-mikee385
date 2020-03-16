/**
 *  Roomba Automation
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
 
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "Roomba Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Roomba Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Updates the state of an Appliance Status device representing a Roomba.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/roomba-automation.groovy")

preferences {
    page(name: "settings", title: "Roomba Automation", install: true, uninstall: true) {
        section {
            input "appliance", "capability.actuator", title: "Roomba Status", multiple: false, required: true
        }
        section {
            input "awayRoutine", "capability.switch", title: "Routine for Start", multiple: false, required: true
            input "startTime", "time", title: "Start Time", required: true
        }
        section {
            input "homeRoutine", "capability.switch", title: "Routine for Finish", multiple: false, required: true
            input "runDuration", "number", title: "Minimum Duration (in minutes)", required: true
        }
        section {
            input "resetTime", "time", title: "Reset Time", required: true
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
    def currentTime = new Date()

    subscribe(appliance, "state.running", runningHandler)
    subscribe(appliance, "state.finished", finishedHandler)
    
    subscribe(awayRoutine, "switch.on", awayRoutineHandler)
    
    def startToday = timeToday(startTime)
    schedule("$currentTime.seconds $startToday.minutes $startToday.hours * * ? *", dailyStart)
    
    subscribe(homeRoutine, "switch.on", homeRoutineHandler)
    
    def resetToday = timeToday(resetTime)
    schedule("$currentTime.seconds $resetToday.minutes $resetToday.hours * * ? *", dailyReset)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def runningHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    state.runningStartTime = now()
}

def finishedHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    state.runningEndTime = now()
    
    def duration = state.runningEndTime - state.runningStartTime
    if (duration < (minimumDuration*60*1000)) {
        appliance.reset()
    }
}

def awayRoutineHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    def sunRiseSet = getSunriseAndSunset()
    def startToday = timeToday(startTime)
    def endToday = sunRiseSet.sunset
    
    if (appliance.currentValue("state") == "unstarted" && timeOfDayIsBetween(startToday, endToday, new Date(), location.timeZone)) {
        appliance.start()
    }
}

def dailyStart() {
    logDebug("Received daily start time")
    
    if (appliance.currentValue("state") == "unstarted" && location.mode == "Away") {
        appliance.start()
    }
}

def homeRoutineHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (appliance.currentValue("state") == "running") {
        appliance.finish()
    }
}

def dailyReset() {
    logDebug("Received daily reset time")
    
    if (appliance.currentValue("state") == "finished") {
        appliance.reset()
    }
}