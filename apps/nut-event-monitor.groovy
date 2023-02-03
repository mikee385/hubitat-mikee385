/**
 *  NUT Event Monitor
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
 
String getVersionNum() { return "2.0.1" }
String getVersionLabel() { return "NUT Event Monitor, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library

definition(
    name: "NUT Event Monitor",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Listens for events from a UPS monitor and shutdown controller (UPSMON)",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/nut-event-monitor.groovy"
)

preferences {
    page(name: "settings", title: "NUT Event Monitor", install: true, uninstall: true) {
        section {
            input name: "upsName", type: "string", title: "UPS Name", required: true
        }
        section("Shutdown Hub") {
            input "shutdownOnFsd", "bool", title: "Shutdown when FSD event recieved?", required: true, defaultValue: false
            input "shutdownOnShutdown", "bool", title: "Shutdown when shutdown event recieved?", required: true, defaultValue: false
            input "shutdownOnLowbatt", "bool", title: "Shutdown when low battery event recieved?", required: true, defaultValue: false
        }
        section("Power Alerts") {
            input "alertPowerBattery", "bool", title: "Alert when power is on battery?", required: true, defaultValue: false
            input "alertPowerMains", "bool", title: "Alert when power is restored?", required: true, defaultValue: false
            input "alertPowerUnknown", "bool", title: "Alert when power is unknown?", required: true, defaultValue: false
        }
        section("Network Alerts") {
            input "alertNetworkOnline", "bool", title: "Alert when network is online?", required: true, defaultValue: false
            input "alertNetworkOffline", "bool", title: "Alert when network is offline?", required: true, defaultValue: false
        }
        section("Event Alerts") {
            input "alertEventOnline", "bool", title: "Alert when online event received?", required: true, defaultValue: false
            input "alertEventOnbatt", "bool", title: "Alert when onbatt event recieved?", required: true, defaultValue: false
            input "alertEventLowbatt", "bool", title: "Alert when lowbatt event received?", required: true, defaultValue: false
            input "alertEventFsd", "bool", title: "Alert when fsd event received?", required: true, defaultValue: false
            input "alertEventCommok", "bool", title: "Alert when commok event received?", required: true, defaultValue: false
            input "alertEventCommbad", "bool", title: "Alert when commbad event received?", required: true, defaultValue: false
            input "alertEventShutdown", "bool", title: "Alert when shutdown event received?", required: true, defaultValue: false
            input "alertEventReplbatt", "bool", title: "Alert when replbatt event received?", required: true, defaultValue: false
            input "alertEventNocomm", "bool", title: "Alert when nocomm event received?", required: true, defaultValue: false
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/notify/:upsName/:event") {
        action: [
            GET: "urlHandler_notifyEvent"
        ]
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
    state.shuttingDown = false
    
    // Child Device
    def child = childDevice()
    
    // Shutdown Hub
    if (shutdownOnFsd) {
        subscribe(child, "lastEvent.fsd", eventHandler_ShutdownHub)
    }
    if (shutdownOnShutdown) {
        subscribe(child, "lastEvent.shutdown", eventHandler_ShutdownHub)
    }
    if (shutdownOnLowbatt) {
        subscribe(child, "lastEvent.lowbatt", eventHandler_ShutdownHub)
    }
    
    // Power Alerts
    if (alertPowerBattery) {
        subscribe(child, "powerSource.battery", powerHandler_BatteryAlert)
    }
    if (alertPowerMains) {
        subscribe(child, "powerSource.mains", powerHandler_MainsAlert)
    }
    if (alertPowerUnknown) {
        subscribe(child, "powerSource.unknown", powerHandler_UnknownAlert)
    }
    
    // Network Alerts
    if (alertNetworkOnline) {
        subscribe(child, "networkStatus.online", networkHandler_OnlineAlert)
    }
    if (alertNetworkOffline) {
        subscribe(child, "networkStatus.offline", networkHandler_OfflineAlert)
    }
    
    // Event Alerts
    if (alertEventOnline) {
        subscribe(child, "lastEvent.online", eventHandler_OnlineAlert)
    }
    if (alertEventOnbatt) {
        subscribe(child, "lastEvent.onbatt", eventHandler_OnbattAlert)
    }
    if (alertEventLowbatt) {
        subscribe(child, "lastEvent.lowbatt", eventHandler_LowbattAlert)
    }
    if (alertEventFsd) {
        subscribe(child, "lastEvent.fsd", eventHandler_FsdAlert)
    }
    if (alertEventCommok) {
        subscribe(child, "lastEvent.commok", eventHandler_CommokAlert)
    }
    if (alertEventCommbad) {
        subscribe(child, "lastEvent.commbad", eventHandler_CommbadAlert)
    }
    if (alertEventShutdown) {
        subscribe(child, "lastEvent.shutdown", eventHandler_ShutdownAlert)
    }
    if (alertEventReplbatt) {
        subscribe(child, "lastEvent.replbatt", eventHandler_ReplbattAlert)
    }
    if (alertEventNocomm) {
        subscribe(child, "lastEvent.nocomm", eventHandler_NocommAlert)
    }

    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.notifyEventUrl = "${getFullLocalApiServerUrl()}/notify/$UPSNAME/$NOTIFYTYPE?access_token=$state.accessToken"
}

def childDevice() {
    def childID = "nutEventMonitor:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("mikee385", "NUT Event Monitor", childID, 1234, [label: upsName, isComponent: true])
        child.updateSetting("upsName", [value: upsName, type: "text"])
    }
    return child
}

def eventHandler_ShutdownHub(evt) {
    logDebug("eventHandler_ShutdownHub: ${evt.device} changed to ${evt.value}")

    if (!state.shuttingDown) {
        state.shuttingDown = true
        log.warn "${upsName} is shutting down..."
        personToNotify.deviceNotification("${upsName} is shutting down...")
        runIn(15, shutdownHub)
    }
}

def shutdownHub() {
    httpPost("http://127.0.0.1:8080/hub/shutdown", "") { resp -> }
}

def powerHandler_BatteryAlert(evt) {
    logDebug("powerHandler_BatteryAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} is on battery!")
}

def powerHandler_MainsAlert(evt) {
    logDebug("powerHandler_MainsAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} power has been restored!")
}

def powerHandler_UnknownAlert(evt) {
    logDebug("powerHandler_UnknownAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} power is unknown!")
}

def networkHandler_OnlineAlert(evt) {
    logDebug("networkHandler_OnlineAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} is online!")
}

def networkHandler_OfflineAlert(evt) {
    logDebug("networkHandler_OfflineAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} is offline!")
}

def eventHandler_OnlineAlert(evt) {
    logDebug("eventHandler_OnlineAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} received online event!")
}

def eventHandler_OnbattAlert(evt) {
    logDebug("eventHandler_OnbattAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} received onbatt event!")
}

def eventHandler_LowbattAlert(evt) {
    logDebug("eventHandler_LowbattAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} received lowbatt event!")
}

def eventHandler_FsdAlert(evt) {
    logDebug("eventHandler_FsdAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} received fsd event!")
}

def eventHandler_CommokAlert(evt) {
    logDebug("eventHandler_CommokAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} received commok event!")
}

def eventHandler_CommbadAlert(evt) {
    logDebug("eventHandler_CommbadAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} received commbad event!")
}

def eventHandler_ShutdownAlert(evt) {
    logDebug("eventHandler_ShutdownAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} received shutdown event!")
}

def eventHandler_ReplbattAlert(evt) {
    logDebug("eventHandler_ReplbattAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} received replbatt event!")
}

def eventHandler_NocommAlert(evt) {
    logDebug("eventHandler_NocommAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("${upsName} received nocomm event!")
}

def urlHandler_notifyEvent() {
    logDebug("urlHandler_notifyEvent: ${params.upsName} changed to ${params.event}")
    
    childDevice().parse(params.event)
}