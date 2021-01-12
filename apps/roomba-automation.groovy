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
 
String getVersionNum() { return "3.0.0-beta.3" }
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
            input "startAlert", "bool", title: "Alert when Started?", required: true, defaultValue: false
            input "dockedAlert", "bool", title: "Alert when Docked?", required: true, defaultValue: false
            input "durationAlert", "bool", title: "Alert when Duration Changed?", required: true, defaultValue: false
            input "resetAlert", "bool", title: "Alert when Reset?", required: true, defaultValue: false
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
    subscribe(roomba, "cleanStatus", roombaHandler)
    
    //subscribe(location, "mode", modeHandler)
    
    def currentTime = new Date()
    
    def startToday = timeToday(startTime)
    //schedule("$currentTime.seconds $startToday.minutes $startToday.hours * * ? *", dailyStart)
    
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
        
        if (startAlert) {
            notifier.deviceNotification("$roomba has started!")
        }
    } else if (state.endTime < state.startTime) { // should only be true while Roomba is running
        state.endTime = now()
        state.duration += state.endTime - state.startTime
        
        if (dockedAlert) {
            notifier.deviceNotification("$roomba has finished!")
        }
        if (durationAlert) {
            notifier.deviceNotification("$roomba runtime = ${round(state.duration/60/1000)} minutes")
        }
    }
}

def modeHandler(evt) {
    logDebug("modeHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        if (roomba.currentValue("cleanStatus") != "cleaning" && timeOfDayIsBetween(timeToday(startTime), location.sunset, new Date(), location.timeZone) && state.duration < minimumDuration*60*1000) {
            roomba.start()
        }
    } else {
        if (roomba.currentValue("cleanStatus") == "cleaning") {
            roomba.dock()
        }
    }
}

def dailyStart() {
    logDebug("dailyStart")
    
    if (location.mode == "Away" && roomba.currentValue("status") == "docked" && state.duration < minimumDuration*60*1000) {
        roomba.start()
    }
}

def dailyReset() {
    logDebug("dailyReset")
    
    state.duration = 0
    
    if (resetAlert) {
        notifier.deviceNotification("$roomba has reset!")
    }
}