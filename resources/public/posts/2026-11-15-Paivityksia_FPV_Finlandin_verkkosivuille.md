[:article-lead
  date="2026-11-15"
  header="Päivityksiä FPV Finlandin verkkosivuille"]

FPV Finlandin verkkosivuille on tehty pieni ulkoasu-uudistus sekä useita käytettävyyttä ja sisällöntuotantoa parantavia päivityksiä.

## Uudistettu tapahtumat-sivu ja etusivun tapahtumanosto

Tapahtumat-sivua on kehitetty aiempaa käytännöllisemmäksi. Aiemmin sivulla oli vain kalenterinäkymä, mutta nyt sen rinnalla on myös listanäkymä. Listanäkymässä näytetään oletuksena vain tulevat tapahtumat, ja klikkaamalla saa esiin myös menneet tapahtumat.

Lisäksi tulevat tapahtumat nostetaan nyt näkyviin suoraan etusivulle.

## Tekoälyavusteinen sisällöntuotanto koekäytössä

Kun nykyiset verkkosivut julkaistiin noin vuosi sitten, tavoitteena oli, että sisältöä voisi tuottaa useampi tekijä. Käytännössä keskustelut ja ilmoitukset ovat kuitenkin painottuneet yhdistyksen muihin viestintäkanaviin, minkä vuoksi verkkosivut ja tapahtumakalenteri ovat jääneet päivittämättä ja toiminta on voinut näyttää ulospäin hiljaiselta.

Sisällöntuotannon helpottamiseksi sivuille on nyt otettu koekäyttöön tekoälyavusteinen työnkulku. Järjestelmä toimii kerran viikossa lauantai-iltaisin GitHub Actions -putken avulla:

- Valituista yhdistyksen keskusteluista karsitaan ensin henkilökohtaiset tiedot.
- Teksti käsitellään Google AI Studiossa olevalla Gemini 4 Hydrogen -mallilla, joka arvioi, syntyykö aiheesta tapahtumamerkintä tai artikkeli.
- Mahdollisen ehdotuksen pohjalta luodaan uusi Pull Request verkkosivujen GitHub-repositorioon.

Luodut ehdotukset eivät julkaistu automaattisesti, vaan ne käydään läpi, niitä muokataan tarvittaessa ja ne hyväksytään erikseen ennen julkaisua. Järjestelmä on vielä vahvasti koekäyttövaiheessa.
