# Vitesse de Klavyé Kréyòl face à Gboard, et idées de Gboard absentes de l'application

*Réalisé le 19 septembre 2026 sur un téléphone réel. Observations, pas un classement.*

## Conditions

- **Appareil** : Samsung Galaxy A21s (SM-A217F), Android 12, écran 720 x 1600, entrée de gamme.
- **Klavyé Kréyòl Karukera 22.0.2**, version de production installée depuis le Play Store (non débogable).
- **Gboard 18.2.4.969776716** (release, arm64-v8a), installé le jour même depuis le Play Store, disposition « Créole guadeloupéen » AZERTY.
- **Saisie** : touches tapées par `adb shell input tap`, champ de message de Samsung Messages, jamais d'envoi. Même texte des deux côtés : `kreyolnoukapaleanmwen`, 21 lettres, sans espace ni accent, pour ne déclencher aucune correction automatique.
- **Différences avec la mesure d'août** (émulateur, 12.4.05 contre 10.11.4) : appareil réel ; réseau **non coupé** (le téléphone est piloté en Wi-Fi) ; pas de démarrage à froid (voir plus bas).

## Résultats

| Mesure | Klavyé Kréyòl 22.0.2 | Gboard 18.2.4 |
|---|---|---|
| Affichage quand le clavier est déjà en mémoire, médiane de 10 essais | 150 ms | 205 ms |
| Calcul par frappe, saisie rapide (21 touches enchaînées), 3 essais | 37,1 à 41,0 ms (médiane 39,0) | 77,6 à 86,2 ms (médiane 85,2) |
| Calcul par frappe, saisie posée (une touche toutes les 0,6 s), 2 essais | 51,4 et 53,8 ms | 94,3 et 95,2 ms |
| Mémoire occupée pendant la saisie (PSS) | 55 Mo | 134 Mo |
| Images dessinées pour 21 frappes rapides | 16, 20, 20 | 66, 67, 66 |
| Images « janky » sur ces 21 frappes | 5, 6, 4 | 4, 3, 2 |
| Temps de dessin d'une image, médiane et 90e centile | 12 ms et 19 à 23 ms | 12 à 13 ms et 16 ms |
| Caractères reçus sur 21 frappes rapides | 21 sur 21 | 21 sur 21 |

## Ce que ces chiffres disent

- Klavyé Kréyòl calcule environ deux fois moins par frappe et occupe environ deux fois et demie moins de mémoire. L'écart va dans le même sens que celui d'août (38 ms contre 100 ms, 52 Mo contre 153 Mo).
- Gboard fait davantage à chaque frappe (aperçu de touche, tracé, correction automatique, prédiction), ce qui explique une bonne part de l'écart. Un clavier plus simple coûte moins cher à faire tourner.
- **Fluidité : un écart mince, en faveur de Gboard.** Il ne s'agit pas de vitesse mais de régularité du dessin : une image « janky » est une image qui dépasse le budget de 16,7 ms d'un écran à 60 Hz. Sur 21 frappes, 4 à 6 images dépassent ce budget chez Klavyé Kréyòl et 2 à 4 chez Gboard, soit environ une image lente de plus toutes les dix frappes (0,19 à 0,29 par frappe contre 0,10 à 0,19). Les pourcentages, eux, sont trompeurs (20 à 31 % contre 3 à 6 %) : Gboard dessine quatre fois plus d'images (66 contre 16 à 20), surtout des animations très légères, ce qui dilue la part des lentes. Le 90e centile (19 à 23 ms contre 16 ms) va dans le même sens. Trois essais seulement : l'écart est à confirmer, pas à conclure.
- L'écart d'affichage (150 contre 205 ms) est réel mais modeste. La sonde comprend le lancement de la commande de toucher, identique des deux côtés (voir plus bas).

## Limites

- **Pas de démarrage à froid.** La mesure d'août s'appuyait sur `kill -9`, impossible sans droits root. `am kill` ne tue pas un clavier lié, et `am force-stop` remet le clavier Samsung par défaut : relancer ensuite par `ime set` démarre le service avant la mesure, ce que les premiers relevés (identiques à chaud et à froid) ont bien montré. Aucun chiffre à froid n'est donc publié.
- **Sonde d'affichage.** `mInputShown` passe à vrai dès la demande, avant le dessin : inutilisable. La sonde retenue est `mWindowVisible=true` dans `dumpsys input_method`, côté service, mesurée par `/proc/uptime` dans une seule commande sur l'appareil. Elle inclut le lancement de `input tap` (environ 90 ms) et la granularité d'un `dumpsys`.
- **Réseau actif.** Ni la synchronisation de Gboard ni ses services en ligne n'ont été coupés.
- Un seul appareil, quelques essais (3 en saisie rapide, 2 en saisie posée).
- Deux incidents de méthode, sans effet sur les résultats : la première série était inutilisable (le clavier n'était plus affiché, car Retour avait quitté la conversation) et a été refaite avec des gardes ; des touches du banc ont ouvert l'appareil photo, sans créer aucun média (vérifié dans le MediaStore et sur les cartes).

## Idées de Gboard qui manquent à l'application

Relevées dans les réglages de Gboard 18.2.4 sur le téléphone. Ne figurent ici que celles compatibles avec le principe du projet : un clavier simple, hors ligne, sans collecte.

**À étudier en priorité**

1. **Correction automatique limitée aux accents, avec bouton d'annulation.** Gboard rétablit `kreyol` en `kréyòl`, `pale` en `palé`, mais remplace aussi des mots (8 sur 59 dans notre essai). Une version qui ne restaure que l'accent, uniquement quand le mot replié a une forme accentuée dominante dans le dictionnaire, ne remplacerait jamais un mot par un autre. Le bouton d'annulation (flèche dans la bande) rend l'erreur peu coûteuse.
2. **Petits conforts d'écriture, peu coûteux** : double espace qui pose un point, espace retirée avant la ponctuation, adaptation au type de champ (clavier numérique pour un numéro, arobase et point pour une adresse). Ce sont les lignes « non » de notre inventaire.
3. **Aperçu de la touche au-dessus du doigt, durée de l'appui long réglable, force de la vibration réglable.** Gboard propose l'aperçu (activé), une durée d'appui long de 300 ms modifiable, et une force de vibration. Utile pour la précision et pour l'accessibilité motrice que le projet a déjà instruite.
4. **Effacement en glissant vers la gauche sur la touche de suppression** (Gboard : « Suppression par glissement de doigt »). Geste peu coûteux, complète l'effacement par mots que nous avons.

**À étudier avec précaution**

5. **Mots appris**, séparément par langue (Gboard a un « Dictionnaire personnel » par langue, dont le créole guadeloupéen). Notre application a supprimé cette fonction en 10.6.0 pour la frontière de confidentialité. Une version qui garde les prénoms et lieux en local, sur demande, hors sauvegarde, exclue des champs sensibles, consultable et effaçable, resterait dans l'esprit, comme les emoji récents.
6. **Filtre de termes choquants** (option Gboard « Ne pas suggérer de termes choquants »). Pertinent pour la classe de langue vivante régionale ; demande de constituer la liste, sans inventer de kréyòl.
7. **Ligne de chiffres permanente** en option, et **mode une main** : des conforts de saisie connus, au coût d'espace ou de disposition.
8. **Saisie glissée.** Elle fonctionne bien avec la disposition créole de Gboard, mais c'est un chantier lourd, déjà identifié.

**À ne pas suivre**

- Dictée vocale, traduction, GIF, autocollants, suggestions de contacts, synchronisation : elles reposent sur le réseau ou sur des données personnelles, à l'opposé de « aucune permission Internet ».
- Le presse-papiers avec historique : sensible pour la vie privée, faible utilité pour l'écriture en kréyòl.

**Une idée inspirée de la traduction de Gboard, sans réseau** : montrer le sens en français d'un mot en touchant longuement une suggestion, grâce aux 1 145 glosses embarquées. À peser contre le positionnement « clavier simple, pas un outil d'apprentissage ».
