/**
 *  Backup Battery Automation
 *
 *  Copyright 2023 Michael Pierce
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
String getVersionLabel() { return "Backup Battery Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library

definition(
    name: "Backup Battery Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Shuts down hub when backup battery is low on power and sends alerts based on status.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/backup-battery-automation.groovy"
)

preferences {
    page(name: "settings", title: "Backup Battery Automation", install: true, uninstall: true) {
        section {
            input "backupBattery", "capability.powerSource", title: "Backup Battery", multiple: false, required: true
        }
        section("Shutdown hub when:") {
            input "minBatteryLevel", "number", title: "Charge is below:", required: true, defaultValue: 10
            input "minRuntimeLevel", "number", title: "Runtime is below:", required: true, defaultValue: 300
        }
        section("Alerts") {
            input "alertBattery", "bool", title: "Alert when on battery?", required: true, defaultValue: false
            input "alertMains", "bool", title: "Alert when power is restored?", required: true, defaultValue: false
            input "alertOffline", "bool", title: "Alert when offline?", required: true, defaultValue: false
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
    state.shuttingDown = false

    // Shutdown Hub
    subscribe(backupBattery, "powerSource", backupBatteryHandler_ShutdownHub)
    subscribe(backupBattery, "battery", backupBatteryHandler_ShutdownHub)
    subscribe(backupBattery, "batteryRuntimeSecs", backupBatteryHandler_ShutdownHub)
    
    // Power Source Alert
    subscribe(backupBattery, "powerSource", backupBatteryHandler_PowerSourceAlert)     
    
    // Device Checks
    initializeDeviceChecks()
}

def backupBatteryHandler_ShutdownHub(evt) {
    logDebug("backupBatteryHandler_ShutdownHub: ${evt.device} changed to ${evt.value}")

    if (backupBattery.currentValue("powerSource") == "battery") {
        if (!state.shuttingDown) {
            if (backupBattery.currentValue("battery") <= minBatteryLevel) {
                state.shuttingDown = true
                log.warn "Backup battery charge is low! Shutting down..."
                personToNotify.deviceNotification("Backup battery charge is low! Shutting down...")
                runIn(15, shutdownHub)
            } else if (backupBattery.currentValue("batteryRuntimeSecs") <= minRuntimeLevel) {
                state.shuttingDown = true
                log.warn "Backup battery runtime is low! Shutting down..."
                personToNotify.deviceNotification("Backup battery runtime is low! Shutting down...")
                runIn(15, shutdownHub)
            }
        }
    } else if (backupBattery.currentValue("powerSource") == "mains") {
        unschedule("shutdownHub")
        state.shuttingDown = false
    }
}

def shutdownHub() {
    httpPost("http://127.0.0.1:8080/hub/shutdown", "") { resp -> }
}

def backupBatteryHandler_PowerSourceAlert(evt) {
    logDebug("backupBatteryHandler_PowerSourceAlert: ${evt.device} changed to ${evt.value}")

    if (evt.value == "battery") {
        log.info "Power is on battery!"
        if (alertBattery) {
            personToNotify.deviceNotification("Power is on battery!")
        }
    } else if (evt.value == "mains") {
        log.info "Power has been restored!"
        if (alertMains) {
            personToNotify.deviceNotification("Power has been restored!")
        }
    } else if (evt.value == "unknown") {
        log.warn "Backup battery is offline!"
        if (alertOffline) {
            personToNotify.deviceNotification("Backup battery is offline!")
        }
    } else {
        log.warn "Unknown powerSource value: ${evt.value}"
    }
}