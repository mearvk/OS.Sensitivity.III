# Belize Historical Economic & Agricultural Archive

Source-aware annual archive for Belize, 1965-2026. Mirrors the model used in `../afghanistan`,
`../korea`, `../china`, keeping Belize data distinct.

## Architecture

Each year contains `META.md`, `INDEX.md`, and ten domain documents: `ECONOMY.md`,
`AGRICULTURE.md`, `FARMING_STATS.md`, `PRODUCE.md`, `GRAINS.md`, `MEAT.md`, `LIVESTOCK.md`,
`VEGETABLES.md`, `FRUIT.md`, `WATER.md`.

## Data sources

See `DATA_SOURCES.md`. Bountiful machine-readable families: FAOSTAT (1961+), World Bank WDI,
FAO AQUASTAT, Our World in Data. Partial national family: Statistical Institute of Belize (SIB).

## Full-data build

`tools/belize/build_belize_annual_data.py` fetches source data at build time and writes the
ten domain records for every year 1965-2026. No interpolation; gaps are `N/A`; 2026 is
live/incomplete. Run by `.github/workflows/build-belize-annual-data.yml` on change and monthly.

## Build locally

```bash
python3 tools/belize/build_belize_annual_data.py
```
