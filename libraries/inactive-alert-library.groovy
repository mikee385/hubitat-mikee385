/**
 *  name: Inactive Alert Library
 *  author: Michael Pierce
 *  version: 1.4.0
 *  minimumHEVersion: 2.2.8
 *  licenseFile: https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/LICENSE
 *  releaseNotes: Tweak inactive duration and attribute display
 *  dateReleased: 2022-02-19
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
        def deviceIDs = []
        
        def oldInactiveThresholds = []
        if (getInactiveThresholds) {
            oldInactiveThresholds = getInactiveThresholds()
        }
        
        for (item in oldInactiveThresholds) {
            if (!deviceIDs.contains(item.device.id)) {
                if (item.device.getLastActivity()) {
                    def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
                    if (item.device.getLastActivity().getTime() <= cutoffTime) {
                        deviceIDs.add(item.device.id)
                        personToNotify.inactiveNotification("${item.device} - ${timeSince(item.device.getLastActivity().getTime())}")
                    }
                } else {
                    deviceIDs.add(item.device.id)
                    personToNotify.inactiveNotification("${item.device} - No Activity")
                }
            }
        }
        
        def oldUnchangedThresholds = []
        if (getUnchangedThresholds) {
            oldUnchangedThresholds = getUnchangedThresholds()
        }
        
        for (item in oldUnchangedThresholds) {
            if (!deviceIDs.contains(item.device.id)) {
                def lastEvent = item.device.events(max: 200).find{it.name == item.attribute}
                if (lastEvent) {
                    def cutoffTime = now() - (item.inactiveHours * 60*60*1000)
                    if (lastEvent.getDate().getTime() <= cutoffTime) {
                        deviceIDs.add(item.device.id)
                        personToNotify.inactiveNotification("${item.device}* - ${timeSince(lastEvent.getDate().getTime())}")
                    }
                } else {
                    deviceIDs.add(item.device.id)
                    personToNotify.inactiveNotification("${item.device}* - No Activity")
                }
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