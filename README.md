# Numeris Android

Numeriksen itsenäinen, henkilökohtaiseen käyttöön tarkoitettu Android-versio. Sovellus tallentaa raporttidatan paikalliseen Room-tietokantaan ja synkronoi providerit suoraan ilman Windows-konetta tai etäpalvelinta.

Raportit: portfolio-Overview ja Page detail, Cloudflare/Web Analytics, Google/Bing, CrUX/PageSpeed sekä Uptime/Sitemap. Sources hallitsee provider-kohtaiset yhteydet ja mitatut URL:t. GA4 on poistettu kokonaan; Room-migraatio 1→2 poistaa myös vanhan GA4-historian ja yhteydet. Windowsin Sources → Android data transfer vie salatun `.nmrx`-paketin: raportit sekä valinnaisesti yhteydet, API-avaimet ja raporttivalinnat. Androidin Sources → Import from Windows näyttää sisällön ennen tuontia. Google-kirjautuminen tehdään Androidissa uudelleen.

Toteutuksen hyväksyntänäyttö ja avoimet rajat: [parity acceptance](docs/parity-acceptance.md). Havainnekuvat ovat [design-preview](docs/design-preview/README.md)-hakemistossa.

## Paikallinen build

Vaatimukset: Android SDK 37, Build Tools 36.0.0 ja JDK 17. `targetSdk` pysyy suunnitelman mukaisesti 36:ssa. Projektin wrapper käyttää Gradle 9.6.1:tä.

```powershell
.\gradlew.bat test assembleDebug
```

Debug-APK syntyy polkuun `app\build\outputs\apk\debug\app-debug.apk`.

## Release ja asennus

Release-avain sijaitsee käyttäjäprofiilin suojatussa
`%USERPROFILE%\.numeris\signing`-hakemistossa. Salasana on tallennettu vain
Windows DPAPI:lla salattuna nykyiselle Windows-käyttäjälle. Älä koskaan commitoi
keystorea tai salasanoja. Allekirjoitusskripti valitsee `ANDROID_HOME`- tai
`ANDROID_SDK_ROOT`-hakemistosta uusimman vakaan Build Tools -version, jossa
`apksigner.bat` on käytettävissä.

```powershell
.\gradlew.bat clean test lint detektCheck ktlintCheck assembleRelease
.\tools\sign-release.ps1 -NoBuild
adb install -r app\build\outputs\apk\release\app-release.apk
```

`-NoBuild` ohittaa vain Gradle-vaiheen. Skripti kopioi allekirjoittamattoman
APK:n yksilölliseen saman hakemiston väliaikaistiedostoon, antaa molemmat
salasanat `apksigner`ille stdin-kanavan kautta ja ajaa
`verify --verbose --print-certs` ennen atomista julkaisua. Epäonnistuminen
poistaa vain kyseisen ajon väliaikaistiedoston ja jättää aiemman
`app-release.apk`:n ennalleen. Build-, sign- ja verify-vaiheilla on erilliset
määräajat; aikakatkaisu tai peruutus lopettaa koko käynnistetyn prosessipuun
ennen rollbackin valmistumista. Batch-työkalujen poluissa ja argumenteissa
sallitaan välilyönnit, mutta cmd.exe:n shell- ja kontrollimerkit hylätään ennen
prosessin käynnistystä.

Sama signing key on säilytettävä kaikissa päivityksissä. Varmuuskopioi se vähintään kahteen erilliseen salattuun paikkaan.
