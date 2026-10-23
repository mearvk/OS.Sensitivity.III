# Korea Historical Economic & Agricultural Archive

This directory is a source-aware annual archive for the Republic of Korea, organized from 1965 through 2026. It mirrors the structured annual research model used elsewhere in this repository while keeping Korean data distinct from other country archives.

## Architecture

Each year contains `META.md`, `INDEX.md`, and ten generated data-domain documents: `ECONOMY.md`, `AGRICULTURE.md`, `FARMING_STATS.md`, `PRODUCE.md`, `GRAINS.md`, `MEAT.md`, `LIVESTOCK.md`, `VEGETABLES.md`, `FRUIT.md`, and `WATER.md`. The generator preserves the existing annual metadata/index files and replaces domain placeholders with source observations on each build.

## Data sources

- **KOSIS / Statistics Korea:** national statistical tables, agriculture and forestry, prices, economy, national accounts, population, and related domains.
- **MAFRA:** agricultural and food statistical yearbooks and administrative statistics. MAFRA states that its statistical yearbook has been published annually since 1952 and contains agricultural, forestry, livestock, food, production, price, and rural-household statistics.
- **KAMIS:** agricultural, livestock, and fishery market-price information, including annual and historical retail series.
- **Bank of Korea:** national accounts and macroeconomic statistics.
- **FAOSTAT / FAO AQUASTAT:** compatible international crop, livestock, and water series used to make the annual archive reproducible without inventing missing observations.
- **World Bank WDI / Our World in Data:** machine-readable macro and selected agricultural/water series used by the automated builder when they preserve a compatible Korea series.

## Full-data build

`tools/korea/build_korea_annual_data.py` fetches source data at build time and writes the ten domain Markdown records for every year 1965-2026. It does **not** interpolate missing years. Missing observations are written as `N/A`; 2026 is treated as a live/incomplete year unless a source provides a final annual observation.

The builder is run automatically by `.github/workflows/build-korea-annual-data.yml` on relevant changes and on a monthly schedule. This makes the Markdown archive refreshable rather than a one-time transcription.

## Data posture

Source observations remain distinguishable from derived model outputs. Korean primary sources are preferred for future table-level supplementation, especially KOSIS, MAFRA, KAMIS, and Bank of Korea. International machine-readable series are used where their geography, units, item definitions, and temporal coverage are compatible. No historical value is manufactured solely to make a table continuous.

## Document contract

Every annual document points to that year's `META.md`. `META.md` is controlling metadata for the annual set. `INDEX.md` is the annual navigation map. The domain files contain source observations and source notes. Analytical scores or models must be explicitly labeled as derived values and must retain the underlying observations.

## Build locally

```bash
python3 tools/korea/build_korea_annual_data.py
```

The existing C/C++ evaluation module remains at the top level of `korea/`. The annual archive is documentary data, while `data.json`, `congruences.xml`, and `results.xml` remain machine-readable model inputs/outputs.
