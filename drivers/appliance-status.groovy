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
    definition (name: "Appliance Status", namespace: "mikee385", author: "Michael Pierce", importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/appliance-status.groovy") {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"

        attribute "state", "enum", ["running", "finished", "unstarted"]
        attribute "stateColor", "enum", ["running-blue", "running-orange", "running-gray", "finished-blue", "finished-orange", "finished-gray", "unstarted-blue", "unstarted-orange", "unstarted-gray"]

        attribute "running", "boolean"
        attribute "finished", "boolean"
        attribute "unstarted", "boolean"

        command "start"
        command "finish"
        command "reset"
    }
    
    preferences {
        input name: "stateColorRunning", type: "enum", title: "What color should be shown for 'Running'?", options: ["Blue", "Orange", "Gray"], defaultValue: "Blue", required: true, displayDuringSetup: false
        input name: "stateColorFinished", type: "enum", title: "What color should be shown for 'Finished'?", options: ["Blue", "Orange", "Gray"], defaultValue: "Orange", required: true, displayDuringSetup: false
        input name: "stateColorUnstarted", type: "enum", title: "What color should be shown for 'Unstarted'?", options: ["Blue", "Orange", "Gray"], defaultValue: "Gray", required: true, displayDuringSetup: false
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
    if (!device.currentValue("state")) {
        reset()
    }
}

def on() {
    start()
}

def off() {
    finish()
}

def start() {
    sendEvent(name: "state", value: "running", descriptionText: "$device.displayName changed to running", displayed: true)
    
    sendEvent(name: "running", value: true, displayed: false)
    sendEvent(name: "finished", value: false, displayed: false)
    sendEvent(name: "unstarted", value: false, displayed: false)
    
    sendEvent(name: "switch", value: "on", displayed: false)
    
    if (stateColorRunning == "Blue") {
        sendEvent(name: "stateColor", value: "running-blue", displayed: false)
    } else if (stateColorRunning == "Orange") {
        sendEvent(name: "stateColor", value: "running-orange", displayed: false)
    } else if (stateColorRunning == "Gray") {
        sendEvent(name: "stateColor", value: "running-gray", displayed: false)
    } else {
        sendEvent(name: "stateColor", value: "running-blue", displayed: false)
    }
}

def finish() {
    sendEvent(name: "state", value: "finished", descriptionText: "$device.displayName changed to finished", displayed: true)
    
    sendEvent(name: "running", value: false, displayed: false)
    sendEvent(name: "finished", value: true, displayed: false)
    sendEvent(name: "unstarted", value: false, displayed: false)
    
    sendEvent(name: "switch", value: "off", displayed: false)
    
    if (stateColorFinished == "Blue") {
        sendEvent(name: "stateColor", value: "finished-blue", displayed: false)
    } else if (stateColorFinished == "Orange") {
        sendEvent(name: "stateColor", value: "finished-orange", displayed: false)
    } else if (stateColorFinished == "Gray") {
        sendEvent(name: "stateColor", value: "finished-gray", displayed: false)
    } else {
        sendEvent(name: "stateColor", value: "finished-orange", displayed: false)
    }
}

def reset() {
    sendEvent(name: "state", value: "unstarted", descriptionText: "$device.displayName changed to unstarted", displayed: true)
    
    sendEvent(name: "running", value: false, displayed: false)
    sendEvent(name: "finished", value: false, displayed: false)
    sendEvent(name: "unstarted", value: true, displayed: false)
    
    sendEvent(name: "switch", value: "off", displayed: false)
    
    if (stateColorUnstarted == "Blue") {
        sendEvent(name: "stateColor", value: "unstarted-blue", displayed: false)
    } else if (stateColorUnstarted == "Orange") {
        sendEvent(name: "stateColor", value: "unstarted-orange", displayed: false)
    } else if (stateColorUnstarted == "Gray") {
        sendEvent(name: "stateColor", value: "unstarted-gray", displayed: false)
    } else {
        sendEvent(name: "stateColor", value: "unstarted-gray", displayed: false)
    }
}