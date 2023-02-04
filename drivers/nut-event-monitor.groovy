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
 
String getVersionNum() { return "3.0.0" }
String getVersionLabel() { return "NUT Event Monitor, version ${getVersionNum()} on ${getPlatform()}" }

 metadata {
    definition (
		name: "NUT Event Monitor", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/nut-event-monitor.groovy"
	) {
        capability "Initialize"
        capability "PowerSource"
        capability "Refresh"
        capability "Sensor"
        capability "Telnet"
        
        attribute "networkStatus", "enum", ["online", "offline"]
        attribute "lastEvent", "enum", ["online", "onbatt", "lowbatt", "fsd", "commok", "commbad", "shutdown", "replbatt", "nocomm"]
    }
    preferences {
        input name: "upsName", type: "text", title: "UPS Name:", required: true, displayDuringSetup: true
        input name: "nutServerHost", type: "text", description: "IP or hostname of NUT server", title: "NUT server hostname"
        input name: "nutServerPort", type: "number", description: "Port number of NUT server", title: "NUT server port number", defaultValue: 3493, range: "1..65535"
        input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false, displayDuringSetup: true
    }
}

def installed() {
    sendEvent(name: "networkStatus", value: "offline")
    sendEvent(name: "powerSource", value: "unknown")
    sendEvent(name: "lastEvent", value: "nocomm")
}

def initialize() {
    refresh()
}

def updated() {
    unschedule()
    initialize()
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def handleEvent(notifyType) {
    logDebug("handleEvent: ${notifyType}")
    
    if (notifyType == "ONLINE") {
        sendEvent(name: "networkStatus", value: "online")
        sendEvent(name: "powerSource", value: "mains")
        sendEvent(name: "lastEvent", value: "online", isStateChange: true)
    
    } else if (notifyType == "ONBATT") {
        sendEvent(name: "networkStatus", value: "online")
        sendEvent(name: "powerSource", value: "battery")
        sendEvent(name: "lastEvent", value: "onbatt", isStateChange: true)
    
    } else if (notifyType == "LOWBATT") {
        sendEvent(name: "networkStatus", value: "online")
        sendEvent(name: "lastEvent", value: "lowbatt", isStateChange: true)
    
    } else if (notifyType == "FSD") {
        sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "powerSource", value: "unknown")
        sendEvent(name: "lastEvent", value: "fsd", isStateChange: true)
    
    } else if (notifyType == "COMMOK") {
        sendEvent(name: "networkStatus", value: "online")
        sendEvent(name: "lastEvent", value: "commok", isStateChange: true)
    
    } else if (notifyType == "COMMBAD") {
        sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "powerSource", value: "unknown")
        sendEvent(name: "lastEvent", value: "commbad", isStateChange: true)
    
    } else if (notifyType == "SHUTDOWN") {
        sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "powerSource", value: "unknown")
        sendEvent(name: "lastEvent", value: "shutdown", isStateChange: true)
    
    } else if (notifyType == "REPLBATT") {
        sendEvent(name: "networkStatus", value: "online")
        sendEvent(name: "lastEvent", value: "replbatt", isStateChange: true)
    
    } else if (notifyType == "NOCOMM") {
        sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "powerSource", value: "unknown")
        sendEvent(name: "lastEvent", value: "nocomm", isStateChange: true)
    
    } else {
        log.warn "Unknown event: ${notifyType}"
    }
}

def refresh() {
    try {
		telnetConnect([termChars:[10]], nutServerHost, nutServerPort.toInteger(), null, null)
		sendCommand("GET VAR ${upsName} ups.status")
        sendCommand("LOGOUT")
        telnetClose()
	} catch (err) {
		log.error "Refresh telnet connection error: ${err}"
	}
}

def parse(String message) {
    log.debug "parse: ${message}"
}

def telnetStatus(String message) {
    log.error "telnetStatus: ${message}"
}

def sendCommand(cmd) {
	log.debug "sendCommand: ${cmd}"
	return sendHubCommand(new hubitat.device.HubAction("${cmd}", hubitat.device.Protocol.TELNET))
}