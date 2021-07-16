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
String getVersionNum() { return "2.0.0" }
String getVersionLabel() { return "${getName()}, version ${getVersionNum()}" }

metadata {
    definition (
		name: "${getName()}", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/zones/zone-device.groovy"
	) {
        capability "Actuator"
        capability "Sensor"

        attribute "occupancy", "enum", ["engaged", "active", "checking", "vacant"]
        
        command "occupancyEngaged"
        command "occupancyActive"
        command "occupancyChecking"
        command "occupancyVacant"
        
        attribute "activity", "enum", ["active", "unknown", "inactive"]
        
        command "activityActive"
        command "activityUnknown"
        command "activityInactive"
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
    if (!device.currentValue("occupancy")) {
        occupancyVacant()
    }
    if (!device.currentValue("activity")) {
        activityInactive()
    }
}

def occupancyEngaged() {
    sendEvent(name: "occupancy", value: "engaged")
}

def occupancyActive() {
    sendEvent(name: "occupancy", value: "active")
}

def occupancyChecking() {
    sendEvent(name: "occupancy", value: "checking")
}

def occupancyVacant() {
    sendEvent(name: "occupancy", value: "vacant")
}

def activityActive() {
    sendEvent(name: "activity", value: "active")
}

def activityUnknown() {
    sendEvent(name: "activity", value: "unknown")
}

def activityInactive() {
    sendEvent(name: "activity", value: "inactive")
}