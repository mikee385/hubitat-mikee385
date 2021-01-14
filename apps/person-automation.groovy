/**
 *  Person Automation
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
 
String getVersionNum() { return "1.0.0-beta.2" }
String getVersionLabel() { return "Person Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Person Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Updates the status of a Person Status device using a presence sensor and a switch.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/person-automation.groovy")

preferences {
    page(name: "settings", title: "Person Automation", install: true, uninstall: true) {
        section {
            input "person", "device.PersonStatus", title: "Person Status", multiple: false, required: true
            input "presenceSensor", "capability.presenceSensor", title: "Presence Sensor", multiple: false, required: false
            input "sleepSwitch", "capability.switch", title: "Sleep Switch", multiple: false, required: false
        }
        section("Alerts") {
            input "alertArrived", "bool", title: "Alert when Arrived?", required: true, defaultValue: false
            input "alertDeparted", "bool", title: "Alert when Departed?", required: true, defaultValue: false
            input "alertAwake", "bool", title: "Alert when Awake?", required: true, defaultValue: false
            input "alertAsleep", "bool", title: "Alert when Asleep?", required: true, defaultValue: false
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
        }
        section {
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/arrived") {
        action: [
            GET: "urlHandler_arrived"
        ]
    }
    path("/departed") {
        action: [
            GET: "urlHandler_departed"
        ]
    }
    path("/awake") {
        action: [
            GET: "urlHandler_awake"
        ]
    }
    path("/asleep") {
        action: [
            GET: "urlHandler_asleep"
        ]
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
    if (presenceSensor) {
        // Person Status
        subscribe(presenceSensor, "presence", presenceHandler_PersonStatus)
    }
    
    if (sleepSwitch) {
        // Person Status
        subscribe(sleepSwitch, "switch", switchHandler_PersonStatus)
    
        // Switch
        subscribe(location, "mode", modeHandler_Switch)
    
        // Away Alert
        subscribe(sleepSwitch, "switch.on", handler_AwayAlert)
    }
    
    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.arrivedUrl = "${getFullLocalApiServerUrl()}/arrived?access_token=$state.accessToken"
    state.departedUrl = "${getFullLocalApiServerUrl()}/departed?access_token=$state.accessToken"
    state.awakeUrl = "${getFullLocalApiServerUrl()}/awake?access_token=$state.accessToken"
    state.asleepUrl = "${getFullLocalApiServerUrl()}/asleep?access_token=$state.accessToken"
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def arrived() {
    if (person.currentValue("presence") == "not present") {
        person.arrived()
        if (alertArrived) {
            notifier.deviceNotification("$person is home!")
        }
    }
}

def departed() {
    if (person.currentValue("presence") == "present") {
        person.departed()
        if (alertDeparted) {
            notifier.deviceNotification("$person has left!")
        }
    }
}

def awake() {
    if (person.currentValue("status") == "sleep") {
        person.awake()
        if (alertAwake) {
            notifier.deviceNotification("$person is awake!")
        }
    }
}

def asleep() {
    if (person.currentValue("status") == "home") {
        person.asleep()
        if (alertAsleep) {
            notifier.deviceNotification("$person is asleep!")
        }
    }
}

def presenceHandler_PersonStatus(evt) {
    logDebug("presenceHandler_PersonStatus: ${evt.device} changed to ${evt.value}")

    if (evt.value == "present") {
        arrived()
    } else {
        departed()
    }
}

def switchHandler_PersonStatus(evt) {
    logDebug("switchHandler_PersonStatus: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        asleep()
    } else {
        awake()
    }
}

def modeHandler_Switch(evt) {
    logDebug("modeHandler_Switch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        sleepSwitch.off()
    }
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        notifier.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}

def urlHandler_arrived() {
    logDebug("urlHandler_arrived")
    
    arrived()
}

def urlHandler_departed() {
    logDebug("urlHandler_departed")
    
    departed()
}

def urlHandler_awake() {
    logDebug("urlHandler_awake")
    
    awake()
}

def urlHandler_asleep() {
    logDebug("urlHandler_asleep")
    
    asleep()
}