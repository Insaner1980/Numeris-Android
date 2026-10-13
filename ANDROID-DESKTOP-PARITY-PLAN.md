> Current decision, 2026-10-13: The user explicitly authorized complete GA4 removal, including historical data. Room migration 1 to 2 replaces the preservation proposal in section 10 and J14 below. Earlier inspection notes remain historical.

# Numeris Android: desktop parity and mobile redesign plan

**Prepared:** 2026-10-11

**Status:** Proposed implementation plan. No implementation is authorized by this document alone.

**Android repository:** `C:\Dev\Numeris_Android`

**Desktop reference:** `C:\Dev\Numeris`

**Goal:** Deliver the recognizable Numeris desktop product on Android, with equivalent reporting information, calculations, and user capabilities, presented through a native, comfortable mobile interface.

## 1. Scope, evidence, and completion contract

### 1.1 What “the same application” means

The Android application must preserve the desktop product's visual identity, information hierarchy, report semantics, provider separation, and actionable details. A user moving between platforms should recognize the same product and understand the same numbers without relearning their meaning.

The Android layout must adapt to touch, narrow windows, large text, intermittent connectivity, rotation, process recreation, and Android Back. A nine-column desktop table becomes a structured mobile list with accessible details; a desktop side panel becomes a full-screen detail destination on a phone. Moving information behind a clearly labeled detail action is acceptable. Dropping information because it does not fit is not.

Parity has four independent acceptance dimensions:

1. **Visual identity:** matching semantic colors, surface hierarchy, chart language, typography hierarchy, icons, and product terminology.
2. **Functional parity:** every current desktop reporting and source-management capability has an Android destination and working action.
3. **Data parity:** identical source records, dates, site scope, and calculation rules produce identical results, missing-data states, ordering, and insight decisions.
4. **Mobile quality:** all required information and actions remain usable at the mandatory window and font configurations, including 320 dp with 200% font scale.

“Same data” does not mean that independently refreshed devices always show identical values at the same wall-clock moment. Provider latency, authorization, retained history, and last successful synchronization affect availability. Prove parity using the same sanitized snapshot and fixed clock first; then compare controlled live acquisitions with their fetch timestamps and scope recorded. Android must work independently after setup/import, without the desktop application or a desktop server running.

### 1.2 Verified planning baseline

| Item | Observed baseline |
| --- | --- |
| Desktop | Native WinUI 3, C#, SQLite/Dapper, LiveCharts; seven current shell routes |
| Desktop branch / HEAD | `codex/numeris-installer-1.0.0`, `c973201847b18e4fb044c21d64eec04f32ff5b0f` |
| Desktop working tree | Dirty, including report ViewModels, repositories, normalization, tests, and an untracked `SitePeriodCoverage.cs`; current local files are the reference, not HEAD alone |
| Android starting branch / HEAD | `main`, `c0d723ef84c56d224fb2d0ea3f0ecf2cb66f3da8` |
| Plan branch | `codex/android-desktop-parity-plan`, created before writing this document |
| Android working tree | Existing changes in `.deepsec`, `app/build.gradle.kts`, and check configuration; preserve them |
| Android stack | Kotlin, Compose Material 3, Navigation 3, manual `AppContainer`, Room 3, DataStore, WorkManager, OkHttp, Vico |
| Android application | `com.emmah.numeris`, version `0.1.0`, minSdk 28, targetSdk 36; shared configuration currently declares compileSdk 37 and Java 17 |
| Declared key versions | AGP 9.3.1, Kotlin 2.4.10, Compose UI 1.11.4, Material 3 1.4.0, Room 3 3.0.1, Navigation 3 1.1.4, Vico 3.2.3 |
| Database versions | Desktop migration constant 11; Android Room schema 1 |
| Existing Android tests found | Model normalization/period/trend/insight tests, network policy/client tests, and encryption/import-package tests |
| Coverage gap | No report UI, report repository, or Room migration test source was found in the inspected Android app/data/database/feature source trees |

Versions above are declarations read from source, not a fresh dependency resolution or compatibility certification. No dependency upgrade is proposed simply to implement this redesign.

This planning pass inspected current source, instructions, layouts, tokens, repositories, sync paths, import code, and test entry points. It did **not** launch either application, inspect a live user database, run providers, build, execute tests, or establish screenshot parity. All screen descriptions below are source-derived; actual rendering and interaction remain implementation acceptance work.

### 1.3 Boundaries

- English UI; dark Numeris theme only; dynamic color remains disabled.
- Preserve the existing Android platform and modular architecture. Do not introduce a WebView wrapper, shared Windows runtime, new DI framework, or replacement navigation framework.
- No telemetry, advertising, Numeris user accounts, Firebase, or Play distribution. Provider authorization remains part of source setup.
- No AI model, voice assistant, or features described only in desktop planning documents. Current deterministic Insights is in scope.
- No live credentials, private analytics snapshots, or provider responses with secrets in fixtures, screenshots, logs, or Git.
- Do not run the user-owned `lint-check` or `security-check` wrappers. Direct Gradle checks listed later are separate.
- Implementation, dependency/build changes, migrations, and navigation changes require explicit approval of their proposed scope under the Android project instructions. This request authorizes the plan file only.
- No commit, push, or PR is part of this work. PR creation and merging remain the user's manual responsibility.

## 2. Source-of-truth map

Paths in this document use `W/` for `C:\Dev\Numeris` and `A/` for `C:\Dev\Numeris_Android`. Proposed filenames are identified as proposed rather than existing.

| ID | Current source | Use in implementation |
| --- | --- | --- |
| W01 | [Desktop instructions](../Numeris/AGENTS.md) | Product boundaries, current visual system, source/report separation |
| W02 | [Desktop tokens](../Numeris/Numeris/Themes/Tokens.xaml), `W/Numeris/Themes/ChartTheme.cs`, `ChartPalette.cs` | Actual visual values and chart conventions |
| W03 | [Desktop shell](../Numeris/Numeris/MainWindow.xaml.cs), `W/Numeris/ViewModels/ShellViewModel.cs` | Current destinations, site/period context, insight navigation |
| W04 | [Desktop Overview](../Numeris/Numeris/ViewModels/DashboardViewModel.cs), `W/Numeris/Views/DashboardPage.xaml` | Portfolio composition, site selection, first-run and freshness behavior |
| W05 | [Portfolio repository](../Numeris/Numeris/Services/Database/Repositories/PortfolioRepository.cs), `W/Numeris/ViewModels/PageDetailViewModel.cs` | Cross-provider page joins and details |
| W06 | `W/Numeris/Views/{Cloudflare,SearchConsole,Bing,Performance,Health,Sources}Page.xaml` and corresponding ViewModels | Report fields, tabs, filters, chart metrics, actions |
| W07 | `W/Numeris/Services/Database/Repositories/` | Aggregation, weighting, latest-record selection, coverage and ordering |
| W08 | `W/Numeris/Helpers/{SiteIdentity,DisplayFormat,PeriodComparison,DateSeries}.cs`, `W/Numeris/Models/Period.cs` | Identity, number/date formatting, date ranges and missing days |
| W09 | `W/Numeris/Models/InsightRows.cs`, `W/Numeris/Services/Insights/{InsightEngine,Trend}.cs`, `InsightMetricsRepository.cs` under W07 | Insight inputs, presence/comparison flags, thresholds, destination targets |
| W10 | [Desktop migrations](../Numeris/Numeris/Services/Database/Migrations.cs), `W/BING-WEBMASTER-SYNC.md`, provider sync/client files | Active schema and bounded provider behavior |
| A01 | [Android shell](app/src/main/java/com/emmah/numeris/NumerisShell.kt), [report screens](app/src/main/java/com/emmah/numeris/ReportScreens.kt) | Current routes, flattened report rendering, refresh dispatch |
| A02 | [Android Overview](feature/overview/src/main/kotlin/com/emmah/numeris/feature/overview/OverviewScreen.kt), [Overview repository](data/src/main/kotlin/com/emmah/numeris/data/OverviewRepository.kt) | Current summary model and placeholders |
| A03 | [Report repository](data/src/main/kotlin/com/emmah/numeris/data/ReportRepository.kt), `A/core/database/.../Daos.kt`, `Entities.kt`, `ProviderDetailEntities.kt` | Existing Room reads, entities and available stored detail |
| A04 | [Android colors](core/designsystem/src/main/kotlin/com/emmah/numeris/core/designsystem/NumerisColors.kt), sibling `NumerisTheme.kt`, `NumerisTokens.kt`, `NumerisComponents.kt` | Existing Compose design primitives |
| A05 | [App container](app/src/main/java/com/emmah/numeris/NumerisApplication.kt), [orchestrator](data/src/main/kotlin/com/emmah/numeris/data/DefaultSyncOrchestrator.kt), provider services in the same data package | Existing manual wiring and synchronization lifecycle |
| A06 | [Sources screen](feature/sources/src/main/kotlin/com/emmah/numeris/feature/sources/SourcesScreen.kt), sibling `SourcesViewModel.kt`, `RoomConnectionRepository.kt` under data | Source forms, connection operations, import UI |
| A07 | [Windows importer](data/src/main/kotlin/com/emmah/numeris/data/WindowsDatabaseImporter.kt), `AndroidImportRepository.kt`, `core/security/.../ImportPackageReader.kt` | Snapshot compatibility and replacement safety |
| A08 | [Android schema](core/database/src/main/kotlin/com/emmah/numeris/core/database/NumerisDatabase.kt), exported `core/database/schemas/.../1.json` | Existing-install upgrade baseline |
| A09 | [Version catalog](gradle/libs.versions.toml), `settings.gradle.kts`, `build-logic/src/main/kotlin/AndroidConfiguration.kt` | Toolchain and module constraints |

Ellipses in non-link paths abbreviate the existing `src/main/kotlin/com/emmah/numeris/...` package hierarchy. During implementation, use current source files instead of treating old prose as executable specifications. In particular:

- `A/UI-SPEC.md` disagrees with both current Kotlin colors and desktop colors.
- `A/DATA-PARITY.md` mentions GA4 and states that golden fixtures run on both platforms; matching cross-platform fixture infrastructure was not established by this inspection.
- Historical desktop `PROJECT.md` and older plans describe behavior that has since changed. Current local implementation and current instructions take precedence.
- Recheck the desktop working tree before implementation. If it changes materially, revise the corresponding contract and fixtures before porting that area.

## 3. Verified gaps and required disposition

These are source-backed implementation differences, not claims about a device test result.

| ID | Observed difference | Required change | Evidence |
| --- | --- | --- | --- |
| G01 | Android card `#202024` and accent `#FF8A3D` differ from desktop card `#1A1B20` and accent `#F56E0F` | Align semantic tokens and actual Material/Vico surfaces | W02, A04 |
| G02 | Android Overview has six summary KPIs and static Traffic trend, Search trend and Insights text | Replace with the desktop portfolio hierarchy and real Room-derived content | W04, A02 |
| G03 | Android Overview always says no cached CrUX status; no Sites or cross-provider Top pages section | Implement actual Vitals state, site summaries and page rollups | W04–W07, A02–A03 |
| G04 | Desktop page detail includes metrics, time series, latest stored page queries, indexing and PageSpeed | Add a native detail destination reading cached data only | W05, A01–A03 |
| G05 | Traffic Security maps country visitor rows; the generic chart still uses visitors for Cloudflare tabs | Use threats for Security and a metric-specific series for every tab | W06, A01 |
| G06 | Android fetches Cloudflare top pages but does not render them in Traffic; stored Web Analytics breakdowns lack report accessors | Expose and render actual country/page/referrer breakdowns | W06, A01, A03, WebAnalyticsSyncService.kt |
| G07 | Google/Bing reports largely display dated records rather than desktop period aggregates, comparisons and discoveries | Add aggregate report models, sorting/filtering and current/previous comparison queries | W06–W08, A01–A03 |
| G08 | Android Performance mixes runs into one generic sequence, prints wire names, and lacks desktop metric/device controls | Separate URL, device, metric, time and field/lab semantics | W06, A01 |
| G09 | Android Health reads all uptime records without the selected period and lacks desktop summary/incident presentation | Separate latest status, historical summary and period-filtered charts | W06–W07, A01–A03 |
| G10 | Android `MetricWindow` has only two numbers; desktop also carries data-presence and comparison flags | Port missing-data and complete-period semantics before porting insight output | W08–W09, core:model |
| G11 | Android `InsightCard` has no structured destination and its 5xx threshold/severity differs | Add route targets and current rule parity; connect actual metric inputs to Overview | W09, core:model |
| G12 | Android has active GA4 source/UI/sync; current desktop has removed GA4 in schema 11 | Retire GA4 from the proposed active product surface without silently deleting existing user data | W10, A01, A05–A08 |
| G13 | Android import queries and inserts GA4 tables unconditionally; current desktop schema 11 removes them | Introduce a tested schema-aware import mapping; do not claim current imports work | W10, A07 |
| G14 | Android domain discovery unions selected cached tables but omits configured-only and some search-only sites | Build the selector from normalized configured targets plus relevant cached identities | W03, A03 |
| G15 | Traffic/Search/Performance/Health feature files are marker objects; most report UI lives in app | Move each rewritten report into its existing feature module as part of that rewrite | A01, feature modules |
| G16 | `ReportRepository` returns database entities, and app report UI imports them | Expose domain/report models so feature modules obey existing dependency rules | A01, A03, Android AGENTS.md |
| G17 | Generic list keys sometimes omit site identity, and chart points omit dates | Use complete logical keys and dated series, especially for All sites | A01 |
| G18 | Tabs use `remember`; top-level navigation clears its stack; saved last-page preference is not used to initialize this shell | Define state restoration and Back behavior explicitly | A01, AppPreferencesRepository.kt |
| G19 | `syncAll` filters configured sources but report-group dispatch does not apply that filter; Indexing inspection calls outside the orchestrator | Unify configured-target filtering, serialization and visible outcomes for all refresh paths | A01, A05 |
| G20 | Source forms are generic metadata fields; desktop supports source-specific discovery and target management | Implement source-specific forms and target lists while retaining secure Android authorization | W06, A06 |

Do not “fix” every backend subsystem preemptively. Reuse existing provider clients, transactions, secure storage, entities and repositories where their verified behavior meets the contract. Many missing UI details already have stored Android entities and sync paths.

## 4. Target information architecture and navigation

### 4.1 Destinations

Keep the existing five primary mobile destinations. Preserve explicit provider identities inside them.

| Mobile navigation label | Screen title / contents | Desktop equivalent |
| --- | --- | --- |
| Overview | Portfolio, Needs attention, Sites, Top pages, Page detail | Overview |
| Traffic | Cloudflare: Traffic, Cache, Security, Status codes, Web Analytics | Cloudflare |
| Search | Provider selector: Google / Bing, with independent tab state | Google Search and Bing |
| Vitals | Performance: Overview, CrUX, PageSpeed, URLs | Performance |
| More | Health, Sources, Privacy and sync | Health, Sources, Android settings |

Keep `Vitals` as the short existing navigation label, but use `Performance` as the report title and the explicit destination name in links. Do not put seven compressed labels into a phone navigation bar. On wider windows, a navigation rail may show the same five destinations; Health and Sources remain clearly listed in More so route behavior does not depend on screen size.

Internal routes must carry stable values, not translated display labels. Use the existing serializable Navigation 3 route approach. Extend it for report provider/tab arguments, source setup, and page detail. A page detail key includes normalized site identity and normalized page path; its period context must be explicit.

### 4.2 Shared report context

- Shared context: selected site or All sites, selected period, and current date range. Preserve it when moving between reports.
- Site picker: searchable sheet on a phone; wrap full site names; include configured sites with no cached results. Avoid selecting a different site automatically because the first sync has not completed.
- Period choices remain `7d`, `30d`, `90d`, `All`. Show the resolved dates, and explain that the current `All` contract is a rolling 365-day window.
- At regular phone size, site and period selectors can occupy a second header row. At 320 dp or large text, stack them; use a labeled period menu if the segmented control cannot fit.
- Header: title, refresh action, selected scope, and relative freshness. A refresh indicator must not replace cached content.
- Use one persistent report refresh affordance. Optional pull-to-refresh must invoke the same operation and remain secondary to a visible accessible button.
- Each tab owns its metric/chart selection and loading state. Changing site/period invalidates the appropriate queries without clearing unrelated navigation state.

### 4.3 Back, restoration, and deep navigation

1. Selecting a primary destination selects its root without accumulating repeated root entries. Preserve that destination's tab/filter/scroll state.
2. Opening Health or Sources from More pushes a child destination. Back returns to More.
3. Opening Page detail on a phone pushes a full-screen destination. Back restores the exact originating list position and filters.
4. Opening an insight selects the correct provider and tab, retains the insight's site/period context, and consumes the target once. It must not trigger a network request merely by navigation.
5. Back first dismisses a visible picker/dialog. After a detail closes, focus returns to the originating row. At a root, delegate to the agreed Android root behavior; never remove the last route into an empty stack.
6. Save small route/filter/scroll identifiers using appropriate Navigation 3, saved-state and Compose mechanisms. Reload report data from Room. Never save database rows, raw responses or secrets into a navigation argument or saved-state bundle.
7. Non-secret form drafts can survive configuration changes. Secret fields must not be persisted into DataStore or saved state; indicate when secure input must be entered again.

Android's guidance distinguishes saved UI state from durable data; use saved state for small identifiers and Room for report records. [Save UI state in Compose](https://developer.android.com/develop/ui/compose/state-saving).

## 5. Visual design contract

### 5.1 Semantic token mapping

Use the current desktop tokens as the brand reference and map their roles into the existing Android design system.

| Role | Desktop reference / proposed Android value | Usage |
| --- | --- | --- |
| App background | `#111114` | Continuous matte shell and page background |
| Card | `#1A1B20` | Main content groups |
| Control | `#24262B` | Selectors, input surfaces, secondary actions |
| Selected navigation surface | `#1F2126` | Restrained selection background |
| Primary text | `#FBFBFB` | Primary labels and values |
| Secondary text | `#DAD4CC` | Explanations and supporting data |
| Tertiary text | `#8E8B87` | Metadata, after contrast validation on its actual surface |
| Disabled text | `#66645F` | Disabled content only; never critical information |
| Primary accent | `#F56E0F` | Primary action, selected state, main chart series |
| On-accent text | `#151419` | Text/icons on orange fill |
| Secondary chart series | `#83B8C0` | Petrol comparison/secondary metric |
| Tertiary chart series | `#A8A39C` | Additional neutral metric |
| Neutral bars | `#4A4D55`, muted `#2D2F35` | Breakdown bars; highest/selected row uses orange |
| Card border / divider | `#14FFFFFF` / `#0FFFFFFF` in ARGB notation | Subtle separation, composited over the actual surface |

Map the full relevant Material color roles and Vico series/axes/markers explicitly so library defaults do not reintroduce a different palette. Keep status success/warning/error/info roles separate from brand accents and extract their exact current desktop values during the token implementation. Do not use orange for every number or every status.

No decorative gradients, glass, blur, mesh backgrounds, large drop shadows, colored strips on card edges, or looping animation. Headers are unboxed. Use the existing Numeris logo and coherent line icons; preserve meaning and optical weight while giving actions mobile touch targets. Do not copy Windows title-bar controls or require a Windows font.

### 5.2 Typography and spacing

Proposed phone typography, using Android's available sans-serif font and scalable `sp`:

| Role | Starting size / line height | Behavior |
| --- | --- | --- |
| Screen title | 28 / 34 sp | Wrap when needed; no fixed-height clipping |
| Section title | 16 / 22 sp, semibold | Matches desktop hierarchy |
| Hero metric | 32 / 40 sp, semibold | Tabular numerals where supported; allow a separate label/value row |
| Supporting metric | 24 / 32 sp | One or two columns based on actual fit |
| Body / data labels | 14–16 / 20–24 sp | Use 16 sp for form input and longer mobile explanations |
| Metadata | 12 / 18 sp minimum starting size | Never shrink further to force a layout |

Use the shared 4/8/12/16/24/32/48 spacing scale. Starting phone margins: 16 dp, with 12 dp permissible at 320 dp; card padding 16 dp; section gaps 16–24 dp. Use 10 dp card corners and 8 dp controls to echo desktop tokens. Keep the existing design-system ownership of these values.

Do not scale down the user's font setting, force single-line KPI labels, or use desktop 32 dp page gutters on the narrowest phone. Avoid a separate card for every individual datum: group related metrics and use quiet dividers.

### 5.3 Responsive behavior

| Available window | Proposed composition |
| --- | --- |
| Compact, below 600 dp | Bottom navigation, one main content column, full-screen detail |
| Medium, 600–839 dp | Navigation rail where suitable; one or two content columns only when measured content fits |
| Expanded, 840 dp and above | Rail; optional side-by-side list/detail when both panes remain usable; bounded readable content width |
| Any width at 200% font | Prefer stacked controls/KPIs; independently evaluate remaining content width, not only the window class |
| Compact height / landscape / IME open | Reduce nonessential header space, keep content scrollable, and fall back to one pane if detail would be cramped |

Use the actual available app window, not a device-type flag. Width classes and compact height are documented in [Android window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes). The composition choices above are Numeris design decisions, not claims that the library implements them automatically.

### 5.4 Reusable components to implement or extend

Extend existing primitives first. Add a component only when the approved reports require it:

- Report header with title, refresh, scope and freshness.
- Site picker and period selector with narrow/large-text variants.
- KPI block with nullable value, delta state, period coverage and optional sparkline.
- Status label with text and semantic state; color is supplemental.
- Report section header and empty/error/offline presentation.
- Dated chart with legend, selected-point details and text alternative.
- Horizontal breakdown row with label, numeric value and optional share.
- Expandable data row for query/page/site summaries.

Do not build a general dashboard schema, configurable widget framework, or universal report renderer. The current `OfflineReport` abstraction loses report-specific meaning; share presentation primitives while keeping each report's model and composition explicit.

## 6. Screen-by-screen specification

### 6.1 Overview

Phone reading order:

```text
Overview                                      Refresh
Updated 2 hours ago
[All sites v]              [Last 7 days v]

Visitors, last 7 days
41,993     +12.4%           [only with valid comparison]
Cloudflare, 7 of 7 days
[current series + dashed previous series]

Search clicks              Indexed sitemap URLs
Google + Bing              indexed / inspected, active URL coverage
Uptime                     [explicit historical basis]

Needs attention
[severity] concrete observation -> exact report tab

Sites
example.com        visitors, change, sparkline
Google / Bing      Vitals / mobile PageSpeed / uptime

Top pages
/example/          page views, Google clicks, Bing clicks, index
Tap -> Page detail
```

Numbers in this wireframe are illustrative layout content only; never seed them into production data.

Required behavior:

- Portfolio defaults to All sites. Site rows contain every available configured/cached site, including sites with unavailable metrics.
- Preserve desktop order: visitors descending, then domain; use desktop Top pages ordering and top-ten summary limit. A mobile expansion must not silently change the ranking.
- Selecting a site row filters Overview; selecting it again returns to All sites. Also provide an explicit All sites choice so deselection is discoverable.
- Hero: Cloudflare visitors, correct period coverage, valid delta only, and current/previous dated chart. Do not substitute GA4 users or Web Analytics visits.
- Search summary combines Google and Bing clicks with a visible provider breakdown. Keep absence distinguishable from observed zero.
- Indexing summary uses saved URL Inspection results for active sitemap URLs. The desktop percentage is indexed / inspected, not indexed / all active URLs; show active and inspected counts separately. With no inspected URLs, show an unavailable percentage.
- Uptime mirrors the desktop summary basis and labels it: each site's percentage is successful / all stored probes, and the portfolio is the arithmetic mean of percentages for sites with a recorded check. Do not substitute a probe-weighted portfolio total or imply continuous monitored availability over the selected report period.
- Needs attention comes from the deterministic engine, including concrete `whyShown` evidence and a structured destination. Empty evaluated results and insufficient data must have different copy.
- Never show “No issues were found” before the engine has evaluated sufficient cached data.
- First-run state is based on usable local content plus connection/sync state. Imported usable data must display even if Android has not yet recorded a successful local sync.

### 6.2 Page detail

Use a full-screen phone destination and an optional second pane on sufficiently wide windows. Avoid a long nested scrolling bottom sheet for the complete report.

Required sections:

1. Full page identity: domain and readable path, with the complete normalized URL available through an explicit action.
2. Page views, Google clicks, Bing clicks and impression-weighted Google position.
3. Current/previous traffic and search history with dated axes, coverage and missing-day gaps.
4. Latest stored Google page-query period, its dates, query/click/impression/CTR/position fields, and desktop query summary limit of eight.
5. Saved URL Inspection status, coverage explanation and inspection time when available.
6. Latest PageSpeed information for the matching tracked URL/device; never substitute another URL's results.
7. If the URL is not tracked for performance, show a contextual action to Sources with this URL prefilled. Adding it is an explicit user action.

Opening or revisiting the detail reads Room only. Navigating into it does not trigger URL inspection, PageSpeed, or a provider fetch. Preserve desktop page-path grouping rules for cross-provider rollups while retaining full URL identity for provider calls and performance tracking.

### 6.3 Traffic / Cloudflare

Keep the five desktop views. At 320 dp/large text, a labeled View selector may replace a crowded tab strip; every view must remain discoverable.

| View | Required data and presentation | Mobile behavior |
| --- | --- | --- |
| Traffic | Visitors, page views, pages per visit, valid deltas/coverage, daily traffic, top countries, top pages | KPI groups stack; country/page bars use readable labels and exact values |
| Cache | Cache hit ratio, cached requests, bytes saved, relevant daily series | Use ratio/byte units; no visitor chart under a cache title |
| Security | Threat count and daily threats | Country visitors must never masquerade as security events |
| Status codes | Success rate, 4xx/5xx count and share, top issue, daily status groups, code/meaning/count/share rows | Expand a status row to show all fields; explain unknown codes without guessing |
| Web Analytics | Visits and page views history; top referrers, pages and countries | Clearly distinguish Web Analytics visits from Zone Analytics visitors/requests |

Do not offer the obsolete GA4 Analytics report as part of the new desktop-parity navigation. Existing GA4 data preservation is handled in section 10.

Scope each chart to its tab's actual metric. For multiple sites, aggregate each day using the desktop repository contract rather than drawing one point per site/day row as a single sequence.

### 6.4 Search / Google

The provider selector visibly names Google and Bing; their metrics are not merged into a generic search table. Preserve provider-specific tab/filter state.

| View | Required information/actions |
| --- | --- |
| Overview | Clicks, impressions, weighted CTR and position, valid period deltas, coverage, clicks/impressions history |
| Queries | Period-aggregated query list, text filter, sort by clicks/impressions/CTR/position, and New queries section |
| Pages | Period-aggregated pages and Declining pages section with current/previous clicks and change |
| Devices | Daily clicks by device and aggregate device shares; readable device labels |
| Indexing | Active sitemap URL coverage, inspected/indexed counts, verdict/coverage detail and timestamps |

For query/page rows, show identity and clicks in the collapsed row; expose impressions, CTR, position and relevant comparisons through a labeled expansion/detail action. No hover dependency. Long queries and paths must remain inspectable in full.

The main refresh action on Indexing performs URL Inspection through the existing Google authorization and configured property mapping. It must not merely refresh Search Analytics and imply that index status changed. For All sites, either perform a bounded operation across eligible configured properties with visible scope, or require site selection with a clear explanation; choose and test this behavior in phase P5 before release. Never silently execute a different refresh type.

New queries and declining pages require valid comparison inputs. A missing previous window must not be interpreted as every query being new or every page having declined.

### 6.5 Search / Bing

| View | Required information/actions |
| --- | --- |
| Overview | Clicks, impressions, CTR, coverage, valid deltas and daily history |
| Queries | Period aggregation, clicks/impressions/CTR and average impression position, supported sort options |
| Pages | Page identity, clicks, impressions and CTR |
| Crawl | Stored report summaries, readable method labels, fetch times, issue list and safe issue details |

Preserve the existing bounded Bing method set: `GetUserSites`, `GetRankAndTrafficStats`, `GetQueryStats`, `GetPageStats`, `GetCrawlStats`, `GetCrawlIssues`. No bulk expansion into additional detail APIs. Preserve API-provided record dates and historical keys. Do not make raw method identifiers or JSON the primary mobile experience; retain necessary desktop detail through a readable disclosure, with redaction enforced before persistence/display.

### 6.6 Performance

| View | Required information/actions |
| --- | --- |
| Overview | Actual Core Web Vitals status/detail, mobile and desktop PageSpeed score/rating, current metric summaries and latest runs |
| CrUX | Metric selector, form-factor selector, origin/URL identity, p75 history and collection-window context |
| PageSpeed | Device-specific score history, latest run identity/time, performance/accessibility/best-practices/SEO scores, audit issues and display values |
| URLs | Measured URL list, enabled/source metadata and access to target management |

Use LCP/INP/CLS and other metric labels only when present in the selected current desktop contract/data. Map raw identifiers and form factors to readable labels and correct units. Keep field data (CrUX) and lab data (PageSpeed) distinct. No CrUX record means unavailable field data, not failed performance.

History must not combine different URLs, strategies or CrUX metrics into one unlabeled line. Select or facet them explicitly. “Latest run” is selected per URL and strategy using stable timestamp ordering. Preserve all four PageSpeed categories and runtime/warning context supported by the stored record.

Keep URL management in Sources as on desktop. The Performance URLs view exposes the same list and a clear Manage URLs action. If retaining an inline shortcut, route it through the same validation/write path; do not duplicate logic. A path such as `https://example.com/article/` must not be normalized into the site's home page when adding a measured URL.

### 6.7 Health

- Uptime: site status, last check, HTTP status when available, response time, historical probe success percentage, failed-check count, period-filtered response-time and incident charts.
- Keep failed checks distinguishable from missing response-time samples. A timeout is not a successful 0 ms response.
- Explain that checks run when the app performs them; periodic WorkManager scheduling is not a continuous monitoring SLA.
- Sitemap: known active URLs, last seen, inspection state and refresh timestamp; preserve removal/reactivation history according to the desktop repository contract.
- Keep sitemap discovery and Google URL Inspection conceptually separate. A discovered URL is not proof of indexing.
- Refresh dispatch follows the active tab: check uptime or refresh sitemap. A generic refresh must not run both unexpectedly.
- All-site charts retain domain identity or use an explicitly labeled aggregate matching the desktop calculation; do not alternate sites in one unlabeled series.

### 6.8 Sources

Use a provider-grouped list with concise status summaries. Open a dedicated scrollable form for a provider on a phone; avoid placing a long multi-field form inside a small alert dialog.

| Group | Required management capability |
| --- | --- |
| Cloudflare Zone Analytics | Add/edit domain and Zone ID, enter/update token, test saved/new connection, disconnect |
| Cloudflare Web Analytics | Account ID/token, discover authorized sites, show host/site-tag mapping, add/remove mappings, test/disconnect |
| Google Search Console | Android authorization, configured property/site mapping, test/reconnect/disconnect, property/domain distinction |
| Web Performance | Separate CrUX and PageSpeed key/status handling; add/enable/disable/remove measured URLs |
| Bing | API key, test/disconnect, configured/discovered site URLs, add/remove targets |
| Android data transfer | Encrypted desktop import preview, supported schema handling, explicit replacement confirmation and outcome |

Do not copy Windows OAuth client-secret or loopback login UI into Android. Retain Android-native provider authorization and verify its actual configuration before claiming live parity. Show user-facing labels instead of internal field names such as `zoneId` or `sites (comma separated)`.

Connection testing may contact the selected provider, but it must not silently populate report history or run a general sync. Saving configuration does not fetch reports. Preserve cached reports on disconnect unless the user separately requests data deletion. Require confirmation for destructive target/data replacement actions and explain their real effects.

Use existing Keystore/SecretStore handling. Never display a saved secret again; an empty replacement field keeps the existing secret. App lock and import biometric prompts were removed at the user's request on 2026-10-13. Provider-level success/failure and busy state must identify the affected source/target accurately.

### 6.9 More / Android settings

Retain useful Android-specific behavior: background sync enablement and the existing 6/12/24-hour choices. These support mobile use and do not violate desktop reporting parity. Explain background execution as approximate. Health and Sources must be clearly labeled and reachable without searching through unrelated settings.

## 7. Calculation and data contracts

### 7.1 Normalize without losing meaning

- Reuse `SiteIdentity` as the single site/domain/origin authority and bring its behavior into parity with the current desktop implementation.
- Cross-provider page rollup key: normalized domain plus the desktop `PagePath` result, including its query/fragment removal and trailing-slash rules.
- Provider property identity, report page grouping and measured Performance URL identity are different concepts. Do not collapse them into one home-page-only string.
- Include domain/provider/date/dimension where required in database and UI row keys. Same query/path/date on two different sites must not collide accidentally.
- Build available sites from configured targets and appropriate cached identities, with stable sorting and deduplication. Do not hard-code the user's current site names.

### 7.2 Periods, coverage and series

1. Fixed clock inputs in parity tests. Selected end date is the shared reference date; start is `end - (days - 1)`; bounds are inclusive.
2. Previous period has the same number of days and ends one day before the current period.
3. Preserve `All = 365` days until a separate product decision changes both contracts.
4. Build dated series with explicit missing values. Missing days produce gaps, not invented zero observations.
5. Gate percentage deltas using complete current and previous coverage and a positive previous total, matching desktop `CompleteChange` and site-coverage behavior.
6. Preserve `NewActivity` for valid zero-to-positive trend semantics. Do not display a fabricated `+100%` KPI delta from a zero denominator.
7. In All sites, verify per-site coverage. One site's complete series cannot conceal another site's missing history.
8. Distinguish daily measures, period aggregates, latest snapshots and rolling provider collection windows in both models and labels.
9. Provider dates must retain the provider's meaning. Normalize timestamps for storage/display without shifting date-only records into a different reporting day.

Current desktop source itself contains legacy fallbacks and special cases. Capture them explicitly in fixtures; if a case would double-count or misrepresent records, record a contract discrepancy and resolve it before accepting parity. Do not blindly port a known defect or quietly change the desktop reference.

### 7.3 Metric rules

| Metric | Contract / pitfall to test |
| --- | --- |
| Google/Bing clicks and impressions | Sum the intended daily or dimension dataset once; never sum daily + query + page rows into the same total |
| CTR | `sum(clicks) / sum(impressions)` with explicit zero/absence handling, not mean(row CTR) |
| Google position | Impression-weighted position; missing weight/data is not a meaningful zero position |
| Cache hit ratio | Total cached requests / total requests for the scope, not unweighted average of daily percentages |
| Status shares | Counts and denominators from the same scope/window; preserve success-class definition from desktop |
| Pages per visit | Match desktop numerator, denominator, coverage and undefined-state behavior |
| PageSpeed scores | Store ratios; display 0–100 once; select latest per URL/strategy and use matching rounding |
| CrUX p75 | Preserve metric units, form factor, target type and collection dates |
| Indexing | Active sitemap, inspected and indexed populations remain separate; no inference from search clicks |
| Uptime | Keep probe-count percentage and latest status distinct from period daily chart aggregates |
| Portfolio Top pages | Join normalized domain/path across Web Analytics, Google page rows and Bing page rows; preserve deterministic tie-breaking |
| Freshness | Use successful source/target result metadata; a failure cannot advance successful-sync time |

Centralize display formatting in a small Kotlin equivalent of desktop `DisplayFormat`: English/en-US grouping, percentages, bytes, milliseconds, dates, relative time, missing values, readable countries/devices/metrics and URL display. Keep unrounded values through aggregation. Test C# versus Kotlin rounding at midpoint and negative-delta cases rather than assuming their default rounding is identical.

### 7.4 Deterministic Insights

- Add presence/comparison flags required by the current desktop `MetricWindow` behavior.
- Build insight metrics from Room through the report data boundary, not from UI-formatted strings.
- Port the current desktop rule set, severity, priority, limit/order, `whyShown` and structured destination.
- Remove retired GA4-dependent insights from the active engine path without deleting historical GA4 records as a side effect.
- Include explicit 5xx boundary fixtures: fewer than ten errors must not produce the desktop server-error insight; at least ten with share below 1% is Warning; at least ten and share at least 1% is Critical, subject to the rule's minimum request/data conditions.
- Test missing source data, incomplete comparisons, new activity, stale data, indexing coverage, query discovery, declining pages, mobile performance demand, cache efficiency and threat changes.
- Each insight opens the precise relevant provider/tab, with visible site context. No AI service or extra API call is necessary.

## 8. Android implementation structure

### 8.1 Keep the current ownership model

```text
Provider API -> existing network client -> source sync service
            -> atomic repository/database write -> Room
            -> ReportRepository -> report ViewModel UiState -> Compose

AppContainer: wiring, application lifecycle, route dispatch
DataStore: preferences and non-secret selection state
SecretStore: provider secrets
```

| Module | Approved-direction work to propose at implementation time |
| --- | --- |
| `:core:model` | Report models, dated samples, coverage, structured insight targets, identity/format calculation contracts |
| `:core:database` | Required aggregate/detail queries and only necessary schema migrations/indexes |
| `:core:network` | Retain shared redaction/HTTP policy; fix provider request/mapping gaps only when parity evidence requires it |
| `:core:security` | Preserve SecretStore and encrypted package boundaries; bounded import compatibility changes |
| `:data` | Report aggregation/mapping, source/target operations, sync outcomes, importer and preferences |
| `:core:designsystem` | Desktop-derived semantic tokens and mobile reusable presentation components |
| Existing feature modules | Screen-specific ViewModels and Compose UI, depending only on allowed model/designsystem/data boundaries |
| `:app` | Composition root, navigation, authorization integration, worker scheduling |

Keep `ReportRepository` as the report read boundary. It may compose small internal query/mapping collaborators for materially different reports, but do not introduce a second competing report data source. Extend/refactor `OverviewRepository` into this arrangement rather than maintain duplicate calculations.

Proposed report-model families: portfolio summary, site summary, portfolio page, page detail, Cloudflare report, Google report, Bing report, performance report and health report. Use typed nullable numeric fields and explicit coverage, freshness and status. Add only the fields required by the screen contracts. Do not expose Room entities or provider DTOs to feature UI.

### 8.2 UI state and asynchronous work

- Each report ViewModel exposes a single coherent state with cached content, local loading state, refresh state and safe result message.
- Use lifecycle-aware Flow collection; cancel/replace superseded site/period reads so slow old results cannot overwrite a newer selection.
- Query and aggregate off the main thread. Do not scan/sort large tables inside a composable or on every recomposition.
- A report data update must correspond to a coherent snapshot/transaction, not temporarily mixed old/new provider tables.
- Keep stable list keys, restore scroll, and load details on demand. Do not subscribe to every provider table for a hidden tab unless needed for a visible summary.
- Separate “no data yet,” “zero observed,” “no matching results,” “not configured,” “authorization required,” “offline with cache,” and “partial refresh.”

### 8.3 Synchronization behavior

- Reuse `DefaultSyncOrchestrator` for user refresh and background sync. Route Indexing inspection through the same serialization and outcome lifecycle.
- Apply configured-source and selected-target filtering consistently to global, report and individual operations. Eligibility must not disappear merely because a previous successful result changed a status label from `configured` to `connected`; test the existing status/query interaction.
- Snapshot the requested site/period/operation when refresh begins. Changing filters mid-refresh must not retarget a running operation or apply its status to the wrong screen.
- Record success/error/freshness at the actual affected target granularity. Do not mark every connection for a provider successful when only one site's result was written.
- Preserve prior cached records on failure; commit each complete logical provider result atomically. Separate successful sibling targets from failed ones.
- Handle cancellation without reporting success or corrupting metadata. Repeated taps must not queue an unbounded series of identical jobs.
- Expose sanitized partial/auth/rate-limit/error outcomes, with reconnect/retry actions where appropriate. Never display raw URLs containing credentials or raw error response bodies.
- Ordinary screen/tab navigation remains read-only. Source testing, report refresh and target configuration are separate operations.

## 9. Charts, lists, accessibility and mobile performance

### 9.1 Charts

- Retain Vico if it supports the required dated gaps, comparison styling and accessibility with the current dependency. Verify those capabilities before inventing adapters or changing libraries.
- X-axis represents actual dates/timestamps, not row index. Sort and aggregate before rendering.
- Current/previous series use a clear legend and distinguishable line style. The previous-period dates are aligned by period offset, with exact dates available in details.
- Units are explicit. Never put counts and percentages on an unlabeled shared scale.
- Single-point, all-zero, sparse, missing, flat and extreme-value series each have deliberate rendering.
- Marker/tap details expose date, value, source and scope. No hover-only information.
- Provide an accessible summary and a reachable textual data view/list. A decorative sparkline can be hidden from accessibility when its parent already supplies the equivalent summary.
- Proposed phone chart height starts around 200–240 dp, adjusted to content and available height. Do not hard-code a desktop 380 dp chart on every phone or clip scaled axis labels.

### 9.2 Lists and dense information

Use virtualized lists for records. Summaries can retain desktop top-N limits; when showing a full report with more records than the displayed limit, make that limit explicit and provide a deliberate load-more/local paging path if required to expose the desktop data. Sort/filter the full eligible dataset before limiting it.

At 320 dp, data rows become labeled vertical groups. At 200% font, values may move beneath labels. Avoid horizontal page scrolling as the main way to reach essential metrics. An optional wide-table view must not be the only accessible representation.

### 9.3 Accessibility

- Minimum 48 × 48 dp independent touch targets, including refresh, expansion, close and source actions. Android documents both minimum target sizing and semantics defaults; inspect custom components explicitly. [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
- Proposed acceptance contrast: at least 4.5:1 for ordinary readable text and 3:1 for large text/meaningful non-text controls, measured on actual composited surfaces.
- TalkBack announces title, metric value/unit, comparison, missing-data state and action where relevant; avoid duplicate announcements from nested semantics.
- Use selected/expanded/busy semantics. Status cannot rely only on hue, arrows or chart shape.
- Reading/focus order follows visual order. Sheets/dialogs have correct focus entry and restoration.
- Full long URLs/queries remain available through an explicit accessible action; truncation alone is insufficient.
- Support external keyboard navigation for forms and visible actions. Respect system animation settings and predictive Back behavior where supported by the retained stack.
- Test IME open, cutouts, gesture navigation and three-button navigation. System insets must not cover the final list row or primary form action.

### 9.4 Performance acceptance

Measure on a named representative device with a deterministic dataset after functional implementation. Record hardware, API, build type, data volume and cold/warm conditions.

Proposed starting budgets, to confirm against the first implementation baseline:

- Cached first meaningful content within 1 second on a warm navigation and within 2 seconds after app initialization on the reference device.
- Visible response to a tap within 100 ms; network operations show progress while keeping cache visible.
- At least 95% of measured scroll frames within the device refresh interval on the reference report flow; record the actual interval and frame data.
- No main-thread database/network operations, ANRs, unbounded report subscriptions or repeated fetch caused by recomposition.
- Large local report lists use bounded visible work; test at least a multi-site 365-day fixture and a 1,000-row query/page list.

These are proposed acceptance targets, not current measured performance or guaranteed timing across all devices. Start with existing Android profiling/test tools; add a benchmark dependency only if needed to prove a specific budget.

## 10. Existing installations, GA4 retirement and desktop import

### 10.1 Preserve Android data

Android Room schema 1 contains useful history and retired-provider tables. Prefer additive queries and models. Keep schema 1 if no stored structure must change. For every actual schema change, increment the version, export the schema, provide a migration and test the old-to-new path with representative records. No destructive fallback and no uninstall/reinstall as the upgrade strategy.

Proposed GA4 disposition:

1. Remove GA4 from the new active report/source-add flow and from new automated parity calculations.
2. Stop GA4 scheduled/manual acquisition as part of the approved retirement change.
3. Preserve existing GA4 rows and credentials during upgrade; do not copy the Windows drop-table migration blindly.
4. If existing GA4 configuration is found, surface its retired status in Sources and offer a separately confirmed disconnect/credential removal. Preserve its historical data unless deletion is explicitly requested.
5. Do not require a separate legacy analytics viewer or data-export subsystem for this redesign. Record preserved legacy data clearly in migration/import documentation.

This disposition is a proposal requiring approval with implementation scope. It prevents “match desktop” from silently becoming authorization to delete existing Android data.

### 10.2 Import compatibility

The inspected Android importer has an encrypted package reader and preview/replace UI, but direct GA4 table queries are incompatible with a schema-11 desktop snapshot lacking those tables. No current desktop export command/UI was located in the inspected application/tools sources. Therefore end-to-end user transfer is a dependency to resolve, not an established capability.

Required sequence:

1. Specify supported desktop schema versions and export format independently. Confirm the actual existing exporter location, or explicitly scope a minimal desktop exporter as a separate approved change.
2. Do not advertise import as usable until a real supported export can be produced and imported end to end. Fresh Android configuration and provider sync must work without an export.
3. Validate manifest/version, required tables/columns, row counts and supported identifiers before replacing any Android data. Unknown future schemas fail without mutation.
4. Use a schema-specific mapping of supported current tables; absent retired GA4 tables are expected for desktop schema 11.
5. Translate snake_case Windows columns and provider/connection identifiers into Android's actual schema. Do not attach and assume the files are interchangeable.
6. Validate normalized targets, historical query/page dates, page-query windows, raw-data limits/redaction, enabled flags and existing secrets handling.
7. Present sites, record counts, snapshot time, supported/omitted categories and replacement effects before confirmation. Support data-only transfer if secrets are unavailable; require Android Google reauthorization rather than assuming Windows tokens are portable.
8. Coordinate import with sync so replacement cannot race provider writes.
9. Test recovery across Room, SecretStore and DataStore. A SQLite transaction alone cannot atomically roll back all three stores; preserve/restore staged values or fail before changes so the UI cannot truthfully claim success after partial replacement.
10. Clean temporary decrypted material after success, cancellation and failure; preserve the original encrypted input file.

Mandatory import tests: schema 11 without GA4, each explicitly supported older format, unsupported newer schema, row-count mismatch, missing required column, invalid archive entry, bad passphrase/GCM tag, duplicate normalized records, replacement cancellation, injected database failure, secret-store failure and process interruption. No test may touch the user's live Windows database or credentials.

## 11. Cross-platform parity fixtures

Create a small, deterministic, sanitized fixture set. This is required evidence for “same information,” not a large new testing framework.

- Store fixtures in a proposed `test-fixtures/parity/` directory with a fixed clock/timezone, schema/contract version, sanitized records and expected outputs.
- Generate/check the desktop reference against current C# calculations using isolated data. The desktop test project is a custom console harness, not automatically a `dotnet test` project; inspect its selected execution path before running it, including any owner-reserved wrappers.
- If the C# harness needs a focused parity mode, propose the smallest addition in the desktop repository. Do not run the entire harness blindly or modify desktop application logic to make Android tests pass.
- Run the same cases through Kotlin calculations and actual Room queries where SQL behavior matters. Pure Kotlin tests alone cannot prove the DAO contract.
- Compare integer counts, normalized strings, date windows, selected records, ordering, insight severity/target and displayed strings exactly. For intermediate floating arithmetic, document a narrow numerical tolerance such as `1e-9` where justified; it must never hide a different final formatted value or threshold decision.

| Fixture ID | Required scenario | Evidence produced |
| --- | --- | --- |
| F01 | Empty database, configured-only site, search-only site | Domain list and correct empty/missing states |
| F02 | One site with full 7/30-day current and previous data | KPI, delta, series and ordering parity |
| F03 | Multiple sites with duplicate dates/queries/paths | Correct aggregation and stable unique UI keys |
| F04 | One missing day/site in otherwise full periods | No false complete-period delta; chart gap preserved |
| F05 | True zeros, absent data, zero-to-positive activity | Zero/missing/new-activity distinctions |
| F06 | Unequal impressions and unequal daily request volumes | Weighted position/CTR/cache ratios |
| F07 | Full URLs, paths, queries/fragments, trailing slash and case variants | Identity/join parity without measured-URL path loss |
| F08 | Page available in only one or two providers | Portfolio join, absence labels and detail fields |
| F09 | Mixed URL/device PageSpeed runs and CrUX collection windows | Latest-record selection, histories and units |
| F10 | Active/removed/uninspected/indexed sitemap URLs | Separate sitemap and inspection population semantics |
| F11 | Insight threshold boundaries and missing comparisons | Exact title/evidence/severity/priority/target decisions |
| F12 | Timeout/failed/successful uptime probes across dates | Correct historical percentage and period charts |
| F13 | Imported schema 11 with no GA4 tables | Supported transfer and post-import offline reports |
| F14 | Midpoint rounding, very large values and negative changes | Display parity and absence of overflow/truncation |
| F15 | Partial provider failure and interrupted transaction | Prior cache preserved; accurate source/target freshness |

Synthetic fixtures are for tests/previews only. Production startup must never generate demo analytics data.

## 12. Implementation phases and dependencies

The sequence delivers testable vertical slices. Each phase ends with evidence for its own behavior; broad release checks are run after integration. Do not consider the whole request complete after only the theme or Overview.

### P0 — Freeze the reference and approve the implementation scope

**Depends on:** This plan.

**Changes:** Planning/fixtures preparation only until explicit implementation approval.

- Record current desktop/Android HEADs plus dirty-file hashes for the files used as the reference.
- Capture actual desktop views with sanitized data at a regular desktop size; include all tabs, Page detail and source forms. Capture Android baseline with the same fixture where possible.
- Convert the section 6 inventory into a checked parity ledger with row IDs and link every row to its desktop behavior, Android destination and test.
- Resolve import exporter availability and the exact All sites Indexing behavior. Approve GA4 retirement/data-preservation behavior.
- Confirm that implementation retains the existing toolchain/DI/navigation and needs only narrowly justified test dependencies.

**Exit:** No ambiguous major product scope; current reference recorded; implementation approval covers the selected phases. Missing runtime reference evidence remains explicitly open, never substituted with an old screenshot.

### P1 — Establish the calculation and persistence contract

**Depends on:** P0.

**Primary files:** `core:model` identity/period/trend/insight models; `data/ReportRepository.kt`, `OverviewRepository.kt`; `core:database` DAO/entity/schema files; proposed fixture/test files.

- Implement F01–F08 and F11/F14 against isolated data.
- Add presence/coverage and dated report models; map database entities behind the report boundary.
- Add portfolio/site/page-detail and missing Web Analytics/page-query read paths using existing entities where possible.
- Implement comparison/date-series/display contracts and correct site discovery.
- Add only necessary indexes/migrations and prove existing-install preservation.

**Exit:** Deterministic report calculations and query results match the desktop contract; no feature-facing database types; old data survives required migrations. No placeholder metrics are required to build the UI.

### P2 — Align the design system and mobile shell

**Depends on:** P0; report model names from P1.

**Primary files:** `core:designsystem/*`, `app/NumerisShell.kt`, `AppPreferencesRepository.kt`, proposed route/state tests.

- Apply the section 5 token map and core component states.
- Implement responsive headers, selectors, list rows, KPIs and chart presentation primitives.
- Add typed report-tab/detail/source routes, root Back protection and restoration.
- Use representative fixture screens to validate 320 dp and 200% text before converting every report.

**Exit:** Shared components pass all four mandatory phone width/font combinations; navigation preserves site/period and tab state; no framework migration.

### P3 — Complete Overview, Insights and Page detail

**Depends on:** P1, P2.

**Primary files:** existing `feature:overview` plus proposed Overview/PageDetail ViewModels and screens; report repository and insight mapping as needed.

- Replace summary placeholders with the portfolio hierarchy.
- Wire actual Vitals, site rollups, Top pages, comparison charts and deterministic insights.
- Implement page detail and context-preserving insight navigation.
- Verify first-run, imported cache, missing provider, partial coverage and long-path cases.

**Exit:** F01–F08/F11 pass through UI; every desktop Overview field has a visible location; opening detail/insights causes no network call; Back restores context.

### P4 — Complete Cloudflare and Web Analytics

**Depends on:** P1, P2.

**Primary files:** `feature:traffic`, Cloudflare/Web Analytics report reads, necessary client/sync mapping corrections.

- Replace marker feature with the actual screen/ViewModel and remove its superseded app renderer.
- Implement all five views and their actual metric series.
- Add breakdowns, weighted ratios, status summaries and contextual source/empty states.
- Test day/site aggregation, missing days, zero requests and actual threats.

**Exit:** Traffic/Cache/Security/Status codes/Web Analytics all match their independent contracts; no country-as-threat or visitor-as-cache chart remains.

### P5 — Complete Google and Bing reporting

**Depends on:** P1, P2; sync dispatch support as required from P8.

**Primary files:** `feature:search`, Google/Bing report queries, typed routes, indexing operation.

- Implement provider-specific tabs and aggregates.
- Add Google query filtering/sorting, New queries, Declining pages and device reporting.
- Add Bing query sorting, page reporting, crawl summaries and safe issue detail.
- Implement Indexing summary/detail and the approved active-tab refresh behavior.
- Test fixtures with absent previous history, repeated query identities and mixed property mappings.

**Exit:** Every Google/Bing tab is usable and data-correct; Indexing invokes inspection rather than generic refresh; selected provider/tab survives navigation and recreation.

### P6 — Complete Performance and Health

**Depends on:** P1, P2.

**Primary files:** `feature:performance`, `feature:health`, corresponding report queries and target-management interfaces.

- Implement all Performance views, selectors, latest snapshots and correctly scoped histories.
- Implement Health status, historical summary, response/incident charts and sitemap reporting.
- Route URL management to Sources and preserve complete measured URLs.
- Validate F09/F10/F12, missing CrUX data, failed probes and long URL/metric labels.

**Exit:** No mixed-device/URL chart or missing-as-zero response time; period-scoped charts and latest/historical summaries are visibly differentiated.

### P7 — Complete Sources and transfer compatibility

**Depends on:** P1, P2; P0 import decision.

**Primary files:** `feature:sources`, `RoomConnectionRepository.kt`, import repositories/readers, target models and required migrations.

- Implement provider-specific forms, discovery and editable target lists.
- Preserve Android authorization, source tests, cached-data-on-disconnect and encrypted secret storage.
- Implement approved GA4 retirement without silent data/credential loss.
- Implement schema-aware import, preview and failure recovery; if desktop exporter work is needed, keep it a separately approved, narrowly scoped dependency.
- Validate all import cases with sanitized files and isolated stores.

**Exit:** Sources supports the desktop management capabilities and mobile-safe forms; current-format import is proven end to end or explicitly remains a release blocker. App setup/sync operates without desktop runtime access.

### P8 — Integrate synchronization and lifecycle correctness

**Depends on:** Implement alongside P3–P7 where their actions need it; final gate after those phases.

**Primary files:** `DefaultSyncOrchestrator.kt`, source services, `NumerisApplication.kt`, `NumerisSyncWorker.kt`, MainActivity/authorization integration and affected ViewModels.

- Unify configured/target scope, Indexing serialization and source-specific outcomes.
- Verify repeated refresh, provider partial failures, concurrent background work, filter changes and process recreation.
- Preserve correct success/error metadata and prior cache after cancellation/failure.
- Verify unlocked resume/cold start and authorization recovery with the final route model.

**Exit:** One coherent refresh pipeline, accurate target freshness, no overlapping writes/import race, and deterministic recovery to readable cache.

### P9 — Mobile acceptance and final parity audit

**Depends on:** P3–P8.

**Changes:** Only fixes for observed acceptance failures, required test infrastructure and current documentation.

- Run the complete parity ledger and validation matrix below.
- Compare desktop and Android against the same fixture records and pinned reference.
- Capture all mandatory mobile configurations and review screenshots plus actual touch/Back/TalkBack behavior.
- Measure large-list/chart performance on the reference device and inspect release behavior.
- Update `UI-SPEC.md`, `DATA-PARITY.md`, `ARCHITECTURE.md`, `TESTING.md` and README to the actual implemented state. If module/data-flow/responsibility contracts change, update Android `AGENTS.md` and the required desktop `AGENTS.md`/project `memory/MEMORY.md` within the approved implementation scope.

**Exit:** Every in-scope parity row is PASS with evidence; no silent feature omission, placeholder, data loss or mandatory mobile blocker. Report remaining external/live-provider limits explicitly. Do not call an incomplete transfer or device gate complete.

## 13. Validation plan

### 13.1 Test layers and commands

| Layer | Required evidence |
| --- | --- |
| Pure Kotlin | Identity, dates, coverage, weighted metrics, rounding, insight rules/targets and report mappings |
| Database | Actual Room query results, deterministic ordering, transactions/rollback, schema migrations and import transformations |
| Network contract | Exact scope/dimensions/intervals, provider failures and sanitized messages, using isolated HTTP fixtures |
| ViewModel | Scope changes, cancellation/stale results, refresh outcomes, restoration and preserved cache |
| Compose/device | All routes/tabs, detail and forms, semantics, large text, narrow windows, Back/IME/insets |
| Cross-platform | Same sanitized inputs and fixed clock produce the same approved report outputs |
| Live provider | Separately authorized/configured calls with site/time scope and outcome recorded; never inferred from mocks |
| Release | Build, signing path where authorized, upgrade/install behavior and minified runtime smoke |

During approved implementation use the smallest relevant module test task first, for example the existing pure model task `:core:model:test`. Resolve Android module task names from the actual configured variants; do not assume instrumentation infrastructure already exists. Add the minimum AndroidX test/Compose/Room test dependencies required for the selected checks, with explicit scope approval and version compatibility verification. Current instrumentation-runner declaration alone does not prove that UI or migration tests can run.

At final integration run the project's direct checks:

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat detektCheck
.\gradlew.bat ktlintCheck
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Run the configured targeted device/instrumentation suites after their harness is established. Record the exact task, device serial, API, width, font scale and nonzero test count. Zero tests, an ADB exit code, an APK build or a screenshot alone is not proof of interaction coverage. Do not execute the `lint-check`/`security-check` wrappers. Release build success does not prove signed installation; use the existing `tools/SignRelease.psm1` process only within separately authorized release validation.

### 13.2 Mandatory device/window matrix

Each case uses the **actual app window width** after system configuration, not an assumed physical screen width. Record effective dimensions, density and font scale; restore any changed device settings after validation.

| ID | Configuration | Required coverage |
| --- | --- | --- |
| M01 | 320 dp width, 100% font | Every report tab, site/period selectors, Page detail, source forms and confirmations |
| M02 | 320 dp width, 200% font | Same full coverage; no clipped/unreachable text, values or actions |
| M03 | Typical phone width 360–412 dp, 100% font | Full primary workflows and visual comparison |
| M04 | Typical phone width 360–412 dp, 200% font | All report/form families, wrapping and touch accessibility |
| M05 | Phone landscape / compact height | Header, chart/list scrolling, Back, bottom/rail navigation and IME |
| M06 | 600 dp and 839 dp windows | Rail transition, available content width, no premature two-pane layout |
| M07 | 840 dp and larger | Optional list/detail, pane minimum fit, resizing while detail is open |
| M08 | Split screen/resizing | Preserve site, period, provider, tab and detail state |
| M09 | API 28 and API 36 | Minimum/target-platform startup, navigation, storage/auth paths as available |
| M10 | TalkBack; gesture and three-button navigation | Reading/focus order, semantics, action labels, insets and Back |

Include empty, sparse, full and partially failed data; long domains/queries/URLs; large numbers; one and many sites; and dialogs with IME visible. M01 and M04 do not substitute for M02.

### 13.3 End-to-end acceptance journeys

| ID | Journey | Expected result |
| --- | --- | --- |
| J01 | Fresh install -> Sources -> configure/test -> explicit refresh -> Overview | No fabricated data; accurate configuration/progress/result; cached reports appear |
| J02 | Overview -> select site -> change period -> Top page -> Back | Correct scope/data and exact list-state restoration |
| J03 | Needs attention -> targeted provider/tab -> Back | Correct site/tab, actionable evidence, no automatic API call from navigation |
| J04 | Every Cloudflare tab -> refresh -> offline revisit | Metric-specific content and preserved cache |
| J05 | Google queries filter/sort -> pages/declines -> devices -> Indexing refresh | Correct aggregates and actual inspection operation |
| J06 | Bing queries/pages/crawl under All sites and one site | No identity collisions or unlabeled method/raw-data dump |
| J07 | CrUX metric/device -> PageSpeed device/URL -> manage measured URL | Accurate field/lab distinction and full path preserved |
| J08 | Health uptime -> failed probe -> sitemap refresh | Error is not zero latency or indexed proof; cached history retained |
| J09 | Import supported desktop snapshot -> restart offline | Exact supported records/metrics preserved; no desktop runtime dependency |
| J10 | Import cancelled/invalid/fails during storage update | Previous database/preferences/secrets remain consistent and usable |
| J11 | Rotation/process recreation during browsing and refresh | Restored non-secret context, readable cache and honest refresh outcome |
| J12 | 401/403/429/5xx/offline with multiple configured targets | Correct safe per-target outcome; successful siblings and failed-target cache retained |
| J13 | Manual refresh while periodic work is eligible | Serialized/coalesced work; no overlapping replacement or stale success status |
| J14 | Upgrade schema-1 installation with GA4 history and stored settings | No silent data/credential loss; retired-source status and current reports usable |
| J15 | Background/resume -> cold start without authentication | App stays unlocked; sensitive forms/credentials do not leak through restoration |

Run J01–J08 and relevant forms/details at M01–M04; use the same reusable screen cases rather than four unrelated partial smoke tests. Run lifecycle/import/network journeys on suitable representative configurations plus narrow/large-text error/confirmation screens.

### 13.4 Evidence recording

For each parity requirement record: desktop source/reference hash, fixture/scenario, Android screen/operation, expected result, actual result, exact check and evidence path, and verdict `PASS`, `FAIL`, `BLOCKED` or `NOT RUN`. Keep runtime screenshots/logs and private evidence under ignored `reports/`; commit only sanitized fixtures/tests when a commit is explicitly requested.

Visual evidence must pair semantic components rather than compare entire desktop/mobile screens pixel-for-pixel. Inspect palette, hierarchy, density, chart meaning, readable labels and interaction reachability. A screenshot proves appearance in that state; a test/action trace proves behavior. Neither proves provider correctness without matching data evidence.

## 14. Risks and decisions to settle during implementation

| Risk / dependency | Handling |
| --- | --- |
| Desktop reference changes while Android is being ported | Pin source/hash per contract; review deltas before accepting the affected feature |
| Historical documentation asserts more parity than source provides | Current code and executable fixtures override old completion claims |
| Windows export is not available or differs from Android's expected package | Resolve exporter/format explicitly in P0; standalone setup continues, transfer cannot be called complete |
| GA4 retirement could delete user data | Preserve records/credentials on upgrade; separate explicit removal actions |
| Provider record windows/retention differ | Label coverage; compare same snapshot/window; do not claim unavailable history exists |
| OAuth configuration/credentials are unavailable | Mock/request tests prove local behavior only; live authorization remains BLOCKED until configured |
| Vico lacks a required visual/data behavior in the declared version | Verify capability with a focused fixture; only then propose the smallest compatible adaptation |
| Large fonts break navigation or KPI grids | Validate shared components at M02 before broad screen implementation; stack/menu fallback |
| Database success but secrets/preferences import fails | Test multi-store recovery and process interruption; no premature success message |
| Existing dirty check/build configuration changes | Preserve byte contents; keep task changes identifiable; report unrelated failures separately |
| Source-only desktop behavior contains an unresolved defect | Document the discrepancy; resolve the contract rather than copy a bug or change Windows silently |

No extra recommendation here authorizes a migration, dependency upgrade or desktop edit. At implementation time, ask only about unresolved choices that materially change scope; routine implementation details should follow the approved contracts and existing patterns.

## 15. Definition of done

- [ ] All seven current desktop destinations and every tab/action in section 6 have an Android location and acceptance evidence.
- [ ] Desktop Numeris identity is recognizable in actual Android rendering, with mobile-appropriate structure and controls.
- [ ] Overview contains real portfolio data, Insights, Sites and Top pages; Page detail is complete.
- [ ] Google, Bing, Cloudflare, Web Analytics, CrUX, PageSpeed and Health preserve their distinct meanings and correct aggregation.
- [ ] Identical fixtures produce matching calculations, coverage, missing states, sorting, latest records, formatting and insight decisions.
- [ ] Existing Android records/preferences/secrets survive supported upgrade paths; any schema change has tested migration coverage.
- [ ] Current desktop transfer is supported and proven, or remains explicitly incomplete rather than hidden behind a working file picker.
- [ ] Independent Android setup, refresh, offline browsing and authorization work without a running desktop application.
- [ ] 320 dp, 200% font scale and their combination pass the required screen/form/action checks.
- [ ] Back, recreation, IME, TalkBack, window resizing, partial failures and background/manual sync behave as specified.
- [ ] Required direct checks and meaningful device tests ran; blocked/unrun external checks are reported accurately.
- [ ] No placeholder analytics, silent truncation of required desktop information, retired-provider acquisition, secret exposure or unapproved destructive migration remains.
- [ ] Documentation reflects the actual final implementation, and temporary processes/device configuration changes from validation are cleaned up.

## 16. Status of this planning deliverable

This file is the only intended content change made for the planning request. The Android feature branch was created to comply with repository rules; existing modified files were left untouched. Neither repository's application code, dependencies, database, credentials or configuration was intentionally changed.

The implementation phases, tests, screenshots, live-provider checks and performance measurements described above are **planned**, not completed. Before implementation, approve the proposed scope under the Android project rules; there is no separate approval needed to read and review this plan.
