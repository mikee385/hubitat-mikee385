/**
 *  Window Alerts
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
String getVersionLabel() { return "Window Alerts, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Window Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Alerts for the windows.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/window-alerts.groovy")

preferences {
    page(name: "settings", title: "Window Alerts", install: true, uninstall: true) {
        section {
            input "windows", "capability.contactSensor", title: "Windows", multiple: true, required: true
        }
        section("Alerts") {
            input "person", "device.PersonStatus", title: "Person", multiple: false, required: true
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
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
    // Window Alert
    for (window in windows) {
        subscribe(window, "contact", windowHandler_WindowAlert)
    }
    subscribe(person, "status", personHandler_WindowAlert)
    
    // Away Alert
    for (window in windows) {
        subscribe(window, "contact", handler_AwayAlert)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def windowHandler_WindowAlert(evt) {
    logDebug("windowHandler_WindowAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (person.currentValue("status") == "home") {
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
    
    if (evt.value != "home") {
        unschedule("windowAlert")
        
        for (window in windows) {
            if (window.currentValue("contact") == "open") {
                notifier.deviceNotification("$window is still open!")
            }
        }
    }
}

def windowAlert() {
    def anyWindowOpen = false
    for (window in windows) {
        if (window.currentValue("contact") == "open") {
            notifier.deviceNotification("Should the ${evt.device} still be open?")
            anyWindowOpen = true
        }
    }
    if (anyWindowOpen) {
        runIn(60*30, windowAlert)
    }
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        notifier.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}