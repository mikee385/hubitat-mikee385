/**
 *  Guest Alerts
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
 
String getVersionNum() { return "1.0.0-beta8" }
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
        section("") {
            input "guest", "capability.presenceSensor", title: "Guest", multiple: false, required: true
            
            input "bedroomDoor", "capability.contactSensor", title: "Bedroom Door", multiple: false, required: true
            input "frontDoor", "capability.contactSensor", title: "Front Door", multiple: false, required: true
            
            input "primaryPerson", "capability.presenceSensor", title: "Primary Person", multiple: false, required: true
            input "otherPeople", "capability.presenceSensor", title: "Other People", multiple: true, required: true
            
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
            
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
    state.lockoutTime = now()
    state.lockoutString = new Date(state.lockoutTime)
    state.waitForBedroomDoor = false
    state.waitForFrontDoor = false
    
    subscribe(bedroomDoor, "contact.open", bedroomDoorHandler)
    subscribe(frontDoor, "contact.open", frontDoorHandler)
    
    subscribe(primaryPerson, "presence.not present", personHandler)
    for (person in otherPeople) {
        subscribe(person, "presence.not present", personHandler)
    }

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

def bedroomDoorHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (now() < state.lockoutTime) {
        return
    }
    if (guest.currentValue("presence") == "not present" && primaryPerson.currentValue("state") == "sleep") {
        return
    }
    
    if (frontDoor.currentValue("contact") == "open") {
        sendAlert()
    } else if (state.waitForBedroomDoor) {
        sendAlert()
    } else {
        state.waitForFrontDoor = true
        runIn(10*60, cancelWaitForFrontDoor)
    }
}

def frontDoorHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (now() < state.lockoutTime) {
        return
    }
    if (guest.currentValue("presence") == "not present" && primaryPerson.currentValue("state") == "sleep") {
        return
    }
    
    if (bedroomDoor.currentValue("contact") == "open") {
        sendAlert()
    } else if (state.waitForFrontDoor) {
        sendAlert()
    } else {
        state.waitForBedroomDoor = true
        runIn(10*60, cancelWaitForBedroomDoor)
    }
}

def personHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
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

def sendAlert() {
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

def cancelWaitForBedroomDoor() {
    state.waitForBedroomDoor = false
}

def cancelWaitForFrontDoor() {
    state.waitForFrontDoor = false
}