/**
 *  Daily Notification
 *
 *  Copyright 2020 Michael Pierce
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
 
String getVersionNum() { return "1.0.0-beta1" }
String getVersionLabel() { return "Daily Notification, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Daily Notification",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Sends a notification every day at a specific time.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/daily-notification.groovy")

preferences {
    page(name: "settings", title: "Daily Notification", install: true, uninstall: true) {
        section {
            input "notifier", "capability.notification", title: "Notification Device", multiple: false, required: true
            input "message", "text", title: "Message Text", multiple: false, required: true
        }
        section {
            input "timeToNotify", "time", title: "Time", required: true
        }
        section {
            input "daysToNotify", "enum", title: "Only on certain days of the week", multiple: true, required: false, options: ["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"]
            input "modesToNotify" "mode", title: "Only when mode is", multiple: true, required: false
        }
        section {
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
    def daysFilter = '*'
    if (daysToNotify) {
        daysFilter = daysToNotify.collect { it.getValue().toString() }.join(",")
        notifier.deviceNotification(daysFilter)
    }
    
    def timeToNotifyToday = timeToday(timeToNotify)
    def currentTime = new Date()
    schedule("$currentTime.seconds $timeToNotifyToday.minutes $timeToNotifyToday.hours ? * $daysFilter *", notify)
}

def notify() {
    if (location.mode in modesToNotify) {
        notifier.deviceNotification(message)
    } else {
        notifier.deviceNotification("Not correct mode")
    }
}