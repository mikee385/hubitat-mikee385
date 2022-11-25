/**
 *  Device Monitor App
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
 
String getVersionNum() { return "3.1.0" }
String getVersionLabel() { return "Device Monitor, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library

definition(
    name: "Device Monitor",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Runs device checks and sends alerts for low battery and inactive devices.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/device-monitor.groovy"
)

preferences {
    page(name: "settings", title: "Device Monitor", install: true, uninstall: true) {
        section {
            input "runDaily", "bool", title: "Run every day at 8PM?", required: true, defaultValue: true
        }
        section {
            input "alertLowBattery", "bool", title: "Alert for Low Battery?", required: true, defaultValue: true
            input "alertInactive", "bool", title: "Alert for Inactive Device?", required: true, defaultValue: true
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

def uninstalled() {
    for (device in getChildDevices()) {
        deleteChildDevice(device.deviceNetworkId)
    }
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    def child = childDevice()
    
    subscribe(child, "deviceCheck.inactive", handler_DeviceCheck)
    
    if (runDaily) {
        def currentTime = new Date()
        def runTime = timeToday("20:00")
    
        schedule("$currentTime.seconds $runTime.minutes $runTime.hours * * ? *", runDeviceCheck)
    }
}

def childDevice() {
    def childID = "deviceMonitor:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("mikee385", "Device Monitor", childID, 1234, [label: "Device Monitor", isComponent: false])
    }
    return child
}

def runDeviceCheck() {
    logDebug("runDeviceCheck")

    if (location.mode == "Home") {
        childDevice().runDeviceCheck()
    } 
}

def handler_DeviceCheck(evt) {
    logDebug("handler_DeviceCheck: ${evt.device} changed to ${evt.value}")

    def batteryMessages = childDevice().getBatteryMessages() 
    if (batteryMessages) {
        def message = "Low Battery:\n${batteryMessages.sort().join('\n')}"
        log.warn(message)
        if (alertLowBattery) {
            personToNotify.deviceNotification(message)
        }
    }
    
    def inactiveMessages = childDevice().getInactiveMessages()
    if (inactiveMessages) {
        def message = "Inactive Devices:\n${inactiveMessages.sort().join('\n')}"
        log.warn(message)
        if (alertInactive) {
            personToNotify.deviceNotification(message)
        }
    }
}