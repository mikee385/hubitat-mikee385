/**
 *  Virtual Alexa Switch Driver
 *
 *  Copyright 2020 Michael Pierce
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
 metadata {
	definition (name: "Virtual Alexa Switch", namespace: "mikee385", author: "Michael Pierce", importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/drivers/virtual-alexa-switch.groovy") {
		capability "Actuator"
		capability "Contact Sensor"
		capability "Sensor"
		capability "Switch"
	}
}

def on() {
	sendEvent(name: "switch", value: "on")
	sendEvent(name: "contact", value: "open")
}

def off() {
	sendEvent(name: "switch", value: "off")
	sendEvent(name: "contact", value: "closed")
}