/**
 *  Zone Device
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
 
String getName() { return "Zone Device" }
String getVersionNum() { return "6.0.1" }
String getVersionLabel() { return "${getName()}, version ${getVersionNum()}" }

metadata {
    definition (
		name: "${getName()}", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/zones/zone-device.groovy"
	) {
        capability "Actuator"
        capability "ContactSensor"
        capability "Sensor"
        capability "Switch"

        attribute "occupancy", "enum", ["occupied", "checking", "vacant"]
        
        command "occupied"
        command "checking"
        command "vacant"
        
        command "open"
        command "close"
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
    if (!device.currentValue("occupancy") || !device.currentValue("switch")) {
        vacant()
    }
    if (!device.currentValue("contact")) {
        open()
    }
}

def occupied() {
    sendEvent(name: "occupancy", value: "occupied")
    sendEvent(name: "switch", value: "on")
}

def checking() {
    sendEvent(name: "occupancy", value: "checking")
    sendEvent(name: "switch", value: "on")
}

def vacant() {
    sendEvent(name: "occupancy", value: "vacant")
    sendEvent(name: "switch", value: "off")
}

def on() {
    occupied()
}

def off() {
    vacant()
}

def open() {
    sendEvent(name: "contact", value: "open")
}

def close() {
    sendEvent(name: "contact", value: "closed")
}