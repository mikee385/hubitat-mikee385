/**
 *  URL Logger
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
 
String getAppName() { return "URL Logger" }
String getAppVersion() { return "1.0.0" }
String getAppTitle() { return "${getAppName()}, version ${getAppVersion()}" }

#include mikee385.debug-library

definition(
    name: getAppName(),
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Provides a URL to log HTTP messages from an external source (e.g. Locative, Owntracks).",
    category: "My Apps",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/apps/url-logger.groovy"
)

preferences {
    page(name: "settings", title: getAppTitle(), install: true, uninstall: true) {
        section {
            input name: "enableDebugLog", type: "bool", title: "Enable debug logging?", defaultValue: false
            label title: "Assign a name", required: true
        }
    }
}

mappings {
    path("/endpoint") {
        action: [
            GET: "urlHandler_endpoint",
            POST: "urlHandler_endpoint"
        ]
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
    // URLs
    if(!state.accessToken) {
        createAccessToken()
    }
    state.localUrl = "${getFullLocalApiServerUrl()}/endpoint?access_token=$state.accessToken"
    state.externalUrl = "${getFullApiServerUrl()}/endpoint?access_token=$state.accessToken"
}

def urlHandler_endpoint() {
    logDebug("urlHandler_endpoint")
    
    log.info(request.body)
}