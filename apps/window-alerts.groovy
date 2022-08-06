/**
 *  Window Alerts
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
 
String getVersionNum() { return "4.2.0" }
String getVersionLabel() { return "Window Alerts, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-check-library

definition(
    name: "Window Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Alerts for the windows.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/window-alerts.groovy"
)

preferences {
    page(name: "settings", title: "Window Alerts", install: true, uninstall: true) {
        section {
            input "windows", "capability.contactSensor", title: "Windows", multiple: true, required: true
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input "deviceChecker", "device.DeviceChecker", title: "Device Checker", multiple: false, required: true
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
    // Window Alert
    for (window in windows) {
        subscribe(window, "contact", windowHandler_WindowAlert)
    }
    subscribe(personToNotify, "presence", personHandler_WindowAlert)
    subscribe(personToNotify, "sleeping", personHandler_WindowAlert)
    
    // Device Checks
    initializeDeviceChecks()
}

def windowHandler_WindowAlert(evt) {
    logDebug("windowHandler_WindowAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            unschedule("windowAlert")
            runIn(60*5, windowAlert)
        }
    } else {
        def allWindowsClosed = true
        for (window in windows) {
            if (window.currentValue("contact") == "open") {
                allWindowsClosed = false
                break
            }
        }
        if (allWindowsClosed) {
            unschedule("windowAlert")
        }
    }
}

def personHandler_WindowAlert(evt) {
    logDebug("personHandler_WindowAlert: ${evt.device} changed to ${evt.value}")
    
    if (personToNotify.currentValue("presence") == "not present" || personToNotify.currentValue("sleeping") == "sleeping") {
        unschedule("windowAlert")
        
        for (window in windows) {
            if (window.currentValue("contact") == "open") {
                personToNotify.deviceNotification("$window is still open!")
            }
        }
    }
}

def windowAlert() {
    def anyWindowOpen = false
    for (window in windows) {
        if (window.currentValue("contact") == "open") {
            personToNotify.deviceNotification("Should the ${evt.device} still be open?")
            anyWindowOpen = true
        }
    }
    if (anyWindowOpen) {
        runIn(60*30, windowAlert)
    }
}