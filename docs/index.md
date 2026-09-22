---
title: "Clavier Créole Guadeloupéen pour Android : Klavyé Kréyòl Karukera"
description: "Le clavier Android intelligent pour écrire en kréyòl guadeloupéen : suggestions bilingues créole/français, accents par appui long, 100 % hors ligne et gratuit."
lang: fr
---

<!-- nav:start -->
<nav class="site">
  <a class="nav-brand" href="./" aria-current="page">⌨️ Klavyé Kréyòl</a>
  <a href="simulateur.html">🧪 Essayer</a>
  <a href="nouveautes.html">🎁 Nouveautés</a>
  <details class="menu" name="navmenu">
    <summary>ℹ️ Le projet</summary>
    <div class="menu-panel">
      <a href="corpus.html">Le corpus en chiffres</a>
      <a href="presskit.html">Dossier de presse</a>
      <a href="partenaires.html">Partenariats institutionnels</a>
      <a href="notes_techniques.html">Notes techniques</a>
      <a href="https://github.com/famibelle/KreyolKeyb">Code source</a>
      <a href="privacy/privacy-policy.html">Confidentialité</a>
    </div>
  </details>
  <a href="faq.html">❓ FAQ</a>
  <a class="nav-cta" href="https://play.google.com/store/apps/details?id=com.potomitan.kreyolkeyboard&amp;referrer=utm_source%3Dlanding%26utm_campaign%3Dlaunch10k%26utm_content%3Dnav">📲 Installer, c'est gratuit</a>
  <details class="menu nav-after" name="navmenu">
    <summary>Déjà installé ?</summary>
    <div class="menu-panel">
      <a href="guide.html">Guide d'installation</a>
      <a href="feedbacks_form.html">Donner son avis</a>
      <a href="beta_onboarding.html">Devenir bêta-testeur</a>
    </div>
  </details>
  <details class="menu nav-after" name="navmenu">
    <summary>📣 Ambassadeurs</summary>
    <div class="menu-panel">
      <a href="ambassadeurs.html">Espace ambassadeurs</a>
      <a href="ambassade.html">Vin Anbasadè</a>
      <a href="charte-ambassadeur.html">Charte de l'ambassadeur</a>
      <a href="tract.html">Tract à imprimer</a>
      <a href="affiche.html">Affiche à imprimer</a>
      <a href="triptyque.html">Triptyque à imprimer</a>
      <a href="publicites.html">Visuels publicitaires</a>
      <a href="charte-graphique.html">Charte graphique</a>
    </div>
  </details>
  <button type="button" class="theme-toggle" aria-label="Changer de thème">🌙</button>
</nav>
<!-- Sur mobile, le bouton de la barre est masqué et remplacé par celle-ci,
     fixée en bas dans la zone du pouce, donc atteignable à toute profondeur
     de défilement. -->
<div class="install-bar">
  <a class="install-bar-cta" href="https://play.google.com/store/apps/details?id=com.potomitan.kreyolkeyboard&amp;referrer=utm_source%3Dlanding%26utm_campaign%3Dlaunch10k%26utm_content%3Dbarre-mobile">📲 Installer, c'est gratuit</a>
  <a class="install-bar-alt" href="simulateur.html">Essayer d'abord dans le navigateur</a>
</div>
<!-- nav:end -->

# Osez le Kréyòl

**Écrire en bon kréyòl, sans avoir à y penser.** Ce n'est pas vous qui
écriviez mal le kréyòl : c'est votre clavier qui ne le connaissait pas.

Klavyé Kréyòl Karukera est le **clavier créole guadeloupéen pour Android**
qui vous **propose les mots justes** pendant que vous tapez, puis ceux qui
viennent habituellement après, et qui **remet les accents** que vous
oubliez. Ses suggestions sortent des textes de Sylviane Telchid, Sonny
Rupaire, Max Rippon, Robert Fontes, Esnard Boisdur et bien d'autres.
Gratuit, open source, zéro pub, 100 % hors ligne.

## Ce qui vous retenait, et ce qui a changé

| | |
|---|---|
| 🤔 **« Mon téléphone corrige mon kréyòl en français »** | Il ne le fait plus. Ce clavier ne remplace jamais un mot par un autre : vos lettres restent exactement les vôtres. |
| 😬 **« Je ne suis pas sûr d'écrire les mots comme il faut »** | Le clavier vous les propose, tirés des textes de nos auteurs, et devine celui qui vient après. Vous n'inventez rien : vous choisissez. |
| 😤 **« Je ne sais jamais où mettre les accents »** | Tapez « kreyol » sans y penser : il devient « kréyòl » dès que vous validez le mot. Les accents oubliés reviennent tout seuls. |
| 🤷 **« De toute façon, personne ne l'écrit »** | <span id="dl-inline">Plus de 3 000</span> personnes l'ont déjà téléchargé. |

<script>
// Le compteur se lit dans le même fichier que la jauge des ambassadeurs, qui
// vit sur sa propre page : un seul chiffre, une seule source. Il est arrondi
// au millier inférieur, donc toujours vrai et toujours rond, et il passera de
// lui-même à « plus de 4 000 » le jour venu. La valeur écrite en dur ci-dessus
// n'est qu'un repli si la requête échoue.
fetch('stats/downloads.json').then(function(r){ return r.json(); }).then(function(s){
  var el = document.getElementById('dl-inline');
  if (!el || !s || !s.current) { return; }
  var arrondi = Math.floor(s.current / 1000) * 1000;
  if (arrondi >= 1000) { el.textContent = 'Plus de ' + arrondi.toLocaleString('fr-FR'); }
}).catch(function(){});
</script>

<div align="center" style="display:flex;justify-content:center;align-items:flex-start;gap:18px;flex-wrap:wrap;margin:22px 0;">
  <figure style="margin:0;max-width:340px;">
    <img src="Screenshots/gif_suggestion.gif" alt="Animation : « An kre » tapé au clavier, un halo suivant le doigt, puis la pastille « kréyòl » touchée dans la barre de suggestions. Le mot s'écrit en entier avec ses accents, et le clavier propose aussitôt les mots qui viennent après" width="340">
    <figcaption style="font-size:14px;color:var(--ink-soft);margin-top:6px;">Trois lettres suffisent : le mot juste est déjà proposé.</figcaption>
  </figure>
  <figure style="margin:0;max-width:340px;">
    <img loading="lazy" src="Screenshots/gif_accent_auto.gif" alt="Animation : « kreyol » tapé lettre par lettre sans accent, un halo suivant le doigt d'une touche à l'autre pendant que les suggestions kréyòl s'affichent au-dessus du clavier. À l'espace, le mot devient « kréyòl » tout seul" width="340">
    <figcaption style="font-size:14px;color:var(--ink-soft);margin-top:6px;">Et si vous tapez tout, les accents se remettent seuls.</figcaption>
  </figure>
</div>

## Vérifiez tout de suite, sans rien installer

Tapez un mot dans ce clavier d'essai : les suggestions kréyòl et
françaises apparaissent comme sur votre téléphone, et vous retrouvez
l'exemple ci-dessus avec vos propres mots.

<div align="center">
  <iframe src="simulateur.html?embed=1" width="380" height="620" style="border:0;max-width:100%;" loading="lazy" title="Simulateur Klavyé Kréyòl Karukera"></iframe>
</div>

## Ils en ont parlé

**[Guadeloupe la 1ère](https://la1ere.franceinfo.fr/guadeloupe/klavye-kreyol-karukera-l-appli-pour-smartphone-qui-facilite-la-redaction-de-messages-en-creole-guadeloupeen-1723969.html)**
(France Télévisions) lui a consacré un sujet au journal de 19h30 et un
article. **Canal 10** l'a présenté dans sa chronique Tech.
**[France-Antilles](https://www.guadeloupe.franceantilles.fr/actualite/economie/si-le-creole-guadeloupeen-nexiste-pas-petit-a-petit-on-sefface-1088477.php)**
en a fait un entretien : « Si le créole guadeloupéen n'existe pas, petit à
petit, on s'efface ». Les extraits sont dans le [dossier de
presse](presskit.html).

Et ceux qui l'utilisent, sur Google Play :

> ⭐⭐⭐⭐⭐ « Je passe ma note de 4 à 5 étoiles car depuis la maj, il y a les
> traductions des mots dans les jeux juste parfait ! »
>
> <small>Lionel TAURUS, 8 septembre 2026 · <a href="https://play.google.com/store/apps/details?id=com.potomitan.kreyolkeyboard&hl=fr">les 52 avis sur Google Play</a></small>

<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.potomitan.kreyolkeyboard&referrer=utm_source%3Dlanding%26utm_campaign%3Dlaunch10k%26utm_content%3Dhero">
    <img src="Screenshots/GetItOnGooglePlay_Badge_Web_color_French.svg" alt="Télécharger Klavyé Kréyòl Karukera sur Google Play" width="60%">
  </a>
</div>

<p align="center"><em>Gratuit, sans publicité. L'installation prend deux
minutes, et le <a href="guide.html">guide</a> vous montre chaque écran.</em></p>

> 🔒 **Ce que vous tapez ne quitte pas votre téléphone.** Pas de compte, pas
> de serveur, aucune sauvegarde dans le nuage, pas même celle d'Android. Le
> code est public et vérifiable, et la [politique de
> confidentialité](privacy/privacy-policy.html) le dit en toutes lettres.

## Le clavier en action

**Une phrase entière, dans un vrai SMS.** Le mot « kréyòl » s'y écrit deux
fois : la première en touchant la suggestion, la seconde tapé sans accent,
que le clavier remet à l'espace. Comme « palé » et « maké » juste après.

<div align="center">
  <img loading="lazy" src="Screenshots/KlavyéAnAktion.gif" alt="Animation : la phrase « An kréyòl nou ka palé, an kréyòl nou ka maké » écrite dans un SMS, un halo suivant le doigt d'une touche à l'autre. Le premier « kréyòl » est posé en touchant la suggestion kréyòl ; le second est tapé sans accent et le clavier les rétablit à l'espace, comme « pale » qui devient « palé » et « make » qui devient « maké »" width="380">
</div>

## Ce que ça change pour vous

**Klavyé Kréyòl Karukera aide à deux niveaux : écrire le kréyòl au
quotidien, et le pratiquer davantage.**

### Pour écrire

| | |
|---|---|
| 💡 **Le mot juste vous est proposé, et le suivant aussi** | Les suggestions sortent d'un corpus littéraire créole, pas d'un dictionnaire générique. Le mot posé, le clavier propose ceux qui viennent habituellement après |
| ✍️ **Les accents ne se perdent plus** | Appui long pour les taper, et depuis peu, ils reviennent tout seuls si vous les oubliez : vos lettres ne changent jamais, seuls les accents s'ajoutent |
| 🇫🇷 **Un seul clavier pour le kréyòl et le français** | Le français prend le relais quand aucun mot créole ne correspond, pas besoin de changer de clavier |
| 🔢 **Il s'adapte à ce que vous écrivez** | Pavé de chiffres pour un numéro ou une date, arobase pour un e-mail : sans réglage à chercher |
| 🚫 **Zéro publicité, zéro tracker** | Le clavier reste concentré sur l'essentiel |
| 🆓 **Gratuit et open source** | Code public sur [GitHub](https://github.com/famibelle/KreyolKeyb), licence MIT |

### Pour pratiquer

**La gamification n'est pas un gadget : elle donne envie d'écrire encore
plus de kréyòl.** Chaque mot tapé peut devenir une carte à collectionner,
et chaque partie fait découvrir un mot qu'on n'aurait pas cherché
autrement.

| | |
|---|---|
| 📔 **Sanblé, le carnet des mots gagnés** | Chaque mot trouvé dans un jeu devient une carte, avec son sens en français et une phrase d'un auteur guadeloupéen |
| 🔁 **Sonjé, la révision qui vous relance au bon moment** | Si vous avez déjà réécrit le mot au clavier depuis, la carte avance toute seule : le clavier fait office d'examen |
| 🔡 **Six jeux, dont un jouable sans connaître un mot de créole** | Mo an plas donne les mots tout faits, à vous de les caser dans la grille |
| 🏆 **Une progression, de Pipirit à Potomitan** | Au fil des mots que vous tapez vraiment |

<div align="center" style="display: flex; justify-content: center; align-items: center; gap: 10px; flex-wrap: wrap;">
   <img loading="lazy" src="Screenshots/gif_jeu_mokarenaj.gif" alt="Animation : les essais RIPAJ, TIRAJ puis VIRAJ tapés au clavier, les cases se colorant en vert, orange et gris jusqu'au message « Bravo ! »" width="30%">
   <img loading="lazy" src="Screenshots/gif_eventail_cartes.gif" alt="Animation : un casier de la boîte s'ouvre et ses huit cartes se déploient en éventail, défilent sous le doigt (kouté, pyé, rivyè, solèy), puis la carte pyébwa s'ouvre en grand : arbre, avec une phrase du corpus" width="30%">
   <img loading="lazy" src="Screenshots/gif_boite_leitner.gif" alt="Animation : la révision Sonjé. Cinq cartes (pyébwa, solèy, kouté, rivyè, chanjé) montrent leur mot, se retournent pour donner leur sens, puis se rangent selon « Je savais » ou « Pas su » ; la boîte se met à jour" width="30%">
</div>

## Un projet de préservation linguistique

Le créole est parlé par **1,6 million de locuteurs en France**.

Ce n'est donc pas juste un clavier : **chaque message écrit en kréyòl aide
notre langue à exister** dans le numérique, là où elle se joue désormais
chaque jour. Le dictionnaire et les suggestions s'appuient sur les œuvres
d'écrivains, de linguistes et d'artistes qui ont donné au créole
guadeloupéen ses lettres de noblesse.

Il fait partie de l'écosystème **Potomitan™**, qui développe aussi
[POTOMITAN](https://potomitan.io), un traducteur français ↔ créole
guadeloupéen pensé pour les urgences et les démarches administratives
(soutenu par la Préfecture de Guadeloupe via Lab'An Nou, présenté par
Orange Antilles-Guyane).

## À découvrir en exclusivité 🎁

**Le clavier progresse chaque semaine.** Ce n'est pas une application
livrée puis abandonnée : les nouveautés arrivent en continu, parfois en
avance sur le Play Store.

<div class="card" style="margin:16px 0;display:flex;justify-content:space-between;align-items:center;gap:14px;flex-wrap:wrap;">
  <span id="ef-teaser" style="color:var(--ink-soft);">Chargement…</span>
  <a href="nouveautes.html" class="btn" style="padding:10px 22px;white-space:nowrap;">🎁 Voir les nouveautés</a>
</div>

<script>
fetch('stats/exclusive_features.json').then(function(r){ return r.json(); }).then(function(d){
  var n = d.features ? d.features.length : 0;
  document.getElementById('ef-teaser').textContent = n
    ? n + ' fonctionnalité' + (n > 1 ? 's' : '') + ' déjà disponible' + (n > 1 ? 's' : '') + ', pas encore sur le Play Store.'
    : 'Le Play Store est à jour : rien en exclusivité pour le moment.';
}).catch(function(){});
</script>

## Vin Anbasadè ! 📣

**Vous voulez aider le kréyòl à rayonner ?** Notre page ambassadeurs vous
donne tout : les contacts des médias locaux, les emails pré-remplis en un
clic, quoi dire si vous appelez une radio, et un tract à imprimer pour
le laisser chez le boulanger, le pharmacien ou la boutique du coin.

<div align="center" style="margin: 16px 0; display:flex; justify-content:center; gap:10px; flex-wrap:wrap;">
  <a href="ambassade.html" class="btn" style="padding:12px 28px;">🤝 Devenir ambassadeur du Klavyé Kréyòl</a>
  <a href="tract.html" class="btn" style="padding:12px 28px;">🖨️ Imprimer le tract</a>
</div>

**Pou laprès :** [dossier de presse / press kit](presskit.html)

## Contacts

Les questions les plus posées, « est-ce que ça remplace mon clavier ? »,
« mes messages partent-ils quelque part ? », ont leur réponse sur la page
**[questions fréquentes](faq.html)**.

Pour le reste, une proposition, un partenariat ? Écrivez à
**[contact@potomitan.io](mailto:contact@potomitan.io)**.

Le code source est ouvert et public sur
[GitHub](https://github.com/famibelle/KreyolKeyb).

---

<p align="center" style="font-size:22px;font-weight:700;line-height:1.35;margin:28px 0 6px;">An kréyòl nou ka palé,<br>an kréyòl nou ka maké.</p>

<p align="center" style="color:var(--ink-soft);margin:0 0 24px;">C'est ce que vous rejoignez en l'installant.</p>

<!-- footer:start -->
<footer class="site">
  <div class="footer-cta">
    <p class="footer-baseline">An kréyòl nou ka palé, an kréyòl nou ka maké.</p>
    <a class="btn primary" href="https://play.google.com/store/apps/details?id=com.potomitan.kreyolkeyboard&amp;referrer=utm_source%3Dlanding%26utm_campaign%3Dlaunch10k%26utm_content%3Dpied">📲 Installer, c'est gratuit</a>
  </div>
  <div class="footer-cols">
    <div>
      <strong>Le clavier</strong>
      <a href="./" aria-current="page">Accueil</a>
      <a href="simulateur.html">Essayer en ligne</a>
      <a href="guide.html">Guide d'installation</a>
      <a href="faq.html">Questions fréquentes</a>
      <a href="nouveautes.html">Nouveautés</a>
    </div>
    <div>
      <strong>Le projet</strong>
      <a href="corpus.html">Le corpus en chiffres</a>
      <a href="presskit.html">Dossier de presse</a>
      <a href="partenaires.html">Partenariats institutionnels</a>
      <a href="notes_techniques.html">Notes techniques</a>
      <a href="ergotherapie.html">Fiche ergothérapie</a>
      <a href="comparatif.html">Comparatif des claviers</a>
      <a href="comparatif-gboard.html">Mesures face à Gboard</a>
      <a href="https://github.com/famibelle/KreyolKeyb">Code source</a>
    </div>
    <div>
      <strong>Faire connaître</strong>
      <a href="ambassadeurs.html">Espace ambassadeurs</a>
      <a href="ambassade.html">Vin Anbasadè</a>
      <a href="charte-ambassadeur.html">Charte de l'ambassadeur</a>
      <a href="tract.html">Tract</a>
      <a href="affiche.html">Affiche</a>
      <a href="triptyque.html">Triptyque</a>
      <a href="publicites.html">Visuels publicitaires</a>
      <a href="charte-graphique.html">Charte graphique</a>
    </div>
    <div>
      <strong>Participer</strong>
      <a href="beta_onboarding.html">Devenir bêta-testeur</a>
      <a href="feedbacks_form.html">Donner son avis</a>
      <a href="jauge.html">La jauge des 10 000</a>
      <a href="mailto:contact@potomitan.io">contact@potomitan.io</a>
    </div>
  </div>
  <div class="footer-legal">
    <span>Potomitan™, Teknoloji pou tout moun.</span>
    <a href="privacy/privacy-policy.html">Politique de confidentialité</a>
    <span>Gratuit, open source (MIT), zéro pub, 100&nbsp;% hors ligne.</span>
  </div>
</footer>
<!-- footer:end -->
