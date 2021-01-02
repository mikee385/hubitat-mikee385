/**
 *  Dishwasher Automation
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
 
String getVersionNum() { return "3.0.0-beta.4" }
String getVersionLabel() { return "Dishwasher Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Dishwasher Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Updates the state of an Appliance Status device representing a dishwasher.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/dishwasher-automation.groovy")

preferences {
    page(name: "settings", title: "Dishwasher Automation", install: true, uninstall: true) {
        section {
            input "appliance", "device.ApplianceStatus", title: "Dishwasher Status", multiple: false, required: true
        }
        section("Start") {
            input "contactSensor", "capability.contactSensor", title: "Contact Sensor", multiple: false, required: true
            input "bedtimeStart", "time", title: "Bedtime Start", required: true
            input "bedtimeEnd", "time", title: "Bedtime End", required: true
        }
        section("Finish") {
            input "runDuration", "number", title: "Duration (in minutes)", required: true
        }
        section("Reset") {
            input "resetTime", "time", title: "Reset Time", required: true
        }
        section("Alerts") {
            input "alertStarted", "bool", title: "Alert when Started?", required: true, defaultValue: false
            input "alertFinished", "bool", title: "Alert when Finished?", required: true, defaultValue: false
            input "alertReset", "bool", title: "Alert when Reset?", required: true, defaultValue: false
            input "reminderSwitch", "capability.switch", title: "Reminder Switch", multiple: false, required: true
            input "reminderRoutine", "capability.switch", title: "Turn On When", multiple: false, required: true
            input "person", "device.PersonStatus", title: "Person", multiple: false, required: true
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
    // Appliance Status
    subscribe(contactSensor, "contact.open", contactSensorHandler_ApplianceStatus)
    subscribe(appliance, "state", applianceHandler_ApplianceStatus)
    
    def resetToday = timeToday(resetTime)
    def currentTime = new Date()
    schedule("$currentTime.seconds $resetToday.minutes $resetToday.hours * * ? *", resetTimeHandler_ApplianceStatus)
    
    // Reminder Switch
    subscribe(reminderRoutine, "switch.on", routineHandler_ReminderSwitch)
    subscribe(appliance, "state", applianceHandler_ReminderSwitch)
    subscribe(person, "state", personHandler_ReminderSwitch)
    
    // Reminder Alert
    subscribe(reminderSwitch, "switch", reminderHandler_ReminderAlert)
    
    // Away Alert
    subscribe(contactSensor, "contact", handler_AwayAlert)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def contactSensorHandler_ApplianceStatus(evt) {
    logDebug("contactSensorHandler_ApplianceStatus: ${evt.device} changed to ${evt.value}")
    
    if (timeOfDayIsBetween(timeToday(bedtimeStart), timeToday(bedtimeEnd), new Date(), location.timeZone) || reminderSwitch.currentValue("switch") == "on") {
        appliance.start()
    }
}

def applianceHandler_ApplianceStatus(evt) {
    logDebug("applianceHandler_ApplianceStatus: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "running") {
        state.runningStartTime = now()
        runIn(60*runDuration, durationComplete)
        
        if (alertStarted) {
            notifier.deviceNotification("Dishwasher has started.")
        }
    } else if (evt.value == "finished") {
        if (alertFinished) {
            notifier.deviceNotification("Dishwasher has finished.")
        }
    } else if (evt.value == "unstarted") {
        if (alertReset) {
            notifier.deviceNotification("Dishwasher has reset.")
        }
    }
}

def durationComplete() {
    logDebug("durationComplete")
    
    if (appliance.currentValue("state") == "running") {
        appliance.finish()
    }
}

def resetTimeHandler_ApplianceStatus() {
    logDebug("resetTimeHandler_ApplianceStatus")
    
    if (appliance.currentValue("state") == "finished") {
        appliance.reset()
    } else if (appliance.currentValue("state") == "running") {
        def currentDuration = now() - state.runningStartTime
        if (currentDuration > 2*runDuration*60*1000) {
            logDebug("Ran too long. Resetting...")
            appliance.reset()
        }
    }
}

def routineHandler_ReminderSwitch(evt) {
    logDebug("routineHandler_ReminderSwitch: ${evt.device} changed to ${evt.value}")
    
    if (appliance.currentValue("state") == "unstarted") {
        reminderSwitch.on()
    }
}

def applianceHandler_ReminderSwitch(evt) {
    logDebug("applianceHandler_ReminderSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "running") {
        reminderSwitch.off()
    }
}

def personHandler_ReminderSwitch(evt) {
    logDebug("personHandler_ReminderSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value != "home") {
        if (reminderSwitch.currentValue("switch") == "on") {
            reminderSwitch.off()
            notifier.deviceNotification("Dishwasher Reminder has been canceled!")
        }
    }
}

def reminderHandler_ReminderAlert(evt) {
    logDebug("reminderHandler_ReminderAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        reminderAlert()
    } else {
        unschedule("reminderAlert")
    }
}

def reminderAlert() {
    notifier.deviceNotification("Start the dishwasher!")
    runIn(60*5, reminderAlert)
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        notifier.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}