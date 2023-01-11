/**
 *  Virtual Garage Door Device Handler
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
String getVersionLabel() { return "Virtual Garage Door, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Virtual Garage Door", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/virtual-garage-door.groovy"
	) {
        capability "Actuator"
        capability "ContactSensor"
        capability "DoorControl"
        capability "GarageDoorControl"
        capability "Sensor"
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
    if (!device.currentValue("contact")) {
        sendEvent(name: "contact", "value": "closed")
    }
    if (!device.currentValue("door")) {
        sendEvent(name: "door", "value": "closed")
    }
}

def open() {
    parent?.open()
}

def close() {
    parent?.close()
}