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
String getVersionNum() { return "5.1.0" }
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
            input "zoneType", "enum", title: "Zone Type", options: ["Simple", "Standard", "Manual"], multiple: false, required: true, submitOnChange: true
            
            if (zoneType == "Simple") {
                paragraph "Simple Zones only include a single door. The zone will be occupied when the door is open and will be vacant when the door is closed. This can be used for closets and other small rooms. The main benefit is that it will transition immediately to vacant when the door is closed. Unlike the Standard Zone, it will not use the checking state or the associated timeout before changing to vacant."
            } else if (zoneType == "Standard") {
                paragraph "Standard Zones utilize every device within the zone to determine its occupancy. This is the most common case and can be used in most scenarios. The zone can be either open or closed, depending on the status of the entry doors, and will change its behavior accordingly to accurately determine if the zone is occupied. It also supports nested child zones, so it can be used for larger, more generic zones, like 'Downstairs' or 'Home', which then contain smaller, more specific zones, like 'Bedroom' and 'Kitchen'."
            } else if (zoneType == "Manual") {
                paragraph "Manual Zones are intended to be controlled by some other automation, e.g. Rule Machine. There is no automatic behavior, except for the time-based transition from checking to vacant."
            }
        }
        
        if (zoneType == "Simple") {
            section {
                input "simpleDoor", "capability.contactSensor", title: "Door", multiple: false, required: true
            }
            section {
                input "checkingSeconds", "number", title: "CHECKING - Time that zone will check for activity when manually set to checking (seconds)", required: true, defaultValue: 60
                paragraph "Simple Zones will not automatically transition to the checking state, but it can be triggered manually from the device page or by other automations (e.g. Rule Machine)."
            }

        } else if (zoneType == "Standard") {
            section {
                input "childZones", "device.ZoneDevice", title: "Child Zones", multiple: true, required: false
                input "entryDoors", "capability.contactSensor", title: "Entry Doors", multiple: true, required: false
                }
            section("ENGAGED - Zone will stay occupied while:") {
                input "presenceSensors", "capability.presenceSensor", title: "Presence Sensor is present", multiple: true, required: false
                input "motionSensors", "capability.motionSensor", title: "Motion Sensor is active", multiple: true, required: false
                input "engagedDoors_Open", "capability.contactSensor", title: "Door/Window is Open", multiple: true, required: false
                input "engagedDoors_Closed", "capability.contactSensor", title: "Door/Window is Closed", multiple: true, required: false
                input "engagedSwitches_On", "capability.switch", title: "Switch is On", multiple: true, required: false
                input "engagedSwitches_Off", "capability.switch", title: "Switch is Off", multiple: true, required: false
            }
            section("ACTIVE - Zone will be occupied briefly when device state changes") {
                input "interiorDoors", "capability.contactSensor", title: "Doors & Windows", multiple: true, required: false
                input "buttons", "capability.pushableButton", title: "Buttons", multiple: true, required: false
            }
            section {
                input "checkingSeconds", "number", title: "CHECKING - Time that zone will check for activity before returning to vacant (seconds)", required: true, defaultValue: 60
                input "motionSeconds", "number", title: "RETRIGGER - Time that it takes for motion sensors to return to inactive after detecting motion. This should be slightly longer than the longest retrigger time of any motion sensor in the zone (seconds)", required: true, defaultValue: 20
            }
        } else if (zoneType == "Manual") {
            section {
                input "checkingSeconds", "number", title: "CHECKING - Time that zone will check for activity when manually set to checking (seconds)", required: true, defaultValue: 60
                paragraph "Manual Zones will not automatically transition to the checking state, but it can be triggered manually from the device page or by other automations (e.g. Rule Machine)."
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
    subscribe(zone, "occupancy", occupancyHandler)
    
    if (zoneType == "Simple") {
        if (simpleDoor.currentValue("contact") == "open") {
            zone.open()
            zone.occupied()
        } else {
            zone.close()
            zone.vacant()
        }
        
        subscribe(simpleDoor, "contact", simpleDoorHandler)
    
    } else if (zoneType == "Standard") {
        if (entryDoors) {
            if (zoneIsOpen()) {
                zone.open()
            } else {
                zone.close()
            }
        }   
        if (zoneIsEngaged()) {
            zone.occupied()
        } else {
            zone.vacant()
        }
    
        def allPresenceSensors = getAllDevices("presenceSensors")
        def allMotionSensors = getAllDevices("motionSensors")
        def allEngagedDoors_Open = getAllDevices("engagedDoors_Open")
        def allEngagedDoors_Closed = getAllDevices("engagedDoors_Closed")
        def allEngagedSwitches_On = getAllDevices("engagedSwitches_On")
        def allEngagedSwitches_Off = getAllDevices("engagedSwitches_Off")
        def allInteriorDoors = getAllDevices("entryDoors") + getAllDevices("interiorDoors")
        def allButtons = getAllDevices("buttons")
        
        entryDoorIds = entryDoors.collect{ it.id }
        engagedDoorIds_Open = allEngagedDoors_Open.collect{ it.id }
        engagedDoorIds_Closed = allEngagedDoors_Closed.collect{ it.id }
        engagedSwitchIds_On = allEngagedSwitches_On.collect{ it.id }
        engagedSwitchIds_Off = allEngagedSwitches_Off.collect{ it.id }
        
        for (entryDoor in entryDoors) {
            subscribe(entryDoor, "contact", entryDoorHandler)
        }
        
        for (presenceSensor in allPresenceSensors) {
            subscribe(presenceSensor, "presence", presenceSensorHandler)
        }
        
        for (motionSensor in allMotionSensors) {
            subscribe(motionSensor, "motion", motionSensorHandler)
        }
        
        for (engagedDoor in allEngagedDoors_Open) {
            if (!(engagedDoor.id in entryDoorIds)) {
                subscribe(engagedDoor, "contact.open", engagedDeviceHandler)
            
                if (engagedDoor.id in engagedDoorIds_Closed) {
                    subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
                } else {
                    subscribe(engagedDoor, "contact.closed", activeDeviceHandler)
                }
            }
        }
        
        for (engagedDoor in allEngagedDoors_Closed) {
            if (!(engagedDoor.id in entryDoorIds) && !(engagedDoor.id in engagedDoorIds_Open)) { 
                subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
                subscribe(engagedDoor, "contact.open", activeDeviceHandler)
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
        
        for (interiorDoor in allInteriorDoors) {
            if (!(interiorDoor.id in entryDoorIds) && !(interiorDoor.id in engagedDoorIds_Open) && !(interiorDoor.id in engagedDoorIds_Closed)) {
                subscribe(interiorDoor, "contact", activeDeviceHandler)
            }
        }
        
        for (button in allButtons) {
            subscribe(button, "pushed", activeDeviceHandler)
            subscribe(button, "doubleTapped", activeDeviceHandler)
            subscribe(button, "held", activeDeviceHandler)
            subscribe(button, "released", activeDeviceHandler)
        }
        
        for (childZone in childZones) {
            subscribe(childZone, "occupancy", childZoneHandler)
        }
    
    } else if (zoneType != "Manual") {
        log.error "Unknown zone type: $zoneType"
    }
    
    logDebug("""Zone ${app.label} - Initial
engaged = ${zoneIsEngaged()}
entry = ${zone.currentValue('entry')}
occupancy = ${zone.currentValue('occupancy')}
""")
    
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
    if (zoneType == "Simple") {
        if (settingName == "engagedDoors_Open") {
            return [settings["simpleDoor"]]
        } else {
            return []
        }
    } else if (zoneType == "Standard") {
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
    } else {
        return []
    }
}

//-----------------------------------------

def occupancyHandler(evt) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    if (evt.value == "checking") {
        scheduleCheckingTimeout()
    }
}

def simpleDoorHandler(evt) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Simple Door
${evt.device} is ${evt.value}
"""
    
    if (evt.value == "open") {
        zone.open()
        zone.occupied()
        logDebug("$debugContext => occupied")
    } else {
        zone.close()
        zone.vacant()
        logDebug("$debugContext => vacant")
    }
}

def entryDoorHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""
    
    if (zoneIsOpen()) {
        zone.open()
        
        if (evt.value == "open") {
            if (engagedDoors_Open.any{ it.id == evt.device.id }) {
                engagedEvent(debugContext)
            } else {
                activeEvent(debugContext)
            }
        } else {
            if (engagedDoors_Closed.any{ it.id == evt.device.id }) {
                engagedEvent(debugContext)
            } else {
                activeEvent(debugContext)
            }
        }
    } else {
        zone.close()
        
        if (engagedDoors_Closed.any{ it.id == evt.device.id }) {
            engagedEvent(debugContext)
        } else {
            closedEvent(debugContext)
        }
    }
}

def presenceSensorHandler(evt) {
    def debugContext = """Zone ${app.label} - Presence Sensor
${evt.device} is ${evt.value}"""
    
    if (evt.value == "present") {
        engagedEvent(debugContext)
    } else {
        inactiveEvent(debugContext)
    }
}

def motionSensorHandler(evt) {
    def debugContext = """Zone ${app.label} - Motion Sensor
${evt.device} is ${evt.value}"""
    
    if (evt.value == "active") {
        engagedEvent(debugContext)
    } else {
        inactiveEvent(debugContext)
    }
}

def engagedDeviceHandler(evt) {
    def debugContext = """Zone ${app.label} - Engaged Device
${evt.device} is ${evt.value}"""
    
    engagedEvent(debugContext)
}

def activeDeviceHandler(evt) {
    def debugContext = """Zone ${app.label} - Active Device
${evt.device} is ${evt.value}"""
    
    activeEvent(debugContext)
}

def childZoneHandler(evt) {
    def debugContext = """Zone ${app.label} - Child Zone
${evt.device} is ${evt.value}"""
    
    if (evt.value == "occupied") {
        engagedEvent(debugContext)
    } else {
        inactiveEvent(debugContext)
    }
}

//-----------------------------------------

def engagedEvent(debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    debugContext = """$debugContext
Engaged Event
engaged = ${zoneIsEngaged()}
entry = ${zone.currentValue('entry')}
occupancy = ${zone.currentValue('occupancy')}
"""

    zone.occupied()
    logDebug("$debugContext => occupied (engaged)")
}

def activeEvent(debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    debugContext = """$debugContext
Active Event
engaged = ${zoneIsEngaged()}
entry = ${zone.currentValue('entry')}
occupancy = ${zone.currentValue('occupancy')}
"""
    
    if (zoneIsEngaged()) {
        zone.occupied()
        logDebug("$debugContext => occupied (engaged)")
    } else {
        if (zone.currentValue("entry") == "open") {
            zone.checking()
            logDebug("$debugContext => checking (${checkingSeconds}s)")
            scheduleCheckingTimeout()
        } else {
            zone.occupied()
            logDebug("$debugContext => occupied (closed)")
        }
    }
}

def inactiveEvent(debugContext) {
    def zone = getZoneDevice()
    debugContext = """$debugContext
Inactive Event
engaged = ${zoneIsEngaged()}
entry = ${zone.currentValue('entry')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zoneIsEngaged()) {
        logDebug("$debugContext => ignored (engaged)")
    } else {
        if (zone.currentValue("entry") == "open") {
            if (zone.currentValue("occupancy") == "occupied") {
                zone.checking()
                logDebug("$debugContext => checking (${checkingSeconds}s)")
                scheduleCheckingTimeout()
            } else {
                logDebug("$debugContext => ignored (not occupied)")
            }
        } else {
            logDebug("$debugContext => ignored (closed)")
        }
    }
}

def closedEvent(debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    debugContext = """$debugContext
Closed Event
engaged = ${zoneIsEngaged()}
entry = ${zone.currentValue('entry')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (anyDoorOrSwitchIsEngaged()) {
        logDebug("$debugContext => ignored (engaged)")
    } else {
        zone.checking()
        logDebug("$debugContext => checking (${checkingSeconds}s)")
        if (motionSeconds) {
            runIn(motionSeconds, checkForSustainedMotion)
        }
        scheduleCheckingTimeout()
    }
}

//-----------------------------------------

def checkForSustainedMotion() {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Motion Check (${motionSeconds}s)
engaged = ${zoneIsEngaged()}
entry = ${zone.currentValue('entry')}
occupancy = ${zone.currentValue('occupancy')}
"""
    if (anyMotionSensorIsActive()) {
        unschedule("checkingTimeout")
        zone.occupied()
        logDebug("$debugContext => occupied")
    }
}

def scheduleCheckingTimeout() {
    if (checkingSeconds > 0) {
        runIn(checkingSeconds, checkingTimeout)
    } else {
        checkingTimeout()
    }
}

def checkingTimeout() {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Checking Timeout (${checkingSeconds}s)
engaged = ${zoneIsEngaged()}
entry = ${zone.currentValue('entry')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zoneIsEngaged()) {
        zone.occupied()
        logDebug("$debugContext => occupied (engaged)")
    } else {
        zone.vacant()
        logDebug("$debugContext => vacant")
    }
}

//-----------------------------------------

def anyDoorOrSwitchIsEngaged() {
    if (zoneType == "Simple") {
        if (simpleDoor.currentValue("contact") == "open") {
            return "$simpleDoor is open"
        }
        
    } else if (zoneType == "Standard") {
        def allEngagedDoors_Open = getAllDevices("engagedDoors_Open")
        if (allEngagedDoors_Open) {
            for (engagedDoor in allEngagedDoors_Open) {
                if (engagedDoor.currentValue("contact") == "open") {
                    return "$engagedDoor is open"
                }
            }
        }
        
        def allEngagedDoors_Closed = getAllDevices("engagedDoors_Closed")
        if (allEngagedDoors_Closed) {
            for (engagedDoor in allEngagedDoors_Closed) {
                if (engagedDoor.currentValue("contact") == "closed") {
                    return "$engagedDoor is closed"
                }
            }
        }
        
        def allEngagedSwitches_On = getAllDevices("engagedSwitches_On")
        if (allEngagedSwitches_On) {
            for (engagedSwitch in allEngagedSwitches_On) {
                if (engagedSwitch.currentValue("switch") == "on") {
                    return "$engagedSwitch is on"
                }
            }
        }
        
        def allEngagedSwitches_Off = getAllDevices("engagedSwitches_Off")
        if (allEngagedSwitches_Off) {
            for (engagedSwitch in allEngagedSwitches_Off) {
                if (engagedSwitch.currentValue("switch") == "off") {
                    return "$engagedSwitch is off"
                }
            }
        }
    }
    
    return false
}

def anyMotionSensorIsActive() {
    if (zoneType == "Standard") {
        def allMotionSensors = getAllDevices("motionSensors")
        if (allMotionSensors) {
            for (motionSensor in allMotionSensors) {
                if (motionSensor.currentValue("motion") == "active") {
                    return "$motionSensor is active"
                }
            }
        }
    }
    
    return false
}

def zoneIsEngaged() {
    if (zoneType == "Simple") {
        if (simpleDoor.currentValue("contact") == "open") {
            return "$simpleDoor is open"
        }
        
    } else if (zoneType == "Standard") {
        def engagedDoorOrSwitch = anyDoorOrSwitchIsEngaged()
        if (engagedDoorOrSwitch) {
            return engagedDoorOrSwitch
        }
        
        def allPresenceSensors = getAllDevices("presenceSensors")
        if (allPresenceSensors) {
            for (presenceSensor in allPresenceSensors) {
                if (presenceSensor.currentValue("presence") == "present") {
                    return "$presenceSensor is present"
                }
            }
        }
        
        def activeMotionSensor = anyMotionSensorIsActive()
        if (activeMotionSensor) {
            return activeMotionSensor
        }
        
        if (childZones) {
            for (childZone in childZones) {
                if (childZone.currentValue("occupancy") == "occupied") {
                    return "$childZone is occupied"
                }
            }
        }
    }
    
    return false
}

def zoneIsOpen() {
    if (zoneType == "Simple") {
        if (simpleDoor.currentValue("contact") == "open") {
            return "$simpleDoor is open"
        } else {
            return false
        }
        
    } else if (zoneType == "Standard") {
        if (entryDoors) {
            for (entryDoor in entryDoors) {
                if (entryDoor.currentValue("contact") == "open") {
                    return "$entryDoor is open"
                }
            }
            return false
        }
    }
    
    return "No entry doors"
}