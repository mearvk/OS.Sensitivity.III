# Afghanistan Historical Economic & Agricultural Archive

This directory is a source-aware annual archive for Afghanistan, organized from 1965
through 2026. It mirrors the structured annual research model used elsewhere in this
repository (see `../korea`, `../china`) while keeping Afghan data distinct from other
country archives.

## Architecture

Each year contains `META.md`, `INDEX.md`, and ten generated data-domain documents:
`ECONOMY.md`, `AGRICULTURE.md`, `FARMING_STATS.md`, `PRODUCE.md`, `GRAINS.md`, `MEAT.md`,
`LIVESTOCK.md`, `VEGETABLES.md`, `FRUIT.md`, and `WATER.md`. The generator preserves the
annual metadata/index files and replaces domain placeholders with source observations on
each build.

## Data sources

See `DATA_SOURCES.md` for the full graded source landscape. In brief, the **bountiful**
machine-readable families are FAOSTAT (agriculture, 1961+), World Bank WDI (macro/land),
FAO AQUASTAT (water), and Our World in Data (reproducible extraction layer). The
**partial** Afghan primary families are NSIA statistical yearbooks and MAIL crop
estimates, preferred for table-level supplementation where a compatible series exists.

## Full-data build

`tools/afghanistan/build_afghanistan_annual_data.py` fetches source data at build time and
writes the ten domain Markdown records for every year 1965-2026. It does **not**
interpolate missing years. Missing observations are written as `N/A`; 2026 is treated as a
live/incomplete year unless a source provides a final annual observation.

The builder is run by `.github/workflows/build-afghanistan-annual-data.yml` on relevant
changes and on a monthly schedule, so the Markdown archive is refreshable rather than a
one-time transcription. The builder must run in an environment with outbound access to the
source APIs; in a restricted environment it safely resolves every value to `N/A` rather
than fabricating data.

## Data posture

Source observations remain distinguishable from derived model outputs. Afghan primary
sources are preferred for future table-level supplementation. International machine-readable
series are used where their geography, units, item definitions, and temporal coverage are
compatible. No historical value is manufactured solely to make a table continuous —
Afghanistan's statistical record has real gaps from decades of disruption, and those gaps
are preserved as `N/A`.

## Build locally

```bash
python3 tools/afghanistan/build_afghanistan_annual_data.py
```
