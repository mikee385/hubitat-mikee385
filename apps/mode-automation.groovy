/**
 *  Mode Automation
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
 
String getVersionNum() { return "1.2.1" }
String getVersionLabel() { return "Mode Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Mode Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Changes mode based on presence sensors and sleep sensors.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/mode-automation.groovy")

preferences {
    page(name: "settings", title: "Mode Automation", install: true, uninstall: true) {
        section("Sensors") {
            input "presenceSleepSensors", "capability.sleepSensor", title: "Presence+Sleep Sensors", multiple: true, required: false
            input "presenceSensors", "capability.presenceSensor", title: "Presence Sensors", multiple: true, required: false
        }
        section("Backup Times") {
            input "awakeTime", "time", title: "Awake Time", required: false
            input "asleepTime", "time", title: "Asleep Time", required: false
        }
        section("Alarm") {
            input "alarmAway", "capability.switch", title: "Arm Away", multiple: false, required: false
            input "alarmSleep", "capability.switch", title: "Arm Stay", multiple: false, required: false
        }
        section("Alerts") {
            input "alertArrived", "bool", title: "Alert when Arrived?", required: true, defaultValue: false
            input "alertDeparted", "bool", title: "Alert when Departed?", required: true, defaultValue: false
            input "alertAwake", "bool", title: "Alert when Awake?", required: true, defaultValue: false
            input "alertAsleep", "bool", title: "Alert when Asleep?", required: true, defaultValue: false
        }
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
    for (sensor in presenceSleepSensors) {
        subscribe(sensor, "presence.present", arrivedHandler)
        subscribe(sensor, "presence.not present", departedHandler)
        subscribe(sensor, "sleeping.sleeping", asleepHandler)
        subscribe(sensor, "sleeping.not sleeping", awakeHandler)
    }
    
    for (sensor in presenceSensors) {
        subscribe(sensor, "presence.present", arrivedHandler)
        subscribe(sensor, "presence.not present", departedHandler)
    }
    
    if (awakeTime) {
        schedule(awakeTime, awakeTimeHandler)
    }
    if (asleepTime) {
        schedule(asleepTime, asleepTimeHandler)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def arrivedHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        if (alertArrived) {
            person.deviceNotification("Welcome Back!")
        }
        location.setMode("Home")
    } else if (location.mode == "Sleep") {
        if (alertAwake) {
            person.deviceNotification("Good Morning!")
        }
        location.setMode("Home")
    }
}

def departedHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    def anyonePresent = false
    def anyoneAwake = false
    def anyoneAsleep = false
    for (sensor in presenceSleepSensors) {
        if (sensor.currentValue("presence") == "present") {
            anyonePresent = true
            if (sensor.currentValue("sleeping") == "not sleeping") {
                anyoneAwake = true
            } else {
                anyoneAsleep = true
            }
        }
    }
    for (sensor in presenceSensors) {
        if (sensor.currentValue("presence") == "present") {
            anyonePresent = true
        }
    }
    if (anyonePresent == false) {
        if (location.mode != "Away") {
            if (alertDeparted) {
                person.deviceNotification("Goodbye!")
            }
            location.setMode("Away")
            if (alarmAway) {
                alarmAway.on()
            }
        }
    } else if (anyoneAsleep == true && anyoneAwake == false) {
        if (location.mode != "Sleep") {
            if (alertAsleep) {
                person.deviceNotification("Good Night!")
            }
            location.setMode("Sleep")
            if (alarmSleep) {
                alarmSleep.on()
            }
        }
    }
}

def asleepHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (evt.device.currentValue("presence") == "present") {
        def anyoneAwake = false
        for (sensor in presenceSleepSensors) {
            if (sensor.currentValue("presence") == "present" && sensor.currentValue("sleeping") == "not sleeping") {
                anyoneAwake = true
            }
        }
        if (anyoneAwake == false) {
            if (location.mode != "Sleep") {
                if (alertAsleep) {
                    person.deviceNotification("Good Night!")
                }
                location.setMode("Sleep")
                if (alarmSleep) {
                    alarmSleep.on()
                }
            }
        }
    }
}

def awakeHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (evt.device.currentValue("presence") == "present") {
        if (location.mode == "Sleep") {
            if (alertAwake) {
                person.deviceNotification("Good Morning!")
            }
            location.setMode("Home")
        }
    }
}

def awakeTimeHandler(evt) {
    logDebug("Received awake time event")
    
    if (location.mode == "Sleep") {
        if (alertAwake) {
            person.deviceNotification("Good Morning!")
        }
        location.setMode("Home")
    }
}

def asleepTimeHandler(evt) {
    logDebug("Received asleep time event")
    
    if (location.mode == "Home") {
        if (alertAsleep) {
            person.deviceNotification("Good Night!")
        }
        location.setMode("Sleep")
        if (alarmSleep) {
            alarmSleep.on()
        }
    }
}