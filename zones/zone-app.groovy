/**
 *  Zone App
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
 
String getName() { return "Zone App" }
String getVersionNum() { return "10.0.0-beta.6" }
String getVersionLabel() { return "${getName()}, version ${getVersionNum()}" }

#include mikee385.debug-library

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
                input "checkingSeconds", "number", title: "CHECKING - Time that zone will stay active after momentary events (seconds)", required: true, defaultValue: 60
                input "questionableSeconds", "number", title: "QUESTIONABLE - Time that zone will check for additional activity after receiving an unexpected event when closed and vacant (seconds)", required: true, defaultValue: 60
                input "closedSeconds", "number", title: "CLOSED - Time that zone will check for activity after all entry doors have been closed (seconds)", required: true, defaultValue: 300
            }
        } else if (zoneType == "Manual") {
            section {
                input "checkingSeconds", "number", title: "CHECKING - Time that zone will stay active after momentary events (seconds)", required: true, defaultValue: 60
                paragraph "Manual Zones will not automatically transition to the checking state, but it can be triggered manually from the device page or by other automations (e.g. Rule Machine)."
            }
        }
        
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
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
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Initial"""
    )
    
    if (zoneType == "Automated") {
        if (entryDoors) {
            if (zoneIsOpen(zone)) {
                setContactToOpen(zone, debugContext)
            } else {
                setContactToClosed(zone, debugContext)
            }
        } else {
            setContactToOpen(zone, debugContext)
        }
        
        def allChildZones = getDevices(zone, "childZones")
        def allEntryDoors = getDevices(zone, "entryDoors")
        
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
        
        state.devices = [:]
        
        for (entryDoor in allEntryDoors.values()) {
            if (allEngagedDoors_Open.containsKey(entryDoor.id) && allEngagedDoors_Closed.containsKey(entryDoor.id)) {
                addDevice(entryDoor, "engaged", "active")
            
                subscribe(entryDoor, "contact.open", openEngagedHandler)
                subscribe(entryDoor, "contact.closed", closedEngagedHandler)
            } else if (allEngagedDoors_Open.containsKey(entryDoor.id)) {
                addDevice(entryDoor, "engaged", entryDoor.currentValue("contact") == "open" ? "active" : "idle")
            
                subscribe(entryDoor, "contact.open", openEngagedHandler)
                subscribe(entryDoor, "contact.closed", closedDisengagedHandler)
            } else if (allEngagedDoors_Closed.containsKey(entryDoor.id)) {
                addDevice(entryDoor, "engaged", entryDoor.currentValue("contact") == "closed" ? "active" : "idle")
            
                subscribe(entryDoor, "contact.open", openDisengagedHandler)
                subscribe(entryDoor, "contact.closed", closedEngagedHandler)
            } else {
                addDevice(entryDoor, "momentary", "idle")
                
                subscribe(entryDoor, "contact.open", openMomentaryHandler)
                subscribe(entryDoor, "contact.closed", closedMomentaryHandler)
            }
        }
        
        for (presenceSensor in allPresenceSensors.values()) {
            addDevice(presenceSensor, "active", presenceSensor.currentValue("presence") == "present" ? "active" : "idle")
            
            subscribe(presenceSensor, "presence.present", activeDeviceHandler)
            subscribe(presenceSensor, "presence.not present", inactiveDeviceHandler)
        }
        
        for (motionSensor in allMotionSensors.values()) {
            addDevice(motionSensor, "active", motionSensor.currentValue("motion") == "active" ? "active" : "idle")
            
            subscribe(motionSensor, "motion.active", activeDeviceHandler)
            subscribe(motionSensor, "motion.inactive", inactiveDeviceHandler)
        }
        
        for (accelerationSensor in allAccelerationSensors.values()) {
            addDevice(accelerationSensor, "active", accelerationSensor.currentValue("acceleration") == "active" ? "active" : "idle")
            
            subscribe(accelerationSensor, "acceleration.active", activeDeviceHandler)
            subscribe(accelerationSensor, "acceleration.inactive", inactiveDeviceHandler)
        }
        
        for (engagedDoor in allEngagedDoors_Open.values()) {
            if (!allEntryDoors.containsKey(engagedDoor.id)) {
                addDevice(engagedDoor, "engaged", engagedDoor.currentValue("contact") == "open" ? "active" : "idle")
                
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
                addDevice(engagedDoor, "engaged", engagedDoor.currentValue("contact") == "closed" ? "active" : "idle")
                
                subscribe(engagedDoor, "contact.closed", engagedDeviceHandler)
                subscribe(engagedDoor, "contact.open", disengagedDeviceHandler)
            }
        }
        
        for (engagedSwitch in allEngagedSwitches_On.values()) {
            addDevice(engagedSwitch, "engaged", engagedSwitch.currentValue("switch") == "on" ? "active" : "idle")
            
            subscribe(engagedSwitch, "switch.on", engagedDeviceHandler)
            
            if (allEngagedSwitches_Off.containsKey(engagedSwitch.id)) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
            } else {
                subscribe(engagedSwitch, "switch.off", disengagedDeviceHandler)
            }
        }
        
        for (engagedSwitch in allEngagedSwitches_Off.values()) {
            addDevice(engagedSwitch, "engaged", engagedSwitch.currentValue("switch") == "off" ? "active" : "idle")
            
            if (!allEngagedSwitches_On.containsKey(engagedSwitch.id)) {
                subscribe(engagedSwitch, "switch.off", engagedDeviceHandler)
                subscribe(engagedSwitch, "switch.on", disengagedDeviceHandler)
            }
        }
        
        for (engagedLock in allEngagedLocks_Unlocked.values()) {
            addDevice(engagedLock, "engaged", engagedLock.currentValue("lock") == "unlocked" ? "active" : "idle")
            
            subscribe(engagedLock, "lock.unlocked", engagedDeviceHandler)
            
            if (allEngagedLocks_Locked.containsKey(engagedLock.id)) {
                subscribe(engagedLock, "lock.locked", engagedDeviceHandler)
            } else {
                subscribe(engagedLock, "lock.locked", disengagedDeviceHandler)
            }
        }
        
        for (engagedLock in allEngagedLocks_Locked.values()) {
            addDevice(engagedLock, "engaged", engagedLock.currentValue("lock") == "locked" ? "active" : "idle")
            
            if (!allEngagedLocks_Unlocked.containsKey(engagedLock.id)) {
                subscribe(engagedLock, "lock.locked", engagedDeviceHandler)
                subscribe(engagedLock, "lock.unlocked", disengagedDeviceHandler)
            }
        }
        
        for (momentaryDoor in allMomentaryDoors.values()) {
            if (!allEntryDoors.containsKey(momentaryDoor.id) && !allEngagedDoors_Open.containsKey(momentaryDoor.id) && !allEngagedDoors_Closed.containsKey(momentaryDoor.id)) {
                addDevice(momentaryDoor, "momentary", "idle")
                
                subscribe(momentaryDoor, "contact", momentaryDeviceHandler)
            }
        }
        
        for (momentaryButton in allMomentaryButtons.values()) {
            addDevice(momentaryButton, "momentary", "idle")
            
            subscribe(momentaryButton, "pushed", momentaryDeviceHandler)
            subscribe(momentaryButton, "doubleTapped", momentaryDeviceHandler)
            subscribe(momentaryButton, "held", momentaryDeviceHandler)
            subscribe(momentaryButton, "released", momentaryDeviceHandler)
        }
        
        for (momentarySwitch in allMomentarySwitches.values()) {
            if (!allEngagedSwitches_On.containsKey(momentarySwitch.id) && !allEngagedSwitches_Off.containsKey(momentarySwitch.id)) {
                addDevice(momentarySwitch, "momentary", "idle")
                
                subscribe(momentarySwitch, "switch", momentaryDeviceHandler)
            }
        }
        
        for (momentaryLock in allMomentaryLocks.values()) {
            if (!allEngagedLocks_Unlocked.containsKey(momentaryLock.id) && !allEngagedLocks_Locked.containsKey(momentaryLock.id)) {
                addDevice(momentaryLock, "momentary", "idle")
                
                subscribe(momentaryLock, "lock", momentaryDeviceHandler)
            }
        }
        
        for (childZone in allChildZones.values()) {
            if (childZone.id != zone.id) {
                addDevice(childZone, "zone", childZone.currentValue("activity"))  
                
                subscribe(childZone, "activity", childZoneActivityHandler)
                subscribe(childZone, "event", childZoneEventHandler)
            }
        }
        
        setActivityFromDevices(zone, debugContext)
    
    } else if (zoneType != "Manual") {
        log.error "Unknown zone type: $zoneType"
    }
    
    logDebug(debugContext)
}

def uninstalled() {
    parent.deleteZoneDevice(app.id)
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

def addDevice(device, type, activity) {
    state.devices[device.id] = [
        id: device.id,
        name: device.displayName,
        type: type,
        activity: activity,
        timerId: null
    ]
}

//-----------------------------------------

def setDeviceToActive(evt) {
    state.devices["${evt.deviceId}"].activity = "active"
    state.devices["${evt.deviceId}"].timerId = null
}

def setDeviceToChecking(evt) {
    state.devices["${evt.deviceId}"].activity = "checking"
    state.devices["${evt.deviceId}"].timerId = "${evt.id}"
    runIn(checkingSeconds, checkingTimer, [overwrite: false, data: [id: "${evt.id}", deviceId: "${evt.deviceId}"]])
}

def setDeviceToQuestionable(evt) {
    state.devices["${evt.deviceId}"].activity = "questionable"
    state.devices["${evt.deviceId}"].timerId = "${evt.id}"
    runIn(questionableSeconds, questionableTimer, [overwrite: false, data: [id: "${evt.id}", deviceId: "${evt.deviceId}"]])
}

def setDeviceToIdle(evt) {
    state.devices["${evt.deviceId}"].activity = "idle"
    state.devices["${evt.deviceId}"].timerId = null
}

//-----------------------------------------

def setContactToOpen(zone, debugContext) {
    zone.sendEvent(name: "contact", value: "open")
    
    debugContext.append("""
contact => open"""
    )
}

def setContactToClosed(zone, debugContext) {
    zone.sendEvent(name: "contact", value: "closed")
    
    debugContext.append("""
contact => closed"""
    )
}

def setActivityToActive(zone, debugContext) {
    zone.sendEvent(name: "activity", value: "active")
    
    debugContext.append("""
activity => active"""
    )
}

def setActivityToChecking(zone, debugContext) {
    zone.sendEvent(name: "activity", value: "checking")
    
    debugContext.append("""
activity => checking"""
    )
}

def setActivityToQuestionable(zone, debugContext) {
    zone.sendEvent(name: "activity", value: "questionable")
    
    debugContext.append("""
activity => questionable"""
    )
}

def setActivityToIdle(zone, debugContext) {
    zone.sendEvent(name: "activity", value: "idle")
    
    debugContext.append("""
activity => idle"""
    )
}

def setActivityFromDevices(zone, debugContext) {
    def isActive = false
    def isChecking = false
    def isQuestionable = false
    
    for (device in state.devices.values()) {
        if (device.activity == "active") {
            isActive = true
        } else if (device.activity == "checking") {
            isChecking = true
        } else if (device.activity == "questionable") {
            isQuestionable = true
        }
    }
    
    if (isActive) {
        setActivityToActive(zone, debugContext)
    } else if (isChecking) {
        setActivityToChecking(zone, debugContext)
    } else if (isQuestionable) {
        setActivityToQuestionable(zone, debugContext)
    } else {
        setActivityToIdle(zone, debugContext)
    }
}

def setEvent(zone, type, debugContext) {
    zone.sendEvent(name: "event", value: type, isStateChange: true)
    
    debugContext.append("""
event => ${type}"""
    )
}

//-----------------------------------------

def engagedDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Engaged Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    cancelClosedTimer()
    setDeviceToActive(evt)
    setActivityToActive(zone, debugContext)
    setEvent(zone, "engaged", debugContext)
    
    logDebug(debugContext)
}

def disengagedDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Disengaged Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    cancelClosedTimer()
    setDeviceToChecking(evt)
    if (zone.currentValue("contact") == "closed") {
        setActivityToActive(zone, debugContext)
        setEvent(zone, "engaged", debugContext)
    } else {
        setActivityFromDevices(zone, debugContext)
        setEvent(zone, "disengaged", debugContext)
    }
    
    logDebug(debugContext)
}

def activeDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Active Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    cancelClosedTimer()
    if (zone.currentValue("contact") == "closed") {
        if (zone.currentValue("activity") == "idle") {
            setDeviceToQuestionable(evt)
            setActivityFromDevices(zone, debugContext)
            setEvent(zone, "questionable", debugContext)
        } else {
            setDeviceToActive(evt)
            setActivityToActive(zone, debugContext)
            setEvent(zone, "engaged", debugContext)
        }
    } else {
        setDeviceToActive(evt)
        setActivityToActive(zone, debugContext)
        setEvent(zone, "active", debugContext)
    }
    
    logDebug(debugContext)
}

def inactiveDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Inactive Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    setDeviceToChecking(evt)
    if (!(zone.currentValue("contact") == "closed" && zone.currentValue("activity") == "active")) {
        setActivityFromDevices(zone, debugContext)
    }
    setEvent(zone, "inactive", debugContext)
    
    logDebug(debugContext)
}

def momentaryDeviceHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Momentary Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    cancelClosedTimer()
    if (zone.currentValue("contact") == "closed") {
        if (zone.currentValue("activity") == "idle") {
            setDeviceToQuestionable(evt)
            setActivityFromDevices(zone, debugContext)
            setEvent(zone, "questionable", debugContext)
        } else {
            setDeviceToChecking(evt)
            setActivityToActive(zone, debugContext)
            setEvent(zone, "engaged", debugContext)
        }
    } else {
        setDeviceToChecking(evt)
        setActivityFromDevices(zone, debugContext)
        setEvent(zone, "momentary", debugContext)
    }
    
    logDebug(debugContext)
}

//-----------------------------------------

def openEngagedHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Open, Engaged Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    setContactToOpen(zone, debugContext)
    
    cancelClosedTimer()
    setDeviceToActive(evt)
    setActivityToActive(zone, debugContext)
    setEvent(zone, "engaged", debugContext)
    
    logDebug(debugContext)
}

def openDisengagedHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Open, Disengaged Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    setContactToOpen(zone, debugContext)

    cancelClosedTimer()
    setDeviceToChecking(evt)
    setActivityFromDevices(zone, debugContext)
    setEvent(zone, "disengaged", debugContext)
    
    logDebug(debugContext)
}

def openMomentaryHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Open, Momentary Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    setContactToOpen(zone, debugContext)
    
    cancelClosedTimer()
    setDeviceToChecking(evt)
    setActivityFromDevices(zone, debugContext)
    setEvent(zone, "momentary", debugContext)
    
    logDebug(debugContext)
}

def closedEngagedHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Closed, Engaged Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    if (!zoneIsOpen(zone)) {
        setContactToClosed(zone, debugContext)
    }

    cancelClosedTimer()
    setDeviceToActive(evt)
    setActivityToActive(zone, debugContext)
    setEvent(zone, "engaged", debugContext)
    
    logDebug(debugContext)
}

def closedDisengagedHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Closed, Disengaged Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    if (!zoneIsOpen(zone)) {
        setContactToClosed(zone, debugContext)
        
        cancelClosedTimer()
        setDeviceToChecking(evt)
        setActivityToChecking(zone, debugContext)
        setEvent(zone, "disengaged", debugContext)
        startClosedTimer()
    } else {
        cancelClosedTimer()
        setDeviceToChecking(evt)
        setActivityFromDevices(zone, debugContext)
        setEvent(zone, "disengaged", debugContext)
    }
    
    logDebug(debugContext)
}

def closedMomentaryHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Closed, Momentary Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    if (!zoneIsOpen(zone)) {
        setContactToClosed(zone, debugContext)
        
        cancelClosedTimer()
        setDeviceToChecking(evt)
        setActivityToChecking(zone, debugContext)
        setEvent(zone, "momentary", debugContext)
        startClosedTimer()
    } else {
        cancelClosedTimer()
        setDeviceToChecking(evt)
        setActivityFromDevices(zone, debugContext)
        setEvent(zone, "momentary", debugContext)
    }
    
    logDebug(debugContext)
}

//-----------------------------------------

def childZoneActivityHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Child Zone Activity Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    state.devices["${evt.deviceId}"].activity = evt.value
    if (!(zone.currentValue("contact") == "closed" && zone.currentValue("activity") == "active")) {
        setActivityFromDevices(zone, debugContext)
    } 
    
    logDebug(debugContext)
}

def childZoneEventHandler(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Child Zone Event Handler
${evt.device} is ${evt.value}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    if (evt.value == "engaged"
     || evt.value == "disengaged"
     || evt.value == "active"
     || evt.value == "momentary") {
        cancelClosedTimer()
    }
    setEvent(zone, evt.value, debugContext)
    
    logDebug(debugContext)
}

//-----------------------------------------

def checkingTimer(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Checking Timer
${state.devices[evt.deviceId].name}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    if (state.devices["${evt.deviceId}"].timerId == "${evt.id}") {
        setDeviceToIdle(evt)
        if (!(zone.currentValue("contact") == "closed" && zone.currentValue("activity") == "active")) {
            setActivityFromDevices(zone, debugContext)
        } 
        setEvent(zone, "idle", debugContext)
    } else {
        debugContext.append("""
Expired""")
    }
    
    logDebug(debugContext)
}

def questionableTimer(evt) {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Questionable Timer
${state.devices[evt.deviceId].name}
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    if (state.devices["${evt.deviceId}"].timerId == "${evt.id}") {
        setDeviceToIdle(evt)
        if (!(zone.currentValue("contact") == "closed" && zone.currentValue("activity") == "active")) {
            setActivityFromDevices(zone, debugContext)
        } 
        setEvent(zone, "idle", debugContext)
    } else {
        debugContext.append("""
Expired""")
    }
    
    logDebug(debugContext)
}

def closedTimer() {
    def zone = getZoneDevice()
    def debugContext = new StringBuilder(
"""Zone ${app.label}
Closed Timer
contact: ${zone.currentValue('contact')}
activity: ${zone.currentValue('activity')}"""
    )

    def anyDeviceActive = false
    for (device in state.devices.values()) {
        if (device.activity == "active") {
            anyDeviceActive = true
            break 
        }
    }
    
    if (anyDeviceActive) {
        setActivityToActive(zone, debugContext)
        setEvent(zone, "engaged", debugContext)
    } else {
        setActivityToIdle(zone, debugContext)
        setEvent(zone, "idle", debugContext)
    }
    
    logDebug(debugContext)
}

def startClosedTimer() {
    runIn(closedSeconds, closedTimer)
}

def cancelClosedTimer() {
    unschedule("closedTimer")
}

//-----------------------------------------

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