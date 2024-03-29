/**
 *  Security Automation
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
 
String getVersionNum() { return "5.0.0" }
String getVersionLabel() { return "Security Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library

definition(
    name: "Security Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Turns on/off the cameras and sends alerts based on the alarm system and mode.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/security-automation.groovy"
)

preferences {
    page(name: "settings", title: "Security Automation", install: true, uninstall: true) {
        section {
            input "enableAutomation", "bool", title: "Enabled?", defaultValue: true
        }
        section {
            input "alarmPanel", "device.VivintPanel", title: "Alarm Panel", multiple: false, required: false
            input "cameras", "capability.switch", title: "Cameras", multiple: true, required: false
            input "smokeDetectors", "capability.smokeDetector", title: "Smoke Detectors", multiple: true, required: false
            input "glassBreaks", "capability.shockSensor", title: "Glass Breaks", multiple: true, required: false
        }
        section {
            input "deviceMonitor", "device.DeviceMonitor", title: "Device Monitor", multiple: false, required: true
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
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
    if (enableAutomation) {
        // Camera Switch
        if (cameras) {
            subscribe(location, "mode", modeHandler_CameraSwitch)
            if (alarmPanel) {
                subscribe(alarmPanel, "alarm", alarmPanelHandler_CameraSwitch)
            }
        }
        
        // Alarm Alert
        if (alarmPanel) {
            subscribe(location, "mode", modeHandler_AlarmAlert)
        }
        
        // Camera Alert
        if (cameras) {
            subscribe(location, "mode", modeHandler_CameraAlert)
            for (camera in cameras) {
                subscribe(camera, "switch.on", cameraHandler_CameraAlert)
            }
        }
        
        // Smoke Alert
        for (smokeDetector in smokeDetectors) {
            subscribe(smokeDetector, "smoke.detected", smokeDetectorHandler_SmokeAlert)
        }
        
        // Glass Break Alert
        for (glassBreak in glassBreaks) {
            subscribe(glassBreak, "shock.detected", glassBreakHandler_GlassBreakAlert)
        }
        
        // Device Checks
        initializeDeviceChecks()
    }
}

def modeHandler_CameraSwitch(evt) {
    logDebug("modeHandler_CameraSwitch: ${evt.device} changed to ${evt.value}")

    if (evt.value == "Home") {
        if (!alarmPanel || alarmPanel.currentValue("alarm") == "disarmed") {
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

def modeHandler_AlarmAlert(evt) {
    logDebug("modeHandler_AlarmAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        checkAlarm()
    } else if (evt.value == "Sleep") {
        runIn(60, checkAlarm)
    } else {
        unschedule("checkAlarm")
    }
}

def checkAlarm() {
    if (location.mode != "Home" && alarmPanel.currentValue("alarm") == "disarmed") {
        personToNotify.deviceNotification("Set the alarm!")
    }
}

def modeHandler_CameraAlert(evt) {
    logDebug("modeHandler_CameraAlert: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Home") {
        runIn(5, checkCameras)
    } else {
        unschedule("checkCameras")
    }
}

def cameraHandler_CameraAlert(evt) {
    logDebug("cameraHandler_CameraAlert: ${evt.device} changed to ${evt.value}")

    if (location.mode == "Home") {
        runIn(5, checkCameras)
    }
}

def checkCameras() {
    if (location.mode == "Home" && (!alarmPanel || alarmPanel.currentValue("alarm") == "disarmed")) {
        def anyCameraOn = false
        for (camera in cameras) {
            if (camera.currentValue("switch") == "on") {
                anyCameraOn = true
                break
            }
        }
        if (anyCameraOn) {
            personToNotify.deviceNotification("Should the cameras be on?")
        }
    }
}

def smokeDetectorHandler_SmokeAlert(evt) {
    logDebug("smokeDetectorHandler_SmokeAlert: ${evt.device} changed to ${evt.value}")

    personToNotify.deviceNotification("Smoke detected by ${evt.device}!")
}

def glassBreakHandler_GlassBreakAlert(evt) {
    logDebug("glassBreakHandler_GlassBreakAlert: ${evt.device} changed to ${evt.value}")

    personToNotify.deviceNotification("Glass Break detected by ${evt.device}!")
}