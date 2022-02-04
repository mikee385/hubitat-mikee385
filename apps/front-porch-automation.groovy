/**
 *  Front Porch Automation
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
 
String getVersionNum() { return "2.4.0" }
String getVersionLabel() { return "Front Porch Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Front Porch Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Automation and alerts for the door and lights associated with the front porch.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/front-porch-automation.groovy")

preferences {
    page(name: "settings", title: "Front Porch Automation", install: true, uninstall: true) {
        section {
            input "door", "capability.contactSensor", title: "Door", multiple: false, required: true
            input "lock", "capability.lock", title: "Door Lock", multiple: false, required: false
            input "lights", "capability.switch", title: "Lights", multiple: true, required: true
        }
        section("Outdoor Sensors") {
            input "sunlight", "capability.switch", title: "Sunlight", multiple: false, required: true
            input "motionSensor", "capability.motionSensor", title: "Motion Sensor", multiple: false, required: false
        }
        section("Sprinklers") {
            input "sprinklerController", "device.RachioController", title: "Sprinkler Controller", multiple: false, required: false
            input "sprinklerZones", "device.RachioZone", title: "Sprinkler Zones", multiple: true, required: false
        }
        section("Light Button") {
            input "buttonDevice", "capability.pushableButton", title: "Button Device", required: false
            input "buttonNumber", "number", title: "Button Number", required: false
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
    // Light Switch
    subscribe(sunlight, "switch", sunlightHandler_LightSwitch)
    subscribe(location, "mode", modeHandler_LightSwitch)
    if (buttonDevice) {
        subscribe(buttonDevice, "pushed", buttonHandler_LightSwitch)
    }
    
    // Motion Alert
    if (motionSensor) {
        subscribe(motionSensor, "motion", motionHandler_MotionAlert)
    }
    
    // Door Alert
    subscribe(door, "contact", doorHandler_DoorAlert)
    subscribe(person, "presence", personHandler_DoorAlert)
    subscribe(person, "sleeping", personHandler_DoorAlert)
    
    // Lock Alert
    if (lock) {
        subscribe(lock, "lock", lockHandler_LockAlert)
        subscribe(person, "presence", personHandler_LockAlert)
        subscribe(person, "sleeping", personHandler_LockAlert)
    }
    
    // Away Alert
    subscribe(door, "contact", handler_AwayAlert)
    if (lock) {
        subscribe(lock, "lock", handler_AwayAlert)
    }
    for (light in lights) {
        subscribe(light, "switch.on", handler_AwayAlert)
    }

    def currentTime = new Date()
    
    // Battery Alert
    def batteryAlertTime = timeToday("20:00")
    schedule("$currentTime.seconds $batteryAlertTime.minutes $batteryAlertTime.hours * * ? *", handler_BatteryAlert)
    
    // Inactive Alert
    def inactiveAlertTime = timeToday("20:00")
    schedule("$currentTime.seconds $inactiveAlertTime.minutes $inactiveAlertTime.hours * * ? *", handler_InactiveAlert)
}

def getBatteryThresholds() {
    def thresholds = [
        [device: door, lowBattery: 10]
    ]
    if (lock) {
        thresholds.add([device: lock, lowBattery: 10])
    }
    return thresholds
}

def getInactiveThresholds() {
    def thresholds = [
        [device: door, inactiveHours: 2]
    ]
    if (lock) {
        thresholds.add([device: lock, inactiveHours: 2])
    }
    for (light in lights) {
        thresholds.add([device: light, inactiveHours: 24])
    }
    return thresholds
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def on() {
    for (light in lights) {
        light.on()
    }
}

def off() {
    for (light in lights) {
        light.off()
    }
}

def toggle() {
    def anyLightOn = false
    for (light in lights) {
        if (light.currentValue("switch") == "on") {
            anyLightOn = true
            break 
        }
    }
    if (anyLightOn) {
        off()
    } else {
        on()
    }
}

def sunlightHandler_LightSwitch(evt) {
    logDebug("sunlightHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        off()
    }
}

def modeHandler_LightSwitch(evt) {
    logDebug("modeHandler_LightSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value == "Sleep") {
        off()
    }
}

def buttonHandler_LightSwitch(evt) {
    logDebug("buttonHandler_LightSwitch: ${evt.device} changed to ${evt.value}")
    
    if (buttonNumber == null || evt.value == buttonNumber.toString()) {
        toggle()
    }
}

def motionHandler_MotionAlert(evt) {
    logDebug("motionHandler_MotionAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "active") {
        if (door.currentValue("contact") == "closed") {
            person.deviceNotification("Motion on the Front Porch!")
        }
    }
}

def doorHandler_DoorAlert(evt) {
    logDebug("doorHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "open") {
        if (person.currentValue("presence") == "present" && person.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, doorAlert)
        }
    } else {
        unschedule("doorAlert")
    }
}

def personHandler_DoorAlert(evt) {
    logDebug("personHandler_DoorAlert: ${evt.device} changed to ${evt.value}")
    
    if (person.currentValue("presence") == "not present" || person.currentValue("sleeping") == "sleeping") {
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

def lockHandler_LockAlert(evt) {
    logDebug("lockHandler_LockAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "unlocked") {
        if (person.currentValue("presence") == "present" && person.currentValue("sleeping") == "not sleeping") {
            runIn(60*5, lockAlert)
        }
    } else {
        unschedule("lockAlert")
    }
}

def personHandler_LockAlert(evt) {
    logDebug("personHandler_LockAlert: ${evt.device} changed to ${evt.value}")
    
    if (person.currentValue("presence") == "not present" || person.currentValue("sleeping") == "sleeping") {
        unschedule("lockAlert")
        
        if (lock.currentValue("lock") == "unlocked") {
            person.deviceNotification("$lock is still unlocked!")
        }
    }
}

def lockAlert() {
    person.deviceNotification("Should the $lock still be unlocked?")
    runIn(60*30, lockAlert)
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        person.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}

def handler_BatteryAlert() {
    logDebug("handler_BatteryAlert")
    
    if (person.currentValue("presence") == "present" && person.currentValue("sleeping") == "not sleeping") {
        def deviceIDs = []
        def message = ""
        
        for (item in getBatteryThresholds()) {
            if (!deviceIDs.contains(item.device.id)) {
                if (item.device.currentValue("battery") <= item.lowBattery) {
                    deviceIDs.add(item.device.id)
                    message += """
${item.device} - ${item.device.currentValue('battery')}%"""
                }
            }
        }
        
        if (message) {
            person.deviceNotification("Low Battery: $message")
        }
    }
}

def handler_InactiveAlert() {
    logDebug("handler_InactiveAlert")
    
    if (person.currentValue("presence") == "present" && person.currentValue("sleeping") == "not sleeping") {
        def dateTimeFormat = "MMM d, yyyy, h:mm a"
        def deviceIDs = []
        def message = ""
        
        for (item in getInactiveThresholds()) {
            if (!deviceIDs.contains(item.device.id)) {
                if (item.device.getLastActivity()) {
                    def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
                    if (item.device.getLastActivity().getTime() <= cutoffTime) {
                        deviceIDs.add(item.device.id)
                        message += """
${item.device} - ${item.device.getLastActivity().format(dateTimeFormat, location.timeZone)}"""
                    }
                } else {
                    deviceIDs.add(item.device.id)
                    message += """
${item.device} - No Activity"""
                }
            }
        }
        
        if (message) {
            person.deviceNotification("Inactive Devices: $message")
        }
    }
}