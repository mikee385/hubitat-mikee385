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

The rolling history window shall contain:

 
- Exactly **5 samples**
- Stored per-sensor metric
- Updated whenever new sensor readings are processed
- Maintained in chronological order
- Trimmed to the most recent five values

This window size balances:

 
- Noise reduction
- Storm responsiveness
- Interpretability
- Alignment with atmospheric change timescales

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

## Directional Semantics

Some signals increase during storm formation, while others decrease.  
Windowed deltas must preserve the existing directional meaning used by the probability scoring system.

Conceptual definitions:

```
RH trend     = newestRH − oldestRH
Wind trend   = newestWind − oldestWind
VPD trend    = oldestVPD − newestVPD
Pressure trend = oldestPressure − newestPressure
```

This maintains the existing interpretation:

 
- Rising humidity increases rain probability
- Increasing wind increases rain probability
- Falling VPD increases rain probability
- Falling pressure increases rain probability

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

This preserves the original scoring philosophy while improving signal reliability, robustness to noise, and detection of sustained atmospheric changes that precede rainfall.

The probability model, thresholds, and user-visible behavior should remain conceptually consistent while becoming more stable and trustworthy.