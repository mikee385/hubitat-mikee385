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
 
String getVersionNum() { return "2.0.0" }
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
            input "arrivalSensors", "capability.presenceSensor", title: "Arrival Sensors", multiple: true, required: false
            input "departureSensors", "capability.presenceSensor", title: "Departure Sensors", multiple: true, required: false
            input "sleepSwitch", "capability.switch", title: "Sleep Switch", multiple: false, required: false
            input "notificationDevices", "capability.notification", title: "Notification Devices", multiple: true, required: false
        }
        section("Alerts") {
            input "alertArrived", "bool", title: "Alert when Arrived?", required: true, defaultValue: false
            input "alertDeparted", "bool", title: "Alert when Departed?", required: true, defaultValue: false
            input "alertAwake", "bool", title: "Alert when Awake?", required: true, defaultValue: false
            input "alertAsleep", "bool", title: "Alert when Asleep?", required: true, defaultValue: false
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
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
    // Person Status
    for (presenceSensor in arrivalSensors) {
        subscribe(presenceSensor, "presence.present", arrivalHandler_PersonStatus)
    }
    for (presenceSensor in departureSensors) {
        subscribe(presenceSensor, "presence.not present", departureHandler_PersonStatus)
    }
    
    if (sleepSwitch) {
        // Person Status
        subscribe(sleepSwitch, "switch", switchHandler_PersonStatus)
    
        // Switch
        subscribe(location, "mode", modeHandler_Switch)
    
        // Away Alert
        subscribe(sleepSwitch, "switch.on", handler_AwayAlert)
    }
    
    // Command Alert
    subscribe(person, "command", personHandler_CommandAlert)
    
    if (notificationDevices) {
        // Notification
        subscribe(person, "message", handler_Notification)
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

def arrivalHandler_PersonStatus(evt) {
    logDebug("arrivalHandler_PersonStatus: ${evt.device} changed to ${evt.value}")

    person.arrived()
}

def departureHandler_PersonStatus(evt) {
    logDebug("departureHandler_PersonStatus: ${evt.device} changed to ${evt.value}")

    person.departed()
}

def switchHandler_PersonStatus(evt) {
    logDebug("switchHandler_PersonStatus: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        person.asleep()
    } else {
        person.awake()
    }
}

def modeHandler_Switch(evt) {
    logDebug("modeHandler_Switch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        sleepSwitch.off()
    }
}

def personHandler_CommandAlert(evt) {
    logDebug("personHandler_CommandAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "arrived") {
        if (alertArrived) {
            personToNotify.deviceNotification("$person is home!")
        }
    } else if (evt.value == "departed") {
        if (alertDeparted) {
            personToNotify.deviceNotification("$person has left!")
        }
    } else if (evt.value == "awake") {
        if (alertAwake) {
            personToNotify.deviceNotification("$person is awake!")
        }
    } else if (evt.value == "asleep") {
        if (alertAsleep) {
            personToNotify.deviceNotification("$person is asleep!")
        }
    }
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        personToNotify.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}
    
def handler_Notification(evt) {
    logDebug("handler_Notification: ${evt.device} changed to ${evt.value}")
    
    for (notifier in notificationDevices) {
        notifier.deviceNotification("${evt.value}")
    }
}

def urlHandler_arrived() {
    logDebug("urlHandler_arrived")
    
    person.arrived()
}

def urlHandler_departed() {
    logDebug("urlHandler_departed")
    
    person.departed()
}

def urlHandler_awake() {
    logDebug("urlHandler_awake")
    
    person.awake()
}

def urlHandler_asleep() {
    logDebug("urlHandler_asleep")
    
    person.asleep()
}