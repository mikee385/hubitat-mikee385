# Planning Handoff — 5-Sample Windowed Delta Trend Detection

## 1. Summary of Desired Behavior After the Change

After this change, trend detection in Smart Rain Alerts will use **windowed delta calculations** instead of **single-interval deltas** for:
 
- Relative Humidity (RH)
- Vapor Pressure Deficit (VPD)
- Wind Speed
- Barometric Pressure

The system will maintain a **rolling FIFO window of up to five samples per metric**.

Windowed delta is defined as:

```
windowedDelta = newestSample − oldestSample
```

Directional semantics remain unchanged for VPD and pressure.

The system must:
 
- Operate immediately with partial windows (1–4 samples)
- Produce a delta of `0` when only one sample exists
- Continue using the existing probability scoring model
- Preserve trend weights
- Preserve TrendMax configuration values
- Preserve confidence scoring structure
- Preserve alert logic

Pressure trend calculations must use the windowed delta in **both probability scoring and confidence scoring**.

State resets must cause windows to rebuild naturally from incoming samples.

---

## 2. Non-Goals (Explicitly Out of Scope)

This change will NOT:
 
- Modify probability scoring structure
- Change trend weights
- Modify TrendMax configuration values
- Change alert thresholds
- Introduce filtering of sensor samples
- Introduce rolling averages
- Introduce slope calculations
- Introduce momentum scoring
- Change logging philosophy
- Change seasonal scaling logic
- Change alert behavior
- Introduce new sensors

This change is strictly a **measurement-layer modification**.

---

## 3. High-Level Design

The design replaces single previous-value storage with **independent rolling windows stored in state**.

Each metric maintains its own window:
 
- RH window
- Wind window
- Pressure window
- VPD window

Execution flow remains unchanged:

```
sensor event → sensorHandler() → calculate()
```

Within `calculate()`, the only behavioral changes are:
 
- Window maintenance
- Windowed delta calculation replacing single-interval delta usage

The probability model and confidence scoring remain structurally unchanged.

---

## 4. Detailed Step-by-Step Plan

### Step 1 — Replace Single-Value State Storage with Rolling Windows

For each metric:
 
- RH
- Wind
- Pressure
- VPD

Maintain a rolling list of samples in state.

Requirements:
 
- Append every new sample
- Maintain chronological order
- Trim to a maximum size of five using FIFO behavior

---

### Step 2 — Store Computed VPD in the VPD Window

After VPD is calculated:
 
- Append the computed VPD value to the VPD window

Raw sensor inputs must not be stored in the VPD window.

---

### Step 3 — Implement Partial-Window Behavior

Window rules:
 
- Window size = 1 → delta = 0
- Window size = 2–5 → delta = newest − oldest

This must apply independently per metric.

---

### Step 4 — Replace Delta Calculations

Replace single-interval delta logic with windowed delta for:
 
- RH
- VPD
- Wind
- Pressure

Directional semantics must remain:

```
RH trend = newestRH − oldestRH
Wind trend = newestWind − oldestWind
VPD trend = oldestVPD − newestVPD
Pressure trend = oldestPressure − newestPressure
```

---

### Step 5 — Update Pressure Delta Usage

Pressure trend must use windowed delta in:
 
- Probability scoring
- Confidence scoring

No single-interval pressure delta calculations may remain.

---

### Step 6 — Preserve Scoring Pipeline

The following must remain unchanged:
 
- Normalization using TrendMax
- Probability scoring formula
- Confidence scoring logic
- Alert evaluation
- Seasonal scaling

---

### Step 7 — Preserve State Reset Behavior

No initialization logic should be introduced.

Windows must rebuild naturally as new samples arrive.

---

## 5. Error Handling and Edge-Case Behavior

### Cold Start
 
- Windows begin empty
- First sample produces delta = 0

---

### Partial Windows

Trend scoring must function with:
 
- 1 sample
- 2 samples
- 3 samples
- 4 samples

---

### Irregular Sampling Intervals

 
- No special handling required

---

### Sensor Anomalies

 
- All samples are accepted without filtering

---

### State Reset

 
- Windows rebuild naturally from new samples

---

## 6. Logging / Observability Expectations

Logging must continue to show:
 
- Calculated delta values
- Directional interpretation
- Probability contribution per signal

The logging format remains unchanged. Only the interpretation of delta changes to windowed net change.

---

## 7. Testing Considerations (High-Level)

Testing should verify:
 
- FIFO window trimming
- Partial-window delta correctness
- First-sample delta = 0
- Independent metric windows
- VPD window stores computed values
- Pressure delta used in confidence scoring
- Probability score stability
- State reset recovery

All tests validate **measurement behavior**, not scoring redesign.

---

Planning complete. Please explicitly approve before implementation begins.