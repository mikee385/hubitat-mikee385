/**
 *  Bedtime Automation
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

String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "Bedtime Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library

definition(
    name: "Bedtime Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Automation and alerts for the bedtime routine.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/bedtime-automation.groovy"
)

preferences {
    page(name: "settings", title: "Bedtime Automation", install: true, uninstall: true) {
        section {
            input "routine", "capability.switch", title: "Routine", multiple: false, required: true  
        }
        section {
            input "door", "capability.contectSensor", title: "Door", multiple: false, required: true  
            input "startTime", "time", title: "Start Time", required: true
            input "endTime", "time", title: "End Time", required: true
        }
        section {
            input "deviceMonitor", "device.DeviceMonitor", title: "Device Monitor", multiple: false, required: true
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
    // Bedtime Routine
    subscribe(door, "contact.closed", doorHandler_BedtimeRoutine)
    
    // Device Checks
    initializeDeviceChecks()
}

def doorHandler_BedtimeRoutine(evt) {
    logDebug("doorHandler_BedtimeRoutine: ${evt.device} changed to ${evt.value}")
    
    if (location.mode != "Away" && timeOfDayIsBetween(timeToday(startTime), timeToday(endTime), new Date(), location.timeZone)) {
        routine.on()
    }
}