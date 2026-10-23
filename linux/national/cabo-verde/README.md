# Cabo Verde Historical Economic & Agricultural Archive

Source-aware annual archive for Cabo Verde, 1965-2026. Mirrors the model used in `../afghanistan`,
`../korea`, `../china`, keeping Cabo Verde data distinct.

## Architecture

Each year contains `META.md`, `INDEX.md`, and ten domain documents: `ECONOMY.md`,
`AGRICULTURE.md`, `FARMING_STATS.md`, `PRODUCE.md`, `GRAINS.md`, `MEAT.md`, `LIVESTOCK.md`,
`VEGETABLES.md`, `FRUIT.md`, `WATER.md`.

## Data sources

See `DATA_SOURCES.md`. Bountiful machine-readable families: FAOSTAT (1961+), World Bank WDI,
FAO AQUASTAT, Our World in Data. Partial national family: INE Cabo Verde (Instituto Nacional de Estatistica).

## Full-data build

`tools/cabo-verde/build_cabo-verde_annual_data.py` fetches source data at build time and writes the
ten domain records for every year 1965-2026. No interpolation; gaps are `N/A`; 2026 is
live/incomplete. Run by `.github/workflows/build-cabo-verde-annual-data.yml` on change and monthly.

## Build locally

```bash
python3 tools/cabo-verde/build_cabo-verde_annual_data.py
```
