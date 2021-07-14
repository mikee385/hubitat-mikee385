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
String getVersionNum() { return "1.9.0" }
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
                input "simpleDoor", "capability.contactSensor", title: "Door", multiple: false, required: true
            }

        } else if (zoneType == "Standard") {
            section {
                input "childZones", "device.ZoneDevice", title: "Child Zones", multiple: true, required: false, submitOnChange: true
                input "entryDoors", "capability.contactSensor", title: "Entry Doors", multiple: true, required: false, submitOnChange: true
                if (childZones || entryDoors) {
                    input "checkingSeconds", "number", title: "Time that zone will check for activity after all entry doors are closed (seconds)", required: true, defaultValue: 60
                }
            }
            section("ACTIVE - Zone will be occupied briefly when device state changes") {
                input "motionSensors", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false, submitOnChange: true
                input "interiorDoors", "capability.contactSensor", title: "Interior Doors", multiple: true, required: false
                input "buttons", "capability.pushableButton", title: "Buttons", multiple: true, required: false
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

//-----------------------------------------

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
    
    state.anyDeviceIsActive = false
    for (motionSensor in motionSensors) {
        if (motionSensor.currentValue("motion") == "active") {
            state.anyDeviceIsActive = true
            break
        }
    }
    
    if (zoneType == "Simple") {
        subscribe(simpleDoor, "contact", simpleDoorHandler)
    
    } else if (zoneType == "Standard") {
        entryDoorIds = entryDoors.collect{ it.id }
        engagedDoorIds_Open = engagedDoors_Open.collect{ it.id }
        engagedDoorIds_Closed = engagedDoors_Closed.collect{ it.id }
        engagedSwitchIds_On = engagedSwitches_On.collect{ it.id }
        engagedSwitchIds_Off = engagedSwitches_Off.collect{ it.id }
    
        for (childZone in childZones) {
            subscribe(childZone, "occupancy", childZoneHandler)
        }
        
        for (entryDoor in entryDoors) {
            if (!(entryDoor.id in engagedDoorIds_Open) && !(entryDoor.id in engagedDoorIds_Closed)) {
                subscribe(entryDoor, "contact", entryDoorHandler)
            }
        }
        
        for (motionSensor in motionSensors) {
            subscribe(motionSensor, "motion", motionSensorHandler)
        }
        
        for (interiorDoor in interiorDoors) {
            if (!(interiorDoor.id in entryDoorIds) && !(interiorDoor.id in engagedDoorIds_Open) && !(interiorDoor.id in engagedDoorIds_Closed)) {
                subscribe(interiorDoor, "contact", activeDeviceHandler)
            }
        }
        
        for (button in buttons) {
            subscribe(button, "pushed", activeDeviceHandler)
        }
        
        for (engagedDoor in engagedDoors_Open) {
            subscribe(engagedDoor, "contact.open", engagedDeviceHandler)
            
            if (engagedDoor.id in engagedDoorIds_Closed) {
                subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
            } else if (engagedDoor.id in entryDoorIds) {
                subscribe(engagedDoor, "contact.closed", entryDoorHandler)
            } else {
                subscribe(engagedDoor, "contact.closed", activeDeviceHandler)
            }
        }
        
        for (engagedDoor in engagedDoors_Closed) {
            if (!(engagedDoor.id in engagedDoorIds_Open)) { 
                subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
            
                if (engagedDoor.id in entryDoorIds) {
                    subscribe(engagedDoor, "contact.open", entryDoorHandler)
                } else {
                    subscribe(engagedDoor, "contact.open", activeDeviceHandler)
                }
            }
        }
        
        for (engagedSwitch in engagedSwitches_On) {
            subscribe(engagedSwitch, "switch.on", engagedDeviceHandler)
            
            if (engagedSwitch.id in engagedSwitchIds_Off) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
            } else {
                subscribe(engagedSwitch, "switch.off", activeDeviceHandler)
            }
        }
        
        for (engagedSwitch in engagedSwitches_Off) {
            if (!(engagedSwitch.id in engagedSwitchIds_On)) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
                subscribe(engagedSwitch, "switch.on", activeDeviceHandler)
            }
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

//-----------------------------------------

def simpleDoorHandler(evt) {
    def debugContext = "Zone ${app.label} - Simple Door - ${evt.device} is ${evt.value}"
    
    def zone = getZoneDevice()
    if (evt.value == "open") {
        zone.occupied()
        logDebug("$debugContext - engaged")
    } else {
        zone.vacant()
        logDebug("$debugContext - vacant")
    }
}

def entryDoorHandler(evt) {
    def debugContext = "Zone ${app.label} - Entry Door - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${anyChildZoneIsOccupied() ? 'child occupied' : 'child not occupied'} - ${anyMotionIsActive() ? 'motion active' : 'motion inactive'} - ${state.anyDeviceIsActive ? 'device active' : 'device inactive'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    if (zoneIsOpen()) {
        activeEvent(debugContext)
    } else {
        checkingEvent(debugContext)
    }
}

def activeDeviceHandler(evt) {
    def debugContext = "Zone ${app.label} - Active Device - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${anyChildZoneIsOccupied() ? 'child occupied' : 'child not occupied'} - ${anyMotionIsActive() ? 'motion active' : 'motion inactive'} - ${state.anyDeviceIsActive ? 'device active' : 'device inactive'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    if (zoneIsOpen()) {
        activeEvent(debugContext)
    } else {
        engagedEvent(debugContext)
    }
}

def motionSensorHandler(evt) {
    def debugContext = "Zone ${app.label} - Motion Sensor - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${anyChildZoneIsOccupied() ? 'child occupied' : 'child not occupied'} - ${anyMotionIsActive() ? 'motion active' : 'motion inactive'} - ${state.anyDeviceIsActive ? 'device active' : 'device inactive'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    if (evt.value == "active") {
        if (zoneIsOpen()) {
            motionActiveEvent(debugContext)
        } else {
            def zone = getZoneDevice()
            if (zone.currentValue("occupancy") == "vacant") {
                checkingEvent(debugContext)
            } else {
                engagedEvent(debugContext)
            }
        }
    } else {
        motionInactiveEvent(debugContext)
    }
}

def engagedDeviceHandler(evt) {
    def debugContext = "Zone ${app.label} - Engaged Device Active - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${anyChildZoneIsOccupied() ? 'child occupied' : 'child not occupied'} - ${anyMotionIsActive() ? 'motion active' : 'motion inactive'} - ${state.anyDeviceIsActive ? 'device active' : 'device inactive'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    engagedEvent(debugContext)
}

def childZoneHandler(evt) {
    def debugContext = "Zone ${app.label} - Child Zone - ${evt.device} is ${evt.value} - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${anyChildZoneIsOccupied() ? 'child occupied' : 'child not occupied'} - ${anyMotionIsActive() ? 'motion active' : 'motion inactive'} - ${state.anyDeviceIsActive ? 'device active' : 'device inactive'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"
    
    if (evt.value == "occupied") {
        engagedEvent(debugContext)
    } else {
        inactiveEvent(debugContext)
    }
}

//-----------------------------------------

def engagedEvent(debugContext) {
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    zone.occupied()
    
    logDebug("$debugContext - Engaged Event - engaged")
}

def activeEvent(debugContext) {
    unschedule("checkingTimeout")
    unschedule("activeTimeout")
    
    def zone = getZoneDevice()
    zone.occupied()
        
    logDebug("$debugContext - Active Event - active (${activeSeconds}s)")
    
    state.anyDeviceIsActive = true
    
    if (activeSeconds > 0) {
        runIn(activeSeconds, activeTimeout)
    } else {
        activeTimeout()
    }
}

def activeTimeout() {
    def debugContext = "Zone ${app.label} - Active Timeout - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${anyChildZoneIsOccupied() ? 'child occupied' : 'child not occupied'} - ${anyMotionIsActive() ? 'motion active' : 'motion inactive'} - ${state.anyDeviceIsActive ? 'device active' : 'device inactive'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"

    state.anyDeviceIsActive = false
    inactiveEvent(debugContext)
}

def inactiveEvent(debugContext) {
    if (!anyDeviceIsEngaged() && !anyChildZoneIsOccupied() && !anyMotionIsActive() && !state.anyDeviceIsActive) {
        if (zoneIsOpen()) {
            def zone = getZoneDevice()
            if (anyChildZoneIsChecking()) {
                unschedule("checkingTimeout")
                zone.checking()
                logDebug("$debugContext - Inactive Event - checking")
            } else {
                zone.vacant()
                logDebug("$debugContext - Inactive Event - vacant")
            }
        } else {
            logDebug("$debugContext - Inactive Event - ignored (closed)")
        }
    } else {
        logDebug("$debugContext - Inactive Event - ignored (active)")
    }
}

def motionActiveEvent(debugContext) {
    unschedule("checkingTimeout")
    unschedule("activeTimeout")
    
    def zone = getZoneDevice()
    zone.occupied()
        
    logDebug("$debugContext - Motion Active Event - active")
}

def motionInactiveEvent(debugContext) {
    state.anyDeviceIsActive = true
    
    logDebug("$debugContext - Motion Inactive Event - waiting (${activeSeconds}s)")
    
    if (activeSeconds > 0) {
        runIn(activeSeconds, activeTimeout)
    } else {
        activeTimeout()
    }
}

def checkingEvent(debugContext) {
    if (!anyDeviceIsEngaged() && !anyChildZoneIsOccupied()) {
        unschedule("checkingTimeout")
    
        def zone = getZoneDevice()
        zone.checking()
    
        logDebug("$debugContext - Checking Event - checking (${checkingSeconds}s)")
    
        if (checkingSeconds > 0) {
            runIn(checkingSeconds, checkingTimeout)
        } else {
            checkingTimeout()
        }
    } else {
        logDebug("$debugContext - Checking Event - ignored (engaged)")
    }
}

def checkingTimeout() {
    def debugContext = "Zone ${app.label} - Checking Timeout - [${anyDeviceIsEngaged() ? 'engaged' : 'not engaged'} - ${anyChildZoneIsOccupied() ? 'child occupied' : 'child not occupied'} - ${anyMotionIsActive() ? 'motion active' : 'motion inactive'} - ${state.anyDeviceIsActive ? 'device active' : 'device inactive'} - ${zoneIsOpen() ? 'open' : 'closed'} - ${getZoneDevice().currentValue('occupancy')}]"

    def zone = getZoneDevice()
    if (anyMotionIsActive()) {
        zone.occupied()
        logDebug("$debugContext - engaged")
    } else {
        zone.vacant()
        logDebug("$debugContext - vacant")
    }
}

//-----------------------------------------

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

def anyChildZoneIsChecking() {
    if (childZones) {
        for (childZone in childZones) {
            if (childZone.currentValue("occupancy") == "checking") {
                return true
            }
        }
        return false
    }
    
    return false
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