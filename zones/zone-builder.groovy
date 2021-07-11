/**
 *  Zone Builder
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

String getName() { return "Zone Builder" }
String getVersionNum() { return "1.0.1" }
String getVersionLabel() { return "${getName()}, version ${getVersionNum()}" }

definition(
    name: "${getName()}",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Creates Zone apps and devices to manage the occupancy status of each zone in your home based on the devices contained within it.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/zones/zone-builder.groovy")

preferences {
    page(name: "mainPage", title: "${getVersionLabel()}", uninstall: false, install: true) {
        section() {
            app(name: "childApps", appName: "Zone App", namespace: "mikee385", title: "New Zone...", multiple: true)
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
    createParentDevice()
}

def uninstalled() {
    deleteAllZones()
}

def logDebug(msg) {
    if (logEnable) {
        log.debug msg
    }
}

def getParentID() {
    return "zone:" + app.getId()
}

def createParentDevice() {
    def parentID = getParentID()
    def parent = getChildDevice(parentID)
    if (!parent) {
        parent = addChildDevice("mikee385", "Zone Parent", parentID, [label:"Zones", isComponent:true, name:"Zone Parent"])
    }
}

def addZoneDevice(appID, name) {
    def parentID = getParentID()
    def parent = getChildDevice(parentID)
    if (parent) {
        parent.addZoneDevice(appID, name)
    } else {
        log.error "No Parent Device Found."
    }
}

def getZoneDevice(appID) {
    def parentID = getParentID()
    def parent = getChildDevice(parentID)
    if (parent) {
        return parent.getZoneDevice(appID)
    } else {
        log.error "No Parent Device Found."
    }
}

def deleteZoneDevice(appID) {
    def parentID = getParentID()
    def parent = getChildDevice(parentID)
    if (parent) {
        parent.deleteZoneDevice(appID)
    } else {
        log.error "No Parent Device Found."
    }
}

def deleteAllZones() {
    def parentID = getParentID()
    def parent = getChildDevice(parentID)
    if (parent) {
        parent.deleteAllZones()
        deleteChildDevice(parentID)
    } else {
        log.error "No Parent Device Found."
    }
}