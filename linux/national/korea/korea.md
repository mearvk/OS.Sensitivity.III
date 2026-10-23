# Korea Economic & Supply Evaluation

**Copyright (c) 2026 Max Rupplin - MEARVK LLC 2026.**

> **Model status:** source-aware research prototype. Facts and assumptions are deliberately separated.

## Executive view

| Measure | Observation | Treatment |
|---|---:|---|
| Korea nominal GDP, 2025 | $1.872T | factual |
| Hanwoo 1st-grade rib, 2022 | ₩7,850 / 100g | factual historical retail observation |
| Hanwoo 1st-grade rib, 2021 | ₩6,857 / 100g | factual historical retail observation |
| Hanwoo 1st-grade sirloin, 2026 snapshot | ₩10,137 / 100g | current KAMIS/MFRA observation |
| Hanwoo herd | 337 → 321 → 315k?* | factual/projection series |
| U.S. adult male mean height | 68.9 in / ~175.0 cm | factual population reference |
| U.S. adult female mean height | 63.5 in / ~161.3 cm | factual population reference |
| Oldest active football record | 74 years 125 days | factual Guinness record |
| Lifetime water baseline | 1,000,000 L | explicit model assumption |

## Purpose and method

This project combines public economic observations with clearly labeled assumptions. It is intended to produce readable, reproducible evaluations rather than moral rankings of people, countries, or consumers.

## Hanwoo price and quality

KAMIS reports 1st-grade Hanwoo rib at ₩6,857/100g in 2021 and ₩7,850/100g in 2022 in its historical annual retail series. Current KAMIS/MFRA material reports a 2026 1st-grade sirloin retail observation of ₩10,137/100g. These are **not treated as one uninterrupted product series** because the cut and reporting periods differ.

The project uses an **ordinal quality score** for computation rather than claiming an official numeric quality index:

| Grade | Model score |
|---|---:|
| 1+ | 100 |
| 1 | 90 |
| 2 | 75 |
| 3 | 60 |

This score is a modeling convenience, not an official Korean quality measurement.

## Supply lines

The model separates cattle supply, feed, price, and consumer pressure so future datasets can be substituted without changing the evaluation structure. No missing feed or wealth observation is silently converted into a factual number.

## American stature

U.S. adult height is treated only as a population reference. It is not a quality, intelligence, or worth ranking.

## Long-life football reference

The oldest active football-player observation is retained as an age/record variable. No combined-wealth value is assigned without a reliable public source, and “lifetime giftedness” remains a model variable rather than a factual claim about a person's intelligence or worth.

## Water over a lifetime

The first model uses 1,000,000 liters as a neutral lifetime-use baseline. This is explicitly an assumption so later household, municipal, agricultural, or industrial datasets can replace it.

## Consumer viewpoint

The computation uses **consumer price pressure** rather than “American greed” as a computed category. Price burden can be evaluated from observations; a moral characterization of consumers cannot be inferred from those observations.

## Four-property congruence model

Each item receives four related line properties:

| Property | Meaning |
|---|---|
| Sign | Positive, negative, or neutral relationship |
| Tenor | Short, medium, long, annual, age, or record horizon |
| Length | Normalized relation magnitude from 0 to 1 |
| Relation | Price, supply, quality, burden, resilience, or use |

The machine-readable relations are in `congruences.xml`; evaluated output is in `results.xml`; source observations and assumptions are in `data.json`.

## Build

```sh
make
make check
make clean
```

## Attribution

**Max Rupplin - MEARVK LLC 2026.**

This project is a research/evaluation prototype. Factual observations should retain their source, date, unit, and provenance; assumptions should remain explicitly marked as assumptions.
