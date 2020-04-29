/**
 *  Person Status Device Handler
 *
 *  Copyright 2019 Michael Pierce
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
 
String getVersionNum() { return "1.0.1" }
String getVersionLabel() { return "Person Status, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Person Status", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/person-status.groovy"
	) {
        capability "Actuator"
        capability "Presence Sensor"
        capability "Sensor"
        capability "Sleep Sensor"

        attribute "state", "enum", ["home", "away", "sleep"]
        
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
    if (!device.currentValue("state")) {
        awake()
    }
}

def awake() {
    sendEvent(name: "state", value: "home", descriptionText: "$device.displayName changed to home (awake)", displayed: true)
    
    sendEvent(name: "presence", value: "present", displayed: false)
    sendEvent(name: "sleeping", value: "not sleeping", displayed: false)
}

def asleep() {
    sendEvent(name: "state", value: "sleep", descriptionText: "$device.displayName changed to sleep", displayed: true)
    
    sendEvent(name: "presence", value: "present", displayed: false)
    sendEvent(name: "sleeping", value: "sleeping", displayed: false)
}

def arrived() {
    sendEvent(name: "state", value: "home", descriptionText: "$device.displayName changed to home (arrived)", displayed: true)
    
    sendEvent(name: "presence", value: "present", displayed: false)
    sendEvent(name: "sleeping", value: "not sleeping", displayed: false)
}

def departed() {
    sendEvent(name: "state", value: "away", descriptionText: "$device.displayName changed to away", displayed: true)
    
    sendEvent(name: "presence", value: "not present", displayed: false)
    sendEvent(name: "sleeping", value: "not sleeping", displayed: false)
}