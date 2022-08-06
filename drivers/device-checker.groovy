/**
 *  Device Checker Device Handler
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
 
String getVersionNum() { return "2.1.0" }
String getVersionLabel() { return "Device Checker, version ${getVersionNum()} on ${getPlatform()}" }

metadata {
    definition (
		name: "Device Checker", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/device-checker.groovy"
	) {
        capability "Actuator"
        capability "Sensor"

        attribute "deviceCheck", "enum", ["active", "inactive"]
        
        command "runDeviceCheck"
        
        command "addBatteryMessage", ["number", "string"]
        command "addInactiveMessage", ["number", "string"]
    }
}

def runDeviceCheck() {
    atomicState.batteryMessageText = [:]
    atomicState.inactiveMessageText = [:]
    
    sendEvent(name: "deviceCheck", value: "active")
    runIn(10, resetDeviceCheck)
}

def addBatteryMessage(deviceId, message) {
    atomicState.batteryMessageText[deviceId] = message
    runIn(10, resetDeviceCheck)
}

def addInactiveMessage(deviceId, message) {
  	atomicState.inactiveMessageText[deviceId] = message
  	runIn(10, resetDeviceCheck)
}

def resetDeviceCheck() {
    sendEvent(name: "deviceCheck", value: "inactive")
}

def getBatteryMessages() {
    return atomicState.batteryMessageText.values()
}

def getInactiveMessages() {
    return atomicState.inactiveMessageText.values()
}