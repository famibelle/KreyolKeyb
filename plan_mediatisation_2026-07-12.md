# Plan de médiatisation — Klavyé Kréyòl Karukera

**Objectif :** 10 000 téléchargements. **Budget :** 5 €.

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

## Messages prêts à copier-coller

### Post Facebook / groupe diaspora

> 🏝️ Ou ka galéré pou ékri an kréyòl asi telefòn ou ? Klavyé Kréyòl Karukera
> sé an klavyé Android GRATIS ki ba w sigjesyon mo an kréyòl Gwadloup, tiré
> asi tèks Sylviane Telchid, Max Rippon, Sonny Rupaire é lòt gran défansè kréyòl la.
> Zéro kolekt données, kòd sous ouvè, gratis toubannman.
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

> Ékri an kréyòl san ou pa ni pè fè fot 🇬🇵 Nouvo klavyé gratis ki ka édé w
> ékri kréyòl Gwadloup — sigjesyon otantik, san pyèj, san kolekt données.
> Lien an bio 👆 #kreyol #gwada #guadeloupe #kréyòl #patwa #languecreole

## Kit presse — faits clés

- Premier clavier Android intelligent dédié au kréyòl guadeloupéen
- Dictionnaire de 1 800+ mots, modèle n-gram construit sur un corpus littéraire créole authentique
- Auteurs et voix du corpus : Sylviane Telchid, Sonny Rupaire, Robert Fontes, Max Rippon, Alain Rutil, Alain Vérin, Katel, Esnard Boisdur, Pierre Édouard Décimus
- Gratuit, open source, zéro collecte de données, fonctionnement 100 % local
- Disponible sur Google Play : `com.potomitan.kreyolkeyboard`

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
