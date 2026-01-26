# Smart Rain Alerts

Smart Rain Alerts is a Hubitat app that provides **actionable rain-related alerts** using a weather station, while explicitly acknowledging the limitations of consumer-grade sensors.

The app deliberately separates:
- **Detection** (what is actually happening),
- **Confirmation** (whether a sensor reading makes sense),
- **Trends** (whether conditions are becoming wetter or drier).

This separation avoids overfitting, reduces alert noise, and keeps alerts aligned with real human decisions.

—

## Core Design Philosophy

1. **The rain sensor is the only true precipitation detector**
   - If the rain sensor reports rain, rain *may* be occurring.
   - If the rain sensor reports zero, rain *may still* be occurring (e.g., drizzle).

2. **Environmental data cannot detect rain**
   - Temperature, humidity, dew point, wind, and VPD describe *conditions*, not precipitation.
   - These values are useful only as **context** or **sanity checks**.

3. **Alerts should reflect state transitions, not continuous conditions**
   - Alerts exist to influence behavior.
   - State latches and hysteresis are preferred over raw thresholds.

—

## High-Level Concepts

The app computes two independent scores:

| Score | What it Represents | What it Does *Not* Do |
|——|-——————|————————|
| **Probability** | Whether the environment is trending from dry → wet | Detect rain |
| **Confidence** | Whether conditions support rain *if the sensor reports rain* | Detect rain |

Rain detection itself is handled **exclusively** by the rain sensor.

—

## Rain Detection Logic

### Rain States

The app tracks three rain-related states:

- **No rain**
- **Rain confirmed**
- **Rain sensor false positive**

These states are mutually exclusive and driven by transitions.

### Alerts

| Condition | Alert |
|———|——|
| Rain sensor > 0 AND confidence ≥ threshold | 🌧️ Rain confirmed |
| Rain sensor > 0 AND confidence < threshold | ⚠️ Rain sensor false positive |
| Rain confirmed → confidence drops below threshold | ⚠️ Rain confidence lost |
| Rain confirmed → rain sensor returns to zero | ☀️ Rain has stopped |

### Key Assumptions

- The rain sensor is *usually* correct but can glitch.
- Environmental conditions can invalidate a rain reading but cannot replace it.
- Confidence decay is intentionally **disabled** to keep semantics clean.

—

## Probability Score (Environmental Trend)

### Purpose

Probability answers a single question:

> **“Is the environment becoming wetter?”**

It is **not** a precipitation forecast and is not expected to predict every rain event.

### Inputs

- Absolute Relative Humidity
- RH trend
- VPD trend
- Wind trend

### Behavior

- Probability rises when the environment shifts toward saturation.
- Once the environment is wet, probability may remain elevated for long periods.
- Rain may start, stop, and restart without probability changing meaningfully.

### Alerts

| Condition | Alert |
|———|——|
| Probability crosses upper threshold | 💦 Environment is wetter, rain likely |
| Probability drops below lower threshold | 🌵 Environment is drier |

Hysteresis prevents alert flapping.

—

## Confidence Score (Rain Plausibility)

### Purpose

Confidence answers:

> **“If the rain sensor says it’s raining, does that make physical sense?”**

It is a **sanity check**, not a detector.

### Inputs

- Relative Humidity
- Dew point proximity
- Vapor Pressure Deficit
- Wind speed

### Key Properties

- Confidence is **ignored** unless the rain sensor reports rain.
- No decay is applied — confidence reflects *current conditions only*.
- Seasonal scaling adjusts interpretation, not raw inputs.

—

## Seasonal Intelligence

Temperature-based scaling is applied **after** score calculation.

| Season | Effect |
|——|-——|
| Cool | Boost probability and confidence |
| Hot | Dampen probability and confidence |

Rationale:
- Cold drizzle persists under marginal conditions.
- Hot rain requires stronger signals and dries faster.

—

## What This App Does NOT Do

- ❌ Predict rain onset reliably
- ❌ Detect drizzle without a rain sensor
- ❌ Estimate rainfall amounts
- ❌ Model cloud physics or radar data

These are intentional omissions to avoid false certainty.

—

## Known Limitations & Gotchas

### Drizzle Detection
- Very light drizzle may not register on the rain sensor.
- Environmental conditions alone are insufficient to confirm drizzle.
- The app intentionally avoids “drizzle guessing” to prevent stuck states.

### Long Wet Periods
- Probability may remain elevated for days.
- This is expected behavior and not a bug.

### Sensor Resolution
- Rain sensors typically quantize measurements (e.g., 0.1 mm increments).
- This can cause binary-looking behavior at low rainfall rates.

—

## Debug Logging

The app logs:
- Raw sensor values and deltas
- Base and temperature-adjusted scores
- Component contributions to probability and confidence

This is intentional and designed for long-term tuning.

—

## Future Improvements (Ideas)

These are **not commitments**, just possibilities:

- Optional confidence decay (reintroduced carefully)
- Separate drizzle advisory alerts
- User-selectable alert verbosity
- Per-season threshold overrides
- Visual trend graphs
- Adaptive thresholds based on local climate history

—

## Guiding Principle Going Forward

> **If an alert doesn’t change what I would do, it doesn’t belong.**

All future changes should be evaluated against that standard.

—

## License

Apache License 2.0  
Copyright © 2026 Michael Pierce