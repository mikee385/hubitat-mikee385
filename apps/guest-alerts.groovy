/**
 *  Guest Alerts
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
 
String getVersionNum() { return "3.0.0" }
String getVersionLabel() { return "Guest Alerts, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Guest Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Sends a reminder alert to check if guests are still present.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/guest-alerts.groovy")

preferences {
    page(name: "settings", title: "Guest Alerts", install: true, uninstall: true) {
        section {
            input "guest", "capability.presenceSensor", title: "Guest", multiple: false, required: true
            input "otherPeople", "capability.presenceSensor", title: "Other People", multiple: true, required: true
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
    // Reminder Alert
    for (person in otherPeople) {
        subscribe(person, "presence.not present", personHandler_ReminderAlert)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def personHandler_ReminderAlert(evt) {
    logDebug("personHandler_ReminderAlert: ${evt.device} changed to ${evt.value}")
    
    if (guest.currentValue("presence") == "present") {
        def everyoneLeft = true
        for (person in otherPeople) {
            if (person.currentValue("presence") != "not present") {
                everyoneLeft = false
            }
        }
        if (everyoneLeft) {
            personToNotify.deviceNotification("Do you still have guests?")
        }
    }
}