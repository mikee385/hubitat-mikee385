/**
 *  Virtual Alexa Button Driver
 *
 *  Copyright 2020 Michael Pierce
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
 metadata {
    definition (name: "Virtual Alexa Button", namespace: "mikee385", author: "Michael Pierce", importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/virtual-alexa-button.groovy") {
        capability "Actuator"
        capability "Contact Sensor"
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
}

def toggleOff() {
    sendEvent(name: "switch", value: "off", isStateChange: true)
    sendEvent(name: "contact", value: "closed", isStateChange: true)
}

def push(button) {
    sendEvent(name: "switch", value: "on", isStateChange: true)
    sendEvent(name: "contact", value: "open", isStateChange: true)

    sendEvent(name: "momentary", value: "pushed", isStateChange: true)    
    sendEvent(name: "pushed", value: "${button}", isStateChange: true, type: "digital")
    
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