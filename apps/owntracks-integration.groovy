/**
 *  OwnTracks Integration
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
 
String getAppName() { return "OwnTracks Integration" }
String getAppVersion() { return "1.0.0" }
String getAppTitle() { return "${getAppName()}, version ${getAppVersion()}" }

#include mikee385.debug-library

definition(
    name: getAppName(),
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Provides a URL endpoint for OwnTracks and parses the response into device attributes.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/owntracks-integration.groovy"
)

preferences {
    page(name: "settings", title: getAppTitle(), install: true, uninstall: true) {
        section {
            input name: "presenceName", type: "string", title: "Location name to use for presence", required: true
        }
        section {
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/update") {
        action: [
            POST: "urlHandler_update"
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
    state.url = "${getFullApiServerUrl()}/update?access_token=$state.accessToken"
}

def getPresenceName() {
    return presenceName
}

def childDevice() {
    def childID = "owntracks:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("mikee385", "OwnTracks Device", childID, 1234, [label: app.label, isComponent: false])
    }
    return child
}

def urlHandler_update() {
    logDebug("urlHandler_update")
    logDebug(request.JSON)
    
    childDevice().update(request.JSON)
}