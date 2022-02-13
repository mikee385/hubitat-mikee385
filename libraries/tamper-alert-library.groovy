/**
 *  name: Tamper Alert Library
 *  author: Michael Pierce
 *  version: 1.0.0
 *  minimumHEVersion: 2.2.8
 *  licenseFile: https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/LICENSE
 *  releaseNotes: Initial commit
 *  dateReleased: 2022-02-13
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

library (
    name: "tamper-alert-library",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Common method for sending an alert when a tamper alarm is triggered for a device.",
    category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/libraries/tamper-alert-library.groovy"
)

def handler_TamperAlert(evt) {
    logDebug("handler_TamperAlert: ${evt.device} changed to ${evt.value}")
    
    personToNotify.deviceNotification("Tamper alert for ${evt.device}!")
}