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
String getAppVersion() { return "0.23.0" }
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
    
    state.cfg = [

        // ─────────────────────────────────────────────
        // Relative Humidity thresholds (% RH)
        // Used for absolute humidity scoring
        // ─────────────────────────────────────────────
        rhConfMin  : 60.0,   // % RH where confidence begins contributing
        rhConfSpan : 30.0,   // % RH span to reach full confidence

        rhProbMin  : 70.0,   // % RH where probability begins contributing
        rhProbSpan : 25.0,   // % RH span to reach full probability


        // ─────────────────────────────────────────────
        // Dew Point proximity thresholds (°C)
        // deltaT = air temp − dew point
        // Smaller deltaT = higher rain likelihood
        // ─────────────────────────────────────────────
        dewNear : 2.0,   // °C → air nearly saturated
        dewFar  : 5.0,   // °C → dry separation


        // ─────────────────────────────────────────────
        // Vapor Pressure Deficit thresholds (kPa)
        // Lower VPD = wetter air
        // ─────────────────────────────────────────────
        vpdWet : 0.30,   // kPa → surfaces stay wet
        vpdDry : 1.00,   // kPa → rapid drying


        // ─────────────────────────────────────────────
        // Trend normalization maxima (per sample)
        // Assumes ~5 minute sampling interval
        // ─────────────────────────────────────────────
        rhTrendMax   : 3.0,    // % RH change per sample
        vpdTrendMax  : 0.15,   // kPa change per sample
        windTrendMax : 2.0,    // m/s change per sample


        // ─────────────────────────────────────────────
        // Alert thresholds (% score)
        // Hysteresis prevents alert flapping
        // ─────────────────────────────────────────────
        probAlertOn  : 75.0,  // % probability → rain likely soon
        probAlertOff : 60.0,  // % probability → clear prediction

        confAlertOn  : 75.0,  // % confidence → rain confirmed
        
        
        // ─────────────────────────────────────────────
        // Confidence decay (per sample)
        // Limits how fast rain confidence may fall
        // Assumes ~5 minute evaluation cadence
        // ─────────────────────────────────────────────
        confDecayPerSample : 10.0,  // % confidence drop allowed per run


        // ─────────────────────────────────────────────
        // Drizzle detection controls
        // ─────────────────────────────────────────────
        drizzleConfCap  : 55.0,   // Max confidence allowed without rain sensor
        
        // ─────────────────────────────────────────────
        // Drizzle detection thresholds
        // Atmospheric rain without gauge confirmation
        // ─────────────────────────────────────────────
        drizzleConfOn  : 45.0,  // % → drizzle likely
        drizzleConfOff : 35.0,  // % → drizzle ended
        
        
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
        probCoolBoost  : 1.15,  // Cold rain more trustworthy
        probHotDampen  : 0.85,  // Hot rain needs stronger signals


        // ─────────────────────────────────────────────
        // Seasonal confidence multipliers (unitless)
        // Applied AFTER raw confidence score
        // ─────────────────────────────────────────────
        confCoolBoost  : 1.10,  // Cold drizzle persists
        confHotDampen  : 0.90,  // Hot rain dries faster
    ]
    
    // Initialize State
    state.rainConfirmed   = state.rainConfirmed ?: false
    state.rainPredicted   = state.rainPredicted ?: false
    state.drizzleDetected = state.drizzleDetected ?: false
    
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
    
    // Previous Sensor Data
    def prevTempF     = state.prevTempF
    def prevRHraw     = state.prevRHraw
    def prevRainRate  = state.prevRainRate
    def prevWindMPH   = state.prevWindMPH
    
    // Current Sensor Data
    def tempF = weatherStation.currentValue("temperature")
    def rh = weatherStation.currentValue("humidity")
    def rainRateInHr = weatherStation.currentValue("precip_1hr")
    def windMPH = weatherStation.currentValue("wind")
    
    logDebug(
        String.format(
            "SENS ▶ T=%.1f°F (%+.1f) RH=%.0f%% (%+.1f) Rain=%.3f in/hr (%+.3f) Wind=%.1f mph (%+.1f)",
            tempF,
            prevTempF != null ? (tempF - prevTempF) : 0.0,
            rh,
            prevRHraw != null ? (rh - prevRHraw) : 0.0,
            rainRateInHr,
            prevRainRate != null ? (rainRateInHr - prevRainRate) : 0.0,
            windMPH,
            prevWindMPH != null ? (windMPH - prevWindMPH) : 0.0
        )
    )

    def tempC = (tempF - 32.0) * 5.0 / 9.0
    def windMS = windMPH * 0.44704
    
    // Core Calculations
    def vpd = vaporPressureDeficit(tempC, rh)
    def tf = temperatureFactor(tempC)

    def baseProb = probabilityScore(tempC, rh, windMS, vpd)
    def adjProb  = clamp(baseProb * tf.prob, 0.0, 100.0)

    def baseConf = confidenceScore(tempC, rh, windMS, vpd, rainRateInHr)
    def adjConf  = clamp(baseConf * tf.conf, 0.0, 100.0)
    
    if (rainRateInHr <= 0.0 && adjConf > cfg.drizzleConfCap) {
        adjConf = cfg.drizzleConfCap
    }

    // Confidence decay smoothing
    def prevEffConf = state.prevEffConf ?: adjConf
    def effConf
    if (rainRateInHr > 0.0) {
        effConf = adjConf
    } else {
        def decayed = (state.prevEffConf ?: adjConf) - cfg.confDecayPerSample
        effConf = Math.max(
            Math.min(adjConf, state.prevEffConf ?: adjConf),
            decayed
        )
    }
    state.prevEffConf = effConf

    logDebug(
        String.format(
            "SCORE ▶ Prob=%.1f%% (Base=%.1f ×%.2f) | Conf=%.1f%% (Base=%.1f ×%.2f → %.1f) → Eff=%.1f",
            adjProb,
            baseProb, tf.prob,
            adjConf,
            baseConf, tf.conf, adjConf,
            effConf
        )
    )

    // Confidence
    def rainConfirmed = (effConf >= cfg.confAlertOn)
    def wasConfirmed = state.rainConfirmed ?: false

    if (rainConfirmed && !wasConfirmed) {
        def msg = "🌧️ Rain confirmed: ${effConf.round(1)}%"
        logInfo(msg)
        sendAlert(msg)
        
        state.rainConfirmed   = true
        state.drizzleDetected = false
    }

    if (wasConfirmed && !rainConfirmed) { 
        def msg = "☀️ Rain has stopped: ${effConf.round(1)}%"
        logInfo(msg)
        sendAlert(msg)
        
        state.rainConfirmed = false
        state.rainPredicted = false
    }
    
    // Drizzle
    def drizzleDetected =
        (rainRateInHr <= 0.0) &&
        (effConf <= cfg.drizzleConfCap) &&
        (effConf >= cfg.drizzleConfOn)

    if (drizzleDetected && !state.drizzleDetected) {
        def msg = "🌦️ Drizzle detected -- wet conditions likely: ${effConf.round(1)}%"
        logInfo(msg)
        sendAlert(msg)
        
        state.drizzleDetected = true
    }
    
    if (state.drizzleDetected && effConf < cfg.drizzleConfOff) {
        def msg = "🌤️ Drizzle ended -- conditions drying: ${effConf.round(1)}%"
        logInfo(msg)
        sendAlert(msg)
        
        state.drizzleDetected = false
    }
    
    // Probability
    def rainLikelySoon = (adjProb >= cfg.probAlertOn)
    def wasPredicted = state.rainPredicted ?: false
    
    if (!state.drizzleDetected && rainLikelySoon && !wasPredicted) {
        if (!rainConfirmed && !wasConfirmed) {
            def msg = "🌧️ Rain likely soon: ${adjProb.round(1)}%"
            logInfo(msg)
            sendAlert(msg)
        } 
        state.rainPredicted = true
    }

    if (wasPredicted && adjProb < cfg.probAlertOff) {
        state.rainPredicted = false
    }
    
    // Save Sensor Data
    state.prevTempF    = tempF
    state.prevRHraw    = rh
    state.prevRainRate = rainRateInHr
    state.prevWindMPH  = windMPH
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

def confidenceScore(tempC, rh, wind, vpd, rainRateInHr) {
    def cfg = state.cfg
    
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

    def sWind = clamp(wind / 2.0, 0.8, 1.0)
    
    def score =
        100.0 * (
            0.45 * sRH +
            0.35 * sDew +
            0.15 * sVPD +
            0.05 * sWind
        )

    logDebug(
        String.format(
            "CONF ▶ RH=%.2f Dew=%.2f VPD=%.2f Wind=%.2f | ΔT=%.2f°C VPD=%.2f kPa → %.1f%%",
            sRH, sDew, sVPD, sWind,
            deltaT, vpd, score
        )
    )

    return clamp(score, 0.0, 100.0)
}

def probabilityScore(tempC, rh, wind, vpd) {
    def cfg = state.cfg
    
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

    def sRHtrend   = clamp(dRH   / cfg.rhTrendMax,   0.0, 1.0)
    def sVPDtrend  = clamp(dVPD  / cfg.vpdTrendMax,  0.0, 1.0)
    def sWindTrend = clamp(dWind / cfg.windTrendMax, 0.0, 1.0)
    
    state.prevRH   = rh
    state.prevVPD  = vpd
    state.prevWind = wind

    def score =
        100.0 * (
            0.40 * sRHabs +
            0.30 * sRHtrend +
            0.20 * sVPDtrend +
            0.10 * sWindTrend
        )

    logDebug(
        String.format(
            "PROB ▶ RH=%.2f RH↑=%.2f VPD↓=%.2f Wind↑=%.2f | ΔRH=%.2f%% ΔVPD=%.3f kPa ΔWind=%.2f m/s → %.1f%%",
            sRHabs, sRHtrend, sVPDtrend, sWindTrend,
            dRH, dVPD, dWind, score
        )
    )

    return clamp(score, 0.0, 100.0)
}

def sendAlert(msg) {
    personToNotify.deviceNotification(msg)
}