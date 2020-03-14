/**
 *  Bedtime Triggers
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
String getVersionLabel() { return "Bedtime Triggers, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Bedtime Triggers",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Trigger routines for various bedtime actions.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/bedtime-triggers.groovy")

preferences {
    page(name: "settings", title: "Bedtime Triggers", install: true, uninstall: true) {
        section("Kid Bedtime Soon") {
            input "kidBedtimeSoonButton", "capability.pushableButton", title: "Button Device", multiple: false, required: true
            input "kidBedtimeSoonNumber", "number", title: "Button Number", required: true
            input "kidBedtimeSoonStartTime", "time", title: "Start Time", required: true
            input "kidBedtimeSoonEndTime", "time", title: "End Time", required: true
            input "kidBedtimeSoonRoutine", "capability.switch", title: "Routine", multiple: false, required: true
        }
        section("Kid Bedtime Now") {
            input "kidBedtimeNowButton", "capability.pushableButton", title: "Button Device", multiple: false, required: true
            input "kidBedtimeNowNumber", "number", title: "Button Number", required: true
            input "kidBedtimeNowStartTime", "time", title: "Start Time", required: true
            input "kidBedtimeNowEndTime", "time", title: "End Time", required: true
            input "kidBedtimeNowRoutine", "capability.switch", title: "Routine", multiple: false, required: true
        }
        section("Adult Bedtime") {
            input "adultBedtimeDoor", "capability.contactSensor", title: "Door", multiple: false, required: true
            input "adultBedtimeStartTime", "time", title: "Start Time", required: true
            input "adultBedtimeEndTime", "time", title: "End Time", required: true
            input "adultBedtimeRoutine", "capability.switch", title: "Routine", multiple: false, required: true
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
    subscribe(kidBedtimeSoonButton, "pushed.$kidBedtimeSoonNumber", kidBedtimeSoonHandler)
    
    subscribe(kidBedtimeNowButton, "pushed.$kidBedtimeNowNumber", kidBedtimeNowHandler)
    
    subscribe(adultBedtimeDoor, "contact.closed", adultBedtimeHandler)
    
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

def kidBedtimeSoonHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    def startToday = timeToday(kidBedtimeSoonStartTime)
    def endToday = timeToday(kidBedtimeSoonEndTime)
    
    if (timeOfDayIsBetween(startToday, endToday, new Date(), location.timeZone)) {
        kidBedtimeSoonRoutine.on()
    }
}

def kidBedtimeNowHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    def startToday = timeToday(kidBedtimeNowStartTime)
    def endToday = timeToday(kidBedtimeNowEndTime)
    
    if (timeOfDayIsBetween(startToday, endToday, new Date(), location.timeZone)) {
        kidBedtimeNowRoutine.on()
    }
}

def adultBedtimeHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    def startToday = timeToday(adultBedtimeStartTime)
    def endToday = timeToday(adultBedtimeEndTime)
    
    if (timeOfDayIsBetween(startToday, endToday, new Date(), location.timeZone)) {
        adultBedtimeRoutine.on()
    }
}