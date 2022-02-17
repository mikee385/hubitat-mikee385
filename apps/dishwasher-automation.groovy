/**
 *  Dishwasher Automation
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
 
String getVersionNum() { return "5.4.0" }
String getVersionLabel() { return "Dishwasher Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.away-alert-library
#include mikee385.battery-alert-library
#include mikee385.inactive-alert-library

definition(
    name: "Dishwasher Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Updates the status of an Appliance Status device representing a dishwasher.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/dishwasher-automation.groovy"
)

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
        }
        section("Reminder") {
            input "reminderSwitch", "capability.switch", title: "Reminder Switch", multiple: false, required: false
            input "reminderRoutine", "capability.switch", title: "Turn On When", multiple: false, required: false
            input "reminderTime", "time", title: "Turn On At        ", required: false
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
    // Create state
    if (state.startTime == null) {
        state.startTime = now()
    }
    if (state.endTime == null) {
        state.endTime = now()
    }
    if (state.durationMinutes == null) {
        state.durationMinutes = 0
    }

    // Appliance Status
    subscribe(appliance, "status", applianceHandler_ApplianceStatus)
    subscribe(contactSensor, "contact", contactSensorHandler_ApplianceStatus)
    
    def resetToday = timeToday(resetTime)
    def currentTime = new Date()
    schedule("$currentTime.seconds $resetToday.minutes $resetToday.hours * * ? *", resetTimeHandler_ApplianceStatus)
    
    if (reminderSwitch) {
        // Reminder Switch
        if (reminderRoutine) {
            subscribe(reminderRoutine, "switch.on", routineHandler_ReminderSwitch)
        }
        if (reminderTime) {
            def reminderToday = timeToday(reminderTime)
            schedule("$currentTime.seconds $reminderToday.minutes $reminderToday.hours * * ? *", timeHandler_ReminderSwitch)
        }
        subscribe(appliance, "status", applianceHandler_ReminderSwitch)
        subscribe(personToNotify, "presence", personHandler_ReminderSwitch)
        subscribe(personToNotify, "sleeping", personHandler_ReminderSwitch)
    
        // Reminder Alert
        subscribe(reminderSwitch, "switch", reminderHandler_ReminderAlert)
    }
    
    // Away Alert
    subscribe(contactSensor, "contact", handler_AwayAlert)
    
    // Battery Alert
    scheduleBatteryCheck()
    
    // Inactive Alert
    scheduleInactiveCheck()
    
    // Set initial state
    def deviceRunning = appliance.currentValue("status") == "running"
    def stateRunning = state.endTime < state.startTime
    
    if (deviceRunning && !stateRunning) {
        started()
    } else if (!deviceRunning && stateRunning) {
        finished()
    }
}

def getBatteryThresholds() {
    return [
        [device: contactSensor, lowBattery: 10]
    ]
}

def getInactiveThresholds() {
    return [
        [device: contactSensor, inactiveHours: 1]
    ]
}

def started() {
    state.startTime = now()
    runIn(60*runDuration, durationComplete)
        
    if (alertStarted) {
        personToNotify.deviceNotification("Dishwasher has started.")
    }
}

def finished() {
    state.endTime = now()
    state.durationMinutes += (state.endTime - state.startTime)/1000.0/60.0
    
    if (alertFinished) {
        personToNotify.deviceNotification("Dishwasher has finished.")
    }
}

def reset() {
    if (alertReset) {
        personToNotify.deviceNotification("Dishwasher has reset.")
    }
}

def applianceHandler_ApplianceStatus(evt) {
    logDebug("applianceHandler_ApplianceStatus: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "running") {
        started()
    } else if (evt.value == "finished") {
        finished()
    } else if (evt.value == "idle") {
        reset()
    }
}

def contactSensorHandler_ApplianceStatus(evt) {
    logDebug("contactSensorHandler_ApplianceStatus: ${evt.device} changed to ${evt.value}")
    
    if (timeOfDayIsBetween(timeToday(bedtimeStart), timeToday(bedtimeEnd), new Date(), location.timeZone) || (reminderSwitch && reminderSwitch.currentValue("switch") == "on")) {
        appliance.start()
    }
}

def durationComplete() {
    logDebug("durationComplete")
    
    if (appliance.currentValue("status") == "running") {
        appliance.finish()
    }
}

def resetTimeHandler_ApplianceStatus() {
    logDebug("resetTimeHandler_ApplianceStatus")
    
    if (appliance.currentValue("status") == "finished") {
        appliance.reset()
    } else if (appliance.currentValue("status") == "running") {
        def currentDuration = now() - state.startTime
        if (currentDuration > 2*runDuration*60*1000) {
            logDebug("Ran too long. Resetting...")
            appliance.reset()
        }
    }
}

def routineHandler_ReminderSwitch(evt) {
    logDebug("routineHandler_ReminderSwitch: ${evt.device} changed to ${evt.value}")
    
    turnOnReminderSwitch()
}

def timeHandler_ReminderSwitch() {
    logDebug("timeHandler_ReminderSwitch")
    
    turnOnReminderSwitch()
}

def turnOnReminderSwitch() {
    if (appliance.currentValue("status") == "idle") {
        if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
            reminderSwitch.on()
        }
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

    if (personToNotify.currentValue("presence") == "not present" || personToNotify.currentValue("sleeping") == "sleeping") {
        if (reminderSwitch.currentValue("switch") == "on") {
            reminderSwitch.off()
            personToNotify.deviceNotification("Dishwasher Reminder has been canceled!")
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
    personToNotify.deviceNotification("Start the dishwasher!")
    runIn(60*5, reminderAlert)
}