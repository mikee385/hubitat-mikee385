/**
 *  name: Device Check Library
 *  author: Michael Pierce
 *  version: 3.0.0
 *  minimumHEVersion: 2.2.8
 *  licenseFile: https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/LICENSE
 *  releaseNotes: Add alerts for tamper, away, and sleep
 *  dateReleased: 2022-08-05
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

library (
    name: "device-check-library",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Common methods for checking for low battery and inactivity in all used devices.",
    category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/libraries/device-check-library.groovy"
)

def initializeDeviceChecks() {
    // Low Battery and Inactivity Alerts
    subscribe(deviceChecker, "deviceCheck.active", deviceCheck)
    
    for (device in getDevicesFromSettings()) {
        if (!device.getTypeName().contains("Virtual")) {
            // Tamper Alerts
            if (device.hasCapability("TamperAlert")) {
                subscribe(device, "tamper.detected", tamperAlert)
            }
            
            // Away Alerts
            if (device.hasCapability("ContactSensor")) {
                subscribe(device, "contact", awayAlert)
            }
            if (device.hasCapability("GarageDoorControl")) {
                subscribe(device, "door", awayAlert)
            }
            if (device.hasCapability("Lock")) {
                subscribe(device, "lock", awayAlert)
            }
            if (device.hasCapability("MotionSensor")) {
                subscribe(device, "motion.active", awayAlert)
            }
            if (device.hasCapability("PushableButton")) {
                subscribe(device, "pushed", awayAlert)
            }
            if (device.hasCapability("Switch")) {
                if (device.getDisplayName().contains("Camera") || device.getDisplayName().contains("Blink")) {
                    subscribe(device, "switch.off", awayAlert)
                } else {
                    subscribe(device, "switch.on", awayAlert)
                }
            }
            if (device.getTypeName() == "Vivint Panel") {
                subscribe(device, "alarm.disarmed", awayAlert)
            }
            
            // Sleep Alerts
            if (device.hasCapability("Switch")) {
                if (device.getDisplayName().contains("Camera") || device.getDisplayName().contains("Blink")) {
                    subscribe(device, "switch.off", sleepAlert)
                }
            } 
            if (device.getTypeName() == "Vivint Panel") {
                subscribe(device, "alarm.disarmed", sleepAlert)
            }
        }
    }
}

def getDevicesFromSettings() {
    def devices = []
    
    for (setting in settings) {
        if (setting.value instanceof List) {
            for (item in setting.value) {
                if (item.metaClass.respondsTo(item, 'getDeviceNetworkId')) {
                    devices.add(item)
                }
            }
        } else {
            if (setting.value.metaClass.respondsTo(setting.value, 'getDeviceNetworkId')) {
                devices.add(setting.value)
            }
        }
    }
    
    return devices
}

def deviceCheck(evt) {
    logDebug("deviceCheck")
    
    //Get devices from settings
    def devices = getDevicesFromSettings()
    
    //Get Battery Thresholds
    def excludedBatteryDeviceTypes = [
        "Aladdin Connect Garage Door",
        "Konke ZigBee Temperature Humidity Sensor"
    ]
    def batteryThresholds = []
    for (device in devices) {
        if (!excludedBatteryDeviceTypes.contains(device.getTypeName())) {
            if (device.hasCapability("Battery")) {
                batteryThresholds.add([device: device, lowBattery: 10])
            }
        }
    }
    
    //Check Battery Levels
    def batteryDeviceIDs = []
    for (item in batteryThresholds) {
        if (!batteryDeviceIDs.contains(item.device.id)) {
            if (item.device.currentValue("battery") <= item.lowBattery) {
                batteryDeviceIDs.add(item.device.id)
                    
                def message = "${item.device} - ${item.device.currentValue('battery')}%"
                log.warn(message)
                deviceChecker.addBatteryMessage(message)
            }
        }
    }
    
    //***DEBUG ONLY***
    def oldBatteryThresholds = []
    if (getBatteryThresholds) {
        oldBatteryThresholds = getBatteryThresholds()
    }
    def newBatteryIDs = []
    for (item in batteryThresholds) {
        newBatteryIDs.add(item.device.id)
    }
    for (item in oldBatteryThresholds) {
        if (!newBatteryIDs.contains(item.device.id)) {
            def message = "Not Montiored in New: ${item.device}"
            log.warn(message)
            personToNotify.deviceNotification(message)
        }
    }
    
    //Get Inactive and Unchanged Thresholds
    def excludedInactiveDeviceTypes = [
        "Appliance Status",
        "Application Refresh Button",
        "Echo Glow Device",
        "Echo Glow Scene",
        "Occupancy Status",
        "Person Status",
        "Philips Dimmer Button Controller"
    ]
    def inactiveThresholds = []
    def unchangedThresholds = []
    for (device in devices) {
        if (!excludedInactiveDeviceTypes.contains(device.getTypeName())) {
            if (device.hasCapability("TemperatureMeasurement")) {
                inactiveThresholds.add([device: device, inactiveHours: 6])
                unchangedThresholds.add([device: device, attribute: "temperature", inactiveHours: 6])
                
            } else if (device.hasCapability("RelativeHumidityMeasurement")) {
                inactiveThresholds.add([device: device, inactiveHours: 6])
                unchangedThresholds.add([device: device, attribute: "humidity", inactiveHours: 6])
                
            } else if (device.hasCapability("Battery")) {
                inactiveThresholds.add([device: device, inactiveHours: 24])
                
            } else if (device.hasCapability("PresenceSensor") && device.getDisplayName() != "Guest") {
                unchangedThresholds.add([device: device, attribute: "presence", inactiveHours: 72])
                
            } else if (!device.getTypeName().contains("Virtual")) {
                inactiveThresholds.add([device: device, inactiveHours: 24])
            }
        } 
    }
    
    //Check Inactive Devices
    def inactiveDeviceIDs = []
    for (item in inactiveThresholds) {
        if (!inactiveDeviceIDs.contains(item.device.id)) {
            if (item.device.getLastActivity()) {
                def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
                if (item.device.getLastActivity().getTime() <= cutoffTime) {
                    inactiveDeviceIDs.add(item.device.id)
                        
                    def message = "${item.device} - ${timeSince(item.device.getLastActivity().getTime())}"
                    log.warn(message)
                    deviceChecker.addInactiveMessage(message)
                }
            } else {
                inactiveDeviceIDs.add(item.device.id)
                    
                def message = "${item.device} - No Activity"
                log.warn(message)
                deviceChecker.addInactiveMessage(message)
            }
        }
    }
    
    //***DEBUG ONLY***
    def oldInactiveThresholds = []
    if (getInactiveThresholds) {
        oldInactiveThresholds = getInactiveThresholds()
    }
    def newInactiveIDs = []
    for (item in inactiveThresholds) {
        newInactiveIDs.add(item.device.id)
    }
    for (item in oldInactiveThresholds) {
        if (!newInactiveIDs.contains(item.device.id)) {
            def message = "Not Inactive Montiored in New: ${item.device}"
            log.warn(message)
            personToNotify.deviceNotification(message)
        }
    }
    
    //Check Unchanged Devices
    for (item in unchangedThresholds) {
        if (!inactiveDeviceIDs.contains(item.device.id)) {
            def lastEvent = item.device.events(max: 200).find{it.name == item.attribute}
            if (lastEvent) {
                def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
                if (lastEvent.getDate().getTime() <= cutoffTime) {
                    inactiveDeviceIDs.add(item.device.id)
                        
                    def message = "${item.device}* - ${timeSince(lastEvent.getDate().getTime())}"
                    log.warn(message)
                    deviceChecker.addInactiveMessage(message)
                }
            } else {
                inactiveDeviceIDs.add(item.device.id)
                    
                def message = "${item.device}* - No Activity"
                log.warn(message)
                deviceChecker.addInactiveMessage(message)
            }
        }
    }
    
    //***DEBUG ONLY***
    def oldUnchangedThresholds = []
    if (getUnchangedThresholds) {
        oldUnchangedThresholds = getUnchangedThresholds()
    }
    def newUnchangedIDs = []
    for (item in unchangedThresholds) {
        newUnchangedIDs.add(item.device.id)
    }
    for (item in oldUnchangedThresholds) {
        if (!newUnchangedIDs.contains(item.device.id)) {
            def message = "Not Unchanged Montiored in New: ${item.device}"
            log.warn(message)
            personToNotify.deviceNotification(message)
        }
    }
}

def timeSince(date) {
    def seconds = (now() - date) / 1000
    if (seconds < 45) {
        return (int)Math.round(seconds) + " seconds"
    } else if (seconds < 90) {
        return "1 minute"
    }
    
    def minutes = seconds / 60
    if (minutes < 45) {
        return (int)Math.round(minutes) + " minutes"
    } else if (minutes < 90) {
        return "1 hour"
    }
    
    def hours = minutes / 60
    if (hours < 22) {
        return (int)Math.round(hours) + " hours"
    } else if (hours < 36) {
        return "1 day"
    }
    
    def days = hours / 24
    if (days < 26) {
        return (int)Math.round(days) + " days"
    } else if (days < 45) {
        return "1 month"
    }
    
    def months = days / (365.25 / 12)
    if (days < 320) {
        return (int)Math.round(months) + " months"
    } else if (days < 548) {
        return "1 year"
    }
    
    def years = days / 365.25
    return (int)Math.round(years) + " years"
}

def tamperAlert(evt) {
    logDebug("tamperAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("Tamper alert for ${evt.device}!")
}

def awayAlert(evt) {
    logDebug("awayAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Away") {
        personToNotify.deviceNotification("${evt.device} is ${evt.value} while Away!")
    }
}

def sleepAlert(evt) {
    logDebug("sleepAlert: ${evt.device} changed to ${evt.value}")
    
    if (location.mode == "Sleep") {
        personToNotify.deviceNotification("${evt.device} is ${evt.value} during Sleep!")
    }
}