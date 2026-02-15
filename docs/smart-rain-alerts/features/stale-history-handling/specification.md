# Stale History Handling Specification
Smart Rain Alerts

## Purpose

The goal is to improve the reliability of the trend histories when dealing with stale sensor data. When processing an update, the app should get the latest timestamp from the weather station and compare it against the last update it processed. If the last update was more than 45 minutes ago, it should clear the history state variables.

## Requirements

- Store the timestamp each time a new update is processed using the existing dateutc value from the weather station.

- Compare the latest timestamp to the previous processed timestamp.

- Create a config variable called staleThresholdMinutes with a value of 45.

- If the difference between the current timestamp and previous processed timestamp (in minutes) is greater than staleThresholdMinutes, all trend histories should be cleared and all previous-value tracking variables should be set to null.

- If the current timestamp is the same as the previous processed timestamp, all processing should be skipped.

- The lastProcessedTimestamp must be updated only after processing the update.