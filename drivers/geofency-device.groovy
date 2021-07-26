/**
 *  Geofency Integration Driver
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
 
String getVersionNum() { return "1.0.0" }
String getVersionLabel() { return "Geofency Device, version ${getVersionNum()} on ${getPlatform()}" }

 metadata {
    definition (
		name: "Geofency Device", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/geofency-device.groovy"
	) {
        capability "Presence Sensor"
        capability "Sensor"
        
        attribute "id", "string"
        attribute "name", "string"
        attribute "entry", "number"
        attribute "date", "string"
        attribute "latitude", "number"
        attribute "longitude", "number"
        attribute "radius", "number"
        attribute "address", "string"
        attribute "device", "string"
        attribute "beaconUUID", "string"
        attribute "major", "number"
        attribute "minor", "number"
        attribute "currentLatitude", "number"
        attribute "currentLongitude", "number"
        attribute "wifiSSID", "string"
        attribute "wifiBSSID", "string"
        attribute "motion", "string"
        
        command "arrived"
        command "departed"
    }
}

def update(mapParams) {
    sendEvent(name: "id", value: mapParams["id"])
    sendEvent(name: "name", value: mapParams["name"])
    sendEvent(name: "entry", value: mapParams["entry"]?.toBigDecimal())
    sendEvent(name: "date", value: mapParams["date"])
    sendEvent(name: "latitude", value: mapParams["latitude"]?.toBigDecimal(), unit: '°')
    sendEvent(name: "longitude", value: mapParams["longitude"]?.toBigDecimal(), unit: '°')
    sendEvent(name: "radius", value: mapParams["radius"]?.toBigDecimal(), unit: 'm')
    sendEvent(name: "device", value: mapParams["device"])
    sendEvent(name: "beaconUUID", value: mapParams["beaconUUID"])
    sendEvent(name: "major", value: mapParams["major"]?.toBigDecimal())
    sendEvent(name: "minor", value: mapParams["minor"]?.toBigDecimal())
    sendEvent(name: "currentLatitude", value: mapParams["currentLatitude"]?.toBigDecimal(), unit: '°')
    sendEvent(name: "currentLongitude", value: mapParams["currentLongitude"]?.toBigDecimal(), unit: '°')
    sendEvent(name: "wifiSSID", value: mapParams["wifiSSID"])
    sendEvent(name: "wifiBSSID", value: mapParams["wifiBSSID"])
    sendEvent(name: "motion", value: mapParams["motion"])
    
    if (mapParams["name"] == parent.getPresenceName()) {
        if (mapParams["entry"] == "1") {
            arrived()
        } else if (mapParams["entry"] == "0") {
            departed()
        }
    }
}

def arrived() {
    sendEvent(name: "presence", value: "present")
}

def departed() {
    sendEvent(name: "presence", value: "not present")
}