/**
 *  NUT Event Monitor Driver
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
 
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "NUT Event Monitor, version ${getVersionNum()} on ${getPlatform()}" }

 metadata {
    definition (
		name: "NUT Event Monitor", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/nut-event-monitor.groovy"
	) {
        capability "PowerSource"
        capability "Sensor"
        
        attribute "networkStatus", "enum", ["online", "offline"]
        attribute "lastEvent", "enum", ["online", "onbatt", "lowbatt", "fsd", "commok", "commbad", "shutdown", "replbatt", "nocomm"]
    }
}

def installed() {
    sendEvent(name: "networkStatus", value: "offline")
    sendEvent(name: "powerSource", value: "unknown")
    sendEvent(name: "lastEvent", value: "nocomm")
}

def parse(String message) {
    logDebug("parse: ${message}")
    
    if (message == "online") {
        sendEvent(name: "networkStatus", value: "online")
        sendEvent(name: "powerSource", value: "mains")
        sendEvent(name: "lastEvent", value: "online", isStateChange: true)
    
    } else if (message == "onbatt") {
        sendEvent(name: "networkStatus", value: "online")
        sendEvent(name: "powerSource", value: "battery")
        sendEvent(name: "lastEvent", value: "onbatt", isStateChange: true)
    
    } else if (message == "lowbatt") {
        sendEvent(name: "networkStatus", value: "online")
        sendEvent(name: "powerSource", value: "battery")
        sendEvent(name: "lastEvent", value: "lowbatt", isStateChange: true)
    
    } else if (message == "fsd") {
        sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "lastEvent", value: "fsd", isStateChange: true)
    
    } else if (message == "commok") {
        sendEvent(name: "networkStatus", value: "online")
        sendEvent(name: "lastEvent", value: "commok", isStateChange: true)
    
    } else if (message == "commbad") {
        sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "lastEvent", value: "commbad", isStateChange: true)
    
    } else if (message == "shutdown") {
        sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "lastEvent", value: "shutdown", isStateChange: true)
    
    } else if (message == "replbatt") {
        sendEvent(name: "networkStatus", value: "online")
        sendEvent(name: "lastEvent", value: "replbatt", isStateChange: true)
    
    } else if (message == "nocomm") {
        sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "lastEvent", value: "nocomm", isStateChange: true)
    
    } else {
        log.warn "Unknown message: ${message}"
    }
}