/**
 *  Presence URLs
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
String getVersionLabel() { return "Presence URLs, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library

definition(
    name: "Presence URLs",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Provides URLs to control the status of child presence sensor from an external source (e.g. Locative).",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/presence-urls.groovy"
)

preferences {
    page(name: "settings", title: "Presence URLs", install: true, uninstall: true) {
        section {
            input name: "logEnable", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/arrived") {
        action: [
            GET: "urlHandler_arrived",
            POST: "urlHandler_arrived"
        ]
    }
    path("/departed") {
        action: [
            GET: "urlHandler_departed",
            POST: "urlHandler_departed"
        ]
    }
}

def installed() {
    initialize()
}

def uninstalled() {
    for (device in getChildDevices()) {
        deleteChildDevice(device.deviceNetworkId)
    }
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    // Child Device
    def child = childDevice()
    
    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.arrivedUrl = "${getFullApiServerUrl()}/arrived?access_token=$state.accessToken"
    state.departedUrl = "${getFullApiServerUrl()}/departed?access_token=$state.accessToken"
}

def childDevice() {
    def childID = "presence:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("hubitat", "Virtual Presence", childID, 1234, [name: app.label, isComponent: false])
    }
    return child
}

def urlHandler_arrived() {
    logDebug("urlHandler_arrived")
    
    childDevice().arrived()
}

def urlHandler_departed() {
    logDebug("urlHandler_departed")
    
    childDevice().departed()
}