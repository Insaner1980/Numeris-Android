# Testing

## Automaattinen

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat detektCheck
.\gradlew.bat ktlintCheck
.\gradlew.bat assembleDebug
```

Testaa domain-normalisointi, periodit, trendit, InsightEngine, DTO-mapperit, virheredaktointi, SecretStore, importin väärä passphrase/GCM-tag, DAO:t, transaktioiden rollback, retention, migraatiot ja tarkat HTTP-pyynnöt.

## Fyysinen laite

Testaa lentotila, hidas verkko, 401/403/429/5xx, prosessin tappaminen syncin aikana, reboot, biometrinen cold start/resume, predictive back, TalkBack, font scale 200 %, portrait, landscape ja split screen. Tarkista APK Analyzerilla, ettei APK sisällä salaisuuksia tai debug-URL:eja.
