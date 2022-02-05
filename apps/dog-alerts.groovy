/**
 *  Dog Alerts
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
 
String getVersionNum() { return "2.0.0" }
String getVersionLabel() { return "Dog Alerts, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library

definition(
    name: "Dog Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Alerts for chores related to the dogs.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/dog-alerts.groovy"
)

preferences {
    page(name: "settings", title: "Dog Alerts", install: true, uninstall: true) {
        section {
            input "backyardDoor", "capability.contactSensor", title: "Backyard Door", multiple: false, required: false
            input "foodDoor", "capability.contactSensor", title: "Food Door", multiple: false, required: false
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
    if (state.backyardAlertSent == null) {
        state.backyardAlertSent = false
    }
    if (state.foodAlertSent == null) {
        state.foodAlertSent = false
    }

    if (backyardDoor) {
        subscribe(backyardDoor, "contact.open", backyardDoorHandler)
    }
    
    if (foodDoor) {
        subscribe(foodDoor, "contact.open", foodDoorHandler)
    }
    
    subscribe(personToNotify, "sleeping", personHandler)
}

def backyardDoorHandler(evt) {
    logDebug("backyardDoorHandler: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("sleeping") == "sleeping" && state.backyardAlertSent == false) {
        personToNotify.deviceNotification("Dogs have gone out!")
        state.backyardAlertSent = true
    }
}

def foodDoorHandler(evt) {
    logDebug("foodDoorHandler: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("sleeping") == "sleeping" && state.foodAlertSent == false) {
        personToNotify.deviceNotification("Dogs have been fed!")
        state.foodAlertSent = true
    }
}

def personHandler(evt) {
    logDebug("personHandler: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleeping") {
        state.backyardAlertSent = false
        state.foodAlertSent = false
    }
}