# Numeris Android

Numeriksen itsenäinen, henkilökohtaiseen käyttöön tarkoitettu Android-versio. Sovellus tallentaa raporttidatan paikalliseen Room-tietokantaan ja synkronoi providerit suoraan ilman Windows-konetta tai etäpalvelinta.

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
keystorea tai salasanoja.

```powershell
.\gradlew.bat clean test lint detektCheck ktlintCheck assembleRelease
.\tools\sign-release.ps1 -NoBuild
adb install -r app\build\outputs\apk\release\app-release.apk
```

Sama signing key on säilytettävä kaikissa päivityksissä. Varmuuskopioi se vähintään kahteen erilliseen salattuun paikkaan.
