/**
 *  name: Device Monitor Library
 *  author: Michael Pierce
 *  version: 4.6.0
 *  minimumHEVersion: 2.2.8
 *  licenseFile: https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/LICENSE
 *  releaseNotes: Exclude NUT Child UPS from unchanged device checks
 *  dateReleased: 2023-02-08
 *
 *  Copyright 2023 Michael Pierce
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
    name: "device-monitor-library",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Common methods for checking for low battery and inactivity in all used devices.",
    category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/libraries/device-monitor-library.groovy"
)

import groovy.transform.Field

@Field static final List virtualDeviceTypes = [
    "Appliance Status",
    "Application Refresh Button",
    "Child Button",
    "Child Switch",
    "Device Monitor",
    "Echo Glow Device",
    "Echo Glow Routines",
    "Echo Glow Scene",
    "Occupancy Status",
    "Person Status",
    "Zone Device",
    "Zone Parent"
]

@Field static final List excludedBatteryDeviceTypes = [
    "Aladdin Connect Garage Door",
    "Konke ZigBee Temperature Humidity Sensor"
]
    
@Field static final List excludedInactiveDeviceTypes = [
    "LG ThinQ Dryer",
    "LG ThinQ Washer",
    "Philips Dimmer Button Controller", 
    "Roku TV"
]

@Field static final List excludedUnchangedDeviceTypes = [
    "NUT Child UPS"
]

def initializeDeviceChecks() {
    // Low Battery and Inactivity Alerts
    subscribe(deviceMonitor, "deviceCheck.active", deviceCheck)
    
    for (device in getDevicesFromSettings()) {
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

def isVirtualDevice(device) {
    if (device.getTypeName().contains("Virtual")) {
        return true
    } else if (device.getTypeName().contains("Generic Component")) {
        return true
    } else if (virtualDeviceTypes.contains(device.getTypeName())) {
        return true
    } else {
        return false
    }
}

def getDevicesFromSettings() {
    def devices = [:]
    
    for (setting in settings) {
        if (setting.value instanceof List) {
            for (item in setting.value) {
                if (item.metaClass.respondsTo(item, 'getDeviceNetworkId')) {
                    if (!isVirtualDevice(item)) {
                        devices[item.id] = item
                    } 
                }
            }
        } else {
            if (setting.value.metaClass.respondsTo(setting.value, 'getDeviceNetworkId')) {
                if (!isVirtualDevice(setting.value)) {
                    devices[setting.value.id] = setting.value
                } 
            }
        }
    }
    
    return devices.values()
}

def deviceCheck(evt) {
    logDebug("deviceCheck")
    
    //Get devices from settings
    def devices = getDevicesFromSettings()
    
    //Get Battery Thresholds
    def batteryThresholds = []
    for (device in devices) {
        if (!excludedBatteryDeviceTypes.contains(device.getTypeName())) {
            if (device.hasCapability("Battery")) {
                batteryThresholds.add([device: device, lowBattery: 10])
            }
        }
    }
    
    //Check Battery Levels
    for (item in batteryThresholds) {
        if (item.device.currentValue("battery") <= item.lowBattery) {
            deviceMonitor.addBatteryMessage(item.device.id, "${item.device} - ${item.device.currentValue('battery')}%")
        }
    }
    
    //Get Inactive and Unchanged Thresholds
    def inactiveThresholds = []
    def unchangedThresholds = []
    for (device in devices) {
        if (!excludedInactiveDeviceTypes.contains(device.getTypeName())) {
            if (!device.hasCapability("PresenceSensor")) {
                inactiveThresholds.add([device: device, inactiveHours: 24])
            }
        }
        if (!excludedUnchangedDeviceTypes.contains(device.getTypeName())) {
            if (device.hasCapability("TemperatureMeasurement")) {
                unchangedThresholds.add([device: device, attribute: "temperature", inactiveHours: 24])
                
            } else if (device.hasCapability("RelativeHumidityMeasurement")) {
                unchangedThresholds.add([device: device, attribute: "humidity", inactiveHours: 24])
                
            }
        } 
    }
    
    //Check Inactive Devices
    def inactiveDeviceIDs = []
    for (item in inactiveThresholds) {
        if (item.device.getLastActivity()) {
            def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
            if (item.device.getLastActivity().getTime() <= cutoffTime) {
                inactiveDeviceIDs.add(item.device.id)
                deviceMonitor.addInactiveMessage(item.device.id, "${item.device} - ${timeSince(item.device.getLastActivity().getTime())}")
            }
        } else {
            inactiveDeviceIDs.add(item.device.id)
            deviceMonitor.addInactiveMessage(item.device.id, "${item.device} - No Activity")
        }
    }
    
    //Check Unchanged Devices
    for (item in unchangedThresholds) {
        if (!inactiveDeviceIDs.contains(item.device.id)) {
            def lastEvent = item.device.events(max: 200).find{it.name == item.attribute}
            if (lastEvent) {
                def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
                if (lastEvent.getDate().getTime() <= cutoffTime) {
                    deviceMonitor.addInactiveMessage(item.device.id, "${item.device}* - ${timeSince(lastEvent.getDate().getTime())}")
                }
            } else {
                deviceMonitor.addInactiveMessage(item.device.id, "${item.device}* - No Activity")
            }
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