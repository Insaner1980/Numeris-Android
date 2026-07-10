# Architecture

Numeris käyttää offline-first-arkkitehtuuria ja yksisuuntaista datavirtaa:

`provider API -> SourceSyncService -> repository transaction -> Room -> Flow -> ViewModel UiState -> Compose`

Feature-moduuli ei tiedä provider-DTO:sta, HTTP-clientistä, DAO:sta tai salaisuuksista. Manuaalinen ja WorkManager-synkronointi kulkevat saman `SyncOrchestrator`-rajapinnan kautta. Yhden lähteen virhe tuottaa partial success -tilan ja säilyttää muiden lähteiden onnistuneet sekä epäonnistuneen lähteen aiemmat rivit.

Raporttinäkymä lukee `ReportRepository`-rajapintaa, joka välittää Roomin `Flow`-virrat. Näkymä näyttää aina paikallisen välimuistin myös synkronoinnin aikana. Orkestroija suodattaa global refreshiin vain Roomissa `configured`-tilassa olevat lähteet, suorittaa ne yhdellä mutexilla ja päivittää yhteyksien turvalliset tilat keskitetysti. WorkManager käyttää unique work -nimeä `numeris.periodic-sync`.

Domain, periodi, viimeinen sivu, biometrinen lukko ja taustasynkin asetukset ovat DataStoressa. Raporttien domain-lista muodostetaan Roomin cached datasta. Performance-URL:t ja Bing-sivustot ovat paikallista kohdemetadataa, eivät salaisuuksia.

Moduulirajat ja riippuvuussuunnat ovat `AGENTS.md`-tiedostossa. Room-skeema vastaa käsitteellisesti Windows-Numeriksen SQLite-tauluja, mutta Androidin salaisuudet tallennetaan ainoastaan Keystore-pohjaiseen SecretStoreen.
