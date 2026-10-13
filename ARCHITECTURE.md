# Architecture

Numeris käyttää offline-first-arkkitehtuuria ja yksisuuntaista datavirtaa:

`provider API -> SourceSyncService -> repository transaction -> Room -> Flow -> ViewModel UiState -> Compose`

Feature-moduuli ei tiedä provider-DTO:sta, HTTP-clientistä, DAO:sta tai salaisuuksista. Manuaalinen ja WorkManager-synkronointi kulkevat saman `SyncOrchestrator`-rajapinnan kautta. Yhden lähteen virhe tuottaa partial success -tilan ja säilyttää muiden lähteiden onnistuneet sekä epäonnistuneen lähteen aiemmat rivit.

Raporttinäkymä lukee `ReportRepository`-rajapintaa. Se palauttaa core:model-raportteja ja lukee yhden raportin Roomin yhtenäisestä read-transaktiosta IO-dispatcherilla. ReportReader ja mapperit ovat data-moduulin sisäisiä. RoomSnapshotDao invalidoi raporttivirran raportti- ja yhteystaulujen muutoksista. Näkymä näyttää paikallisen välimuistin myös synkronoinnin aikana.

Orkestroija suodattaa kaikki refresh-polut vain Roomissa `configured`-tilassa oleviin lähteisiin ja konfiguroituihin kohteisiin, sarjallistaa ajot ja yhdistää identtiset yhtäaikaiset pyynnöt. Indexing käyttää samaa porttia ja vaatii valitun sivuston. Lähdepalvelut jatkavat onnistuvia sisarkohteita virheen jälkeen ja päivittävät loogisen kohdetuloksen transaktiossa. Rajattu onnistuminen ei väitä kaikkia kohteita päivitettyiksi. WorkManager käyttää unique work -nimeä `numeris.periodic-sync`.

Domain, periodi, viimeinen sivu ja taustasynkin asetukset ovat DataStoressa. Domain-lista yhdistää konfiguroidut kohteet ja raporttien cached identiteetit. Navigation 3:n tallennettava stack säilyttää detail-kontekstin; rememberSaveable ja state holder säilyttävät tabit, suodattimet ja scrollin. Salaisuuksia ei tallenneta navigaatioon tai saved stateen.

Feature-moduulit omistavat omat Compose-raporttinsa. App omistaa shellin, lukon, navigoinnin ja AppContainerin. Sourcesin yhteysmuutokset ja import käyttävät samaa exclusive-porttia kuin synkronointi. SecretStore-muutoksen virhe palauttaa aiemmat salaisuudet; report-data säilyy disconnectissa. Windows-tuonti korvaa schema-11-sopimuksen raporttitaulut ja valinnaisesti vastaavat yhteydet, API-avaimet ja raporttivalinnat. WindowsConfigurationTransfer tallentaa salatun palautusjournalin SecretStoreen ennen muutoksia ja tarkistaa Roomin commit-tunnisteen palautuksessa. Application, MainActivity ja sync-worker odottavat keskeytyneen tuonnin palautusta ennen käyttöä. Puretut raporttivälitiedostot siivotaan myös seuraavassa käynnistyksessä; avaimia sisältävää plaintext-tiedostoa ei tehdä. Performance-URL:t ja Bing-sivustot ovat kohdemetadataa, eivät salaisuuksia.

Moduulirajat ja riippuvuussuunnat ovat `AGENTS.md`-tiedostossa. Room-skeema vastaa käsitteellisesti Windows-Numeriksen SQLite-tauluja, mutta Androidin salaisuudet tallennetaan ainoastaan Keystore-pohjaiseen SecretStoreen.
