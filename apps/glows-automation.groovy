/**
 *  Glows Automation
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
 
String getVersionNum() { return "1.0.0-beta.2" }
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
        section("Remote") {
            input "remote", "capability.pushableButton", title: "Button Device", required: false
        }
        section("Notifications") {
            input "bedtimeSoonAlert", "bool", title: "Alert when Bedtime Soon?", required: true, defaultValue: false
            input "bedtimeNowAlert", "bool", title: "Alert when Bedtime Now?", required: true, defaultValue: false
            input "wakeUpAlert", "bool", title: "Alert when Wake Up?", required: true, defaultValue: false
            input "glowsOffAlert", "bool", title: "Alert when Glows Off?", required: true, defaultValue: false
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
        }
        section {
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
            
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/bedtimeSoon") {
        action: [
            GET: "bedtimeSoonUrlHandler"
        ]
    }
    path("/bedtimeNow") {
        action: [
            GET: "bedtimeNowUrlHandler"
        ]
    }
    path("/wakeUp") {
        action: [
            GET: "wakeUpUrlHandler"
        ]
    }
    path("/glowsOff") {
        action: [
            GET: "glowsOffUrlHandler"
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
    subscribe(bedtimeSoonRoutine, "switch.on", bedtimeSoonHandler)
    subscribe(bedtimeNowRoutine, "switch.on", bedtimeNowHandler)
    subscribe(wakeUpRoutine, "switch.on", wakeUpHandler)
    subscribe(glowsOffRoutine, "switch.on", glowsOffHandler)
    
    if (remote) {
        subscribe(remote, "pushed", remoteHandler)
    }
    
    subscribe(location, "mode", modeHandler)
    
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

def bedtimeSoonHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (bedtimeSoonAlert) {
        notifier.deviceNotification("Bedtime Soon!")
    }
}

def bedtimeNowHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (bedtimeNowAlert) {
        notifier.deviceNotification("Bedtime Now!")
    }
}

def wakeUpHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (wakeUpAlert) {
        notifier.deviceNotification("Wake Up!")
    }
}

def glowsOffHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (glowsOffAlert) {
        notifier.deviceNotification("Glows Off!")
    }
}

def remoteHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
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

def modeHandler(evt) {
    logDebug("${evt.device} changed to ${evt.value}")
    
    if (evt.value == "Away") {
        glowsOffRoutine.on()
    }
}

def bedtimeSoonUrlHandler(evt) {
    logDebug("Bedtime Soon URL called")
    
    bedtimeSoonRoutine.on()
}

def bedtimeNowUrlHandler(evt) {
    logDebug("Bedtime Now URL called")
    
    bedtimeNowRoutine.on()
}

def wakeUpUrlHandler(evt) {
    logDebug("Wake Up URL called")
    
    wakeUpRoutine.on()
}

def glowsOffUrlHandler(evt) {
    logDebug("Glows Off URL called")
    
    glowsOffRoutine.on()
}