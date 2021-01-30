/**
 *  Life360 Refresh
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
 
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "Life360 Refresh, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Life360 Refresh",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Refreshes Life360 if presence sensors are inconsistent.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/life360-refresh")

preferences {
    page(name: "settings", title: "Life360 Refresh", install: true, uninstall: true) {
        section {
            input "refreshButton", "device.ApplicationRefreshButton", title: "Refresh Button", multiple: false, required: true
            input "alertRefreshed", "bool", title: "Alert when refreshed?", required: true, defaultValue: false
        }
        section("Person 1") {
            input "person1", "capability.presenceSensor", title: "Life360 Presence", multiple: false, required: true
            input "otherPresence1", "capability.presenceSensor", title: "Other Presence Sensors", multiple: true, required: true
            input "alertInconsistent1", "bool", title: "Alert when inconsistent?", required: true, defaultValue: false
        }
        section("Person 2") {
            input "person2", "capability.presenceSensor", title: "Life360 Presence", multiple: false, required: true
            input "otherPresence2", "capability.presenceSensor", title: "Other Presence Sensors", multiple: true, required: true
            input "alertInconsistent2", "bool", title: "Alert when inconsistent?", required: true, defaultValue: false
        }
        section {
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
    for (presenceSensor in otherPresence1) {
        subscribe(presenceSensor, "presence.present", presenceHandler_Person1)
    }
    
    for (presenceSensor in otherPresence2) {
        subscribe(presenceSensor, "presence.present", presenceHandler_Person2)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def presenceHandler_Person1(evt) {
    logDebug("presenceHandler_Person1: ${evt.device} changed to ${evt.value}")

    runIn(30, refresh_Person1)
}

def refresh_Person1() {
    if (person1.currentValue("presence") != "present") {
        refreshButton.refresh()
        if (alertRefreshed) {
            notifier.deviceNotification("Refreshing Life360! ($person1)")
        }
        if (alertInconsistent1) {
            runIn(60, alert_Person1)
        }
    }
}

def alert_Person1() {
    if (person1.currentValue("presence") != "present") {
        notifier.deviceNotification("$person1 may be incorrect!")
    }
}

def presenceHandler_Person2(evt) {
    logDebug("presenceHandler_Person2: ${evt.device} changed to ${evt.value}")

    runIn(30, refresh_Person2)
}

def refresh_Person2() {
    if (person2.currentValue("presence") != "present") {
        refreshButton.refresh()
        if (alertRefreshed) {
            notifier.deviceNotification("Refreshing Life360! ($person2)")
        }
        if (alertInconsistent2) {
            runIn(60, alert_Person2)
        }
    }
}

def alert_Person2() {
    if (person2.currentValue("presence") != "present") {
        notifier.deviceNotification("$person2 may be incorrect!")
    }
}