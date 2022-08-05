/**
 *  Media Room Automation
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
 
String getVersionNum() { return "3.0.0" }
String getVersionLabel() { return "Media Room Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.away-alert-library
#include mikee385.device-check-library

definition(
    name: "Media Room Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Automation and alerts for the devices associated with the media room.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/media-room-automation.groovy"
)

preferences {
    page(name: "settings", title: "Media Room Automation", install: true, uninstall: true) {
        section {
            input "door", "capability.contactSensor", title: "Door", multiple: false, required: false
            input "tv", "device.RokuTV", title: "TV", multiple: false, required: false
        }
        section("Comfort Profile") {
            input "thermostat", "device.EcobeeThermostat", title: "Thermostat", multiple: false, required: false
            input "comfortProfileButton", "capability.pushableButton", title: "Comfort Profile", multiple: false, required: false
        }
        section("Alerts") {
            input "alertOccupied", "bool", title: "Alert when Occupied?", required: true, defaultValue: false
            input "alertVacant", "bool", title: "Alert when Vacant?", required: true, defaultValue: false
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input "deviceChecker", "device.DeviceChecker", title: "Device Checker", multiple: false, required: true
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
    // Comfort Profile
    if (tv) {
        subscribe(tv, "switch", tvHandler_ComfortProfile)
    }
    
    // Away Alert
    if (door) {
        subscribe(door, "contact", handler_AwayAlert)
    }
    if (tv) {
        subscribe(tv, "switch.on", handler_AwayAlert)
    }
    if (comfortProfileButton) {
        subscribe(comfortProfileButton, "pushed", handler_AwayAlert)
    }
    
    // Device Checks
    initializeDeviceChecks()
}

def getBatteryThresholds() {
    def thresholds = []
    
    if (door) {
        thresholds.add([device: door, lowBattery: 10])
    }
    
    return thresholds
}

def getInactiveThresholds() {
    def thresholds = []
    
    if (door) {
        thresholds.add([device: door, inactiveHours: 24])
    }
    
    return thresholds
}

def tvHandler_ComfortProfile(evt) {
    logDebug("tvHandler_ComfortProfile: ${evt.device} changed to ${evt.value}")
    
    if (location.mode != "Away") {
        if (evt.value == "on") {
            if (comfortProfileButton) {
                comfortProfileButton.push()
            }
            if (alertOccupied) {
                personToNotify.deviceNotification("Media Room is occupied!")
            }
        } else {
            if (thermostat) {
                thermostat.resumeProgram()
            }
            if (alertVacant) {
                personToNotify.deviceNotification("Media Room is vacant!")
            }
        }
    }
}