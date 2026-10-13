# Windows-Android data parity

Windows-repon nykyinen paikallinen C#-toteutus on laskenta- ja SQLite-sopimuksen lähde. Vertailu on sidottu 2026-10-11 tarkastettuihin tiedostoihin; dirty-työpuu kuuluu viitteeseen. C#-apuohjelmista tuotettu `test-fixtures/parity/desktop-reference.csv` tarkistetaan Kotlinin DesktopReferenceTestissä. Se ei yksin todista kaikkien raporttien tai elävien providerien pariteettia.

Numeerisia eroja ei hyväksytä seuraavissa: domain/origin/home URL, periodin inklusiiviset päivämäärät, KPI:t, trendit, prosentit, listajärjestys ja deterministic Insights. Mobiilin esitystapa saa poiketa, mutta data ei.

Toteutuksen invariantit:

- `Period.All` = 365 päivää.
- Nollasta alkava aktiivisuus = `TrendState.NewActivity`.
- `PerformanceUrl` delegoi `SiteIdentity`lle.
- Puuttuva havainto on null, todellinen nolla on 0. Vertailu vaatii jokaisen odotetun sivuston molempien jaksojen päivät.
- CTR, cache ratio ja position painotetaan kokonaismäärillä/impressioilla, ei päivittäisten keskiarvojen keskiarvolla.
- Sivun join-avain on normalisoitu domain ja case/encoding/trailing slash -säännöt säilyttävä polku. Mitattu URL säilyttää koko polun ja sallitut query-parametrit.
- Uptime on sivustokohtaisten kaikkien tallennettujen probejen prosenttien keskiarvo. Valittu jakso rajaa vasteaikakaavion ja incident-listan; epäonnistuminen ei ole 0 ms.
- Sitemapin aktiivinen populaatio ja tarkastetut/indexed-URL:t ovat erillisiä. Poistettua URL:ää ei lasketa aktiiviseen nimittäjään.
- Google/Bing daily-, query- ja page-havainnot säilyttävät providerin päivämäärän. Tuntematonta legacy-päivää ei muuteta fetch-päiväksi.
- GA4 on poistettu kokonaan. Room 1→2 poistaa käyttäjän pyynnöstä viisi vanhaa GA4-taulua, GA4-yhteydet ja synkronointimetadatan; migraatiotesti varmistaa muiden taulujen säilymisen.

Room-skeema on versiossa 2; versio 1 päivitetään eksplisiittisellä GA4-poistomigraatiolla. Windows-tuonti tukee salattua exportVersion 1 / schema 11 -sopimusta ja validoi sarakkeet, rivimäärät, päivämäärät ja kohteet ennen report-data-transaktiota. Windowsin AndroidReportExporter kopioi 23 raporttitaulua erilliseen snapshotiin, redaktoi raakadataa ja salaa paketin PBKDF2-SHA256/AES-GCM:llä. Valinnainen täysi vienti sisältää yhteysmetadatan, API-avaimet ja raporttivalinnat. Desktopin Google OAuth -tunnuksia ei viedä; Android-kirjautuminen tehdään uudelleen. Pelkkä raporttituonti säilyttää Android-konfiguraation.

Android lukee snapshotin read-only-yhteydellä ja korvaa raportit sekä tuodun yhteysmetadatan yhdellä Room-transaktiolla. Salaisuudet tallennetaan vain SecretStoreen. Aiemmat avaimet ja raporttivalinnat sisältävä palautusjournal tallennetaan myös salattuun SecretStoreen ennen muutoksia; Roomin commit-tunniste ratkaisee keskeytyksen jälkeisen palautuksen ennen UI:n ja taustasynkin käynnistystä. Tuonnin plaintext-välitiedosto sisältää vain raporttitietokannan, ei `secrets.json`-tiedostoa. Androidin taustasynkkivalinnat ja muut yhteydet säilyvät. WindowsTransferTest ja WindowsConfigurationTest käyttävät Windowsin varsinaisella exporterilla tehtyjä synteettisiä paketteja; ne tarkistavat myös virhepalautuksen ja tietokannan uudelleenavauksen.

F01–F15:n toteutunut näyttö ja rajat: [hyväksyntäraportti](docs/parity-acceptance.md).
