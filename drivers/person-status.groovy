/**
 *  Person Status Device Handler
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
 
String getVersionNum() { return "9.0.0" }
String getVersionLabel() { return "Person Status, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Person Status", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/person-status.groovy"
	) {
        capability "Actuator"
        capability "Initialize"
        capability "Notification"
        capability "Presence Sensor"
        capability "Sensor"
        capability "Sleep Sensor"

        attribute "message", "string"
        
        command "awake"
        command "asleep"
        command "arrived"
        command "departed"
    }
}

def installed() {
    initialize()
}

def uninstalled() {
    for (device in getChildDevices()) {
        deleteChildDevice(device.deviceNetworkId)
    }
}

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    def awakeButton = childDevice("Awake")
    def asleepButton = childDevice("Asleep")
    def arrivedButton = childDevice("Arrived")
    def departedButton = childDevice("Departed")
    
    if (!device.currentValue("presence")) {
        arrived()
    }
    if (!device.currentValue("sleeping")) {
        awake()
    }
}

def childDevice(name) {
    def childID = "person:${device.getId()}:$name"
    def child = getChildDevice(childID)
    if (!child) {
        def childName = "${device.label ?: device.name}"
        child = addChildDevice("hubitat", "Generic Component Switch", childID, [label: "$childName $name", isComponent: true])
        child.updateSetting("logEnable", [value: "false", type: "bool"])
        child.updateSetting("txtEnable", [value: "false", type: "bool"])
        child.updateDataValue("Name", name)
        child.sendEvent(name: "switch", value: "off")
    }
    return child
}

def componentRefresh(cd) {}

def componentOn(cd) {
    def child = getChildDevice(cd.deviceNetworkId)
    child.sendEvent(name: "switch", value: "on")
    
    def name = child.getDataValue("Name")
    if (name == "Awake") {
        awake()
    } else if (name == "Asleep") {
        asleep()
    } else if (name == "Arrived") {
        arrived()
    } else if (name == "Departed") {
        departed()
    } else {
        log.error "Unknown command name: $name"
    }
    
    runIn(1, componentOff, [data: [deviceNetworkId: cd.deviceNetworkId]])
}

def componentOff(cd) {
    def child = getChildDevice(cd.deviceNetworkId)
    child.sendEvent(name: "switch", value: "off")
}

def awake() {
    sendEvent(name: "sleeping", value: "not sleeping")
}

def asleep() {
    sendEvent(name: "sleeping", value: "sleeping")
}

def arrived() {
    sendEvent(name: "presence", value: "present")
}

def departed() {
    sendEvent(name: "presence", value: "not present")
}

def deviceNotification(message) {
  	sendEvent(name: "message", value: "${message}", isStateChange: true)
}