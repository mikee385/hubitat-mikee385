/**
 *  Camera Reminder
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
 
String getVersionNum() { return "1.0.0-beta2" }
String getVersionLabel() { return "Camera Reminder, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Camera Reminder",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Reminder to turn off the cameras.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/camera-reminder.groovy")

preferences {
    page(name: "settings", title: "Camera Reminder", install: true, uninstall: true) {
        section {
            input "reminderSwitch", "capability.switch", title: "Reminder Switch", multiple: false, required: true
        }
        section("Turn On") {
            input "person", "capability.sleepSensor", title: "Person", multiple: false, required: true
        }
        section("Turn Off") {
            input "contactSensor", "capability.contactSensor", title: "Contact Sensor", multiple: false, required: false
            
            input "button", "capability.pushableButton", title: "Button", multiple: false, required: false
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
    subscribe(reminderSwitch, "switch", switchHandler)

    subscribe(person, "sleeping.not sleeping", awakeHandler)
    subscribe(person, "state", stateHandler)
    
    subscribe(contactSensor, "contact", offHandler)
    subscribe(button, "pushed", offHandler)
    
    //if (logEnable) {
    //    log.warn "Debug logging enabled for 30 minutes"
    //    runIn(1800, logsOff)
    //}
}

def logsOff(){
    log.warn "Debug logging disabled"
    app.updateSetting("logEnable", [value: "false", type: "bool"])
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def switchHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        runIn(60*5, startReminder)
    } else {
        unschedule()
        notifier.deviceNotification("Camera Reminder is off.")
    }
}

def startReminder() {
    sendAlert()
    runEvery5Minutes(sendAlert)
}

def sendAlert() {
    notifier.deviceNotification("Turn off the cameras!")
}

def awakeHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (person.currentValue("state") == "home") {
        reminderSwitch.on()
    }
}

def stateHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (person.currentValue("state") != "home") {
        if (reminderSwitch.currentValue("switch") == "on") {
            notifier.deviceNotification("Camera Reminder canceled!")
            reminderSwitch.off()
        }
    }
}

def offHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    reminderSwitch.off()
}