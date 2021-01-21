/**
 *  Roomba Automation
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
 
String getVersionNum() { return "3.1.1" }
String getVersionLabel() { return "Roomba Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Roomba Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Starts and stops the Roomba based on a schedule and presence.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/roomba-automation.groovy")

preferences {
    page(name: "settings", title: "Roomba Automation", install: true, uninstall: true) {
        section {
            input "roomba", "device.Roomba", title: "Roomba", multiple: false, required: true
        }
        section {
            input "startTime", "time", title: "Start Time", required: true
            input "minimumDuration", "number", title: "Minimum Duration (in minutes)", required: true
            input "resetTime", "time", title: "Reset Time", required: true
        }
        section("Alerts") {
            input "alertStarted", "bool", title: "Alert when Started?", required: true, defaultValue: false
            input "alertFinished", "bool", title: "Alert when Finished?", required: true, defaultValue: false
            input "alertReset", "bool", title: "Alert when Reset?", required: true, defaultValue: false
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
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
    state.startTime = now()
    state.endTime = now()
    state.duration = 0

    subscribe(roomba, "cleanStatus", roombaHandler)
    
    subscribe(location, "mode", modeHandler)
    
    def currentTime = new Date()
    
    def startToday = timeToday(startTime)
    schedule("$currentTime.seconds $startToday.minutes $startToday.hours * * ? *", dailyStart)
    
    def resetToday = timeToday(resetTime)
    schedule("$currentTime.seconds $resetToday.minutes $resetToday.hours * * ? *", dailyReset)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def roombaHandler(evt) {
    logDebug("roombaHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "cleaning") {
        state.startTime = now()
        
        if (alertStarted) {
            notifier.deviceNotification("$roomba has started!")
        }
    } else if (state.endTime < state.startTime) { // should only be true while Roomba is running
        state.endTime = now()
        state.duration += state.endTime - state.startTime
    }
    
    if (evt.value == "charging" && alertFinished) {
        notifier.deviceNotification("$roomba has cleaned for ${Math.round(state.duration/60/1000)} minutes today!")
    }
}

def modeHandler(evt) {
    logDebug("modeHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        logDebug("Away!"
        logDebug("cleanStatus = ${roomba.currentValue('cleanStatus')}")
        logDebug("startTime = ${timeToday(startTime)}")
        logDebug("sunset = ${location.sunset}")
        logDebug("current = ${new Date()}")
        logDebug("duration = ${state.duration}")
        logDebug("minimumDuration = ${minimumDuration*60*1000}")
        logDebug("minimumMinutes = ${minimumDuration}")
        
        if (roomba.currentValue("cleanStatus") != "cleaning" && timeOfDayIsBetween(timeToday(startTime), location.sunset, new Date(), location.timeZone) && state.duration < minimumDuration*60*1000) {
            logDebug("Starting!")
            roomba.start()
        }
    } else {
        if (roomba.currentValue("cleanStatus") == "cleaning") {
            logDebug("Docking!")
            roomba.dock()
        }
    }
}

def dailyStart() {
    logDebug("dailyStart")
    
    if (location.mode == "Away" && roomba.currentValue("cleanStatus") != "cleaning" && state.duration < minimumDuration*60*1000) {
        roomba.start()
    }
}

def dailyReset() {
    logDebug("dailyReset")
    
    state.duration = 0
    
    if (alertReset) {
        notifier.deviceNotification("$roomba has reset!")
    }
}