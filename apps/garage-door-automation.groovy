/**
 *  Garage Dooor Automation
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
 
String getVersionNum() { return "3.2.0" }
String getVersionLabel() { return "Garage Door Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-check-library

definition(
    name: "Garage Door Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Combines several sensors and controllers into a single garage door device.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/garage-door-automation.groovy"
)

preferences {
    page(name: "settings", title: "Garage Door Automation", install: true, uninstall: true) {
        section {
            input "controllers", "capability.garageDoorControl", title: "Controllers", multiple: true, required: false
            input "sensors", "capability.contactSensor", title: "Sensors", multiple: true, required: false
            input "alertInconsistent", "bool", title: "Alert when Sensors are Inconsistent?", required: true, defaultValue: true
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input "deviceChecker", "device.DeviceChecker", title: "Device Checker", multiple: false, required: true
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
    
    // Door Contact
    for (controller in controllers) {
        subscribe(controller, "door", handler_DoorContact)
    }
    for (sensor in sensors) {
        subscribe(sensor, "contact", handler_DoorContact)
    }
    
    // Inconsistency Check
    if (alertInconsistent) {
        for (controller in controllers) {
            subscribe(controller, "door", handler_InconsistencyCheck)
        }
        for (sensor in sensors) {
            subscribe(sensor, "contact", handler_InconsistencyCheck)
        }
    }
        
    // Device Checks
    initializeDeviceChecks()
}

def childDevice() {
    def childID = "garageVirtualDoor:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("hubitat", "Virtual Contact Sensor", childID, 1234, [label: "Garage Virtual Door", isComponent: false])
    }
    return child
}

def handler_DoorContact(evt) {
    logDebug("handler_DoorContact: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        childDevice().open()
    } else if (evt.value == "closed") {
        childDevice().close()
    } 
}

def handler_InconsistencyCheck(evt) {
    logDebug("handler_InconsistencyCheck: ${evt.device} changed to ${evt.value}")
    
    runIn(5*60, inconsistencyCheck)
}

def inconsistencyCheck() {
    def virtualValue = childDevice().currentValue("contact")
    
    for (controller in controllers) {
        def doorValue = controller.currentValue("door")
        if (doorValue != virtualValue) {
            def message = "WARNING: $controller failed to change to $virtualValue!"
            log.warn(message)
            personToNotify.deviceNotification(message)
        }
    }
    
    for (sensor in sensors) {
        def doorValue = sensor.currentValue("contact")
        if (doorValue != virtualValue) {
            def message = "WARNING: $sensor failed to change to $virtualValue!"
            log.warn(message)
            personToNotify.deviceNotification(message)
        }
    }
}