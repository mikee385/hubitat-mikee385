/**
 *  Reboot Notification
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
 
String getVersionNum() { return "1.1.0" }
String getVersionLabel() { return "Reboot Notification, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Reboot Notification",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Sends a notification when the hub reboots.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/reboot-notification.groovy")

preferences {
    page(name: "settings", title: "Reboot Notification", install: true, uninstall: true) {
        section {
            input "person", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
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
    subscribe(location, "systemStart", rebootHandler)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def rebootHandler(evt) {
    logDebug("rebootHandler: ${evt.device} changed to ${evt.value}")
    
    person.deviceNotification("Hub has rebooted.")
}