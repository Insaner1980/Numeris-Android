# Testing

## Automaattinen

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat detektCheck
.\gradlew.bat ktlintCheck
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Testaa domain-normalisointi, periodit, trendit, InsightEngine, DTO-mapperit, virheredaktointi, SecretStore, importin väärä passphrase/GCM-tag, DAO:t, transaktioiden rollback, retention, migraatiot ja tarkat HTTP-pyynnöt.

Instrumentaatiotestit sijaitsevat `app/src/androidTest`. ReportPersistenceTest ja ConnectionIntegrityTest käyttävät eristettyä Roomia ja fixture-salaisuuksia. ImportContractTest ja ImportRecoveryTest käyttävät schema-11-fixtureä ja salattua testipakettia. MobileReportsTest käy kaikki raporttitabien ja lomakkeiden perheet sekä valitsimet ja URL-poiston vahvistuksen; se tarkistaa tekstin todelliset glyph-rajat ja kalibroi tarkistimen tahallisesti leikkautuvalla tekstillä. ShellJourneyTest käyttää oikeaa MainActivitya navigoinnin ja Activityn uudelleenluonnin todistamiseen. Testit eivät käytä käyttäjän tietokantaa tai provider-tunnuksia.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb -s <disposable-emulator-serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <disposable-emulator-serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <disposable-emulator-serial> shell am instrument -w -r com.emmah.numeris.test/androidx.test.runner.AndroidJUnitRunner
```

Pakolliset kapeat tarkistukset ovat 320 dp / 100 %, 320 dp / 200 % sekä 360–412 dp / 100 % ja / 200 %. Tarkista lisäksi landscape + IME, 600/839/840+ dp, resize, API 28 ja 36 sekä TalkBack ja molemmat navigointitavat. Ei nollan testin hyväksyntää. Kuvat ja semantiikka eivät korvaa oikeaa TalkBack- tai live-OAuth-näyttöä. Tarkat ajot, lukumäärät ja avoimet kohdat ovat [hyväksyntäraportissa](docs/parity-acceptance.md); yksityiset runtime-lokit ja kuvat pysyvät ignored `reports/`-hakemistossa.

Windowsin `Numeris.Tests --android-export <fixture-path>` tuottaa eristetyt raportti- ja yhteyssiirron testipaketit. WindowsConfigurationTest tarkistaa täydellisen tuonnin, SQLite-virheen jälkeisen avainten/asetusten palautuksen ja keskeytetyn korvauksen palautuksen uudella repository-instanssilla. Keskeytysinjektio ei yksin todista käyttöjärjestelmän tekemää prosessitappoa. Asenna samasta buildista sekä sovellus- että instrumentaatio-APK; vanha sovellus uusien testien kanssa ei ole toiminnallinen testitulos. Näitä fixture-testipaketteja ei asenneta henkilökohtaiseen puhelimeen.

## Fyysinen laite

Testaa lentotila, hidas verkko, 401/403/429/5xx, prosessin tappaminen syncin aikana, reboot, biometrinen cold start/resume, predictive back, TalkBack, font scale 200 %, portrait, landscape ja split screen. Tarkista APK Analyzerilla, ettei APK sisällä salaisuuksia tai debug-URL:eja.
