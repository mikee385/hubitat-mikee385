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
 
String getVersionNum() { return "6.5.0" }
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

        attribute "location", "string"
        attribute "message", "string"
        
        command "awake"
        command "asleep"
        command "arrived"
        command "departed"
        
        command "setLocation", ["string"]
        
        command "batteryNotification", ["string"]
        command "inactiveNotification", ["string"]
    }
    
    preferences {
        input "alertLowBattery", "bool", title: "Alert for Low Battery Report (8PM)?", required: true, defaultValue: false
        input "alertInactive", "bool", title: "Alert for Inactive Device Report (8PM)?", required: true, defaultValue: false
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
    if (!device.currentValue("location")) {
        setLocation("")
    }
    
    if (!atomicState.batteryMessage) {
        batteryReset()
    }
    if (!atomicState.inactiveMessage) {
        inactiveReset()
    }
    
    schedule("0 0 0 * * ? *", batteryReset)
    schedule("0 0 0 * * ? *", inactiveReset)
    
    def currentTime = new Date()
    def alertTime = timeToday("20:00")
    
    if (alertLowBattery) {
      schedule("$currentTime.seconds $alertTime.minutes $alertTime.hours * * ? *", batteryAlert)
    }
    if (alertInactive) {
      schedule("$currentTime.seconds $alertTime.minutes $alertTime.hours * * ? *", inactiveAlert)
    } 
}

def childDevice(name) {
    def childID = "person:${device.getId()}:$name"
    def child = getChildDevice(childID)
    if (!child) {
        def childName = "${device.label ?: device.name}"
        child = addChildDevice("mikee385", "Child Button", childID, [label: "$childName $name", isComponent: true])
        child.setCommand(name)
    }
    return child
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

def runCommand(name) {
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
}

def setLocation(locationName) {
    if (locationName) {
        sendEvent(name: "location", value: "${locationName}")
    } else {
        sendEvent(name: "location", value: "none")
    }
}

def deviceNotification(message) {
  	sendEvent(name: "message", value: "${message}", isStateChange: true)
}

def batteryNotification(message) {
    atomicState.batteryMessage += "\n" + message
}

def batteryReset() {
    atomicState.batteryMessage = ""
}

def batteryAlert() {
    if (atomicState.batteryMessage) {
        deviceNotification("Low Battery:${atomicState.batteryMessage.split('\n').sort().join('\n')}")
    }
}

def inactiveNotification(message) {
  	atomicState.inactiveMessage += "\n" + message
}

def inactiveReset() {
    atomicState.inactiveMessage = ""
}

def inactiveAlert() {
    if (atomicState.inactiveMessage) {
        deviceNotification("Inactive Devices:${atomicState.inactiveMessage.split('\n').sort().join('\n')}")
    }
}