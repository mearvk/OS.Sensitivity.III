# Korea Data Sources and Full-Data Build

## Source hierarchy

### KOSIS / Statistics Korea
KOSIS provides long historical national indicators and statistical tables. The current public indicators confirm, for example, nominal GDP coverage from 1953 through 2025 and per-capita rice consumption coverage from 1963 through 2025. The food-crop production indicator currently exposes 1998-2024/2026-era coverage depending on the table/version. These ranges demonstrate why coverage must be tracked per series rather than assumed globally.

### MAFRA
The Ministry of Agriculture, Food and Rural Affairs publishes annual agricultural and food statistical yearbooks. MAFRA's statistical service documents the yearbook as a long-running publication beginning in 1952; historical volumes contain agriculture, forestry, livestock, food, production, price-index, and rural-household statistics. The current publication system exposes yearbooks and major-statistics volumes through recent years.

### KAMIS
KAMIS provides agricultural, livestock, and fishery market-price information with annual, monthly, daily, wholesale, retail, and historical views. Its public annual pages expose current and historical commodity price observations. Price units, grade, market type, and product definition must be retained when transcribing.

### Bank of Korea
The Bank of Korea supplies national-account observations and methodology. KOSIS identifies the BOK national-accounts table as the source for the annual nominal GDP series and notes that 2025 figures may be provisional depending on the release.

### FAOSTAT / AQUASTAT
The automated annual builder uses compatible FAOSTAT Crops and livestock products data for item-level agricultural/livestock series and compatible AQUASTAT-derived freshwater-withdrawal data where available. These international series are not treated as superior to Korean primary tables; they provide a reproducible machine-readable baseline and make gaps explicit.

### World Bank WDI / Our World in Data
The builder uses World Bank WDI for GDP, GDP growth, and agriculture value-added indicators and compatible Our World in Data series for selected crop, livestock, and water observations. These are machine-readable extraction layers, not replacements for primary Korean source tables.

## Current source-backed anchors

- KOSIS reports nominal GDP in KRW with an annual series beginning in 1953 and extending through 2025 on its current GDP indicator.
- KOSIS reports per-capita rice consumption for 1963-2025 and identifies the source as the Grain Consumption Survey.
- KOSIS currently reports food-crop production series beginning in 1998 and identifies the Crop Production Survey as the source.
- KOSIS currently reports 2025 per-capita rice consumption at 53.9 kg.
- KOSIS currently reports 2025 cultivated area at 1,499,911 hectares and 2024 food-crop production at 4,190,030 tonnes.
- KAMIS currently exposes annual retail and wholesale price series and supports historical year selection.
- MAFRA's publication system exposes agricultural and food statistical yearbooks through 2025, while older yearbooks establish deeper historical coverage.

## Automated Markdown coverage

`tools/korea/build_korea_annual_data.py` generates ten domain documents for every year 1965-2026:

`ECONOMY`, `AGRICULTURE`, `FARMING_STATS`, `PRODUCE`, `GRAINS`, `MEAT`, `LIVESTOCK`, `VEGETABLES`, `FRUIT`, `WATER`.

The builder fetches data at build time, preserves source units, performs no interpolation, and writes `N/A` where a source has no observation. The 2026 records are explicitly live/incomplete unless a final annual source observation exists.

## Validation rules

1. A numeric value must have an identified source family.
2. Units must remain source-compatible.
3. Country geography must be Republic of Korea / South Korea.
4. Missing values remain `N/A`; they are not estimated from adjacent years.
5. Revisions supersede older observations only when the source identifies the revised series.
6. KOSIS/MAFRA/KAMIS/BOK table-level observations should replace or supplement international series when an exact Korean primary series is available.
7. Derived values must identify the underlying observations and formula.

## Web research anchors

- KOSIS GDP: https://kosis.kr/visual/nsportalStats/detailContents.do?listId=B&statJipyoId=3654&vStatJipyoId=5181
- KOSIS per-capita rice consumption: https://kosis.kr/visual/nsportalStats/detailContents.do?listId=N&statJipyoId=3720&vStatJipyoId=4851
- KOSIS food-crop production: https://kosis.kr/visual/nsportalStats/detailContents.do?listId=N&statJipyoId=3724&vStatJipyoId=4867
- MAFRA statistical publications: https://kass.mafra.go.kr/newkass/kas/sti/pbl/kasPblictn.do
- KAMIS annual retail prices: https://www.kamis.or.kr/customer/price/agricultureRetail/period.do?action=yearly
