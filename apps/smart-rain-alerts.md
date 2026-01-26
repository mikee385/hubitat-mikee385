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

| Score           | What it Represents                                           | What it Does *Not* Do |
| --------------- | ------------------------------------------------------------ | --------------------- |
| **Probability** | Whether the environment is trending from dry → wet           | Detect rain           |
| **Confidence**  | Whether conditions support rain *if the sensor reports rain* | Detect rain           |

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

| Condition                                         | Alert                         |
| ------------------------------------------------- | ----------------------------- |
| Rain sensor > 0 AND confidence ≥ threshold        | 🌧️ Rain confirmed            |
| Rain sensor > 0 AND confidence < threshold        | ⚠️ Rain sensor false positive |
| Rain confirmed → confidence drops below threshold | ⚠️ Rain confidence lost       |
| Rain confirmed → rain sensor returns to zero      | ☀️ Rain has stopped           |

---

### Key Assumptions

- The rain sensor is *usually* correct but can glitch.
- Environmental conditions can **invalidate** a rain reading but cannot replace it.
- Confidence decay is intentionally **disabled** to keep semantics clean and predictable.

---

## Physical Foundations

Environmental scoring is built on established meteorological relationships. Constants and formulations are chosen for internal consistency rather than theoretical purity.

---

### Saturation Vapor Pressure

Saturation vapor pressure represents the maximum water vapor pressure air can hold at a given temperature. The app uses the Magnus–Tetens approximation:

$$
e_s(T) = 0.6108 \cdot \exp\left(\frac{17.27 \cdot T}{T + 237.3}\right)
$$

Where:

- $T$ is air temperature in °C
- $e_s$ is saturation vapor pressure in kPa

---

### Actual Vapor Pressure

$$
e = \frac{RH}{100} \cdot e_s
$$

Where:

- $RH$ is relative humidity in percent

---

### Vapor Pressure Deficit (VPD)

$$
VPD = e_s - e
$$

Interpretation:

- **Low VPD** → air near saturation → surfaces remain wet
- **High VPD** → rapid evaporation and drying

VPD is a more reliable drying metric than relative humidity alone.

---

### Dew Point

Using the Magnus formula:

$$
\alpha = \ln\left(\frac{RH}{100}\right) + \frac{17.27 \cdot T}{237.3 + T}
$$

$$
T_d = \frac{237.3 \cdot \alpha}{17.27 - \alpha}
$$

The key derived metric used by the app is the dew point depression:

$$
\Delta T = T - T_d
$$

Smaller values of $\Delta T$ indicate conditions closer to condensation and precipitation.

---

## Confidence Score (Rain Plausibility)

### Purpose

Confidence answers:

> **“If the rain sensor says it’s raining, does that make physical sense?”**

It is a **sanity check**, not a precipitation detector.

Confidence is ignored unless the rain sensor reports rain.

---

### Inputs

- Relative humidity
- Dew point proximity
- Vapor pressure deficit
- Wind speed

Each component is normalized to a 0–1 range, then combined and scaled to a 0–100 score.

---

### Normalized Components

#### Relative Humidity

Relative humidity contributes strongly up to near-saturation:

$$
s_{RH} = \mathrm{clamp}\left(\frac{RH - RH_{min}}{RH_{span}}, 0, 1\right)
$$

Default values:

- $RH_{min} = 60\%$
- $RH_{span} = 30\%$

Below ~60% RH, rain is implausible. Above ~90%, additional RH adds little information.

---

#### Dew Point Proximity

Dew point proximity reflects how close the air is to condensation:

- $s_{Dew} = 1.0$ when $\Delta T \le 2^\circ C$
- $s_{Dew} = 0.0$ when $\Delta T \ge 5^\circ C$
- Linear interpolation between these bounds

---

#### Vapor Pressure Deficit

VPD contributes inversely:

- $s_{VPD} = 1.0$ when $VPD \le 0.30\ \text{kPa}$
- $s_{VPD} = 0.0$ when $VPD \ge 1.00\ \text{kPa}$
- Linear interpolation otherwise

---

#### Wind Speed

Wind is a weak modifier reflecting rain survivability:

$$
s_{Wind} = \mathrm{clamp}\left(\frac{Wind_{m/s}}{2.0}, 0.8, 1.0\right)
$$

Higher wind slightly improves plausibility by reducing evaporation near surfaces.

---

### Weighted Confidence Score

$$
Confidence =
100 \cdot \left(
0.45 \cdot s_{RH} +
0.35 \cdot s_{Dew} +
0.15 \cdot s_{VPD} +
0.05 \cdot s_{Wind}
\right)
$$

No decay is applied. Confidence always reflects **current environmental conditions**.

---

## Probability Score (Environmental Trend)

### Purpose

Probability answers:

> **“Is the environment becoming wetter?”**

It is **not** a precipitation forecast and is not expected to predict all rain events.

---

### Inputs

- Baseline relative humidity level
- Relative humidity trend
- Vapor pressure deficit trend
- Wind trend

---

### Baseline Relative Humidity

$$
s_{RH,abs} = \mathrm{clamp}\left(\frac{RH - RH_{min}}{RH_{span}}, 0, 1\right)
$$

Defaults:

- $RH_{min} = 70\%$
- $RH_{span} = 25\%$

---

### Trend Terms (per sample)

$$
\Delta RH = RH - RH_{prev}
$$

$$
\Delta VPD = VPD_{prev} - VPD
$$

$$
\Delta Wind = Wind - Wind_{prev}
$$

Normalized trend components:

$$
s_{RH,trend} = \mathrm{clamp}\left(\frac{\Delta RH}{RH_{max}}, 0, 1\right)
$$

$$
s_{VPD,trend} = \mathrm{clamp}\left(\frac{\Delta VPD}{VPD_{max}}, 0, 1\right)
$$

$$
s_{Wind,trend} = \mathrm{clamp}\left(\frac{\Delta Wind}{Wind_{max}}, 0, 1\right)
$$

Defaults assume ~5-minute sampling:

- $RH_{max} = 3\%$
- $VPD_{max} = 0.15\ \text{kPa}$
- $Wind_{max} = 2\ \text{m/s}$

---

### Weighted Probability Score

$$
Probability =
100 \cdot \left(
0.40 \cdot s_{RH,abs} +
0.30 \cdot s_{RH,trend} +
0.20 \cdot s_{VPD,trend} +
0.10 \cdot s_{Wind,trend}
\right)
$$

---

### Probability Alerts

| Condition                               | Alert                                 |
| --------------------------------------- | ------------------------------------- |
| Probability crosses upper threshold     | 💦 Environment is wetter, rain likely |
| Probability drops below lower threshold | 🌵 Environment is drier               |

Hysteresis prevents alert flapping.

---

## Seasonal Intelligence

Temperature-based scaling is applied **after** raw score calculation.

### Temperature Breakpoints

| Setting        | Default |
| -------------- | ------- |
| Cool threshold | 10 °C   |
| Hot threshold  | 35 °C   |

### Scaling Behavior

| Condition | Probability | Confidence |
| --------- | ----------- | ---------- |
| Cool      | Boosted     | Boosted    |
| Hot       | Dampened    | Dampened   |

Linear interpolation is used between thresholds.

Rationale:

- Cold drizzle can persist under marginal conditions
- Hot rain requires stronger signals and dries faster

---

## Configuration Tuning Guidance

### Safe to Adjust

- Alert thresholds
- Seasonal multipliers
- Relative humidity spans

### Adjust With Care

- Dew point and VPD thresholds
- Trend normalization maxima

### Avoid Adjusting

- Core physical equations
- Score weight ordering
- Removal of latches or hysteresis

---

## Known Limitations & Gotchas

### Drizzle Detection

- Very light drizzle may not register on the rain sensor.
- Environmental conditions alone cannot confirm drizzle.
- The app intentionally avoids “drizzle guessing” to prevent stuck states.

### Long Wet Periods

- Probability may remain elevated for days.
- This behavior is expected and not a bug.

### Sensor Resolution

- Rain sensors quantize small amounts (e.g., 0.1 mm).
- This can appear binary at low rainfall rates.

---

## Debug Logging

The app logs:

- Raw sensor values and deltas
- Base and temperature-adjusted scores
- Individual component contributions

This verbosity is intentional and supports long-term tuning.

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