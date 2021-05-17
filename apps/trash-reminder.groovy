/**
 *  Trash Reminder
 *
 *  Copyright 2021 Michael Pierce
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
 
String getVersionNum() { return "2.0.0" }
String getVersionLabel() { return "Trash Reminder, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Trash Reminder",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Reminder to take out the trash on specific days.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/trash-reminder.groovy")

preferences {
    page(name: "settings", title: "Trash Reminder", install: true, uninstall: true) {
        section {
            input "trashDays", "enum", title: "Trash Days", multiple: true, required: true, options: ["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"]
            input "recycleDay", "enum", title: "Recycle Day", multiple: false, required: true, options: ["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"]
            input "isRecycleWeek", "bool", title: "Recycle this week?", required: true, defaultValue: true
        }
        section {
            input "person", "device.PersonStatus", title: "Person to Notify", multiple: false, required: true
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
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
    subscribe(person, "status", personHandler_TrashReminder)
    
    def currentTime = new Date()
    schedule("$currentTime.seconds 0 0 ? * 1 *", updateRecycleWeek)
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def personHandler_TrashReminder(evt) {
    logDebug("personHandler_TrashReminder: ${evt.device} changed to ${evt.value}")
    
    if (evt.value == "home") {
        if (timeOfDayIsBetween(timeToday("00:00"), timeToday("12:00"), new Date(), location.timeZone)) {
            def df = new java.text.SimpleDateFormat("EEEE")
            df.setTimeZone(location.timeZone)
            def day = df.format(new Date())
        
            def isTrashDay = trashDays.contains(day)
            def isRecycleDay = (recycleDay == day && isRecycleWeek)
            
            if (isTrashDay && isRecycleDay) {
                person.deviceNotification("Take out the trash and recycle!")
            } else if (isTrashDay) {
                person.deviceNotification("Take out the trash!")
            } else if (isRecycleDay) {
                person.deviceNotification("Take out the recycle!")
            }
        }
    }
}

def updateRecycleWeek() {
    logDebug("updateRecycleWeek")
    
    if (isRecycleWeek) {
        app.updateSetting("isRecycleWeek", false)
    } else {
        app.updateSetting("isRecycleWeek", true)
    }
}