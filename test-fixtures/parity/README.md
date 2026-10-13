# Numeris report fixtures

Contract: desktop schema 11 / Android Room schema 1. Reference date: 2026-10-11, UTC.

`desktop-reference.csv` is produced by compiling the current desktop helpers and insight engine in an isolated console project. It contains sanitized input and exact expected output for threshold, midpoint, missing and identity cases. `DesktopReferenceTest` checks the same records on Android.

`schema11.sql` plus `portfolio.sql` is a shared SQLite input for the current, unmodified desktop PortfolioRepository and Android's schema-aware importer/ReportRepository. `portfolio-reference.csv` records all-sites and per-site page order, cross-provider totals, weighted position and complete daily coverage from that desktop source. DesktopPortfolioTest checks seven cases with a fixed clock. An absent desktop aggregate field uses zero; Android also checks that its separate presence value remains null.

`insight-reference.csv` records 18 scenarios and 21 exact decisions from the linked current desktop InsightEngine. DesktopInsightTest compares all eleven rules, threshold boundaries, missing comparisons, ordering, the four-card limit, full wording and structured destinations. `desktop-source-hashes.json` pins the 66 reference files; the final hash check found no reference drift.

`ReportMathTest` covers period completeness per expected site, zero/missing/new activity, weighted aggregates, indexing populations, historical uptime and target normalization. Android instrumentation uses isolated Room databases, two sites with 365 days, 1,000 queries and 1,000 pages. It includes populated Bing, HTTP statuses, country/page/referrer breakdowns, ten stored page queries (eight displayed), a PageSpeed audit, sparse/missing data and import rollback fixtures. It has no real credentials and makes no provider requests. Functional reachability is separate from performance-budget measurement.

The schema 11 import fixture is generated from the supported import mapping into an isolated database. It is a reader-contract fixture; it does not establish that the desktop app has an export feature.
