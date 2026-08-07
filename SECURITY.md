# Security

- Backup ja device transfer on estetty manifestissa ja data extraction -säännöissä.
- Cleartext HTTP on estetty Network Security Configilla.
- Provider-salaisuudet salataan AES-256-GCM:llä Android Keystoren `numeris.secrets.v1`-avaimella ja tallennetaan `noBackupFilesDir`-hakemistoon.
- Google Search Console ja GA4 käyttävät Android AuthorizationClientia. Windowsin client secretiä tai refresh tokenia ei tuoda.
- Release-buildissä ei lokiteta HTTP-bodyja. Turvallinen virheviesti ei sisällä raw bodya, query-parametreja, bearer-tokenia tai API-avainta.
- `.numeris-export` käyttää `NMRX`-headeria, PBKDF2-HMAC-SHA256:ta (600000 kierrosta) ja AES-256-GCM:ää. Plaintext-väliaikaistiedostot poistetaan aina `finally`-polussa.
- Oma 4096-bittinen RSA/JKS-release-avain on ACL-suojatussa `%USERPROFILE%\.numeris\signing`-hakemistossa. Salasana on Windows DPAPI:n salaamassa tiedostossa ja puretaan muistiin vain `tools/sign-release.ps1`-ajon ajaksi. Salasana välitetään `apksigner`ille vain stdin-kanavan kautta, ei prosessin argumentteina tai diagnostiikassa. Allekirjoitus ja `verify --verbose --print-certs` kohdistuvat ajokohtaiseen väliaikaiseen APK:hon; lopullinen APK korvataan atomisesti vasta verifioinnin jälkeen. Batch-prosessiraja hylkää cmd.exe:n shell- ja kontrollimerkit. Aikakatkaisu, peruutus tai pipeline-keskeytys tappaa koko ulkoisen prosessipuun ja varmistaa sen päättymisen ennen oman temp-tiedoston rollbackia. Avainta tai salasanaa ei ole Gradle-tiedostoissa, APK:ssa eikä repossa.
