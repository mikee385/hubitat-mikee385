/**
 *  Appliance Status Device Handler
 *
 *  Copyright 2019 Michael Pierce
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
    definition (name: "Appliance Status", namespace: "mikee385", author: "Michael Pierce", importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/appliance-status.groovy")) {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"

        attribute "state", "enum", ["running", "finished", "unstarted"]
        attribute "running", "boolean"
        attribute "finished", "boolean"
        attribute "unstarted", "boolean"

        command "start"
        command "finish"
        command "reset"
    }
}

def installed() {
    log.debug "Executing 'installed'"
    
    initialize()
}

def updated() {
    log.debug "Executing 'updated'"
    
    unschedule()
    initialize()
}

def initialize() {
    log.debug "Executing 'initialize'"
    
    if (!device.currentValue("state")) {
        reset()
    }
}

// parse events into attributes
def parse(String description) {
    log.debug "Parsing '${description}'"
}

// handle commands
def on() {
    log.debug "Executing 'on'"
    
    start()
}

def off() {
    log.debug "Executing 'off'"
    
    finish()
}

def start() {
    log.debug "Executing 'start'"    
    
    sendEvent(name: "state", value: "running", descriptionText: "$device.displayName changed to running", displayed: true)
    
    sendEvent(name: "running", value: true, displayed: false)
    sendEvent(name: "finished", value: false, displayed: false)
    sendEvent(name: "unstarted", value: false, displayed: false)
    
    sendEvent(name: "switch", value: "on", displayed: false)
}

def finish() {
    log.debug "Executing 'finish'"
    
    sendEvent(name: "state", value: "finished", descriptionText: "$device.displayName changed to finished", displayed: true)
    
    sendEvent(name: "running", value: false, displayed: false)
    sendEvent(name: "finished", value: true, displayed: false)
    sendEvent(name: "unstarted", value: false, displayed: false)
    
    sendEvent(name: "switch", value: "off", displayed: false)
}

def reset() {
    log.debug "Executing 'reset'"
    
    sendEvent(name: "state", value: "unstarted", descriptionText: "$device.displayName changed to unstarted", displayed: true)
    
    sendEvent(name: "running", value: false, displayed: false)
    sendEvent(name: "finished", value: false, displayed: false)
    sendEvent(name: "unstarted", value: true, displayed: false)
    
    sendEvent(name: "switch", value: "off", displayed: false)
}