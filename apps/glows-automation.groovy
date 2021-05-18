/**
 *  Glows Automation
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
 
String getVersionNum() { return "2.1.0" }
String getVersionLabel() { return "Glows Automation, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Glows Automation",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Triggers Alexa virtual buttons using various mechanisms.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/glows-automation.groovy")

preferences {
    page(name: "settings", title: "Glows Automation", install: true, uninstall: true) {
        section("Glow Routines") {
            input "bedtimeSoonRoutine", "capability.switch", title: "Bedtime Soon", multiple: false, required: true
            input "bedtimeNowRoutine", "capability.switch", title: "Bedtime Now", multiple: false, required: true
            input "wakeUpRoutine", "capability.switch", title: "Wake Up", multiple: false, required: true
            input "glowsOffRoutine", "capability.switch", title: "Glows Off", multiple: false, required: true
        }
        section("Remotes") {
            input "hueRemote", "capability.pushableButton", title: "Hue Remote", required: false
            input "harmonyRemote", "capability.pushableButton", title: "Harmony Remote", required: false
            input "rokuRemote", "device.RokuTV", title: "Roku Remote", required: false
            input "bedtimeNowPause", "bool", title: "Pause when Bedtime Now?", required: true, defaultValue: false
        }
        section("Alerts") {
            input "bedtimeSoonAlert", "bool", title: "Alert when Bedtime Soon?", required: true, defaultValue: false
            input "bedtimeNowAlert", "bool", title: "Alert when Bedtime Now?", required: true, defaultValue: false
            input "wakeUpAlert", "bool", title: "Alert when Wake Up?", required: true, defaultValue: false
            input "glowsOffAlert", "bool", title: "Alert when Glows Off?", required: true, defaultValue: false
        }
        section {
            input "person", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
            
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

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    // Routine Switches
    if (hueRemote) {
        subscribe(hueRemote, "pushed", hueRemoteHandler_RoutineSwitch)
    }
    if (harmonyRemote) {
        subscribe(harmonyRemote, "pushed", harmonyRemoteHandler_RoutineSwitch)
    }
    subscribe(location, "mode", modeHandler_RoutineSwitch)
    
    // Routine Alerts
    subscribe(bedtimeSoonRoutine, "switch.on", bedtimeSoonHandler_RoutineAlert)
    subscribe(bedtimeNowRoutine, "switch.on", bedtimeNowHandler_RoutineAlert)
    subscribe(wakeUpRoutine, "switch.on", wakeUpHandler_RoutineAlert)
    subscribe(glowsOffRoutine, "switch.on", glowsOffHandler_RoutineAlert)
    
    // Away Alerts
    subscribe(bedtimeSoonRoutine, "switch.on", handler_AwayAlert)
    subscribe(bedtimeNowRoutine, "switch.on", handler_AwayAlert)
    subscribe(wakeUpRoutine, "switch.on", handler_AwayAlert)
    
    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.bedtimeSoonUrl = "${getFullLocalApiServerUrl()}/bedtimeSoon?access_token=$state.accessToken"
    state.bedtimeNowUrl = "${getFullLocalApiServerUrl()}/bedtimeNow?access_token=$state.accessToken"
    state.wakeUpUrl = "${getFullLocalApiServerUrl()}/wakeUp?access_token=$state.accessToken"
    state.glowsOffUrl = "${getFullLocalApiServerUrl()}/glowsOff?access_token=$state.accessToken"
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def hueRemoteHandler_RoutineSwitch(evt) {
    logDebug("hueRemoteHandler_RoutineSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "1") {
        if (timeOfDayIsBetween(timeToday("00:00"), timeToday("12:00"), new Date(), location.timeZone)) {
            wakeUpRoutine.on()
        } else {
            bedtimeSoonRoutine.on()
        }
    } else if (evt.value == "2") {
        bedtimeNowRoutine.on()
    } else if (evt.value == "4") {
        glowsOffRoutine.on()
    }
}

def harmonyRemoteHandler_RoutineSwitch(evt) {
    logDebug("harmonyRemoteHandler_RoutineSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "1") {
        bedtimeSoonRoutine.on()
    } else if (evt.value == "2") {
        bedtimeNowRoutine.on()
    } else if (evt.value == "3") {
        wakeUpRoutine.on()
    } else if (evt.value == "4") {
        glowsOffRoutine.on()
    }
}

def modeHandler_RoutineSwitch(evt) {
    logDebug("modeHandler_RoutineSwitch: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        glowsOffRoutine.on()
    }
}

def bedtimeSoonHandler_RoutineAlert(evt) {
    logDebug("bedtimeSoonHandler_RoutineAlert: ${evt.device} changed to ${evt.value}")
    
    if (bedtimeSoonAlert) {
        person.deviceNotification("Bedtime Soon!")
    }
}

def bedtimeNowHandler_RoutineAlert(evt) {
    logDebug("bedtimeNowHandler_RoutineAlert: ${evt.device} changed to ${evt.value}")
    
    if (bedtimeNowAlert) {
        person.deviceNotification("Bedtime Now!")
    }
    
    if (bedtimeNowPause && rokuRemote) {
        rokuRemote.queryMediaPlayer()
        if (rokuRemote.currentValue("transportStatus") == "playing") {
            rokuRemote.pause()
        }
    }
}

def wakeUpHandler_RoutineAlert(evt) {
    logDebug("wakeUpHandler_RoutineAlert: ${evt.device} changed to ${evt.value}")
    
    if (wakeUpAlert) {
        person.deviceNotification("Wake Up!")
    }
}

def glowsOffHandler_RoutineAlert(evt) {
    logDebug("glowsOffHandler_RoutineAlert: ${evt.device} changed to ${evt.value}")
    
    if (glowsOffAlert) {
        person.deviceNotification("Glows Off!")
    }
}

def handler_AwayAlert(evt) {
    logDebug("handler_AwayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        person.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}

def urlHandler_bedtimeSoon(evt) {
    logDebug("urlHandler_bedtimeSoon")
    
    bedtimeSoonRoutine.on()
}

def urlHandler_bedtimeNow(evt) {
    logDebug("urlHandler_bedtimeNow")
    
    bedtimeNowRoutine.on()
}

def urlHandler_wakeUp(evt) {
    logDebug("urlHandler_wakeUp")
    
    wakeUpRoutine.on()
}

def urlHandler_glowsOff(evt) {
    logDebug("urlHandler_glowsOff")
    
    glowsOffRoutine.on()
}