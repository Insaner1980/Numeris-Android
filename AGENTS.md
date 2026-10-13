# Numeris Android - agenttiohje

## Rajat

- Tämä repo on itsenäinen Android-sovellus. Se ei saa riippua Windows-Numeriksesta ajon aikana.
- Pakettinimi on `com.emmah.numeris`, `minSdk` 28 ja `targetSdk` 36.
- UI on englanninkielinen ja käyttää vain tummaa Numeris-teemaa.
- Älä tallenna provider-salaisuuksia Roomiin, DataStoreen, lokiin, virheviestiin tai build-konfiguraatioon.
- Älä lisää telemetriaa, mainoksia, käyttäjätilejä, Firebasea tai Play-jakelua.
- Älä aja `lint-check`- tai `security-check`-wrappereita; käyttäjä ajaa ne pyytäessään.

## Arkkitehtuuri

- `:app` omistaa Android Applicationin, shellin, navigaation ja prosessitason alustuksen.
- `:core:model` on pure Kotlin ja sisältää domain-tyypit sekä julkiset rajapinnat.
- `:core:database` omistaa Room 3 -skeeman, DAO:t, migraatiot ja transaktiot.
- `:core:network` omistaa HTTP:n, redaktoinnin ja provider-wire-tyypit.
- `:core:security` omistaa SecretStoren, Keystoren, biometrian ja tuontipaketin purun.
- `:data` omistaa repositoryt, provider-clientit ja sync-palvelut.
- `ReportRepository` on raporttinäkymien Room-lukuraja; se palauttaa core:model-raportteja yhtenäisestä read-transaktiosta IO-dispatcherilla. Käyttöliittymä ei kutsu DAO:ita suoraan.
- `DefaultSyncOrchestrator` suorittaa vain configured-lähteet, sarjallistaa ajot ja päivittää status-, virhe- ja last-sync-metadatan keskitetysti.
- Päivityksiä on yksi: `syncEverything()` hakee kaikki lähteet kaikille sivustoille koko historian jaksolta ja tekee URL Inspectionin vanhentuneille sivustokartan URL:ille (yli 7 vrk, enintään 200 per sivusto). Kaikki päivityspainikkeet ja taustasynkka käyttävät sitä.
- Cloudflare-haku kysyy ensin vyöhykkeen GraphQL-rajat (`settings`) ja rajaa aikavälin niihin (`CloudflareQueryWindow`). Cloudflaren oma virheviesti näytetään redaktoituna yhteyden tilassa.
- Feature-moduulit saavat käyttää vain `core:model`-, `core:designsystem`- ja `data`-rajapintoja. UI ei käytä DAO:ta, HTTP-clientiä tai SecretStorea suoraan.
- Room on raportti-UI:n ainoa datalähde. Onnistunut looginen provider-tulos kirjoitetaan yhtenä transaktiona; virhe ei saa poistaa cached-dataa.
- Feature-moduulit omistavat raportti-UI:n; app omistaa shellin, detail-reitit ja palautettavan raporttikontekstin. Sources ei tallenna salaisuuksia saved stateen.
- Yhteysmuutokset, Indexing ja import kulkevat saman exclusive-portin kautta kuin sync. Windowsin AndroidReportExporter tuottaa salatun schema-11-paketin, jossa yhteydet, API-avaimet ja raporttivalinnat ovat valinnaisia. Google OAuth -tunnuksia ei siirretä. Android näyttää esikatselun ennen tuontia; raportit ja tuotu yhteysmetadata korvataan yhdessä Room-transaktiossa. WindowsConfigurationTransfer tallentaa salatun palautusjournalin SecretStoreen ja palauttaa keskeytyneen tuonnin Roomin commit-tunnisteen perusteella ennen UI:ta ja taustasynkkiä. Muu Android-konfiguraatio, lukko/taustasynkkivalinnat ja vanha GA4-historia säilyvät. GA4 ei ole aktiivinen lähde. Snapshot luetaan read-only-yhteydellä; plaintext-avaintiedostoa ei tehdä.

## Yhden totuuden lähteet

- Riippuvuusversiot: `gradle/libs.versions.toml`.
- Android-buildin yhteisasetukset: `build-logic`.
- Värit, fontti (Inter, `res/font`), spacing, shapet ja motion: `:core:designsystem`.
- Domain/URL-normalisointi: `SiteIdentity`; `PerformanceUrl` delegoi sille.
- Lähteiden näyttönimet: `SourceId.displayName`. Core Web Vitals -kynnysarvot: `ReportMath.vitalRating`.
- UI-komponentit ja tilojen sävyt (`PillTone`, indeksointi, lab-pisteet): `:core:designsystem`; määrittely `UI-SPEC.md`.
- Raakadatan rajaus ja redaktointi: `:core:network` yhteinen policy.
- Salaisuudet: `SecretStore`; yhteysmetadata kuuluu Roomiin.
- Release-avain ja DPAPI-salasanatiedosto: `%USERPROFILE%\.numeris\signing`; niitä ei saa kopioida repoon.
- Release-allekirjoituksen prosessi-, stdin-, verifiointi- ja atominen julkaisulogiikka: `tools/SignRelease.psm1`; `tools/sign-release.ps1` on vain komentorivin entrypoint.

## Tarkistuskomennot

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat detektCheck
.\gradlew.bat ktlintCheck
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Jokaisesta Room-skeemamuutoksesta lisätään migraatio ja migraatiotesti. Destructive migrationia ei sallita. Kun moduuleja, datavirtaa tai vastuita muutetaan, päivitä tämä tiedosto ja Windows-repon `AGENTS.md` sekä `memory/MEMORY.md`.
