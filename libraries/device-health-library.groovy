/**
 *  name: Device Health Library
 *  author: Michael Pierce
 *  version: 1.0.0
 *  minimumHEVersion: 2.2.8
 *  licenseFile: https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/LICENSE
 *  releaseNotes: Initial release
 *  dateReleased: 2022-08-03
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
    name: "device-health-library",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Common methods for checking for low battery and inactivity in all used devices.",
    category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/libraries/device-health-library.groovy"
)

def initializeDeviceHealthCheck() {
    subscribe(deviceHealthChecker, "deviceCheck.active", deviceHealthCheck)
}

def deviceHealthCheck(evt) {
    logDebug("deviceHealthCheck")
    
    //Get devices from settings
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
        
    def excludedDeviceTypes = [
        "Appliance Status",
        "Application Refresh Button",
        "Occupancy Status",
        "Person Status"
    ]
    
    //Get Battery Thresholds
    def batteryThresholds = []
    for (device in devices) {
        if (!excludedDeviceTypes.contains(device.getTypeName())) {
            if (device.hasCapability("Battery")) {
                newBatteryThresholds.add([device: device, lowBattery: 10])
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
                deviceHealthChecker.addBatteryMessage(message)
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
    def inactiveThresholds = []
    def unchangedThresholds = []
    for (device in devices) {
        if (!excludedDeviceTypes.contains(device.getTypeName())) {
            if (device.hasCapability("TemperatureMeasurement")) {
                inactiveThresholds.add([device: device, inactiveHours: 1])
                unchangedThresholds.add([device: device, attribute: "temperature", inactiveHours: 1])
                
            } else if (device.hasCapability("RelativeHumidityMeasurement")) {
                inactiveThresholds.add([device: device, inactiveHours: 1])
                unchangedThresholds.add([device: device, attribute: "humidity", inactiveHours: 1])
                
            } else if (device.hasCapability("Battery")) {
                inactiveThresholds.add([device: device, inactiveHours: 24])
                
            } else if (device.hasCapability("PresenceSensor")) {
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
                    deviceHealthChecker.addInactiveMessage(message)
                }
            } else {
                inactiveDeviceIDs.add(item.device.id)
                    
                def message = "${item.device} - No Activity"
                log.warn(message)
                deviceHealthChecker.addInactiveMessage(message)
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
                    deviceHealthChecker.addInactiveMessage(message)
                }
            } else {
                inactiveDeviceIDs.add(item.device.id)
                    
                def message = "${item.device}* - No Activity"
                log.warn(message)
                deviceHealthChecker.addInactiveMessage(message)
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