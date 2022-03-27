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
 
String getVersionNum() { return "4.8.0" }
String getVersionLabel() { return "Echo Glow Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.away-alert-library
#include mikee385.battery-alert-library
#include mikee385.inactive-alert-library

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
        section("Echo Glows") {
            input "downstairsGlow", "device.EchoGlowDevice", title: "Downstairs Glow", multiple: false, required: true
            input "upstairsGlow", "device.EchoGlowDevice", title: "Upstairs Glow", multiple: false, required: true
        }
        section("Routines") {
            input "bedtimeTimerRoutine", "device.VirtualAlexaButton", title: "Bedtime Timer", multiple: false, required: true
            input "bedtimeSoonRoutine", "device.VirtualAlexaButton", title: "Bedtime Soon", multiple: false, required: true
            input "bedtimeNowRoutine", "device.VirtualAlexaButton", title: "Bedtime Now", multiple: false, required: true
            input "naptimeNowRoutine", "device.VirtualAlexaButton", title: "Naptime Now", multiple: false, required: true
            input "wakeUpRoutine", "device.VirtualAlexaButton", title: "Wake Up", multiple: false, required: true
            input "glowsOffRoutine", "device.VirtualAlexaButton", title: "Glows Off", multiple: false, required: true
        }
        section("Announcements") {
            input "bedtimeAnnouncement", "device.VirtualAlexaButton", title: "Bedtime Announcement", multiple: false, required: true
        }
        section("Doors") {
            input "bedroomDoor", "capability.contactSensor", title: "Bedroom Door", multiple: false, required: false    
        }
        section("Remotes") {
            input "hueRemote", "capability.pushableButton", title: "Hue Remote", required: false
            input "harmonyRemote", "capability.pushableButton", title: "Harmony Remote", required: false
        }
        section("Media Devices") {
            input "rokuRemotes", "device.RokuTV", title: "Roku", multiple: true, required: false
            input "bedtimeNowPause", "bool", title: "Pause when Bedtime Now?", required: true, defaultValue: false
            input "naptimeNowPause", "bool", title: "Pause when Naptime Now?", required: true, defaultValue: false
        }
        section("Alerts") {
            input "bedtimeSoonAlert", "bool", title: "Alert when Bedtime Soon?", required: true, defaultValue: false
            input "bedtimeNowAlert", "bool", title: "Alert when Bedtime Now?", required: true, defaultValue: false
            input "naptimeNowAlert", "bool", title: "Alert when Naptime Now?", required: true, defaultValue: false
            input "wakeUpAlert", "bool", title: "Alert when Wake Up?", required: true, defaultValue: false
            input "glowsOffAlert", "bool", title: "Alert when Glows Off?", required: true, defaultValue: false
        }
        section {
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/bedtimeTimer") {
        action: [
            GET: "urlHandler_bedtimeTimer"
        ]
    }
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
    path("/naptimeNow") {
        action: [
            GET: "urlHandler_naptimeNow"
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

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    // Create state
    if (state.lastRoutine == null) {
        state.lastRoutine = ""
    }
    if (state.timerActive == null) {
        state.timerActive = false
    }
    
    // Routines
    subscribe(bedtimeTimerRoutine, "pushed", routineHandler_BedtimeTimer)
    subscribe(bedtimeSoonRoutine, "pushed", routineHandler_BedtimeSoon)
    subscribe(bedtimeNowRoutine, "pushed", routineHandler_BedtimeNow)
    subscribe(naptimeNowRoutine, "pushed", routineHandler_NaptimeNow)
    subscribe(wakeUpRoutine, "pushed", routineHandler_WakeUp)
    subscribe(glowsOffRoutine, "pushed", routineHandler_GlowsOff)

    // Buttons and Modes
    if (hueRemote) {
        subscribe(hueRemote, "pushed", hueRemoteHandler_Routine)
    }
    if (harmonyRemote) {
        subscribe(harmonyRemote, "pushed", harmonyRemoteHandler_Routine)
    }
    subscribe(location, "mode", modeHandler_Routine)
    
    // Away Alerts
    subscribe(bedtimeSoonRoutine, "pushed", handler_AwayAlert)
    subscribe(bedtimeNowRoutine, "pushed", handler_AwayAlert)
    subscribe(naptimeNowRoutine, "pushed", handler_AwayAlert)
    subscribe(wakeUpRoutine, "pushed", handler_AwayAlert)
    
    // Battery Alert
    scheduleBatteryCheck()
    
    // Inactive Alert
    scheduleInactiveCheck()
    
    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.bedtimeTimerUrl = "${getFullLocalApiServerUrl()}/bedtimeTimer?access_token=$state.accessToken"
    state.bedtimeSoonUrl = "${getFullLocalApiServerUrl()}/bedtimeSoon?access_token=$state.accessToken"
    state.bedtimeNowUrl = "${getFullLocalApiServerUrl()}/bedtimeNow?access_token=$state.accessToken"
    state.naptimeNowUrl = "${getFullLocalApiServerUrl()}/naptimeNow?access_token=$state.accessToken"
    state.wakeUpUrl = "${getFullLocalApiServerUrl()}/wakeUp?access_token=$state.accessToken"
    state.glowsOffUrl = "${getFullLocalApiServerUrl()}/glowsOff?access_token=$state.accessToken"
}

def getBatteryThresholds() {
    def thresholds = []

    if (hueRemote) {
        thresholds.add([device: hueRemote, lowBattery: 10])
    }
    
    return thresholds
}

def getInactiveThresholds() {
    def thresholds = []
    
    for (rokuRemote in rokuRemotes) {
        thresholds.add([device: rokuRemote, inactiveHours: 24])
    }
    
    return thresholds
}

def getUnchangedThresholds() {
    def thresholds = []
    
    for (rokuRemote in rokuRemotes) {
        thresholds.add([device: rokuRemote, attribute: "application", inactiveHours: 24])
    }

    return thresholds
}

def routineHandler_BedtimeTimer(evt) {
    logDebug("routineHandler_BedtimeTimer: ${evt.device} changed to ${evt.value}")

    bedtimeSoonRoutine.push()
    
    if (state.timerActive == false) {
        state.timerActive = true
        runIn(5*60, bedtimeNow)
    }
}

def bedtimeNow() {
    bedtimeNowRoutine.push()
}

def routineHandler_BedtimeSoon(evt) {
    logDebug("routineHandler_BedtimeSoon: ${evt.device} changed to ${evt.value}")
    
    unschedule("downstairsGlowOff")
    unschedule("glowsOff")
    
    unsubscribe("doorHandler_DownstairsGlowOff")
    unsubscribe("doorHandler_GlowsOff")

    downstairsGlow.orange()
    upstairsGlow.orange()
    
    if (state.lastRoutine != "BedtimeSoon") {
        if (bedtimeSoonAlert) {
            personToNotify.deviceNotification("Bedtime Soon!")
        }
        
        state.lastRoutine = "BedtimeSoon"
    }
}

def routineHandler_BedtimeNow(evt) {
    logDebug("routineHandler_BedtimeNow: ${evt.device} changed to ${evt.value}")
    
    if (state.timerActive == true) {
        state.timerActive = false
        unschedule("bedtimeNow")
    }
    
    unschedule("downstairsGlowOff")
    unschedule("glowsOff")
    
    unsubscribe("doorHandler_DownstairsGlowOff")
    unsubscribe("doorHandler_GlowsOff")

    downstairsGlow.purple()
    upstairsGlow.purple()
    
    if (bedroomDoor) {
        subscribe(bedroomDoor, "contact.closed", doorHandler_DownstairsGlowOff)
    } else {
        runIn(10*60, downstairsGlowOff)
    }
    
    if (state.lastRoutine != "BedtimeNow") {
        if (bedtimeNowAlert) {
            personToNotify.deviceNotification("Bedtime Now!")
        }
        
        if (bedtimeNowPause && rokuRemotes) {
            for (rokuRemote in rokuRemotes) {
                rokuRemote.queryMediaPlayer()
            }
            runIn(2, pauseRoku)
        }
        
        bedtimeAnnouncement.push()
        
        state.lastRoutine = "BedtimeNow"
    }
}

def downstairsGlowOff() {
    downstairsGlow.off()
}

def pauseRoku() {
    for (rokuRemote in rokuRemotes) {
        if (rokuRemote.currentValue("transportStatus") == "playing" && rokuRemote.currentValue("application") != "Live TV") {
            rokuRemote.pause()
            rokuRemote.queryMediaPlayer()
        }
    }
}

def routineHandler_NaptimeNow(evt) {
    logDebug("routineHandler_NaptimeNow: ${evt.device} changed to ${evt.value}")
    
    if (state.timerActive == true) {
        state.timerActive = false
        unschedule("bedtimeNow")
    }
    
    unschedule("downstairsGlowOff")
    unschedule("glowsOff")
    
    unsubscribe("doorHandler_DownstairsGlowOff")
    unsubscribe("doorHandler_GlowsOff")

    downstairsGlow.blue()
    upstairsGlow.blue()
    
    if (bedroomDoor) {
        subscribe(bedroomDoor, "contact.closed", doorHandler_DownstairsGlowOff)
    } else {
        runIn(10*60, downstairsGlowOff)
    }
    
    if (state.lastRoutine != "NaptimeNow") {
        if (naptimeNowAlert) {
            personToNotify.deviceNotification("Naptime Now!")
        }
        
        if (naptimeNowPause && rokuRemotes) {
            for (rokuRemote in rokuRemotes) {
                rokuRemote.queryMediaPlayer()
            }
            runIn(2, pauseRoku)
        }
        
        state.lastRoutine = "NaptimeNow"
    }
}

def routineHandler_WakeUp(evt) {
    logDebug("routineHandler_WakeUp: ${evt.device} changed to ${evt.value}")
    
    if (state.timerActive == true) {
        state.timerActive = false
        unschedule("bedtimeNow")
    }
    
    unschedule("downstairsGlowOff")
    unschedule("glowsOff")
    
    unsubscribe("doorHandler_DownstairsGlowOff")
    unsubscribe("doorHandler_GlowsOff")

    downstairsGlow.green()
    upstairsGlow.green()
    
    if (bedroomDoor) {
        subscribe(bedroomDoor, "contact.open", doorHandler_GlowsOff)
    } else {
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
    glowsOffRoutine.push()
}

def routineHandler_GlowsOff(evt) {
    logDebug("routineHandler_GlowsOff: ${evt.device} changed to ${evt.value}")
    
    if (state.timerActive == true) {
        state.timerActive = false
        unschedule("bedtimeNow")
    }
    
    unschedule("downstairsGlowOff")
    unschedule("glowsOff")
    
    unsubscribe("doorHandler_DownstairsGlowOff")
    unsubscribe("doorHandler_GlowsOff")

    downstairsGlow.off()
    upstairsGlow.off()
    
    if (state.lastRoutine != "GlowsOff") {
        if (glowsOffAlert) {
            personToNotify.deviceNotification("Glows Off!")
        }
    
        state.lastRoutine = "GlowsOff"
    }
}

def doorHandler_DownstairsGlowOff(evt) {
    logDebug("doorHandler_DownstairsGlowOff: ${evt.device} changed to ${evt.value}")
    
    runIn(10*60, downstairsGlowOff)
}

def doorHandler_GlowsOff(evt) {
    logDebug("doorHandler_GlowsOff: ${evt.device} changed to ${evt.value}")
    
    runIn(10*60, glowsOff)
}

def hueRemoteHandler_Routine(evt) {
    logDebug("hueRemoteHandler_Routine: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "1") {
        if (timeOfDayIsBetween(timeToday("00:00"), timeToday("09:00"), new Date(), location.timeZone)) {
            wakeUpRoutine.push()
        } else if (timeOfDayIsBetween(timeToday("09:00"), timeToday("14:00"), new Date(), location.timeZone)) {
            naptimeNowRoutine.push()
        } else if (timeOfDayIsBetween(timeToday("14:00"), timeToday("17:00"), new Date(), location.timeZone)) {
            wakeUpRoutine.push()
        } else {
            bedtimeTimerRoutine.push()
        }
    } else if (evt.value == "2") {
        bedtimeNowRoutine.push()
    } else if (evt.value == "4") {
        glowsOffRoutine.push()
    }
}

def harmonyRemoteHandler_Routine(evt) {
    logDebug("harmonyRemoteHandler_Routine: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "1") {
        bedtimeTimerRoutine.push()
    } else if (evt.value == "2") {
        bedtimeNowRoutine.push()
    } else if (evt.value == "3") {
        wakeUpRoutine.push()
    } else if (evt.value == "4") {
        glowsOffRoutine.push()
    }
}

def modeHandler_Routine(evt) {
    logDebug("modeHandler_Routine: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        glowsOffRoutine.push()
    }
}

def urlHandler_bedtimeTimer(evt) {
    logDebug("urlHandler_bedtimeTimer")
    
    bedtimeTimerRoutine.push()
}

def urlHandler_bedtimeSoon(evt) {
    logDebug("urlHandler_bedtimeSoon")
    
    bedtimeSoonRoutine.push()
}

def urlHandler_bedtimeNow(evt) {
    logDebug("urlHandler_bedtimeNow")
    
    bedtimeNowRoutine.push()
}

def urlHandler_naptimeNow(evt) {
    logDebug("urlHandler_naptimeNow")
    
    naptimeNowRoutine.push()
}

def urlHandler_wakeUp(evt) {
    logDebug("urlHandler_wakeUp")
    
    wakeUpRoutine.push()
}

def urlHandler_glowsOff(evt) {
    logDebug("urlHandler_glowsOff")
    
    glowsOffRoutine.push()
}