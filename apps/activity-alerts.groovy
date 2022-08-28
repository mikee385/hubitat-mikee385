/**
 *  Activity Alerts
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
 
String getVersionNum() { return "1.2.0" }
String getVersionLabel() { return "Activity Alerts, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library

definition(
    name: "Activity Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Sends alerts when certain doors and switches are active after bedtime",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/activity-alerts.groovy"
)

preferences {
    page(name: "settings", title: "Activity Alerts", install: true, uninstall: true) {
        section {
            input "person", "capability.presenceSensor", title: "Person", multiple: false, required: false
        }
        section("Doors") {
            input "bedroomDoor", "capability.contactSensor", title: "Bedroom Door", multiple: false, required: false
            input "otherDoors", "capability.contactSensor", title: "Other Doors", multiple: true, required: false
        }
        section("Bedtime") {
            input "bedtimeStart", "time", title: "Start Time", required: false
            input "bedtimeEnd", "time", title: "End Time", required: false
        }
        section("Alerts") {
            input "alertArmed", "bool", title: "Alert when armed?", required: true, defaultValue: false
            input "alertDisrmed", "bool", title: "Alert when disarmed?", required: true, defaultValue: false
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
    // Create state
    if (person) {
        state.personName = person.displayName
    }
    if (bedroomDoor) {
        state.personName = bedroomDoor.displayName.split(" ")[0]
    }
    for (door in otherDoors) {
        state.personName = door.displayName.split(" ")[0]
        break
    }

    // Arm & Disarm
    if (person) {
        if (person.hasCapability("SleepSensor")) {
            subscribe(person, "sleeping", personHandler_Arm)
        } 
    } else if (bedroomDoor) {
        subscribe(bedroomDoor, "contact", doorHandler_Arm)
    }
    
    // Activity
    if (bedroomDoor) {
        subscribe(bedroomDoor, "contact", doorHandler_Activity)
    }
    for (door in otherDoors) {
        subscribe(door, "contact", doorHandler_Activity)
    }
}

def personHandler_Arm(evt) {
    logDebug("personHandler_Arm: ${evt.device} changed to ${evt.value}")
    
    unschedule("arm")
    unschedule("disarm")
    
    if (evt.value == "sleeping") {
        if (!(state.armed == true)) {
            arm()
        } 
    } else if (!(state.armed == false) && evt.value == "not sleeping") {
        if (!(state.armed == false)) {
            disarm()
        } 
    }
}

def doorHandler_Arm(evt) {
    logDebug("doorHandler_Arm: ${evt.device} changed to ${evt.value}")
    
    unschedule("arm")
    unschedule("disarm")
    
    if (evt.value == "closed") {
        if (!(state.armed == true) && timeOfDayIsBetween(timeToday(bedTimeStart), timeToday(bedTimeEnd), new Date(), location.timeZone)) {
            runIn(10*60, arm)
        } 
    } else if (evt.value == "open") {
        if (!(state.armed == false) && timeOfDayIsBetween(timeToday(bedTimeEnd), timeToday(bedTimeStart), new Date(), location.timeZone)) {
            runIn(10*60, disarm)
        } 
    }
}

def arm() {
    state.armed = true
        
    if (alertArmed) {
        personToNotify.deviceNotification("${state.personName} is armed!")
    }
}

def disarm() {
    state.armed = false
        
    if (alertDisrmed) {
        personToNotify.deviceNotification("${state.personName} is disarmed!")
    }
}

def doorHandler_Activity(evt) {
    logDebug("doorHandler_Activity: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "present" && (!person || person.currentValue("presence") == "present")) {
        if (personToNotify.currentValue("sleeping") == "sleeping") {
            sendAlert(evt)
        } else if (state.armed) {
            sendAlert(evt)
        }
    }
}

def sendAlert(evt) {
    personToNotify.deviceNotification("${state.personName} is active!")
}