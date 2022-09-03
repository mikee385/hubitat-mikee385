/**
 *  People Alerts
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
 
String getVersionNum() { return "5.0.0" }
String getVersionLabel() { return "People Alerts, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library

definition(
    name: "People Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Sends alerts when people arrive, depart, etc.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/people-alerts.groovy"
)

preferences {
    page(name: "settings", title: "People Alerts", install: true, uninstall: true) {
        section("Primary Person") {
            input "primaryPerson", "device.PersonStatus", title: "Person", multiple: false, required: true
            input "alertPrimaryArrived", "bool", title: "Alert when Arrived?", required: true, defaultValue: false
            input "alertPrimaryDeparted", "bool", title: "Alert when Departed?", required: true, defaultValue: false
        }
        section("Secondary Person") {
            input "secondaryPerson", "device.PersonStatus", title: "Person", multiple: false, required: true
            input "alertSecondaryArrived", "bool", title: "Alert when Arrived?", required: true, defaultValue: false
            input "alertSecondaryDeparted", "bool", title: "Alert when Departed?", required: true, defaultValue: false
        }
        section("Combined") {
            input "combinedArrivedSeconds", "number", title: "Arrival Time Window (seconds)", required: true, defaultValue: 15
            input "combinedDepartedSeconds", "number", title: "Departure Time Window (seconds)", required: true, defaultValue: 60
            input "alertCombinedArrived", "bool", title: "Alert when Arrived?", required: true, defaultValue: false
            input "alertCombinedDeparted", "bool", title: "Alert when Departed?", required: true, defaultValue: false
        }
        section("Guest") {
            input "guest", "capability.presenceSensor", title: "Guest", multiple: false, required: true
            input "alertReminder", "bool", title: "Reminder Alert?", required: true, defaultValue: false
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
    if (state.primaryArrivedTime == null) {
        state.primaryArrivedTime = now() - (24*60*60*1000)
    }
    if (state.primaryDepartedTime == null) {
        state.primaryDepartedTime = now() - (24*60*60*1000)
    }
    if (state.secondaryArrivedTime == null) {
        state.secondaryArrivedTime = now() - (24*60*60*1000)
    }
    if (state.secondaryDepartedTime == null) {
        state.secondaryDepartedTime = now() - (24*60*60*1000)
    }

    // Person Alerts
    subscribe(primaryPerson, "presence", personHandler_PrimaryPresenceAlert)
    subscribe(secondaryPerson, "presence", personHandler_SecondaryPresenceAlert)

    // Guest Reminder
    subscribe(primaryPerson, "presence.not present", personHandler_GuestReminder)
    subscribe(secondaryPerson, "presence.not present", personHandler_GuestReminder)
    subscribe(location, "sunrise", sunriseHandler_GuestReminder)
    subscribe(location, "sunset", sunsetHandler_GuestReminder)
}

def personHandler_PrimaryPresenceAlert(evt) {
    logDebug("personHandler_PrimaryPresenceAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "present") {
        state.primaryArrivedTime = now()
        
        if (secondaryPerson.currentValue("presence") == "present") {
            deltaTime = state.primaryArrivedTime - state.secondaryArrivedTime
            if (deltaTime <= (combinedArrivedSeconds*1000)) {
                combinedArrivedAlert()
            } else {
                primaryArrivedAlert()
            }
        } else {
            runIn(combinedArrivedSeconds, primaryArrivedAlert)
        }
    } else if (evt.value == "not present") {
        state.primaryDepartedTime = now()
        
        if (secondaryPerson.currentValue("presence") == "not present") {
            deltaTime = state.primaryDepartedTime - state.secondaryDepartedTime
            if (deltaTime <= (combinedDepartedSeconds*1000)) {
                combinedDepartedAlert()
            } else {
                primaryDepartedAlert()
            }
        } else {
            runIn(combinedDepartedSeconds, primaryDepartedAlert)
        }
    }
}

def personHandler_SecondaryPresenceAlert(evt) {
    logDebug("personHandler_SecondaryPresenceAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "present") {
        state.secondaryArrivedTime = now()
        
        if (primaryPerson.currentValue("presence") == "present") {
            deltaTime = state.secondaryArrivedTime - state.primaryArrivedTime
            if (deltaTime <= (combinedArrivedSeconds*1000)) {
                combinedArrivedAlert()
            } else {
                secondaryArrivedAlert()
            }
        } else {
            runIn(combinedArrivedSeconds, secondaryArrivedAlert)
        }
    } else if (evt.value == "not present") {
        state.secondaryDepartedTime = now()
        
        if (primaryPerson.currentValue("presence") == "not present") {
            deltaTime = state.secondaryDepartedTime - state.primaryDepartedTime
            if (deltaTime <= (combinedDepartedSeconds*1000)) {
                combinedDepartedAlert()
            } else {
                secondaryDepartedAlert()
            }
        } else {
            runIn(combinedDepartedSeconds, secondaryDepartedAlert)
        }
    }
}

def primaryArrivedAlert() {
    if (alertPrimaryArrived) {
        personToNotify.deviceNotification("${primaryPerson} is home!")
    }
}

def primaryDepartedAlert() {
    if (alertPrimaryDeparted) {
        personToNotify.deviceNotification("${primaryPerson} has left!")
    }
}

def secondaryArrivedAlert() {
    if (alertSecondaryArrived) {
        personToNotify.deviceNotification("${secondaryPerson} is home!")
    }
}

def secondaryDepartedAlert() {
    if (alertSecondaryDeparted) {
        personToNotify.deviceNotification("${secondaryPerson} has left!")
    }
}

def combinedArrivedAlert() {
    unschedule("primaryArrivedAlert")
    unschedule("secondaryArrivedAlert")

    if (alertCombinedArrived) {
        personToNotify.deviceNotification("${primaryPerson} and ${secondaryPerson} are home!")
    }
}

def combinedDepartedAlert() {
    unschedule("primaryDepartedAlert")
    unschedule("secondaryDepartedAlert")

    if (alertCombinedDeparted) {
        personToNotify.deviceNotification("${primaryPerson} and ${secondaryPerson} have left!")
    }
}

def personHandler_GuestReminder(evt) {
    logDebug("personHandler_GuestReminder: ${evt.device} changed to ${evt.value}")
    
    if (primaryPerson.currentValue("presence") == "not present" && secondaryPerson.currentValue("presence") == "not present") {
        guestReminder()
    }
}

def sunriseHandler_GuestReminder(evt) {
    logDebug("sunriseHandler_GuestReminder: Received sunrise event")
    
    guestReminder()
}

def sunsetHandler_GuestReminder(evt) {
    logDebug("sunsetHandler_GuestReminder: Received sunset event")
    
    guestReminder()
}

def guestReminder() {
    if (guest.currentValue("presence") == "present") {
        personToNotify.deviceNotification("Do you still have guests?")
    }
}