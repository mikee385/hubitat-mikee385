/**
 *  Child Button Driver
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
 
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "Child Button, version ${getVersionNum()} on ${getPlatform()}" }

 metadata {
    definition (
		name: "Child Button", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/child-button.groovy"
	) {
        capability "Actuator"
        capability "Momentary"
        capability "PushableButton"
        capability "Sensor"
        capability "Switch"
        
        command "push", ["number"]
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
    sendEvent(name: "numberOfButtons", value: 1)
    
    if (!device.currentValue("switch")) {
        sendEvent(name: "switch", value: "off")
    }
}

def setCommand(name) {
    state.command = name
}

def toggleOff() {
    sendEvent(name: "switch", value: "off", isStateChange: true)
}

def push(button) {
    sendEvent(name: "switch", value: "on", isStateChange: true)
    sendEvent(name: "pushed", value: "${button}", isStateChange: true, type: "digital")
    
    if (state.method) {
        parent.runCommand(state.command)
    } else {
        log.warn "Command has not been specified"
    }
    
    runIn(1, toggleOff)
}

def push() {
    push(1)
}

def on() {
    push()
}

def off() {
    push()
}