/**
 *  Alexa Button Driver
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
String getVersionLabel() { return "Alexa Button, version ${getVersionNum()} on ${getPlatform()}" }

 metadata {
    definition (
		name: "Alexa Button", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/alexa-button.groovy"
	) {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
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
    if (!device.currentValue("switch")) {
        reset()
    }
}

def on() {
    trigger()
}

def off() {
    trigger()
}

def trigger() {
    sendEvent(name: "switch", value: "on")
    runIn(1, "reset")
}

def reset() {
    sendEvent(name: "switch", value: "off")
}