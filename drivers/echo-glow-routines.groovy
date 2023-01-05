/**
 *  Echo Glow Routines Device Handler
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
 
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "Echo Glow Routines, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Echo Glow Routines", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/echo-glow-routines.groovy"
	) {}
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
    def bedtimeSoon = childDevice("Alexa Bedtime Soon")
    def bedtimeNow = childDevice("Alexa Bedtime Now")
    def wakeUp = childDevice("Alexa Wake Up")
    def glowsOff = childDevice("Alexa Glows Off")
}

def childDevice(name) {
    def childID = "echoGlow:${device.getId()}:$name"
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("hubitat", "Generic Component Contact/Switch", childID, [label: name, isComponent: true])
        child.updateSetting("logEnable", [value: "false", type: "bool"])
        child.updateSetting("txtEnable", [value: "false", type: "bool"])
        child.updateDataValue("Name", name)
        child.sendEvent(name: "switch", value: "off")
        child.sendEvent(name: "contact", value: "closed")
    }
    return child
}

def componentRefresh(cd) {}

def componentOn(cd) {
    def child = getChildDevice(cd.deviceNetworkId)
    child.sendEvent(name: "switch", value: "on")
    child.sendEvent(name: "contact", value: "open")
    
    runIn(1, componentOff, [data: [deviceNetworkId: cd.deviceNetworkId]])
}

def componentOff(cd) {
    def child = getChildDevice(cd.deviceNetworkId)
    child.sendEvent(name: "switch", value: "off")
    child.sendEvent(name: "contact", value: "closed")
}