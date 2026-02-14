# Smart Rain Alerts — 5-Sample Windowed Delta Trend Detection
## Formal Context Document

## Purpose

This document describes the desired behavioral changes to the Smart Rain Alerts trend-detection system.  
The goal is to improve reliability of atmospheric trend detection by replacing single-sample deltas with a **5-sample windowed delta calculation** while preserving the existing probability scoring model, thresholds, and interpretability.

This change is intended to improve noise resistance, emphasize sustained atmospheric change, and maintain responsiveness to storm formation signals.

---

## Relevant Files

[Code](https://github.com/mikee385/hubitat-mikee385/blob/master/apps/smart-rain-alerts.groovy)  
[README](https://github.com/mikee385/hubitat-mikee385/blob/master/apps/smart-rain-alerts.md)

---

## Background

The current implementation calculates trend signals using **single-interval deltas** between the current sensor reading and the immediately previous reading.

Example of the current conceptual behavior:

```
delta = currentReading − previousReading
```

While responsive, this approach is sensitive to:


- Sensor jitter
- Gust-level wind spikes
- Irregular reporting intervals
- Single anomalous readings
- Short-lived environmental fluctuations

Storm formation, however, is typically characterized by **sustained atmospheric change over multiple readings**, not single-interval variation.

The system should therefore detect **net movement across a recent time window**, rather than instantaneous change.

---

## Design Goal

Replace single-sample trend detection with **windowed trend detection across the most recent five samples** for key atmospheric signals.

The updated trend logic should measure:

> Net environmental change over the recent observation window (~25 minutes)

rather than:

> Change since the previous reading.

This preserves the meaning of trend signals while improving stability.

---

## Window Size

The rolling history window shall:


- Contain **up to 5 samples**
- Store samples independently per metric
- Be updated whenever new sensor readings are processed
- Be maintained in chronological order
- Be trimmed to the most recent five values using FIFO behavior
- Work immediately with any number of samples (1–5)

### FIFO Behavior

When a new sample is added:

```
append new sample
if window size > 5:
    remove oldest sample
```

No special batching or synchronization between metrics is required.  
Each metric maintains its own independent rolling window.

---

## Partial Window Behavior

Trend scoring shall:


- Operate immediately upon receiving the first reading
- Work correctly with 1–4 samples
- Not wait for a fully populated 5-sample window

### First Sample Rule

When only one sample exists in a window:

```
windowedDelta = 0
```

Because:

```
newestSample == oldestSample
```

This ensures deterministic and stable startup behavior.

---

## State Reset Behavior

If the app is:


- Updated
- Reinstalled
- Or its `state` is cleared

The system shall:


- Rebuild windows naturally from new readings
- Apply no special initialization logic
- Resume normal trend behavior as samples accumulate

---

## Definition of Windowed Delta

Trend signals shall be calculated using **windowed delta**, defined as:

```
windowedDelta = newestSample − oldestSample
```

This represents the **total net change across the observation window**.

Unlike averaging or smoothing, this approach preserves strong directional signals while ignoring short-term oscillation.

---

## Metrics Using Windowed Delta

The following atmospheric trend signals shall use the windowed-delta calculation:


- Relative Humidity (RH)
- Vapor Pressure Deficit (VPD)
- Wind Speed
- Barometric Pressure

Each metric retains its existing interpretation within the probability model.

---

## Metric Storage Rules

Each metric maintains an independent rolling window.


- RH window stores RH values
- Wind window stores wind speed values
- Pressure window stores pressure values
- VPD window stores **computed VPD values**

The VPD window shall store derived VPD values after calculation, not raw sensor inputs.

---

## Directional Semantics

Some signals increase during storm formation, while others decrease.  
Windowed deltas must preserve the existing directional meaning used by the probability scoring system.

Conceptual definitions:

```
RH trend        = newestRH − oldestRH
Wind trend      = newestWind − oldestWind
VPD trend       = oldestVPD − newestVPD
Pressure trend  = oldestPressure − newestPressure
```

This maintains the existing interpretation:


- Rising humidity increases rain probability
- Increasing wind increases rain probability
- Falling VPD increases rain probability
- Falling pressure increases rain probability

---

## Pressure Trend Usage

Both uses of pressure delta shall use the windowed delta:


- Probability scoring
- Confidence scoring

There shall be no remaining single-interval pressure delta calculations.

---

## Relationship to Probability Scoring

This change is strictly a **measurement improvement**, not a scoring-model redesign.

The following shall remain unchanged:


- Probability score structure
- Trend weighting
- TrendMax configuration values
- Confidence score behavior
- Logging format philosophy
- Alert thresholds

The trend layer continues to represent **environmental movement**, but with improved signal quality.

---

## TrendMax Semantics

After this change, TrendMax configuration values represent:

> The maximum expected change across the window

They are no longer interpreted as per-sample limits, but as window-scale normalization limits.

No change to the actual configuration values is introduced by this specification.

---

## Sample Acceptance

A new sample shall:


- Always be added to its metric window
- Not be conditionally filtered based on completeness

This preserves system determinism and avoids hidden data suppression.

---

## Expected Behavioral Improvements

The windowed-delta approach should produce the following outcomes:


- Reduced sensitivity to single anomalous readings
- Suppression of wind gust spikes
- Stronger detection of sustained atmospheric change
- More stable probability score progression
- Improved detection of storm buildup periods
- More interpretable trend logs
- Fewer false probability spikes

---

## Non-Goals

This change does **not** introduce:


- Rolling averages in the probability score
- Momentum scoring
- Rate-of-change calculations
- Time-normalized slope calculations
- New probability weights
- Forecasting behavior
- Additional sensors
- Changes to alert logic

The system remains a **storm detection model**, not a predictive weather model.

---

## Logging Expectations

Trend logging should continue to reflect:


- The calculated delta values
- Directional interpretation
- Probability contribution of each signal

The transition to windowed delta should remain transparent and interpretable through logs.

---

## Summary

This change upgrades trend detection from:

```
single-interval delta detection
```

to:

```
5-sample windowed delta detection
```

The system:


- Works immediately with partial windows
- Uses FIFO rolling metric windows
- Uses windowed pressure deltas in both scoring paths
- Treats TrendMax as window-scale normalization limits
- Rebuilds naturally after state reset
- Stores computed VPD values in its own window

This preserves the original scoring philosophy while improving signal reliability, robustness to noise, and detection of sustained atmospheric changes that precede rainfall.

The probability model, thresholds, and user-visible behavior remain conceptually consistent while becoming more stable and trustworthy.