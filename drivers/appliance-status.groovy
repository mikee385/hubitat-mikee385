/**
 *  Appliance Status Device Handler
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
String getVersionLabel() { return "Appliance Status, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Appliance Status", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/appliance-status.groovy"
	) {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"

        attribute "status", "enum", ["idle", "running", "finished"]
        
        attribute "startTime", "string"
        attribute "finishTime", "string"
        attribute "resetTime", "string"

        command "start"
        command "finish"
        command "reset"
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
        reset()
    }
}

def on() {
    start()
}

def off() {
    finish()
}

def start() {
    sendEvent(name: "status", value: "running", descriptionText: "$device.displayName changed to running", displayed: true)    
    sendEvent(name: "switch", value: "on", displayed: false)
    sendEvent(name: "startTime", value: new Date(), displayed: false)
}

def finish() {
    sendEvent(name: "status", value: "finished", descriptionText: "$device.displayName changed to finished", displayed: true)    
    sendEvent(name: "switch", value: "off", displayed: false)
    sendEvent(name: "finishTime", value: new Date(), displayed: false)
}

def reset() {
    sendEvent(name: "status", value: "idle", descriptionText: "$device.displayName changed to idle", displayed: true)    
    sendEvent(name: "switch", value: "off", displayed: false)
    sendEvent(name: "resetTime", value: new Date(), displayed: false)
}