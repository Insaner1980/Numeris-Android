> Current scope, 2026-10-13: GA4 is fully removed at the user's request, including its API client, sync service, source UI and stored history via Room migration 1 to 2. Any GA4 implementation requirements in this original specification are superseded.

# Numeris Android – kattava toteutussuunnitelma

## 1. Tavoite ja lukitut ratkaisut

Rakennetaan täysin itsenäinen, natiivi Android-versio Numeriksesta. Sovellus kutsuu samoja Cloudflare-, Google-, Bing-, CrUX-, PageSpeed-, uptime- ja sitemap-lähteitä suoraan, tallentaa datan paikalliseen Room-tietokantaan ja toimii ilman Windows-konetta, etäpalvelinta tai pilvisynkronointia.

Lukitut rajaukset:

- Sovellus tulee vain käyttäjän omaan käyttöön.
- Ei Google Play -julkaisua, Firebase App Distributionia, telemetriaa, mainoksia tai käyttäjätilejä.
- Jakelu tapahtuu omalla allekirjoitusavaimella rakennetulla release-APK:lla.
- APK asennetaan USB:n ja ADB:n kautta tai kopioimalla tiedosto puhelimeen.
- Androidissa ovat raporttien lisäksi kaikki Sources-asetukset.
- Nykyinen Windows-Numeris jää ennalleen omaksi sovelluksekseen.
- Windowsin nykyinen historia ja asetukset tuodaan Androidiin kerran salatulla vientipaketilla.
- Kertatuonnin jälkeen Windows ja Android synkronoivat lähteet itsenäisesti.
- Jatkuvaa laitteiden välistä synkronointia ei rakenneta.
- Sovellus avautuu suoraan ilman sovelluslukkoa (käyttäjän päätös 13.10.2026).
- Käyttöliittymä säilyy englanninkielisenä nykyisen Numeriksen mukaisesti.
- Android tukee tummaa Numeris-teemaa; vaaleaa teemaa ei toteuteta ensimmäiseen versioon.
- Puhelin on ensisijainen kohde, mutta layout mukautuu tabletille ja taittuvalle laitteelle.
- Sovelluksen pakettinimi on `com.emmah.numeris`.
- `minSdk` on 28 ja `targetSdk` 36. Android 17/API 37 otetaan käyttöön vasta sen vakaan julkaisun ja yhteensopivuustestien jälkeen.

Android-sovellus sijoitetaan erilliseen `C:\Dev\Numeris-Android`-repoon. Nykyiseen `C:\Dev\Numeris`-repoon tehdään vain salatun vientipaketin muodostamiseen ja alustariippumattomien parity-fixturejen ylläpitoon tarvittavat muutokset.

## 2. Arkkitehtuuri ja rajapinnat

### Android-projektin rakenne

Käytetään Gradle Kotlin DSL:ää, version catalogia ja convention plugineja.

Moduulit:

- `:app`: Application, MainActivity, navigaatio, sovellustason DI ja WorkManager-alustus.
- `:core:model`: alustariippumattomat domain-mallit, periodit, domain-identiteetti ja raporttirivit.
- `:core:common`: tulostyypit, aikapalvelu, dispatcherit, lokitus ja virheluokittelu.
- `:core:designsystem`: Material 3 -teema, tokenit, ikonit, yhteiset komponentit ja chart palette.
- `:core:database`: Room-tietokanta, entityt, DAO:t ja migraatiot.
- `:core:network`: HTTP-client, turvallinen virheenkäsittely, redaktointi ja provider-kohtaiset verkkomallit.
- `:core:security`: SecretStore, Android Keystore ja salatun tuontipaketin purku.
- `:core:testing`: fake-palvelut, fixturet ja testidatan rakentajat.
- `:data`: repositoryt, API-clientit, synkronointipalvelut, mapperit ja yhteysasetusten hallinta.
- `:feature:overview`
- `:feature:traffic`
- `:feature:search`
- `:feature:performance`
- `:feature:health`
- `:feature:sources`
- `:benchmark`: Macrobenchmark ja Baseline Profile.

Riippuvuussuunnat:

- Feature-moduulit saavat riippua `core:model`-, `core:designsystem`- ja `data`-rajapinnoista.
- UI ei saa käyttää DAO:ta, HTTP-clientiä tai SecretStorea suoraan.
- API-client hakee datan.
- SyncService orkestroi autentikoinnin, aikavälit ja kutsujärjestyksen.
- Repository omistaa paikallisen tallennuksen ja lukemisen.
- ViewModel tuottaa yhden muuttumattoman `UiState`-virran.
- Composablet näyttävät tilan ja välittävät käyttäjätapahtumat takaisin ViewModelille.
- Provider-salaisuuksia ei saa esiintyä domain-malleissa, UI-tilassa tai Roomissa.

Androidin kerrostettu rakenne noudattaa Googlen suosittelemaa yksisuuntaista datavirtaa ja paikallista tietokantaa tiedon ensisijaisena käyttöliittymälähteenä. [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations).

### Julkiset Kotlin-rajapinnat

Määritellään vähintään seuraavat rajapinnat:

```kotlin
interface SecretStore {
    suspend fun put(key: SecretKey, value: CharArray)
    suspend fun get(key: SecretKey): CharArray?
    suspend fun contains(key: SecretKey): Boolean
    suspend fun delete(key: SecretKey)
    suspend fun clearAll()
}

interface SourceSyncService {
    val source: SourceId
    suspend fun testConnection(): ConnectionTestResult
    suspend fun sync(request: SyncRequest): SyncResult
}

interface SyncOrchestrator {
    fun observeState(): Flow<GlobalSyncState>
    suspend fun syncSource(source: SourceId, request: SyncRequest): SyncBatchResult
    suspend fun syncReportingGroup(group: ReportingGroup, request: SyncRequest): SyncBatchResult
    suspend fun syncAll(request: SyncRequest): SyncBatchResult
}

interface ConnectionRepository {
    fun observeConnections(): Flow<List<Connection>>
    suspend fun save(command: SaveConnectionCommand)
    suspend fun delete(id: ConnectionId)
    suspend fun test(id: ConnectionId): ConnectionTestResult
}

interface ImportRepository {
    suspend fun inspect(uri: Uri, passphrase: CharArray): ImportPreview
    suspend fun replaceFromImport(uri: Uri, passphrase: CharArray): ImportResult
}
```

Yhteiset tilatyypit:

- `UiState.Loading`
- `UiState.Content<T>`
- `UiState.Empty`
- `UiState.Error`, joka voi sisältää aiemmin tallennetun sisällön.
- `SyncState.Idle`
- `SyncState.Running`
- `SyncState.PartialSuccess`
- `SyncState.Success`
- `SyncState.AuthRequired`
- `SyncState.RateLimited`
- `SyncState.Failed`

Virhetyypit:

- `AuthenticationFailure`
- `AuthorizationFailure`
- `RateLimitFailure`
- `TransientNetworkFailure`
- `ProviderFailure`
- `InvalidConfigurationFailure`
- `StorageFailure`
- `ImportFailure`

Virhetyypit eivät saa sisältää raw response bodya, request-URLin query-parametreja, bearer-tokeneita tai API-avaimia.

### Room-tietokanta

Roomiin toteutetaan nykyisen SQLite-skeeman käsitteellisesti vastaavat tauluryhmät:

- Connections ja provider-asetusten metadata.
- Cloudflare traffic, countries, pages ja status codes.
- Cloudflare Web Analytics daily, referrers, pages ja countries.
- Search Console daily, devices, page-query-rivit ja sitemap inspection.
- Bing sites, rank traffic, queries, pages ja rajattu raw-metodidata.
- GA4 daily, pages, acquisition sources, events ja devices.
- Performance URLs, CrUX metric points, PageSpeed runs ja audits.
- Uptime checks.
- Sovelluksen sync-run-historia ja viimeisimmät lähdevirheet.
- Room-skeeman metadata ja tuontipaketin alkuperäversio.

Säännöt:

- Room on UI:n ainoa raportointitiedon lähde.
- Verkosta saatu onnistunut looginen tulos kirjoitetaan yhdessä `withTransaction`-transaktiossa.
- Epäonnistunut tai osittainen vastaus ei saa tyhjentää aiempaa onnistunutta dataa.
- Kaikilla päiväkohtaisilla riveillä on providerin päivään perustuva yhdistelmäavain.
- Domainit ja URL:t normalisoidaan ennen tallennusta.
- `Period.All` tarkoittaa nykyisen Windows-sopimuksen mukaisesti 365 päivää.
- Cloudflare saa hakea enintään 3650 päivää vain eksplisiittisessä historiatuonnissa; normaalit UI-periodit ovat 7, 30, 90 ja 365 päivää.
- Bing raw -rivit säilytetään 180 päivää.
- Raw JSON rajataan 200 000 merkkiin ja yksityiskohtaiset payloadit 40 000 merkkiin.
- Kaikki raw JSON redaktoidaan ennen tallennusta.
- PageSpeed-runien retention kopioidaan nykyisestä Windows-toteutuksesta.
- Jokaisesta Room-skeemamuutoksesta lisätään versioitu migration-testi.
- Destructive migrationia ei sallita release-buildissä.

`SiteIdentity`, `PerformanceUrl`, periodien päivämäärärajat, prosenttilaskenta, trendit ja Insights-säännöt portataan pure Kotlin -muotoon. Nykyisestä Numeris-reposta tuotetaan alustariippumattomat golden-fixturet, joilla C#- ja Kotlin-toteutusten tulokset todistetaan samoiksi.

### Salaisuudet

Provider-salaisuuksia ei tallenneta Roomiin eikä tavalliseen DataStoreen.

Toteutetaan:

- Android Keystore -avain aliasnimellä `numeris.secrets.v1`.
- AES-256-GCM-salaus.
- Satunnainen 12 tavun nonce jokaiselle kirjoitukselle.
- Ciphertext ja metadata tallennetaan `noBackupFilesDir`-hakemistoon atomisesti.
- `android:allowBackup="false"`.
- Leikepöydälle kopiointia ei tarjota salaisuuksille.
- Salaisuus näytetään vain syöttöhetkellä; tallennuksen jälkeen UI näyttää ainoastaan “Configured”.
- Salaisuudet käsitellään `CharArray`-arvoina ja nollataan käytön jälkeen mahdollisuuksien mukaan.
- Lokitus, crash dumpit ja exception-viestit eivät saa sisältää salaisuuksia.
- Release-buildissä verkon body logging on kokonaan pois käytöstä.

Sovelluslukkoa ei ole. Keystore-avaimelle ei aseteta jokaisella käyttökerralla vaadittavaa biometriaa. Muuten WorkManager ei voisi suorittaa taustasynkronointia laitteen ollessa lukittuna.

### Google-autorisointi

Google Search Console ja GA4 eivät käytä Androidissa Windows-version client secretiä tai itse rakennettua custom URI -OAuth-callbackia.

Toteutetaan Google Identity Servicesin `AuthorizationClient`:

- Android OAuth client luodaan pakettinimelle `com.emmah.numeris`.
- OAuth client sidotaan release-allekirjoitusavaimen SHA-256-varmenteeseen.
- Pyydetään vain scope-arvot:
  - `https://www.googleapis.com/auth/webmasters.readonly`
  - `https://www.googleapis.com/auth/analytics.readonly`
- AuthorizationClientilta pyydetään access token ennen Google-synkronointia.
- Jos lupa on jo annettu, token haetaan ilman UI:ta.
- Jos tarvitaan käyttäjän toimintaa, sync keskeytyy tilaan `AuthRequired` ja UI käynnistää palautetun PendingIntentin.
- Tokenia ei kirjoiteta Roomiin, DataStoreen tai lokiin.
- Disconnect käyttää `revokeAccess`-polkua.
- HTTP 401 tyhjentää tokenin paikallisen välimuistin ja vaatii uuden authorization-kierroksen.
- Search Console ja GA4 käyttävät samaa valtuutettua Google-tiliä, mutta säilyttävät erilliset provider- ja domain-asetukset.

Google suosittelee Androidin `AuthorizationClient`-rajapintaa Google-datan käyttöoikeuksiin; vanha `GoogleAuthUtil` on poistuva polku. [AuthorizationClient](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient).

Sovelluksen henkilökohtainen käyttö kirjataan OAuth consent screenin testikäyttäjäasetuksiin. Google Play Store -julkaisua tai julkista OAuth-verifiointia ei tehdä.

### Muut integraatiot

Cloudflare Zone Analytics:

- Asetukset: domain, zone ID ja API token.
- Testi validoi tokenin ja zonen ennen yhteyden tallentamista connected-tilaan.
- Synkronointi hakee daily trafficin, cache-metriikat, security events, statuskoodit, countries ja pages.
- Looginen domain-tulos kirjoitetaan yhdessä Room-transaktiossa.

Cloudflare Web Analytics:

- Asetukset: account ID, account analytics token ja site tagit.
- Token on account-tasoinen.
- Daily-, referrer-, page- ja country-rivit tallennetaan site-tag/domain-avainyhdistelmällä.

Search Console:

- Androidin Google-authorization.
- Käyttäjän valitsemat propertyt tallennetaan metadatana.
- Hakee clicks, impressions, CTR, position, queries, pages ja devices.
- Indexing käyttää URL Inspection APIa ja aktiivisia sitemap-URL:eja.
- Indexing-refresh suoritetaan saman sivun refreshillä vain Indexing-tabilla.

Google Analytics:

- Androidin Google-authorization.
- Domain-kohtaiset property ID- ja measurement ID -asetukset.
- Hakee daily-, page-, source/acquisition-, event- ja device-raportit.
- `engagedSessions` pysyy count-arvona ja `engagementRate` ratio-arvona.
- GA4-kutsut muodostetaan samoilla dimensioilla ja metriikoilla kuin nykyisessä Windows-clientissä.
- Data API -kutsuihin lisätään `returnPropertyQuota`, jotta rate limit voidaan näyttää ymmärrettävästi. [GA4 Data API quotas](https://developers.google.com/analytics/devguides/reporting/data/v1/quotas).

CrUX ja PageSpeed:

- Erilliset API-avaimet ja erilliset yhteystilat.
- `performance_urls` on yhteinen URL-lähdelista.
- Oletus-URL:t:
  - `https://finnvek.com/`
  - `https://knittoolsapp.com/`
  - `https://runcheckapp.com/`
- URL-tason normalisointi delegoi yhteiselle `SiteIdentity`-logiikalle.
- PageSpeed mobile ja desktop haetaan erikseen.
- PageSpeed-kutsut sarjoitetaan; enintään kolme yritystä transient- tai rate-limit-virheessä.
- Retry käyttää exponential backoffia ja palvelimen `Retry-After`-arvoa, jos se on annettu.

Bing:

- Asetukset: API key ja site URL -lista.
- Automaattinen sync rajataan nykyisen sopimuksen metodeihin:
  - `GetUserSites`
  - `GetRankAndTrafficStats`
  - `GetQueryStats`
  - `GetPageStats`
  - `GetCrawlStats`
  - `GetCrawlIssues`
- Päivä säilytetään osana page/query-avainta.
- Muiden read-only-metodien mahdollinen raw-data rajataan ja redaktoidaan.

Health:

- Ei provider-salaisuutta.
- Uptime käyttää HTTPS:ää.
- HTTP-status ja vasteaika tallennetaan.
- Sitemap discovery ja nykyiset inspection-tiedot yhdistetään domainin ja canonical URL:n perusteella.
- Cleartext HTTP estetään Network Security Configilla.

## 3. Käyttöliittymä ja käyttäjäpolut

### Sovellusshell

Käytetään Material 3:a ja `NavigationSuiteScaffold`-rakennetta:

- Kompakti puhelin: viiden kohteen bottom navigation.
- Keskikokoinen ja leveä ikkuna: navigation rail.
- Järjestelmän back-ele ja predictive back toimivat kaikissa detail-, dialogi- ja bottom-sheet-polkuissa.
- Edge-to-edge on käytössä.
- Status- ja navigation bar -kontrasti johdetaan Numeris-teemasta.
- Landscape ei venytä sisältöä koko leveydelle; raportin maksimileveys rajataan ja lisätila käytetään rinnakkaisiin kortteihin.
- Tabletilla list-detail-sisällöt voivat näyttää listan ja valitun detailin rinnakkain.

Navigation 3:n back stack on tyyppiturvallinen ja serialisoitava. [Navigation 3](https://developer.android.com/guide/navigation/navigation-3).

Pääkohteet:

1. Overview
2. Traffic
3. Search
4. Performance
5. More

More avaa Health- ja Sources-kohteet.

### Overview

Sisältöjärjestys:

1. Top app bar: Numeris ja global refresh.
2. Domain filter chip.
3. Period filter chip.
4. Viimeisin onnistunut ja viimeisin yritetty synkronointi.
5. KPI-ruudukko:
   - Cloudflare visitors
   - Google Search clicks
   - Bing clicks
   - CrUX Web Vitals
   - PageSpeed mobile
   - Source health
6. Traffic trend.
7. Search trend.
8. Deterministic Insights.
9. Cache hit ratio, indexed ratio ja source freshness.

Overview ei sisällä provider-kohtaisia pitkiä listoja. Insight-kortti näyttää severityn, otsikon, viestin, “Why shown” ja “Next step” -osion.

Global refresh suorittaa vain configured-tilassa olevat lähteet. Yhden lähteen epäonnistuminen ei estä muiden tulosten tallentamista; lopputulos on `PartialSuccess`.

### Traffic

Ylin source selector:

- Cloudflare
- Analytics

Cloudflare-tabien järjestys:

1. Traffic
2. Cache
3. Security
4. Status
5. Web Analytics

Analytics-tabien järjestys:

1. Overview
2. Pages
3. Acquisition
4. Events
5. Devices

Desktop-taulukot muutetaan mobiililistoiksi:

- Ensimmäinen rivi näyttää nimen ja tärkeimmän arvon.
- Toinen rivi näyttää kaksi tai kolme tukimittaria.
- Rivin painaminen avaa modal bottom sheetin, jossa näkyvät kaikki sarakkeet.
- Lajittelu avataan top app barin actionista.
- Suodatus käyttää hakukenttää, joka voidaan sulkea.
- Listat toteutetaan stable key -avaimellisella `LazyColumn`illa.

### Search

Ylin source selector:

- Google
- Bing

Google Search -tabit:

1. Overview
2. Queries
3. Pages
4. Devices
5. Indexing

Bing-tabit:

1. Overview
2. Queries
3. Pages
4. Crawl

Indexing-tab näyttää:

- sitemap summary
- indexed/not indexed/error-countit
- viimeisimmän inspection-ajan
- URL-listan status- ja coverage-tiedoilla
- refreshin, joka suorittaa sitemap discovery- ja URL Inspection -polun.

### Performance

Tabit:

1. Overview
2. CrUX
3. PageSpeed
4. URLs

Overview näyttää Core Web Vitals -tilan sekä mobile/desktop PageSpeed-scoret. CrUX näyttää metriikkakohtaiset P75-trendit. PageSpeed näyttää scoretrendit ja audit-ongelmat. URLs mahdollistaa URL:n lisäämisen, poistamisen ja enable/disable-tilan.

### Health

Tabit:

1. Uptime
2. Sitemap

Uptime näyttää domainien statukset, vasteajat, uptime-prosentit, incidentit ja kaksi trendikaaviota. Sitemap näyttää URL:t, last seen -ajan ja inspection-statuksen.

### Sources

Providerit ryhmitellään:

- Cloudflare
  - Zone Analytics
  - Web Analytics
- Google
  - Search Console
  - Google Analytics
  - CrUX
  - PageSpeed
- Bing

Jokaiselle yhteydelle tarjotaan:

- yhteystila
- viimeisin onnistunut sync
- viimeisin virhe turvallisesti muotoiltuna
- Test
- Save
- Disconnect/Delete
- tarvittavien domainien, sitejen ja URL-kohteiden hallinta.

Saman providerin Test/Save/Delete/sync-toiminnot eivät saa olla rinnakkain. Poistaminen vaatii vahvistusdialogin. Google Disconnect peruuttaa authorizationin vasta, kun käyttäjä vahvistaa, että se vaikuttaa sekä Search Consoleen että GA4:ään.

### Visuaalinen järjestelmä

Android-tokenit johdetaan nykyisestä Numeris-teemasta:

- Background: `#151419`
- Navigation/content background: sama matte background
- Card: `#F017181D`
- Control: `#F0222526`
- Chart panel: `#E8151419`
- Primary text: `#FBFBFB`
- Secondary text: `#DAD4CC`
- Tertiary text: `#BFBFBF`
- Accent/Liquid Lava: `#F56E0F`
- Accent foreground: `#151419`
- Copper: `#E09145`
- Warm Platinum: `#FCD9B8`

Säännöt:

- Värit määritellään vain `NumerisColorScheme`- ja chart palette -tiedostoissa.
- Spacing perustuu 4 dp:n asteikkoon.
- Korttien oletussäde on 12 dp.
- Kontrollien vähimmäiskosketusalue on 48 × 48 dp.
- Varsinainen tekstityyli käyttää Androidin järjestelmäfonttia.
- Numeeriset KPI:t käyttävät tabular figures -ominaisuutta, jos fontti tukee sitä.
- Dynamic Color on pois käytöstä.
- UI ei käytä gradienttiorbeja, blur-pintoja, glassmorphismia tai desktopin Mica-jäljitelmää.
- Kortit, kaaviot ja kontrollit muodostavat kontrastin; koko sivun päälle ei lisätä scrimiä.

### Kaaviot

Käytetään Vicoa line-, column- ja stacked column -kaavioihin. Kirjaston versio lukitaan version catalogiin toteutushetken uusimpana vakaana versiona. [Vico](https://github.com/patrykandpatrick/vico).

Yhteinen `NumerisChart`-wrapper omistaa:

- taustapinnan
- akselien ja gridin värit
- label-formaattorit
- markerin ja tooltipin
- legendan
- tyhjän tilan
- loading-tilan
- animation togglen
- saavutettavan tekstiyhteenvedon.

Kaaviokartoitus:

- Traffic, search, GA4 daily, Bing traffic, cache, security, CrUX ja uptime response: line chart.
- Status groups, devices, PageSpeed scoret ja incidentit: column tai stacked column.
- Countries, pages, referrers, queries ja events: yhteinen Compose `HorizontalBars`.
- KPI-scoret: progress indicator tai pieni sparkline; ei gauge-kaaviota ilman todellista lisäarvoa.

Jokaisen kaavion semantics sisältää:

- otsikon
- aikavälin
- kokonaisarvon tai keskiarvon
- korkeimman ja matalimman pisteen
- trendin suunnan
- datan viimeisimmän päivän.

### Animaatiot

Animaatioita käytetään vain tilan tai hierarkian selventämiseen:

- Navigaatiosiirtymä: 220 ms shared-axis.
- Bottom sheet: Material 3 -oletusliike.
- KPI-arvon muutos: 180 ms crossfade.
- Chartin ensimmäinen reveal: 300 ms.
- Insightin expand/collapse: 220 ms.
- Refresh-indikaattori näkyy vain todellisen synkin ajan.
- Listan sisältöä ei animoida rivi kerrallaan suurissa listoissa.
- Ei jatkuvaa pulssia, shimmeriä tai koristeellista loop-animaatiota.
- Loading käyttää progress indicatoria tai staattista skeletonia.
- Järjestelmän animaatioasteikon ollessa nolla kaikki omat animaatiot muuttuvat välittömiksi.
- Animaatiot tehdään draw- tai graphics layer -vaiheessa aina kun mahdollista. [Compose animation guidance](https://developer.android.com/develop/ui/compose/animation/quick-guide).

## 4. Toteutusvaiheet

### Vaihe 0 – Android-repon perustaminen

- Luo `C:\Dev\Numeris-Android`.
- Lisää oma `AGENTS.md`, jossa dokumentoidaan arkkitehtuuri, tokenit, salaisuusmalli, provider-vastuut ja tarkistuskomennot.
- Ota käyttöön AGP 9.2.0, Gradle 9.4.1, JDK 17 ja Kotlin 2.3.21.
- Käytä vain vakaita AndroidX-versioita: Compose UI 1.11.4, Material 3 1.4.0, Navigation 3 1.1.4, Material 3 Adaptive 1.2.0, WorkManager 2.11.2 ja Room 3.0.0. Versiot perustuvat 1.7.2026 stable channeliin. [AndroidX stable releases](https://developer.android.com/jetpack/androidx/versions/stable-channel).
- Lisää ktlint, detekt, Android lint, dependency verification ja version catalog.
- Lisää debug- ja release-buildit; ei staging- tai Play-flavoreita.
- Lisää CI:hin unit test, lint, detekt, ktlint ja assembleRelease ilman allekirjoitusavainta.
- Luo release-keystore käyttäjän suojattuun paikalliseen hakemistoon, ei repoon.
- Dokumentoi avaimen alias, luontipäivä ja varmuuskopion sijainti erillisessä käyttäjän yksityisessä tiedostossa.

Valmis, kun tyhjä sovellus rakentuu ja allekirjoittamaton CI-release sekä paikallinen allekirjoitettu APK onnistuvat.

### Vaihe 1 – Domain-mallit ja parity-sopimus

- Porttaa kaikki raporttimallit pure Kotliniksi.
- Toteuta `SiteIdentity`, `PerformanceUrl`, `Period`, ratio/prosentti-formaatit ja trendit.
- Lisää nykyiseen Numeris-repoon JSON-fixturet normalisoinnista, periodeista ja Insights-säännöistä.
- Aja fixturet sekä C#- että Kotlin-testeissä.
- Pidä raw API DTO:t erillään domain-malleista.
- Lisää mapperit provider DTO → domain model ja domain model → Room entity.

Valmis, kun molemmat alustat tuottavat samoista fixtureistä samat domainit, home page URL:t, periodirajat, trendit ja Insights-kortit.

### Vaihe 2 – Room ja offline-first-data

- Toteuta Room-schema, DAO:t ja repositoryt.
- Lisää ensimmäinen versionoitu migration.
- Toteuta transactional upsert jokaiselle providerille.
- Toteuta retention ja raw-redaktointi.
- Toteuta domain- ja periodikohtaiset Flow-kyselyt.
- Lisää DataStoreen vain ei-salaiset asetukset:
  - valittu domain
  - valittu periodi
  - viimeisin sivu
  - taustasynkin toggle
  - taustasynkin aikaväli

- UI lukee aina Roomista ja näyttää cached datan myös syncin aikana.

Valmis, kun fixture-data voidaan kirjoittaa Roomiin, lukea kaikilla raporttikyselyillä ja näyttää offline-tilassa prosessin uudelleenkäynnistyksen jälkeen.

### Vaihe 3 – Keystore ja Windows-tuonti

Windows-Numerikseen lisätään “Export for Numeris Android” -toiminto.

Vientipaketti:

- Tiedostopääte `.numeris-export`.
- Magic header `NMRX`.
- Format version `1`.
- Sisältää SQLite Backup APIlla muodostetun konsistentin `numeris.db`-snapshotin.
- Sisältää ei-salaiset asetukset.
- Sisältää erillisen `secrets.json`-tiedoston, jonka avaimet ovat provider- ja connection-ID-pohjaisia.
- Koko ZIP-payload salataan AES-256-GCM:llä.
- Avain johdetaan käyttäjän kertakäyttöisestä passphrasesta PBKDF2-HMAC-SHA256:lla, 600 000 kierroksella ja satunnaisella 16 tavun saltilla.
- GCM nonce on 12 tavua.
- Manifest sisältää export-version, Windows-skeemaversion, luontiajan ja rivimäärät tauluittain.
- Passphrasea tai johdettua avainta ei tallenneta.

Android-tuonti:

1. Käyttäjä valitsee tiedoston system file pickerillä.
2. Käyttäjä syöttää passphrasen.
3. Tuonti jatkuu ilman biometristä tunnistuspyyntöä.
4. Paketti puretaan sovelluksen cache-hakemistoon.
5. Header, version, GCM-tag ja manifest validoidaan.
6. SQLite avataan read-only-tilassa.
7. Importer laskee esikatselun: domainit, yhteydet ja rivimäärät.
8. Käyttäjä vahvistaa “Replace Android data”.
9. Room-data ja SecretStore tyhjennetään yhdessä ohjatussa operaatioketjussa.
10. Kaikki taulut mapataan Roomiin transaktioittain.
11. Salaisuudet kirjoitetaan SecretStoreen.
12. Väliaikaiset plaintext-tiedostot poistetaan `finally`-polussa.
13. Tuonnin jälkeen tehdään yhteyksien metadata-validointi, mutta provider-API:ja ei kutsuta automaattisesti.
14. Google client secret- ja refresh token -arvoja ei tuoda Androidiin; käyttäjä valtuuttaa Googlen Androidissa uudelleen AuthorizationClientilla.

Valmis, kun koko nykyinen historia tuodaan, rivimäärät vastaavat manifestia, salaisuuksia ei löydy Roomista tai backupista ja väärä passphrase epäonnistuu muuttamatta nykyistä Android-dataa.

### Vaihe 4 – Provider-clientit ja synkronointi

Toteutusjärjestys:

1. Uptime ja sitemap.
2. Cloudflare Zone Analytics.
3. Cloudflare Web Analytics.
4. CrUX.
5. PageSpeed.
6. Bing.
7. Google Authorization.
8. Search Console.
9. GA4.
10. URL Inspection.
11. Global SyncOrchestrator.

Jokaiselle providerille tehdään:

- Retrofit/Ktor-rajapinta.
- Wire DTO:t.
- mapperit.
- turvallinen virhemuotoilu.
- fake HTTP -testit.
- connection test.
- sync-palvelu.
- repository-transaktio.
- status- ja last sync -päivitys.
- rate limit- ja retry-politiikka.

Manuaalinen refresh käyttää coroutinea ja näkyy vain kyseisen ruudun elinkaaren ajan. Pysyvä tai ajastettu työ käyttää WorkManageria.

Taustasynkronointi:

- Unique periodic work nimellä `numeris.periodic-sync`.
- Oletusväli 12 tuntia.
- Ehto: verkkoyhteys saatavilla ja battery not low.
- Ei foreground serviceä.
- Yksi global sync kerrallaan prosessien ja WorkManagerin tasolla.
- Source-kohtaiset synkit suoritetaan järjestyksessä API-quota- ja virheriskin pienentämiseksi.
- Käyttäjän manuaalinen sync korvaa odottavan kertaluonteisen työn mutta ei käynnistä toista työtä jo käynnissä olevan rinnalle.
- Transient-virheet käyttävät exponential backoffia.
- AuthRequired ei retrytä automaattisesti.
- RateLimited käyttää providerin Retry-After-arvoa.
- Partial success tallentaa onnistuneet lähteet ja säilyttää epäonnistuneiden vanhan datan.

WorkManager on Androidin suositeltu ratkaisu prosessin ja uudelleenkäynnistyksen yli säilyvään työhön. [WorkManager](https://developer.android.com/reference/androidx/work/WorkManager.html).

### Vaihe 5 – Design system ja sovellusshell

- Toteuta väri-, spacing-, shape-, typography- ja motion-tokenit.
- Toteuta icon mapping Material Symbols / omat VectorDrawablet nykyisistä Fluent-polkuikoneista.
- Toteuta yhteiset komponentit:
  - `NumerisScaffold`
  - `NumerisTopBar`
  - `KpiCard`
  - `DeltaBadge`
  - `PeriodFilter`
  - `DomainFilter`
  - `SourceSelector`
  - `StatusChip`
  - `ConnectionCard`
  - `NumerisChart`
  - `HorizontalBars`
  - `EmptyState`
  - `ErrorState`
  - `StaleDataBanner`
  - `SyncProgress`
  - `ConfirmDangerDialog`.
- Toteuta Navigation 3 ja adaptive navigation.
- Avaa sovellus ilman cold-start- tai resume-lukkoa.

Valmis, kun shell toimii kompaktissa, medium- ja expanded-koossa ja kaikki komponentit näkyvät Compose Previewissä oikeilla tokeneilla.

### Vaihe 6 – Overview pystysuorana siivuna

- Toteuta Overview kokonaan Roomista UI:hin.
- Toteuta domain, periodi, global refresh, cached data ja stale banner.
- Porttaa `InsightEngine` ja parity-testit.
- Lisää traffic- ja search-chartit.
- Lisää loading, empty, partial success, auth required ja offline -tilat.
- Tarkista lukuarvot nykyiseen Windows-Numerikseen samoilla fixtureillä.

Tämä on arkkitehtuurin hyväksyntäpiste. Muita feature-näkymiä ei rakenneta ennen kuin Overview läpäisee parity-, offline-, accessibility- ja screenshot-testit.

### Vaihe 7 – Traffic

- Toteuta Cloudflare kaikki viisi tabia.
- Toteuta GA4 kaikki viisi tabia.
- Toteuta listojen suodatus, lajittelu ja bottom-sheet detail.
- Varmista engaged sessions / engagement rate -erottelu.
- Lisää chartien saavutettavat yhteenvedot.
- Lisää provider- ja domain-kohtainen refresh.

### Vaihe 8 – Search

- Toteuta Search Console kaikki viisi tabia.
- Toteuta Bing kaikki neljä tabia.
- Toteuta query/page-filterit ja declining/new-listat.
- Toteuta Indexing-refresh URL Inspectionilla.
- Säilytä Bing päiväkohtaiset avaimet ja Crawl-metodien rajaus.

### Vaihe 9 – Performance ja Health

- Toteuta Performance kaikki neljä tabia.
- Toteuta URL-listan add/delete/enable.
- Toteuta CrUX P75 -status ja PageSpeed mobile/desktop.
- Toteuta Healthin Uptime- ja Sitemap-tabit.
- Toteuta vasteaika- ja incident-kaaviot.

### Vaihe 10 – Sources

- Toteuta kaikki connection cardit ja lomakkeet.
- Google Sources ei kysy client secretiä.
- Token- ja API key -kentät käyttävät `PasswordVisualTransformation`ia.
- Save ei korvaa olemassa olevaa salaisuutta tyhjällä kentällä.
- Delete poistaa connection-metadatan, provider-datan sovitun omistajuuden mukaan ja siihen liittyvät salaisuudet.
- Disconnectin jälkeen vanha raporttidata säilyy, kunnes käyttäjä valitsee erillisen “Delete cached data” -toiminnon.
- Add/Test/Save/Delete ovat busy-tilassa keskenään poissulkevia.
- Lomakevirheet ovat kenttäkohtaisia eivätkä pelkkiä Snackbar-viestejä.

### Vaihe 11 – Kovennus ja oma release

- Lisää Room migration -testit.
- Lisää Macrobenchmarkit kylmäkäynnistykselle, Overview-scrollille ja suuren listan renderöinnille.
- Generoi Baseline Profile.
- Testaa R8/ProGuard-provider DTO:t ja serialization.
- Tarkista APK Analyzerilla, ettei APK:ssa ole API-tokenia, Windowsin secrettejä, fixture-salaisuuksia tai debug-URL:eja.
- Testaa lentotila, hidas verkko, DNS-virhe, HTTP 401/403/429/500, timeout ja osittainen provider-vastaus.
- Testaa puhelimen reboot, prosessin tappaminen kesken syncin ja WorkManagerin retry.
- Testaa järjestelmäfontti 200 %, display size suurena ja TalkBack.
- Testaa portrait, landscape, split screen, tabletti ja taittuva preview/emulaattori.
- Rakenna release:
  - `.\gradlew.bat clean test lint detektCheck ktlintCheck assembleRelease`
- Asenna:
  - `adb install -r app-release.apk`
- Varmuuskopioi signing keystore vähintään kahteen salattuun, erilliseen paikkaan.
- Säilytä aina sama signing key, jotta seuraava APK voidaan asentaa päivityksenä vanhan päälle.

## 5. Testaus, hyväksymiskriteerit ja dokumentointi

### Automaattiset testit

Unit-testit:

- Domain- ja URL-normalisointi.
- Periodien date range.
- Ratio- ja prosenttimuotoilu.
- TrendState ja NewActivity.
- InsightEngine.
- Jokaisen providerin DTO-mapperit.
- Virheredaktointi.
- SecretStore round trip ja corruption.
- Import header, version, väärä passphrase ja GCM-tag.
- Connection validation.
- Retry- ja rate-limit-luokittelu.

Database-testit:

- Jokainen DAO.
- Logical multi-row transaction rollback.
- Upsert-idempotenssi.
- Päiväkohtaisten avainten säilyminen.
- Retention.
- Schema migration jokaisesta julkaistusta versiosta uusimpaan.
- Windows-import kaikkien nykyisten taulujen fixture-snapshotilla.
- Tuonti ei jätä puolittaista tilaa virheessä.

HTTP-integraatiotestit:

- Exact request method, endpoint, headers ja body.
- Domain- ja periodiparametrit.
- Providerin onnistunut vastaus.
- Tyhjä vastaus.
- Osittainen vastaus.
- Malformed JSON.
- 401, 403, 404, 429 ja 5xx.
- Timeout ja connection reset.
- Raw body ei päädy käyttäjäviestiin tai tietokantaan redaktoimattomana.

Compose-testit:

- Navigointi ja back stack.
- Domain- ja periodivalinta.
- Refresh disabled busy-tilassa.
- Loading, content, empty, error ja stale data.
- Sources-lomakkeiden validointi.
- Danger confirmation.
- TalkBack-nimet.
- Minimum touch targetit.
- Scrollaus suurella fontilla.

Screenshot-testit:

- Kompakti puhelin 360 × 800 dp.
- Suuri puhelin.
- Landscape.
- Tabletti/expanded.
- Font scale 1.0 ja 2.0.
- Kaikki pääruudut content-, empty- ja error-tilassa.

Parity-testit:

- Jokaisen raportin fixturet ajetaan Windows-repositoryn ja Android-repositoryn läpi.
- KPI:t, listajärjestys, trendit, prosentit ja Insights verrataan.
- Sallitaan ainoastaan erikseen dokumentoitu mobiilin esitystapaero; numeerisia eroja ei sallita.
- Tuonnin jälkeen Room-rivimäärät vastaavat export manifestia.
- Oikealla testidatalla Windowsin ja Androidin samat domain/period-arvot tarkistetaan käsin ennen ensimmäistä releasea.

### Lopulliset hyväksymiskriteerit

Julkaisu on valmis vasta, kun:

- Android toimii ilman tietokonetta, etäpalvelinta ja Google Play Storea.
- Kaikki nykyiset Numeris-raportointialueet ovat käytettävissä.
- Sources sisältää kaikkien integraatioiden testauksen, tallennuksen ja poistamisen.
- Nykyinen historia voidaan tuoda salatulla paketilla.
- Windowsin Google client secret- tai refresh token -arvoja ei päädy Androidiin.
- Provider-salaisuuksia ei löydy APK:sta, Roomista, DataStoresta, lokeista tai Android backupista.
- Google Search Console ja GA4 toimivat Android AuthorizationClientilla.
- Jokainen ruutu näyttää cached datan offline-tilassa.
- Yhden lähteen virhe ei tuhoa aiempaa dataa eikä estä muiden lähteiden onnistumista.
- Kaikki luvut täsmäävät Windows-Numerikseen parity-fixtureillä.
- Kaikki värit, spacing, typography, motion ja chart-asetukset tulevat yhteisistä tokeneista.
- Kaavioilla on saavutettava tekstiyhteenveto.
- TalkBack, 200 % fontti, system back ja predictive back toimivat.
- Release-APK asentuu ja päivittyy samalla omalla signing keyllä.
- Release-build läpäisee unit-, database-, integration-, Compose-, lint-, detekt-, ktlint- ja security-tarkistukset.

### Dokumentointi

Android-repoon kirjoitetaan:

- `AGENTS.md`: arkkitehtuuri ja pakolliset rajat.
- `README.md`: paikallinen build, signing ja ADB-asennus.
- `ARCHITECTURE.md`: data flow, moduulit ja provider-vastuut.
- `SECURITY.md`: SecretStore, OAuth, export/import ja lokitus.
- `DATA-PARITY.md`: Windows–Android-fixturet ja hyväksytyt esityserot.
- `UI-SPEC.md`: tokenit, navigointi, komponentit, chartit ja motion.
- `TESTING.md`: testikomennot ja physical-device-tarkistuslista.

Nykyisen Numeris-repon `AGENTS.md` ja `memory/MEMORY.md` päivitetään export/import-rajapinnan ja Android-sisarsovelluksen vuoksi, koska kyseessä on uusi arkkitehtoninen vastuu.

### Arvioitu toteutusjärjestys ja työmäärä

- Perusta, parity-mallit ja Room: 2–3 viikkoa.
- Security, import/export ja Google authorization: 2–3 viikkoa.
- Provider-clientit ja synkronointi: 3–5 viikkoa.
- Design system, shell ja Overview: 2–3 viikkoa.
- Muut raportit ja Sources: 4–6 viikkoa.
- Kovennus, parity ja release: 2–3 viikkoa.

Realistinen kokonaisarvio yhdelle toteuttajalle on 15–23 työviikkoa. Ensimmäinen käyttökelpoinen pystysuora versio — tuonti, yhteydet, offline Room ja Overview — valmistuu arviolta 6–9 viikossa.
