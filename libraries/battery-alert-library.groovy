/**
 *  name: Battery Alert Library
 *  author: Michael Pierce
 *  version: 1.2.0
 *  minimumHEVersion: 2.2.8
 *  licenseFile: https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/LICENSE
 *  releaseNotes: Rename methods for clarity
 *  dateReleased: 2022-02-11
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
    name: "battery-alert-library",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Common method for sending an alert when a device has a low battery.",
    category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/libraries/battery-alert-library.groovy"
)

def scheduleBatteryCheck() {
    def checkTime = timeToday("19:55")
    def currentTime = new Date()
    schedule("$currentTime.seconds $checkTime.minutes $checkTime.hours * * ? *", batteryCheck)
}

def batteryCheck() {
    logDebug("batteryCheck")
    
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        def deviceIDs = []
        
        def oldBatteryThresholds = []
        if (getBatteryThresholds) {
            oldBatteryThresholds = getBatteryThresholds()
        }
        
        for (item in oldBatteryThresholds) {
            if (!deviceIDs.contains(item.device.id)) {
                if (item.device.currentValue("battery") <= item.lowBattery) {
                    deviceIDs.add(item.device.id)
                    personToNotify.batteryNotification("${item.device} - ${item.device.currentValue('battery')}%")
                }
            }
        }
    }
}