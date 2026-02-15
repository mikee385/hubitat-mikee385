# Stale Alerts Specification
Smart Rain Alerts

## Purpose

The goal is to alert the user when the weather station stops reporting data and when the data begins updating again. Internal state should be cleared and reset as needed.

## Requirements

- Schedule a process to check for staleness, rather than relying on event subscriptions. It could run at regular intervals, or it could be scheduled as-hoc as needed. 

- Staleness checks should also be performed on event subscriptions, in case the weather sense sends an update that contains old data.

- Staleness should be checked against the dateutc value from the weather station. It should not check individual sensors.

- An alert should be displayed when staleness first occurs, but not repeatedly. 
 
- The existing "device checks" functionality should be used to repeat the alert once per day. 
  
- An alert should be displayed when the weather station starts reporting new data again. 
 
- Consider clearing the rain confirmed, false positive, and wet trend active variables when staleness is detected, although this may not be necessary, since these are already cleared as soon as the data is restored.
 
- Try to be as consistent as possible with the existing stale history handling. Try not to create a situation where the staleness alert is sent, but the history is not cleared. Try not to create a situation where the history is cleared, but the staleness alert is not sent. 

- The existing stale history handling can be modified to align with these new requirements, if necessary.