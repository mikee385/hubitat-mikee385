/**
 *  Zone Device
 *
 *  Copyright 2022 Michael Pierce
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
String getVersionNum() { return "10.0.0-beta.4" }
String getVersionLabel() { return "${getName()}, version ${getVersionNum()}" }

metadata {
    definition (
		name: "${getName()}", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/zones/zone-device.groovy"
	) {
        capability "ContactSensor"
        capability "Sensor"

        attribute "occupancy", "enum", ["occupied", "unknown", "unoccupied"]
        attribute "activity", "enum", ["engaged", "active", "checking", "closing", "unknown", "inactive"]
        attribute "event", "enum", ["engaged", "disengaged", "active", "inactive", "momentary", "questionable", "idle"]
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
        sendEvent(name: "contact", value: "open")
    }
    if (!device.currentValue("occupancy")) {
        sendEvent(name: "occupancy", value: "unoccupied")
    }
    if (!device.currentValue("activity")) {
        sendEvent(name: "activity", value: "inactive")
    }
    if (!device.currentValue("event")) {
        sendEvent(name: "event", value: "idle")
    }
}