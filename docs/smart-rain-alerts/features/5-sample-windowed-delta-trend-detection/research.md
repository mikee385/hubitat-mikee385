# Smart Rain Alerts — Windowed Trend Detection
## Research Phase Handoff Document

This document summarizes the findings of the **Research Phase** for upgrading Smart Rain Alerts from single-interval delta detection to 5-sample windowed delta detection.

This document does not propose implementation steps.  
It exists to formally capture current behavior, constraints, risks, and clarified decisions before planning begins.

---

# 1. Understanding of Current Behavior

## Execution Flow

The application operates as an event-driven Hubitat app:

```
sensor event → sensorHandler() → calculate()
```

Within `calculate()`:

- Current sensor values are read
- Derived metrics (including VPD and dew point) are computed
- Previous values are retrieved from `state`
- Probability score is computed
- Confidence score is computed
- Seasonal scaling is applied
- Alerts are evaluated
- Current values overwrite previous values in `state`

Trend signals are used inside probability scoring and partially inside confidence scoring (pressure only).

---

## Current Trend Detection Model

Trend deltas are calculated using single-sample comparisons:

```
dRH    = rh - prevRH
dVPD   = prevVPD - vpd
dWind  = wind - prevWind
dPress = prevPress - pressInHg
```

These deltas are normalized using configured TrendMax values:

```
sTrend = clamp(delta / trendMax, 0, 1)
```

Probability score is calculated using fixed weights:

```
score = 100 * (
    0.35 * sRHabs
  + 0.25 * sRHtrend
  + 0.20 * sVPDtrend
  + 0.10 * sWindTrend
  + 0.10 * sPressTrend
)
```

Confidence scoring uses instantaneous metrics plus a single-interval pressure delta.

State persistence currently stores only one previous value per metric.

---

# 2. Known Constraints and Invariants

## Scoring Model Must Remain Unchanged

The following elements are explicitly preserved:


- Probability score structure
- Trend weight distribution
- TrendMax configuration values
- Confidence scoring structure
- Alert thresholds
- Seasonal scaling logic
- Logging philosophy
- Alert behavior

This change affects only measurement of trend deltas.

---

## Directional Semantics Must Be Preserved

Trend direction must retain the existing meaning:


- Rising RH increases probability
- Increasing wind increases probability
- Falling VPD increases probability
- Falling pressure increases probability

Windowed deltas must preserve this directional interpretation.

---

## Runtime and Platform Constraints

The system runs as a Hubitat Groovy app with:


- Event-driven execution
- Persistent `state`
- Potential irregular sensor intervals
- Lightweight state storage requirements
- Single-threaded execution model

---

## Window Behavior Requirements

Each metric must:


- Maintain its own independent rolling window
- Store up to 5 samples
- Operate correctly with 1–5 samples
- Use FIFO trimming behavior
- Accept every new sample without filtering

When only one sample exists:

```
windowedDelta = 0
```

No special logic is required for state resets; windows rebuild naturally.

---

## TrendMax Semantics

After this change, TrendMax values represent:


- Maximum expected change across the window

They are no longer interpreted as per-sample limits.

No configuration values are changed.

---

## Pressure Trend Usage

Both probability and confidence scoring must use windowed pressure delta.

There shall be no remaining single-interval pressure delta calculations.

---

## Metric Storage Requirements

Each metric stores its own values:


- RH window stores RH values
- Wind window stores wind speed values
- Pressure window stores pressure values
- VPD window stores computed VPD values

Derived VPD values are stored after computation, not raw inputs.

---

# 3. Potential Edge Cases and Risk Areas

## Startup / Cold State

When state is empty:

- Windows begin accumulating naturally
- Delta is zero when only one sample exists
- Early scoring may underrepresent trend movement

This is intentional and deterministic.

---

## Irregular Sampling Intervals

A 5-sample window does not guarantee a fixed time span.

Possible effects:


- Short intervals → compressed observation window
- Long intervals → expanded observation window

TrendMax values assume typical sampling frequency.

---

## Sensor Anomalies

Because all samples are accepted:


- Sudden spikes enter the window
- Extreme values may influence deltas
- Behavior is governed entirely by normalization

No filtering is introduced by specification.

---

## Logging Interpretation Shift

Current logs display single-interval deltas.

After change, logged deltas will represent:


- Net change across the window

Logging format remains consistent, but interpretation shifts.

---

## Magnitude Scaling

Windowed deltas may produce larger absolute changes than single-sample deltas.

TrendMax now represents window-scale expectations.

Normalization remains unchanged.

---

# 4. Explicit Assumptions

The following assumptions underpin the specification:


- Sensor readings occur approximately every ~5 minutes
- Windows contain up to 5 samples
- Each metric window is independent
- Samples are appended in chronological order
- Oldest sample is removed when window exceeds size 5
- Delta is newest − oldest (directionally adjusted where required)
- First-sample delta equals zero
- VPD window stores computed values
- All new samples are accepted
- State resets require no special handling
- Scoring structure remains unchanged
- Alert logic remains unchanged

---

# 5. Clarifications Resolved During Research

The following design questions were resolved:


- Trend scoring does not wait for 5 samples
- Delta equals zero when only one sample exists
- New samples are always appended
- Windows use FIFO trimming
- State resets rebuild naturally
- Pressure delta uses windowed delta in both scoring paths
- Each metric maintains its own window
- TrendMax represents window-scale limits
- VPD window stores computed VPD values

No open behavioral questions remain.

---

# 6. Research Conclusion

The existing system:

- Uses single-interval deltas
- Stores only one historical value per metric
- Normalizes deltas using TrendMax values
- Applies fixed weighting to compute probability
- Uses pressure delta in both probability and confidence scoring

The new specification:

- Replaces single-sample deltas with 5-sample windowed deltas
- Preserves scoring structure and semantics
- Preserves directional meaning
- Preserves alert logic
- Preserves interpretability
- Introduces deterministic partial-window behavior
- Requires independent per-metric rolling windows

The behavior is fully specified.

No open ambiguities remain.

---

**Status:**  
Research Phase complete.  
System understanding confirmed.  
Ready to proceed to Planning Phase.