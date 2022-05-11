/**
 *  Locative Integration
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
 
String getVersionNum() { return "4.1.0" }
String getVersionLabel() { return "Locative Integration, version ${getVersionNum()} on ${getPlatform()}" }

#include mikee385.debug-library

definition(
    name: "Locative Integration",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Provides a URL endpoint for Locative and parses the response into device attributes.",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/locative-integration.groovy"
)

preferences {
    page(name: "settings", title: "Locative Integration", install: true, uninstall: true) {
        section {
            input name: "presenceId", type: "string", title: "ID to use for presence", required: true
        }
        section {
            input name: "enableMessageLog", type: "bool", title: "Enable logging of Locative message?", defaultValue: false
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
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

def getPresenceId() {
    return presenceId
}

def childDevice() {
    def childID = "locative:" + app.getId()
    def child = getChildDevice(childID)
    if (!child) {
        child = addChildDevice("mikee385", "Locative Device", childID, 1234, [label: app.label, isComponent: false])
    }
    return child
}

def urlHandler_update() {
    logDebug("urlHandler_update")
    
    if (enableMessageLog) {
        logInfo(request.body)
    }
    
    def queryParams = request.body.split("&")
    def mapParams = queryParams.collectEntries { param -> param.split('=').collect { URLDecoder.decode(it) }}
    
    childDevice().update(mapParams)
}