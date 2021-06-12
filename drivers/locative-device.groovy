/**
 *  Locative Integration Driver
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
String getVersionLabel() { return "Locative Device, version ${getVersionNum()} on ${getPlatform()}" }

 metadata {
    definition (
		name: "Locative Device", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/locative-device.groovy"
	) {
        capability "Sensor"
        
        attribute "latitude", "number"
        attribute "longitude", "number"
        attribute "id", "string"
        attribute "device", "string"
        attribute "device_type", "string"
        attribute "device_model", "string"
        attribute "trigger", "enum", ["enter", "exit"]
        attribute "timestamp", "string"
    }
}

def uninstalled() {
    for (device in getChildDevices()) {
        deleteChildDevice(device.deviceNetworkId)
    }
}

def update(mapParams) {
    sendEvent(name: "latitude", value: mapParams["latitude"].toBigDecimal(), unit: '°')
    sendEvent(name: "longitude", value: mapParams["longitude"].toBigDecimal(), unit: '°')
    sendEvent(name: "id", value: mapParams["id"])
    sendEvent(name: "device", value: mapParams["device"])
    sendEvent(name: "device_type", value: mapParams["device_type"])
    sendEvent(name: "device_model", value: mapParams["device_model"])
    sendEvent(name: "trigger", value: mapParams["trigger"])
    sendEvent(name: "timestamp", value: new Date((mapParams["timestamp"].toBigDecimal()*1000).longValue()))
    
    def child = childDevice(mapParams["id"])
    if (mapParams["trigger"] == "enter") {
        child.arrived()
    } else if (mapParams["trigger"] == "exit") {
        child.departed()
    }
}

def childDevice(locationID) {
    def childID = device.getDeviceNetworkId() + ":" + locationID
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("hubitat", "Virtual Presence", childID, [name: "Locative Presence", label: "${parent.getUserName()} $locationID", isComponent: false])
    }
    return child
}