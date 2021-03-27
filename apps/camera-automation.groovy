/**
 *  Camera Automation
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
 
String getVersionNum() { return "2.0.1" }
String getVersionLabel() { return "Camera Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Camera Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns on/off the cameras and reminders based on modes and other devices.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/camera-automation.groovy")

preferences {
    page(name: "settings", title: "Camera Automation", install: true, uninstall: true) {
        section {
            input "cameras", "capability.switch", title: "Cameras", multiple: true, required: true
            input "alarmPanel", "device.VivintPanel", title: "Alarm Panel", multiple: false, required: false
            input "backupButton", "capability.pushableButton", title: "Button", multiple: false, required: false
        }
        section {
            input "reminderSwitch", "capability.switch", title: "Reminder Switch", multiple: false, required: true
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
    state.mode = location.mode
    
    // Camera Switch
    subscribe(location, "mode", modeHandler_CameraSwitch)
    if (alarmPanel) {
        subscribe(alarmPanel, "status", alarmPanelHandler_CameraSwitch)
    }
    if (backupButton) {
        subscribe(backupButton, "pushed", buttonHandler_CameraSwitch)
    }
    
    // Reminder Switch
    for (camera in cameras) {
        subscribe(camera, "switch", cameraHandler_ReminderSwitch)
    }
    subscribe(person, "status", personHandler_ReminderSwitch)
    
    // Reminder Alert
    subscribe(reminderSwitch, "switch", reminderHandler_ReminderAlert)
    
    // Away Alert
    for (camera in cameras) {
        subscribe(camera, "switch.off", handler_AwayAlert)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def modeHandler_CameraSwitch(evt) {
    logDebug("modeHandler_CameraSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value == "Home") {
        if (alarmPanel) {
            if (alarmPanel.currentValue("status") == "disarmed") {
                for (camera in cameras) {
                    camera.off()
                }
            }
        } else if (state.mode == "Away") {
            for (camera in cameras) {
                camera.off()
            }
        }
    } else {
        for (camera in cameras) {
            camera.on()
        }
    }
    state.mode = evt.value
}

def alarmPanelHandler_CameraSwitch(evt) {
    logDebug("alarmPanelHandler_CameraSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "disarmed") {
        if (location.mode == "Home") {
            for (camera in cameras) {
                camera.off()
            }
        }
    } else {
        for (camera in cameras) {
            camera.on()
        }
    }
}

def buttonHandler_CameraSwitch(evt) {
    logDebug("buttonHandler_CameraSwitch: ${evt.device} changed to ${evt.value}")

    for (camera in cameras) {
        camera.off()
    }
}

def cameraHandler_ReminderSwitch(evt) {
    logDebug("cameraHandler_ReminderSwitch: ${evt.device} changed to ${evt.value}")
    
    if (person.currentValue("status") == "home") {
        unschedule("checkCameras")
        runIn(5, checkCameras)
    } else {
        reminderSwitch.off()
    }
}

def personHandler_ReminderSwitch(evt) {
    logDebug("personHandler_ReminderSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value == "home") {
        unschedule("checkCameras")
        runIn(5, checkCameras)
    } else {
        reminderSwitch.off()
    }
}

def checkCameras() {
    def anyCameraOn = false
    for (camera in cameras) {
        if (camera.currentValue("switch") == "on") {
            anyCameraOn = true
            break
        }
    }
    if (anyCameraOn) {
        reminderSwitch.on()
    } else {
        reminderSwitch.off()
    }
}

def reminderHandler_ReminderAlert(evt) {
    logDebug("reminderHandler_ReminderAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "on") {
        runIn(60*5, reminderAlert)
    } else {
        unschedule("reminderAlert")
        person.deviceNotification("Camera Reminder is off.")
    }
}

def reminderAlert() {
    person.deviceNotification("Turn off the cameras!")
    runIn(60*5, reminderAlert)
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        person.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}