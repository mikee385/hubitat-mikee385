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
String getVersionNum() { return "9.4.0" }
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
    parent.addZoneDevice(app.id, app.label)
    
    def zone = getZoneDevice()
    subscribe(zone, "occupancy", occupancyHandler)
    
    if (zoneType == "Automated") {
        if (entryDoors) {
            if (zoneIsOpen(zone)) {
                zone.open()
            } else {
                zone.close()
            }
        }
        def data = [:]
        if (zoneIsEngaged(zone)) {
            setToEngaged(zone, data, "initial")
        } else if (zoneIsActive(zone)) {
            setToActive(zone, data, "initial")
        } else {
            setToVacant(zone, data, "initial")
        }
        
        def allChildZones = getDevices(zone, "childZones")
        def allEntryDoors = getDevices(zone, "entryDoors")
        state.entryDoorIds = allEntryDoors.collect{ it.id }
    
        def allPresenceSensors = getDevices(zone, "presenceSensors")
        def allMotionSensors = getDevices(zone, "motionSensors")
        def allAccelerationSensors = getDevices(zone, "accelerationSensors")
        
        def allEngagedDoors_Open = getDevices(zone, "engagedDoors_Open")
        def allEngagedDoors_Closed = getDevices(zone, "engagedDoors_Closed")
        def allEngagedSwitches_On = getDevices(zone, "engagedSwitches_On")
        def allEngagedSwitches_Off = getDevices(zone, "engagedSwitches_Off")
        def allEngagedLocks_Unlocked = getDevices(zone, "engagedLocks_Unlocked")
        def allEngagedLocks_Locked = getDevices(zone, "engagedLocks_Locked")
        
        def allMomentaryDoors = getDevices(zone, "momentaryDoors")
        def allMomentaryButtons = getDevices(zone, "momentaryButtons")
        def allMomentarySwitches = getDevices(zone, "momentarySwitches")
        def allMomentaryLocks = getDevices(zone, "momentaryLocks")
        
        for (entryDoor in allEntryDoors.values()) {
            if (allEngagedDoors_Open.containsKey(entryDoor.id) && allEngagedDoors_Closed.containsKey(entryDoor.id)) {
                subscribe(entryDoor, "contact.open", openEngagedHandler)
                subscribe(entryDoor, "contact.closed", closedEngagedHandler)
            } else if (allEngagedDoors_Open.containsKey(entryDoor.id)) {
                subscribe(entryDoor, "contact.open", openEngagedHandler)
                subscribe(entryDoor, "contact.closed", closedDisengagedHandler)
            } else if (allEngagedDoors_Closed.containsKey(entryDoor.id)) {
                subscribe(entryDoor, "contact.open", openDisengagedHandler)
                subscribe(entryDoor, "contact.closed", closedEngagedHandler)
            } else {
                subscribe(entryDoor, "contact.open", openMomentaryHandler)
                subscribe(entryDoor, "contact.closed", closedMomentaryHandler)
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
                    subscribe(engagedDoor, "contact.closed", disengagedDeviceHandler)
                }
            }
        }
        
        for (engagedDoor in allEngagedDoors_Closed.values()) {
            if (!allEntryDoors.containsKey(engagedDoor.id) && !allEngagedDoors_Open.containsKey(engagedDoor.id)) { 
                subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
                subscribe(engagedDoor, "contact.open", disengagedDeviceHandler)
            }
        }
        
        for (engagedSwitch in allEngagedSwitches_On.values()) {
            subscribe(engagedSwitch, "switch.on", engagedDeviceHandler)
            
            if (allEngagedSwitches_Off.containsKey(engagedSwitch.id)) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
            } else {
                subscribe(engagedSwitch, "switch.off", disengagedDeviceHandler)
            }
        }
        
        for (engagedSwitch in allEngagedSwitches_Off.values()) {
            if (!allEngagedSwitches_On.containsKey(engagedSwitch.id)) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
                subscribe(engagedSwitch, "switch.on", disengagedDeviceHandler)
            }
        }
        
        for (engagedLock in allEngagedLocks_Unlocked.values()) {
            subscribe(engagedLock, "lock.unlocked", engagedDeviceHandler)
            
            if (allEngagedLocks_Locked.containsKey(engagedLock.id)) {
                subscribe(engagedLock, "lock.locked", engagedDeviceHandler)
            } else {
                subscribe(engagedLock, "lock.locked", disengagedDeviceHandler)
            }
        }
        
        for (engagedLock in allEngagedLocks_Locked.values()) {
            if (!allEngagedLocks_Unlocked.containsKey(engagedLock.id)) {
                subscribe(engagedLock, "lock.locked", engagedDeviceHandler)
                subscribe(engagedLock, "lock.unlocked", disengagedDeviceHandler)
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
                subscribe(childZone, "occupancy", childZoneHandler)
            }
        }
    
    } else if (zoneType != "Manual") {
        log.error "Unknown zone type: $zoneType"
    }
    
    logDebug("""Zone ${app.label} - Initial
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
""")
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

def openEngagedHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""

    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    if (zone.currentValue("contact") == "closed") {
        zone.open()
    }
    engagedEvent(zone, data, debugContext)
}

def openDisengagedHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""

    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    if (zone.currentValue("contact") == "closed") {
        zone.open()
        openDisengagedEvent(zone, data, debugContext)
    } else {
        disengagedEvent(zone, data, debugContext)
    }
}

def openMomentaryHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""

    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    if (zone.currentValue("contact") == "closed") {
        zone.open()
        if (zone.currentValue("occupancy") == "engaged") {
            openDisengagedEvent(zone, data, debugContext)
        } else {
            openMomentaryEvent(zone, data, debugContext)
        }
    } else {
        momentaryEvent(zone, data, debugContext)
    }
}

def closedEngagedHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""

    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    if (!zoneIsOpen(zone)) {
        zone.close()
    }
    engagedEvent(zone, data, debugContext)
}

def closedDisengagedHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""

    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    if (!zoneIsOpen(zone)) {
        zone.close()
        closedDisengagedEvent(zone, data, debugContext)
    } else {
        disengagedEvent(zone, data, debugContext)
    }
}

def closedMomentaryHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Entry Door
${evt.device} is ${evt.value}"""

    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    if (!zoneIsOpen(zone)) {
        zone.close()
        closedMomentaryEvent(zone, data, debugContext)
    } else {
        momentaryEvent(zone, data, debugContext)
    }
}

//-----------------------------------------

def engagedDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Engaged Device
${evt.device} is ${evt.value}"""
    
    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    engagedEvent(zone, data, debugContext)
}

def disengagedDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Disengaged Device
${evt.device} is ${evt.value}"""

    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    disengagedEvent(zone, data, debugContext)
}

def activeDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Active Device
${evt.device} is ${evt.value}"""
    
    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    activeEvent(zone, data, debugContext)
}

def inactiveDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Inactive Device
${evt.device} is ${evt.value}"""
    
    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    inactiveEvent(zone, data, debugContext)
}

def momentaryDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Momentary Device
${evt.device} is ${evt.value}"""

    def data = [
        sourceId: evt.deviceId,
        sourceName: evt.displayName,
        sourceValue: evt.value
    ]
    momentaryEvent(zone, data, debugContext)
}

def childZoneHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Child Zone
${evt.device} is ${evt.value}"""

    def data = parseJson(evt.data)
    if (data.sourceId != null) {
        debugContext = """$debugContext
${data.sourceName} is ${data.sourceValue}"""
    } else {
        debugContext = """$debugContext
No source data"""
    }

    if (evt.value == "engaged") {
        engagedEvent(zone, data, debugContext)
    } else if (data.sourceId in state.entryDoorIds && data.sourceValue == "closed" && zone.currentValue("contact") == "closed") {
        log.warn "Duplicate closed event!"
        if (data.eventType == "disengaged") {
            closedDisengagedEvent(zone, data, debugContext)
        } else if (evt.value == "active") {
            activeEvent(zone, data, debugContext)
        } else if (data.eventType == "inactive") {
            inactiveEvent(zone, data, debugContext)
        } else if (data.eventType == "momentary") {
            closedMomentaryEvent(zone, data, debugContext)
        }
    } else if (data.eventType == "disengaged") {
        disengagedEvent(zone, data, debugContext)
    } else if (evt.value == "active") {
        activeEvent(zone, data, debugContext)
    } else if (data.eventType == "inactive") {
        inactiveEvent(zone, data, debugContext)
    } else if (data.eventType == "momentary") {
        momentaryEvent(zone, data, debugContext)
    } else if (evt.value != "vacant") {
        log.warn "Unknown event type: ${data.eventType} (${evt.value})"
    }
}

//-----------------------------------------

def engagedEvent(zone, data, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    debugContext = """$debugContext
Engaged Event
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    setToEngaged(zone, data, "engaged")
    logDebug("$debugContext => engaged")
}

def disengagedEvent(zone, data, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    debugContext = """$debugContext
Disengaged Event
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zoneIsEngaged(zone)) {
        logDebug("$debugContext => ignored (engaged)")
    } else if (zone.currentValue("contact") == "closed") {
        logDebug("$debugContext => ignored (closed)")
    } else if (zoneIsActive(zone)) {
        setToActive(zone, data, "disengaged")
        logDebug("$debugContext => active (open)")
    } else {
        setToChecking(zone, data, "disengaged")
        logDebug("$debugContext => checking (${checkingSeconds}s)")
        scheduleCheckingTimeout(zone)
    }
}

def activeEvent(zone, data, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    debugContext = """$debugContext
Active Event
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zone.currentValue("occupancy") == "engaged") {
        logDebug("$debugContext => ignored (engaged)")
    } else if (zone.currentValue("contact") == "closed") {
        setToEngaged(zone, data, "active")
        logDebug("$debugContext => engaged (closed)")
    } else {
        setToActive(zone, data, "active")
        logDebug("$debugContext => active (open)")
    }
}

def inactiveEvent(zone, data, debugContext) {
    debugContext = """$debugContext
Inactive Event
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zone.currentValue("occupancy") == "engaged") {
        logDebug("$debugContext => ignored (engaged)")
    } else if (zone.currentValue("contact") == "closed") {
        logDebug("$debugContext => ignored (closed)")
    } else if (zoneIsActive(zone)) {
        logDebug("$debugContext => ignored (active)")
    } else if (zone.currentValue("occupancy") == "active") {
        setToChecking(zone, data, "inactive")
        logDebug("$debugContext => checking (${checkingSeconds}s)")
        scheduleCheckingTimeout(zone)
    } else {
        logDebug("$debugContext => ignored (${zone.currentValue('occupancy')})")
    }
}

def momentaryEvent(zone, data, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    debugContext = """$debugContext
Momentary Event
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zone.currentValue("occupancy") == "engaged") {
        logDebug("$debugContext => ignored (engaged)")
    } else if (zone.currentValue("contact") == "closed") {
        setToEngaged(zone, data, "momentary")
        logDebug("$debugContext => engaged (closed)")
    } else if (zone.currentValue("occupancy") == "active") {
        logDebug("$debugContext => ignored (active)")
    } else {
        setToChecking(zone, data, "momentary")
        logDebug("$debugContext => checking (${checkingSeconds}s)")
        scheduleCheckingTimeout(zone)
    }
}

//-----------------------------------------

def openDisengagedEvent(zone, data, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    debugContext = """$debugContext
Open, Disengaged Event
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zoneIsEngaged(zone)) {
        logDebug("$debugContext => ignored (engaged)")
    } else if (zoneIsActive(zone)) {
        setToActive(zone, data, "disengaged")
        logDebug("$debugContext => active")
    } else {
        setToChecking(zone, data, "disengaged")
        logDebug("$debugContext => checking (${checkingSeconds}s)")
        scheduleCheckingTimeout(zone)
    }
}

def openMomentaryEvent(zone, data, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    debugContext = """$debugContext
Open, Momentary Event
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zone.currentValue("occupancy") == "engaged") {
        logDebug("$debugContext => ignored (engaged)")
    } else if (zone.currentValue("occupancy") == "active") {
        logDebug("$debugContext => ignored (active)")
    } else {
        setToChecking(zone, data, "momentary")
        logDebug("$debugContext => checking (${checkingSeconds}s)")
        scheduleCheckingTimeout(zone)
    }
}

def closedDisengagedEvent(zone, data, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    debugContext = """$debugContext
Closed, Disengaged Event
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zoneIsEngaged(zone)) {
        logDebug("$debugContext => ignored (engaged)")
    } else {
        setToChecking(zone, data, "disengaged")
        logDebug("$debugContext => checking (${checkingSeconds}s)")
        scheduleCheckingTimeout(zone)
    }
}

def closedMomentaryEvent(zone, data, debugContext) {
    unschedule("checkForSustainedMotion")
    unschedule("checkingTimeout")
    
    debugContext = """$debugContext
Closed, Momentary Event
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    if (zone.currentValue("occupancy") == "engaged") {
        logDebug("$debugContext => ignored (engaged)")
    } else {
        setToChecking(zone, data, "momentary")
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
    def debugContext = """Zone ${app.label} - Motion Check (${motionSeconds}s)
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    def data = [:]
    if (motionIsActive(zone)) {
        unschedule("checkingTimeout")
        setToEngaged(zone, data, "motionCheck")
        logDebug("$debugContext => engaged (motion)")
    } else {
        logDebug("$debugContext => ignored (no motion)")
    }
}

def checkingTimeout() {
    def zone = getZoneDevice()
    def debugContext = """Zone ${app.label} - Checking Timeout (${checkingSeconds}s)
engaged = ${zoneIsEngaged(zone)}
active = ${zoneIsActive(zone)}
contact = ${zone.currentValue('contact')}
occupancy = ${zone.currentValue('occupancy')}
"""

    def data = [:]
    if (zoneIsEngaged(zone)) {
        setToEngaged(zone, data, "checkingTimeout")
        logDebug("$debugContext => engaged")
    } else if (zoneIsActive(zone)) {
        if (zone.currentValue("contact") == "closed") {
            setToEngaged(zone, data, "checkingTimeout")
            logDebug("$debugContext => engaged (closed)")
        } else {
            setToActive(zone, data, "checkingTimeout")
            logDebug("$debugContext => active (open)")
        }
    } else {
        setToVacant(zone, data, "checkingTimeout")
        logDebug("$debugContext => vacant")
    }
}

//-----------------------------------------

def zoneIsEngaged(zone) {
    if (zoneType == "Automated") {
        def allEngagedDoors_Open = getDevices(zone, "engagedDoors_Open")
        if (allEngagedDoors_Open) {
            for (engagedDoor in allEngagedDoors_Open.values()) {
                if (engagedDoor.currentValue("contact") == "open") {
                    return "$engagedDoor is open"
                }
            }
        }
        
        def allEngagedDoors_Closed = getDevices(zone, "engagedDoors_Closed")
        if (allEngagedDoors_Closed) {
            for (engagedDoor in allEngagedDoors_Closed.values()) {
                if (engagedDoor.currentValue("contact") == "closed") {
                    return "$engagedDoor is closed"
                }
            }
        }
        
        def allEngagedSwitches_On = getDevices(zone, "engagedSwitches_On")
        if (allEngagedSwitches_On) {
            for (engagedSwitch in allEngagedSwitches_On.values()) {
                if (engagedSwitch.currentValue("switch") == "on") {
                    return "$engagedSwitch is on"
                }
            }
        }
        
        def allEngagedSwitches_Off = getDevices(zone, "engagedSwitches_Off")
        if (allEngagedSwitches_Off) {
            for (engagedSwitch in allEngagedSwitches_Off.values()) {
                if (engagedSwitch.currentValue("switch") == "off") {
                    return "$engagedSwitch is off"
                }
            }
        }
        
        def allEngagedLocks_Unlocked = getDevices(zone, "engagedLocks_Unlocked")
        if (allEngagedLocks_Unlocked) {
            for (engagedLock in allEngagedLocks_Unlocked.values()) {
                if (engagedLock.currentValue("lock") == "unlocked") {
                    return "$engagedLock is unlocked"
                }
            }
        }
        
        def allEngagedLocks_Locked = getDevices(zone, "engagedLocks_Locked")
        if (allEngagedLocks_Locked) {
            for (engagedLock in allEngagedLocks_Locked.values()) {
                if (engagedLock.currentValue("lock") == "locked") {
                    return "$engagedLock is locked"
                }
            }
        }
        
        if (childZones) {
            for (childZone in childZones) {
                if (childZone.id != zone.id) {
                    if (childZone.currentValue("occupancy") == "engaged") {
                        return "$childZone is engaged"
                    }
                }
            }
        }
    }
    
    return false
}

def zoneIsActive(zone) {
    if (zoneType == "Automated") {
        def activeMotionSensor = motionIsActive(zone)
        if (activeMotionSensor) {
            return activeMotionSensor
        }
        
        def allPresenceSensors = getDevices(zone, "presenceSensors")
        if (allPresenceSensors) {
            for (presenceSensor in allPresenceSensors.values()) {
                if (presenceSensor.currentValue("presence") == "present") {
                    return "$presenceSensor is present"
                }
            }
        }
        
        def allAccelerationSensors = getDevices(zone, "accelerationSensors")
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
                    if (childZone.currentValue("occupancy") == "active") {
                        return "$childZone is active"
                    }
                }
            }
        }
    }
        
    return false
}

def motionIsActive(zone) {
    if (zoneType == "Automated") {
        def allMotionSensors = getDevices(zone, "motionSensors")
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

//-----------------------------------------

def setToEngaged(zone, data, eventType) {
    data["eventType"] = eventType
    zone.sendEvent(name: "occupancy", value: "engaged", data: data)
    zone.sendEvent(name: "switch", value: "on")
}

def setToActive(zone, data, eventType) {
    data["eventType"] = eventType
    zone.sendEvent(name: "occupancy", value: "active", data: data)
    zone.sendEvent(name: "switch", value: "on")
}

def setToChecking(zone, data, eventType) {
    data["eventType"] = eventType
    zone.sendEvent(name: "occupancy", value: "checking", data: data)
    zone.sendEvent(name: "switch", value: "on")
}

def setToVacant(zone, data, eventType) {
    data["eventType"] = eventType
    zone.sendEvent(name: "occupancy", value: "vacant", data: data)
    zone.sendEvent(name: "switch", value: "off")
}