/**
 *  MQTT Presence Driver
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
 
String getName() { return "MQTT Presence" }
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "${getName()}, version ${getVersionNum()}" }

 metadata {
    definition (
		name: "${getName()}", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/mqtt-presence.groovy"
	) {
        capability "Initialize"
        capability "Presence Sensor"
        capability "Sensor"
        
        command "arrived"
        command "departed"
    }
    preferences {
        input name: "brokerIPAddress", type: "text", title: "MQTT Broker IP Address:", required: true, displayDuringSetup: true
        input name: "brokerPort", type: "text", title: "MQTT Broker Port:", required: true, displayDuringSetup: true
    	input name: "username", type: "text", title: "MQTT Username (optional):", description: "(blank if none)", required: false, displayDuringSetup: true
    	input name: "password", type: "password", title: "MQTT Password (optional):", description: "(blank if none)", required: false, displayDuringSetup: true
    	input name: "subscribedTopic", type: "text", title: "Topic to Subscribe:", description: "Example Topic (topic/device/#)", required: true, displayDuringSetup: true
    	input name: "qos", type: "number", title: "QOS (optional):", required: false, displayDuringSetup: true
    	input name: "presentPayload", type: "text", title: "Payload when Present:", description: "(e.g. on, home)", required: true, displayDuringSetup: true
    	input name: "notPresentPayload", type: "text", title: "Payload when Not Present:", description: "(e.g. off, not home)", required: true, displayDuringSetup: true
        input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false, displayDuringSetup: true
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
    try {
        state.mqttBroker = "tcp://${brokerIPAddress}:${brokerPort}"
        interfaces.mqtt.connect(state.mqttBroker, "hubitat-${device.id}", username, password)
        pauseExecution(1000)
        log.info "Connection established to ${state.mqttBroker}"
		
        if (qos != null) {
        	interfaces.mqtt.subscribe(subscribedTopic, qos)
        } else {
        	interfaces.mqtt.subscribe(subscribedTopic)
        }
        log.info "Subscribed to ${subscribedTopic}"
    } catch(e) {
        log.error "Error while initilizing: ${e.message}"
    }
}

def uninstalled() {
    log.info "Disconnecting from ${state.mqttBroker}"
    interfaces.mqtt.disconnect()
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def parse(String description) {
	response = interfaces.mqtt.parseMessage(description)
	logDebug("Recieved: $response")
	
	state.topic = reponse.topic
	state.payload = reponse.payload
	
	if (reponse.topic == subscribedTopic) {
		logDebug("Subscribed topic: ${response.topic}")
		
		if (response.payload == presentPayload) {
			arrived()
			logDebug("Present payload: ${response.payload}")
		} else if (response.payload == notPresentPayload) {
			departed()
			logDebug("Not Present payload: ${response.payload}")
		} else {
			log.warn "Unknown payload: ${response.payload}"
		}
	} else {
		log.warn "Unknown topic: ${response.topic}"
	}
}

def mqttClientStatus(String status) {
	if (status.startsWith("Error")) {
		def restart = false
		if (!interfaces.mqtt.isConnected()) {
			log.warn "MQTT not connected: ${status}"
			restart = true
		}  else if (status.contains("lost")) {
			log.warn "MQTT connection lost: ${status}"
			restart = true
		} else {
			log.warn "MQTT error: ${status}"
		}
		if (restart) {
			def reconnected = false
			for (int i = 0; i < 60; i++) {
				// wait for a minute for things to settle out, server to restart, etc...
				pauseExecution(1000*60)
				initialize()
				if (interfaces.mqtt.isConnected()) {
					log.info "MQTT reconnected!"
					reconnected = true
					break
				}
			}
			if (!reconnected) {
				log.error "Unable to reconnect to MQTT. Device must be reinitialized."
			}
		}
	} else {
    	logDebug("MQTT OK: ${status}")
    }
}

def arrived() {
    sendEvent(name: "presence", value: "present")
}

def departed() {
    sendEvent(name: "presence", value: "not present")
}