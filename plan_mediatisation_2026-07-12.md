# Plan de médiatisation — Klavyé Kréyòl Karukera

**Objectif :** 10 000 téléchargements. **Budget :** 5 €.

## Calibration (données réelles au 12/07/2026)

- La fiche Play Store affiche aujourd'hui **100+ téléchargements** : l'objectif
  est donc un **×100**, pas une simple accélération.
- Benchmark : les claviers créole haïtien génériques (thèmes/photo keyboards,
  sans intelligence linguistique) dépassent les 100 k installations — mais le
  créole haïtien compte ~11 M de locuteurs. Rapporté aux ~400 k habitants de
  la Guadeloupe + la diaspora antillaise en métropole (plusieurs centaines de
  milliers de personnes), 10 000 ≈ 1-2 % du public adressable. **Ambitieux
  mais atteignable** — à condition de toucher la diaspora, pas seulement
  l'archipel.
- Aucun concurrent direct n'existe pour le kréyòl guadeloupéen avec
  suggestions intelligentes : l'angle « premier et seul » est factuel.

## Constat de départ

5 € ne finance aucune campagne publicitaire efficace : les enchères minimales
Facebook/Google Ads tournent autour de 1-5 €/jour, et un budget total de 5 €
s'épuiserait en quelques heures pour quelques centaines d'impressions sans
volume significatif. La stratégie repose donc à 100 % sur des leviers
gratuits : bouche-à-oreille, communautés existantes, presse locale, et
référencement naturel de la fiche Play Store (ASO).

Ce que j'ai livré dans le code cette session, pour amorcer le bouche-à-oreille :
un bouton **« Partager l'application »** dans l'onglet À Propos
(`SettingsActivity.kt`), qui ouvre le sélecteur de partage natif Android avec
un message pré-rempli (créole + lien Play Store). C'est le seul levier viral
qu'on peut réellement automatiser depuis le code — le reste demande une
action humaine (poster, écrire, appeler) que je ne peux pas exécuter à ta
place (pas de compte réseaux sociaux, pas d'accès e-mail).

## Canaux prioritaires

| Canal | Effort | Coût | Pourquoi |
|---|---|---|---|
| Groupes Facebook diaspora (Guadeloupe, Martinique, Guyane) | Faible | 0 € | Audience déjà concentrée, forte affinité culturelle, partage naturel |
| WhatsApp (statut personnel + groupes familiaux/associatifs) | Très faible | 0 € | Canal n°1 de communication dans la diaspora antillaise |
| Presse locale (France-Antilles, Guadeloupe la 1ère, RCI Guadeloupe, Antilla) | Moyen | 0 € | Portée large, angle « patrimoine + tech » vendeur |
| Auteurs/artistes cités dans l'app (Telchid, Rippon, Rupaire, Fontes, Boisdur...) | Moyen | 0 € | Ils sont valorisés par le projet — partage naturellement motivé, boucle de reconnaissance |
| Milieu éducatif (Université des Antilles, dépt. Créole, profs de CAPES créole) | Moyen | 0 € | Public captif, usage pédagogique légitime, prescripteurs |
| Communautés tech (Hacker News Show HN, r/programming, r/opensource) | Faible | 0 € | Angle open-source + préservation linguistique très bien reçu sur HN |
| Réseaux créolophones (Instagram/TikTok, hashtags #kreyol #gwada #guadeloupe) | Moyen | 0 € | Format court, viral, jeune public |
| Product Hunt | Faible | 0 € | Lancement gratuit, trafic ponctuel + backlink |
| Fiche Play Store (ASO) | Faible | 0 € | Déjà bien rédigée ; ajouter une courte vidéo de démo augmente la conversion |

## Carnet d'adresses vérifié (recherche web du 12/07/2026)

### Presse & radio

| Média | Contact | Note |
|---|---|---|
| France-Antilles Guadeloupe | `redaction@franceantilles.fr` — 0590 25 18 88 | Email direct rédaction |
| RCI Guadeloupe | `rcigua@yahoo.fr` / `accueil971@radiocaraibes.com` — ligne info 0590 89 44 44 | Radio n°1, Les Abymes |
| Guadeloupe la 1ère | Formulaire : la1ere.franceinfo.fr/guadeloupe/contact.html — standard 0590 60 96 96 | TV + radio publiques, Baie-Mahault |
| Le Courrier de Guadeloupe | lecourrierdeguadeloupe.com/nous-rejoindre | Presse écrite indépendante |

### Éducation & institutions

| Cible | Contact | Note |
|---|---|---|
| Université des Antilles — Licence/Master Études créoles | `lsh-creole-pedagogie@univ-antilles.fr` | Responsable pédagogique créole |
| CRILLASH (labo de recherche lettres/langues) | `mirella.pelage@univ-antilles.fr` — 0596 72 75 00 | Secrétariat, campus Schoelcher |
| Académie de Guadeloupe — LVR Créole | pedagogie.ac-guadeloupe.fr (page Langues Vivantes Régionales) | Prescripteurs : profs de créole (CAPES créole) |
| Région Guadeloupe | regionguadeloupe.fr | Leur site a une version créole → sensibilité avérée au sujet |

### Associations créolophones

| Association | Localisation | Note |
|---|---|---|
| Lang Kréyol Gwadloup an Bannzil | Allée de la Distillerie, Baie-Mahault | Promotion du créole : événements, concours, projets média |

### L'atout maître : le calendrier

La **Jounen Entènasyonal Kréyòl (28 octobre)** et le mois du créole (octobre)
sont LE moment où tous les médias antillais cherchent des sujets créole. Un
clavier kréyòl open source est un sujet clé en main pour eux. Pitcher la
presse **fin septembre / début octobre** multiplie les chances de reprise —
c'est le meilleur multiplicateur gratuit du plan.

## Calendrier phasé

| Phase | Période | Actions | Cible cumulée |
|---|---|---|---|
| 1. Cercle proche | Juillet-août 2026 | WhatsApp, groupes Facebook diaspora, bouton partage in-app (livré en 7.0.7) | 500–1 000 |
| 2. Prescripteurs | Septembre (rentrée) | Profs de créole (académie), Université des Antilles, associations, auteurs du corpus | 2 000–3 000 |
| 3. Pic médiatique | Octobre (mois du créole, Jounen kréyòl 28/10) | Pitch presse France-Antilles + RCI + la 1ère, posts réseaux coordonnés, Show HN / Product Hunt en parallèle | 6 000–10 000 |

## Messages prêts à copier-coller

### Post Facebook / groupe diaspora

> 🏝️ Ou ka galéré pou ékri an kréyòl asi téléfòn a'w ? Klavyé Kréyòl Karukera
> sé on klavyé Android GRATUI ki ba'w sigjesyon mo an kréyòl Gwadloup, tiré
> asi tèks Sylviane Telchid, Max Rippon, Sonny Rupaire é lòt gran défansè kréyòl la.
> Zéro kolekt données, kòd sous ouvè, gratui toubannman.
> 👉 [lien Play Store]

### Statut / message WhatsApp

> J'utilise Klavyé Kréyòl Karukera pour écrire en créole sur mon téléphone —
> clavier Android gratuit, suggestions basées sur de vrais textes créoles.
> Ça vaut le coup si tu écris en créole : [lien Play Store]

### Show HN (Hacker News)

> Show HN: I built a Creole keyboard for Guadeloupean French Creole
>
> A free, open-source Android IME with bilingual (Creole/French) word
> suggestions built from an n-gram model trained on a curated corpus of
> Guadeloupean Creole literature (Telchid, Rupaire, Fontes...). Zero data
> collection, fully offline. Built to help a minority language survive on
> phones where autocorrect actively fights it.
> [lien GitHub] [lien Play Store]

### Email de pitch presse (court)

> Objet : Un clavier Android pour écrire en kréyòl guadeloupéen
> *(variante octobre : « Pour la Jounen Kréyòl, un clavier pour écrire
> en kréyòl gwadloupéyen sur son téléphone »)*
>
> Bonjour,
>
> Klavyé Kréyòl Karukera est un clavier Android gratuit et open source qui
> propose des suggestions de mots en kréyòl guadeloupéen, construites à
> partir des textes de [Sylviane Telchid, Max Rippon, Sonny Rupaire...].
> Zéro collecte de données, fonctionnement 100 % local.
>
> Disponible sur Google Play : [lien]
> Code source : [lien GitHub]
>
> Je me tiens à disposition pour toute question ou démonstration.

### Légende Instagram / TikTok

> Ékri an kréyòl san ou pa ni pè fè fot 🇬🇵 Nouvo klavyé gratui ki ka édé'w
> ékri kréyòl Gwadloup — sigjesyon otantik, san pyèj, san kolekt données.
> Lien an bio 👆 #kreyol #gwada #guadeloupe #kréyòl #patwa #languecreole

## Kit presse — faits clés

- Premier clavier Android intelligent dédié au kréyòl guadeloupéen
- Dictionnaire de 1 800+ mots, modèle n-gram construit sur un corpus littéraire créole authentique
- Auteurs et voix du corpus : Sylviane Telchid, Sonny Rupaire, Robert Fontes, Max Rippon, Alain Rutil, Alain Vérin, Katel, Esnard Boisdur, Pierre Édouard Décimus
- Gratuit, open source, zéro collecte de données, fonctionnement 100 % local
- Disponible sur Google Play : `com.potomitan.kreyolkeyboard`

## Au-delà de 10 000 : les moteurs continus (ajout du 12/07/2026)

Le plan initial culmine sur un pic unique (octobre). Pour **dépasser**
10 000, il faut des moteurs qui tournent en continu après le pic.

### Rythme requis

Dépasser 10 000 d'ici fin 2026 = **~60 installations/jour en moyenne**
à partir d'octobre (avec les phases 1-2 réalisées). Jalon intermédiaire :
**50 avis à 4,5+ avant fin octobre** — c'est le carburant du classement
Play Store, plus important que n'importe quel post.

### Nouveaux canaux vérifiés (recherche du 12/07)

| Cible | Contact | Levier |
|---|---|---|
| **potomitan.info** (site de référence des cultures créoles) | 0596 61 19 56 — accepte les soumissions de ressources | Backlink + audience ultra-qualifiée ; le nom Potomitan™ crée une reconnaissance immédiate |
| **potomitan.io / VwaKréyòl** — ✅ **partenariat déjà actif** : le clavier figure dans potomitan.io/#applications | Équipe : Brigitte Démocrite, **Monique Famibelle**, Yisselda Rhoc | À optimiser : (1) faire pointer leur lien vers l'URL trackée `utm_source=potomitan_io` pour mesurer ce canal ; (2) relais par la communauté VwaKréyòl ; (3) emprunter leurs portes déjà ouvertes — **Lab'An Nou (Préfecture de Guadeloupe)** et **Orange Antilles-Guyane** (qui leur a consacré une capsule vidéo) : une intro par l'équipe POTOMITAN vaut mieux qu'un contact à froid |
| **KEPKAA — Mois du Créole à Montréal** (octobre) | `kepkaa@videotron.ca` — 514 750-8800 | 2ᵉ pic d'octobre : diaspora créolophone canadienne |
| **Tropiques FM** (radio antillaise, Paris 92.6 FM) | tropiquesfm.com — 4 bd des Îles, Issy-les-Moulineaux | Diaspora antillaise d'Île-de-France (le plus gros bassin hors archipel) |

### Distribution alternative (gratuite)

- **IzzyOnDroid** : dépôt compatible F-Droid alimenté automatiquement par
  les releases GitHub (déjà en place !). Soumission = une simple issue sur
  leur dépôt. Audience open source androïd militante → bouche-à-oreille tech.
- **F-Droid officiel** (plus tard) : plus exigeant (build reproductible)
  mais crédibilité maximale pour un projet de préservation linguistique.

### Fiche Play Store : exploiter la console

- **Fiches localisées** : ajouter une fiche **anglais** (« Guadeloupean
  Creole keyboard ») et **créole haïtien (ht)** — la langue la plus proche
  disponible dans la console — pour capter les recherches « creole keyboard »
  de toute la Caraïbe et des US.
- **Expériences de fiche** (A/B testing natif de la Play Console) : tester
  icône et première capture dès ~1 000 visites/mois.

### Backlog produit growth (à développer ensuite)

- **Partage de badge de progression** : chaque passage de niveau (Pipirit →
  Benzo) génère une carte partageable — boucle virale liée à la gamification
  existante.
- **« Mo a jou »** (mot du jour) : notification quotidienne avec un mot
  créole + sa source littéraire, partageable. Rétention + contenu social
  automatique.
- Livré le 12/07 : bouton **« ⭐ Noter l'application »** dans l'onglet
  À Propos (lien direct `market://`), complément de l'In-App Review soumis
  à quota.

## Sur les 5 €

À ce niveau, la publicité en ligne n'a pas de retour mesurable. Deux usages
plus utiles si tu veux quand même les dépenser :
- Imprimer une dizaine de flyers/QR code à déposer dans des lieux
  culturels (librairies, associations créoles, radios locales)
- Les garder de côté : ils ne changeront rien tant que le vrai goulot
  d'étranglement est la portée organique, pas l'argent

## Suivi

- Google Play Console → statistiques d'installations, par source si tu
  ajoutes des liens trackés (UTM) dans chaque post
- GitHub (étoiles, forks) comme signal secondaire d'intérêt côté tech

## Checklist — ce qui reste à faire humainement

- [ ] Poster dans 3-5 groupes Facebook diaspora Guadeloupe/Martinique
- [ ] Partager sur WhatsApp (statut + groupes)
- [ ] Contacter France-Antilles, Guadeloupe la 1ère, RCI Guadeloupe
- [ ] Écrire aux auteurs/artistes cités dans l'app
- [ ] Contacter le département Créole de l'Université des Antilles
- [ ] Poster un Show HN + r/opensource
- [ ] Publier sur Product Hunt
- [ ] Poster 2-3 courtes vidéos Instagram/TikTok montrant le clavier en action
