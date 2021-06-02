/**
 *  People Alerts
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
String getVersionLabel() { return "People Alerts, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "People Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Sends alerts when people arrive, depart, etc.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/people-alerts.groovy")

preferences {
    page(name: "settings", title: "People Alerts", install: true, uninstall: true) {
        section("Primary Person") {
            input "primaryPerson", "device.PersonStatus", title: "Person", multiple: false, required: true
            input "alertPrimaryArrived", "bool", title: "Alert when Arrived?", required: true, defaultValue: false
            input "alertPrimaryDeparted", "bool", title: "Alert when Departed?", required: true, defaultValue: false
            input "alertPrimaryAwake", "bool", title: "Alert when Awake?", required: true, defaultValue: false
            input "alertPrimaryAsleep", "bool", title: "Alert when Asleep?", required: true, defaultValue: false
        }
        section("Secondary Person") {
            input "secondaryPerson", "device.PersonStatus", title: "Person", multiple: false, required: true
            input "alertSecondaryArrived", "bool", title: "Alert when Arrived?", required: true, defaultValue: false
            input "alertSecondaryDeparted", "bool", title: "Alert when Departed?", required: true, defaultValue: false
            input "alertSecondaryAwake", "bool", title: "Alert when Awake?", required: true, defaultValue: false
            input "alertSecondaryAsleep", "bool", title: "Alert when Asleep?", required: true, defaultValue: false
        }
        section("Combined") {
            input "combinedArrivedSeconds", "number", title: "Arrival Time Window (seconds)", required: true, defaultValue: 15
            input "combinedDepartedSeconds", "number", title: "Departure Time Window (seconds)", required: true, defaultValue: 60
            input "alertCombinedArrived", "bool", title: "Alert when Arrived?", required: true, defaultValue: false
            input "alertCombinedDeparted", "bool", title: "Alert when Departed?", required: true, defaultValue: false
        }
        section("Guest") {
            input "guest", "capability.presenceSensor", title: "Guest", multiple: false, required: true
            input "alertGuestArrived", "bool", title: "Alert when Arrived?", required: true, defaultValue: false
            input "alertGuestDeparted", "bool", title: "Alert when Departed?", required: true, defaultValue: false
            input "alertReminder", "bool", title: "Reminder Alert?", required: true, defaultValue: false
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
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
    subscribe(primaryPerson, "command", personHandler_PrimaryAlert)
    subscribe(secondaryPerson, "command", personHandler_SecondaryAlert)
    subscribe(guest, "presence", personHandler_GuestAlert)

    // Guest Reminder
    subscribe(primaryPerson, "presence.not present", personHandler_GuestReminder)
    subscribe(secondaryPerson, "presence.not present", personHandler_GuestReminder)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def personHandler_PrimaryAlert(evt) {
    logDebug("personHandler_PrimaryAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "arrived") {
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
    } else if (evt.value == "departed") {
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
    } else if (evt.value == "awake") {
        if (alertPrimaryAwake) {
            personToNotify.deviceNotification("${primaryPerson} is awake!")
        }
    } else if (evt.value == "asleep") {
        if (alertPrimaryAsleep) {
            personToNotify.deviceNotification("${primaryPerson} is asleep!")
        }
    }
}

def personHandler_SecondaryAlert(evt) {
    logDebug("personHandler_SecondaryAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "arrived") {
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
    } else if (evt.value == "departed") {
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
    } else if (evt.value == "awake") {
        if (alertSecondaryAwake) {
            personToNotify.deviceNotification("${secondaryPerson} is awake!")
        }
    } else if (evt.value == "asleep") {
        if (alertSecondaryAsleep) {
            personToNotify.deviceNotification("${secondaryPerson} is asleep!")
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

def personHandler_GuestAlert(evt) {
    logDebug("personHandler_GuestAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "present") {
        if (alertGuestArrived) {
            personToNotify.deviceNotification("Guests have arrived!")
        }
    } else if (evt.value == "not present") {
        if (alertGuestDeparted) {
            personToNotify.deviceNotification("Guests have left!")
        }
    }
}

def personHandler_GuestReminder(evt) {
    logDebug("personHandler_GuestReminder: ${evt.device} changed to ${evt.value}")
    
    if (guest.currentValue("presence") == "present") {
        if (primaryPerson.currentValue("presence") == "not present" && secondaryPerson.currentValue("presence") == "not present") {
            personToNotify.deviceNotification("Do you still have guests?")
        }
    }
}