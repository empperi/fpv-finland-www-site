# FPV Finlandin www-sivut

Tämä Github repository sisältää FPV Finlandin sivujen koko sisällön ja logiikan. Sivut on toteutettu seuraavalla
arkkitehtuurilla:

- Hostaus Google Cloudissa Firebasen päällä staattisena webbisivustona
- Sivusto generoidaan Clojure-pohjaisella koodilla hyödyntäen Stasista
- Sivujen sisältö kirjoitetaan Markdown-tiedostoihin mistä lopulliset sivut generoidaan
- Palvelimia ei ole, mutta backend-logiikan osalta hyödynnetään Firebasen integraatiota Google Cloud Functioneihin

Perustelut tälle arkkitehtuurille ovat seuraavat:

- Kustannukset saadaan nolliin. Firebasen tarjoama ilmainen palvelu riittää tarjoamaan koko sivuston hostauksen
  ilman mitään kuluja. Cloud Function -kutsut maksavat ensimmäisen 5 miljoonan kutsun per kk jälkeen. Tämä raja ei
  tule ikinä vastaan tai jos tulee, niin silloin meillä ei ole yhdistyksenä enää rahaongelmia.
- Vanha sivusto oli Wordpressin päällä mikä on tolkuttoman monimutkainen kokonaisuus FPV Finlandin tarpeisiin mikä on
  lähinnä staattisen sisällön tuottaminen ja tapahtumakalenterin ylläpito. Nyt uutta sisältöä voi luoda yksinkertaisesti
  luomalla uuden Markdown-tiedoston oikeaan paikkaan (tästä alla lisää).
- Tämä malli mahdollistaa Githubin PR:ien avulla sisällön tuottamisen jäsenistön toimesta. Teknistä osaamista tarvitaan
  hieman, mutta tämä rajoittuu hyvin minimaaliseen osaamisvaatimukseen. Jos osaamista ei ole, niin aina voi kirjoittaa
  artikkelisisällön, jolloin joku joka osaa luoda PR:n tekee sen hänen puolestaan. Joka tapauksessa tämän myötä
  sisällön tuottaminen ei ole enää vain hallituksen vastuulla, vaan kuka tahansa voi osallistua siihen. Kontrolli siitä,
  että mitä sivustolle kuitenkin päätyy säilyy hallituksella.

## Miten luon uutta sisältöä?

Sivusto koostuu joukosta staattisesti määriteltyjä päänavigaatiosivuja. Uusien tällaisten lisääminen vaatii
koodimuutoksia ihan siksikin, että se vaikuttaa sivuston layouttiin. Olemassa olevia sivuja voi kuitenkin muokata ja
naputella niihin sisältöä. Nämä löytyvät [tästä hakemistosta](./resources/public/pages).

Näiden lisäksi sivustolla on dynaamisempi artikkelien (tai postauksien) käsite. Näitä voi luoda vapaasti uusia. Nämä
tiedostot [luodaan tähän hakemistoon](./resources/public/posts). Kun luot uutta artikkelia niin tiedostonimen tulee
olla seuraavaa muotoa:

`yyyy-MM-dd-Kuvaava_nimi_artikkelille.md`

Eli vaikkapa `2025-12-27-Parhaat_vinkit_oman_dronen_rakentamiseen.md`. Tiedostonimestä parsitaan mm. päivämäärä, jota
hyödynnetään sivugeneroinnissa.

Artikkelin tulisi alkaa h1-tason otsikolla. Eli:

```markdown
# Mun otsikko

Ja sisältöä.
```

Markdown-tiedostojen käsittelyssä on hieman ekstralogiikkaa (jota voi lukea [täältä](./src/fpvfinland/pages.clj)) ja
niille tehdään seuraavaa:

1. Ensimäisen rivin h1-tason otsikko luetaan ja sitä käytetään listauksissa ja hauissa kuvaamaan artikkelia
2. Liidi-kuva tunnistetaan erikseen jos sellainen on ja sille asetetaan omat tyylinsä (alla esimerkki)

Lisäksi etusivulle voi määritellä liidi-videon. Siitä on esimerkki nähtävissä suoraan kyseisen sivun markdown
-tiedostossa.

Jos haluat artikkeliisi ns. liidi-kuvan niin määritä se seuraavanlaisella aloituksella:

```markdown
# Tähän se otsikko

![LEAD: Tähän liidiin liitettävä teksti](./public/images/sun-liidikuva.jpg)
```

Luonnollisesti sinun pitää lisätä tuo liidikuva määriteltyyn hakemistoon, jotta kuva näkyy. Käyttäkää järkeviä 
resoluutioita kuvissa. Koska tämä on puhtaasti staattista sivugenerointia ja kaikki menee versionhallintaan niin emme
halua paisuttaa asioita tolkuttoman suureksi. Jos et osaa skaalata kuvia järkeväksi niin kysy apua Slackissa.

Muuten artikkelin kirjoittamisessa ei ole mitään erityistä. Jos Markdown ei ole tuttu, niin se on hyvin yksinkertainen
formaatti, jonka oppii nopeasti ja jota on helppo kirjoittaa ja lukea. Ohjeita syntaksiin löytyy 
[täältä](https://www.markdownguide.org/basic-syntax/). Tämäkin ohjesivu mitä luet on kirjoitettu Markdownilla ja voit
avata sen suoraan nähdäksesi sen miltä se näyttää auki kirjoitettuna.

Jos haluat käyttää CSS-tyylejä mitä ei ole vakiona tarjolla, niin niitä voi vapaasti lisätä. Uudet postaukset menevät
PR-prosessin läpi, joten jos ne eivät ole "ihan timanttisia" niin muut pystyvät auttamaan ennenkuin muutokset menevät
liveksi. Eli toivottavasti kynnys kaikenlaiseen säätämiseen on kohtuullisen matala.

## Miten näen kirjoittamani sisällön samanlaisena kun se on sitten itse sivulla?

Jos haluat nähdä kirjoituksesi "valmiina" sitä kirjoittaessa niin tähän on muutamakin tapa:

1. Docker ja Docker Compose
2. Java ja Leiningen
3. Tekemällä PR:n Githubissa

### Docker ja Docker Compose

Tämä on ylivoimaisesti yksinkertaisin tapa mikäli sinulla on jo asennettuna Docker koneellesi. Dockerin itsensä
asentaminen voi vaatia hieman temppuja (Windowsissa tarvitset WSL:n jne.), joten jos et ole tekninen sielu eikä
sinulla ole vielä Dockeria niin tämä **voi** olla haastavahkoa. Älä kuitenkaan lannistu välittömästi. Ohjeet
asentamiseen Windowsille [löytyy täältä](https://docs.docker.com/desktop/setup/install/windows-install/). Ohjeet
taas sen asentamiseen Macille [löytyy täältä](https://docs.docker.com/desktop/setup/install/mac-install/). Jos
käytät Linuxia niin et tarvitse ohjeita :)

Joka tapauksessa jos sinulla on Docker niin voit käynnistää kokonaisuuden yksinkertaisesti kirjoittamalla:

```sh
docker compose up
```

Ensimmäisellä kerralla käynnistymiseen menee hetki, mutta kun kaikki on tehty niin voit avata `http://localhost:3000`
nähdäksesi sivuston. Kaikki muutokset mitä teet Markdown-tiedostoihin tai lisätyt uudet artikkelit ilmestyvät suoraan
lennossa näkyviin. Samoin muutokset tyylitiedostoihin tai uudet kuvatiedostot näkyvät samalla tavalla.

Tällä tavalla ajettuna sivulataukset voivat olla hieman hitaampia ja et pysty tekemään muuta kehitystyötä, mutta
tämä on yksinkertaisin tapa päästä kirjoittamaan artikkeleita niin, että näet lopputuloksen suoraan.

### Java ja Leiningen

Toinen tapa mahdollistaa myös täysiverisen muun kehitystyön. Tällöin sinun tulee asentaa Javan JDK sekä Leiningen.
Etäisesti modernissa Windowsissa on `winget` jolla JDK asentuu helposti:

```sh
winget install --id EclipseAdoptium.Temurin.25.JDK
```

Tätä kirjoittaessa JDK 25 on uusin, mutta halutessasi varmista että onko uudempia tarjolla.

Leiningen ei ikävä kyllä ole tarjolla wingetin alta. Onneksi kuitenkin sen oma käynnistysskripti osaa ladata itse
itsensä, ohjeet [Leiningenin sivuilla](https://leiningen.org). Jos haluat käyttää pakettihallintaa, niin MacOS:lla
Brew tarjoaa sen suoraan (https://formulae.brew.sh/formula/leiningen), Windowsilla vaihtoehtoinen pakettihallinta
Scoop (https://scoop.sh) tarjoaa Leiningenin (https://bjansen.github.io/scoop-apps/main/leiningen/). Linuxin
pakettihallintoja on useita, joten tarkista omasi tilanne tai käytä Leiningenin omaa skriptiä.

Kun molemmat on asennettuna niin voit tarkistaa, että homma onnistui:

```sh
❯ lein version
Leiningen 2.12.0 on Java 25.0.1 OpenJDK 64-Bit Server VM
```

Komennon tulos pitäisi ola jotain yllä kuvatun tyylistä. Jos on, niin kaikki on valmista!

Jos haluat päästä samaan kuin yllä kuvatulla Docker-ohjeella niin tässä vaiheessa sinun tarvitsee ajaa vain yksi
komento:

```sh
lein run-local
```

Tätä komentoa Docker-lähestyminenkin käyttää pinnan alla. Lopputulos on sama, mutta suurella todennäköisyydellä
jonkin verran nopeampana sillä välissä ei ole WSL:ää ja Dockeria ja sen levy mountteja (erityisesti Windowsilla
hidastaa).

Jos pääsit tähän asti niin voisit myös tehdä muuta devausta. Tästä lisää alla.

### Tekemällä PR:n Githubissa

Tähän repoon on integroitu Github Actionsin kautta kyky projektin automaattiseen siirtämiseen oikeaan ympäristöön.
Kaikki muutokset `master` haaraan Gitissä päätyvät suoraan julkaistuksi webbisivustolle. Jos kuka tahansa avaa
uuden PR:n niin tämän PR:n koodimuutokset ilmestyvät näkyviin Internettiin julkisesti. Webbiosoite, mihin tulos
ilmestyy näkyy PR:n käyttöliittymässä kun se on saatu sinne ajoon.

Koska PR:n luominen on osa prosessia millä kirjoituksia saadaan näkyviin nettiin niin tämä on se viimeinen piste,
milloin ainakin näet muutoksesi livenä hiin halutessasi. Tätä varten sinun ei tarvitse tehdä mitään erityistä.
[Githubilla on ohjeet PR:n tekemiseen](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request),
jos se ei ole tuttua.

## Miten kontribuoin itse koodiin?

Koodi on tehty Clojurella, joten kyseinen ohjelmointikieli pitäisi olla edes auttavasti hyppysissä. Clojure on
funktionaalinen ohjelmointikieli, joka voi vaatia hieman totuttelemista osaavallekin koodarille jos hän ei ole
koskaan tutustunut funktionaaliseen ohjelmointiin. Jos olet tällainen, niin ota tämä mahdollisuutena oppia jotain
uutta ja nousta uudelle tasolle koodarina! Lupaan, että Clojuren opettelu laajentaa ajatteluasi :)

[Aphyrillä on blogipostausten sarja](https://aphyr.com/tags/Clojure-from-the-ground-up), millä pääsee hyvin alkuun.
Lisäksi tietysti voit pyytää Slackissa apua Empperiltä.

Clojurea osaavalle toteutuksessa ei ole mitään hirveän ihmeellistä. Oleellisin gotcha on siinä, että koska toteutus
käyttää [Stasista](https://github.com/magnars/stasis) niin webbisivujen palveleminen tapahtuu hieman tavallisesta
poikkeavasti. Stasis luo määritellyistä sivuista ja resursseista staattisia tiedostoja, mutta devausvaiheessa se
luo ne muistiin. Joka tapauksessa tämä tarkoittaa sitä, että **jokaisen sivun koodi ajetaan aina heti**. Tämä myös
takaa sen, että sivujen logiikassa ei voi olla mitään sellaista, mitä ei voitaisi ajaa sivujen luontihetkellä niin,
että lopputulos voitaisin tulostaa osaksi staattisia resursseja.

Koska sivusto perustuu staattisiin sivuihin niin javascriptin osuus on äärimmäisen pienessä roolissa. Koska kuitenkin
järkevä sivusto tarvitsee edes **jotain** dynaamisuutta, niin tämä osuus on tehty ihan plain-old javascriptinä. Ei
nodeja, webpackeja, typescriptejä tai mitään muutakaan. Vain vanhaa kunnon javascriptiä as-is. Löytyy hakemistosta
[./resources/public/script/scripts.js](./resources/public/script/scripts.js). Sillä hetkellä jos alamme tarvitsemaan
jotain hienompaa niin koska codebase on Clojurea niin järkevintä on integroida kokonaisuuteen ClojureScript. Nyt
toistaiseksi kuitenkin näin. Keep it simple.