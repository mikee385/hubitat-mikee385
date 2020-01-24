/**
 *  Ecobee Home/Away Controller
 *
 *  Copyright 2019 Michael Pierce
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
 
String getVersionNum() 		{ return "1.0.0-beta5" }
String getVersionLabel() 	{ return "Ecobee Home/Away Controller, version ${getVersionNum()} on ${getPlatform()}" }

definition(
    name: "Ecobee Home/Away",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Updates the home/away status of Ecobee thermostats based on the Hubitat mode and the current Ecobee schedule.",
    category: "My Apps",
    iconUrl: "https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/thermometer.png",
    iconX2Url: "https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/thermometer.png",
	importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/ecobee-home-away.groovy")

def getTurnedOn() {
    return "Turned On"
}

def getTurnedOff() {
    return "Turned Off"
}

preferences {
    page(name:"settings")
}

def settings() {
    dynamicPage(name: "settings", title: "Ecobee Home/Away", install: true, uninstall: true, submitOnChange: true) {
        section("Ecobee Thermostats") {
            input "thermostats", "capability.thermostat", title: "Which thermostats should be controlled?", multiple: true, required: true, submitOnChange: true
        }
        if (thermostats) {
			section("Pause Switch") {
				input "pauseSwitch", "capability.switch", title: "Which switch should pause and resume the automation? (optional)", required: false, submitOnChange: true
				if (pauseSwitch) input "pauseSwitchStatus", "enum", title: "Pause automation when switch is:", options: [turnedOn, turnedOff], required: true, submitOnChange: true
				if (pauseSwitch && pauseSwitchStatus) {
					if (reminderPause == turnedOn) {
						paragraph "Automation will pause when ${pauseSwitch} is turned on and will resume when ${pauseSwitch} is turned off."
					} else {
						paragraph "Automation will pause when ${pauseSwitch} is turned off and will resume when ${pauseSwitch} is turned on."
					}
				}
			}
			section("Notification") {
				input "sendPushMessage", "capability.notification", title: "Notification Devices:", multiple: true, required: false, submitOnChange: true
			}
		}
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
	subscribe(location, "mode", modeHandler)

	for (thermostat in thermostats) {
		subscribe(thermostat, "scheduledProgram", scheduleHandler)
		subscribe(thermostat, "currentProgram", currentHandler)
		subscribe(thermostat, "currentProgramName", currentHandler)
	}
}

def modeHandler(evt) {
	if (sendPushMessage)
		sendPushMessage.deviceNotification("EHA: Mode changed to ${evt.value}")

	for (thermostat in thermostats) {
		updateThermostat(thermostat, evt.value, false)
	}
}

def scheduleHandler(evt) {
	if (sendPushMessage)
		sendPushMessage.deviceNotification("EHA: ${evt.device} schedule changed to ${evt.value}")

	updateThermostat(evt.device, location.mode, true)
}

def currentHandler(evt) {
	if (sendPushMessage)
		sendPushMessage.deviceNotification("EHA: ${evt.device} current changed to ${evt.value}")

	updateThermostat(evt.device, location.mode, false)
}

private def updateThermostat(thermostat, mode, scheduleChanged) {
	if (sendPushMessage)
		sendPushMessage.deviceNotification("EHA: Updating ${thermostat}")

	def scheduleId = thermostat.currentValue("scheduledProgram")
	def currentId = thermostat.currentValue("currentProgram")
	def currentName = thermostat.currentValue("currentProgramName")
    
	if (mode == "Away") {
		if (scheduleId == "Away") {
			if (sendPushMessage)
				sendPushMessage.deviceNotification("EHA: Setting schedule away for ${thermostat}")

			setScheduleAway(thermostat, currentId, currentName)
		} else if (scheduleChanged) {
			if (sendPushMessage)
				sendPushMessage.deviceNotification("EHA: Setting schedule home for ${thermostat}")

			setScheduleHome(thermostat, currentId, currentName)
		} else {
			if (sendPushMessage)
				sendPushMessage.deviceNotification("EHA: Setting hold away for ${thermostat}")

			setHoldAway(thermostat, currentId, currentName)
		}
	} else {
		if (scheduleId == "Away") {
			if (sendPushMessage)
				sendPushMessage.deviceNotification("EHA: Setting hold home for ${thermostat}")

			setHoldHome(thermostat, currentId, currentName)
		} else {
			if (sendPushMessage)
				sendPushMessage.deviceNotification("EHA: Setting schedule home for ${thermostat}")

			setScheduleHome(thermostat, currentId, currentName)
		}
	}
}

private def setScheduleAway(thermostat, currentId, currentName) {
	if (currentName == "Hold: Away") {
		if (sendPushMessage)
			sendPushMessage.deviceNotification("EHA: Resuming program for ${thermostat} (Hold: Away)")

		thermostat.resumeProgram()
	} else if (currentId != "Away") {
		if (sendPushMessage)
			sendPushMessage.deviceNotification("EHA: Resuming program for ${thermostat} (Home)")

		thermostat.resumeProgram()
	}
}

private def setHoldAway(thermostat, currentId, currentName) {
	if (currentId != "Away") {
		if (sendPushMessage)
			sendPushMessage.deviceNotification("EHA: Setting Hold: Away for ${thermostat}")

		thermostat.away()
	}
}

private def setScheduleHome(thermostat, currentId, currentName) {
	if (currentName == "Hold: Home") {
		if (sendPushMessage)
			sendPushMessage.deviceNotification("EHA: Resuming program for ${thermostat} (Hold: Home)")

		thermostat.resumeProgram()
	} else if (currentId == "Away") {
		if (sendPushMessage)
			sendPushMessage.deviceNotification("EHA: Resuming program for ${thermostat} (Away)")

		thermostat.resumeProgram()
	}
}

private def setHoldHome(thermostat, currentId, currentName) {
	if (currentId == "Away") {
		if (sendPushMessage)
			sendPushMessage.deviceNotification("EHA: Setting Hold: Home for ${thermostat}")

		thermostat.present()
	}
}