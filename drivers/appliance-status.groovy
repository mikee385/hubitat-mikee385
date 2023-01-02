/**
 *  Appliance Status Device Handler
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
 
String getVersionNum() { return "5.0.1" }
String getVersionLabel() { return "Appliance Status, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Appliance Status", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/appliance-status.groovy"
	) {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"

        attribute "status", "enum", ["idle", "running", "finished"]
        
        attribute "startTime", "string"
        attribute "finishTime", "string"
        attribute "resetTime", "string"

        command "start"
        command "finish"
        command "reset"
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
    def startButton = childDevice("Start")
    def finishButton = childDevice("Finish")
    def resetButton = childDevice("Reset")

    if (!device.currentValue("status")) {
        reset()
    }
}

def childDevice(name) {
    def childID = "appliance:${device.getId()}:$name"
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
    if (name == "Start") {
        start()
    } else if (name == "Finish") {
        finish()
    } else if (name == "Reset") {
        reset()
    } else {
        log.error "Unknown command name: $name"
    }
    
    runIn(1, componentOff, [data: [deviceNetworkId: cd.deviceNetworkId]])
}

def componentOff(cd) {
    def child = getChildDevice(cd.deviceNetworkId)
    child.sendEvent(name: "switch", value: "off")
}

def on() {
    start()
}

def off() {
    finish()
}

def start() {
    sendEvent(name: "status", value: "running", descriptionText: "$device.displayName changed to running")    
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "startTime", value: new Date())
}

def finish() {
    sendEvent(name: "status", value: "finished", descriptionText: "$device.displayName changed to finished")    
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "finishTime", value: new Date())
}

def reset() {
    sendEvent(name: "status", value: "idle", descriptionText: "$device.displayName changed to idle")    
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "resetTime", value: new Date())
}