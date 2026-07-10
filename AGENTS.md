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
- `ReportRepository` on raporttinäkymien Room-lukuraja; käyttöliittymä ei kutsu DAO:ita suoraan.
- `DefaultSyncOrchestrator` suorittaa vain configured-lähteet, sarjallistaa ajot ja päivittää status-, virhe- ja last-sync-metadatan keskitetysti.
- Feature-moduulit saavat käyttää vain `core:model`-, `core:designsystem`- ja `data`-rajapintoja. UI ei käytä DAO:ta, HTTP-clientiä tai SecretStorea suoraan.
- Room on raportti-UI:n ainoa datalähde. Onnistunut looginen provider-tulos kirjoitetaan yhtenä transaktiona; virhe ei saa poistaa cached-dataa.

## Yhden totuuden lähteet

- Riippuvuusversiot: `gradle/libs.versions.toml`.
- Android-buildin yhteisasetukset: `build-logic`.
- Värit, spacing, shapet ja motion: `:core:designsystem`.
- Domain/URL-normalisointi: `SiteIdentity`; `PerformanceUrl` delegoi sille.
- Raakadatan rajaus ja redaktointi: `:core:network` yhteinen policy.
- Salaisuudet: `SecretStore`; yhteysmetadata kuuluu Roomiin.
- Release-avain ja DPAPI-salasanatiedosto: `%USERPROFILE%\.numeris\signing`; niitä ei saa kopioida repoon.

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
