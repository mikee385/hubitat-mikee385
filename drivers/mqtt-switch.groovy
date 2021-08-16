/**
 *  MQTT Switch Driver
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
 
String getName() { return "MQTT Switch" }
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "${getName()}, version ${getVersionNum()}" }

metadata {
    definition (
		name: "${getName()}", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/mqtt-switch.groovy"
	) {
		capability "Actuator"
        capability "Initialize"
        capability "Sensor"
        capability "Switch"
    }
    preferences {
        input name: "brokerIPAddress", type: "text", title: "MQTT Broker IP Address:", required: true, displayDuringSetup: true
        input name: "brokerPort", type: "text", title: "MQTT Broker Port:", defaultValue: "1883", required: true, displayDuringSetup: true
    	input name: "username", type: "text", title: "MQTT Username (optional):", description: "(blank if none)", required: false, displayDuringSetup: true
    	input name: "password", type: "password", title: "MQTT Password (optional):", description: "(blank if none)", required: false, displayDuringSetup: true
    	input name: "publishTopic", type: "text", title: "Topic to Publish:", description: "Example Topic (topic/device/#)", required: true, displayDuringSetup: true
    	input name: "subscribeTopic", type: "text", title: "Topic to Subscribe:", description: "Example Topic (topic/device/#)", required: true, displayDuringSetup: true
    	input name: "qos", type: "number", title: "QOS (optional):", required: false, displayDuringSetup: true
    	input name: "retained", type: "bool", title: "Retained (optional):", defaultValue: false, displayDuringSetup: true
    	input name: "onCommand", type: "text", title: "Command to send for On:", description: "(e.g. on, true)", defaultValue: "on", required: true, displayDuringSetup: true
    	input name: "offCommand", type: "text", title: "Command to send for Off:", description: "(e.g. off, false)", defaultValue: "off", required: true, displayDuringSetup: true
    	input name: "onStatus", type: "text", title: "Response when On:", description: "(e.g. on, true)", defaultValue: "true", required: true, displayDuringSetup: true
    	input name: "offStatus", type: "text", title: "Response when Off:", description: "(e.g. off, false)", defaultValue: "false", required: true, displayDuringSetup: true
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
        	interfaces.mqtt.subscribe(subscribeTopic, qos)
        } else {
        	interfaces.mqtt.subscribe(subscribeTopic)
        }
        log.info "Subscribed to ${subscribeTopic}"
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
	
	state.topic = response.topic
	state.payload = response.payload
	
	if (response.topic == subscribeTopic) {
		logDebug("Subscribed topic: ${response.topic}")
		
		if (response.payload == onStatus) {
			sendEvent(name: "switch", value: "on")
			logDebug("On payload: ${response.payload}")
		} else if (response.payload == offStatus) {
			sendEvent(name: "switch", value: "off")
			logDebug("Off payload: ${response.payload}")
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

def on() {
    logDebug("Publishing to topic: ${publishTopic}")
	logDebug("Sending payload: $onCommand")
    
	if (qos != null) {
		if (retained != null) {
			interfaces.mqtt.publish(topic, onCommand, qos, retained)
		} else {
			interfaces.mqtt.publish(topic, onCommand, qos)
		}
	} else {
		if (retained != null) {
			interfaces.mqtt.publish(topic, onCommand, 1, retained)
		} else {
			interfaces.mqtt.publish(topic, onCommand)
		}
	}
}

def off() {
    logDebug("Publishing to topic: ${publishTopic}")
	logDebug("Sending payload: $offCommand")
    
    if (qos != null) {
		if (retained != null) {
			interfaces.mqtt.publish(topic, offCommand, qos, retained)
		} else {
			interfaces.mqtt.publish(topic, offCommand, qos)
		}
	} else {
		if (retained != null) {
			interfaces.mqtt.publish(topic, offCommand, 1, retained)
		} else {
			interfaces.mqtt.publish(topic, offCommand)
		}
	}
}