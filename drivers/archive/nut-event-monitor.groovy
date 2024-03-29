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
 
String getVersionNum() { return "3.7.0" }
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
    logDebug("installed")
    
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
    unschedule("terminateConnection")
    telnetClose()
    
    logDebug("refresh")
    
    try {
		telnetConnect([termChars:[10]], nutServerHost, nutServerPort.toInteger(), null, null)
		sendCommand("GET VAR ${upsName} ups.status")
		runIn(30, terminateConnection)
        
	} catch (err) {
	    telnetClose()
	    
		log.error "Refresh telnet connection error: ${err}"
		sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "powerSource", value: "unknown")
        sendEvent(name: "lastEvent", value: "nocomm")
	
	}
}

def terminateConnection() {
    telnetClose()
    
    log.error "No response from telnet command"
	sendEvent(name: "networkStatus", value: "offline")
    sendEvent(name: "powerSource", value: "unknown")
    sendEvent(name: "lastEvent", value: "nocomm")
}

def parse(String message) {
    unschedule("terminateConnection")
    
    logDebug("parse: ${message}")
    
    def online = false
    def onbatt = false
    def fsd = false
    def nocomm = false
    
    def values = message.split(" ")
    if (values[0] == "VAR" && values[1] == upsName && values[2] == "ups.status") {
        for (int i = 3; i < values.size(); i++) {
            def status = values[i].replace("\"", "")
            if (status == "OL") {
                online = true
            } else if (status == "OB") {
                onbatt = true
            } else if (status == "FSD") {
                fsd = true
            } else if (status == "OFF") {
                nocomm = true
            }
        }
        
        if (nocomm) {
            logDebug("parse: status is OFF")
            sendEvent(name: "networkStatus", value: "offline")
            sendEvent(name: "powerSource", value: "unknown")
            sendEvent(name: "lastEvent", value: "nocomm")
        
        } else if (fsd) {
            logDebug("parse: status is FSD")
            sendEvent(name: "networkStatus", value: "offline")
            sendEvent(name: "powerSource", value: "unknown")
            sendEvent(name: "lastEvent", value: "fsd")
        
        } else if (onbatt) {
            logDebug("parse: status is OB")
            sendEvent(name: "networkStatus", value: "online")
            sendEvent(name: "powerSource", value: "battery")
            sendEvent(name: "lastEvent", value: "onbatt")
        
        } else if (online) {
            logDebug("parse: status is OL")
            sendEvent(name: "networkStatus", value: "online")
            sendEvent(name: "powerSource", value: "mains")
            sendEvent(name: "lastEvent", value: "online")
            
        } else {
            log.error "Unknown status: ${message}"
            sendEvent(name: "networkStatus", value: "offline")
            sendEvent(name: "powerSource", value: "unknown")
            sendEvent(name: "lastEvent", value: "commbad")
        
        }
    
    } else {
        log.error "Unknown message: ${message}"
        sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "powerSource", value: "unknown")
        sendEvent(name: "lastEvent", value: "commbad")
        
    }
    
    telnetClose()
}

def telnetStatus(String message) {
    unschedule("terminateConnection")
    
    if (message == "receive error: Stream is closed") {
        logDebug("telnetStatus: ${message}")
    
    } else {
        log.error "telnetStatus: ${message}"
        sendEvent(name: "networkStatus", value: "offline")
        sendEvent(name: "powerSource", value: "unknown")
        sendEvent(name: "lastEvent", value: "commbad")
    
	}
	
	telnetClose()
}

def sendCommand(cmd) {
	logDebug("sendCommand: ${cmd}")
	return sendHubCommand(new hubitat.device.HubAction("${cmd}", hubitat.device.Protocol.TELNET))
}