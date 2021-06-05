/**
 *  Person Status Device Handler
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
 
String getVersionNum() { return "3.0.0" }
String getVersionLabel() { return "Person Status, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Person Status", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/person-status.groovy"
	) {
        capability "Actuator"
        capability "Notification"
        capability "Presence Sensor"
        capability "Sensor"
        capability "Sleep Sensor"

        attribute "status", "enum", ["home", "away", "sleep"]
        attribute "message", "string"
        
        command "awake"
        command "asleep"
        command "arrived"
        command "departed"
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
    if (!device.currentValue("status")) {
        awake()
    }
}

def awake() {
    if (device.currentValue("status") == "sleep") {
        sendEvent(name: "status", value: "home", descriptionText: "$device.displayName changed to home (awake)")
    
        sendEvent(name: "presence", value: "present")
        sendEvent(name: "sleeping", value: "not sleeping")
    }
}

def asleep() {
    if (device.currentValue("status") == "home") {
        sendEvent(name: "status", value: "sleep", descriptionText: "$device.displayName changed to sleep")
    
        sendEvent(name: "presence", value: "present")
        sendEvent(name: "sleeping", value: "sleeping")
    }
}

def arrived() {
    if (device.currentValue("presence") == "not present") {
        sendEvent(name: "status", value: "home", descriptionText: "$device.displayName changed to home (arrived)")
    
        sendEvent(name: "presence", value: "present")
        sendEvent(name: "sleeping", value: "not sleeping")
    }
}

def departed() {
    if (device.currentValue("presence") == "present") {
        sendEvent(name: "status", value: "away", descriptionText: "$device.displayName changed to away")
    
        sendEvent(name: "presence", value: "not present")
        sendEvent(name: "sleeping", value: "not sleeping")
    }
}

def deviceNotification(message) {
  	sendEvent(name: "message", value: "${message}", isStateChange: true)
}