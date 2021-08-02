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
String getVersionNum() { return "6.5.0" }
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
            input "zoneType", "enum", title: "Zone Type", options: ["Automated", "Manual"], multiple: false, required: true, submitOnChange: true
            
            if (zoneType == "Automated") {
                paragraph "Automated Zones utilize every device within the zone to determine its occupancy. This is the most common case and can be used in most scenarios. The zone can be either open or closed, depending on the status of the entry doors, and will change its behavior accordingly to accurately determine if the zone is occupied. It also supports nested child zones, so it can be used for larger, more generic zones, like 'Downstairs' or 'Home', which then contain smaller, more specific zones, like 'Bedroom' and 'Kitchen'."
            } else if (zoneType == "Manual") {
                paragraph "Manual Zones are intended to be controlled by some other automation, e.g. Rule Machine. There is no automatic behavior, except for the time-based transition from checking to vacant."
            }
        }
        
        if (zoneType == "Automated") {
            section {
                input "childZones", "device.ZoneDevice", title: "Child Zones", multiple: true, required: false
                input "entryDoors", "capability.contactSensor", title: "Entry Doors", multiple: true, required: false
                }
            section("ENGAGED - Zone will stay occupied while:") {
                input "presenceSensors", "capability.presenceSensor", title: "Presence Sensor is Present", multiple: true, required: false
                input "motionSensors", "capability.motionSensor", title: "Motion Sensor is Active", multiple: true, required: false
                input "accelerationSensors", "capability.accelerationSensor", title: "Acceleration Sensor is Active", multiple: true, required: false
                input "engagedDoors_Open", "capability.contactSensor", title: "Door/Window is Open", multiple: true, required: false
                input "engagedDoors_Closed", "capability.contactSensor", title: "Door/Window is Closed", multiple: true, required: false
                input "engagedSwitches_On", "capability.switch", title: "Switch is On", multiple: true, required: false
                input "engagedSwitches_Off", "capability.switch", title: "Switch is Off", multiple: true, required: false
                input "engagedLocks_Unlocked", "capability.lock", title: "Lock is Unlocked", multiple: true, required: false
                input "engagedLocks_Locked", "capability.switch", title: "Lock is Locked", multiple: true, required: false
            }
            section("ACTIVE - Zone will be occupied briefly when device state changes") {
                input "activeDoors", "capability.contactSensor", title: "Doors & Windows", multiple: true, required: false
                input "activeButtons", "capability.pushableButton", title: "Buttons", multiple: true, required: false
                input "activeSwitches", "capability.switch", title: "Switches", multiple: true, required: false
                input "activeLocks", "capability.lock", title: "Locks", multiple: true, required: false
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
    
    if (zoneType == "Automated") {
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
        
        def allChildZones = childZones?.collectEntries{ [(it.id): it] } ?: [:]
        def allEntryDoors = entryDoors?.collectEntries{ [(it.id): it] } ?: [:]
    
        def allPresenceSensors = getAllDevices("presenceSensors")
        def allMotionSensors = getAllDevices("motionSensors")
        def allAccelerationSensors = getAllDevices("accelerationSensors")
        
        def allEngagedDoors_Open = getAllDevices("engagedDoors_Open")
        def allEngagedDoors_Closed = getAllDevices("engagedDoors_Closed")
        def allEngagedSwitches_On = getAllDevices("engagedSwitches_On")
        def allEngagedSwitches_Off = getAllDevices("engagedSwitches_Off")
        def allEngagedLocks_Unlocked = getAllDevices("engagedLocks_Unlocked")
        def allEngagedLocks_Locked = getAllDevices("engagedLocks_Locked")
        
        def allActiveDoors = getAllDevices("entryDoors") + getAllDevices("activeDoors")
        def allActiveButtons = getAllDevices("activeButtons")
        def allActiveSwitches = getAllDevices("activeSwitches")
        def allActiveLocks = getAllDevices("activeLocks")
        
        for (entryDoor in allEntryDoors.values()) {
            if (allEngagedDoors_Open.containsKey(entryDoor.id) && allEngagedDoors_Closed.containsKey(entryDoor.id)) {
                subscribe(entryDoor, "contact", entryDoorHandler_Engaged)
            } else if (allEngagedDoors_Open.containsKey(entryDoor.id)) {
                subscribe(entryDoor, "contact", entryDoorHandler_Engaged_Open)
            } else if (allEngagedDoors_Closed.containsKey(entryDoor.id)) {
                subscribe(entryDoor, "contact", entryDoorHandler_Engaged_Closed)
            } else {
                subscribe(entryDoor, "contact", entryDoorHandler_Active)
            }
        }
        
        for (presenceSensor in allPresenceSensors.values()) {
            subscribe(presenceSensor, "presence.present", engagedDeviceHandler)
            subscribe(presenceSensor, "presence.not present", inactiveDeviceHandler)
        }
        
        for (motionSensor in allMotionSensors.values()) {
            subscribe(motionSensor, "motion.active", engagedDeviceHandler)
            subscribe(motionSensor, "motion.inactive", inactiveDeviceHandler)
        }
        
        for (accelerationSensor in allAccelerationSensors.values()) {
            subscribe(accelerationSensor, "acceleration.active", engagedDeviceHandler)
            subscribe(accelerationSensor, "acceleration.inactive", inactiveDeviceHandler)
        }
        
        for (engagedDoor in allEngagedDoors_Open.values()) {
            if (!allEntryDoors.containsKey(engagedDoor.id)) {
                subscribe(engagedDoor, "contact.open", engagedDeviceHandler)
            
                if (allEngagedDoors_Closed.containsKey(engagedDoor.id)) {
                    subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
                } else {
                    subscribe(engagedDoor, "contact.closed", activeDeviceHandler)
                }
            }
        }
        
        for (engagedDoor in allEngagedDoors_Closed.values()) {
            if (!allEntryDoors.containsKey(engagedDoor.id) && !allEngagedDoors_Open.containsKey(engagedDoor.id)) { 
                subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
                subscribe(engagedDoor, "contact.open", activeDeviceHandler)
            }
        }
        
        for (engagedSwitch in allEngagedSwitches_On.values()) {
            subscribe(engagedSwitch, "switch.on", engagedDeviceHandler)
            
            if (allEngagedSwitches_Off.containsKey(engagedSwitch.id)) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
            } else {
                subscribe(engagedSwitch, "switch.off", activeDeviceHandler)
            }
        }
        
        for (engagedSwitch in allEngagedSwitches_Off.values()) {
            if (!allEngagedSwitches_On.containsKey(engagedSwitch.id)) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
                subscribe(engagedSwitch, "switch.on", activeDeviceHandler)
            }
        }
        
        for (engagedLock in allEngagedLocks_Unlocked.values()) {
            subscribe(engagedLock, "lock.unlocked", engagedDeviceHandler)
            
            if (allEngagedLocks_Locked.containsKey(engagedLock.id)) {
                subscribe(engagedLock, "lock.locked", engagedDeviceHandler)
            } else {
                subscribe(engagedLock, "lock.locked", activeDeviceHandler)
            }
        }
        
        for (engagedLock in allEngagedLocks_Locked.values()) {
            if (!allEngagedLocks_Unlocked.containsKey(engagedLock.id)) {
                subscribe(engagedLock, "lock.locked", engagedDeviceHandler)
                subscribe(engagedLock, "lock.unlocked", activeDeviceHandler)
            }
        }
        
        for (activeDoor in allActiveDoors.values()) {
            if (!allEntryDoors.containsKey(activeDoor.id) && !allEngagedDoors_Open.containsKey(activeDoor.id) && !allEngagedDoors_Closed.containsKey(activeDoor.id)) {
                subscribe(activeDoor, "contact", activeDeviceHandler)
            }
        }
        
        for (activeButton in allActiveButtons.values()) {
            subscribe(activeButton, "pushed", activeDeviceHandler)
            subscribe(activeButton, "doubleTapped", activeDeviceHandler)
            subscribe(activeButton, "held", activeDeviceHandler)
            subscribe(activeButton, "released", activeDeviceHandler)
        }
        
        for (activeSwitch in allActiveSwitches.values()) {
            if (!allEngagedSwitches_On.containsKey(activeSwitch.id) && !allEngagedSwitches_Off.containsKey(activeSwitch.id)) {
                subscribe(activeSwitch, "switch", activeDeviceHandler)
            }
        }
        
        for (activeLock in allActiveLocks.values()) {
            if (!allEngagedLocks_Unlocked.containsKey(activeLock.id) && !allEngagedLocks_Locked.containsKey(activeLock.id)) {
                subscribe(activeLock, "lock", activeDeviceHandler)
            }
        }
        
        for (childZone in allChildZones.values()) {
            if (childZone.id != zone.id) {
                subscribe(childZone, "occupancy.occupied", engagedDeviceHandler)
                subscribe(childZone, "occupancy.checking", inactiveDeviceHandler)
                subscribe(childZone, "occupancy.vacant", inactiveDeviceHandler)
            }
        }
    
    } else if (zoneType != "Manual") {
        log.error "Unknown zone type: $zoneType"
    }
    
    logDebug("""Zone ${app.label} - Initial
engaged = ${zoneIsEngaged()}
contact = ${zone.currentValue('contact')}
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
    if (zoneType == "Automated") {
        def allDevices = settings[settingName]?.collectEntries{ [(it.id): it] } ?: [:]
        if (childZones) {
            def zone = getZoneDevice()
            for (childZone in childZones) {
                if (childZone.id != zone.id) {
                    def childAppId = getZoneAppId(childZone)
                    def childApp = parent.getChildAppById(childAppId)
                    def childDevices = childApp.getAllDevices(settingName)
                    if (childDevices) {
                        allDevices.putAll(childDevices)
                    }
                }
            }
        }
        return allDevices
    } else {
        return [:]
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

def entryDoorHandler_Engaged(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""
    
    if (zoneIsOpen()) {
        zone.open()
        engagedEvent(debugContext)
    } else {
        zone.close()
        engagedEvent(debugContext)
    }
}

def entryDoorHandler_Engaged_Open(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""
    
    if (zoneIsOpen()) {
        zone.open()
        
        if (evt.value == "open") {
            engagedEvent(debugContext)
        } else {
            activeEvent(debugContext)
        }
    } else {
        zone.close()
        closedEvent(debugContext)
    }
}

def entryDoorHandler_Engaged_Closed(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""
    
    if (zoneIsOpen()) {
        zone.open()
        
        if (evt.value == "open") {
            activeEvent(debugContext)
        } else {
            engagedEvent(debugContext)
        }
    } else {
        zone.close()
        engagedEvent(debugContext)
    }
}

def entryDoorHandler_Active(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""
    
    if (zoneIsOpen()) {
        zone.open()
        activeEvent(debugContext)
    } else {
        zone.close()
        closedEvent(debugContext)
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

def inactiveDeviceHandler(evt) {
    def debugContext = """Zone ${app.label} - Inactive Device
${evt.device} is ${evt.value}"""
    
    inactiveEvent(debugContext)
}

//-----------------------------------------

def engagedEvent(debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    debugContext = """$debugContext
Engaged Event
engaged = ${zoneIsEngaged()}
contact = ${zone.currentValue('contact')}
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
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""
    
    if (zoneIsEngaged()) {
        zone.occupied()
        logDebug("$debugContext => occupied (engaged)")
    } else {
        if (zone.currentValue("contact") == "open") {
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
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zoneIsEngaged()) {
        logDebug("$debugContext => ignored (engaged)")
    } else {
        if (zone.currentValue("contact") == "open") {
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
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (anyDoorSwitchLockIsEngaged()) {
        logDebug("$debugContext => ignored (engaged)")
    } else {
        zone.checking()
        logDebug("$debugContext => checking (${checkingSeconds}s)")
        scheduleCheckingTimeout()
    }
}

//-----------------------------------------

def scheduleCheckingTimeout() {
    def zone = getZoneDevice()
    if (zone.currentValue("contact") == "closed") {
        if (motionSeconds) {
            runIn(motionSeconds, checkForSustainedMotion)
        }
    }
    
    if (checkingSeconds > 0) {
        runIn(checkingSeconds, checkingTimeout)
    } else {
        checkingTimeout()
    }
}

def checkForSustainedMotion() {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Motion Check (${motionSeconds}s)
engaged = ${zoneIsEngaged()}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""
    if (anyMotionSensorIsActive()) {
        unschedule("checkingTimeout")
        zone.occupied()
        logDebug("$debugContext => occupied")
    } else {
        logDebug("$debugContext => ignored (no motion)")
    }
}

def checkingTimeout() {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Checking Timeout (${checkingSeconds}s)
engaged = ${zoneIsEngaged()}
contact = ${zone.currentValue('contact')}
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

def anyDoorSwitchLockIsEngaged() {
    if (zoneType == "Automated") {
        def allEngagedDoors_Open = getAllDevices("engagedDoors_Open")
        if (allEngagedDoors_Open) {
            for (engagedDoor in allEngagedDoors_Open.values()) {
                if (engagedDoor.currentValue("contact") == "open") {
                    return "$engagedDoor is open"
                }
            }
        }
        
        def allEngagedDoors_Closed = getAllDevices("engagedDoors_Closed")
        if (allEngagedDoors_Closed) {
            for (engagedDoor in allEngagedDoors_Closed.values()) {
                if (engagedDoor.currentValue("contact") == "closed") {
                    return "$engagedDoor is closed"
                }
            }
        }
        
        def allEngagedSwitches_On = getAllDevices("engagedSwitches_On")
        if (allEngagedSwitches_On) {
            for (engagedSwitch in allEngagedSwitches_On.values()) {
                if (engagedSwitch.currentValue("switch") == "on") {
                    return "$engagedSwitch is on"
                }
            }
        }
        
        def allEngagedSwitches_Off = getAllDevices("engagedSwitches_Off")
        if (allEngagedSwitches_Off) {
            for (engagedSwitch in allEngagedSwitches_Off.values()) {
                if (engagedSwitch.currentValue("switch") == "off") {
                    return "$engagedSwitch is off"
                }
            }
        }
        
        def allEngagedLocks_Unlocked = getAllDevices("engagedLocks_Unlocked")
        if (allEngagedLocks_Unlocked) {
            for (engagedLock in allEngagedLocks_Unlocked.values()) {
                if (engagedLock.currentValue("lock") == "unlocked") {
                    return "$engagedLock is unlocked"
                }
            }
        }
        
        def allEngagedLocks_Locked = getAllDevices("engagedLocks_Locked")
        if (allEngagedLocks_Locked) {
            for (engagedLock in allEngagedLocks_Locked.values()) {
                if (engagedLock.currentValue("lock") == "locked") {
                    return "$engagedLock is locked"
                }
            }
        }
    }
    
    return false
}

def anyMotionSensorIsActive() {
    if (zoneType == "Automated") {
        def allMotionSensors = getAllDevices("motionSensors")
        if (allMotionSensors) {
            for (motionSensor in allMotionSensors.values()) {
                if (motionSensor.currentValue("motion") == "active") {
                    return "$motionSensor is active"
                }
            }
        }
    }
    
    return false
}

def zoneIsEngaged() {
    if (zoneType == "Automated") {
        def engagedDoorSwitchLock = anyDoorSwitchLockIsEngaged()
        if (engagedDoorSwitchLock) {
            return engagedDoorSwitchLock
        }
        
        def allPresenceSensors = getAllDevices("presenceSensors")
        if (allPresenceSensors) {
            for (presenceSensor in allPresenceSensors.values()) {
                if (presenceSensor.currentValue("presence") == "present") {
                    return "$presenceSensor is present"
                }
            }
        }
        
        def activeMotionSensor = anyMotionSensorIsActive()
        if (activeMotionSensor) {
            return activeMotionSensor
        }
        
        def allAccelerationSensors = getAllDevices("accelerationSensors")
        if (allAccelerationSensors) {
            for (accelerationSensor in allAccelerationSensors.values()) {
                if (accelerationSensor.currentValue("acceleration") == "active") {
                    return "$accelerationSensor is active"
                }
            }
        }
        
        if (childZones) {
            def zone = getZoneDevice()
            for (childZone in childZones) {
                if (childZone.id != zone.id) {
                    if (childZone.currentValue("occupancy") == "occupied") {
                        return "$childZone is occupied"
                    }
                }
            }
        }
    }
    
    return false
}

def zoneIsOpen() {
    if (zoneType == "Automated") {
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