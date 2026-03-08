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
String getAppVersion() { return "0.55.0" }
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
        section("Sensors") {
            input "weatherStation", "device.AmbientWeatherDevice", title: "Weather Station", multiple: false, required: true
        }
        section("Logs") {
            input "enableSensorLog", "bool", title: "Log Sensor Data?", required: true, defaultValue: false
            input "enableScoreLog", "bool", title: "Log Score Calculations?", required: true, defaultValue: false
            input "enableStateLog", "bool", title: "Log State?", required: true, defaultValue: false
            input "enableStalenessLog", "bool", title: "Log Staleness Checks?", required: true, defaultValue: false
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
    
    state.cfg = [

        // ─────────────────────────────────────────────
        // Relative Humidity thresholds (% RH)
        // Used for absolute humidity scoring
        // ─────────────────────────────────────────────
        rhConfMin  : 72.0,   // % RH where confidence begins contributing
        rhConfSpan : 25.0,   // % RH span to reach full confidence

        rhProbMin  : 78.0,   // % RH where probability begins contributing
        rhProbSpan : 20.0,   // % RH span to reach full probability


        // ─────────────────────────────────────────────
        // Dew Point proximity thresholds (°C)
        // dewPointSpread = air temp − dew point
        // Smaller dewPointSpread = higher rain likelihood
        // ─────────────────────────────────────────────
        dewNear : 2.0,   // °C → air nearly saturated
        dewFar  : 5.0,   // °C → dry separation


        // ─────────────────────────────────────────────
        // Vapor Pressure Deficit thresholds (kPa)
        // Lower VPD = wetter air
        // ─────────────────────────────────────────────
        vpdWet : 0.25,   // kPa → surfaces stay wet
        vpdDry : 1.75,   // kPa → rapid drying


        // ─────────────────────────────────────────────
        // Barometric Pressure trend thresholds (inHg)
        // Used for confidence and probability modifiers
        // Positive ΔP indicates falling pressure
        // ─────────────────────────────────────────────
        pressureConfStart : 0.01,  // inHg → minimum falling pressure to contribute
        pressureConfMax   : 0.04,  // inHg → strong pressure drop (saturates contribution)


        // ─────────────────────────────────────────────
        // Solar Radiation thresholds (W/m²)
        // Used as a plausibility suppressor under strong sunlight
        // ─────────────────────────────────────────────
        solarOvercast : 400.0,  // W/m² → thick cloud cover
        solarClear    : 800.0,  // W/m² → strong direct sun


        // ─────────────────────────────────────────────
        // Wind normalization thresholds
        // ─────────────────────────────────────────────
        windConfMax : 3.0,  // m/s → caps wind plausibility benefit


        // ─────────────────────────────────────────────
        // Trend normalization maxima (per sample)
        // Assumes ~5 minute sampling interval
        // ─────────────────────────────────────────────
        rhTrendMax        : 3.0,   // % RH change per sample
        vpdTrendMax       : 0.15,  // kPa change per sample
        windTrendMax      : 2.0,   // m/s change per sample
        pressureTrendMax  : 0.03,  // inHg per sample
        
        
        // ─────────────────────────────────────────────
        // Stale sensor data handling
        // ─────────────────────────────────────────────
        staleThresholdMinutes : 45,


        // ─────────────────────────────────────────────
        // Rain sensor validation thresholds
        // Used to immediately confirm rain when rate is high
        // Bypasses environmental confidence checks
        // ─────────────────────────────────────────────
        rainRateConfirm : 0.1,  // in/hr → definite rain (e.g., moderate+ rainfall)


        // ─────────────────────────────────────────────
        // Alert thresholds (% score)
        // Hysteresis prevents alert flapping
        // ─────────────────────────────────────────────
        wetTrendOn  : 40.0,  // % probability → rain likely soon
        wetTrendOff : 30.0,  // % probability → clear prediction

        wetConfMin  : 75.0,  // % confidence → rain confirmed
        
        
        // ─────────────────────────────────────────────
        // Temperature breakpoints (°C)
        // Used for seasonal intelligence scaling
        // ─────────────────────────────────────────────
        tempCool : 10.0,   // ~50°F → cool drizzle regimes
        tempHot  : 35.0,   // ~95°F → hot summer storms


        // ─────────────────────────────────────────────
        // Seasonal probability multipliers (unitless)
        // Applied AFTER raw probability score
        // ─────────────────────────────────────────────
        probCoolBoost : 1.15,  // Cold rain more trustworthy
        probHotDampen : 0.92,  // Hot rain needs stronger signals


        // ─────────────────────────────────────────────
        // Seasonal confidence multipliers (unitless)
        // Applied AFTER raw confidence score
        // ─────────────────────────────────────────────
        confCoolBoost : 1.10,  // Cold drizzle persists
        confHotDampen : 0.95,  // Hot rain dries faster
    ]
    
    // Initialize State
    state.rainConfirmed   = state.rainConfirmed  ?: false
    state.wetTrendActive  = state.wetTrendActive ?: false
    state.falsePositive   = state.falsePositive  ?: false

    state.rhHist    = state.rhHist    ?: []
    state.windHist  = state.windHist  ?: []
    state.pressHist = state.pressHist ?: []
    state.vpdHist   = state.vpdHist   ?: [] 
    
    state.lastProcessedTimestamp = state.lastProcessedTimestamp ?: null
    state.isStale = state.isStale ?: false
    
    // Rain Alert
    subscribe(weatherStation, "dateutc", sensorHandler)
    
    // Staleness Check
    runEvery5Minutes("scheduleCheck_Staleness")
    
    // Device Checks
    initializeDeviceChecks()
    subscribe(deviceMonitor, "deviceCheck.active", deviceCheck_Staleness)
    
    // Initial Calculations
    subscribe(location, "systemStart", sensorHandler)
    calculate()
}

def sensorHandler(evt) {
    logDebug("sensorHandler: ${evt.device} changed to ${evt.value}")
    
    calculate()
}

def calculate() {
    def cfg = state.cfg
    
    // Null Data Detection
    def missingAttributes = checkForNullData()
    if (missingAttributes) {
        log.warn("Skipping calculations -- sensor data is null: ${missingAttributes.join(', ')}")
        return
    }
    
    def sensorTimestamp = weatherStation.currentValue("dateutc")?.toLong()
    
    // Stale Data Detection
    def staleNow = checkForStaleness(sensorTimestamp)
    if (staleNow) {
        log.warn("Skipping calculations -- sensor data is stale") 
        return
    }

    // Duplicate Data Detection
    def duplicateUpdate = checkForDuplicate(sensorTimestamp)
    if (duplicateUpdate) {
        log.debug("Skipping calculations -- duplicate sensor data")
        return
    }
    
    // Previous Sensor Data
    def prevTempF      = state.prevTempF
    def prevPressInHg  = state.prevPressInHg
    def prevRHraw      = state.prevRHraw
    def prevRainRate   = state.prevRainRate
    def prevWindMPH    = state.prevWindMPH
    def prevSolar      = state.prevSolar
    
    // Current Sensor Data
    def tempF        = weatherStation.currentValue("temperature")
    def pressInHg    = weatherStation.currentValue("pressure")
    def rh           = weatherStation.currentValue("humidity")
    def rainRateInHr = weatherStation.currentValue("precip_1hr")
    def windMPH      = weatherStation.currentValue("wind")
    def solarWm2     = weatherStation.currentValue("solarradiation")
    
    if (enableSensorLog) {
        log.debug(
            String.format(
                "SENS ▶ T=%.1f°F (%+.1f) P=%.1f inHg (%+.1f) RH=%.0f%% (%+.1f) Rain=%.3f in/hr (%+.3f) Wind=%.1f mph (%+.1f) Solar=%.0f W/m² (%+.0f)",
                tempF,
                prevTempF != null ? (tempF - prevTempF) : 0.0,
                pressInHg,
                prevPressInHg != null ? (pressInHg - prevPressInHg) : 0.0,
                rh,
                prevRHraw != null ? (rh - prevRHraw) : 0.0,
                rainRateInHr,
                prevRainRate != null ? (rainRateInHr - prevRainRate) : 0.0,
                windMPH,
                prevWindMPH != null ? (windMPH - prevWindMPH) : 0.0,
                solarWm2,
                prevSolar != null ? (solarWm2 - prevSolar) : 0.0
            )
        ) 
    }

    def tempC  = (tempF - 32.0) * 5.0 / 9.0
    def windMS = windMPH * 0.44704
    def vpd    = vaporPressureDeficit(tempC, rh)
    
    // Trend Histories
    updateHistory("rhHist"   , rh)
    updateHistory("windHist" , windMS)
    updateHistory("pressHist", pressInHg)
    updateHistory("vpdHist"  , vpd)

    // Core Calculations
    def tf = temperatureFactor(tempC)

    def baseProb = probabilityScore(rh)
    def adjProb  = clamp(baseProb * tf.prob, 0.0, 100.0)

    def baseConf = confidenceScore(tempC, rh, windMS, vpd, solarWm2)
    def adjConf  = clamp(baseConf * tf.conf, 0.0, 100.0)

    // Confidence
    def isRaining = (rainRateInHr > 0)
    def wasConfirmed = state.rainConfirmed ?: false
    def wasFalsePositive = state.falsePositive ?: false

    if (isRaining && !wasConfirmed) {
        if (adjConf >= cfg.wetConfMin || rainRateInHr >= cfg.rainRateConfirm) {
            if (state.falsePositive) {
                log.info("🌧️ Rain confirmation resolved previous false positive.")
            }

            sendAlert(
                String.format(
"""🌧️ Rain confirmed!
(confidence=%.1f%%, rate=%.2f in./hr)""",
                    adjConf, rainRateInHr
                )
            )
        
            state.rainConfirmed = true
            state.falsePositive = false
        
        } else if (!wasFalsePositive) {
            sendAlert(
                String.format(
"""⚠️ Rain sensor reports rain, but conditions don’t support it.
(confidence=%.1f%%, rate=%.2f in./hr)""",
                    adjConf, rainRateInHr
                )
            )
            
            state.falsePositive = true
        } 
    }
    
    if (wasConfirmed && isRaining && adjConf < cfg.wetConfMin && rainRateInHr < cfg.rainRateConfirm) {
        sendAlert(
            String.format(
"""⚠️ Rain confidence lost.
(confidence=%.1f%%, rate=%.2f in./hr)""",
                adjConf, rainRateInHr
            )
        )
        
        state.rainConfirmed = false
        state.falsePositive = true
    }

    if (wasConfirmed && !isRaining) { 
        sendAlert("⛅️ Rain has stopped!")
        
        state.rainConfirmed = false
    }
        
     if (!isRaining) {
        state.falsePositive = false
    }
    
    // Probability
    def wetTrendActive = (adjProb >= cfg.wetTrendOn)
    def wasTrendActive = state.wetTrendActive ?: false
    
    if (wetTrendActive && !wasTrendActive && !state.rainConfirmed) {
        sendAlert(
            String.format(
"""🌦️ Rain may be starting!
(probability=%.1f%%)""",
                adjProb
            )
        )
        
        state.wetTrendActive = true
    }

    if (wasTrendActive && adjProb < cfg.wetTrendOff) {
        state.wetTrendActive = false
    }
    
    // Status
    def status = deriveStatus(adjProb)
    
    if (enableStateLog) {
        log.info(
            String.format(
                "STATE ▶ Prob=%.1f%% (Base=%.1f ×%.2f) | Conf=%.1f%% (Base=%.1f ×%.2f) | Status=%s",
                adjProb,
                baseProb, tf.prob,
                adjConf,
                baseConf, tf.conf,
                status
            )
        )
    }
    
    // Save Sensor Data
    state.prevTempF     = tempF
    state.prevPressInHg = pressInHg
    state.prevRHraw     = rh
    state.prevRainRate  = rainRateInHr
    state.prevWindMPH   = windMPH
    state.prevSolar     = solarWm2
    
    state.lastProcessedTimestamp = sensorTimestamp
}

def clamp(val, minVal, maxVal) {
    return Math.max(minVal, Math.min(maxVal, val))
}

def updateHistory(key, value) {
    def history = (state[key] ?: []).toList()

    history.add(value)
    
    if (history.size() > 5) {
        history.remove(0)
    }

    state[key] = history
}

def rateOfChange(history) {
    if (history == null || history.size() < 2) {
        return 0.0
    }

    return (history[-1] - history[0]) / (history.size() - 1)
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

def temperatureFactor(tempC) {
    def cfg = state.cfg

    if (tempC <= cfg.tempCool) {
        return [
            prob: cfg.probCoolBoost,
            conf: cfg.confCoolBoost,
        ]
    }
    if (tempC >= cfg.tempHot) {
        return [
            prob: cfg.probHotDampen,
            conf: cfg.confHotDampen,
        ]
    }

    // Linear interpolation between cool ↔ hot
    def t = (tempC - cfg.tempCool) / (cfg.tempHot - cfg.tempCool)

    return [
        prob: cfg.probCoolBoost + t * (cfg.probHotDampen - cfg.probCoolBoost),
        conf: cfg.confCoolBoost + t * (cfg.confHotDampen - cfg.confCoolBoost),
    ]
}

def confidenceScore(tempC, rh, wind, vpd, solar) {
    def cfg = state.cfg
    
    def dew = dewPoint(tempC, rh)
    def dewPointSpread = tempC - dew
    
    def pressureDrop = -rateOfChange(state.pressHist)

    def sRH = clamp(
        (rh - cfg.rhConfMin) / cfg.rhConfSpan,
        0.0, 1.0
    )

    def sDew =
        (dewPointSpread <= cfg.dewNear) ? 1.0 :
        (dewPointSpread >= cfg.dewFar)  ? 0.0 :
        (cfg.dewFar - dewPointSpread) / (cfg.dewFar - cfg.dewNear)

    def sVPD =
        (vpd <= cfg.vpdWet) ? 1.0 :
        (vpd >= cfg.vpdDry) ? 0.0 :
        (cfg.vpdDry - vpd) / (cfg.vpdDry - cfg.vpdWet)

    // Wind acts as mild plausibility booster (does not penalize calm air)
    def sWind = 0.8 + 0.2 * clamp(wind / cfg.windConfMax, 0.0, 1.0)
    
    def sPress =
        (pressureDrop < cfg.pressureConfStart) ? 0.0 :
        (pressureDrop >= cfg.pressureConfMax)  ? 1.0 :
        (pressureDrop - cfg.pressureConfStart) /
        (cfg.pressureConfMax - cfg.pressureConfStart)
            
    def sSolar =
        (solar <= cfg.solarOvercast) ? 1.0 :
        (solar >= cfg.solarClear)    ? 0.0 :
        1.0 - (
            (solar - cfg.solarOvercast) /
            (cfg.solarClear - cfg.solarOvercast)
        )

    def score =
        100.0 * (
            0.35 * sRH +
            0.32 * sDew +
            0.21 * sVPD +
            0.04 * sWind +
            0.06 * sPress +
            0.02 * sSolar
        )
    
    if (enableScoreLog) {
        log.debug(
            String.format(
                "CONF ▶ RH=%.2f Dew=%.2f VPD=%.2f Wind=%.2f P=%.2f Sun=%.2f | ΔT=%.2f°C ΔP=%.2f inHg VPD=%.2f kPa Solar=%.0f W/m² → %.1f%%",
                sRH, sDew, sVPD, sWind, sPress, sSolar,
                dewPointSpread, pressureDrop, vpd, solar, score
            )
        ) 
    }

    return clamp(score, 0.0, 100.0)
}

def probabilityScore(rh) {
    def cfg = state.cfg
    
    def humidityRise =  rateOfChange(state.rhHist)
    def vpdDrop      = -rateOfChange(state.vpdHist)
    def windIncrease =  rateOfChange(state.windHist)
    def pressureDrop = -rateOfChange(state.pressHist)

    def sRHabs = clamp(
        (rh - cfg.rhProbMin) / cfg.rhProbSpan,
        0.0, 1.0
    )

    def sRHtrend    = clamp(humidityRise / cfg.rhTrendMax,       0.0, 1.0)
    def sVPDtrend   = clamp(vpdDrop      / cfg.vpdTrendMax,      0.0, 1.0)
    def sWindTrend  = clamp(windIncrease / cfg.windTrendMax,     0.0, 1.0)
    def sPressTrend = clamp(pressureDrop / cfg.pressureTrendMax, 0.0, 1.0)

    def score =
        100.0 * (
            0.29 * sRHabs +
            0.33 * sRHtrend +
            0.16 * sVPDtrend +
            0.16 * sWindTrend +
            0.06 * sPressTrend
        )

    if (enableScoreLog) {
        log.debug(
            String.format(
                "PROB ▶ RH=%.2f RH↑=%.2f VPD↓=%.2f Wind↑=%.2f P↓=%.2f | ΔRH=%.2f%% ΔVPD=%.3f kPa ΔWind=%.2f m/s ΔP=%.2f inHg → %.1f%%",
                sRHabs, sRHtrend, sVPDtrend, sWindTrend, sPressTrend,
                humidityRise, vpdDrop, windIncrease, pressureDrop, score
            )
        )
    }

    return clamp(score, 0.0, 100.0)
}

def deriveStatus(probability) {
    if (state.isStale) {
        return "Sensor Stale"
    }

    if (state.rainConfirmed) {
        return "Raining"
    }

    if (probability >= state.cfg.wetTrendOn) {
        return "Rain Likely"
    }

    if (probability >= state.cfg.wetTrendOff) {
        return "Watching"
    }

    return "Idle"
}

def checkForNullData() {
    def requiredAttributes = [
        "dateutc",
        "temperature",
        "pressure",
        "humidity",
        "precip_1hr",
        "wind",
        "solarradiation",
    ]
    def missingAttributes  = []
    
    for (attribute in requiredAttributes) {
        def value = weatherStation.currentValue(attribute)
        if (value == null) {
            missingAttributes.add(attribute)
        }
    }
    
    return missingAttributes
}

def checkForStaleness(ts) {
    if (ts == null) {
        if (enableStalenessLog) {
            log.debug("Staleness check ▶ timestamp missing")
        }
        return false
    }

    def cfg = state.cfg
    
    def now = new Date().time
    
    def ageMinutes = (now - ts) / 60000.0
    if (enableStalenessLog) {
        log.debug("Staleness check ▶ age=${ageMinutes} minutes")
    }
    
    def staleNow = ageMinutes >= cfg.staleThresholdMinutes

    if (staleNow && !state.isStale) {
        handleStaleDetected()
    }

    if (!staleNow && state.isStale) {
        handleStaleRecovered()
    }
    
    return staleNow 
}

def handleStaleDetected() {
    state.isStale = true

    clearTrendState()

    sendAlert("❌ $weatherStation data is stale!")
}

def handleStaleRecovered() {
    state.isStale = false
    
    sendAlert("✅ $weatherStation data is updating again!")
}

def clearTrendState() {
    state.rhHist    = []
    state.windHist  = []
    state.pressHist = []
    state.vpdHist   = []

    state.prevTempF     = null
    state.prevPressInHg = null
    state.prevRHraw     = null
    state.prevRainRate  = null
    state.prevWindMPH   = null
    state.prevSolar     = null

    state.rainConfirmed = false
    state.falsePositive = false
    state.wetTrendActive = false
}

def scheduleCheck_Staleness() {
    logDebug("scheduleCheck_Staleness")
    
    def lastTs = state.lastProcessedTimestamp
    if (lastTs != null) { 
        checkForStaleness(lastTs)
    } 
}

def deviceCheck_Staleness(evt) {
    logDebug("deviceCheck_Staleness")
    
    def lastTs = state.lastProcessedTimestamp
    if (lastTs == null) {
        deviceMonitor.addInactiveMessage(weatherStation.id, "${weatherStation}* - No Activity")
    } else if (state.isStale) {
        deviceMonitor.addInactiveMessage(weatherStation.id, "${weatherStation}* - ${timeSince(lastTs)}")
    }
}

def checkForDuplicate(ts) {
    def lastTs = state.lastProcessedTimestamp
    if (lastTs != null && ts == lastTs) {
        return true
    }
    
    return false
}

def sendAlert(msg) {
    log.info msg
    personToNotify.deviceNotification(msg)
}