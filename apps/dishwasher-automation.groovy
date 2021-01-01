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
 
String getVersionNum() { return "3.0.0-beta.3" }
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
        section("Notifications") {
            input "alertStarted", "bool", title: "Alert when Started?", required: true, defaultValue: false
            
            input "alertFinished", "bool", title: "Alert when Finished?", required: true, defaultValue: false
            
            input "alertReset", "bool", title: "Alert when Reset?", required: true, defaultValue: false
            
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
        }
        section("Reminder") {
            input "reminderSwitch", "capability.switch", title: "Reminder Switch", multiple: false, required: true
            
            input "reminderRoutine", "capability.switch", title: "Turn On When", multiple: false, required: true
            
            input "person", "device.PersonStatus", title: "Person", multiple: false, required: true
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
    subscribe(contactSensor, "contact.open", openHandler)
    
    subscribe(appliance, "state", applianceHandler)
    
    def resetToday = timeToday(resetTime)
    def currentTime = new Date()
    schedule("$currentTime.seconds $resetToday.minutes $resetToday.hours * * ? *", dailyReset)
    
    subscribe(reminderRoutine, "switch.on", routineHandler)
    
    subscribe(reminderSwitch, "switch", reminderHandler)
    
    subscribe(person, "state", personHandler)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def openHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (timeOfDayIsBetween(timeToday(bedtimeStart), timeToday(bedtimeEnd), new Date(), location.timeZone) || reminderSwitch.currentValue("switch") == "on") {
        appliance.start()
    }
}

def applianceHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (evt.value == "running") {
        state.runningStartTime = now()
        runIn(60*runDuration, durationComplete)
    
        reminderSwitch.off()
        
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
    logDebug("Received duration complete time")
    
    if (appliance.currentValue("state") == "running") {
        appliance.finish()
    }
}

def dailyReset() {
    logDebug("Received daily reset time")
    
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

def routineHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (appliance.currentValue("state") == "unstarted") {
        reminderSwitch.on()
    }
}

def reminderHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
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

def personHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")

    if (evt.value != "home") {
        if (reminderSwitch.currentValue("switch") == "on") {
            reminderSwitch.off()
            notifier.deviceNotification("Dishwasher Reminder has been canceled!")
        }
    }
}