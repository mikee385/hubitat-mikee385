/**
 *  Zone App
 *
 *  Copyright 2021 Michael Pierce
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
 
String getName() { return "Zone App" }
String getVersionNum() { return "1.6.1" }
String getVersionLabel() { return "${getName()}, version ${getVersionNum()}" }

definition(
    name: "${getName()}",
    parent: "mikee385:Zone Builder",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Creates a Zone Device and automates the occupancy based on the devices and zones contained within it.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/zones/zone-app.groovy")

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "${getVersionLabel()}", install: true, uninstall: true) {
        section {
            label title: "Zone Name", required: true
            input "zoneType", "enum", title: "Zone Type", options: ["Simple", "Standard"], multiple: false, required: true, submitOnChange: true
        }
        
        if (zoneType == "Simple") {
            section {
                input "childZones", "device.ZoneDevice", title: "Child Zones", multiple: true, required: false
                input "simpleDoor", "capability.contactSensor", title: "Door", multiple: false, required: true
            }

        } else if (zoneType == "Standard") {
            section {
                input "childZones", "device.ZoneDevice", title: "Child Zones", multiple: true, required: false
                input "entryDoors", "capability.contactSensor", title: "Entry Doors", multiple: true, required: false, submitOnChange: true
                if (entryDoors) {
                    input "checkingSeconds", "number", title: "Time that zone will check for activity after all entry doors are closed (seconds)", required: true, defaultValue: 60
                }
            }
            section("ACTIVE - Zone will be occupied briefly when device state changes") {
                input "motionSensors", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false, submitOnChange: true
                input "interiorDoors", "capability.contactSensor", title: "Interior Doors", multiple: true, required: false
                input "activeSeconds", "number", title: "Time that zone will remain active after any device state changes (seconds)", required: true, defaultValue: 60
            }
            section("ENGAGED - Zone will stay occupied while:") {
                input "engagedDoors_Open", "capability.contactSensor", title: "Door is Open", multiple: true, required: false
                input "engagedDoors_Closed", "capability.contactSensor", title: "Door is Closed", multiple: true, required: false
                input "engagedSwitches_On", "capability.switch", title: "Switch is On", multiple: true, required: false
                input "engagedSwitches_Off", "capability.switch", title: "Switch is Off", multiple: true, required: false
            }
        }
        
        section {
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    parent.addZoneDevice(app.getId(), app.label)

    for (childZone in childZones) {
        subscribe(childZone, "occupancy", childZoneHandler)
    }
    
    if (zoneType == "Simple") {
        subscribe(simpleDoor, "contact", simpleDoorHandler)
    
    } else if (zoneType == "Standard") {
        for (entryDoor in entryDoors) {
            subscribe(entryDoor, "contact", entryDoorHandler)
        }
        
        for (motionSensor in motionSensors) {
            subscribe(motionSensor, "motion.active", motionActiveHandler)
        }
        for (interiorDoor in interiorDoors) {
            subscribe(interiorDoor, "contact", activityHandler)
        }
        
        for (engagedDoor in engagedDoors_Open) {
            subscribe(engagedDoor, "contact.open", engagedActiveHandler)
            subscribe(engagedDoor, "contact.closed", engagedInactiveHandler)
        }
        for (engagedDoor in engagedDoors_Closed) {
            subscribe(engagedDoor, "contact.closed", engagedActiveHandler)
            subscribe(engagedDoor, "contact.open", engagedInactiveHandler)
        }
        for (engagedSwitch in engagedSwitches_On) {
            subscribe(engagedSwitch, "switch.on", engagedActiveHandler)
            subscribe(engagedSwitch, "switch.off", engagedInactiveHandler)
        }
        for (engagedSwitch in engagedSwitches_Off) {
            subscribe(engagedSwitch, "switch.off", engagedActiveHandler)
            subscribe(engagedSwitch, "switch.on", engagedInactiveHandler)
        }
    
    } else {
        log.error "Unknown zone type: $zoneType"
    }
}

def uninstalled() {
    parent.deleteZoneDevice(app.getId())
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def getZoneDevice() {
    return parent.getZoneDevice(app.getId())
}

def setZoneEngaged() {
    unsubscribe("motionInactiveHandler")
    unschedule("activeTimeout")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    zone.occupied()
}

def setZoneActive() {
    unsubscribe("motionInactiveHandler")
    unschedule("activeTimeout")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    zone.occupied()
    
    if (activeSeconds > 0) {
        runIn(activeSeconds, activeTimeout)
    } else {
        activeTimeout()
    }
}

def setZoneChecking() {
    unsubscribe("motionInactiveHandler")
    unschedule("activeTimeout")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    zone.checking()
    
    if (checkingSeconds > 0) {
        runIn(checkingSeconds, checkingTimeout)
    } else {
        checkingTimeout()
    }
}

def setZoneVacant() {
    unsubscribe("motionInactiveHandler")
    unschedule("activeTimeout")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    zone.vacant()
}

def childZoneHandler(evt) {
    def debugContext = "Zone ${app.label} - Child Zone - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    if (evt.value == "occupied") {
        logDebug("$debugContext - engaged")
        setZoneEngaged()
    } else if (evt.value == "checking") {
        if (!anyDeviceIsEngaged() && !anyChildZoneIsOccupied()) {
            if (zoneIsOpen()) {
                logDebug("$debugContext - active (${activeSeconds}s)")
                setZoneActive()
            } else if (getZoneDevice().currentValue("occupancy") != "occupied") {
                logDebug("$debugContext - checking (${checkingSeconds}s)")
                setZoneChecking()
            } else {
                logDebug("$debugContext - ignored (occupied)")
            }
        } else {
            logDebug("$debugContext - ignored (engaged)")
        }
    } else {
        logDebug("$debugContext - ignored (vacant)")
    }
}

def simpleDoorHandler(evt) {
    def debugContext = "Zone ${app.label} - Simple Door - ${evt.device} is ${evt.value}"
    
    if (evt.value == "open") {
        logDebug("$debugContext - engaged")
        setZoneEngaged()
    } else {
        logDebug("$debugContext - vacant")
        setZoneVacant()
    }
}

def entryDoorHandler(evt) {
    def debugContext = "Zone ${app.label} - Entry Door - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    if (!anyDeviceIsEngaged() && !anyChildZoneIsOccupied()) {
        if (zoneIsOpen()) {
            logDebug("$debugContext - active (${activeSeconds}s)")
            setZoneActive()
        } else {
            logDebug("$debugContext - checking (${checkingSeconds}s)")
            setZoneChecking()
        }
    } else {
        logDebug("$debugContext - ignored (engaged)")
    }
}

def activityHandler(evt) {
    def debugContext = "Zone ${app.label} - Active Device - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    if (!anyDeviceIsEngaged() && !anyChildZoneIsOccupied()) {
        if (zoneIsOpen()) {
            logDebug("$debugContext - active (${activeSeconds}s)")
            setZoneActive()
        } else {
            logDebug("$debugContext - engaged")
            setZoneEngaged()
        }
    } else {
        logDebug("$debugContext - ignored (engaged)")
    }
}

def motionActiveHandler(evt) {
    def debugContext = "Zone ${app.label} - Motion Sensor - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    if (!anyDeviceIsEngaged() && !anyChildZoneIsOccupied()) {
        if (zoneIsOpen()) {
            logDebug("$debugContext - engaged")
            setZoneEngaged()
            subscribe(evt.device, "motion.inactive", motionInactiveHandler)
        } else {
            def zone = getZoneDevice()
            if (zone.currentValue("occupancy") == "vacant") {
                logDebug("$debugContext - checking (${checkingSeconds}s)")
                setZoneChecking()
            } else if (zone.currentValue("occupancy") == "checking") {
                logDebug("$debugContext - engaged")
                setZoneEngaged()
            } else {
                logDebug("$debugContext - ignored (occupied)")
            }
        }
    } else {
        logDebug("$debugContext - ignored (engaged)")
    }
}

def motionInactiveHandler(evt) {
    def debugContext = "Zone ${app.label} - Motion Sensor - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    logDebug("$debugContext - motion timeout scheduled (${activeSeconds}s)")
    if (activeSeconds > 0) {
        runIn(activeSeconds, activeTimeout)
    } else {
        activeTimeout()
    }
}

def engagedActiveHandler(evt) {
    def debugContext = "Zone ${app.label} - Engaged Device Active - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    logDebug("$debugContext - engaged")
    setZoneEngaged()
}

def engagedInactiveHandler(evt) {
    def debugContext = "Zone ${app.label} - Engaged Device Inactive - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    if (!anyDeviceIsEngaged() && !anyChildZoneIsOccupied()) {
        if (zoneIsOpen()) {
            logDebug("$debugContext - active (${activeSeconds}s)")
            setZoneActive()
        } else {
            logDebug("$debugContext - ignored (closed)")
        }
    } else {
        logDebug("$debugContext - ignored (engaged)")
    }
}

def activeTimeout() {
    def debugContext = "Zone ${app.label} - Active Timeout - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    logDebug("$debugContext - vacant")
    setZoneVacant()
}

def checkingTimeout() {
    def debugContext = "Zone ${app.label} - Checking Timeout - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    if (anyMotionIsActive()) {
        logDebug("$debugContext - engaged")
        setZoneEngaged()
    } else {
        logDebug("$debugContext - vacant")
        setZoneVacant()
    }
}

def anyMotionIsActive() {
    if (motionSensors) {
        for (motionSensor in motionSensors) {
            if (motionSensor.currentValue("motion") == "active") {
                return true
            }
        }
        return false
    }
    
    return false
}

def anyDeviceIsEngaged() {
    if (engagedDoors_Open) {
        for (engagedDoor in engagedDoors_Open) {
            if (engagedDoor.currentValue("contact") == "open") {
                return true
            }
        }
    }
    
    if (engagedDoors_Closed) {
        for (engagedDoor in engagedDoors_Closed) {
            if (engagedDoor.currentValue("contact") == "closed") {
                return true
            }
        }
    }
    
    if (engagedSwitches_On) {
        for (engagedSwitch in engagedSwitches_On) {
            if (engagedSwitch.currentValue("switch") == "on") {
                return true
            }
        }
    }
    
    if (engagedSwitches_Off) {
        for (engagedSwitch in engagedSwitches_Off) {
            if (engagedSwitch.currentValue("switch") == "off") {
                return true
            }
        }
    }
    
    return false
}

def anyChildZoneIsOccupied() {
    if (childZones) {
        for (childZone in childZones) {
            if (childZone.currentValue("occupancy") == "occupied") {
                return true
            }
        }
        return false
    }
    
    return false
}

def zoneIsOpen() {
    if (entryDoors) {
        for (entryDoor in entryDoors) {
            if (entryDoor.currentValue("contact") == "open") {
                return true
            }
        }
        return false
    }
    
    return true
}