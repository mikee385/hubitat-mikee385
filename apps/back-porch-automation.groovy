/**
 *  Back Porch Automation
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
 
String getVersionNum() { return "2.2.0" }
String getVersionLabel() { return "Back Porch Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Back Porch Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Automation and alerts for the door and lights associated with the back porch.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/back-porch-automation.groovy")

preferences {
    page(name: "settings", title: "Back Porch Automation", install: true, uninstall: true) {
        section {
            input "lights", "capability.switch", title: "Lights", multiple: true, required: true
            input "door", "capability.contactSensor", title: "Door", multiple: false, required: true
            input "lock", "capability.contactSensor", title: "Door Lock", multiple: false, required: true
        }
        section {
            input "sunlight", "capability.switch", title: "Sunlight", multiple: false, required: true
            input "cameraNotification", "capability.switch", title: "Camera Notifications", multiple: false, required: false
        }
        section {
            input "sprinklerController", "device.RachioController", title: "Sprinkler Controller", multiple: false, required: false
            input "sprinklerZones", "device.RachioZone", title: "Sprinkler Zones", multiple: true, required: false
        }
        section {
            input "person", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
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
    state.sprinklersPaused = false
    
    // Light Switch
    subscribe(door, "contact", doorHandler_LightSwitch)
    subscribe(sunlight, "switch", sunlightHandler_LightSwitch)
    subscribe(location, "mode", modeHandler_LightSwitch)
    
    // Camera Notification
    subscribe(door, "contact", doorHandler_CameraSwitch)
    subscribe(location, "mode", modeHandler_CameraSwitch)
    
    // Sprinkler Zones
    subscribe(door, "contact", doorHandler_SprinklerZones)
    subscribe(location, "mode", modeHandler_SprinklerZones)
    
    // Light Alert
    for (light in lights) {
        subscribe(light, "switch", lightHandler_LightAlert)
    }
    subscribe(person, "status", personHandler_LightAlert)
    
    // Door Alert
    subscribe(door, "contact", doorHandler_DoorAlert)
    subscribe(person, "status", personHandler_DoorAlert)
    
    // Away Alert
    for (light in lights) {
        subscribe(light, "switch.on", handler_AwayAlert)
    }
    subscribe(door, "contact", handler_AwayAlert)
    subscribe(lock, "contact", handler_AwayAlert)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def doorHandler_LightSwitch(evt) {
    logDebug("doorHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        stopWaiting_LightSwitch()
        if (sunlight.currentValue("switch") == "off") {
            for (light in lights) {
                light.on()
            }
        }
    } else {
        subscribe(lock, "contact", lockHandler_LightSwitch)
        runIn(60*10, stopWaiting_LightSwitch)
    }
}

def lockHandler_LightSwitch(evt) {
    logDebug("lockHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    turnOff_LightSwitch()
}

def sunlightHandler_LightSwitch(evt) {
    logDebug("sunlightHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        turnOff_LightSwitch()
    }
}

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value != "Home") {
        turnOff_LightSwitch()
    }
}

def turnOff_LightSwitch() {
    stopWaiting_LightSwitch()
    for (light in lights) {
        light.off()
    }
}

def stopWaiting_LightSwitch() {
    unschedule("stopWaiting_LightSwitch")
    unsubscribe("lockHandler_LightSwitch")
}

def doorHandler_CameraSwitch(evt) {
    logDebug("doorHandler_CameraSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        stopWaiting_CameraSwitch()
        cameraNotification.off()
    } else {
        subscribe(lock, "contact", lockHandler_CameraSwitch)
        runIn(60*10, turnOn_CameraSwitch)
    }
}

def lockHandler_CameraSwitch(evt) {
    logDebug("lockHandler_CameraSwitch: ${evt.device} changed to ${evt.value}")
    
    runIn(15, turnOn_CameraSwitch)
}

def modeHandler_CameraSwitch(evt) {
    logDebug("modeHandler_CameraSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value != "Home") {
        turnOn_CameraSwitch()
    }
}

def turnOn_CameraSwitch() {
    stopWaiting_CameraSwitch()
    cameraNotification.on()
}

def stopWaiting_CameraSwitch() {
    unschedule("turnOn_CameraSwitch")
    unsubscribe("lockHandler_CameraSwitch")
}

def doorHandler_SprinklerZones(evt) {
    logDebug("doorHandler_SprinklerZones: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        stopWaiting_SprinklerZones()
        
        for (sprinklerZone in sprinklerZones) {
            if (sprinklerZone.currentValue("switch") == "on") {
                state.sprinklersPaused = true
                //sprinklerController.pauseZoneRun(1800)
                person.deviceNotification("Pausing sprinklers!")
                break
            }
        }
    } else {
        subscribe(lock, "contact", lockHandler_SprinklerZones)
        runIn(60*10, resume_SprinklerZones)
    }
}

def lockHandler_SprinklerZones(evt) {
    logDebug("lockHandler_SprinklerZones: ${evt.device} changed to ${evt.value}")
    
    resume_SprinklerZones()
}

def modeHandler_SprinklerZones(evt) {
    logDebug("modeHandler_SprinklerZones: ${evt.device} changed to ${evt.value}")

    if (evt.value != "Home") {
        resume_SprinklerZones()
    }
}

def resume_SprinklerZones() {
    stopWaiting_SprinklerZones()
    
    if (state.sprinklersPaused) {
        state.sprinklersPaused = false
        //sprinklerController.resumeZoneRun()
        person.deviceNotification("Resuming sprinklers!")
    }
}

def stopWaiting_SprinklerZones() {
    unschedule("resume_SprinklerZones")
    unsubscribe("lockHandler_SprinklerZones")
}

def lightHandler_LightAlert(evt) {
    logDebug("lightHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        if (person.currentValue("status") != "sleep") {
            runIn(60*5, lightAlert, [data: [device: "${evt.device}"]])
        }
    } else {
        unschedule("lightAlert")
    }
}

def personHandler_LightAlert(evt) {
    logDebug("personHandler_LightAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "sleep") {
        unschedule("lightAlert")
        
        for (light in lights) {
            if (light.currentValue("switch") == "on") {
                person.deviceNotification("$light is still on!")
            }
        }
    }
}

def lightAlert(evt) {
    person.deviceNotification("Should the ${evt.device} still be on?")
    runIn(60*30, lightAlert, [data: [device: "${evt.device}"]])
}

def doorHandler_DoorAlert(evt) {
    logDebug("doorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (person.currentValue("status") == "home") {
            runIn(60*5, doorAlert)
        }
    } else {
        unschedule("doorAlert")
    }
}

def personHandler_DoorAlert(evt) {
    logDebug("personHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value != "home") {
        unschedule("doorAlert")
        
        if (door.currentValue("contact") == "open") {
            person.deviceNotification("$door is still open!")
        }
    }
}

def doorAlert() {
    person.deviceNotification("Should the $door still be open?")
    runIn(60*30, doorAlert)
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        person.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}