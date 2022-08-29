/**
 *  name: Time Library
 *  author: Michael Pierce
 *  version: 1.0.0
 *  minimumHEVersion: 2.2.8
 *  licenseFile: https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/LICENSE
 *  releaseNotes: Initial commit
 *  dateReleased: 2022-08-28
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
    name: "time-library",
    namespace: "mikee385",
    author: "Michael Pierce",
    description: "Common methods for handling time-based calculations.",
    category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/mikee385/hubitat-mikee385/master/libraries/time-library.groovy"
)

Boolean currentTimeIsBetween(firstTime, secondTime) {
   long currtime = now()
   long start = timeToday(firstTime, location.timeZone).time
   long stop = timeToday(secondTime, location.timeZone).time
   return (start == stop && currTime >= start && currTime - start < 60000) || (start <= stop ? currTime >= start && currTime < stop : currTime < stop || currTime >= start)
}