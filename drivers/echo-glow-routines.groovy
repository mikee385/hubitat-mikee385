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
 
String getVersionNum() { return "2.1.0" }
String getVersionLabel() { return "Echo Glow Routines, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Echo Glow Routines", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/echo-glow-routines.groovy"
	) {
        capability "Actuator"
        capability "ContactSensor"
        capability "Sensor"
        
        attribute "lastRoutine", "enum", ["bedtimeSoon", "bedtimeNow", "wakeUp", "glowsOff"]
        
        command "bedtimeSoon"
        command "bedtimeNow"
        command "wakeUp"
        command "glowsOff"
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
    def bedtimeSoon = bedtimeSoonDevice()
    def bedtimeNow = bedtimeNowDevice()
    def wakeUp = wakeUpDevice()
}

def bedtimeSoonDevice() {
    return childDevice("Alexa Bedtime Soon")
}

def bedtimeNowDevice() {
    return childDevice("Alexa Bedtime Now")
}

def wakeUpDevice() {
    return childDevice("Alexa Wake Up")
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

def bedtimeSoon() {
    bedtimeSoonDevice().sendEvent(name: "switch", value: "on")
    bedtimeNowDevice().sendEvent(name: "switch", value: "off")
    wakeUpDevice().sendEvent(name: "switch", value: "off")
    
    bedtimeSoonDevice().sendEvent(name: "contact", value: "open")
    runIn(1, bedtimeSoonClosed)
    
    sendEvent(name: "lastRoutine", value: "bedtimeSoon", isStateChange: true)
}

def bedtimeSoonClosed() {
    bedtimeSoonDevice().sendEvent(name: "contact", value: "closed")
}

def bedtimeNow() {
    bedtimeSoonDevice().sendEvent(name: "switch", value: "off")
    bedtimeNowDevice().sendEvent(name: "switch", value: "on")
    wakeUpDevice().sendEvent(name: "switch", value: "off")
    
    bedtimeNowDevice().sendEvent(name: "contact", value: "open")
    runIn(1, bedtimeNowClosed)
    
    sendEvent(name: "lastRoutine", value: "bedtimeNow", isStateChange: true)
}

def bedtimeNowClosed() {
    bedtimeNowDevice().sendEvent(name: "contact", value: "closed")
}

def wakeUp() {
    bedtimeSoonDevice().sendEvent(name: "switch", value: "off")
    bedtimeNowDevice().sendEvent(name: "switch", value: "off")
    wakeUpDevice().sendEvent(name: "switch", value: "on")
    
    wakeUpDevice().sendEvent(name: "contact", value: "open")
    runIn(1, wakeUpClosed)
    
    sendEvent(name: "lastRoutine", value: "wakeUp", isStateChange: true)
}

def wakeUpClosed() {
    wakeUpDevice().sendEvent(name: "contact", value: "closed")
}

def glowsOff() {
    bedtimeSoonDevice().sendEvent(name: "switch", value: "off")
    bedtimeNowDevice().sendEvent(name: "switch", value: "off")
    wakeUpDevice().sendEvent(name: "switch", value: "off")
    
    sendEvent(name: "contact", value: "open")
    runIn(1, glowsOffClosed)
    
    sendEvent(name: "lastRoutine", value: "glowsOff", isStateChange: true)
}

def glowsOffClosed() {
    sendEvent(name: "contact", value: "closed")
}

def componentRefresh(cd) {}

def componentOn(cd) {
    def child = getChildDevice(cd.deviceNetworkId)
    def name = child.getDataValue("Name")
    if (name == "Alexa Bedtime Soon") {
        bedtimeSoon()
    } else if (name == "Alexa Bedtime Now") {
        bedtimeNow()
    } else if (name == "Alexa Wake Up") {
        wakeUp()
    } else {
        log.error "Unknown child switch: $name"
    }
}

def componentOff(cd) {
    glowsOff()
}