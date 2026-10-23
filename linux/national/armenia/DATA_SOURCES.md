# Armenia Data Sources and Full-Data Build

Resources are graded **BOUNTIFUL** (deep structured per-year series used by the automated
builder), **PARTIAL** (valuable but discontinuous / document-based), or omitted.

## Sources

### BOUNTIFUL — used by the automated builder

- **FAOSTAT — Crops and Livestock Products (QCL).** Food and agriculture statistics for
  245+ countries; coverage from **1961**. Production, area harvested, yield, livestock stocks.
  - https://www.fao.org/faostat/en/ · bulk: https://bulks-faostat.fao.org/production/
- **World Bank — World Development Indicators (WDI).** Macro/structural series for Armenia
  (`ARM`): GDP, GDP growth, agriculture value added, agricultural/arable land, employment.
  - https://data.worldbank.org · API: https://api.worldbank.org/v2/country/ARM/indicator/{code}
- **FAO AQUASTAT.** Water resources / freshwater withdrawal. WATER domain.
  - https://www.fao.org/aquastat/en/databases/
- **Our World in Data.** Reproducible CSV layer over FAO/WB series.
  - https://ourworldindata.org/agricultural-production

### PARTIAL — valuable, recorded, not relied on for continuous series

- **ARMSTAT (Statistical Committee of Armenia).** National statistical office; preferred for table-level supplementation
  where a compatible series is transcribed. https://armstat.am/

### Ignored for now

Single studies, preprints, and narrative-only reports without an extractable per-year series.

## Automated Markdown coverage

`tools/arm` … see `tools/` builder for this country. It generates ten domain
documents for every year 1965-2026: `ECONOMY`, `AGRICULTURE`, `FARMING_STATS`, `PRODUCE`,
`GRAINS`, `MEAT`, `LIVESTOCK`, `VEGETABLES`, `FRUIT`, `WATER`. The builder fetches at build
time, preserves units, performs no interpolation, and writes `N/A` on gaps. 2026 is
live/incomplete.

## Validation rules

1. A numeric value must have an identified source family.
2. Units must remain source-compatible.
3. Geography must be Armenia.
4. Missing values remain `N/A`; not estimated from adjacent years.
5. Revisions supersede older observations only when the source identifies the revised series.
6. National-office observations replace/supplement international series when a compatible
   primary series is available.
7. Derived values must identify the underlying observations and formula.
