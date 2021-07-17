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
String getVersionNum() { return "3.2.1" }
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
    if (anyDeviceIsEngaged()) {
        zone.engaged()
    } else if (anyMotionSensorIsActive()) {
        if (zoneIsOpen()) {
            zone.active()
        } else {
            zone.engaged()
        }
    } else if (zoneIsOpen()) {
        zone.vacant()
    }
    
    logDebug("""Zone ${app.label} - Initial
engaged = ${anyDeviceIsEngaged()}
motion = ${anyMotionSensorIsActive()}
open = ${zoneIsOpen()}
occupancy = ${zone.currentValue('occupancy')}
""")
    
    if (zoneType == "Simple") {
        subscribe(simpleDoor, "contact", simpleDoorHandler)
    
    } else if (zoneType == "Standard") {
        def allMotionSensors = getAllDevices("motionSensors")
        def allInteriorDoors = getAllDevices("entryDoors") + getAllDevices("interiorDoors")
        def allButtons = getAllDevices("buttons")
        def allEngagedDoors_Open = getAllDevices("engagedDoors_Open")
        def allEngagedDoors_Closed = getAllDevices("engagedDoors_Closed")
        def allEngagedSwitches_On = getAllDevices("engagedSwitches_On")
        def allEngagedSwitches_Off = getAllDevices("engagedSwitches_Off")
    
        entryDoorIds = entryDoors.collect{ it.id }
        engagedDoorIds_Open = allEngagedDoors_Open.collect{ it.id }
        engagedDoorIds_Closed = allEngagedDoors_Closed.collect{ it.id }
        engagedSwitchIds_On = allEngagedSwitches_On.collect{ it.id }
        engagedSwitchIds_Off = allEngagedSwitches_Off.collect{ it.id }
        
        for (entryDoor in entryDoors) {
            if (!(entryDoor.id in engagedDoorIds_Open) && !(entryDoor.id in engagedDoorIds_Closed)) {
                subscribe(entryDoor, "contact", entryDoorHandler)
            }
        }
        
        for (motionSensor in allMotionSensors) {
            subscribe(motionSensor, "motion", motionSensorHandler)
        }
        
        for (interiorDoor in allInteriorDoors) {
            if (!(interiorDoor.id in entryDoorIds) && !(interiorDoor.id in engagedDoorIds_Open) && !(interiorDoor.id in engagedDoorIds_Closed)) {
                subscribe(interiorDoor, "contact", activeDeviceHandler)
            }
        }
        
        for (button in allButtons) {
            subscribe(button, "pushed", activeDeviceHandler)
        }
        
        for (engagedDoor in allEngagedDoors_Open) {
            subscribe(engagedDoor, "contact.open", engagedDeviceHandler)
            
            if (engagedDoor.id in engagedDoorIds_Closed) {
                subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
            } else if (engagedDoor.id in entryDoorIds) {
                subscribe(engagedDoor, "contact.closed", entryDoorHandler)
            } else {
                subscribe(engagedDoor, "contact.closed", activeDeviceHandler)
            }
        }
        
        for (engagedDoor in allEngagedDoors_Closed) {
            if (!(engagedDoor.id in engagedDoorIds_Open)) { 
                subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
            
                if (engagedDoor.id in entryDoorIds) {
                    subscribe(engagedDoor, "contact.open", entryDoorHandler)
                } else {
                    subscribe(engagedDoor, "contact.open", activeDeviceHandler)
                }
            }
        }
        
        for (engagedSwitch in allEngagedSwitches_On) {
            subscribe(engagedSwitch, "switch.on", engagedDeviceHandler)
            
            if (engagedSwitch.id in engagedSwitchIds_Off) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
            } else {
                subscribe(engagedSwitch, "switch.off", activeDeviceHandler)
            }
        }
        
        for (engagedSwitch in allEngagedSwitches_Off) {
            if (!(engagedSwitch.id in engagedSwitchIds_On)) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
                subscribe(engagedSwitch, "switch.on", activeDeviceHandler)
            }
        }
    
    } else {
        log.error "Unknown zone type: $zoneType"
    }
    
    def zoneId = zone.getId()
    for (parentApp in parent.getChildApps()) {
        if (parentApp.getId() != app.getId()) {
            for (parentZone in parentApp.childZones) {
                if (parentZone.getId() == zoneId) {
                    parentApp.initialize()
                    break
                }
            }
        }
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

//-----------------------------------------

def getZoneDevice() {
    return parent.getZoneDevice(app.getId())
}

def getZoneAppId(zone) {
    def networkId = zone.getDeviceNetworkId()
    def appId = networkId.replace("zone:", "")
    return appId as int
}

def getAllDevices(settingName) {
    if (childZones) {
        def allDevices = settings[settingName].collect()
        for (childZone in childZones) {
            def childAppId = getZoneAppId(childZone)
            def childApp = parent.getChildAppById(childAppId)
            def childDevices = childApp.getAllDevices(settingName)
            if (childDevices) {
                allDevices.addAll(childDevices)
            }
        }
        return allDevices
    } else {
        return settings[settingName] ?: []
    }
}

//-----------------------------------------

def simpleDoorHandler(evt) {
    def debugContext = """Zone ${app.label} - Simple Door
${evt.device} is ${evt.value}
"""
    
    def zone = getZoneDevice()
    if (evt.value == "open") {
        zone.engaged()
        logDebug("$debugContext => engaged (1)")
    } else {
        zone.vacant()
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

def engagedDeviceHandler(evt) {
    def debugContext = """Zone ${app.label} - Engaged Device
${evt.device} is ${evt.value}"""
    
    deviceEngagedEvent(debugContext)
}

//-----------------------------------------

def deviceEngagedEvent(debugContext) {
    unschedule("activeTimeout")
    unschedule("unknownTimeout")
    
    def zone = getZoneDevice()
    debugContext = """$debugContext
Device Engaged Event
engaged = ${anyDeviceIsEngaged()}
motion = ${anyMotionSensorIsActive()}
open = ${zoneIsOpen()}
occupancy = ${zone.currentValue('occupancy')}
"""

    zone.engaged()
    logDebug("$debugContext => engaged")
}

def deviceActiveEvent(debugContext) {
    unschedule("activeTimeout")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    debugContext = """$debugContext
Device Active Event
engaged = ${anyDeviceIsEngaged()}
motion = ${anyMotionSensorIsActive()}
open = ${zoneIsOpen()}
occupancy = ${zone.currentValue('occupancy')}
"""
    
    if (!anyDeviceIsEngaged()) {
        if (zoneIsOpen()) {
            zone.active()
        
            if (!anyMotionSensorIsActive()) {
                logDebug("$debugContext => active (${activeSeconds}s)")
        
                if (activeSeconds > 0) {
                    runIn(activeSeconds, activeTimeout)
                } else {
                    activeTimeout()
                }
            } else {
                logDebug("$debugContext => ignored (motion active)")
            }
        } else {
            zone.engaged()
            logDebug("$debugContext => engaged")
        }
    } else {
        logDebug("$debugContext => ignored (engaged)")
    }
}

def activeTimeout() {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Active Timeout (${activeSeconds}s)
engaged = ${anyDeviceIsEngaged()}
motion = ${anyMotionSensorIsActive()}
open = ${zoneIsOpen()}
occupancy = ${zone.currentValue('occupancy')}
"""
    
    if (!anyDeviceIsEngaged()) {
        zone.vacant()
        logDebug("$debugContext => vacant")
    } else {
        logDebug("$debugContext => ignored (engaged)")
    }
}

def motionActiveEvent(debugContext) {
    unschedule("activeTimeout")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    debugContext = """$debugContext
Motion Active Event
engaged = ${anyDeviceIsEngaged()}
motion = ${anyMotionSensorIsActive()}
open = ${zoneIsOpen()}
occupancy = ${zone.currentValue('occupancy')}
"""
    
    if (!anyDeviceIsEngaged()) {
        if (zoneIsOpen()) {
            zone.active()
            logDebug("$debugContext => active")
        } else {
            zone.engaged()
            logDebug("$debugContext => engaged")
        }
    } else {
        logDebug("$debugContext => ignored (engaged)")
    }
}

def motionInactiveEvent(debugContext) {
    def zone = getZoneDevice()
    debugContext = """$debugContext
Motion Inactive Event
engaged = ${anyDeviceIsEngaged()}
motion = ${anyMotionSensorIsActive()}
open = ${zoneIsOpen()}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (!anyDeviceIsEngaged()) {
        if (zoneIsOpen()) {
            logDebug("$debugContext => waiting (${activeSeconds}s)")
        
            if (activeSeconds > 0) {
                runIn(activeSeconds, activeTimeout)
            } else {
                activeTimeout()
            }
        } else {
            logDebug("$debugContext => ignored (closed)")
        }
    } else {
        logDebug("$debugContext => ignored (engaged)")
    }
}

def zoneClosedEvent(debugContext) {
    unschedule("activeTimeout")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    debugContext = """$debugContext
Zone Closed Event
engaged = ${anyDeviceIsEngaged()}
motion = ${anyMotionSensorIsActive()}
open = ${zoneIsOpen()}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (!anyDeviceIsEngaged()) {
        zone.checking()
        logDebug("$debugContext => checking (${checkingSeconds}s)")
        
        if (checkingSeconds > 0) {
            runIn(checkingSeconds, checkingTimeout)
        } else {
            checkingTimeout()
        }
    } else {
        logDebug("$debugContext => ignored (engaged)")
    }
}

def checkingTimeout() {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Checking Timeout (${activeSeconds}s)
engaged = ${anyDeviceIsEngaged()}
motion = ${anyMotionSensorIsActive()}
open = ${zoneIsOpen()}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (!anyDeviceIsEngaged()) {
        if (anyMotionSensorIsActive()) {
            zone.engaged()
            logDebug("$debugContext => engaged")
        } else {
            zone.vacant()
            logDebug("$debugContext => vacant")
        }
    } else {
        logDebug("$debugContext => ignored (engaged)")
    }
}

//-----------------------------------------

def anyDeviceIsEngaged() {
    def allEngagedDoors_Open = getAllDevices("engagedDoors_Open")
    if (allEngagedDoors_Open) {
        for (engagedDoor in allEngagedDoors_Open) {
            if (engagedDoor.currentValue("contact") == "open") {
                return true
            }
        }
    }
    
    def allEngagedDoors_Closed = getAllDevices("engagedDoors_Closed")
    if (allEngagedDoors_Closed) {
        for (engagedDoor in allEngagedDoors_Closed) {
            if (engagedDoor.currentValue("contact") == "closed") {
                return true
            }
        }
    }
    
    def allEngagedSwitches_On = getAllDevices("engagedSwitches_On")
    if (allEngagedSwitches_On) {
        for (engagedSwitch in allEngagedSwitches_On) {
            if (engagedSwitch.currentValue("switch") == "on") {
                return true
            }
        }
    }
    
    def allEngagedSwitches_Off = getAllDevices("engagedSwitches_Off")
    if (allEngagedSwitches_Off) {
        for (engagedSwitch in allEngagedSwitches_Off) {
            if (engagedSwitch.currentValue("switch") == "off") {
                return true
            }
        }
    }
    
    return false
}

def anyMotionSensorIsActive() {
    def allMotionSensors = getAllDevices("motionSensors")
    if (allMotionSensors) {
        for (motionSensor in allMotionSensors) {
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