/**
 *  name: Inactive Alert Library
 *  author: Michael Pierce
 *  version: 1.0.0
 *  minimumHEVersion: 2.2.8
 *  licenseFile: https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/LICENSE
 *  releaseNotes: Initial commit
 *  dateReleased: 2022-02-04
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
    name: "inactive-alert-library",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Common method for sending an alert when a device has not been active for a period of time.",
    category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/libraries/inactive-alert-library.groovy"
)

def handler_InactiveAlert() {
    logDebug("handler_InactiveAlert")
    
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        def dateTimeFormat = "MMM d, yyyy, h:mm a"
        def deviceIDs = []
        def message = ""
        
        for (item in getInactiveThresholds()) {
            if (!deviceIDs.contains(item.device.id)) {
                if (item.device.getLastActivity()) {
                    def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
                    if (item.device.getLastActivity().getTime() <= cutoffTime) {
                        deviceIDs.add(item.device.id)
                        message += """
${item.device} - ${item.device.getLastActivity().format(dateTimeFormat, location.timeZone)}"""
                    }
                } else {
                    deviceIDs.add(item.device.id)
                    message += """
${item.device} - No Activity"""
                }
            }
        }
        
        for (item in getUnchangedThresholds()) {
            if (!deviceIDs.contains(item.device.id)) {
                def lastEvent = item.device.events(max: 200).find{it.name == item.attribute}
                if (lastEvent) {
                    def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
                    if (lastEvent.getDate().getTime() <= cutoffTime) {
                        deviceIDs.add(item.device.id)
                        message += """
${item.device} - ${item.attribute} - ${lastEvent.getDate().format(dateTimeFormat, location.timeZone)}"""
                    }
                } else {
                    deviceIDs.add(item.device.id)
                    message += """
${item.device} - ${item.attribute} - No Activity"""
                }
            }
        }
        
        if (message) {
            personToNotify.deviceNotification("Inactive Devices: $message")
        }
    }
}