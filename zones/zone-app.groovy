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
String getVersionNum() { return "2.0.5" }
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
    
    def zone = getZoneDevice()
    if (anyMotionSensorIsActive()) {
        zone.activityActive()
    } else {
        zone.activityInactive()
    }
    
    def debugContext = """Zone ${app.label} - Initial
"""
    
    updateOccupancy(debugContext)
    
    if (zoneType == "Simple") {
        subscribe(simpleDoor, "contact", simpleDoorHandler)
    
    } else if (zoneType == "Standard") {
        entryDoorIds = entryDoors.collect{ it.id }
    
        for (childZone in childZones) {
            subscribe(childZone, "occupancy", childZoneHandler)
        }
        
        for (entryDoor in entryDoors) {
            subscribe(entryDoor, "contact", entryDoorHandler)
        }
        
        for (motionSensor in motionSensors) {
            subscribe(motionSensor, "motion", motionSensorHandler)
        }
        
        for (interiorDoor in interiorDoors) {
            if (!(interiorDoor.id in entryDoorIds)) {
                subscribe(interiorDoor, "contact", activeDeviceHandler)
            }
        }
        
        for (button in buttons) {
            subscribe(button, "pushed", activeDeviceHandler)
        }
        
        for (engagedDoor in engagedDoors_Open) {
            if (!(engagedDoor.id in entryDoorIds)) {
                subscribe(engagedDoor, "contact", activeDeviceHandler)
            }
        }
        
        for (engagedDoor in engagedDoors_Closed) {
            if (!(engagedDoor.id in entryDoorIds)) { 
                subscribe(engagedDoor, "contact", activeDeviceHandler)
            }
        }
        
        for (engagedSwitch in engagedSwitches_On) {
            subscribe(engagedSwitch, "switch", activeDeviceHandler)
        }
        
        for (engagedSwitch in engagedSwitches_Off) {
            subscribe(engagedSwitch, "switch", activeDeviceHandler)
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
    def debugContext = """Zone ${app.label} - Simple Door
${evt.device} is ${evt.value}
"""
    
    def zone = getZoneDevice()
    if (evt.value == "open") {
        zone.occupancyEngaged()
        logDebug("$debugContext => engaged (1)")
    } else {
        zone.occupancyVacant()
        logDebug("$debugContext => vacant (2)") 
    }
}

def entryDoorHandler(evt) {
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""
    
    if (zoneIsOpen()) {
        deviceActiveEvent(debugContext)
    } else {
        zoneClosedEvent(debugContext)
    }
}

def motionSensorHandler(evt) {
    def debugContext = """Zone ${app.label} - Motion Sensor
${evt.device} is ${evt.value}"""
    
    if (evt.value == "active") {
        motionActiveEvent(debugContext)
    } else {
        motionInactiveEvent(debugContext)
    }
}

def activeDeviceHandler(evt) {
    def debugContext = """Zone ${app.label} - Active Device
${evt.device} is ${evt.value}"""
    
    deviceActiveEvent(debugContext)
}

def childZoneHandler(evt) {
    def debugContext = """Zone ${app.label} - Child Zone
${evt.device} is ${evt.value}"""
    
    updateOccupancy(debugContext)
}

//-----------------------------------------

def deviceActiveEvent(debugContext) {
    unschedule("activeTimeout")
    unschedule("unknownTimeout")
    
    def zone = getZoneDevice()
    zone.activityActive()
    
    updateOccupancy("""$debugContext
Device Active Event (${activeSeconds}s)""")
    
    if (activeSeconds > 0) {
        runIn(activeSeconds, activeTimeout)
    } else {
        activeTimeout()
    }
}

def activeTimeout() {
    def debugContext = """Zone ${app.label} - Active Timeout (${activeSeconds}s)"""
    
    def zone = getZoneDevice()
    zone.activityInactive()
    
    updateOccupancy(debugContext)
}

def motionActiveEvent(debugContext) {
    unschedule("activeTimeout")
    unschedule("unknownTimeout")
    
    def zone = getZoneDevice()
    if (zoneIsOpen()) {
        zone.activityActive()
        
        updateOccupancy("""$debugContext
Motion Active Event""")
    } else {
        if (zone.currentValue("occupancy") == "vacant") {
            zone.activityUnknown()
        
            updateOccupancy("""$debugContext
Motion Active Event (${checkingSeconds}s)""")
        
            if (checkingSeconds > 0) {
                runIn(checkingSeconds, checkingTimeout)
            } else {
                checkingTimeout()
            }
        } else {
            zone.activityActive()
        
            updateOccupancy("""$debugContext
Motion Active Event""")
        }
    }
}

def motionInactiveEvent(debugContext) {
    def zone = getZoneDevice()
    if (zone.currentValue("activity") == "active") {
        unschedule("activeTimeout")
        
        logDebug("""$debugContext
Motion Inactive Event
 => waiting (${activeSeconds}s)""")
        
        if (activeSeconds > 0) {
            runIn(activeSeconds, activeTimeout)
        } else {
            activeTimeout()
        }
    } else {
        logDebug("""$debugContext
Motion Inactive Event
 => ignored (not active)""")
    }
}

def zoneClosedEvent(debugContext) {
    unschedule("activeTimeout")
    unschedule("unknownTimeout")
    
    def zone = getZoneDevice()
    zone.activityUnknown()
        
    updateOccupancy("""$debugContext
Zone Closed Event (${checkingSeconds}s)""")
        
    if (checkingSeconds > 0) {
        runIn(checkingSeconds, checkingTimeout)
    } else {
        checkingTimeout()
    }
}

def checkingTimeout() {
    def debugContext = """Zone ${app.label} - Checking Timeout (${checkingSeconds}s)"""
    
    def zone = getZoneDevice()
    if (anyMotionSensorIsActive()) {
        zone.activityActive()
    } else {
        zone.activityInactive()
    }
    
    updateOccupancy(debugContext)
}

//-----------------------------------------

def updateOccupancy(debugContext) {
    def zone = getZoneDevice()
    
    def zoneOccupancy = zone.currentValue("occupancy")
    def zoneActivity = zone.currentValue("activity")
    def childOccupancy = getChildOccupancy()
    
     debugContext = """$debugContext
engaged = ${anyDeviceIsEngaged()}
children = $childOccupancy
activity = $zoneActivity
open = ${zoneIsOpen()}
occupancy = $zoneOccupancy
"""

    if (anyDeviceIsEngaged()) {
        zone.occupancyEngaged()
        logDebug("$debugContext => engaged (3)")
    } else if (childOccupancy == "engaged") {
        zone.occupancyEngaged()
        logDebug("$debugContext => engaged (4)")
    } else if (zoneActivity == "active") {
        if (zoneIsOpen()) {
            zone.occupancyActive()
            logDebug("$debugContext => active (5)")
        } else {
            zone.occupancyEngaged()
            logDebug("$debugContext => engaged (6)")
        }
    } else if (childOccupancy == "active") {
        if (zoneIsOpen()) {
            zone.occupancyActive()
            logDebug("$debugContext => active (7)")
        } else {
            zone.occupancyEngaged()
            logDebug("$debugContext => engaged (8)")
        }
    } else if (zoneActivity == "unknown") {
        zone.occupancyChecking()
        logDebug("$debugContext => checking (9)")
    } else if (zoneIsOpen() || zoneOccupancy == "checking" || zoneOccupancy == "vacant") {
        if (childOccupancy == "checking") {
            zone.occupancyChecking()
            logDebug("$debugContext => checking (10)")
        } else {
            zone.occupancyVacant()
            logDebug("$debugContext => vacant (11)")
        }
    } else {
        logDebug("$debugContext => ignored (12)")
    }
}

//-----------------------------------------

def getChildOccupancy() {
    def isChildEngaged = false
    def isChildActive = false
    def isChildChecking = false
    def isChildVacant = false
    
    for (childZone in childZones) {
        def occupancy = childZone.currentValue("occupancy")
        if (occupancy == "engaged") {
            isChildEngaged = true
        } else if (occupancy == "active") {
            isChildActive = true
        } else if (occupancy == "checking") {
            isChildChecking = true
        } else if (occupancy == "vacant") {
            isChildVacant = true
        } else {
            log.error "Unknown value for occupancy of zone ${childZone}: ${occupancy}"
        }
    }
    
    if (isChildEngaged) {
        return "engaged"
    } else if (isChildActive) {
        return "active"
    } else if (isChildChecking) {
        return "checking"
    } else {
        return "vacant"
    }
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

def anyMotionSensorIsActive() {
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