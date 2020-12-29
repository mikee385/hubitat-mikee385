/**
 *  Camera Automation
 *
 *  Copyright 2020 Michael Pierce
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
 
String getVersionNum() { return "1.0.0-beta.1" }
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
            
            input "backupButton", "capability.pushableButton", title: "Button", multiple: false, required: false
        }
        section("Reminder") {
            input "reminderSwitch", "capability.switch", title: "Reminder Switch", multiple: false, required: true
            
            input "person", "device.PersonStatus", title: "Person", multiple: false, required: true
            
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
        }
        section {
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
    
    subscribe(location, "mode", modeHandler)
    
    if (backupButton) {
        subscribe(backupButton, "pushed", buttonHandler)
    }

    subscribe(person, "state", personHandler)
    
    for (camera in cameras) {
        subscribe(camera, "switch", cameraHandler)
    }
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def modeHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")

    if (evt.value == "Home") {
        if (state.mode == "Away") {
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

def buttonHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")

    for (camera in cameras) {
        camera.off()
    }
}

def personHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")

    if (evt.value == "home") {
        unschedule()
        runIn(5, checkCameras)
    } else {
        reminderSwitch.off()
    }
}

def cameraHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (person.currentValue("state") == "home") {
        unschedule()
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