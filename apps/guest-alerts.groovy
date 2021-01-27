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
 
String getVersionNum() { return "2.2.0" }
String getVersionLabel() { return "Guest Alerts, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Guest Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Sends alerts when guests may have arrived or departed.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/guest-alerts.groovy")

preferences {
    page(name: "settings", title: "Guest Alerts", install: true, uninstall: true) {
        section {
            input "guest", "capability.presenceSensor", title: "Guest", multiple: false, required: true
            input "bedroomDoor", "capability.contactSensor", title: "Bedroom Door", multiple: false, required: true
            input "frontDoor", "capability.contactSensor", title: "Front Door", multiple: false, required: true
            input "primaryPerson", "device.PersonStatus", title: "Primary Person", multiple: false, required: true
            input "otherPeople", "capability.presenceSensor", title: "Other People", multiple: true, required: true
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
        }
        section {
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
        }
    }
}

def installed() {
    state.lockoutTime = now()
    state.lockoutString = new Date(state.lockoutTime)
    
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    
    initialize()
}

def initialize() {
    state.waitForBedroomDoor = false
    state.waitForFrontDoor = false
    
    // Guest Alert
    subscribe(bedroomDoor, "contact.open", bedroomDoorHandler_GuestAlert)
    subscribe(frontDoor, "contact.open", frontDoorHandler_GuestAlert)
    
    // Reminder Alert
    subscribe(primaryPerson, "presence.not present", personHandler_ReminderAlert)
    for (person in otherPeople) {
        subscribe(person, "presence.not present", personHandler_ReminderAlert)
    }
    
    // Away Alert
    subscribe(bedroomDoor, "contact", handler_AwayAlert)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def bedroomDoorHandler_GuestAlert(evt) {
    logDebug("bedroomDoorHandler_GuestAlert: ${evt.device} changed to ${evt.value}")
    
    if (now() < state.lockoutTime) {
        return
    }
    if (guest.currentValue("presence") == "not present" && primaryPerson.currentValue("status") == "sleep") {
        return
    }
    
    if (frontDoor.currentValue("contact") == "open") {
        guestAlert()
    } else if (state.waitForBedroomDoor) {
        guestAlert()
    } else {
        state.waitForFrontDoor = true
        runIn(10*60, cancelWaitForFrontDoor)
    }
}

def cancelWaitForFrontDoor() {
    state.waitForFrontDoor = false
}

def frontDoorHandler_GuestAlert(evt) {
    logDebug("frontDoorHandler_GuestAlert: ${evt.device} changed to ${evt.value}")
    
    if (now() < state.lockoutTime) {
        return
    }
    if (guest.currentValue("presence") == "not present" && primaryPerson.currentValue("status") == "sleep") {
        return
    }
    
    if (bedroomDoor.currentValue("contact") == "open") {
        guestAlert()
    } else if (state.waitForFrontDoor) {
        guestAlert()
    } else {
        state.waitForBedroomDoor = true
        runIn(10*60, cancelWaitForBedroomDoor)
    }
}

def cancelWaitForBedroomDoor() {
    state.waitForBedroomDoor = false
}

def guestAlert() {
    unschedule()

    state.lockoutTime = now() + (30*60*1000)
    state.lockoutString = new Date(state.lockoutTime)
    state.waitForBedroomDoor = false
    state.waitForFrontDoor = false
    
    if (guest.currentValue("presence") == "present") {
        notifier.deviceNotification("Are guests leaving?")
    } else {
        notifier.deviceNotification("Have guests arrived?")
    }
}

def personHandler_ReminderAlert(evt) {
    logDebug("personHandler_ReminderAlert: ${evt.device} changed to ${evt.value}")
    
    if (guest.currentValue("presence") == "present") {
        def everyoneLeft = primaryPerson.currentValue("presence") == "not present"
        for (person in otherPeople) {
            if (person.currentValue("presence") != "not present") {
                everyoneLeft = false
            }
        }
        if (everyoneLeft) {
            notifier.deviceNotification("Do you still have guests?")
        }
    }
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        notifier.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}