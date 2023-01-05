/**
 *  Person Status Device Handler
 *
 *  Copyright 2023 Michael Pierce
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
 
String getVersionNum() { return "10.1.0" }
String getVersionLabel() { return "Person Status, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Person Status", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/person-status.groovy"
	) {
        capability "Actuator"
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
    def presenceSwitch = childDevice("Presence")
    def sleepingSwitch = childDevice("Sleeping")
    
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
    def name = child.getDataValue("Name")
    if (name == "Presence") {
        arrived()
    } else if (name == "Sleeping") {
        asleep()
    } else {
        log.error "Unknown child switch: $name"
    }
}

def componentOff(cd) {
    def child = getChildDevice(cd.deviceNetworkId)
    def name = child.getDataValue("Name")
    if (name == "Presence") {
        departed()
    } else if (name == "Sleeping") {
        awake()
    } else {
        log.error "Unknown child switch: $name"
    }
}

def awake() {
    sendEvent(name: "sleeping", value: "not sleeping")
    childDevice("Sleeping").sendEvent(name: "switch", value: "off")
}

def asleep() {
    sendEvent(name: "sleeping", value: "sleeping")
    childDevice("Sleeping").sendEvent(name: "switch", value: "on")
}

def arrived() {
    sendEvent(name: "presence", value: "present")
    childDevice("Presence").sendEvent(name: "switch", value: "on")
}

def departed() {
    sendEvent(name: "presence", value: "not present")
    childDevice("Presence").sendEvent(name: "switch", value: "off")
}

def deviceNotification(message) {
  	sendEvent(name: "message", value: "${message}", isStateChange: true)
}