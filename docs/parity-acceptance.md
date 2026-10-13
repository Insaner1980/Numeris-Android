# Android–desktop-pariteetin hyväksyntä, 2026-10-11

Androidin toteutus on tehty suunnitelman P0–P9:n Android-osuuksien mukaan. Koko suunnitelman hyväksyntä on **avoin**. Windows-vienti ja yhteys-/avainsiirto on nyt toteutettu ja testattu API 28/36:lla; allekirjoitettu release on Pixelissä. Alla oleva jatkotarkistus kertoo tuoreemman näytön. Esimerkkikuvat `docs/design-preview` ohjasivat ulkoasua; ne eivät ole desktopin runtime-todiste.

## Jatkotarkistus ja kaikkien Windows-tietojen siirto

`EC/` = ignored `reports/parity-completion-20261011/`. Vanhan `E/`-ledgerin havainnot ja epäonnistumiset säilytetään historiallisina; tämä osio syrjäyttää niiden nykytilaa koskevat väitteet vain alla nimetyssä laajuudessa.

- Windows-referenssin tämänhetkinen haara on `codex/numeris-report-fixes`, HEAD `c97438684d5f845bd64da52cf7403d4e3c1442df`. Numeeristen referenssien 66 tiedostoa ovat muuttumattomat alkuperäisestä vertailusta (`EC/reference-drift.json`); Sources-vientitoiminto lisättiin jatkotyössä. Alkutila on `EC/baseline.json`.
- Native WinUI -harness kuvasi kaikki seitsemän sivua, niiden tabit sekä Sources-lomakkeet tyhjänä ja synteettisesti täytettynä. `EC/windows-native-capture-2.txt` PASS. RenderTargetBitmap-kuvat todistavat XAML-sisältöä; ne eivät ole koko ikkunan compositor-kuvia. Kaikkien semanttisten kuvaparien katselmointi on vielä avoin.
- Desktopin varsinainen `AndroidReportExporter` tuottaa raportti- ja täyden yhteyssiirron fixture-paketit. `EC/desktop-full-transfer-build-2.txt` ja `EC/desktop-full-transfer-fixture-2.txt` PASS. Vientisopimus, salaus, redaktointi, raporttien rajaus, yhteysavaimet ja asetukset tarkistettiin.
- Android `test lint detektCheck ktlintCheck assembleDebug :app:assembleDebugAndroidTest assembleRelease --no-daemon`: `EC/android-all-checks-full-transfer-final-3.txt` BUILD SUCCESSFUL, 2m 52s, 1 142 taskia. Ei lint-check/security-check-wrappereita. Myöhempien testiharness-muutosten omat tarkistukset kirjataan erikseen.
- Todellisilla Windows-fixture-paketeilla API 36 ja API 28: `EC/api36-full-transfer-matched.txt` / `EC/api28-full-transfer-matched.txt`, molemmissa **OK (9 tests)**. Täysi tuonti kirjoittaa raportit/yhteydet Roomiin, avaimet SecretStoreen ja raporttivalinnat DataStoreen. Testit todistavat myös epäonnistuneen SQLite-commitin palautuksen, keskeytysinjektion palautuksen uudella repositoryllä ja plaintext-avaintiedostojen puuttumisen. Keskeytysinjektio ei ole OS-prosessitappo.
- API 28 koko sovelluksen report/Room/import/shell-ajo: `EC/api28-complete-current.txt`, **OK (22 tests)**, 269,071 s. Käynnistys tapahtui normaalissa Android-järjestelmässä, ei minimal-framework-tilassa. API 28:n PIN-fallback korjattiin ja oikea/väärä PIN sekä cold-start kokeiltiin eristetyllä emulaattorilla (`EC/api28-lock-*.json`). Vanha credential-only FAIL säilyy alkuperäisenä havaintona.
- Ensimmäinen uusien testien ajo asennettiin virheellisesti vanhan app-APK:n päälle. `EC/api36-full-transfer-tests.txt` / `EC/api28-full-transfer-tests.txt` sisältävät ABI/class-load-virheet; ne eivät ole toiminnallinen siirtotulos. Molemmat paketit päivitettiin ennen yllä olevia yhdeksän testin läpäisyjä. Alkuperäiset lokit säilyvät.
- Uusi release-avain on käyttäjän luvalla luotu suojattuun `%USERPROFILE%\.numeris\signing`-hakemistoon. `EC/release-full-transfer.apk` SHA-256 `DC995D1D5E36C411F81A5AF2A11006AF03DFF68394AA2ED0B9ED7DA7B7A3B49C`; allekirjoitus verifioitu. Pixel 9 / API 37 päivitettiin `pm install -r`:llä ja puhelimen `base.apk`-hash vastaa tätä tiedostoa. Henkilökohtaisesta puhelimesta ei poistettu sovelluksia/dataa eikä siihen asennettu instrumentaatiotestipakettia.
- Käyttäjän nimenomainen ”siirrä kaikki tiedot” valtuutti koko tallennetun Windows-aineiston ja API-avainten viennin. SQLite avattiin **read-only**, ilman migraatioita. `EC/saved-windows-export.json`: lähde-DB:n ja WAL:n SHA-256:t säilyivät. Aineistossa on **125 255 tuettujen raporttitaulujen riviä** (`EC/source-row-counts.json`); desktop-only Play-taulut ovat tyhjiä eikä GA4-tauluja ole. Paketti ja sen DPAPI-suojattu siirtosalasana säilytetään yksityisessä `%USERPROFILE%\.numeris\transfers`-hakemistossa.
- **Varsinainen Pixel-tuonti onnistui käyttäjän biometrisellä vahvistuksella.** `EC/pixel-transfer-sources-after.json`: **125 255 raporttiriviä, 10 yhteyttä ja 9 API-avainta**. Roomin raportit avautuivat Overview- ja Search-näkymissä (`EC/pixel-imported-overview-settled.json`, `EC/pixel-search-knit-before.json`). Google OAuth -salaisuuksia ei siirretty.
- Ensimmäinen todellisen aineiston paketti hylättiin turvallisesti ennen tuontia: 124 PageSpeed-raakavastausta ylitti tuontirajan sisäkkäisen JSON-escapoinnin vuoksi. Viejän rajaus korjattiin mittareita ja audit-rivejä muuttamatta. `EC/desktop-bounded-export-build.txt` ja `EC/desktop-bounded-export-fixture.txt` PASS; `EC/saved-transfer-validation.json`: kaikki päivät ja raakavastausten rajat kelvollisia. Alkuperäiset havainnot ovat `EC/saved-transfer-validation-initial.json`. Tuodun paketin SHA-256 on `C59E1280D0D18A8E7FAE9B30DFA0BE9C6941DF169027D1995C3DDB48E04CDA0B`.
- Viimeisin yhteinen **320 dp / 200 %** API 36 -ajo: `EC/api36-320dp-200-acceptance-final.txt`, **OK (18 tests)**, 170,849 s. Se kattaa MobileReports/Shell-tuotepolut sekä Windows-tuonnin, palautuksen ja WorkManager-kilpailun. Aiemmat kaksi kahdeksan testin epäonnistumista säilyvät omissa lokeissaan; async-selectorin odotus korjattiin ennen läpäisevää ajoa.
- API 28:n päivitetty rajaus-/siirtopaketti ja oikea WorkManager-ajo: `EC/api28-bounded-transfer-final.txt`, **OK (10 tests)**, 44,337 s. PeriodicManualSyncTest käyttää oikeaa WorkManagerin periodista työtä ja NumerisSyncWorkeria, fake-provideria sekä manuaalista orkestrointia; saman pyynnön yhdistyminen ja erilaisten pyyntöjen sarjallistuminen todistettiin ilman live-pyyntöjä.
- **Allekirjoitetun version päivitys nonempty-aineistolla ja offline-käynnistys onnistuivat omassa API 28 -emulaattorissa.** Vanha B28-release päivitettiin nykyiseen releaseen `install -r`:llä. `EC/signed-upgrade-receipt.json`: schema 1→1, CF 14 riviä, GA4 1 historiarivi, 2 yhteyttä ja kaikki taulurivit muuttumattomat. Vanhan releasen UI:lla tallennettu synteettinen API-avain säilyi salattuna täsmälleen samoina tavuina; tämä hash-tarkistus ei yksin todista uuden releasen avaimen purkua. Sivusto ja periodi palautuivat (`EC/api28-upgrade-restored-search.json`).
- Pixelin rajattu Overview-siirtymän gfxinfo-otos: `EC/pixel-gfx-imported-overview.txt`, 198 frameä, 1 janky frame (0,51 %), 95. persentiili 6 ms. Tämä ei todista koko suuren listan/chartin tai warm/cold-startin suorituskykyporttia.
- Live-luku on edelleen rajattu käyttäjän nimeämiin knittoolsapp.comin Cloudflare-, Google Search Console- ja Bing-yhteyksiin. Muut siirretyt sivustot/avaimet eivät valtuuta live-testejä. Pixelin Numeris-periodic sync poistettiin testin ajaksi käytöstä ennen tuontia. Desktopin Google OAuth -client secret/refresh tokenia ei viedä; Android vaatii oman kirjautumisen. Numeris MCP:tä ei käytetty.

Jäljellä: live/OAuth, fyysisen laitteen täydellinen suorituskykyportti, täydellinen TalkBack-action-ketju sekä alla erikseen avoimet kuvaparit/F/J-tapaukset. Käyttäjän Google-tilin valinnan jälkeen knittoolsapp.comin Search Console -päivitys ilmoitti vielä authorization required (`EC/pixel-google-refresh-initial.json`); sen syytä selvitetään. Siirtotestien ja uuden API 28 -ajon PASS ei yksin sulje muita portteja.

## Referenssi ja rajat

- Android-haara `codex/android-desktop-parity-plan`, lähtö-HEAD `c0d723ef84c56d224fb2d0ea3f0ecf2cb66f3da8`.
- Desktop-haara `codex/numeris-installer-1.0.0`, HEAD `c973201847b18e4fb044c21d64eec04f32ff5b0f`. Nykyiset, myös valmiiksi muokatut lähteet ovat referenssi. Tiedostokohtaiset SHA-256:t: [desktop-source-hashes.json](../test-fixtures/parity/desktop-source-hashes.json).
- Suunnitelman W01–W10-tunnukset tarkoittavat sen lähdekarttaa. Alla jokaisen rivin referenssi sidotaan tähän hash-listaan; W06/W07 tarkentuvat kyseisen providerin ViewModeliin/repositoryyn.
- Ei tuotannon testidataa, käyttäjän analytics-lukuja, live-provider-pyyntöjä, committia, pushia tai PR:ää. Numeris MCP:tä ei käytetty. Kaikki ajettava data on eristettyä synteettistä fixture-dataa.
- Room-skeema pysyy versiona 1, alkuperäinen schema-JSON muuttumaton. GA4-historia, asetukset ja salaisuudet säilyvät; GA4 poistui aktiivisesta navigaatiosta ja hankinnasta.
- Kotlin, AGP, Gradle, Compose, Room, Nav3, nykyinen käsin koottu DI ja SDK-rajat säilyvät. Lisäykset rajoittuvat instrumentaatiotestin riippuvuuksiin ja AndroidSQLiteDriverin tarvitsemaan SQLite-frameworkiin; strict dependency verification säilyy.
- Desktopiin lisättiin vain ohjeiden vaatimat integraatiomuistiinpanot `AGENTS.md`:hen ja projektin `memory/MEMORY.md`:hen. Alkuperäiset dokumenttibytes sekä kaikki valmiiksi muokatut sovellus-/testitiedostot säilytettiin.

## Todisteavain

`E/` = ignored `reports/parity-20261011/`. Lokit ja kuvat ovat paikallisia; julkaistavia fixturejä ei ole vielä commitattu.

| Avain | Tarkistus ja merkitys |
| --- | --- |
| B | `gradlew.bat test lint detektCheck ktlintCheck assembleDebug assembleRelease :app:assembleDebugAndroidTest --no-daemon`; `E/final-gradle-checks-28.txt`: BUILD SUCCESSFUL, 3m, 1 142 taskia. Testiharnessin viimeinen tarkistus: `E/final-test-harness-30.txt`, BUILD SUCCESSFUL, 49s. Ei lint-check/security-check-wrappereita. |
| U | `E/unit-test-receipts.json`: 14 suiteä, 39 testiä, 0 failure/error. Pure model, network, security ja data; XML-polut receiptsissä. |
| R | Nykyisten muuttamattomien C#-helperien 28 tapausta, InsightEnginen 18 skenaariota / 21 tulosriviä sekä PortfolioRepositoryn 7 yhteisen SQLite-fixturen tapausta. `E/desktop-reference-final.txt`, [fixture-kuvaus](../test-fixtures/parity/README.md). Kotlin DesktopReferenceTest/DesktopInsightTest ja Room DesktopPortfolioTest vertaavat samoja odotuksia. |
| D | Debug-instrumentaatio, API 36 `emulator-5560` ja API 28 `emulator-5562`; tarkat kokoonpanot ja testimäärät matriisissa. Testit eivät käytä käyttäjän laitetta/tietokantaa. |
| C | MobileReportsTest: kaikki report-view-perheet, 1 000 queryä ja 1 000 pageä, 2 sivustoa / 365 päivää, valitsimet, 8 lähdelomaketta, URL-poiston vahvistus ja tekstisyöte. Fixture sisältää myös populated Bing/status/breakdown/audit/page-query -rivit. Glyph-rajojen tarkistus on kalibroitu tahallisesti leikkautuvalla tekstillä; parent-shape-clipping tarkastetaan lisäksi kuvista. Oikea IME todistetaan erillisessä manuaaliajossa, ei pelkällä performTextInputilla. |
| L | ShellJourneyTest: oikea MainActivity, site/30-day selection, Page detail → recreate → Back samaan listakohtaan; query-filtteri navigoinnin/recreaten yli; Insight → tarkka Indexing-tab → järjestelmä-Back; Health/Sources → More; metadataluonnos säilyy ja salasana tyhjenee. Sync-tila säilyy mitattuun lähtötilaan nähden (ei oleteta Idleä aiemman tausta-ajon jälkeen). |
| I | ImportContractTest/ImportRecoveryTest: schema 11 ilman GA4:ää, Android-konfiguraation ja GA4-rivin säilytys, väärä versio/sarakkeet/rivimäärä/lippu/päivä/duplikaatti, salattu input, cancel/restart-plaintext-cleanup sekä injektoitu SQLite-keskeytys ja rollback. |
| S | DefaultSyncOrchestratorTest/SyncTargetsTest ja ProviderContractTest: configured-rajaus, coalescing, yhteinen mutex, cancel, onnistuvat sisar-targetit, tarkat mock-HTTP-dimensiot/dates/IDs ja 401/403/429/5xx-redaktointi. Ei live- tai WorkManager-kilpailutodiste. |
| P | `E/preservation-receipts.json` ja `E/preservation-final.json`: alkuperäiset Androidin sivutiedostot, yhteisen app/build.gradle.kts:n käyttäjämuutokset ja desktopin likainen työ säilytetty; schema 1:n SHA-256 `62b784f662f69fecf0fdd105968579098c6a82fc117a1f0689edbd2805ae3f0d`. |

PASS tarkoittaa rivillä nimettyä ja rajattua tarkistusta. BLOCKED tarkoittaa puuttuvaa ulkoista riippuvuutta/valtuutettua live-konfiguraatiota. NOT RUN tarkoittaa suorittamatonta vaadittua näyttöä. Yhden osatarkistuksen PASS ei muuta koko porttia PASSiksi.

## P0–P9: vaiheiden kokonaisportit

| Vaihe | Toteutettu / paikallinen näyttö | Portti ja puuttuva näyttö |
| --- | --- | --- |
| P0 | HEAD/hash-jäädytys, hyväksytty Android-scope, inventory alla; All sites Indexing vaatii sivuston valinnan; GA4 säilytetään | NOT RUN: desktopin oikeat runtime-vertailukuvat puuttuvat; exporter todettu puuttuvaksi |
| P1 | Dated/presence/coverage-mallit, ReportRepository-lukuraja, painotukset, portfolio, identity ja fixturet; U/R/D/I | NOT RUN: kaikkien F01–F15-tapausten täydellinen molempien alustojen vertailu |
| P2 | Tumma token-järjestelmä, typed Nav3, saveable tab/detail/filter-state, leveys/font-adaptaatio; C/L | Laitematriisin ja resize/TalkBack-portin tila alla |
| P3 | Overview, oikeat Sites/Top pages, 11 insight-sääntöä/targetit, Room-only Page detail; R/C/L | NOT RUN: koko desktop-runtime-paritus; paikalliset insight-tap/Back-polut L PASS |
| P4 | Viisi erillistä CF/Web Analytics -view'tä, painotetut ratio/bytes/threat/status-series; U/C | BLOCKED: live-refresh ja oikean palvelun offline-revisit |
| P5 | Google 5 / Bing 4, query/page-sort/filter, comparison-gated discoveries, device/index/crawl; C/U/S | BLOCKED: live OAuth ja URL Inspection |
| P6 | CrUX/PS URL/device/metric-erottelu, 4 kategoriaa, uptime/sitemap; D/C/U | BLOCKED: oikean providerin refresh/failed-probe/sitemap-journey |
| P7 | Source-kohtaiset lomakkeet/discovery/targets, salaisuuksien rollback, URL CRUD, sisäinen schema-11-reader; C/I/D | BLOCKED: Windows-exporter puuttuu; transfer-UI on selkeästi estetty |
| P8 | Serialized/coalesced configured-sync ja import/connection/indexing yhteinen gate, cache-preservation; S/D/L | NOT RUN: oikea periodic/manual/process-interruption-kilpailu ja live-auth-recovery |
| P9 | B/U/R, laitematriisi, nykyiset dokumentit ja säilytystodisteet | NOT RUN: avoimet M/J/F-portit, suorituskykymittaus ja signed/minified runtime |

## G01–G20: löydösten toteutusledger

| ID / referenssi | Android-toteutus ja odotuksen toteutuminen | Tarkistus / tila |
| --- | --- | --- |
| G01 W02 | NumerisColors/Theme/Tokens: #111114/#1A1B20/#F56E0F, samat semanttiset Material/Vico-värit | B/C + kuva-arvio: PASS paikallinen; desktop-runtime-paritus NOT RUN |
| G02 W04 | OverviewScreen: visitor-hero, coverage, oikeat series ja yhteenvetomittarit | R/U/C: PASS |
| G03 W04–07 | Oikea Vitals, kaikki configured/cached Sites, portfolio Top 10 | R/D/C: PASS |
| G04 W05 | PageDetailScreen: URL, mittarit, history, query 8, inspection, matching PS ja Sources-prefill; ei fetchiä | R/D/C/L: PASS |
| G05 W06 | Traffic Security käyttää threat-dataa, cache/status omia mittareita | U/C ja TrafficMappings: PASS |
| G06 W06 | CF countries/pages, Web countries/pages/referrers päivättyinä DTO:ina | C + ReportReader/TrafficMappings: PASS paikallinen; live-fetch BLOCKED |
| G07 W06–08 | SearchMappings/Discoveries: oikeat daily-periodit, painotus, sort/filter/comparison | U/C/S: PASS paikallinen |
| G08 W06 | PerformanceMappings/Screen: exact URL/device/metric/latest, field/lab, yksiköt | PerformanceScopeTest/C/U: PASS |
| G09 W06–07 | HealthReportScreen: kaikki-history-percentage erillään periodin response/incident-seriestä | ReportPersistenceTest/U/C: PASS |
| G10 W08–09 | MetricWindow/presence/coverage ja complete-period-delta | ReportMathTest/R: PASS |
| G11 W09 | InsightEngine: 11 sääntöä, 5xx ≥10 / critical ≥1 %, structured target, max 4 | DesktopInsightTest/R/L: PASS; local Needs attention → Indexing → Back testattu |
| G12 W10 | GA4 ei aktiivista UI/synciä, vanhat records/config/secrets säilyvät | I/S + source inspection: PASS paikallinen |
| G13 W10 | WindowsDatabaseImporter schema-11 mapping ei edellytä GA4-tauluja | I: PASS reader; real transfer BLOCKED |
| G14 W03 | Normalisoitu configured + kaikkien relevanttien cache-identiteettien union | ReportPersistenceTest: PASS |
| G15 W06 | Traffic/Search/Performance/Health-rendererit omissa feature-moduuleissa | B + import/dependency inspection: PASS |
| G16 W07 | core:model ReportRepository-rajapinta; UI ei DAO/database/network/SecretStore-tuloksia | B + source inspection: PASS |
| G17 W07–08 | Täydet site/URL/device/date-keyt ja päivätyt series | R/U/D/C: PASS |
| G18 W03 | Typed routes, last root, saveable tabs/filters/detail; Back-listasäilytys | L: PASS recreate; prosessitappo/resize alla |
| G19 W10 | All/group/source configuredonly + Indexing shared gate ja valittu site | S/L: PASS paikallinen; live/periodic-race BLOCKED/NOT RUN |
| G20 W06 | Provider-lomakkeet, discovery/mappings/targets ja oma token; secure native Google-auth | C/D/S: PASS paikallinen; live source/OAuth BLOCKED |

## §6: ruutu- ja toimintoinventory

Jokainen rivi kuvaa desktopin W04–W07/W09/W10-vaatimusta, Android-sijaintia ja paikallista todistetta. B läpäisee kaikkien moduulien build/style/lint-tarkistukset. Pienet jäljellä olevat hyväksyntäaukot eivät ole piilotettuja ominaisuuksia.

| ID / desktop | Android-sijainti: vaadittu sisältö / toiminto | Näyttö |
| --- | --- | --- |
| O01 W04/W07 | Overview: All sites, site/period/refresh/freshness; configured-only/first-run/import-cache | D/U/C; live-refresh BLOCKED |
| O02 W04/W08 | Cloudflare visitor-hero, current/previous/gaps, valid delta/coverage | U/R/C: PASS |
| O03 W04/W07 | Google+Bing clicks/provider breakdown; indexed/inspected/active; per-site all-history uptime mean | U/R/D/C: PASS |
| O04 W04/W09 | Evaluated vs insufficient Insights, evidence/severity/next action, exact structured target | R/C/L: PASS content sekä local target touch/Back |
| O05 W04/W05 | Sites visitors/domain-sort, change/sparkline/search/vitals/PS/uptime, toggle-select; Top 10 desktop-order | R/C: PASS content; site-toggle touch journey NOT RUN |
| P01 W05 | Page identity/path/full URL copy, views/G+B clicks/weighted position | R/D/C: PASS |
| P02 W05/W08 | Traffic/search dated comparison/coverage, latest stored query period, dates+fields+8-limit | R/U/C: PASS |
| P03 W05/W07 | Saved inspection/status/time, exact-URL/device PS, untracked→Sources-prefill | D/C: PASS content; copy/prefill touch journey NOT RUN |
| T01 W06 | Traffic: visitors/views/pages per visit/deltas/coverage/daily/countries/pages | U/C: PASS local |
| T02 W06 | Cache: weighted hit ratio/cached requests/bytes saved/daily units | U/C: PASS local |
| T03 W06 | Security: actual threats/daily threats | C + mapping inspection: PASS local |
| T04 W06 | Status codes: success/4xx/5xx/share/top issue/groups/code meanings/disclosure | C + mapping inspection: PASS local |
| T05 W06 | Web Analytics: own visits/views/history/referrers/pages/countries | C/S + mapping inspection: PASS local |
| S01 W06 | Google Overview: clicks/impressions/weighted CTR+position/delta/coverage/series | U/C/S: PASS local |
| S02 W06 | Google Queries: period filter/sort 4 metrics, New queries only complete comparison, full expand | C/U: PASS local; 1 000 rows reached |
| S03 W06 | Google Pages: aggregates/declines/current+previous/change/full identity | C/U: PASS local; 1 000 rows reached |
| S04 W06 | Google Devices: readable labels, daily device/share | C/S: PASS local |
| S05 W06 | Google Indexing: active/inspected/indexed/status/time; All sites explanation requires site | U/C/S: PASS local; actual Inspection BLOCKED |
| S06 W06 | Bing Overview: clicks/impressions/CTR/coverage/comparison/history | C/U: PASS local; populated live data BLOCKED |
| S07 W06 | Bing Queries/Pages: period aggregation/sort/clicks/impressions/CTR/position/query/path | U/C + mapping inspection: PASS local; populated All sites UI kahdella sivustolla |
| S08 W06/W10 | Bing Crawl: 6 bounded methods, summaries/time/issue/safe detail, true API dates | U/S/C + mapping inspection: PASS local; populated stats/issues UI |
| V01 W06 | Vitals Overview: actual CWV, mobile/desktop PS rating, current metrics/latest | D/C: PASS local |
| V02 W06 | CrUX: metric/formfactor/URL-or-origin/p75/history/collection-window/units | PerformanceScopeTest/U/C: PASS local |
| V03 W06 | PageSpeed: exact URL/device/history/time, 4 categories, audits/runtime/warnings | PerformanceScopeTest/C: PASS local; populated render-blocking audit UI; warning-case UI NOT RUN |
| V04 W06 | URLs: full normalized identities, enabled/source, Manage URLs→Sources | C/D: PASS local CRUD/confirmation; route-touch NOT RUN |
| H01 W06/W07 | Uptime: latest status/time/HTTP/response, history %/failed probes, selected-period chart/incidents | U/D/C: PASS local; live failed probe BLOCKED |
| H02 W06/W07 | Sitemap: active/removed/history/last seen/inspection separate populations, active-tab refresh | U/C + sync inspection: PASS local; live refresh BLOCKED |
| C01 W06 | CF Zone form: domain/Zone ID/token, saved/new test/disconnect/cache retention | C/ConnectionIntegrityTest/S: PASS local; real test BLOCKED |
| C02 W06 | CF Web form: account/token, discovery host/site-tag add/remove, per-connection status | C/ConnectionIntegrityTest/S: PASS local; real discovery/test BLOCKED |
| C03 W06 | Google native authorize, property/domain mapping, reconnect/test/disconnect | C/S: PASS local; OAuth BLOCKED |
| C04 W06 | Separate CrUX/PS keys/status, measured URL add/enable/disable/remove/confirmation | C/D: PASS local; provider test BLOCKED |
| C05 W06/W10 | Bing key/test/disconnect/discovered+configured sites/add/remove | C/S: PASS local; provider discovery/test BLOCKED |
| C06 W10 | Encrypted import reader preview/replace/rollback; UI visibly disabled pending exporter | I: PASS reader; whole transfer BLOCKED |
| C07 W06/A06 | Opaque scroll+IME forms, human field labels, empty-token keeps stored, secret wiped on recreation | C/L/D: PASS local |
| A01 W03/A05 | More: Health/Sources, lock, approximate periodic sync 6/12/24 h | L/C + source inspection: PASS UI; lock/runtime below |

## F01–F15: fixture-ledger

Tila koskee rivin täydellistä cross-platform-vaatimusta. Android-osatesti mainitaan erikseen; NOT RUN ei tarkoita, ettei toteutusta tai paikallista testiä olisi.

| ID / referenssi | Fixture ja toteutunut tarkistus | Täyden vertailun tila |
| --- | --- | --- |
| F01 W03/W07 | ReportPersistenceTest configured-only/search-only + tyhjät C-ruudut | NOT RUN: sama desktop domain/empty-state runner puuttuu |
| F02 W07/W08 | Shared portfolio full 7+7, 7 C#-caseä; ReportMath periodit; C365päivää | NOT RUN: täydellinen 30+30 kaikkien providerien vertailu puuttuu |
| F03 W05/W07 | Shared SQLite 2 sites/same paths + site-order/joins; C2sites/1krows | PASS rajattu yhteinen Portfolio-output, muut report-aggregaatit NOT RUN |
| F04 W08 | ReportMath missing-day/expected-sites complete-flag + chart gaps | NOT RUN: sama full report-output desktopilla puuttuu |
| F05 W08/W09 | Shared C# Trend/Insight zero/missing/new; Kotlin presence asserts | PASS laskentasäännöissä R/U; kaikki UI-empty/zero yhdistelmät NOT RUN |
| F06 W07/W08 | Shared portfolio unequal impressions weighted position, ReportMath unequal requests/CTR/cache | NOT RUN: shared desktop cache/CTR-kaikkien reportien ajo puuttuu |
| F07 W08 | Shared SiteIdentity/DisplayFormat + Kotlin exact path/query/case/trailing slash | PASS R/U identity; täydellinen kaikkien providerien join NOT RUN |
| F08 W05 | Shared portfolio provider-only/missing/samepath; Android null-presence vs desktop DTO zero documented | PASS R/D rajattu Portfolio-contract |
| F09 W06/W07 | PerformanceScopeTest mixed URLs/mobile/desktop & CrUX windows/formfactors; exact latest and history | NOT RUN: sama desktop runner puuttuu; Android D PASS |
| F10 W07 | ReportMath active/removed/uninspected/indexed denominators | NOT RUN: sama desktop runner puuttuu; Android U PASS |
| F11 W09 | 18 exact C# InsightEngine scenarios / 21 output rows, all 11 rules/boundaries/missing/order/top4/target | PASS R/U |
| F12 W07 | Android historical 601 probes + timeout/failed/success chart contract | NOT RUN: sama desktop fixture runner puuttuu; Android D/U PASS |
| F13 W10 | schema11.sql noGA4, Android preserved config/history, encrypted internal package | BLOCKED: real Windows-exporter absent; I PASS reader |
| F14 W08/W09 | 28 C# helper reference cases include midpoint/large/negative; Kotlin BigDecimal no Int overflow | PASS R/U rajattu helper-contract |
| F15 W10 | SyncTargets siblings/cancel + real Room rollback injection + secret rollback | NOT RUN: full desktop/provider partial-failure same snapshot puuttuu; S/I/D PASS local |

## M01–M10: laite- ja ikkunamatriisi

Owned emulaattorit, debug-build. API 36 density 320 dpi; alla 640 px vastaa 320 dp:tä. MobileReportsTest tallentaa todellisen Compose LocalConfiguration-leveyden/fontin ja PNG:t nimillä `parity-<dp>-<font>`. Tallenteet sisältävät initial/scrolled näkymiä. Testi ei yksin todista kaikkia E2E-journeyitä eikä oikeaa TalkBackia.

| ID | Toteutunut kokoonpano / tarkistus | Tila |
| --- | --- | --- |
| M01 | API36 640×1422, 320dp/100%; kaikki 18 instrumentaatiotestiä; `E/device-M01-context-final.txt` | PASS 18/18 nimetty testiscope; J01–J08 live-scope erikseen |
| M02 | API36 640×1422, 320dp/200%; MobileReportsTest + ShellJourneyTest; `E/device-M02-context-final.txt` | PASS 8/8 |
| M03 | API36 800×1778, 400dp/100%; MobileReportsTest + ShellJourneyTest; `E/device-M03-context-final.txt` | PASS 8/8 |
| M04 | API36 800×1778, 400dp/200%; MobileReportsTest + ShellJourneyTest; `E/device-M04-context-final.txt` | PASS 8/8 |
| M05 | API36 1280×640, 640×320dp/100%; MobileReportsTest + ShellJourneyTest; `E/device-M05-context-final.txt` | PASS 8/8; oikea IME erillisessä osanäytössä alla |
| M06 | API36 1200/1678×1778, 600/839dp/100%; MobileReportsTest + ShellJourneyTest; `E/device-M06-600-context-final.txt`, `E/device-M06-839-context-final.txt` | PASS 8/8 kummassakin; 839 dp:n historiallinen fixture-virhe erikseen |
| M07 | API36 1680×1778, 840dp/100%; MobileReportsTest + ShellJourneyTest; `E/device-M07-context-final.txt`; oikean MainActivityn rail `E/layout-api36-final-840.json`, `E/api36-final-840.png` | PASS 8/8 ja rail-kuva; resize M08:ssa; two-pane on suunnitelmassa optional |
| M08 | API36: Search Google/Queries/journey.com/30d/needle sekä Page detail URL/period 320→840→320 dp; Back samaan Overview-listakohtaan. API28: vastaava Search-resize. `E/layout-api36-resize-query-*.json`, `E/layout-api36-detail-resize-*.json`, `E/layout-api28-resize-search-*.json` | PASS rajattu resize; ei kaikkien tabien state-yhdistelmien näyttö |
| M09 | API28 aiempi normaalin OS:n 17/17 (`E/device-api28-before-legend-fix.txt`). Uudempi APK: 16/17 + korjatun odotuksen erillinen ShellJourney 3/3 (`E/device-api28-final.txt`, `E/device-api28-shell-final.txt`). Vanha laite jäi CryptKeeper/minimal-framework-tilaan. Lopullisen APK:n uusi puhdas fixture-laite jäi ADB-offline-tilaan yli 6 minuutiksi (`E/api28-clean-boot-receipt.json`); APK:ta ei asennettu. | PASS vain aiemmat nimetyt yksittäiset report/Room-tapaukset. Viimeiset sivustovalitsin-/pistevalintalisäykset API28:lla NOT RUN; normaali OS-startup uudella laitteella BLOCKED. Auth FAIL alla |
| M10 | TalkBack 16 bound, touch exploration enabled ja vihreä kohdistus todettu Sources/More-kuvissa; erillinen 3-button-näkymä ja sen Back→More. `E/talkback-*-bound.txt`, `E/talkback-native-back-settled.png`, `E/api36-threebutton-sources.png`, `E/layout-api36-threebutton-back.json` | PASS vain käynnistyminen/kohdistus ja 3-button Back/insets; täydellinen puhuttu lukujärjestys ja ele-/focus-action-ketju NOT RUN |

API36:n lopullisten kokoonpanoreceiptien summa on 74 läpäissyttä testisuoritusta (18 + 7 × 8), 18 erillistä testiä. Jokaisesta kahdeksasta kokoonpanosta kopioitiin 57 fixture-kuvaa, yhteensä 456. Sovellus-APK on sama B28-build kaikissa; viimeinen testiharness-muutos koski vain fixturen sulkemisjärjestystä. Ensimmäinen matriisi pysähtyi 839 dp:n fixture-virheeseen ja jatko testasi 839/840 dp:t uudella harnessilla. Historiallisia failure-receiptejä ei lasketa läpäisyiksi.

Kuvallisesti tarkastettiin kaikki 57 M02-kuvaa 15 contact sheetiltä, M01:n sheetit 06/09/12/14, M03:n 06/12 sekä erikseen M04:n lähdelomake/pistevalinta, M05:n pistevalinta/lähdelomake/statusselite ja M06:n pistevalinta/valitsin. Lisäksi tarkastettiin varsinaisen 840 dp:n MainActivityn kuva. Tarkka 91 tuoreen kuvan luettelo on `E/visual-review.json`:issa; matriisireceiptien lukumäärä- ja PNG-mitat `E/matrix-receipts-final.json`:issa. Kuvissa ei havaittu uutta parent-shape- tai glyph-leikkautumista; ruudun reunan osittaiset rivit ovat vieritettävän listan viewport-rajaa. Erillisen Compose-fixturen järjestelmäpalkit eivät todista MainActivityn insettejä, jotka tarkastettiin sen omista kuvista. Kaikkia 456 kuvaa ei ole arvioitu visuaalisesti eikä niitä ole paritettu desktop-runtime-kuviin.

## J01–J15: kokonaiset hyväksyntäpolut

| ID / referenssi | Toteutunut paikallinen osanäyttö | Kokonaispolun tila / jäljellä |
| --- | --- | --- |
| J01 W06/W10 | Fresh isolated test install/forms/config transactions; no fake analytics; S | BLOCKED: oikea configure/test/provider-refresh puuttuu |
| J02 W04/W05 | L: journey.com + Last 30 days → Top page → recreate → Back samaan listakohtaan; selectors C | PASS paikallinen kokonainen touch-jono |
| J03 W04/W09 | R exact targets; L: actual Needs attention → Google Indexing → system Back, sync-tila muuttumaton | PASS paikallinen touch-jono |
| J04 W06/W10 | C kaikki CF/Web-view't, S failures, I/D cache rollback | BLOCKED: live refresh→offline koko polku |
| J05 W06/W10 | C query filter/sort/pages/devices/index UI, S exact requested dimensions | BLOCKED: actual URL Inspection/OAuth |
| J06 W06/W10 | U dated Bing parsing/keys, C populated Bing All sites: 2 erillistä sama-query/site-URL -riviä, crawl stats/issues | NOT RUN: valitun sivuston yhdistetty touch-polku puuttuu; All sites UI PASS local |
| J07 W06/W07 | D exact mixed URL/device/field window, C selectors/URL Cancel/Remove history | NOT RUN: yhdistetty metric/device/URL/Manage-route touch-jono |
| J08 W06/W07/W10 | D/U uptime false-zero/preservation, C Health tabs | BLOCKED: live failed probe+sitemap refresh |
| J09 W10 | I encrypted schema11 internal fixture, D shared Portfolio after import | BLOCKED: oikea Windows export→import→restart offline |
| J10 W10 | I: cancel/restart/invalid/storage abort preserves encrypted input, Room/config/GA4; D secret rollback | PASS paikallinen internal import-contract |
| J11 W03/A05 | L: Activity-recreation detail/filter/form, secret cleared, sync-tila muuttumaton. API36: Home→oman debug-prosessin kill→sama MAIN/LAUNCHER-task; PID poistui ja Google/Queries/journey.com/30d/needle palautuivat (`E/process-launcher-receipt.json`, `E/layout-api36-launcher-restored.json`). | PASS browsing-prosessipalautuminen; refreshin aikainen katkaisu ja pending OAuth recovery NOT RUN |
| J12 W10 | S mock 401/403/429/5xx/redaction + successful sibling/cancel, D/I actual cache rollback | BLOCKED: actual multi-configured provider/offline integration |
| J13 W10/A05 | S same-request coalescing/shared gate/import exclusion | NOT RUN: actual WorkManager/manual eligible race |
| J14 W10/A08 | Schema1 exact bytes; I retains legacy GA4/config and does not destroy cache; P | NOT RUN: old signed installed APK→new signed APK upgrade |
| J15 A05/A06 | L metadata survives/secret empty after recreate. API36: lock enabled/credential unlocked→627,20 s Home-tausta→resume credential prompt→unlock; force-stop/cold start→credential prompt→unlock; lock disabled testin jälkeen. `E/lock-background-*.json`, `E/layout-api36-lock-*.json`. | PASS API36:n nimetty lock/restoration-polku; API28 credential-only FAIL alla |

## Havaitut ja korjatut hyväksyntäongelmat

- Portfolio TopPages -sortissa puuttuvan `Long?`-arvon `0`-fallback aiheutti ClassCastExceptionin yhteisellä DesktopPortfolio-fixturellä. Fallback on nyt `0L`; sama Room-fixture läpäisee. Historiallinen epäonnistuminen `E/device-M01-before-sort-fix.txt` ei ole lopullinen tulos.
- 320 dp / 200 % -valitsimen capsule-reuna leikkasi pitkän tekstin visuaalisesti. Valitsin käyttää 8 dp:n suorakulmaista shapea. Fonttia tai tarkistimen ehtoja ei pienennetty.
- Testin page-0999-selector oletettiin täsmälliseksi tekstiksi, vaikka käyttöliittymä näyttää myös domainin. Selector tunnistaa täydellisen URL:n näkyvästä tekstistä; 1 000. rivin reachability-vaatimus säilyy.
- SourceForm-kuva otetaan modalin siirtymän päätyttyä. Varsinainen lomake on peittävä ja sisältää oman scroll/IME-tilan; Secrets eivät ole rememberSaveable-tilassa.
- 200 % fontilla pitkät Query/Page/Indexing-rivit leikkautuivat Materialin capsule-nappien reunoihin, vaikka glyph-layout mahtui tekstisolmuun. ReportRow/ExpandableRow käyttävät nyt 8 dp:n control-shapea. Kuvallinen tarkistus on pakollinen parent-clippingin havaitsemiseen.
- Pitkä kaavio näytti aluksi vain muutaman päivän. Alkuzoom on nyt koko sisältö (`Zoom.Content`); päivätty data, gapit, previous-alignment ja zoomaus säilyvät.
- API 28 paljasti `ConcurrentModificationException`-virheen Vico 3.2.3:n producer-update/dispose-kilpailussa. Raportit muodostavat nyt muuttumattoman Vico-mallin Room-snapshotista `remember`-tilassa ja korvaavat sen datan vaihtuessa. Ei kirjastopäivitystä tai virheen nielemistä. [Version 3.2.3:n model-host](https://github.com/patrykandpatrick/vico/blob/v3.2.3/vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/CartesianChartHost.kt) ja [producerin lähde](https://github.com/patrykandpatrick/vico/blob/v3.2.3/vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/data/CartesianChartModelProducer.kt).
- API 28:n fixture-korruptio käyttää taulun uudelleenluontia uudemman SQLite `RENAME COLUMN` -syntaksin sijasta; puuttuvan sarakkeen hylkäys ja vanhan cachen säilytys tarkistetaan edelleen. Historiallinen virhe: `E/device-api28-before-harness-fix.txt`.
- Landscape-testit vierittävät View-valitsimen ja uudelleenluodun metadatan näkyviin ennen kosketusta/näkyvyystarkistusta. Testin odotusta ei poistettu. Historiallinen virhe: `E/device-M05-before-reachability-fix.txt`.
- Uutta API-avain/token-yhteyttä ei voi tallentaa tyhjällä salaisuudella. Edit-yhteyden tyhjä korvauskenttä säilyttää vanhan salaisuuden. MobileReportsTest tarkistaa uuden CrUX/PS-lomakkeen Save-tilan.
- Nav3:n säilytetty entry käytti alkuperäistä ShellContextia: Roomin domain-lista jäi tyhjäksi ja uusi ajanjakso tallentui mutta avoin raportti näytti vanhan valinnan. Entry lukee nyt `rememberUpdatedState`-tilasta tuoreen kontekstin. ShellJourneyTest valitsee journey.comin ja 30 päivää, tarkistaa valitsimen, avaa Page detailin ja palaa samaan listakohtaan. Maiseman kolme polkua PASS: `E/shell-landscape-after-nav-fix.txt`; aiemmat epäonnistumiset säilyvät erillisissä before-nav-fix-lokeissa.
- Kuvallinen review paljasti 2xx- ja 5xx-sarjojen värikierron törmäyksen sekä puuttuvan nimi–väri-sidonnan selitteessä. Neljä statusryhmää käyttää nyt neljää erottuvaa teemaväriä; sama sidonta koskee viivaa, pylvästä ja selitettä. Previous on neutraali ja viivakaaviossa katkoviiva. `E/status-colors-before-fix.png` säilyttää löydön. Vanha matriisi keskeytettiin tarkoituksella M04:n aikana uuden APK:n testaamiseksi (`E/device-matrix-before-legend-fix.txt`); keskeytys ei ole uusi sovellusvirhe.
- More-näkymän lukitus-/periodic-sync-kytkimet olivat nimettömiä erillisiä semantiikkasolmuja. Switch saa nyt oman asetuksen title-contentDescriptionin. Muutos koskee toimintojen saavutettavaa nimeä, ei niiden toimintaa.
- Period-valinnan ja Page/Back-siirtymän testi odottaa nyt DataStore/Room/navigaation näkyvää tulosta ennen assertia. Välitön assert ei löytänyt valitsinta/ruutua asynkronisen siirtymän aikana; oikea sovellus näytti valinnan oikein (`E/period-diagnostic.png`). Vaatimus näkyvästä valinnasta säilyy. API28:n uudelleenkäynnistyksen jälkeen irti oleva emulated-volume esti screenshot-artifactin tallennuksen. Testin kuvat ja glyph-loki tallennetaan nyt sovelluksen sisäiseen filesDir-hakemistoon ja kopioidaan vain omilta fixture-emulaattoreilta `run-as`-komennolla. API28:n historiallinen 3 artifact-virheen receipt: `E/device-api28-after-reboot-storage-failure.txt`.
- Oikea IME matalassa 640×320 dp:n vaakatilassa siirsi SourceFormin kentän ruudun yläpuolelle (`E/api36-form-landscape-ime.png`). Dialogin `decorFitsSystemWindows=false` jättää insettien käsittelyn Compose-paddingille. Samassa tilanteessa kenttä näkyy kokonaan y=84–212 ja IME alkaa y=212 (`E/layout-api36-form-fixed-ime.json`, `E/api36-form-landscape-ime-fixed.png`). Ks. [DialogProperties](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties). M02/M05:n lomake+Shell-regressio 4/4 molemmissa (`E/device-M02-form-final.txt`, `E/device-M05-form-final.txt`).
- Suunnitelman haettava sivustovalitsin on toteutettu ModalBottomSheetin LazyColumn-listana: domain-haku, tyhjän tuloksen selite ja aina saatavilla oleva All sites. Raporttikonteksti näyttää ratkaistut päivämäärät ja All-jakson rolling-365-selitteen. Headerin relative freshness säilyttää puuttuvan ja tulevan timestampin eron; absolute timestamp säilyy detail-ruuduissa.
- Kaavion pistevalinta näyttää päivän markerissa sekä sarjan, alkuperäisen päivämäärän, arvon ja yksikön tavallisena tekstinä kaavion alla. Previous-sarjan tekstissä on sen oma päivämäärä. Show chart data säilyttää koko aineiston tekstivaihtoehdon. Vicon oletusmarkeri hävisi lyhyen napautuksen jälkeen ennen näkyvää tulosta; molemmat kaaviotyypit käyttävät nyt kirjaston `rememberToggleOnTap`-ohjainta ([3.2.3:n lähde](https://github.com/patrykandpatrick/vico/blob/v3.2.3/vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/marker/CartesianMarkerController.kt)). Testi vierittää valitun arvon, ei vain sen otsikon, kokonaan näkyviin.
- Uuden sivustovalitsimen jälkeinen M01-ajo päättyi 16/17-tulokseen insightistä paluun välittömässä näkyvyystarkistuksessa (`E/device-M01-before-marker-and-back-wait.txt`). Testi odottaa nyt rajatusti samaa näkyvää paluukohtaa ilman uutta vieritystä. Pistevalinnan ja insight-paluun kohdennettu testi läpäisi 2/2 (`E/device-marker-and-back-targeted-final.txt`); koko matriisi raportoidaan erikseen.
- 839 dp:n ensimmäinen viimeistelyajo sai 7/8-tuloksen: MobileReportsTest sulki Room-fixturen ennen testin Activityn ja sen havaintovirtojen hävittämistä (`E/device-M06-839-before-fixture-cleanup.txt`). Testin Activity suljetaan nyt ennen tietokantaa. Assertioneita tai sovelluksen Room-elinkaarta ei muutettu; uusinta raportoidaan omana receiptiään.

## Avoimet release- ja suorituskykyportit

`assembleRelease` tuottaa minifioidun **allekirjoittamattoman** APK:n. Se ei todista allekirjoitusta, asennusta, päivitystä tai minifioitua runtimea. Olemassa olevaa SignRelease-prosessia ja yksityistä avainta ei käytetty ilman erillistä release-valtuutusta.

1 000 query/page -rivin saavutettavuus ja 2 sivuston 365 päivän raportit todistavat funktionaalisen suuren aineiston käsittelyn. Warm 1 s / cold 2 s / tap 100 ms / 95 % frame-budjetteja ei ole mitattu nimetyn fyysisen referenssilaitteen kanssa: **NOT RUN**. Ei main-thread DB/network -havaintoa staattisessa IO-boundaryn tarkistuksessa; tämä ei korvaa runtime-profileria.

Oikean desktopin kaikki tabit/lomakkeet samalla fixturellä kuvattuina: **NOT RUN**. Nykyisestä C#-lähteestä suoritetut fixturet kattavat yllä rajatut laskentatulokset, eivät koko desktopin visuaalista tai provider-käyttäytymistä.

API 28:n ennestään olemassa oleva BiometricAppLock ei hyväksynyt eristetyn emulaattorin asetettua laite-PINiä: lukon aktivointi tuotti tekstin "Set up a screen lock or biometrics to unlock Numeris." (`E/layout-api28-lock-unavailable.json`, `E/api28-lock-unavailable.png`, `E/api28-auth-trust.txt`). MainActivity/BiometricAppLock eivät kuulu tämän portin muutoksiin ja pysyvät muuttumattomina. API28:n credential-only-auth on **FAIL havaittu / avoin ennestään oleva rajoitus**; raportti-/Room-tapaukset eivät todista sitä toimivaksi.

API28:n vanha fixture-laite jäi laite-PIN-kokeilun jälkeisessä uudelleenkäynnistyksessä `CryptKeeper`/`vold.decrypt=trigger_restart_min_framework` -tilaan. Tämä esti normaalin Home/OS- ja ulkoisen tallennuksen todistamisen. Sisäisen Roomin yksittäiset tapaukset ja kolme korjatun ajoituksen Shell-testiä läpäisivät tässä rajatussa ympäristössä. Niitä ei lasketa normaalin OS:n startup/auth/upgrade-hyväksynnäksi. Lopuksi luotu uusi `numeris_parity_api28_clean` käytti omaa tyhjää datahakemistoa ja samaa jo asennettua API28-imageä. Sen ADB pysyi offline-tilassa 20:03:26–20:10:13, joten viimeisen APK:n asennus ja testit jäivät ajamatta. Tästä ei päätellä sovellusvirhettä: sovellusta ei käynnistetty. Omat emulaattorit suljettiin ja käyttäjän laitteisiin ei koskettu.

`E/application-assets-final.json` vahvistaa, ettei debug/release-sovelluksen assets sisällä pariteettifixturejä. APK-tiedostojen SHA-256:t ovat `E/apk-hashes-final.json`:issa. Ne ovat paikallisten buildien tunnisteita, eivät allekirjoituksen tai asennetun release-runtimen todiste.

## Siivous ja säilytys

Vain tätä tarkistusta varten käynnistetyt emulaattorit suljettiin serial-kohtaisesti ja omat prosessit varmennettiin päättyneiksi PID:n, prosessin nimen ja luontiajan perusteella. Fyysiseen laitteeseen tai käyttäjän muuhun emulaattoriin ei koskettu. Gradle-ajot käyttivät `--no-daemon`; erillinen C#-runner `UseSharedCompilation=false`. Lopullinen prosessikuitti on `E/cleanup-receipt.json`:issa; API36/API28:n jäljellä olevien omien prosessien listat ovat tyhjät. Käyttäjän olemassa olevia prosesseja ei lopetettu. Säilytystarkistus rekonstruoi yhteisen app/build.gradle.kts:n alkuperäisen sisällön sen LF-rivinvaihdoilla; formatterin nykyiset CRLF-rivinvaihdot eivät muuta käyttäjän asetuksia.
