/**
 *  Mode Automation
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
 
String getVersionNum() { return "1.0.0-beta.1" }
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
        section("Notifications") {
        
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
            notifier.deviceNotification("Welcome Back!")
        }
        location.setMode("Home")
    } else if (location.mode == "Sleep") {
        if (alertAwake) {
            notifier.deviceNotification("Good Morning!")
        }
        location.setMode("Home")
    }
}

def departedHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    def anyonePresent = false
    def anyoneAwake = false
    for (sensor in presenceSleepSensors) {
        if (sensor.currentValue("presence") == "present") {
            anyonePresent = true
            if (sensor.currentValue("sleeping") == "not sleeping") {
                anyoneAwake = true
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
                notifier.deviceNotification("Goodbye!")
            }
            location.setMode("Away")
        }
    } else if (anyoneAwake == false) {
        if (location.mode != "Sleep") {
            if (alertAsleep) {
                notifier.deviceNotification("Good Night!")
            }
            location.setMode("Sleep")
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
                    notifier.deviceNotification("Good Night!")
                }
                location.setMode("Sleep")
            }
        }
    }
}

def awakeHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (evt.device.currentValue("presence") == "present") {
        if (location.mode == "Sleep") {
            if (alertAwake) {
                notifier.deviceNotification("Good Morning!")
            }
            location.setMode("Home")
        }
    }
}

def awakeTimeHandler(evt) {
    logDebug("Received awake time event")
    
    if (location.mode == "Sleep") {
        if (alertAwake) {
            notifier.deviceNotification("Good Morning!")
        }
        location.setMode("Home")
    }
}

def asleepTimeHandler(evt) {
    logDebug("Received asleep time event")
    
    if (location.mode == "Home") {
        if (alertAsleep) {
            notifier.deviceNotification("Good Night!")
        }
        location.setMode("Sleep")
    }
}