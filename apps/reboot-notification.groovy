/**
 *  Reboot Notification
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
 
String getVersionNum() { return "1.0.0-beta1" }
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
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
            input "message", "text", title: "Message Text", multiple: false, required: true
        }
        section {
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
    subscribe(location, "systemStart", handler)
}

def handler() {
    notifier.deviceNotification(message)
}