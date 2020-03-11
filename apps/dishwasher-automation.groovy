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
 
String getVersionNum() { return "1.0.0-beta3" }
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
            input "appliance", "capability.actuator", title: "Dishwasher Status", multiple: false, required: true
        }
        section("Start") {
            input "contactSensor", "capability.contactSensor", title: "Contact Sensor", multiple: false, required: true
            
            input "bedtimeStart", "time", title: "Bedtime Start", required: true
            
            input "bedtimeEnd", "time", title: "Bedtime End", required: true
            
             input "reminderSwitch", "capability.switch", title: "Reminder Switch", multiple: false, required: true
        }
        section("Finish") {
            input "runDuration", "number", title: "Duration (in minutes)", required: true
        }
        section("Reset") {
            input "resetTime", "time", title: "Reset Time", required: true
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
    
    subscribe(appliance, "state.running", runningHandler)
    
    def resetDateTime = timeToday(resetTime)
    def currentTime = new Date()
    schedule("$currentTime.seconds $resetDateTime.minutes $resetDateTime.hours * * ? *", dailyReset)

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

def openHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (timeOfDayIsBetween(timeToday(bedtimeStart), timeToday(bedtimeEnd), new Date(), location.timeZone) || reminderSwitch.currentValue("switch") == "on") {
        appliance.start()
    }
}

def runningHandler(evt) {
    logDebug("Received running event")
    
    runIn(60*runDuration, durationComplete)
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
    }
}