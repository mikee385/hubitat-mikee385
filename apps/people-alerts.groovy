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
 
String getVersionNum() { return "1.0.1" }
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
        if (alertPrimaryArrived) {
            personToNotify.deviceNotification("${evt.device} is home!")
        }
    } else if (evt.value == "departed") {
        if (alertPrimaryDeparted) {
            personToNotify.deviceNotification("${evt.device} has left!")
        }
    } else if (evt.value == "awake") {
        if (alertPrimaryAwake) {
            personToNotify.deviceNotification("${evt.device} is awake!")
        }
    } else if (evt.value == "asleep") {
        if (alertPrimaryAsleep) {
            personToNotify.deviceNotification("${evt.device} is asleep!")
        }
    }
}

def personHandler_SecondaryAlert(evt) {
    logDebug("personHandler_SecondaryAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "arrived") {
        if (alertSecondaryArrived) {
            personToNotify.deviceNotification("${evt.device} is home!")
        }
    } else if (evt.value == "departed") {
        if (alertSecondaryDeparted) {
            personToNotify.deviceNotification("${evt.device} has left!")
        }
    } else if (evt.value == "awake") {
        if (alertSecondaryAwake) {
            personToNotify.deviceNotification("${evt.device} is awake!")
        }
    } else if (evt.value == "asleep") {
        if (alertSecondaryAsleep) {
            personToNotify.deviceNotification("${evt.device} is asleep!")
        }
    }
}

def personHandler_GuestAlert(evt) {
    logDebug("personHandler_GuestAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "present") {
        if (alertGuestArrived) {
            personToNotify.deviceNotification("${evt.device} has arrived!")
        }
    } else if (evt.value == "not present") {
        if (alertGuestDeparted) {
            personToNotify.deviceNotification("${evt.device} has left!")
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