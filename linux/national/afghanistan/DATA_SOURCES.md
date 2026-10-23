# Afghanistan Data Sources and Full-Data Build

This document records the source landscape discovered for Afghanistan and grades each
family by how much continuous, machine-readable, per-year data it yields for the archive
(1965-2026). Resources are marked **BOUNTIFUL** (deep structured time series suitable for
the automated builder), **PARTIAL** (valuable but discontinuous / document-based), or
omitted (not usable for continuous series and ignored for now).

## Sources

### BOUNTIFUL — used by the automated builder

- **FAOSTAT — Crops and Livestock Products (QCL).** Free access to food and agriculture
  statistics for 245+ countries; Afghanistan coverage runs from **1961** to the most recent
  year. Supplies item-level production, area harvested, yield, and livestock stock series.
  Primary agricultural backbone of this archive.
  - Portal: https://www.fao.org/faostat/en/
  - Bulk: https://bulks-faostat.fao.org/production/
- **World Bank — World Development Indicators (WDI).** Macroeconomic and structural series
  for Afghanistan (`AFG`): GDP, GDP growth, agriculture value added, agricultural/arable
  land, agricultural employment. Dedicated country data portal.
  - Country data: https://data.worldbank.org/country/afghanistan
  - API: https://api.worldbank.org/v2/country/AFG/indicator/{indicator}?format=json
- **FAO AQUASTAT.** Core database of water resources and water use (180+ variables),
  including freshwater withdrawal. Source for the WATER domain.
  - Databases: https://www.fao.org/aquastat/en/databases/
- **Our World in Data.** Reproducible, machine-readable CSV layer over FAO/World Bank
  series (crop production, yields, freshwater withdrawals). Used as the builder's
  extraction layer where it preserves a compatible Afghanistan series.
  - Agricultural production: https://ourworldindata.org/agricultural-production

### PARTIAL — valuable, recorded, not relied on for continuous series

- **NSIA — National Statistics and Information Authority.** Publishes Afghanistan
  Statistical Yearbooks (e.g. 2019 edition). Preferred Afghan primary source; strongest
  for recent years. To be used for table-level supplementation where a compatible series
  is transcribed.
- **MAIL — Ministry of Agriculture, Irrigation and Livestock.** Principal source of Afghan
  crop estimates; disseminated through NSIA yearbooks. Reliable crop area/production series
  broadly from 2008 onward.
- **FEWS NET.** Province-level agricultural data and documentation of the NSIA/MAIL vs.
  FAOSTAT relationship. Useful for provenance and reconciliation, not a continuous
  national series.
- **World Bank Afghanistan Economic Monitor / Development Update; ADB Asian Development
  Outlook; UNDP socio-economic reviews.** Periodic report figures (e.g. agriculture share
  of GDP, sector growth) — narrative/tabular, cited where relevant, not machine series.

### Ignored for now

Single-study PDFs, preprints, and narrative-only reports without an extractable per-year
national series. These may be revisited if a specific historical series needs verification.

## Automated Markdown coverage

`tools/afghanistan/build_afghanistan_annual_data.py` generates ten domain documents for
every year 1965-2026:

`ECONOMY`, `AGRICULTURE`, `FARMING_STATS`, `PRODUCE`, `GRAINS`, `MEAT`, `LIVESTOCK`,
`VEGETABLES`, `FRUIT`, `WATER`.

The builder fetches data at build time, preserves source units, performs no interpolation,
and writes `N/A` where a source has no observation. The 2026 records are explicitly
live/incomplete unless a final annual source observation exists.

## Validation rules

1. A numeric value must have an identified source family.
2. Units must remain source-compatible.
3. Country geography must be Afghanistan.
4. Missing values remain `N/A`; they are not estimated from adjacent years.
5. Revisions supersede older observations only when the source identifies the revised series.
6. NSIA/MAIL table-level observations should replace or supplement international series when
   an exact Afghan primary series is available and compatible.
7. Derived values must identify the underlying observations and formula.
