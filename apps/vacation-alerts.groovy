/**
 *  Vacation Alerts
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
 
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "Vacation Alerts, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library

definition(
    name: "Vacation Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Alerts when mode is Away for an extended period of time.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/vacation-alerts.groovy"
)

preferences {
    page(name: "settings", title: "Vacation Alerts", install: true, uninstall: true) {
        section {
            input "durationMinutes", "number", title: "Alert when Away (in minutes)", required: true, defaultValue: 1440
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
    // Vacation Alert
    subscribe(location, "mode", modeHandler_VacationAlert)
}

def modeHandler_VacationAlert(evt) {
    logDebug("modeHandler_VacationAlert: ${evt.device} changed to ${evt.value}")
    
    unschedule("vacationAlert")
    if (evt.value == "Away") {
        runIn(60*durationMinutes, vacationAlert)
    }
}

def vacationAlert() {
    personToNotify.deviceNotification("Are you on vacation?")
}