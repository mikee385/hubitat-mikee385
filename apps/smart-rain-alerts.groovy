/**
 *  Smart Rain Alerts
 *
 *  Copyright 2026 Michael Pierce
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
 
String getAppName() { return "Smart Rain Alerts" }
String getAppVersion() { return "0.8.0" }
String getAppTitle() { return "${getAppName()}, version ${getAppVersion()}" }

#include mikee385.debug-library
#include mikee385.device-monitor-library

definition(
    name: getAppName(),
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Alerts for rain based on probability and confidence",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/smart-rain-alerts.groovy"
)

preferences {
    page(name: "settings", title: getAppTitle(), install: true, uninstall: true) {
        section {
            input "weatherStation", "device.AmbientWeatherDevice", title: "Weather Station", multiple: false, required: true
        }
        section {
            input "deviceMonitor", "device.DeviceMonitor", title: "Device Monitor", multiple: false, required: true
            input "personToNotify", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "enableInfoLog", type: "bool", title: "Enable info logging?", defaultValue: false
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
    // Initialize State
    state.cfg = [
        rhConfMin: 60.0,
        rhConfSpan: 30.0,
        rhProbMin: 70.0,
        rhProbSpan: 25.0,
        
        dewNear: 2.0,
        dewFar: 5.0,
        
        vpdWet: 0.3,
        vpdDry: 1.0,
        
        rhTrendMax: 3.0,     // % per sample
        vpdTrendMax: 0.15,   // kPa per sample
        windTrendMax: 2.0,   // m/s per sample
        
        dryHoldMin: 5.0,
        dryHoldMax: 15.0,
        
        probAlertOn: 75.0,
        probAlertOff: 60.0,
        
        confAlertOn: 75.0
    ]
    
    state.clearScheduled = false
    state.rainConfirmed = state.rainConfirmed ?: false
    state.rainPredicted = state.rainPredicted ?: false
    
    // Rain Alert
    subscribe(weatherStation, "dateutc", sensorHandler)
    
    // Device Checks
    initializeDeviceChecks()
}

def logInfo(msg) {
    if (enableInfoLog) {
        log.info msg
    }
}

def sensorHandler(evt) {
    //logDebug("sensorHandler: ${evt.device} changed to ${evt.value}")
    
    calculate()
}

def calculate() {
    def cfg = state.cfg
    
    def tempF = weatherStation.currentValue("temperature")
    def rh = weatherStation.currentValue("humidity")
    def rainRateInHr = weatherStation.currentValue("precip_1hr")
    def windMPH = weatherStation.currentValue("wind")

    def tempC = (tempF - 32.0) * 5.0 / 9.0
    def windMS = windMPH * 0.44704
    def rainRaw = (rainRateInHr > 0.0)
    
    def prob = probabilityScore(tempC, rh, windMS)
    def conf = confidenceScore(tempC, rh, windMS, rainRaw)
    
    logDebug("probability: ${prob}, confidence : ${conf}")
    
    // Confirmation
    def rainConfirmed = (conf >= cfg.confAlertOn)
    def wasConfirmed = state.rainConfirmed ?: false

    if (rainConfirmed && !wasConfirmed) {
        unschedule("clearRainState")
        state.clearScheduled = false
        
        msg = "🌧️ Rain confirmed: ${conf}%"
        logInfo(msg)
        sendAlert(msg)
        
        state.rainConfirmed = true
    }

    if (wasConfirmed && !rainConfirmed && !state.clearScheduled) { 
        def vpd = vaporPressureDeficit(tempC, rh)
        def hold = dryingHoldMinutes(vpd)
        
        msg = "☀️ Rain has stopped, waiting ${hold} minutes"
        logInfo(msg)
        sendAlert(msg)
        
        state.clearScheduled = true
        runIn(hold * 60, "clearRainState")
    }
    
    // Prediction
    def rainLikelySoon = (prob >= cfg.probAlertOn)
    def wasPredicted = state.rainPredicted ?: false
    
    if (rainLikelySoon && !wasPredicted) {
        if (!rainConfirmed && !wasConfirmed) {
            msg = "🌧️ Rain likely soon: ${prob}%"
            logInfo(msg)
            sendAlert(msg)
        } 
        state.rainPredicted = true
    }

    if (wasPredicted && prob < cfg.probAlertOff) {
        state.rainPredicted = false
    }
}

def clamp(val, minVal, maxVal) {
    return Math.max(minVal, Math.min(maxVal, val))
}

def saturationVaporPressure(tempC) {
    return 0.6108 * Math.exp((17.27 * tempC) / (tempC + 237.3))
}

def vaporPressureDeficit(tempC, rh) {
    def es = saturationVaporPressure(tempC)
    def e  = (rh / 100.0) * es
    return es - e
}

def dewPoint(tempC, rh) {
    def alpha = Math.log(rh / 100.0) + (17.67 * tempC) / (243.5 + tempC)
    return (243.5 * alpha) / (17.67 - alpha)
}

def confidenceScore(tempC, rh, wind, rainRaw) {
    if (!rainRaw) return 0.0

    def cfg = state.cfg
    def vpd = vaporPressureDeficit(tempC, rh)
    def dew = dewPoint(tempC, rh)
    def deltaT = tempC - dew

    def sRH = clamp(
        (rh - cfg.rhConfMin) / cfg.rhConfSpan,
        0.0, 1.0
    )

    def sDew =
        (deltaT <= cfg.dewNear) ? 1.0 :
        (deltaT >= cfg.dewFar)  ? 0.0 :
        (cfg.dewFar - deltaT) / (cfg.dewFar - cfg.dewNear)

    def sVPD =
        (vpd <= cfg.vpdWet) ? 1.0 :
        (vpd >= cfg.vpdDry) ? 0.0 :
        (cfg.vpdDry - vpd) / (cfg.vpdDry - cfg.vpdWet)

    def sWind = (wind <= 0.5) ? 0.8 : 1.0

    return 100.0 * (
        0.45 * sRH +
        0.35 * sDew +
        0.15 * sVPD +
        0.05 * sWind
    )
}

def probabilityScore(tempC, rh, wind) {
    def cfg = state.cfg
    def vpd = vaporPressureDeficit(tempC, rh)

    def prevRH   = state.prevRH   ?: rh
    def prevVPD  = state.prevVPD  ?: vpd
    def prevWind = state.prevWind ?: wind

    def dRH   = rh - prevRH
    def dVPD  = prevVPD - vpd
    def dWind = wind - prevWind

    def sRHabs = clamp(
        (rh - cfg.rhProbMin) / cfg.rhProbSpan,
        0.0, 1.0
    )

    def sRHtrend = clamp(dRH / cfg.rhTrendMax, 0.0, 1.0)
    def sVPDtrend = clamp(dVPD / cfg.vpdTrendMax, 0.0, 1.0)
    def sWindTrend = clamp(dWind / cfg.windTrendMax, 0.0, 1.0)

    def raw =
        0.40 * sRHabs +
        0.30 * sRHtrend +
        0.20 * sVPDtrend +
        (hasWind ? 0.10 * sWindTrend : 0.0)

    def maxWeight = hasWind ? 1.0 : 0.90
    def normalized = (raw / maxWeight) * 100.0

    state.prevRH = rh
    state.prevVPD = vpd
    state.prevWind = wind

    return clamp(normalized, 0.0, 100.0)
}

def dryingHoldMinutes(vpd) {
    def cfg = state.cfg
    def t = cfg.dryHoldMax -
            (cfg.dryHoldMax - cfg.dryHoldMin) * (vpd / cfg.vpdDry)

    return clamp(t, cfg.dryHoldMin, cfg.dryHoldMax)
}

def clearRainState() {
    state.clearScheduled = false
    state.rainConfirmed = false
    state.rainPredicted = false
    
    msg = "☀️ Sensor is dry"
    logInfo(msg)
    sendAlert(msg)
}

def sendAlert(msg) {
    personToNotify.deviceNotification(msg)
}