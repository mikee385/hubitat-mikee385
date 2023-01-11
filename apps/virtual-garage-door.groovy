/**
 *  Virtual Garage Door
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
String getVersionLabel() { return "Virtual Garage Door, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library

definition(
    name: "Virtual Garage Door",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Simulates a garage door by combining a contact sensor, acceleration sensor, and door control.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/virtual-garage-door.groovy"
)

preferences {
    page(name: "settings", title: "Virtual Garage Door", install: true, uninstall: true) {
        section {
            input "contactSensor", "capability.contactSensor", title: "Contact Sensor", multiple: false, required: true
            input "accelerationSensor", "capability.accelerationSensor", title: "Acceleration Sensor", multiple: false, required: false
            input "doorControl", "capability.garageDoorControl", title: "Door Control", multiple: false, required: false
        }
        section {
            input "deviceMonitor", "device.DeviceMonitor", title: "Device Monitor", multiple: false, required: true
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
        }
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
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    // Child Device
    def child = childDevice()
    
    // Sensors
    subscribe(contactSensor, "contact", contactSensorHandler)
    if (accelerationSensor) {
        subscribe(accelerationSensor, "acceleration", accelerationSensorHandler)
    }
    
    // Device Checks
    initializeDeviceChecks()
}

def childDevice() {
    def childID = "virtualGarageDoor:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("mikee385", "Virtual Garage Door", childID, 1234, [label: app.label, isComponent: false])
    }
    return child
}

def contactSensorHandler(evt) {
    logDebug("contactSensorHandler: ${evt.device} changed to ${evt.value}")
    
    def child = childDevice()
    
    if (accelerationSensor) {
        if (accelerationSensor.currentValue("acceleration") == "inactive") {
            child.sendEvent(name: "door", "value": evt.value)
            child.sendEvent(name: "contact", "value": evt.value)
        }
    } else {
        child.sendEvent(name: "door", "value": evt.value)
        child.sendEvent(name: "contact", "value": evt.value)
    }
}

def accelerationSensorHandler(evt) {
    logDebug("accelerationSensorHandler: ${evt.device} changed to ${evt.value}")
    
    def child = childDevice()
    
    if (evt.value == "active") {
        if (child.currentValue("contact") == "open") {
            child.sendEvent(name: "door", "value": "closing")
        } else {
            child.sendEvent(name: "door", "value": "opening")
        }
    } else {
        child.sendEvent(name: "door", "value": contactSensor.currentValue("contact"))
        child.sendEvent(name: "contact", "value": contactSensor.currentValue("contact"))
    }
}

def open() {
    logDebug("open: Sending open command to child device")
        
    if (doorControl) {
        doorControl.open()
    }
}

def close() {
    logDebug("close: Sending close command to child device")
        
    if (doorControl) {
        doorControl.close()
    }
}