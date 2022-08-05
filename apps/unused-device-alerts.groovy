/**
 *  Unused Device Alerts
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
 
String getVersionNum() { return "2.0.0" }
String getVersionLabel() { return "Unused Device Alerts, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-health-library

definition(
    name: "Unused Device Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Alerts for low battery and inactivity for devices not used in other automations.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/unused-device-alerts.groovy"
)

preferences {
    page(name: "settings", title: "Unused Device Alerts", install: true, uninstall: true) {
        section {
            input "devices_2hr", "capability.sensor", title: "Inactivity threshold: 2 hours", multiple: true, required: false
            input "devices_1day", "capability.sensor", title: "Inactivity threshold: 1 day", multiple: true, required: false
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input "deviceHealthChecker", "device.DeviceHealthChecker", title: "Device Health Checker", multiple: false, required: true
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
    // Device Health Checker
    initializeDeviceHealthCheck()
}

def getBatteryThresholds() {
    def thresholds = []
    
    for (item in devices_2hr) {
        if (item.hasCapability("Battery")) {
            thresholds.add([device: item, lowBattery: 10])
        }
    }
    for (item in devices_1day) {
        if (item.hasCapability("Battery")) {
            thresholds.add([device: item, lowBattery: 10])
        }
    }
    
    return thresholds
}

def getInactiveThresholds() {
    def thresholds = []
    
    for (item in devices_2hr) {
        thresholds.add([device: item, inactiveHours: 2])
    }
    for (item in devices_1day) {
        thresholds.add([device: item, inactiveHours: 24])
    }
    
    return thresholds
}