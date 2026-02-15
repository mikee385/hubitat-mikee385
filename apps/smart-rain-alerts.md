# Smart Rain Alerts

Smart Rain Alerts is a Hubitat app that provides **actionable rain-related alerts** using a personal weather station, while explicitly acknowledging the limitations of consumer-grade sensors.

Rather than attempting to “detect rain” from environmental data alone, the app separates:

- **Detection** (what is actually happening),
- **Confirmation** (whether a sensor reading makes physical sense),
- **Trends** (whether conditions suggest rain is becoming more or less likely).

This separation avoids overfitting, reduces alert noise, and keeps alerts aligned with real human decisions.

---

## Core Design Philosophy

1. **The rain sensor is the only true precipitation detector**

   - If the rain sensor reports rain, rain *may* be occurring.
   - If the rain sensor reports zero, rain *may still* be occurring (e.g., drizzle).

2. **Environmental data cannot detect rain**

   - Temperature, pressure, humidity, dew point, wind, solar radiation, and vapor pressure deficit (VPD) describe *conditions*, not precipitation.
   - These values are useful only as **context** or **sanity checks**.

3. **Alerts should reflect state transitions, not continuous conditions**

   - Alerts exist to influence behavior.
   - State latches and hysteresis are preferred over raw thresholds.

---

## High-Level Concepts

The app computes two independent scores:

| Score           | What it Represents                                           | What it Does *Not* Do |
| --------------- | ------------------------------------------------------------ | --------------------- |
| **Probability** | Whether atmospheric trends suggest that rain is likely       | Detect rain           |
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
| Rain sensor > 0 AND rain rate ≥ threshold         | 🌧️ Rain confirmed             |
| Rain sensor > 0 AND confidence ≥ threshold        | 🌧️ Rain confirmed             |
| Rain sensor > 0 AND confidence < threshold        | ⚠️ Rain sensor false positive |
| Rain confirmed → confidence drops below threshold | ⚠️ Rain confidence lost       |
| Rain confirmed → rain sensor returns to zero      | ⛅️ Rain has stopped           |

"Rain has stopped" reflects the *rain sensor state*, not a guarantee of clear skies.

---

### Rain Rate Override

A sufficiently high measured rain rate is treated as definitive evidence of rain, even if atmospheric confidence is still below the normal confirmation threshold.

This prevents false “sensor disagreement” alerts during fast-onset or convective rainfall, where environmental indicators may lag behind observed precipitation.

---

## Key Assumptions

- The rain sensor is *usually* correct but can glitch.
- Environmental conditions can **invalidate** a rain reading but cannot replace it.
- Confidence decay is intentionally **disabled** to keep semantics clean and predictable.

---

## Physical Foundations

Environmental scoring is built on established meteorological relationships. Constants and formulations are chosen for internal consistency rather than theoretical purity.

The definitions and normalized quantities in this section are reused by both the
Probability and Confidence scoring systems unless explicitly noted otherwise.

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

Interpretation:

- Actual vapor pressure represents the portion of saturation vapor pressure
  currently occupied by water vapor.
- It is an intermediate value used to compute VPD and dew point.

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
\alpha = \ln\left(\frac{RH}{100}\right) + \frac{17.67 \cdot T}{243.5 + T}
$$

$$
T_d = \frac{243.5 \cdot \alpha}{17.67 - \alpha}
$$

The key derived metric used by the app is the dew point depression:

$$
\Delta T = T - T_d
$$

Smaller values of $\Delta T$ indicate conditions closer to condensation and precipitation.

---

### Barometric Pressure

Barometric pressure represents the total weight of the atmosphere above the sensor.
Changes in pressure reflect large-scale vertical air motion and synoptic weather patterns.

The app does **not** use absolute pressure values. Instead, it relies exclusively on **pressure trends**, which are more reliable and location-independent indicators of changing weather conditions.

#### Pressure Trend

Pressure trend is defined as the change in pressure across the rolling 5-sample window:

$\Delta P = P_{oldest} - P_{newest}$ (over window)

Where:

- $P_{newest}$ is the barometric pressure from the most recent sample
- $P_{oldest}$ is the barometric pressure from the oldest sample in the window

Interpretation:

- $\Delta P > 0$ → pressure is falling
- $\Delta P < 0$ → pressure is rising

Pressure contribution begins at `pressureConfStart` and saturates at `pressureConfMax`. Falling pressure increases rain plausibility by signaling approaching weather systems.

Pressure trend represents **atmospheric instability**, not precipitation.

It is used by:

- **Probability**, as a trend signal
- **Confidence**, as a plausibility modifier

Pressure alone cannot detect or confirm rain.

---

### Solar Radiation

Solar radiation measures incoming shortwave energy from the sun, expressed in watts per square meter (W/m²).

It serves as a proxy for **cloud cover, atmospheric clearing, and convective suppression**, all of which influence rain plausibility.

Solar radiation is **never used to detect rain**.

Interpretation:

- Low solar radiation → thick cloud cover → rain more plausible
- High solar radiation → atmospheric clearing → rain less plausible

Solar influence transitions between `solarOvercast` (thick cloud cover) and `solarClear` (strong direct sunlight). Solar radiation provides **context**, not confirmation, and is used only as a plausibility modifier in the Confidence score.

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
- Barometric pressure trend
- Solar radiation

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

#### Barometric Pressure Trend

Barometric pressure contributes to confidence only when pressure is **falling at a meaningful rate**, reflecting increasing atmospheric instability:

- $s_{Pressure} = 0.0$ when $\Delta P \le 0.01\ \text{inHg}$
- $s_{Pressure} = 1.0$ when $\Delta P \ge 0.04\ \text{inHg}$
- Linear interpolation otherwise

Interpretation:

- Minor pressure noise contributes nothing
- Sustained falling pressure increases rain plausibility
- Strong pressure drops saturate the contribution

---

#### Solar Radiation

Solar radiation acts as a **negative plausibility modifier**, reflecting cloud cover and atmospheric clearing. It reduces confidence under strong sunlight but never contributes negative weight:

- $s_{Solar} = 1.0$ when $Solar \le 400\ \text{W/m}^2$
- $s_{Solar} = 0.0$ when $Solar \ge 800\ \text{W/m}^2$
- Linear interpolation otherwise

Interpretation:

- Overcast conditions strongly support rain plausibility
- Increasing sunlight reduces confidence
- Strong direct sun suppresses the contribution entirely

Solar radiation cannot invalidate rain by itself but meaningfully reduces confidence when observed conditions contradict sustained precipitation.

---

### Weighted Confidence Score

$$
Confidence =
100 \cdot \left(
0.38 \cdot s_{RH} +
0.30 \cdot s_{Dew} +
0.15 \cdot s_{VPD} +
0.05 \cdot s_{Wind} +
0.10 \cdot s_{Pressure} +
0.02 \cdot s_{Solar}
\right)
$$

No decay is applied. Confidence always reflects **current environmental conditions**.

---

## Probability Score (Environmental Trend)

### Purpose

Probability answers:

> **“Do atmospheric trends suggest that rain is likely?”**

Probability does **not** represent a transition from “dry” to “wet,” and it does not measure environmental wetness.

The environment can become wetter (e.g., rising humidity, lower VPD) without ever producing a high Probability score.  
Probability increases only when **specific short-term atmospheric trends** commonly associated with imminent rainfall are present.

However, it is **not** a precipitation forecast and is not expected to predict all rain events.

---

### Inputs

- Baseline relative humidity level
- Relative humidity trend
- Vapor pressure deficit trend
- Wind trend
- Barometric pressure trend

---

### Baseline Relative Humidity

$$
s_{RH,abs} = \mathrm{clamp}\left(\frac{RH - RH_{min}}{RH_{span}}, 0, 1\right)
$$

Defaults:

- $RH_{min} = 70\%$
- $RH_{span} = 25\%$

---

### Trend Terms (5-sample windowed delta)

Trend signals are computed using a rolling 5-sample window to reduce sensor noise and short-term oscillation.

For a history window:

$X_1$, $X_2$, $X_3$, $X_4$, $X_5$

the windowed delta is defined as:

$$
\Delta X = X_{5} − X_{1}
$$

This represents short-term directional movement rather than instantaneous per-sample change.

The app computes trend terms as follows (over window):

$$
\Delta RH = RH_{newest}- RH_{oldest}
$$

$$
\Delta VPD = VPD_{oldest} - VPD_{newest}
$$

$$
\Delta Wind = Wind_{newest} - Wind_{oldest}
$$

$$
\Delta Pressure = Pressure_{oldest} - Pressure_{newest}
$$

Interpretation:

- RH trend → humidity rising across the window
- VPD trend → air becoming more saturated
- Wind trend → strengthening surface mixing
- Pressure trend → falling pressure over time

Windowed deltas provide more stable atmospheric trend detection than
single-sample differences.

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

$$
s_{Pressure,trend} = \mathrm{clamp}\left(\frac{\Delta Pressure}{Pressure_{max}}, 0, 1\right)
$$

Defaults assume ~5-minute sampling:

- $RH_{max} = 3\%$
- $VPD_{max} = 0.15\ \text{kPa}$
- $Wind_{max} = 2\ \text{m/s}$
- $Pressure_{max} = 0.03\ \text{inHg}$

---

### Stale Sensor Data Handling

Trend histories depend on regular weather station updates.  
If the time between processed updates exceeds `staleThresholdMinutes` (default: 45 minutes), the app:

- Clears all trend history windows
- Resets previous-value tracking variables
- Resumes normal processing on the next update

This prevents outdated sensor values from producing misleading trend calculations after connectivity interruptions, station outages, or long gaps between updates.

If a weather update arrives with the same timestamp as the last processed update, it is ignored to avoid polluting trend histories with duplicate samples.

---

### Weighted Probability Score

$$
Probability =
100 \cdot \left(
0.35 \cdot s_{RH,abs} +
0.25 \cdot s_{RH,trend} +
0.20 \cdot s_{VPD,trend} +
0.10 \cdot s_{Wind,trend} +
0.10 \cdot s_{Pressure,trend}
\right)
$$

---

### Probability Alerts

Probability alerts are **edge-triggered only on rising conditions**.

| Condition                           | Alert                  |
| ----------------------------------- | ---------------------- |
| Probability crosses upper threshold | 🌦️ Rain may be starting |

Once Probability later falls below the lower threshold, **no alert is generated**.

Probability alerts are suppressed while rain is confirmed to avoid redundant or confusing notifications during an active rain event.

This reflects that Probability is a *pattern-detection signal*, not a weather state, and avoids misleading “all clear” messages. Hysteresis prevents alert flapping.

---

## Seasonal Intelligence

Temperature-based scaling is applied **after** raw score calculation.

### Temperature Breakpoints

| Setting        | Default         |
| -------------- | --------------- |
| Cool threshold | 10 °C / 50 °F   |
| Hot threshold  | 35 °C / 95 °F   |

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

- Probability may remain elevated for extended periods when atmospheric dynamics remain rain-favorable.
- This does **not** imply rain is ongoing or guaranteed.
- This behavior is expected and not a bug.

### Sensor Resolution

- Rain sensors quantize small amounts (e.g., 0.1 mm).
- This can appear binary at low rainfall rates.

### Pressure Lag

- Pressure changes are gradual and may lag precipitation onset.
- Pressure improves plausibility and trend detection but does not replace direct rain sensing.

### Solar Transients

- Brief sun breaks during light rain can temporarily reduce confidence.
- This behavior is intentional and reflects physical plausibility, not sensor error.

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