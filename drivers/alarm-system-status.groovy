/**
 *  Alarm System Status Device Handler
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
 
String getVersionNum() { return "1.1.0" }
String getVersionLabel() { return "Alarm System Status, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Alarm System Status", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/alarm-system-status.groovy"
	) {
        capability "Actuator"
        capability "Sensor"

        attribute "status", "enum", ["disarmed", "armed home", "armed away"]
        
        command "disarm"
        command "armHome"
        command "armAway"
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
        disarm()
    }
}

def disarm() {
    if (device.currentValue("status") != "disarmed") {
        sendEvent(name: "status", value: "disarmed", descriptionText: "$device.displayName changed to disarmed")
    }
}

def armHome() {
    if (device.currentValue("status") != "armed home") {
        sendEvent(name: "status", value: "armed home", descriptionText: "$device.displayName changed to armed home")
    }
}
    
def armAway() {
    if (device.currentValue("status") != "armed away") {
        sendEvent(name: "status", value: "armed away", descriptionText: "$device.displayName changed to armed away")
    }
}