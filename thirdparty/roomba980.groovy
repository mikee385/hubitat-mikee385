/**
*  iRobot Roomba v2.0
*. 900 series - Virtual Switch
*
*  Copyright 2016 Steve-Gregory
*  Modified by Adrian Caramaliu to add support for v2 local API
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

/*Known NotReady states*/
def getRoombaStates() {
    def ROOMBA_READY = 0
    def ROOMBA_STUCK = 1
    def ROOMBA_BIN_FULL = 16
    def ROOMBA_NOT_UPRIGHT = 7
    def ROOMBA_IN_THE_DARK = 8
    def ROOMBA_STATES = ['ready': ROOMBA_READY, 'stuck': ROOMBA_STUCK, 'full': ROOMBA_BIN_FULL, 'tilted': ROOMBA_NOT_UPRIGHT, 'dark': ROOMBA_IN_THE_DARK]
    return ROOMBA_STATES
}
metadata {
    definition (name: "Roomba980", namespace: "ady624", author: "Steve Gregory & Adrian Caramaliu") {
        capability "Battery"
        capability "Switch"
        capability "Refresh"
        capability "Polling"
        capability "Consumable"
        capability "Timed Session"
        capability "Configuration"

        command "dock"
        command "resume"
        command "pause"
        command "cancel"
        command "pauseAndDock"

        attribute "totalJobs", "number"
        attribute "totalJobHrs", "number"
        attribute "headline", "string"
        attribute "robotName", "string"
        attribute "preferences_set", "string"
        attribute "status", "string"
        //For ETA heuristic
        attribute "lastSqft", "number"
        attribute "lastRuntime", "number"
        attribute "lastDate", "string"
    }
}
// simulator metadata
simulator {
}
//Preferences
preferences {
    section("Cloud Roomba API Type") {
        input "localAPI", "bool", title: "Use a local REST gateway for Roomba", description: "Enable this if you have installed a local REST gateway for Roomba, you will need to provide the IP of that gateway", displayDuringSetup: true
    }
    section("Roomba Local Settings") {
    input type: "paragraph", title: "Fill these parameters if using a local REST gateway"
        input "roomba_host", "string", title:"IP of Roomba local REST Gateway", displayDuringSetup: true
        input "roomba_port", "number", range: "1..65535", defaultValue: 3000, title:"IP of Roomba local REST Gateway", displayDuringSetup: true
    }
    section("Roomba Cloud Credentials") {
        input type: "paragraph", title: "Please fill in the Roomba credentials below if using a Cloud connection to your robot", description: "The username/password can be retrieved via node.js & dorita980", displayDuringSetup: true
        input "roomba_username", "text", title: "Roomba username/blid", displayDuringSetup: true
        input "roomba_password", "password", title: "Roomba password", displayDuringSetup: true
    }
    section("Misc.") {
        input type: "paragraph", title: "Polling Interval [minutes]", description: "This feature allows you to change the frequency of polling for the robot in minutes (1-59)"
        input "pollInterval", "number", title: "Polling Interval", description: "Change polling frequency (in minutes)", defaultValue:4, range: "1..59", required: true, displayDuringSetup: true
        input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
    }
}
// UI tile definitions
tiles {
    multiAttributeTile(name:"CLEAN", type:"generic", width: 6, height: 4, canChangeIcon: true) {
        tileAttribute("device.status", key: "PRIMARY_CONTROL") {
            attributeState "error", label: 'Error', icon: "st.switches.switch.off", backgroundColor: "#bc2323"
            attributeState "bin-full", label: 'Bin Full', icon: "st.switches.switch.off", backgroundColor: "#bc2323"
            attributeState "docked", label: 'Start Clean', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "starting"
            attributeState "docking", label: 'Docking', icon: "st.switches.switch.off", backgroundColor: "#ffa81e"
            attributeState "starting", label: 'Starting Clean', icon: "st.switches.switch.off", backgroundColor: "#ffffff"
            attributeState "cleaning", label: 'Stop Clean', action: "stop", icon: "st.switches.switch.on", backgroundColor: "#79b821", nextState: "pausing"
            attributeState "pausing", label: 'Stop Clean', icon: "st.switches.switch.on", backgroundColor: "#79b821"
            attributeState "paused", label: 'Send Home', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821", nextState: "docking"
            attributeState "resuming", label: 'Stop Clean', icon: "st.switches.switch.on", backgroundColor: "#79b821"
        }
        tileAttribute("device.headline", key: "SECONDARY_CONTROL") {
           attributeState "default", label:'${currentValue}'
        }
    }
    valueTile("DOCK", "device.status", width: 2, height: 2) {
        state "docked", label: 'Docked', backgroundColor: "#79b821"
        state "docking", label: 'Docking', backgroundColor: "#ffa81e"
        state "starting", label: 'UnDocking', backgroundColor: "#ffa81e"
        state "cleaning", label: 'Not on Dock', backgroundColor: "#ffffff", nextState: "docking", action: "dock"
        state "pausing", label: 'Not on Dock', backgroundColor: "#ffffff", nextState: "docking", action: "dock"
        state "paused", label: 'Dock', backgroundColor: "#ffffff", nextState: "docking", action: "dock"
        state "bin-full", label: 'Bin full', backgroundColor: "#bc2323"
        state "resuming", label: 'Not on Dock', backgroundColor: "#ffffff", defaultState: true, action: "dock"
    }
    valueTile("PAUSE", "device.status", width: 2, height: 2) {
        state "docked", label: 'Pause', backgroundColor: "#ffffff", defaultState: true
        state "docking", label: 'Pause', backgroundColor: "#ffffff"
        state "starting", label: 'Pause', backgroundColor: "#ffffff", action: "pause"
        state "cleaning", label: 'Pause', backgroundColor: "#ffffff", action: "pause"
        state "pausing", label: 'Pausing..', backgroundColor: "#79b821"
        state "paused", label: 'Paused', backgroundColor: "#79b821"
        state "bin-full", label: 'Bin full', backgroundColor: "#bc2323"
        state "resuming", label: 'Pause', backgroundColor: "#ffffff", action: "pause"
    }
    valueTile("RESUME", "device.status", width: 2, height: 2) {
        state "docked", label: 'Resume', backgroundColor: "#ffffff", defaultState: true
        state "docking", label: 'Resume', backgroundColor: "#ffffff"
        state "starting", label: 'Resume', backgroundColor: "#ffffff"
        state "cleaning", label: 'Resume', backgroundColor: "#ffffff"
        state "pausing", label: 'Resume', backgroundColor: "#79b821", action: "resume"
        state "paused", label: 'Resume', backgroundColor: "#ffffff", action: "resume"
        state "bin-full", label: 'Bin full', backgroundColor: "#bc2323"
        state "resuming", label: 'Resuming..', backgroundColor: "#79b821"
    }
    standardTile("refresh", "device.status", width: 4, height: 2, decoration: "flat") {
        state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
    }
    valueTile("battery", "device.battery", width: 2, height: 2, decoration: "flat") {
        state "default", label:'Battery ${currentValue}%'
    }

valueTile("job_count", "device.totalJobs", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Number of Cleaning Jobs:\n${currentValue} jobs'
    }
    valueTile("job_hr_count", "device.totalJobHrs", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Total Job Time:\n${currentValue} hours'
    }
    valueTile("current_job_time", "device.runtimeMins", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Current Job Runtime:\n${currentValue} minutes'
    }
    valueTile("current_job_sqft", "device.runtimeSqft", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Current Job Sqft:\n${currentValue} ft'
    }
    valueTile("current_job_time_estimated", "device.timeRemaining", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Estimated Completion Time:\n${currentValue} minutes'
    }
    valueTile("current_job_sqft_estimated", "device.sqftRemaining", width: 3, height: 1, decoration: "flat") {
        state "default", label:'Estimated Sqft Remaining:\n${currentValue} ft'
    }
    main "CLEAN"
    details(["STATUS",
             "CLEAN", "DOCK", "PAUSE", "RESUME",
             "refresh",
             "battery",
             "current_job_time", "current_job_time_estimated",
             "current_job_sqft", "current_job_sqft_estimated",
             "job_hr_count", "job_count"
             ])
}
def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}
// Settings updated
def updated() {
    //log.debug "Updated settings ${settings}..
    schedule("0 0/${settings.pollInterval} * * * ?", poll)  // 4min polling is normal for irobots
    poll()
}
// Configuration
def configure() {
    logDebug("Configuring..")
    poll()
}
//Timed Session
def setTimeRemaining(timeNumber) {
    logDebug("User requested setting the Time remaining to ${timeNumber}")
    return
}
//Consumable
def setConsumableStatus(statusString) {
    logDebug("User requested setting the Consumable Status - ${statusString}")
    def status = device.latestValue("status")
    logDebug("Setting value based on last roomba state - ${status}")

    if(roomba_value == "bin-full") {
        // Optionally this could be 'replace'?
        state.consumable = "maintenance_required"
    } else if(roomba_value == "error"){
        state.consumable = "missing"
    } else {
        state.consumable = "good"
    }
    return state.consumable
}
//Refresh
def refresh() {
    logDebug("Executing 'refresh'")
    return poll()
}
//Polling
def pollHistory() {
    logDebug("Polling for missionHistory ----")
    sendEvent(name: "headline", value: "Polling history API", displayed: false)
    state.RoombaCmd = "missionHistory"
    return localAPI ? null : apiGet()
}
def poll() {
    //Get historical data first
    pollHistory()
    //Then poll for current status
    logDebug("Polling for status ----")
    sendEvent(name: "headline", value: "Polling status API", displayed: false)
    state.RoombaCmd = "getStatus"
    return localAPI ? local_poll() : apiGet()
}
// Switch methods
def on() {
    // Always start roomba
    def status = device.latestValue("status")
    logDebug("On based on state - ${status}")
    if(status == "paused") {
   return resume()
    } else {
   return start()
    }
}
def off() {
    // Always return to dock..

    def status = device.latestValue("status")
    logDebug("Off based on state - ${status}")
    if(status == "paused") {
    return dock()
    } else {
   return pauseAndDock()
    }
}
// Timed Session
def start() {
    sendEvent(name: "status", value: "starting")
    state.RoombaCmd = "start"
    runIn(15, poll)
return localAPI ? local_start() : apiGet()
}
def stop() {
    sendEvent(name: "status", value: "stopping")
    state.RoombaCmd = "stop"
    runIn(15, poll)
    return localAPI ? local_stop() : apiGet()
}
def pauseAndDock() {
    sendEvent(name: "status", value: "pausing")
    state.RoombaCmd = "pause"
    return localAPI ? local_pauseAndDock() : apiGet()
}
def pause() {
    sendEvent(name: "status", value: "pausing")
    state.RoombaCmd = "pause"
    runIn(15, poll)
    return localAPI ? local_pause() : apiGet()
}
def cancel() {
return off()
}

// Actions
def dock() {
    sendEvent(name: "status", value: "docking")
    state.RoombaCmd = "dock"
    runIn(15, poll)
return localAPI ? local_dock() : apiGet()
}
def resume() {
    sendEvent(name: "status", value: "resuming")
    state.RoombaCmd = "resume"
    runIn(15, poll)
    return localAPI ? local_resume() : apiGet()
}
// API methods
def parse(description) {
//log.trace "GOT HERE"
    def msg = parseLanMessage(description)
    //log.trace "GOT MSG $msg"
    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)
}

def apiGet() {
if (local) return
    def request_query = ""
    def request_host = ""
    def encoded_str = "${roomba_username}:${roomba_password}".bytes.encodeBase64()

    //Handle prefrences
    if("${roomba_host}" == "" || "${roomba_host}" == "null") {
        request_host = "https://irobot.axeda.com"
    } else {
        logDebug("Using Roomba Host: ${roomba_host}")
        request_host = "${roomba_host}"
    }

    //Validation before calling the API
    if(!roomba_username || !roomba_password) {
        def new_status = "Username/Password not set. Configure required before using device."
        sendEvent(name: "headline", value: new_status, displayed: false)
        sendEvent(name: "preferences_set", value: "missing", displayed: false)
        return
    } else if(state.preferences_set != "missing") {
        sendEvent(name: "preferences_set", value: "ready", displayed: false)
    }

    state.AssetID = "ElPaso@irobot!${roomba_username}"
    state.Authorization = "${encoded_str}"

    // Path (No changes required)
    def request_path = "/services/v1/rest/Scripto/execute/AspenApiRequest"
    // Query manipulation
    if( state.RoombaCmd == "getStatus" || state.RoombaCmd == "accumulatedHistorical" || state.RoombaCmd == "missionHistory") {
        request_query = "?blid=${roomba_username}&robotpwd=${roomba_password}&method=${state.RoombaCmd}"
    } else {
        request_query = "?blid=${roomba_username}&robotpwd=${roomba_password}&method=multipleFieldSet&value=%7B%0A%20%20%22remoteCommand%22%20:%20%22${state.RoombaCmd}%22%0A%7D"
    }

    def requestURI = "${request_host}${request_path}${request_query}"
    def httpRequest = [
        method:"GET",
        uri: "${requestURI}",
        headers: [
            'User-Agent': 'aspen%20production/2618 CFNetwork/758.3.15 Darwin/15.4.0',
            Accept: '*/*',
            'Accept-Language': 'en-us',
            'ASSET-ID': state.AssetID,
        ]
    ]
    try {
        httpGet(httpRequest) { resp ->
            logDebug("response Headers:" + resp.headers.collect { "${it.name}:${it.value}" })
            logDebug("response contentType: ${resp.contentType}")
            logDebug("response data: ${resp.data}")
            parseResponseByCmd(resp, state.RoombaCmd)
        }
    } catch (e) {
        log.error "something went wrong: $e"
    }
}

def parseResponseByCmd(resp, command) {
    def data = resp.data
    if(command == "getStatus") {
        setStatus(data)
    } else if(command == "accumulatedHistorical" ) {
        /*readSummaryInfo -- same as getStatus but easier to parse*/
    } else if(command == "missionHistory") {
        setMissionHistory(data)
    }
}
def convertDate(dateStr) {
    return Date.parse("yyyyMMdd H:m", dateStr)
}
def setMissionHistory(data) {
    def lastRuntime = -1
    def lastSqft = -1
    def lastDate = ""
    def mstatus = data.status
    def robot_history = data.missions

    robot_history.sort{ convertDate(it.date) }.each{ mission ->
        if(mission.done == 'ok') {
            lastSqft = mission.sqft
            lastRuntime = mission.runM
            lastDate = mission.date
        }
    }

    state.lastRuntime = lastRuntime
    state.lastSqft = lastSqft
    state.lastDate = lastDate

    sendEvent(name: "lastRuntime", value: state.lastRuntime, displayed: false)
    sendEvent(name: "lastSqft", value: state.lastSqft, displayed: false)
    sendEvent(name: "lastDate", value: state.lastDate, displayed: false)
}

def setStatus(data) {
    def rstatus = data.robot_status
    def robotName = data.robotName
state.robotName = robotName

def mission = data.mission
    def runstats = data.bbrun
    def cschedule = data.cleanSchedule
    def pmaint = data.preventativeMaintenance
    def robot_status = new groovy.json.JsonSlurper().parseText(rstatus)
    def robot_history = new groovy.json.JsonSlurper().parseText(mission)
    def runtime_stats = new groovy.json.JsonSlurper().parseText(runstats)
    def schedule = new groovy.json.JsonSlurper().parseText(cschedule)
    def maintenance = new groovy.json.JsonSlurper().parseText(pmaint)
    logDebug("Robot status = ${robot_status}")
    logDebug("Robot history = ${robot_history}")
    logDebug("Runtime stats= ${runtime_stats}")
    logDebug("Robot schedule= ${schedule}")
    logDebug("Robot maintenance= ${maintenance}")
    def current_cycle = robot_status['cycle']
    def current_charge = robot_status['batPct']
    def current_phase = robot_status['phase']
    def current_sqft = robot_status['sqft']
    def num_mins_running = robot_status['mssnM']
    def flags = robot_status['flags']  // Unknown what 'Flags' 0/1/2/5 mean?
    def readyCode = robot_status['notReady']
    def num_cleaning_jobs = robot_history['nMssn']
    def num_dirt_detected = runtime_stats['nScrubs']
    def total_job_time = runtime_stats['hr']
    

    def new_status = get_robot_status(current_phase, current_cycle, current_charge, readyCode)
    def roomba_value = get_robot_enum(current_phase, readyCode)

    logDebug("Robot updates -- ${roomba_value} + ${new_status}")
    //Set the state object
    if(roomba_value == "cleaning") {
        state.switch = "on"
    } else {
        state.switch = "off"
    }

    /* Consumable state-changes */
    if(roomba_value == "bin-full") {
        state.consumable = "maintenance_required"
    } else if(roomba_value == "error"){
        state.consumable = "missing"
    } else {
        state.consumable = "good"
    }

    /* Timed Session state-changes */
    if(roomba_value == "cleaning") {
        state.sessionStatus = "running"
    } else if (roomba_value == "paused") {
        state.sessionStatus = "paused"
    } else if (roomba_value == "docked" || roomba_value == "docking") {
        state.sessionStatus = "canceled"
    } else {
        state.sessionStatus = "stopped"
    }

    /* Misc. state-changes */
    if(state.lastRuntime == -1) {
        state.timeRemaining = -1
    } else {
        state.timeRemaining = state.lastRuntime - num_mins_running
    }
    if(state.lastSqft == -1) {
        state.sqftRemaining = -1
    } else {
        state.sqftRemaining = state.lastSqft - current_sqft
    }

    /*send events, display final event*/
    sendEvent(name: "robotName", value: robotName, displayed: false)
    sendEvent(name: "runtimeMins", value: num_mins_running, displayed: false)
    sendEvent(name: "runtimeSqft", value: current_sqft, displayed: false)
    sendEvent(name: "timeRemaining", value: state.timeRemaining, displayed: false)
    sendEvent(name: "sqftRemaining", value: state.sqftRemaining, displayed: false)
    sendEvent(name: "totalJobHrs", value: total_job_time, displayed: false)
    sendEvent(name: "totalJobs", value: num_cleaning_jobs, displayed: false)
    sendEvent(name: "battery", value: current_charge, displayed: false)
    sendEvent(name: "headline", value: new_status, displayed: false)
    sendEvent(name: "status", value: roomba_value)
    sendEvent(name: "switch", value: state.switch)
    sendEvent(name: "sessionStatus", value: state.sessionStatus)
    sendEvent(name: "consumable", value: state.consumable)
}

def get_robot_enum(current_phase, readyCode) {
    def ROOMBA_STATES = getRoombaStates()

    if(readyCode != ROOMBA_STATES['ready']) {
        if(readyCode == ROOMBA_STATES['full']) {
            return "bin-full"
        } else if(readyCode != ROOMBA_STATES['dark']) {
            return "error"
        }
    }

    if(current_phase == "charge") {
        return "docked"
    } else if(current_phase == "hmUsrDock") {
        return "docking"
    } else if(current_phase == "pause" || current_phase == "stop") {
        return "paused"
    } else if(current_phase == "run") {
        return "cleaning"
    } else {
        //"Stuck" phase falls into this category.
        log.error "Unknown phase - Raw 'robot_status': ${status}. Add to 'get_robot_enum'"
        return "error"
    }
}
def parse_not_ready_status(readyCode) {
    def robotName = state.robotName
    def ROOMBA_STATES = getRoombaStates()

    if(readyCode == ROOMBA_STATES['full']) {
      return "${robotName}'s bin is full. Empty bin to continue."
    } else if(readyCode == ROOMBA_STATES['tilted']) {
      return "${robotName} is not upright. Place robot on flat surface to continue."
    } else if (readyCode == ROOMBA_STATES['stuck']) {
      return "${robotName} is stuck. Move robot to continue."
    } else {
      return "${robotName} returned notReady=${readyCode}. See iRobot app for details."
    }
}

def get_robot_status(current_phase, current_cycle, current_charge, readyCode) {
    def robotName = state.robotName
    def ROOMBA_STATES = getRoombaStates()

    // 0 and 8 are "okay to run"
    if(readyCode != ROOMBA_STATES['ready'] && readyCode != ROOMBA_STATES['dark']) {
      return parse_not_ready_status(readyCode)
    } else if(current_phase == "charge") {
        if (current_charge == 100) {
            return "${robotName} is Docked/Fully Charged"
        } else {
            return "${robotName} is Docked/Charging"
        }
    } else if(current_phase == "hmUsrDock") {
        return "${robotName} is returning home"
    } else if(current_phase == "run") {
        return "${robotName} is cleaning (${current_cycle} cycle)"
    } else if(current_phase == "pause" || current_phase == "stop") {
        return "Paused - 'Dock' or 'Resume'?"
    }

    log.error "Unknown phase - ${current_phase}."
    return "Error - refresh to continue. Code changes required if problem persists."
}

private local_get(path, cbk) {
//def host = "$roomba_host:$roomba_port"
//new hubitat.device.HubAction("""GET $path HTTP/1.1\r\nHOST: $host\r\n\r\n""", hubitat.device.Protocol.LAN, null, [callback: cbk])
    
    httpGet([
        uri: "http://$roomba_host:$roomba_port$path"
    ]){ resp -> 
        "$cbk"(resp.data)
    }   
}

void local_dummy_cbk(data) {
}

void local_poll_cbk(data) {
    def current_charge = data.batPct
    def robotName = data.name
state.robotName = robotName    
    def mission = data.cleanMissionStatus
    def current_cycle = mission.cycle
    def current_phase = mission.phase
    def current_sqft = mission.sqft
    def num_mins_running = mission.mssnM
    def readyCode = mission.notReady
    def num_cleaning_jobs = mission.nMssn
    def num_dirt_detected = data.bbrun?.nScrubs ?: -1
    def total_job_time = data.bbrun?.hr ?: -1
    

    def new_status = get_robot_status(current_phase, current_cycle, current_charge, readyCode)
    def roomba_value = get_robot_enum(current_phase, readyCode)

    logDebug("Robot updates -- ${roomba_value} + ${new_status}")
    //Set the state object
    if(roomba_value == "cleaning") {
        state.switch = "on"
    } else {
        state.switch = "off"
    }   

    /* Consumable state-changes */
    if(roomba_value == "bin-full") {
        state.consumable = "maintenance_required"
    } else if(roomba_value == "error"){
        state.consumable = "missing"
    } else {
        state.consumable = "good"
    }

    /* Timed Session state-changes */
    if(roomba_value == "cleaning") {
        state.sessionStatus = "running"
    } else if (roomba_value == "paused") {
        state.sessionStatus = "paused"
    } else if (roomba_value == "docked" || roomba_value == "docking") {
        state.sessionStatus = "canceled"
    } else {
        state.sessionStatus = "stopped"
    }

    /* Misc. state-changes */
    if(state.lastRuntime == -1) {
        state.timeRemaining = -1
    } else {
        state.timeRemaining = (state.lastRuntime ?: num_mins_running) - num_mins_running
    }
    if(state.lastSqft == -1) {
        state.sqftRemaining = -1
    } else {
        state.sqftRemaining = (state.lastSqft ?: current_sqft) - current_sqft
    }

    /*send events, display final event*/
    sendEvent(name: "robotName", value: robotName, displayed: false)
    sendEvent(name: "runtimeMins", value: num_mins_running, displayed: false)
    sendEvent(name: "runtimeSqft", value: current_sqft, displayed: false)
    sendEvent(name: "timeRemaining", value: state.timeRemaining, displayed: false)
    sendEvent(name: "sqftRemaining", value: state.sqftRemaining, displayed: false)
    sendEvent(name: "totalJobHrs", value: total_job_time, displayed: false)
    sendEvent(name: "totalJobs", value: num_cleaning_jobs, displayed: false)
    sendEvent(name: "battery", value: current_charge, displayed: false)
    sendEvent(name: "headline", value: new_status, displayed: false)
    sendEvent(name: "status", value: roomba_value)
    sendEvent(name: "switch", value: state.switch)
    sendEvent(name: "sessionStatus", value: state.sessionStatus)
    sendEvent(name: "consumable", value: state.consumable)    
}

private local_poll() {
local_get('/api/local/info/state', 'local_poll_cbk')
}

private local_start() {
local_get('/api/local/action/start', 'local_dummy_cbk')
}

private local_stop() {
local_get('/api/local/action/stop', 'local_dummy_cbk')
}

private local_pause() {
local_get('/api/local/action/pause', 'local_dummy_cbk')
}

private local_resume() {
local_get('/api/local/action/resume', 'local_dummy_cbk')
}

private local_dock() {
local_get('/api/local/action/dock', 'local_dummy_cbk')
}

private local_pauseAndDock() {
local_get('/api/local/action/pause', 'local_dummy_cbk')
    wait(100)
local_get('/api/local/action/dock', 'local_dummy_cbk')
}