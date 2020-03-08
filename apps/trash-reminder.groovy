/**
 *  Trash Reminder Triggers
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
String getVersionLabel() { return "Trash Reminder, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Trash Reminder",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Reminder to take out the trash.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/trash-reminder.groovy")

preferences {
    page(name: "settings", title: "Trash Reminder", install: true, uninstall: true) {
        section {
            input "reminderSwitch", "capability.switch", title: "Reminder Switch", multiple: false, required: true
        }
        section("Turn On") {
            input "person", "capability.sleepSensor", title: "Person", multiple: false, required: true
        }
        section("Turn Off") {
            input "overheadDoor", "capability.contactSensor", title: "Garage Door", multiple: false, required: true
            
            input "trashDays", "enum", title: "Trash Days", multiple: true, required: true, options: ["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"]
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
    
    subscribe(overheadDoor, "contact", overheadDoorHandler)

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
        def d = new Date()
        schedule("$d.seconds $d.minutes/5 * * * ? *", sendAlert)
    } else {
        unschedule()
        notifier.deviceNotification("$reminderSwitch is off.")
    }
}

def sendAlert() {
    notifier.deviceNotification("Take out the trash!")
}

def awakeHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (person.currentValue("state") == "home") {
        def df = new java.text.SimpleDateFormat("EEEE")
        df.setTimeZone(location.timeZone)

        def day = df.format(new Date())
        if (trashDays.contains(day)) {
            reminderSwitch.on()
        }
    }
}

def stateHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (person.currentValue("state") != "home") {
        if (reminderSwitch.currentValue("switch") == "on") {
            notifier.deviceNotification("Trash Reminder canceled!")
            reminderSwitch.off()
        }
    }
}

def overheadDoorHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    reminderSwitch.off()
}