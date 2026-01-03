# Uudet sivut FPV Finlandille!

Jäsenistömme, sekä muu yleisö ovat huomanneet, että FPV Finlandin sivut ovat olleet auttamattomasti vanhentuneet.
Olemme saaneet jopa kysymyksiä, että onko yhdistyksemme enää aktiivinen, sillä monet sivustomme materiaalista on ollut
päivätty vuodelle 2022 ja sen jälkeen tullutta sisältöä on ollut hyvin vähän.

**Totuus yhdistyksemme elinvoimaisuudesta on hyvin toisenlainen**: yhteisössämme on jatkuvaa keskustelua, interaktiota
ja muiden auttamista. Yhdistyksemme on kuitenkin tullut siihen ikään, että alkuperäiset FPV Finlandin perustaneet
aktiivit ovat suurelta osin siirtyneet eteenpäin ja yhdistyksen toiminnan ylläpito on siirtynyt uudelle sukupolvelle.

Suuri tekijä vanhojen sivujemme vanhentuneesta sisällöstä johtui yksinkertaisesti niiden iästä. Sivusto oli pistetty
aikoinaan pystyyn Wordpress-alustan päälle ja sen avulla oli tehty yhtä sun toista. Wordpress-kokonaisuuden tarjosi
webbihotelli, johon liittyi omat erikoisuutensa. Wordpress alustana on nykyaikaan vanhentunut ja turhan monimutkainen
yhdistyksen tarpeisiin nähden. Lisäksi FPV Finland on yhdistyksen lisäksi ennenkaikkea yhteisö eikä vanhojen sivustojen
tekninen toteutus tukenut kunnolla tätä.

Lisäkannustimena uusien sivujen tekemiselle synnytti se, että vanhat sivustomme ovat olleet vuosia 
[Frendyn](https://frendy.fi) tarjoamassa ilmaisessa webhotellissa. Nyt kuitenkin ymmärrettävästi Frendy ei enää
kyennyt jatkamaan sivustojemme toiminnan jatkamista ilmaiseksi ja tämä johti taloudelliseen paineeseen tehdä asialle
jotain. Kiitokset Frendylle yhdistyksemme tukemisesta vuosien ajan! Meidän oli kuitenkin aika muuttaa kokonaisuutta.

### Uudet sivut tukevat yhteisöllistä ylläpitoa

Teknisesti uudet sivumme ovat hyvin erilaiset kuin ennen. Sivusto on luotu generoiden lähdemateriaalista HTML-sivuja,
joita palvellaan sellaisenaan käyttäjille. Sivuston takana ei ole tietokantoja tai mitään muutakaan ihmeellistä.
Hyödynnämme [Github](https://github.com) palvelua sivuston materiaalien ja lähdekoodien ylläpitoon ja kuka tahansa
voi Githubin avulla ehdottaa uutta sisältöä sivustolle hyödyntäen sen Pull Request -mallia. 

Kyseinen palvelu on de facto -asemassa ohjelmistoalalla avoimen lähdekoodin kehitykseen ja myös suuri osa FPV 
droneihin liittyvistä  ohjelmistoista ja työkaluista on jaossa sen kautta. Koska kohtuullisen suuri osa 
jäsenistöstämme on keskivertoa  teknisempiä niin Github todettiin riittävän suoraviivaiseksi, monipuoliseksi ja 
ennenkaikkea ilmaiseksi tavaksi hallinnoida sivuston sisällön hallintaa. Voimmekin todeta, että Github korvaa meidän 
tapauksessamme nyt Wordpressin ja sen tarjoamat ominaisuudet.

### Uuden sivustokokonaisuuden palveleminen on ilmaista

Koska yksi tärkeimmistä syistä siirtyä Frendyn palveluista eteenpäin oli kustannukset, niin päävaatimus sivuston
tekniselle toteutukselle oli sen ilmainen tai lähes ilmainen hostaaminen. Tämä vaatimus saatiin täytettyä hyödyntämällä
[Google Cloud](https://cloud.google.com/) -julkipilven tarjoamaa [Firebase](https://firebase.google.com)-alustaa.

Firebase tarjoaa palvelittoman (ns. serverless) alustakokonaisuuden webbiapplikaatioiden, sekä mobiilisovellusten
toteuttamiseen ja Google itse hyödyntää sitä lukuisissa eri palveluissaan. Firebase itsessään ei ole ilmainen, mutta
järkevillä liikennemäärillä se on käytännön ilmainen - erityisesti kun kokonaisuus koostuu vain staattisista sivuista
ilman sen hienompaa toiminnallisuutta. Mikäli FPV Finlandin sivusto ei saa miljoonia vierailuja kuukaudessa niin
palvelu pysyy ilmaisena. Vaikka kovasti toivommekin FPV-harrastuksen valtavirtaistumista niin vahvasti epäilemme,
että emme aivan heti joudu maksamaan tämän sivuston hostauksesta tällä ratkaisulla :)

### Kutsu jäsenistölle sisällön tuottamiseen!

**Toivommekin koko jäsenistöltämme sisältöä uusille sivuillemme!** Erityisesti kaipaamme tapahtumia ja artikkeleita. Jos
olet järjestämässä tapahtumaa niin lisää se [tapahtumakalenteriimme](/tapahtumat.html). Jos haluaisit vaikka kertoa
tarinan dronen rakentamisesta, niin tee siitä uusi [artikkeli](/artikkelit.html).

Ohjeet ja muukin sisältö, sekä lähdekoodi löytyy [Githubista](https://github.com/empperi/fpv-finland-www-site/). Ja
jos on mitään kysyttävää siitä, miten sisältöä kykenee luomaan niin voit aina kysyä apua Slackissa. Mikäli Github ja
sen käyttö ei tunnu kaikesta huolimatta itselle luontevalta, niin voit myös toimittaa sisältöä hallitukselle muussa
muodossa ja hallitus asettaa sen sivustolle paikalleen.

Tehdään yhdessä Suomen johtava ajantasaisen FPV-tiedon sivusto!
