/**
 *  Echo Glow Automation
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
 
String getVersionNum() { return "10.0.0" }
String getVersionLabel() { return "Echo Glow Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library
#include mikee385.time-library

def getDaysOfWeek() { ["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"] }

definition(
    name: "Echo Glow Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Provides routines to control Echo Glow devices when triggered from various mechanisms.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/echo-glow-automation.groovy"
)

preferences {
    page(name: "settings", title: "Echo Glow Automation", install: true, uninstall: true) {
        section("Routines") {
            input "echoGlowRoutines", "device.EchoGlowRoutines", title: "Echo Glow Routines", multiple: false, required: true
        }
        section("Doors") {
            input "bedroomDoor", "capability.contactSensor", title: "Bedroom Door", multiple: false, required: false    
        }
        section("Media Devices") {
            input "rokuDevices", "device.RokuTV", title: "Roku Devices", multiple: true, required: false
            input "bedtimeNowPause", "bool", title: "Pause when Bedtime Now?", required: true, defaultValue: false
        }
        section("Daily Schedule") {
            input "daysToNotify", "enum", title: "Days of the Week", multiple: true, required: false, options: daysOfWeek
            input "timeToNotify", "time", title: "Time", required: true, defaultValue: "18:55"
        }
        section("Alerts") {
            input "bedtimeSoonAlert", "bool", title: "Alert when Bedtime Soon?", required: true, defaultValue: false
            input "bedtimeNowAlert", "bool", title: "Alert when Bedtime Now?", required: true, defaultValue: false
            input "wakeUpAlert", "bool", title: "Alert when Wake Up?", required: true, defaultValue: false
            input "glowsOffAlert", "bool", title: "Alert when Glows Off?", required: true, defaultValue: false
        }
        section {
            input "deviceMonitor", "device.DeviceMonitor", title: "Device Monitor", multiple: false, required: true
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/bedtimeSoon") {
        action: [
            GET: "urlHandler_bedtimeSoon"
        ]
    }
    path("/bedtimeNow") {
        action: [
            GET: "urlHandler_bedtimeNow"
        ]
    }
    path("/wakeUp") {
        action: [
            GET: "urlHandler_wakeUp"
        ]
    }
    path("/glowsOff") {
        action: [
            GET: "urlHandler_glowsOff"
        ]
    }
}

def installed() {
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
    // Child Device
    def child = childDevice()
    
    // Create state
    if (state.lastRoutine == null) {
        state.lastRoutine = ""
    }
    if (state.timerActive == null) {
        state.timerActive = false
    }
    
    // Routines
    subscribe(echoGlowRoutines, "lastRoutine.bedtimeSoon", routineHandler_BedtimeSoon)
    subscribe(echoGlowRoutines, "lastRoutine.bedtimeNow", routineHandler_BedtimeNow)
    subscribe(echoGlowRoutines, "lastRoutine.wakeUp", routineHandler_WakeUp)
    subscribe(echoGlowRoutines, "lastRoutine.glowsOff", routineHandler_GlowsOff)
    
    // Doors
    if (bedroomDoor) {
        subscribe(bedroomDoor, "contact", doorHandler_GlowsOff)
    }

    // Modes
    subscribe(location, "mode", modeHandler_Routine)
    
    // Daily Schedule
    if (childDevice().currentValue("switch") == "on") {
        initializeBedtimeSchedule()
    }
    subscribe(childDevice(), "switch", switchHandler_Schedule)
    
    // Device Checks
    initializeDeviceChecks()
    
    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.bedtimeSoonUrl = "${getFullLocalApiServerUrl()}/bedtimeSoon?access_token=$state.accessToken"
    state.bedtimeNowUrl = "${getFullLocalApiServerUrl()}/bedtimeNow?access_token=$state.accessToken"
    state.wakeUpUrl = "${getFullLocalApiServerUrl()}/wakeUp?access_token=$state.accessToken"
    state.glowsOffUrl = "${getFullLocalApiServerUrl()}/glowsOff?access_token=$state.accessToken"
}

def childDevice() {
    def childID = "echoGlow:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("hubitat", "Virtual Switch", childID, 1234, [label: app.label, isComponent: false])
    }
    return child
}

def initializeBedtimeSchedule() {
    scheduleBedtime()
        
    def resetToday = timeToday("23:59")
    def currentTime = new Date()
    schedule("$currentTime.seconds $resetToday.minutes $resetToday.hours * * ? *", scheduleBedtime)
}

def scheduleBedtime() {
    if (daysToNotify) {
        def daysFilter = daysToNotify.collect { (daysOfWeek.indexOf(it)+1).toString() }.join(",")
        def timeToNotifyToday = timeToday(timeToNotify)
        schedule("0 $timeToNotifyToday.minutes $timeToNotifyToday.hours ? * $daysFilter *", bedtimeSoon)
    }
}

def bedtimeSoon() {
    echoGlowRoutines.bedtimeSoon()
}

def routineHandler_BedtimeSoon(evt) {
    logDebug("routineHandler_BedtimeSoon: ${evt.device} changed to ${evt.value}")
    
    unschedule("bedtimeSoon")
    unschedule("glowsOff")
    
    if (state.lastRoutine != "BedtimeSoon") {
        if (bedtimeSoonAlert) {
            personToNotify.deviceNotification("Bedtime Soon!")
        }
        
        state.lastRoutine = "BedtimeSoon"
    }
    
    if (state.timerActive == false) {
        state.timerActive = true
        runIn(5*60, bedtimeNow)
    }
}

def bedtimeNow() {
    echoGlowRoutines.bedtimeNow()
}

def routineHandler_BedtimeNow(evt) {
    logDebug("routineHandler_BedtimeNow: ${evt.device} changed to ${evt.value}")
    
    if (state.timerActive == true) {
        state.timerActive = false
        unschedule("bedtimeNow")
    }
    
    unschedule("bedtimeSoon")
    unschedule("glowsOff")
    
    if (state.lastRoutine != "BedtimeNow") {
        if (bedtimeNowAlert) {
            personToNotify.deviceNotification("Bedtime Now!")
        }
        
        if (bedtimeNowPause && rokuDevices) {
            for (rokuDevice in rokuDevices) {
                rokuDevice.queryMediaPlayer()
            }
            runIn(2, pauseRoku)
        }
        
        state.lastRoutine = "BedtimeNow"
    }
}

def pauseRoku() {
    for (rokuDevice in rokuDevices) {
        if (rokuDevice.currentValue("transportStatus") == "playing" && rokuDevice.currentValue("application") != "Live TV") {
            rokuDevice.pause()
            rokuDevice.queryMediaPlayer()
        }
    }
}

def wakeUp() {
    echoGlowRoutines.wakeUp()
}

def routineHandler_WakeUp(evt) {
    logDebug("routineHandler_WakeUp: ${evt.device} changed to ${evt.value}")
    
    if (state.timerActive == true) {
        state.timerActive = false
        unschedule("bedtimeNow")
    }
    
    unschedule("glowsOff")
    
    if (!bedroomDoor) {
        runIn(10*60, glowsOff)
    }
    
    if (state.lastRoutine != "WakeUp") {
        if (wakeUpAlert) {
            personToNotify.deviceNotification("Wake Up!")
        }
        
        state.lastRoutine = "WakeUp"
    }
}

def glowsOff() {
    echoGlowRoutines.glowsOff()
}

def routineHandler_GlowsOff(evt) {
    logDebug("routineHandler_GlowsOff: ${evt.device} changed to ${evt.value}")
    
    if (state.timerActive == true) {
        state.timerActive = false
        unschedule("bedtimeNow")
    }
    
    unschedule("glowsOff")
    
    if (state.lastRoutine != "GlowsOff") {
        if (glowsOffAlert) {
            personToNotify.deviceNotification("Glows Off!")
        }
    
        state.lastRoutine = "GlowsOff"
    }
    
    if (childDevice().currentValue("switch") == "on") {
        scheduleBedtime()
    } 
}

def doorHandler_GlowsOff(evt) {
    logDebug("doorHandler_GlowsOff: ${evt.device} changed to ${evt.value}")
    
    unschedule("glowsOff")
    
    if (evt.value == "open" && currentTimeIsBetween("05:00", "17:00")) {
        runIn(10*60, glowsOff)
    } 
}

def modeHandler_Routine(evt) {
    logDebug("modeHandler_Routine: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        glowsOff()
    }
}

def switchHandler_Schedule(evt) {
    logDebug("switchHandler_Schedule: ${evt.device} changed to ${evt.value}")

    if (evt.value == "on") {
        initializeBedtimeSchedule()
    } else {
        unschedule("bedtimeSoon")
        unschedule("scheduleBedtime")
    } 
}

def urlHandler_bedtimeSoon(evt) {
    logDebug("urlHandler_bedtimeSoon")
    
    bedtimeSoon()
}

def urlHandler_bedtimeNow(evt) {
    logDebug("urlHandler_bedtimeNow")
    
    bedtimeNow()
}

def urlHandler_wakeUp(evt) {
    logDebug("urlHandler_wakeUp")
    
    wakeUp()
}

def urlHandler_glowsOff(evt) {
    logDebug("urlHandler_glowsOff")
    
    glowsOff()
}