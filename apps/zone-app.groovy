/**
 *  Zone App
 *
 *  Copyright 2024 Michael Pierce
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
 
String getAppName() { return "Zone App" }
String getAppVersion() { return "1.0.0" }
String getAppTitle() { return "${getAppName()}, version ${getAppVersion()}" }

#include mikee385.debug-library

definition(
    name: "${getAppName()}",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Creates a Zone Device and automates the occupancy based on the devices and zones contained within it.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/zone-app.groovy"
) 

preferences {
    page(name: "settings", title: getAppTitle(), install: true, uninstall: true) {
        section {
            label title: "Zone Name", required: true
        }
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
            input "engagedTVs_Playing", "device.RokuTV", title: "TV is Playing", multiple: true, required: false
        }
        section {
            input "inactiveTimeout", "number", title: "Time that zone will stay active after devices are inactive (seconds)", required: true, defaultValue: 60
        }
        section {
            input "alertOpen", "bool", title: "Alert when Open?", required: true, defaultValue: false
            input "alertClosed", "bool", title: "Alert when Closed?", required: true, defaultValue: false
            input "alertOccupied", "bool", title: "Alert when Occupied?", required: true, defaultValue: false
            input "alertUnoccupied", "bool", title: "Alert when Unoccupied?", required: true, defaultValue: false
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
        }
    }
}

//-----------------------------------------

def installed() {
    state.wasp_in_box = false
    
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
    def zone = getZoneDevice()
    def contact = zone.currentValue("contact")
    def occupancy = zone.currentValue("occupancy")
    def debugContext = new StringBuilder(
"""Zone ${app.label} ($occupancy, $contact)
Initial"""
    )
    
    keysToRemove = []
    for (key in state.keySet()) {
        if (key.startsWith("active:") || key.startsWith("timer:")) {
            keysToRemove.add(key)
        }   
    }
    for (key in keysToRemove) {
        state.remove(key)
    }
    
    // Child Zones
    
    for (childZone in childZones) {
        if (childZone.id != zone.id) {
            state["name:${childZone.id}"] = "${childZone}"
            state["active:${childZone.id}"] = (childZone.currentValue("occupancy") == "occupied")
        
            subscribe(childZone, "occupancy.occupied", activeDeviceHandler)
            subscribe(childZone, "occupancy.unoccupied", inactiveZoneHandler)
        } 
    }
    
    // Active Devices
    
    for (presenceSensor in presenceSensors) {
        state["name:${presenceSensor.id}"] = "${presenceSensor}"
        state["active:${presenceSensor.id}"] = (presenceSensor.currentValue("presence") == "present")
        
        subscribe(presenceSensor, "presence.present", activeDeviceHandler)
        subscribe(presenceSensor, "presence.not present", inactiveDeviceHandler)
    }
    
    for (motionSensor in motionSensors) {
        state["name:${motionSensor.id}"] = "${motionSensor}"
        state["active:${motionSensor.id}"] = (motionSensor.currentValue("motion") == "active")
            
        subscribe(motionSensor, "motion.active", activeDeviceHandler)
        subscribe(motionSensor, "motion.inactive", inactiveDeviceHandler)
    }
        
    for (accelerationSensor in accelerationSensors) {
        state["name:${accelerationSensor.id}"] = "${accelerationSensor}"
        state["active:${accelerationSensor.id}"] = (accelerationSensor.currentValue("acceleration") == "active")
            
        subscribe(accelerationSensor, "acceleration.active", activeDeviceHandler)
        subscribe(accelerationSensor, "acceleration.inactive", inactiveDeviceHandler)
    }
    
    // Doors
    
    doors = [:]
    for (device in entryDoors) {
        subscribe(device, "contact", entryDoorHandler)
        
        doors[device.id] = [
            device: device,
            type: "momentary",
            active: false,
            open: momentaryDeviceHandler,
            closed: momentaryDeviceHandler
        ]
    }
    for (device in momentaryDoors) {
        doors[device.id] = [
            device: device,
            type: "momentary",
            active: false,
            open: momentaryDeviceHandler,
            closed: momentaryDeviceHandler
        ]
    }
    for (device in engagedDoors_Open) {
        doors[device.id] = [
            device: device,
            type: "engaged_Open",
            active: (device.currentValue("contact") == "open"),
            open: activeDeviceHandler,
            closed: momentaryDeviceHandler
        ]
    }
    for (device in engagedDoors_Closed) {
        if (doors.containsKey(device.id) && doors[device.id].type == "engaged_Open") {
            doors[device.id] = [
                device: device,
                type: "engaged_All",
                active: true,
                open: activeDeviceHandler,
                closed: activeDeviceHandler
            ]
        } else {
            doors[device.id] = [
                device: device,
                type: "engaged_Closed",
                active: (device.currentValue("contact") == "closed"),
                open: momentaryDeviceHandler,
                closed: activeDeviceHandler,
            ]
        }
    }
    for (device in doors.values()) {
        state["name:${device.device.id}"] = "${device.device}"
        state["active:${device.device.id}"] = device.active
        
        subscribe(device.device, "contact.open", device.open)
        subscribe(device.device, "contact.closed", device.closed)
    }
    
    // Switches
    
    switches = [:]
    for (device in momentarySwitches) {
        switches[device.id] = [
            device: device,
            type: "momentary",
            active: false,
            on: momentaryDeviceHandler,
            off: momentaryDeviceHandler
        ]
    }
    for (device in engagedSwitches_On) {
        switches[device.id] = [
            device: device,
            type: "engaged_On",
            active: (device.currentValue("switch") == "on"),
            on: activeDeviceHandler,
            off: momentaryDeviceHandler
        ]
    }
    for (device in engagedSwitches_Off) {
        if (switches.containsKey(device.id) && switches[device.id].type == "engaged_On") {
            switches[device.id] = [
                device: device,
                type: "engaged_All",
                active: true,
                on: activeDeviceHandler,
                off: activeDeviceHandler
            ]
        } else {
            switches[device.id] = [
                device: device,
                type: "engaged_Off",
                active: (device.currentValue("switch") == "off"),
                on: momentaryDeviceHandler,
                off: activeDeviceHandler,
            ]
        }
    }
    for (device in switches.values()) {
        state["name:${device.device.id}"] = "${device.device}"
        state["active:${device.device.id}"] = device.active
            
        subscribe(device.device, "switch.on", device.on)
        subscribe(device.device, "switch.off", device.off)
    }
    
    // Locks
    
    locks = [:]
    for (device in momentaryLocks) {
        locks[device.id] = [
            device: device,
            type: "momentary",
            active: false,
            unlocked: momentaryDeviceHandler,
            locked: momentaryDeviceHandler
        ]
    }
    for (device in engagedLocks_Unlocked) {
        locks[device.id] = [
            device: device,
            type: "engaged_Unlocked",
            active: (device.currentValue("lock") == "unlocked"),
            unlocked: activeDeviceHandler,
            locked: momentaryDeviceHandler
        ]
    }
    for (device in engagedLocks_Locked) {
        if (locks.containsKey(device.id) && locks[device.id].type == "engaged_Unlocked") {
            locks[device.id] = [
                device: device,
                type: "engaged_All",
                active: true,
                unlocked: activeDeviceHandler,
                locked: activeDeviceHandler
            ]
        } else {
            locks[device.id] = [
                device: device,
                type: "engaged_Locked",
                active: (device.currentValue("lock") == "locked"),
                unlocked: momentaryDeviceHandler,
                locked: activeDeviceHandler,
            ]
        }
    }
    for (device in locks.values()) {
        state["name:${device.device.id}"] = "${device.device}"
        state["active:${device.device.id}"] = device.active
            
        subscribe(device.device, "lock.unlocked", device.unlocked)
        subscribe(device.device, "lock.locked", device.locked)
    }
    
    // Buttons
    
    for (momentaryButton in momentaryButtons) {
        state["name:${momentaryButton.id}"] = "${momentaryButton}"
        state["active:${momentaryButton.id}"] = false
            
        subscribe(momentaryButton, "pushed", momentaryDeviceHandler)
        subscribe(momentaryButton, "doubleTapped", momentaryDeviceHandler)
        subscribe(momentaryButton, "held", momentaryDeviceHandler)
        subscribe(momentaryButton, "released", momentaryDeviceHandler)
    }
    
    // TVs
    
    for (engagedTV in engagedTVs_Playing) {
        state["name:${engagedTV.id}"] = "${engagedTV}"
        state["active:${engagedTV.id}"] = (engagedTV.currentValue("transportStatus") == "playing")
            
        subscribe(engagedTV, "transportStatus.playing", activeDeviceHandler)
        subscribe(engagedTV, "transportStatus.paused", inactiveDeviceHandler)
        subscribe(engagedTV, "transportStatus.stopped", inactiveDeviceHandler)
    }
    
    // Zone
    
    subscribe(zone, "contact", zoneHandler_ContactAlert)
    subscribe(zone, "occupancy", zoneHandler_OccupancyAlert)
    
    updateContact("initialize", debugContext)
    updateOccupancy("initialize", debugContext)
    
    if (zone.currentValue("contact") == "open" || zone.currentValue("occupancy") == "unoccupied") {
        state.wasp_in_box = false
    }
    
    logDebug(debugContext)
}

//-----------------------------------------

def getZoneDevice() {
    def childID = "zone:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("mikee385", "Zone Device", childID, 1234, [label: app.label, isComponent: false])
    }
    return child
}

//-----------------------------------------

def entryDoorHandler(evt) {
    if (evt.value == "open") {
        entryDoorHandler_(evt)
    } else {
        runInMillis(500, entryDoorHandler_, [data: [id: "${evt.id}", deviceId: "${evt.deviceId}", device: "${evt.device}", value: "${evt.value}"]])
    } 
}

def entryDoorHandler_(evt) {
    def zone = getZoneDevice()
    def contact = zone.currentValue("contact")
    def occupancy = zone.currentValue("occupancy")
    def debugContext = new StringBuilder(
"""Zone ${app.label} ($occupancy, $contact)
Active Entry Door
${evt.device} changed to ${evt.value}"""
    )
    def message = "${evt.device} is ${evt.value}"
    
    updateContact(message, debugContext)
    clearWaspInBox(debugContext)
    logDebug(debugContext)
}

//-----------------------------------------

def activeDeviceHandler(evt) {
    runInMillis(250, activeDeviceHandler_, [data: [id: "${evt.id}", deviceId: "${evt.deviceId}", device: "${evt.device}", value: "${evt.value}"]])
}

def activeDeviceHandler_(evt) {
    def zone = getZoneDevice()
    def contact = zone.currentValue("contact")
    def occupancy = zone.currentValue("occupancy")
    def debugContext = new StringBuilder(
"""Zone ${app.label} ($occupancy, $contact)
Active Device
${evt.device} changed to ${evt.value}"""
    )
    def message = "${evt.device} is ${evt.value}"
    
    state["name:${evt.deviceId}"] = "${evt.device}"
    state["active:${evt.deviceId}"] = true
    state["timer:${evt.deviceId}"] = null
    
    updateOccupancy(message, debugContext)
    detectWaspInBox(debugContext)
    logDebug(debugContext)
}

def momentaryDeviceHandler(evt) {
    runInMillis(250, momentaryDeviceHandler_, [data: [id: "${evt.id}", deviceId: "${evt.deviceId}", device: "${evt.device}", value: "${evt.value}"]])
}

def momentaryDeviceHandler_(evt) {
    def zone = getZoneDevice()
    def contact = zone.currentValue("contact")
    def occupancy = zone.currentValue("occupancy")
    def debugContext = new StringBuilder(
"""Zone ${app.label} ($occupancy, $contact)
Momentary Device
${evt.device} changed to ${evt.value}"""
    )
    def message = "${evt.device} is ${evt.value}"
    
    state["name:${evt.deviceId}"] = "${evt.device}"
    state["active:${evt.deviceId}"] = true
    state["timer:${evt.deviceId}"] = "${evt.id}"
    
    runIn(inactiveTimeout, inactiveTimerHandler, [overwrite: false, data: [id: "${evt.id}", deviceId: "${evt.deviceId}", device: "${evt.device}", value: "inactive"]])
    
    updateOccupancy(message, debugContext)
    detectWaspInBox(debugContext)
    logDebug(debugContext)
}

def inactiveDeviceHandler(evt) {
    def zone = getZoneDevice()
    def contact = zone.currentValue("contact")
    def occupancy = zone.currentValue("occupancy")
    def debugContext = new StringBuilder(
"""Zone ${app.label} ($occupancy, $contact)
Inactive Device
${evt.device} changed to ${evt.value}"""
    )
    def message = "${evt.device} is ${evt.value}"
    
    state["name:${evt.deviceId}"] = "${evt.device}"
    state["timer:${evt.deviceId}"] = "${evt.id}"
    
    runIn(inactiveTimeout, inactiveTimerHandler, [overwrite: false, data: [id: "${evt.id}", deviceId: "${evt.deviceId}", device: "${evt.device}", value: "${evt.value}"]])
    
    debugContext.append("""
Starting timer..."""
    )
    
    logDebug(debugContext)
}

def inactiveTimerHandler(evt) {
    def zone = getZoneDevice()
    def contact = zone.currentValue("contact")
    def occupancy = zone.currentValue("occupancy")
    def debugContext = new StringBuilder(
"""Zone ${app.label} ($occupancy, $contact)
Inactive Timer  
${evt.device} changed to ${evt.value}"""
    )
    def message = "${evt.device} is ${evt.value}"
    
    if (state["timer:${evt.deviceId}"] == "${evt.id}") {
        state["name:${evt.deviceId}"] = "${evt.device}"
        state["active:${evt.deviceId}"] = false
        state["timer:${evt.deviceId}"] = null
    
        updateOccupancy(message, debugContext)
    
    } else {
        debugContext.append("""
Ignored, because timer has expired"""
        )
    
    }
    
    logDebug(debugContext)
}

def inactiveZoneHandler(evt) {
    def zone = getZoneDevice()
    def contact = zone.currentValue("contact")
    def occupancy = zone.currentValue("occupancy")
    def debugContext = new StringBuilder(
"""Zone ${app.label} ($occupancy, $contact)
Inactive Child Zone  
${evt.device} changed to ${evt.value}"""
    )
    def message = "${evt.device} is ${evt.value}"
    
    state["name:${evt.deviceId}"] = "${evt.device}"
    state["active:${evt.deviceId}"] = false
    state["timer:${evt.deviceId}"] = null
    
    updateOccupancy(message, debugContext)
    
    logDebug(debugContext)
}

//-----------------------------------------

def updateContact(message, debugContext) {
    def zone = getZoneDevice()
    def contact = zone.currentValue("contact")

    anyDoorOpen = false
    for (door in entryDoors) {
        if (door.currentValue("contact") == "open") {
            anyDoorOpen = true
            debugContext.append("""
${door} is open"""
            )
        }   
    }

    if (!entryDoors || anyDoorOpen) {
        if (contact == "closed") {
            debugContext.append("""
Changing to open"""
            )
        }
        zone.sendEvent(name: "contact", value: "open", descriptionText: message)
    
    } else {
        debugContext.append("""
All doors are closed"""
        )
        if (contact == "open") {
            debugContext.append("""
Changing to closed"""
            )
        }
        zone.sendEvent(name: "contact", value: "closed", descriptionText: message)
    
    }
}

def updateOccupancy(message, debugContext) {
    def zone = getZoneDevice()
    def occupancy = zone.currentValue("occupancy")
    
    anyDeviceActive = false
    for (key in state.keySet()) {
        if (key.startsWith("active:")) {
            if (state[key] == true) {
                anyDeviceActive = true
                name = state[key.replace("active:", "name:")]
                debugContext.append("""
${name} is active"""
                )
            }
        }   
    }
    
    if (anyDeviceActive) {
        if (occupancy == "unoccupied") {
            debugContext.append("""
Changing to occupied"""
            )
        }
        zone.sendEvent(name: "occupancy", value: "occupied", descriptionText: message)
    
    } else {
        debugContext.append("""
All devices are inactive"""
        )
        if (state.wasp_in_box == false) {
            if (occupancy == "occupied") {
                debugContext.append("""
Changing to unoccupied"""
                )
            }
            zone.sendEvent(name: "occupancy", value: "unoccupied", descriptionText: message)
        } else {
            debugContext.append("""
Ignored, because wasp is in the box"""
            )
        }
    }
}

//-----------------------------------------

def detectWaspInBox(debugContext) {
    def zone = getZoneDevice()
    def contact = zone.currentValue("contact")

    if (contact == "closed") {
        state.wasp_in_box = true
        debugContext.append("""
Wasp is in the box!"""
        )
    }
}

def clearWaspInBox(debugContext) {
    state.wasp_in_box = false
}

//-----------------------------------------

def zoneHandler_ContactAlert(evt) {
    logDebug("zoneHandler_ContactAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (alertOpen) {
            personToNotify.deviceNotification("${evt.device} is open!")
        }
    } else {
        if (alertClosed) {
            personToNotify.deviceNotification("${evt.device} is closed!")
        }
    }
}

def zoneHandler_OccupancyAlert(evt) {
    logDebug("zoneHandler_OccupancyAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "occupied") {
        if (alertOccupied) {
            personToNotify.deviceNotification("${evt.device} is occupied!")
        }
    } else {
        if (alertUnoccupied) {
            personToNotify.deviceNotification("${evt.device} is unoccupied!")
        }
    }
}