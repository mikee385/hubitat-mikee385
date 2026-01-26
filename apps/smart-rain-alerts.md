# Smart Rain Alerts

Smart Rain Alerts is a Hubitat app that provides **actionable rain-related alerts** using a personal weather station, while explicitly acknowledging the limitations of consumer-grade sensors.

Rather than attempting to “detect rain” from environmental data alone, the app separates:
- **Detection** (what is actually happening),
- **Confirmation** (whether a sensor reading makes physical sense),
- **Trends** (whether conditions are becoming wetter or drier).

This separation avoids overfitting, reduces alert noise, and keeps alerts aligned with real human decisions.

---

## Core Design Philosophy

1. **The rain sensor is the only true precipitation detector**
   - If the rain sensor reports rain, rain *may* be occurring.
   - If the rain sensor reports zero, rain *may still* be occurring (e.g., drizzle).

2. **Environmental data cannot detect rain**
   - Temperature, humidity, dew point, wind, and vapor pressure deficit (VPD) describe *conditions*, not precipitation.
   - These values are useful only as **context** or **sanity checks**.

3. **Alerts should reflect state transitions, not continuous conditions**
   - Alerts exist to influence behavior.
   - State latches and hysteresis are preferred over raw thresholds.

---

## High-Level Concepts

The app computes two independent scores:

| Score | What it Represents | What it Does *Not* Do |
|------|-------------------|------------------------|
| **Probability** | Whether the environment is trending from dry → wet | Detect rain |
| **Confidence** | Whether conditions support rain *if the sensor reports rain* | Detect rain |

Rain detection itself is handled **exclusively** by the rain sensor.

---

## Rain Detection & Confirmation Logic

### Rain States

The app tracks three mutually exclusive rain-related states:

- **No rain**
- **Rain confirmed**
- **Rain sensor false positive**

State transitions — not raw values — drive alerts.

---

### Alerts

| Condition | Alert |
|----------|-------|
| Rain sensor > 0 AND confidence ≥ threshold | 🌧️ Rain confirmed |
| Rain sensor > 0 AND confidence < threshold | ⚠️ Rain sensor false positive |
| Rain confirmed → confidence drops below threshold | ⚠️ Rain confidence lost |
| Rain confirmed → rain sensor returns to zero | ☀️ Rain has stopped |

---

### Key Assumptions

- The rain sensor is *usually* correct but can glitch.
- Environmental conditions can **invalidate** a rain reading but cannot replace it.
- Confidence decay is intentionally **disabled** to keep semantics clean and predictable.

---

## Physical Foundations

Environmental scoring is built on established meteorological relationships.

### Saturation Vapor Pressure

Saturation vapor pressure (`eₛ`) represents the maximum water vapor pressure air can hold at a given temperature.

The app uses the Magnus–Tetens approximation:

eₛ(T) = 0.6108 × exp( (17.27 × T) / (T + 237.3) )

Where:
- `T` = air temperature (°C)
- `eₛ` = kPa

---

### Actual Vapor Pressure

e = (RH / 100) × eₛ

---

### Vapor Pressure Deficit (VPD)

VPD = eₛ − e

Interpretation:
- **Low VPD** → air near saturation → surfaces stay wet
- **High VPD** → rapid evaporation and drying

VPD is a better drying metric than relative humidity alone.

---

### Dew Point

Using the Magnus formula:

α = ln(RH / 100) + (17.67 × T) / (243.5 + T)

Td = (243.5 × α) / (17.67 − α)

The key derived metric:

ΔT = T − Td

Smaller `ΔT` indicates conditions closer to condensation and precipitation.

---

## Confidence Score (Rain Plausibility)

### Purpose

Confidence answers:

> **“If the rain sensor says it’s raining, does that make physical sense?”**

It is a **sanity check**, not a detector.

---

### Inputs

- Relative Humidity
- Dew point proximity
- Vapor Pressure Deficit
- Wind speed

Confidence is **ignored** unless the rain sensor reports rain.

---

### Normalized Components

#### Relative Humidity

sRH = clamp( (RH − rhConfMin) / rhConfSpan , 0, 1 )

Defaults:
- `rhConfMin = 60%`
- `rhConfSpan = 30%`

Below ~60% RH, rain is implausible. Above ~90%, additional RH adds little value.

---

#### Dew Point Proximity

sDew =
1.0  if ΔT ≤ dewNear
0.0  if ΔT ≥ dewFar
linear interpolation otherwise

Defaults:
- `dewNear = 2°C`
- `dewFar = 5°C`

---

#### Vapor Pressure Deficit

sVPD =
1.0  if VPD ≤ vpdWet
0.0  if VPD ≥ vpdDry
linear interpolation otherwise

Defaults:
- `vpdWet = 0.30 kPa`
- `vpdDry = 1.00 kPa`

---

#### Wind Speed

sWind = clamp( wind / 2.0 , 0.8 , 1.0 )

This is a weak modifier intended only to reflect rain survivability.

---

### Weighted Score

Confidence =
100 × (
0.45 × sRH +
0.35 × sDew +
0.15 × sVPD +
0.05 × sWind
)

No decay is applied. Confidence always reflects *current* conditions.

---

## Probability Score (Environmental Trend)

### Purpose

Probability answers:

> **“Is the environment becoming wetter?”**

It is **not** a precipitation forecast.

---

### Inputs

- Absolute Relative Humidity
- RH trend
- VPD trend
- Wind trend

---

### Absolute Humidity Term

sRHabs = clamp( (RH − rhProbMin) / rhProbSpan , 0, 1 )

Defaults:
- `rhProbMin = 70%`
- `rhProbSpan = 25%`

---

### Trend Terms (per sample)

ΔRH   = RH − RH_prev
ΔVPD  = VPD_prev − VPD
ΔWind = Wind − Wind_prev

Normalized by:

sRHtrend   = clamp( ΔRH   / rhTrendMax , 0, 1 )
sVPDtrend  = clamp( ΔVPD  / vpdTrendMax , 0, 1 )
sWindtrend = clamp( ΔWind / windTrendMax , 0, 1 )

Defaults assume ~5-minute sampling:
- `rhTrendMax = 3%`
- `vpdTrendMax = 0.15 kPa`
- `windTrendMax = 2 m/s`

---

### Weighted Score

Probability =
100 × (
0.40 × sRHabs +
0.30 × sRHtrend +
0.20 × sVPDtrend +
0.10 × sWindtrend
)

---

### Alerts

| Condition | Alert |
|----------|-------|
| Probability crosses upper threshold | 💦 Environment is wetter, rain likely |
| Probability drops below lower threshold | 🌵 Environment is drier |

Hysteresis prevents flapping.

---

## Seasonal Intelligence

Temperature-based scaling is applied **after** raw score calculation.

### Temperature Breakpoints

| Setting | Default |
|-------|---------|
| `tempCool` | 10°C |
| `tempHot` | 35°C |

### Scaling

| Condition | Probability | Confidence |
|----------|------------|------------|
| Cool | Boost | Boost |
| Hot | Dampen | Dampen |

Linear interpolation is used between breakpoints.

Rationale:
- Cold drizzle persists under marginal conditions
- Hot rain requires stronger signals and dries faster

---

## Configuration Tuning Guidance

### Safe to Adjust
- Alert thresholds
- Seasonal multipliers
- RH spans

### Adjust With Care
- Dew point and VPD thresholds
- Trend normalization maxima

### Avoid Adjusting
- Core equations
- Score weight ordering
- Removing latches or hysteresis

---

## Known Limitations & Gotchas

### Drizzle Detection
- Very light drizzle may not register on the rain sensor.
- Environmental conditions alone cannot confirm drizzle.
- The app intentionally avoids “drizzle guessing” to prevent stuck states.

### Long Wet Periods
- Probability may remain elevated for days.
- This is expected behavior.

### Sensor Resolution
- Rain sensors quantize small amounts (e.g., 0.1 mm).
- This can appear binary at low rainfall rates.

---

## Debug Logging

The app logs:
- Raw sensor values and deltas
- Base and temperature-adjusted scores
- Component contributions to probability and confidence

This is intentional and designed for long-term tuning.

---

## Future Improvements (Ideas)

Not commitments:

- Optional confidence decay (reintroduced carefully)
- Separate drizzle advisory alerts
- Per-season threshold overrides
- Visual trend graphs
- Climate-adaptive tuning

---

## Guiding Principle Going Forward

> **If an alert doesn’t change what I would do, it doesn’t belong.**

All future changes should be evaluated against that standard.

---

## License

Apache License 2.0  
Copyright © 2026 Michael Pierce