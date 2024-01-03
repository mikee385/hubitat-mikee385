/**
 *  Echo Glow Automation
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
 
String getVersionNum() { return "12.0.2" }
String getVersionLabel() { return "Echo Glow Automation, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library

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
        List globalVars = getGlobalVarsByType("datetime").collect{it.key}.sort()
            
        section("Routines") {
            input "echoGlowRoutines", "device.EchoGlowRoutines", title: "Echo Glow Routines", multiple: false, required: true
        }
        section("Kids") {
            input "kidPerson", "device.PersonStatus", title: "Kid", multiple: false, required: false
        }
        section("Media Devices") {
            input "rokuDevicesToPause", "device.RokuTV", title: "Roku Devices to Pause", multiple: true, required: false
            input "rokuDevicesToTurnOff", "device.RokuTV", title: "Roku Devices to Turn Off", multiple: true, required: false
        }
        section("Bedtime 1") {
            input "time1Variable", "enum", title: "Hub Variable", multiple: false, required: false, options: globalVars
            
            input "time1", "time", title: "Time", required: false, defaultValue: "18:55"
        }
        section("Bedtime 2") {
            input "time2Variable", "enum", title: "Hub Variable", multiple: false, required: false, options: globalVars
            
            input "sundayTime2", "time", title: "Sunday Time", required: false, defaultValue: "19:10"
            input "mondayTime2", "time", title: "Monday Time", required: false, defaultValue: "19:10"
            input "tuesdayTime2", "time", title: "Tuesday Time", required: false, defaultValue: "19:10"
            input "wednesdayTime2", "time", title: "Wednesday Time", required: false, defaultValue: "19:10"
            input "thursdayTime2", "time", title: "Thursday Time", required: false, defaultValue: "19:10"
            input "fridayTime2", "time", title: "Friday Time", required: false, defaultValue: "19:25"
            input "saturdayTime2", "time", title: "Saturday Time", required: false, defaultValue: "19:25"
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
    removeAllInUseGlobalVar()
    
    for (device in getChildDevices()) {
        deleteChildDevice(device.deviceNetworkId)
    }
}

def updated() {
    unsubscribe()
    unschedule()
    removeAllInUseGlobalVar()
    
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
    
    // People
    if (kidPerson) {
        subscribe(kidPerson, "sleeping", personHandler_GlowsOff)
    } 

    // Modes
    subscribe(location, "mode", modeHandler_Routine)
    
    // Daily Schedule
    if (time1Variable) {
        addInUseGlobalVar(time1Variable)
    } 
    if (time2Variable) {
        addInUseGlobalVar(time2Variable)
    }
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
    if (time1Variable) {
        subscribe(location, "variable:${time1Variable}", variableHandler_Time1)
    }
    if (time2Variable) {
        subscribe(location, "variable:${time2Variable}", variableHandler_Time2)
    } 
    
    resetBedtime()
        
    def resetToday = timeToday("00:01")
    def currentTime = new Date()
    schedule("$currentTime.seconds $resetToday.minutes $resetToday.hours * * ? *", resetBedtime)
}

def resetBedtime() {
    if (time1) {
        if (time1Variable) {
            setGlobalVar(time1Variable, time1)
        }
        scheduleBedtime1(time1)
    }
    
    def currentTime = new Date()
    def currentDay = currentTime[Calendar.DAY_OF_WEEK]
    
    def time2 = null
    if (currentDay == 1 && sundayTime2) {
        time2 = sundayTime2
    } else if (currentDay == 2 && mondayTime2) {
        time2 = mondayTime2
    } else if (currentDay == 3 && tuesdayTime2) {
        time2 = tuesdayTime2
    } else if (currentDay == 4 && wednesdayTime2) {
        time2 = wednesdayTime2
    } else if (currentDay == 5 && thursdayTime2) {
        time2 = thursdayTime2
    } else if (currentDay == 6 && fridayTime2) {
        time2 = fridayTime2
    } else if (currentDay == 7 && saturdayTime2) {
        time2 = saturdayTime2
    }
    
    if (time2) {
        if (time2Variable) {
            setGlobalVar(time2Variable, time2)
        }
        scheduleBedtime2(time2)
    } 
}

def scheduleBedtime1(time1Value) {
    def time1Today = timeToday(time1Value)
    schedule("0 $time1Today.minutes $time1Today.hours * * ? *", bedtimeSoon1)
}

def scheduleBedtime2(time2Value) {
    def time2Today = timeToday(time2Value)
    schedule("0 $time2Today.minutes $time2Today.hours * * ? *", bedtimeSoon2)
}

def variableHandler_Time1(evt) {
    logDebug("variableHandler_Time1: ${evt.device} changed to ${evt.value}")
    
    scheduleBedtime1(evt.value)
}

def variableHandler_Time2(evt) {
    logDebug("variableHandler_Time2: ${evt.device} changed to ${evt.value}")
    
    scheduleBedtime2(evt.value)
}

def bedtimeSoon1() {
    echoGlowRoutines.bedtimeSoon()
}

def bedtimeSoon2() {
    echoGlowRoutines.bedtimeSoon()
}

def routineHandler_BedtimeSoon(evt) {
    logDebug("routineHandler_BedtimeSoon: ${evt.device} changed to ${evt.value}")
    
    if (now() < timeToday(time1).getTime() + 1*60*1000) {
        unschedule("bedtimeSoon1")
    } else {
        unschedule("bedtimeSoon2")
    }
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
    
    if (now() < timeToday(time1).getTime() + 6*60*1000) {
        unschedule("bedtimeSoon1")
    } else {
        unschedule("bedtimeSoon2")
    }
    unschedule("glowsOff")
    
    if (state.lastRoutine != "BedtimeNow") {
        if (bedtimeNowAlert) {
            personToNotify.deviceNotification("Bedtime Now!")
        }
        
        if (rokuDevicesToPause) {
            for (rokuDevice in rokuDevicesToPause) {
                rokuDevice.queryMediaPlayer()
            }
            runIn(2, pauseRoku)
        }
        
        for (rokuDevice in rokuDevicesToTurnOff) {
            rokuDevice.off()
        }
        
        state.lastRoutine = "BedtimeNow"
    }
}

def pauseRoku() {
    for (rokuDevice in rokuDevicesToPause) {
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
    
    if (!kidPerson || kidPerson.currentValue("sleeping") == "not sleeping") {
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

def personHandler_GlowsOff(evt) {
    logDebug("personHandler_GlowsOff: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "not sleeping") {
        runIn(10*60, glowsOff)
    } else {
        unschedule("glowsOff")
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
        unschedule("bedtimeSoon1")
        unschedule("bedtimeSoon2")
        unschedule("scheduleBedtime")
    } 
}

def urlHandler_bedtimeSoon(evt) {
    logDebug("urlHandler_bedtimeSoon")
    
    bedtimeSoon1()
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