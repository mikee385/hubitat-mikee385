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
 
String getVersionNum() { return "4.2.0" }
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
        }
        section {
            input "primarySensors", "capability.presenceSensor", title: "Primary Presence (Arrival & Departure)", multiple: true, required: false
            input "secondarySensors", "capability.presenceSensor", title: "Secondary Presence (Arrival Only)", multiple: true, required: false
            input "sleepSwitch", "capability.switch", title: "Sleep Switch", multiple: false, required: false
            input "locationDevice", "device.LocativeDevice", title: "Location Device", multiple: false, required: false
        }
        section("Alerts") {
            input "notificationDevices", "capability.notification", title: "Notification Devices", multiple: true, required: false
            input "alertInconsistent", "bool", title: "Alert when Presence is Inconsistent?", required: true, defaultValue: false
            input "alertAsleepWhenAway", "bool", title: "Alert when Asleep while Away?", required: true, defaultValue: false
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "logPresence", type: "bool", title: "Enable presence logging?", defaultValue: false
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
    for (primarySensor in primarySensors) {
        subscribe(primarySensor, "presence.present", arrivalHandler_PersonStatus)
        subscribe(primarySensor, "presence.not present", departureHandler_PersonStatus)
    }
    for (secondarySensor in secondarySensors) {
        subscribe(secondarySensor, "presence.present", arrivalHandler_PersonStatus)
    }
    
    // Checks
    if (alertInconsistent) {
        subscribe(person, "presence", personHandler_InconsistencyCheck)
    }
    if (alertAsleepWhenAway) {
        subscribe(person, "presence", personHandler_AsleepWhenAwayCheck)
        subscribe(person, "sleeping", personHandler_AsleepWhenAwayCheck)
    }
    
    if (sleepSwitch) {
        // Person Status
        subscribe(sleepSwitch, "switch", switchHandler_PersonStatus)
    
        // Switch
        subscribe(location, "mode", modeHandler_Switch)
    
        // Away Alert
        subscribe(sleepSwitch, "switch.on", handler_AwayAlert)
    }
    
    if (locationDevice) {
        // Location
        subscribe(locationDevice, "trigger", triggerHandler_Location)
    }
    
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
    
    if (logPresence) {
        log.info "${evt.device} is ${evt.value}!"
    }
}

def departureHandler_PersonStatus(evt) {
    logDebug("departureHandler_PersonStatus: ${evt.device} changed to ${evt.value}")

    person.departed()
    
    if (logPresence) {
        log.info "${evt.device} is ${evt.value}!"
    }
}

def personHandler_InconsistencyCheck(evt) {
    logDebug("personHandler_InconsistencyCheck: ${evt.device} changed to ${evt.value}")
    
    runIn(60, inconsistencyCheck)
}

def inconsistencyCheck() {
    def presenceValue = person.currentValue("presence")
    for (primarySensor in primarySensors) {
        if (primarySensor.currentValue("presence") != presenceValue) {
            personToNotify.deviceNotification("WARNING: $primarySensor failed to change to $presenceValue!")
        }
    }
}

def personHandler_AsleepWhenAwayCheck(evt) {
    logDebug("personHandler_AsleepWhenAwayCheck: ${evt.device} changed to ${evt.value}")
    
    if (person.currentValue("presence") == "not present" && person.currentValue("sleeping") == "sleeping") {
        personToNotify.deviceNotification("WARNING: $person is Asleep while Away!")
    }
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

def triggerHandler_Location(evt) {
    logDebug("triggerHandler_Location: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "enter") {
        person.setLocation(locationDevice.currentValue("id"))
    } else {
        person.setLocation("")
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