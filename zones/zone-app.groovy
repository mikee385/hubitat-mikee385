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
String getVersionNum() { return "8.0.1" }
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
            section("ACTIVE - Zone will be occupied while device is active or present") {
                input "presenceSensors", "capability.presenceSensor", title: "Presence Sensors", multiple: true, required: false
                input "motionSensors", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false
                input "accelerationSensors", "capability.accelerationSensor", title: "Acceleration Sensors", multiple: true, required: false
            }
            section("MOMENTARY - Zone will be occupied briefly when device state changes") {
                input "momentaryDoors", "capability.contactSensor", title: "Doors & Windows", multiple: true, required: false
                input "momentaryButtons", "capability.pushableButton", title: "Buttons", multiple: true, required: false
                input "momentarySwitches", "capability.switch", title: "Switches", multiple: true, required: false
                input "momentaryLocks", "capability.lock", title: "Locks", multiple: true, required: false
            }
            section("ENGAGED - Zone will stay occupied while:") {
                input "engagedDoors_Open", "capability.contactSensor", title: "Door/Window is Open", multiple: true, required: false
                input "engagedDoors_Closed", "capability.contactSensor", title: "Door/Window is Closed", multiple: true, required: false
                input "engagedSwitches_On", "capability.switch", title: "Switch is On", multiple: true, required: false
                input "engagedSwitches_Off", "capability.switch", title: "Switch is Off", multiple: true, required: false
                input "engagedLocks_Unlocked", "capability.lock", title: "Lock is Unlocked", multiple: true, required: false
                input "engagedLocks_Locked", "capability.switch", title: "Lock is Locked", multiple: true, required: false
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
    def initializedAppIds = [].toSet()
    initializeZone(initializedAppIds)
}

def initializeZone(initializedAppIds) {
    parent.addZoneDevice(app.id, app.label)
    
    def zone = getZoneDevice()
    subscribe(zone, "occupancy", occupancyHandler)
    
    def zoneApps = getAllChildZoneApps(zone)
    //log.debug(zoneApps.values().collect{ "${it.id}:${it.label}" })
    
    if (zoneType == "Automated") {
        if (entryDoors) {
            if (zoneIsOpen(zone)) {
                zone.open()
            } else {
                zone.close()
            }
        }   
        if (zoneIsEngaged(zone, zoneApps)) {
            zone.occupied()
        } else if (zoneIsActive(zone, zoneApps)) {
            zone.occupied()
        } else {
            zone.vacant()
        }
        
        def allChildZones = getDevices(zone, "childZones")
        def allEntryDoors = getDevices(zone, "entryDoors")
    
        def allPresenceSensors = getAllDevices(zone, zoneApps, "presenceSensors")
        def allMotionSensors = getAllDevices(zone, zoneApps, "motionSensors")
        def allAccelerationSensors = getAllDevices(zone, zoneApps, "accelerationSensors")
        
        def allEngagedDoors_Open = getAllDevices(zone, zoneApps, "engagedDoors_Open")
        def allEngagedDoors_Closed = getAllDevices(zone, zoneApps, "engagedDoors_Closed")
        def allEngagedSwitches_On = getAllDevices(zone, zoneApps, "engagedSwitches_On")
        def allEngagedSwitches_Off = getAllDevices(zone, zoneApps, "engagedSwitches_Off")
        def allEngagedLocks_Unlocked = getAllDevices(zone, zoneApps, "engagedLocks_Unlocked")
        def allEngagedLocks_Locked = getAllDevices(zone, zoneApps, "engagedLocks_Locked")
        
        def allMomentaryDoors = getAllDevices(zone, zoneApps, "entryDoors") + getAllDevices(zone, zoneApps, "momentaryDoors")
        def allMomentaryButtons = getAllDevices(zone, zoneApps, "momentaryButtons")
        def allMomentarySwitches = getAllDevices(zone, zoneApps, "momentarySwitches")
        def allMomentaryLocks = getAllDevices(zone, zoneApps, "momentaryLocks")
        
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
            subscribe(presenceSensor, "presence.present", activeDeviceHandler)
            subscribe(presenceSensor, "presence.not present", inactiveDeviceHandler)
        }
        
        for (motionSensor in allMotionSensors.values()) {
            subscribe(motionSensor, "motion.active", activeDeviceHandler)
            subscribe(motionSensor, "motion.inactive", inactiveDeviceHandler)
        }
        
        for (accelerationSensor in allAccelerationSensors.values()) {
            subscribe(accelerationSensor, "acceleration.active", activeDeviceHandler)
            subscribe(accelerationSensor, "acceleration.inactive", inactiveDeviceHandler)
        }
        
        for (engagedDoor in allEngagedDoors_Open.values()) {
            if (!allEntryDoors.containsKey(engagedDoor.id)) {
                subscribe(engagedDoor, "contact.open", engagedDeviceHandler)
            
                if (allEngagedDoors_Closed.containsKey(engagedDoor.id)) {
                    subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
                } else {
                    subscribe(engagedDoor, "contact.closed", momentaryDeviceHandler)
                }
            }
        }
        
        for (engagedDoor in allEngagedDoors_Closed.values()) {
            if (!allEntryDoors.containsKey(engagedDoor.id) && !allEngagedDoors_Open.containsKey(engagedDoor.id)) { 
                subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
                subscribe(engagedDoor, "contact.open", momentaryDeviceHandler)
            }
        }
        
        for (engagedSwitch in allEngagedSwitches_On.values()) {
            subscribe(engagedSwitch, "switch.on", engagedDeviceHandler)
            
            if (allEngagedSwitches_Off.containsKey(engagedSwitch.id)) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
            } else {
                subscribe(engagedSwitch, "switch.off", momentaryDeviceHandler)
            }
        }
        
        for (engagedSwitch in allEngagedSwitches_Off.values()) {
            if (!allEngagedSwitches_On.containsKey(engagedSwitch.id)) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
                subscribe(engagedSwitch, "switch.on", momentaryDeviceHandler)
            }
        }
        
        for (engagedLock in allEngagedLocks_Unlocked.values()) {
            subscribe(engagedLock, "lock.unlocked", engagedDeviceHandler)
            
            if (allEngagedLocks_Locked.containsKey(engagedLock.id)) {
                subscribe(engagedLock, "lock.locked", engagedDeviceHandler)
            } else {
                subscribe(engagedLock, "lock.locked", momentaryDeviceHandler)
            }
        }
        
        for (engagedLock in allEngagedLocks_Locked.values()) {
            if (!allEngagedLocks_Unlocked.containsKey(engagedLock.id)) {
                subscribe(engagedLock, "lock.locked", engagedDeviceHandler)
                subscribe(engagedLock, "lock.unlocked", momentaryDeviceHandler)
            }
        }
        
        for (momentaryDoor in allMomentaryDoors.values()) {
            if (!allEntryDoors.containsKey(momentaryDoor.id) && !allEngagedDoors_Open.containsKey(momentaryDoor.id) && !allEngagedDoors_Closed.containsKey(momentaryDoor.id)) {
                subscribe(momentaryDoor, "contact", momentaryDeviceHandler)
            }
        }
        
        for (momentaryButton in allMomentaryButtons.values()) {
            subscribe(momentaryButton, "pushed", momentaryDeviceHandler)
            subscribe(momentaryButton, "doubleTapped", momentaryDeviceHandler)
            subscribe(momentaryButton, "held", momentaryDeviceHandler)
            subscribe(momentaryButton, "released", momentaryDeviceHandler)
        }
        
        for (momentarySwitch in allMomentarySwitches.values()) {
            if (!allEngagedSwitches_On.containsKey(momentarySwitch.id) && !allEngagedSwitches_Off.containsKey(momentarySwitch.id)) {
                subscribe(momentarySwitch, "switch", momentaryDeviceHandler)
            }
        }
        
        for (momentaryLock in allMomentaryLocks.values()) {
            if (!allEngagedLocks_Unlocked.containsKey(momentaryLock.id) && !allEngagedLocks_Locked.containsKey(momentaryLock.id)) {
                subscribe(momentaryLock, "lock", momentaryDeviceHandler)
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
    
    initializedAppIds.add(app.id)
    
    logDebug("""Zone ${app.label} - Initial
engaged = ${zoneIsEngaged(zone, zoneApps)}
active = ${zoneIsActive(zone, zoneApps)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
""")
    
    for (parentApp in parent.getChildApps()) {
        for (parentZone in parentApp.childZones) {
            if (parentZone.id == zone.id) {
                if (!initializedAppIds.contains(parentApp.id)) {
                    parentApp.initializeZone(initializedAppIds)
                } else {
                    log.warn "Already initialized ${parentApp.label}. Skipping..."
                }
                break
            }
        }
    }
}

def uninstalled() {
    parent.deleteZoneDevice(app.id)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

//-----------------------------------------

def getZoneDevice() {
    return parent.getZoneDevice(app.id)
}

def getZoneAppId(zone) {
    def networkId = zone.getDeviceNetworkId()
    def appId = networkId.replace("zone:", "")
    return appId as int
}

def getAllChildZoneApps(zone) {
    def zoneApps = [(zone.id): app]
    getChildZoneApps(zoneApps)
    return zoneApps
}

def getChildZoneApps(zoneApps) {
    for (childZone in childZones) {
        if (!zoneApps.containsKey(childZone.id)) {
            def childAppId = getZoneAppId(childZone)
            def childApp = parent.getChildAppById(childAppId)
            zoneApps[childZone.id] = childApp
            childApp.getChildZoneApps(zoneApps)
        }
    }
}

def getAllDevices(zone, zoneApps, settingName) {
    return zoneApps.values().collectEntries{ it.getDevices(zone, settingName) }
}

def getDevices(zone, settingName) {
    return settings[settingName]
        ?.findAll{ it.id != zone.id }
        ?.collectEntries{ [(it.id): it] } 
        ?: [:]
}

//-----------------------------------------

def occupancyHandler(evt) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    def zone = getZoneDevice()
    if (evt.value == "checking") {
        scheduleCheckingTimeout(zone)
    }
}

def entryDoorHandler_Engaged(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""
    
    if (zoneIsOpen(zone)) {
        zone.open()
        engagedEvent(zone, debugContext)
    } else {
        zone.close()
        engagedEvent(zone, debugContext)
    }
}

def entryDoorHandler_Engaged_Open(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""
    
    if (zoneIsOpen(zone)) {
        zone.open()
        
        if (evt.value == "open") {
            engagedEvent(zone, debugContext)
        } else {
            momentaryEvent(zone, debugContext)
        }
    } else {
        zone.close()
        closedEvent(zone, debugContext)
    }
}

def entryDoorHandler_Engaged_Closed(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""
    
    if (zoneIsOpen(zone)) {
        zone.open()
        
        if (evt.value == "open") {
            momentaryEvent(zone, debugContext)
        } else {
            engagedEvent(zone, debugContext)
        }
    } else {
        zone.close()
        engagedEvent(zone, debugContext)
    }
}

def entryDoorHandler_Active(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""
    
    if (zoneIsOpen(zone)) {
        zone.open()
        momentaryEvent(zone, debugContext)
    } else {
        zone.close()
        closedEvent(zone, debugContext)
    }
}

def engagedDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Engaged Device
${evt.device} is ${evt.value}"""
    
    engagedEvent(zone, debugContext)
}

def activeDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Active Device
${evt.device} is ${evt.value}"""
    
    activeEvent(zone, debugContext)
}

def inactiveDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Inactive Device
${evt.device} is ${evt.value}"""
    
    inactiveEvent(zone, debugContext)
}

def momentaryDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Momentary Device
${evt.device} is ${evt.value}"""
    
    momentaryEvent(zone, debugContext)
}

//-----------------------------------------

def engagedEvent(zone, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    def zoneApps = getAllChildZoneApps(zone)
    debugContext = """$debugContext
Engaged Event
engaged = ${zoneIsEngaged(zone, zoneApps)}
active = ${zoneIsActive(zone, zoneApps)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    zone.occupied()
    logDebug("$debugContext => occupied (engaged)")
}

def activeEvent(zone, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    def zoneApps = getAllChildZoneApps(zone)
    debugContext = """$debugContext
Active Event
engaged = ${zoneIsEngaged(zone, zoneApps)}
active = ${zoneIsActive(zone, zoneApps)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""
    
    if (zoneIsEngaged(zone, zoneApps)) {
        zone.occupied()
        logDebug("$debugContext => occupied (engaged)")
    } else {
        if (zone.currentValue("contact") == "open") {
            zone.occupied()
            logDebug("$debugContext => occupied (active)")
        } else {
            if (zone.currentValue("occupancy") == "vacant") {
                zone.checking()
                logDebug("$debugContext => checking (${checkingSeconds}s)")
                scheduleCheckingTimeout(zone)
            } else {
                zone.occupied()
                logDebug("$debugContext => occupied (active)")
            }
        }
    }
}

def inactiveEvent(zone, debugContext) {
    def zoneApps = getAllChildZoneApps(zone)
    debugContext = """$debugContext
Inactive Event
engaged = ${zoneIsEngaged(zone, zoneApps)}
active = ${zoneIsActive(zone, zoneApps)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zoneIsEngaged(zone, zoneApps)) {
        logDebug("$debugContext => ignored (engaged)")
    } else if (zoneIsActive(zone, zoneApps)) {
        logDebug("$debugContext => ignored (active)")
    } else {
        if (zone.currentValue("contact") == "open") {
            if (zone.currentValue("occupancy") == "occupied") {
                zone.checking()
                logDebug("$debugContext => checking (${checkingSeconds}s)")
                scheduleCheckingTimeout(zone)
            } else {
                logDebug("$debugContext => ignored (not occupied)")
            }
        } else {
            logDebug("$debugContext => ignored (closed)")
        }
    }
}

def momentaryEvent(zone, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    def zoneApps = getAllChildZoneApps(zone)
    debugContext = """$debugContext
Momentary Event
engaged = ${zoneIsEngaged(zone, zoneApps)}
active = ${zoneIsActive(zone, zoneApps)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""
    
    if (zoneIsEngaged(zone, zoneApps)) {
        zone.occupied()
        logDebug("$debugContext => occupied (engaged)")
    } else if (zoneIsActive(zone, zoneApps)) {
        zone.occupied()
        logDebug("$debugContext => occupied (active)")
    } else {
        if (zone.currentValue("contact") == "open") {
            zone.checking()
            logDebug("$debugContext => checking (${checkingSeconds}s)")
            scheduleCheckingTimeout(zone)
        } else {
            zone.occupied()
            logDebug("$debugContext => occupied (closed)")
        }
    }
}

def closedEvent(zone, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    def zoneApps = getAllChildZoneApps(zone)
    debugContext = """$debugContext
Closed Event
engaged = ${zoneIsEngaged(zone, zoneApps)}
active = ${zoneIsActive(zone, zoneApps)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zoneIsEngaged(zone, zoneApps)) {
        logDebug("$debugContext => ignored (engaged)")
    } else {
        zone.checking()
        logDebug("$debugContext => checking (${checkingSeconds}s)")
        scheduleCheckingTimeout(zone)
    }
}

//-----------------------------------------

def scheduleCheckingTimeout(zone) {
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
    def zoneApps = getAllChildZoneApps(zone)
    def debugContext = """Zone ${app.label} - Motion Check (${motionSeconds}s)
engaged = ${zoneIsEngaged(zone, zoneApps)}
active = ${zoneIsActive(zone, zoneApps)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""
    if (motionIsActive(zone, zoneApps)) {
        unschedule("checkingTimeout")
        zone.occupied()
        logDebug("$debugContext => occupied")
    } else {
        logDebug("$debugContext => ignored (no motion)")
    }
}

def checkingTimeout() {
    def zone = getZoneDevice()
    def zoneApps = getAllChildZoneApps(zone)
    def debugContext = """Zone ${app.label} - Checking Timeout (${checkingSeconds}s)
engaged = ${zoneIsEngaged(zone, zoneApps)}
active = ${zoneIsActive(zone, zoneApps)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zoneIsEngaged(zone, zoneApps)) {
        zone.occupied()
        logDebug("$debugContext => occupied (engaged)")
    } else if (zoneIsActive(zone, zoneApps)) {
        zone.occupied()
        logDebug("$debugContext => occupied (active)")
    } else {
        zone.vacant()
        logDebug("$debugContext => vacant")
    }
}

//-----------------------------------------

def zoneIsEngaged(zone, zoneApps) {
    if (zoneType == "Automated") {
        def allEngagedDoors_Open = getAllDevices(zone, zoneApps, "engagedDoors_Open")
        if (allEngagedDoors_Open) {
            for (engagedDoor in allEngagedDoors_Open.values()) {
                if (engagedDoor.currentValue("contact") == "open") {
                    return "$engagedDoor is open"
                }
            }
        }
        
        def allEngagedDoors_Closed = getAllDevices(zone, zoneApps, "engagedDoors_Closed")
        if (allEngagedDoors_Closed) {
            for (engagedDoor in allEngagedDoors_Closed.values()) {
                if (engagedDoor.currentValue("contact") == "closed") {
                    return "$engagedDoor is closed"
                }
            }
        }
        
        def allEngagedSwitches_On = getAllDevices(zone, zoneApps, "engagedSwitches_On")
        if (allEngagedSwitches_On) {
            for (engagedSwitch in allEngagedSwitches_On.values()) {
                if (engagedSwitch.currentValue("switch") == "on") {
                    return "$engagedSwitch is on"
                }
            }
        }
        
        def allEngagedSwitches_Off = getAllDevices(zone, zoneApps, "engagedSwitches_Off")
        if (allEngagedSwitches_Off) {
            for (engagedSwitch in allEngagedSwitches_Off.values()) {
                if (engagedSwitch.currentValue("switch") == "off") {
                    return "$engagedSwitch is off"
                }
            }
        }
        
        def allEngagedLocks_Unlocked = getAllDevices(zone, zoneApps, "engagedLocks_Unlocked")
        if (allEngagedLocks_Unlocked) {
            for (engagedLock in allEngagedLocks_Unlocked.values()) {
                if (engagedLock.currentValue("lock") == "unlocked") {
                    return "$engagedLock is unlocked"
                }
            }
        }
        
        def allEngagedLocks_Locked = getAllDevices(zone, zoneApps, "engagedLocks_Locked")
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

def zoneIsActive(zone, zoneApps) {
    if (zoneType == "Automated") {
        def activeMotionSensor = motionIsActive(zone, zoneApps)
        if (activeMotionSensor) {
            return activeMotionSensor
        }
        
        def allPresenceSensors = getAllDevices(zone, zoneApps, "presenceSensors")
        if (allPresenceSensors) {
            for (presenceSensor in allPresenceSensors.values()) {
                if (presenceSensor.currentValue("presence") == "present") {
                    return "$presenceSensor is present"
                }
            }
        }
        
        def allAccelerationSensors = getAllDevices(zone, zoneApps, "accelerationSensors")
        if (allAccelerationSensors) {
            for (accelerationSensor in allAccelerationSensors.values()) {
                if (accelerationSensor.currentValue("acceleration") == "active") {
                    return "$accelerationSensor is active"
                }
            }
        }
        
        if (childZones) {
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

def motionIsActive(zone, zoneApps) {
    if (zoneType == "Automated") {
        def allMotionSensors = getAllDevices(zone, zoneApps, "motionSensors")
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

def zoneIsOpen(zone) {
    if (zoneType == "Automated") {
        if (entryDoors) {
            for (entryDoor in entryDoors) {
                if (entryDoor.id != zone.id) {
                    if (entryDoor.currentValue("contact") == "open") {
                        return "$entryDoor is open"
                    }
                }
            }
            return false
        }
    }
    
    return "No entry doors"
}