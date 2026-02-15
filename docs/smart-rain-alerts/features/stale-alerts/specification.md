# Stale Alerts Specification
Smart Rain Alerts

## Purpose

The goal is to alert the user when the weather station stops reporting data and when the data begins updating again. Internal state should be cleared and reset as needed.

## Requirements

- Keep a unified staleness definition and handling with the existing stale history handling. Use the existing thresholds and checks for staleness.
 
- Schedule a process every 5 minutes to check for staleness. 

- Staleness checks should also be performed on event subscriptions and during initialize(), in case the weather sensor sends an update that contains old data.

- Staleness should be checked against the dateutc value from the weather station. It should not check individual sensors.

- An alert should be displayed when staleness first occurs, but not repeatedly. Use a state  variable to track staleness status. 
 
- The existing "device checks" functionality should be used to repeat the alert once per day. 
 
- When staleness occurs, clear the state variables for sensor history, previous values, and flags for rain confirmed, false positive, and wet trend active.
  
- An alert should be displayed when the weather station resumes reporting fresh data. 
 
- Avoid creating a situation where the staleness alert is sent, but the history is not cleared. 
 
- Avoid creating a situation where the history is cleared, but the staleness alert is not sent. 

- The existing stale history handling can be modified to align with these new requirements, if necessary.