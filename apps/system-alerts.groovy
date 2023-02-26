/**
 *  System Alerts
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
 
String getVersionNum() { return "3.3.0" }
String getVersionLabel() { return "System Alerts, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library

definition(
    name: "System Alerts",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Sends alerts when the system events occur.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/system-alerts.groovy"
)

preferences {
    page(name: "settings", title: "System Alerts", install: true, uninstall: true) {
        section("Alerts") {
            input "alertReboot", "bool", title: "Alert when Rebooted?", required: true, defaultValue: true
            input "alertSevereLoad", "bool", title: "Alert when Severe Load?", required: true, defaultValue: true
            input "alertLowMemory", "bool", title: "Alert when Low Memory?", required: true, defaultValue: true
            input "alertZigbeeOff", "bool", title: "Alert when Zigbee Off?", required: true, defaultValue: true
            input "alertZigbeeOn", "bool", title: "Alert when Zigbee On?", required: true, defaultValue: true
            input "alertZwaveCrashed", "bool", title: "Alert when Z-Wave Crashed?", required: true, defaultValue: true
            input "alertFirmwareUpdate", "bool", title: "Alert when Firmware Update Available?", required: true, defaultValue: true
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
    if (alertReboot) {
        subscribe(location, "systemStart", rebootHandler)
    }
    
    if (alertSevereLoad) {
        subscribe(location, "severeLoad", severeLoadHandler)
    }
    
    if (alertLowMemory) {
        subscribe(location, "lowMemory", lowMemoryHandler)
    }
    
    if (alertZigbeeOff) {
        subscribe(location, "zigbeeOff", zigbeeOffHandler)
    }
    
    if (alertZigbeeOn) {
        subscribe(location, "zigbeeOn", zigbeeOnHandler)
    }
    
    if (alertZwaveCrashed) {
        subscribe(location, "zwaveCrashed", zwaveCrashedHandler)
    }
    
    if (alertFirmwareUpdate) {
        def currentTime = new Date()
        def runTime = timeToday("20:00")
    
        schedule("$currentTime.seconds $runTime.minutes $runTime.hours * * ? *", checkForUpdate)
        checkForUpdate()
    }
}

def rebootHandler(evt) {
    logDebug("rebootHandler: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("Hub has rebooted.")
}

def severeLoadHandler(evt) {
    logDebug("severeLoadHandler: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("Hub has severe load!")
}

def lowMemoryHandler(evt) {
    logDebug("lowMemoryHandler: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("Hub has low memory!")
}

def zigbeeOffHandler(evt) {
    logDebug("zigbeeOffHandler: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("Zigbee has been turned off!")
}

def zigbeeOnHandler(evt) {
    logDebug("zigbeeOnHandler: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("Zigbee has been turned on!")
}

def zwaveCrashedHandler(evt) {
    logDebug("zwaveCrashedHandler: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("Z-Wave has crashed!")
}

def checkForUpdate() {
	def params = [
        uri: "http://127.0.0.1:8080",
        path:"/hub/cloud/checkForUpdate",
		contentType: "application/json"
	]
	def result = null
	logDebug("Checking for firmware update...")
	try {
		httpGet(params) { resp ->
			logDebug("Status: ${resp.status}, Data: ${resp.data}")
			if (resp.status == 200) {
            	if(resp.data.status != "NO_UPDATE_AVAILABLE") {
	            	logDebug("Firmware update available: ${resp.data.version}")
			        personToNotify.deviceNotification("Firmware update available: ${resp.data.version}")
	            } else {
	                logDebug("Firmware is up to date")
			    }
			}
		}
	}
	catch (e) {
		log.error("Exception Received: $e")
	}
}