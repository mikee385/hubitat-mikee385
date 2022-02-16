/**
 *  name: Inactive Alert Library
 *  author: Michael Pierce
 *  version: 1.3.0
 *  minimumHEVersion: 2.2.8
 *  licenseFile: https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/LICENSE
 *  releaseNotes: Change timestamp of last activtiy to duration since last activity
 *  dateReleased: 2022-02-16
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

def scheduleInactiveCheck() {
    def checkTime = timeToday("19:55")
    def currentTime = new Date()
    schedule("$currentTime.seconds $checkTime.minutes $checkTime.hours * * ? *", inactiveCheck)
}

def inactiveCheck() {
    logDebug("inactiveCheck")
    
    if (personToNotify.currentValue("presence") == "present" && personToNotify.currentValue("sleeping") == "not sleeping") {
        def dateTimeFormat = "MMM d, yyyy, h:mm a"
        def deviceIDs = []
        
        if (getInactiveThresholds) {
            for (item in getInactiveThresholds()) {
                if (!deviceIDs.contains(item.device.id)) {
                    if (item.device.getLastActivity()) {
                        def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
                        if (item.device.getLastActivity().getTime() <= cutoffTime) {
                            deviceIDs.add(item.device.id)
                            personToNotify.inactiveNotification("${item.device} - ${timeSince(item.device.getLastActivity())}")
                        }
                    } else {
                        deviceIDs.add(item.device.id)
                        personToNotify.inactiveNotification("${item.device} - No Activity")
                    }
                }
            }
        }
        
        if (getUnchangedThresholds) {
            for (item in getUnchangedThresholds()) {
                if (!deviceIDs.contains(item.device.id)) {
                    def lastEvent = item.device.events(max: 200).find{it.name == item.attribute}
                    if (lastEvent) {
                        def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
                        if (lastEvent.getDate().getTime() <= cutoffTime) {
                            deviceIDs.add(item.device.id)
                            personToNotify.inactiveNotification("${item.device} - ${item.attribute} - ${timeSince(lastEvent.getDate().getTime())}")
                        }
                    } else {
                        deviceIDs.add(item.device.id)
                        personToNotify.inactiveNotification("${item.device} - ${item.attribute} - No Activity")
                    }
                }
            }
        }
    }
}

def timeSince(date) {
    def seconds = Math.floor((now() - date) / 1000)
    
    def interval = seconds / 31536000
    if (interval > 1) {
        return (int)Math.floor(interval) + " years"
    }
    interval = seconds / 2592000
    if (interval > 1) {
        return (int)Math.floor(interval) + " months"
    }
    interval = seconds / 86400
    if (interval > 1) {
        return (int)Math.floor(interval) + " days"
    }
    interval = seconds / 3600
    if (interval > 1) {
        return (int)Math.floor(interval) + " hours"
    }
    interval = seconds / 60.
    if (interval > 1) {
        return (int)Math.floor(interval) + " minutes"
    }
    return (int)Math.floor(seconds) + " seconds"
}