/**
 *  Roomba Native Driver
 *
 *  Copyright 2021 Michael Pierce
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
 
String getVersionNum() { return "1.0.0-alpha.4" }
String getVersionLabel() { return "Roomba Native Driver, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Roomba Native Driver", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/roomba-native.groovy"
	) {
    }
    
    preferences {
        input name: "roombaIpAddress", type: "string", title: "Roomba IP Address", required: true, displayDuringSetup: true
        input name: "roombaPort", type: "string", title: "Roomba Port", required: true, displayDuringSetup: true
        input name: "roombaBlid", type: "string", title: "Roomba Blid", required: true, displayDuringSetup: true
        input name: "roombaPassword", type: "password", title: "Roomba Password", required: true, displayDuringSetup: true
    }
    section {
        input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
    }
}

def installed() {
    initialize()
}

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    log.debug "IP Address: ${settings?.roombaIpAddress}"
    log.debug "Port: ${settings?.roombaPort}"
    log.debug "Client ID: hubitat_${normalize(location.hubs[0].name)}-${hub.hardwareID.toLowerCase()}"
    log.debug "Blid: ${settings?.roombaBlid}"
    log.debug "Password: ${settings?.roombaPassword}"
    log.debug "URL: tls://${settings?.roombaIpAddress}:${settings?.roombaPort}"
    
    try {   
        interfaces.mqtt.connect("tls://${settings?.roombaIpAddress}:${settings?.roombaPort}", "hubitat_${normalize(location.hubs[0].name)}-${hub.hardwareID.toLowerCase()}", settings?.roombaBlid, settings?.roombaPassword)
       
        // delay for connection
        pauseExecution(1000)
        
        log.debug "Connected: ${interfaces.mqtt.isConnected()}"
        
        try {
            interfaces.mqtt.disconnect()
        } catch(e) {
            log.warn "Disconnection from broker failed: ${e.message}"
        }
    } catch(Exception e) {
        log.error "[initialize] ${e}"
    }
}