/**
 *  Virtual Power Source Driver
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
 
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "Virtual Power Source, version ${getVersionNum()} on ${getPlatform()}" }

 metadata {
    definition (
		name: "Virtual Power Source", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/virtual-power-source.groovy"
	) {
        capability "Actuator"
        capability "PowerSource"
        capability "Sensor"
        
        command "battery"
        command "dc"
        command "mains"
        command "unknown"
    }
}

def installed() {
    unknown()
}

def battery() {
    sendEvent(name: "powerSource", value: "battery")
}

def dc() {
    sendEvent(name: "powerSource", value: "dc")
}

def mains() {
    sendEvent(name: "powerSource", value: "mains")
}

def unknown() {
    sendEvent(name: "powerSource", value: "unknown")
}