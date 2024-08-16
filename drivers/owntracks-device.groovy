/**
 *  OwnTracks Integration Driver
 *
 *  Copyright 2024 Michael Pierce
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
 
String getDeviceName() { return "OwnTracks Device" }
String getDeviceVersion() { return "1.0.0" }
String getDeviceTitle() { return "${getDeviceName()}, version ${getDeviceVersion()}" }

metadata {
    definition (
		name: getDeviceName(), 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/owntracks-device.groovy"
	) {
        capability "Presence Sensor"
        capability "Sensor"
        
        attribute "_type", "string"
        attribute "wtst", "number"
        attribute "lat", "number"
        attribute "lon", "number"
        attribute "tst", "number"
        attribute "acc", "number"
        attribute "tid", "string"
        attribute "event", "string"
        attribute "desc", "string"
        attribute "t", "string"
        attribute "rid", "string"
        
        command "arrived"
        command "departed"
    }
}

def update(mapParams) {
    sendEvent(name: "_type", value: mapParams["_type"])
    sendEvent(name: "wtst", value: mapParams["wtst"]?.toBigDecimal())
    sendEvent(name: "lat", value: mapParams["lat"]?.toBigDecimal(), unit: '°')
    sendEvent(name: "lon", value: mapParams["lon"]?.toBigDecimal(), unit: '°')
    sendEvent(name: "tst", value: mapParams["tst"]?.toBigDecimal())
    sendEvent(name: "acc", value: mapParams["acc"]?.toBigDecimal(), unit: 'm')
    sendEvent(name: "tid", value: mapParams["tid"])
    sendEvent(name: "event", value: mapParams["event"])
    sendEvent(name: "desc", value: mapParams["desc"])
    sendEvent(name: "t", value: mapParams["t"])
    sendEvent(name: "rid", value: mapParams["rid"])
    
    if (mapParams["_type"] == "transition") {
        if (mapParams["desc"] == parent.getPresenceName()) {
            if (mapParams["event"] == "enter") {
                arrived()
            } else if (mapParams["event"] == "leave") {
                departed()
            }
        }
    } 
}

def arrived() {
    sendEvent(name: "presence", value: "present")
}

def departed() {
    sendEvent(name: "presence", value: "not present")
}