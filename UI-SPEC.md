# UI specification

Numeris käyttää Material 3:a, edge-to-edge-sisältöä ja hiusviivatyyliä: lähes musta pohja, sisältö erotetaan ohuilla viivoilla eikä täytetyillä laatikoilla. Fontti on Inter 4.1 (painot 400 ja 500, `res/font`, lisenssi `core/designsystem/INTER-LICENSE.txt`); isot luvut ovat kevyitä ja tiiviisti välistettyjä, selitteet pieniä ja hillittyjä. Dynamic Color on pois käytöstä. Kaikki värit, välit, muodot ja tekstityylit ovat `:core:designsystem`-moduulissa (`NumerisColors`, `NumerisTokens`, `NumerisTheme`); näkymät eivät määrittele omia arvoja.

## Värit

- Pinnat: Background `#08090A`. Raporttisisältö on läpinäkyvää hiusviivareunalla (`hairlineSurface`, Divider `#1D1F23`). Täytettyä Card-pintaa `#111214` käyttävät vain kelluvat pinnat (alapaneelit, valikot, dialogit). Control `#18191C`, kontrollien reuna `#2A2C31`, alavalikko `#0C0D0F`.
- Primary text `#FAFAFA`, Secondary text `#D4D4D8`, Tertiary text `#8B8B94`
- Accent `#FF7A1A`: vain ensisijainen toiminto, valittu välilehti ja kaavion pääsarja. Ei linkkien, URL-osoitteiden eikä selitteiden värinä.
- Kaaviot: pääsarja Accent, toinen sarja petrol `#7DB7C2`, kolmas `#A1A1AA`, edellinen jakso `#3F3F46` katkoviivalla. Pääluvun trendi on ohut viiva ilman täyttöä.
- Tilat (`PillTone`): Positive `#4ADE80`, Negative `#F87171`, Warning `#FBBF24` tummilla taustoillaan, Neutral Control-taustalla. Muutos edelliseen jaksoon näytetään värikoodattuna tekstinä (`ChangeText`), ei pillerinä.
- Sovelluskuvakkeen ja käynnistysnäkymän tausta `#0E1014`. Alavalikon valittu kohde: oranssi kuvake ja valkoinen nimi ilman taustapilleriä.
- Sivustoilla on tunnusvärit (`NumerisColors.SitePalette`, `siteColor`): sama sivusto saa saman värin kirjainmerkissä, osuuspalkissa ja korteissa kaikissa näkymissä.
- Omien sivustojen merkkinä on sovelluksen logo (`SiteAvatar`, `siteLogo`; kuvat `site_logo_*` sovellusprojekteista). Tuntematon sivusto saa kirjainmerkin tunnusvärillä. Merkki näkyy Overview'n sivustoissa ja suosituimmissa sivuissa, Vitals-korteissa, Healthin sivustolistassa ja sivustovalinnassa.

## Hierarkia ja komponentit

- Jokaisen päänäkymän alussa on yksi pääluku (`HeroMetric`): selite, iso kevyt luku, muutos edelliseen jaksoon ja ohut trendiviiva ilman korttia. Sen alla enintään kolme pientä lukua hiusviivojen välissä (`CompactKpiRow`).
- Overview: pääluku, sivustojen osuuspalkki, kolme pientä lukua, huomiot yhtenä avattavana rivinä (`AttentionSummary`), sivustot ja viisi suosituinta sivua. Suosituimmat sivut näyttävät sivuston logon ja polun; selitteessä ovat vain toteutuneet haku-klikkaukset ja "Not indexed".
- Vähän aputekstiä: osio-otsikoilla ei ole selitettä, kun otsikko ja sisältö riittävät. Tyhjiä arvoja ei näytetä selitteissä ("—" vain kiinteissä ruudukoissa). Jakson päivämäärät näkyvät jaksovalikon toisella rivillä.
- Päänäkymän otsikon alla on datan tuoreus tilapisteenä ja tekstinä (`FreshnessStatus`): piste on vihreä alle vuorokauden ikäiselle datalle, muuten harmaa.
- Pitkät listat näyttävät viisi riviä ja "See all" -painikkeen (`ShowMoreButton`). Hillityt tekstipainikkeet ("See all", "Show data table") linjautuvat sisällön vasempaan reunaan (`InlineTextButton`). Jakaumapalkit ovat 2 dp:n viivoja ilman rakoa. Vastaukset näytetään värijakopalkkina (`ShareBar`), yksittäiset statuskoodit avautuvat pyynnöstä.
- Traffic-välilehdet: Traffic ja Web Analytics. Välimuisti ja tietoturva ovat Trafficin alaosio.
- Vitals: yksi kortti sivustoa kohden (Loading, Response, Stability ja mobiilin ja desktopin lab-piste); yksityiskohdat avautuvat korttia napauttamalla.
- Search Indexing: osuuspalkki (indeksoitu, ei indeksoitu, tarkistamatta) ja oletuksena vain tarkistettavat URL:t.
- Synkronoinnin tila on kehyksessä tiiviinä pillerinä (esim. "2 sources failed"), josta pääsee Sourcesiin. Onnistunut päivitys ei vie tilaa.
- Sovelluskuvake ja Android 12+ -käynnistysnäkymä käyttävät Windows-version logoa (`ic_numeris_logo`, lähde `Assets/Logo/numeris-logo.svg`) tummalla taustalla.

- Päänäkymä: `NumerisScreenHeader` (otsikko, tuoreus, päivitys), hiusviivareunaiset suodatinsirut (`ReportFilters`: sivusto, jakso ja tarvittaessa palveluntarjoaja samalla rivillä) ja alasivut välilehtinä (`NumerisTabs`).
- Alanäkymät (Health, Sources, Page detail, lähteen lomake) käyttävät `NumerisTopBar`-palkkia, jossa on takaisin-nuoli.
- Tunnusluvut näytetään `KpiGrid`-ruudukossa kahdessa sarakkeessa: selite, iso tabulaarinen luku, muutos edelliseen jaksoon ja tarvittaessa selite. Pienempi on parempi -mittareille (sijoitus, virheet, uhat) muutoksen sävy käännetään.
- Listat ovat hiusviivareunaisessa `ListCard`-kortissa ohuilla erottimilla, ei rivikohtaisina kortteina. `ListRow`: otsikko, selite, valinnainen oikean reunan arvo tai pilleri.
- Tila ilmaistaan `StatusPill`-pillerillä tai hiusviivareunaisen `NoticeCard`-kortin kuvakkeella. Yhteyden toiminnot (Edit, Test connection, Disconnect) ovat ⋮-valikossa.
- Kielletty: `·`, `•` tai ` | ` erottimina sekä värillinen pystypalkki kortin, rivin tai paneelin reunassa. Erottele asettelulla, omalla rivillä, painolla tai pillerillä.
- Raakadata muotoillaan: suhteelliset ajat, lyhyet päivämäärät (`CompactFormat`), URL ilman protokollaa, palvelun nimi tunnisteen sijaan. Cloudflaren `/cdn-cgi/`-polut eivät näy suosituimmissa sivuissa.

## Mitoitus ja saavutettavuus

Spacing perustuu 4 dp:n asteikkoon. Kortin säde on 14 dp, ruudun 12 dp ja kontrollin 8 dp. Hiusviiva on 1 dp. Kosketusalue on vähintään 48 x 48 dp. Kompakti ikkuna käyttää viittä pääkohdetta: Overview, Traffic, Search, Vitals ja More. Health ja Sources avautuvat More-näkymästä. Leveä ikkuna käyttää navigation railia. Suurella fontilla alavalikon kohteilla on ikonit ja saavutettavat nimet.

Suodatinsirut rivittyvät tilan loppuessa. KPI-ruudukko muuttuu yhdeksi sarakkeeksi alle 280 dp:n leveydessä tai vähintään 150 %:n fonttiskaalalla. Tekstiä ei katkaista: pitkät arvot ja URL:t rivittyvät. Raporttilistat säilyttävät kaikki rivit; lisätiedot avautuvat riviä napauttamalla. Page detail säilyttää sivuston, koko sivupolun ja valitun jakson.

## Kaaviot

Kaavioissa on lyhyet päivämäärät, väripisteselite (vertailukaaviossa This period ja Previous period), puuttuvien päivien aukot ja Show data table -tekstiesitys. Valitun päivän arvot näkyvät kaavion alla. Lyhyet count/bytes-sarjat käyttävät pylväitä, tiheät sarjat sekä ratio-, score- ja ms-sarjat viivoja. Edellinen jakso kohdistetaan nykyisen jakson päiville. Pienillä kokonaisluvuilla (enintään 5) y-akselin askel on 1. Tyhjä sarja näyttää tekstin "No data for this period" kaavion sijaan. Health näyttää sivustokohtaiset kaaviot vain valitulle sivustolle.

Ei bluria, glassmorphismia, gradienttiorbeja, jatkuvaa pulssia tai koristeellista loop-animaatiota. [Design-preview](docs/design-preview/README.md) on havainneaineisto; toteutuksen hyväksyntä ja laitekuvat kirjataan [hyväksyntäraporttiin](docs/parity-acceptance.md).
