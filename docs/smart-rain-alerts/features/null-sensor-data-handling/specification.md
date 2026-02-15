# Null Sensor Data Handling Specification
Smart Rain Alerts

## Purpose

Prevent processing when the weather station reports null attribute values, while still allowing staleness detection to function correctly.

## Requirements

- Ignore updates when the weather station timestamp (dateutc) is null.

- Ignore updates when any required weather station attributes are null. Log which attributes are missing.

- Ignored updates must not update sensor history, previous values, timestamps, or rain state flags.

- Staleness detection must continue to function when null updates are received.

- Staleness should be calculated using state.lastProcessedTimestamp (the last successfully processed update), not the sensor timestamp.

- Perform null checks before duplicate detection and staleness checks.

- The behavior should be consistent with existing stale and duplicate update handling.