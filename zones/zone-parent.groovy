/**
 *  Zone Parent
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
 
String getName() { return "Zone Parent" }
String getVersionNum() { return "3.0.0" }
String getVersionLabel() { return "${getName()}, version ${getVersionNum()}" }

metadata {
    definition (
		name: "${getName()}", 
		namespace: "mikee385", 
		author: "Michael Pierce", 
		importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/zones/zone-parent.groovy"
	) {
    }
}

def installed() {
    initialize()
}

def updated() {
    unschedule()
    initialize()
}

def initialize() {
}

def uninstalled() {
    deleteAllZones()
}

def addZoneDevice(appId, name) {
    def zoneId = "zone:" + appId
    def zone = getChildDevice(zoneId)
    if (!zone) {
        addChildDevice("mikee385", "Zone Device", zoneId, [name: "Zone Device", label: name, isComponent: true])
    }
}

def getZoneDevice(appId) {
    def zoneId = "zone:" + appId
    return getChildDevice(zoneId)
}

def deleteZoneDevice(appId) {
    def zoneId = "zone:" + appId
    def zone = getChildDevice(zoneId)
    if (zone) {
        deleteChildDevice(zoneId)
    } else {
        log.error "No Zone Device found for $zoneId."
    }
}

def deleteAllZones() {
    for (device in getChildDevices()) {
        deleteChildDevice(device.deviceNetworkId)
    }
}