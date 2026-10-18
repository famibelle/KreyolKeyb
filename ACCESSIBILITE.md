# Accessibilité : ce qu'il reste à faire

État au 18 août 2026. **Les points 1 et 2 sont faits, les cinq autres ne le sont
pas**, et l'écran de réglages dont quatre d'entre eux dépendaient existe désormais. Ce
fichier est la contrepartie technique de [`docs/ergotherapie.html`](docs/ergotherapie.html),
la fiche publique destinée aux ergothérapeutes : cette page annonce des limites,
celle-ci dit ce qu'il faudrait changer pour les lever, où, et comment le vérifier.

Il vit à la racine et non dans `android_keyboard/`, bien qu'il ne parle que du
clavier Android : le filtre de chemins de `build-apk.yml` couvre
`android_keyboard/**`, donc l'y déposer déclencherait un build APK et AAB complet,
pipeline Python du dictionnaire compris, à chaque correction de typo dans un
document qui ne part pas dans l'application.

## Le profil visé

Une personne qui sait le mot mais ne peut pas exécuter les appuis : hémiplégie
après un AVC, spasticité, tremblement, faiblesse, douleur articulaire. Pour elle,
le clavier alphabétique n'est pas le moyen de saisie, c'est un filtre. La saisie
se fait dans la barre de propositions, et le clavier est déjà, sans l'avoir
cherché, un outil d'économie de gestes :

- les propositions créoles apparaissent dès la première lettre (`SuggestionEngine.MIN_WORD_LENGTH = 1`) ;
- un appui sur une proposition écrit le mot, l'espace et la majuscule (`InputProcessor.processSuggestionSelection()`) ;
- les n-grammes proposent le mot suivant sans qu'aucune lettre soit tapée ;
- le comptage d'usage personnel remonte les mots de la personne (`calculateDictionaryScore()`, `USAGE_WEIGHT = 5.0`, plafonné à `MAX_COUNTED_USAGES = 20`).

Tout ce qui suit a donc un seul objectif : rendre la barre de propositions fiable
à viser, et supprimer les blocages sans échappatoire.

## Invariants à ne pas casser

Ces comportements servent ce profil aujourd'hui. Deux d'entre eux existent par
accident (une fonctionnalité absente qui protège), ce qui les rend faciles à
détruire en croyant améliorer le clavier.

| Invariant | Où | Pourquoi il compte |
|---|---|---|
| La suppression n'efface qu'un glyphe, sans répétition automatique | `InputProcessor.handleBackspace()` | Un doigt qui ne se relève pas n'efface pas la phrase. Ajouter une répétition sur appui long serait une régression pour ce profil : si elle est ajoutée un jour, elle doit être désactivable. |
| Les rangées ne se décalent pas pendant la frappe | `frenchRowScroll` passe en `INVISIBLE`, jamais en `GONE` (correctif du 23/07/2026) | Une cible visée reste au même endroit le temps que le geste aboutisse. |
| La lettre sans accent figure en premier dans la popup d'accents | `AccentHandler.createAccentButton(..., isBase = true)` | Un appui long involontaire (relâchement lent) reste récupérable en un appui, sans caractère faux. |
| Les puces de suggestion réagissent au clic, pas à `ACTION_DOWN` | `addSuggestionChip()`, `setOnClickListener` | Poser le doigt sur la mauvaise puce puis glisser en dehors annule, **à condition de rester dans la fenêtre du clavier** : vérifié le 2026-08-17, l'annulation marche vers une puce voisine, vers l'intervalle entre deux puces et vers les touches, mais pas vers le haut hors du clavier, où le doigt quitte la fenêtre qui ne reçoit alors plus d'événements de déplacement. Passer la sélection sur `ACTION_DOWN` supprimerait cette sortie de secours, et ferait vibrer un geste annulé. |
| Aucune correction automatique | `InputProcessor.handleSpace()` finalise sans réécrire | Le texte affiché est le texte saisi, ce qui compte quand la relecture est difficile. |
| Aucun signal intrusif pendant la frappe | pas de pastille ni d'animation dans la barre de suggestions | Décision déjà prise pour d'autres raisons, mais c'est aussi un acquis d'accessibilité. |

## L'écran de réglages du clavier : il existe depuis la 10.11.7

`SettingsActivity` reste l'écran d'accueil de l'application, et un engrenage dans
son bandeau ouvre désormais un écran « Réglages du clavier » à part entière, dont
l'IME lit les préférences. La plomberie que les points 3 à 6 attendaient est donc en
place :

- [`KeyboardPreferences`](android_keyboard/app/src/main/java/com/example/kreyolkeyboard/KeyboardPreferences.kt),
  point d'entrée unique, pour que les deux côtés ne manipulent pas des clés en dur ;
- [`KeyboardSettingsActivity`](android_keyboard/app/src/main/java/com/example/kreyolkeyboard/KeyboardSettingsActivity.kt),
  écran dédié plutôt qu'un huitième onglet : les réglages vivent derrière un
  engrenage sur Android, et la barre en porte déjà sept. Les réglages ont d'abord
  vécu dans une carte de l'onglet À Propos, une page de présentation où personne ne
  cherche un interrupteur ;
- une relecture des réglages dans `onStartInputView`, pour qu'un changement
  s'applique dès le retour dans un champ de saisie et non au prochain redémarrage.

Le service et l'activité partageant le processus, aucune diffusion n'est
nécessaire : l'instance de `SharedPreferences` est la même des deux côtés.

**Ajouter un réglage coûte donc trois petites choses** : une entrée dans
`KeyboardPreferences`, un interrupteur dans la carte, et la lecture de la valeur là
où elle sert. Point d'attention pour les points 3 et 5 : ils changent la
**géométrie**, donc ils demandent en plus une reconstruction de la mise en page, là
où le retour de frappe se contente d'une valeur relue.

### La leçon, apprise deux fois

Cette plomberie a été écrite le 2026-08-17 avec le point 2, retirée le jour même,
puis rétablie le lendemain. Le détour vaut d'être retenu.

Elle a d'abord été retirée au motif que le téléphone offrait déjà l'interrupteur, et
qu'un clavier n'a pas à dupliquer un réglage du système. Le raisonnement était juste,
la prémisse fausse : **sur One UI, « Vibration au toucher » ne gouverne que le
clavier Samsung**, et aucun réglage accessible ne couvre les claviers tiers.
Constaté sur un appareil réel en 10.11.6, où le clavier était devenu complètement
muet, sans recours.

Deux règles en sortent, dans cet ordre :

1. **Vérifier que le réglage système existe vraiment pour un clavier tiers**, et pas
   seulement dans les écrans de réglages du constructeur. Ce qui est vrai sur AOSP ne
   l'est pas forcément sur One UI, MIUI ou Magic UI.
2. Ne pas dupliquer un réglage du système reste la bonne règle quand ce réglage
   existe et s'applique. Gboard et SwiftKey ont leurs propres interrupteurs de retour
   de frappe précisément parce que ce n'est pas le cas ici.

Corollaire pour la suite : un émulateur AOSP ne pouvait pas révéler ce problème. Les
comportements dépendant des réglages du constructeur demandent un appareil réel.

## 1. Écart et zone neutre entre les puces de suggestion : FAIT le 2026-08-17

**Fait en 10.11.2.** Publié sur GitHub à ce tag ; l'arrivée sur le Play Store
demande une validation, donc une installation faite juste après cette date peut
encore porter la 10.11.1, qui ne l'a pas.

**Ce que la mesure a montré, contre l'hypothèse de départ.** La mesure se refait
avec [`scripts/geo_puces.py`](scripts/geo_puces.py), qui relève les puces au pixel
dans une capture : la fenêtre de l'IME n'apparaît pas dans `uiautomator dump`, et
les valeurs du code ne suffisent pas à prédire ce qui s'affiche. Cette section
affirmait d'abord qu'une puce de deux lettres faisait « environ 42 dp de large ».
C'était faux, et la piste de correction qui en découlait (`minWidth` de 56 à
64 dp) aurait **rétréci** les puces au lieu de les agrandir. Mesuré sur l'AVD
`kreyol_test` (Pixel 5, 1080 × 2340, 440 dpi) en analysant les boîtes englobantes
des puces dans une capture : une puce fait **88 dp de large**, quel que soit le
mot, parce que le style `Button` de la plateforme impose déjà ce plancher. La
largeur n'était donc jamais le problème.

Le vrai défaut était vertical, et il n'avait pas été mesuré du tout : **3,6 dp
(0,58 mm) entre la rangée kréyòl et la rangée française**, contre 6,9 dp
(1,10 mm) entre deux puces d'une même rangée. Or l'imprécision d'un doigt est
d'abord verticale. Un demi-millimètre trop bas validait un mot français à la
place du mot créole visé.

| | avant | après |
|---|---|---|
| Puce (portrait) | 88 × 38,2 dp (14,0 × 6,1 mm) | 88 × 34,2 dp (14,0 × 5,4 mm) |
| Écart horizontal | 6,9 dp (1,10 mm) | 11,6 dp (1,85 mm) |
| Écart vertical entre rangées | **3,6 dp (0,58 mm)** | **11,6 dp (1,85 mm)** |
| Puce (paysage) | 88 × 32 dp | 88 × 32 dp, inchangée |
| Hauteur du clavier | référence | identique au pixel |

**Ce qui a été changé.** Une constante unique,
`SUGGESTION_CHIP_GAP_DP = 12`, dans `KreyolInputMethodServiceRefactored` :

- la moitié de chaque côté en marge de puce (`addSuggestionChip()`, et la puce de
  partage `showShareInviteChip()` pour n'avoir qu'une règle) ;
- la moitié en padding bas de `kreyolScroll` et en padding haut de `frScroll`, ce
  qui crée l'intervalle vertical. Ce padding n'est appliqué que si
  `suggestionRowCount() > 1` : en paysage il n'y a qu'une rangée, donc rien à
  séparer et pas de hauteur à dépenser en vide ;
- `minWidth` fixé à 88 dp, non pour élargir mais pour figer ce que le thème donne
  aujourd'hui, afin qu'un changement de thème ne rétrécisse pas les cibles sans
  qu'on s'en aperçoive.

L'intervalle n'appartient à aucune vue cliquable : le `LinearLayout` parent n'a pas
de `OnClickListener`, donc un appui qui y tombe ne fait rien. C'est le
comportement voulu, un appui perdu coûtant un geste là où un appui sur le mot
voisin en coûte cinq.

**Le coût, assumé.** La hauteur de puce passe de 38,2 à 34,2 dp, soit 0,6 mm
perdus, parce que l'intervalle est pris sur la hauteur des puces et non sur celle
des rangées : `SUGGESTION_ROW_HEIGHT_DP` ne change pas, le clavier occupe la même
place et aucune touche ne bouge (vérifié en comparant les captures avant et après,
la zone des touches est identique au pixel). Échanger 0,6 mm de hauteur contre
1,3 mm de séparation est favorable pour ce profil.

**Ce qui reste à faire sur ce point.**

- La mesure qui compte, le **taux de validations erronées** en visant
  délibérément à côté, n'a pas été faite : elle demande un geste imprécis réel,
  pas des `adb input tap` parfaitement centrés.
- Les mots longs restent à surveiller : trois puces d'un mot comme
  « Bonmaten-la » dépassent la largeur de l'écran et la rangée défile alors
  horizontalement. Une proposition qu'il faut faire défiler pour l'atteindre est
  pire qu'absente pour ce profil. L'intervalle plus large rapproche légèrement ce
  seuil, il ne le crée pas.

## 2. Vibration désactivable : FAIT en 10.11.7, après un détour

**Constat.** `performHapticFeedback()` passait
`HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING` : le clavier vibrait même
quand l'utilisateur avait coupé le retour tactile du téléphone, et l'application
n'offrait aucun interrupteur. C'était le seul point de cette liste qui soit un
blocage total sans échappatoire, pour une hypersensibilité sensorielle ou quand la
vibration perturbe le geste.

**Première tentative, 10.11.5 : retirer le drapeau, sans rien ajouter.** Le clavier
suivait alors le réglage du téléphone, comme n'importe quelle application. Une ligne
de code, aucun réglage à maintenir. C'était le bon raisonnement sur une prémisse
fausse, et ça a cassé le clavier.

**Ce que l'appareil réel a montré.** Sur Samsung en 10.11.6, plus aucune vibration
ni aucun son. Sur One UI, « Vibration au toucher » ne gouverne que le clavier
Samsung : rien n'est proposé pour les claviers tiers. Le clavier était donc muet
sans recours possible, et l'utilisateur n'avait aucun moyen de comprendre pourquoi.

Mesuré sur émulateur au moment du diagnostic, avec `dumpsys vibrator_manager` qui
horodate chaque demande et son sort :

| Réglage système | Sort de la demande |
|---|---|
| retour tactile activé | `status: finished`, la vibration part |
| retour tactile désactivé | `status: ignored_for_settings`, la demande est jetée |

**Ce qui a été fait, 10.11.7.** Le clavier reprend la main, et l'échappatoire passe
par l'application :

- `FLAG_IGNORE_GLOBAL_SETTING` est reposé pour la vibration ;
- le son utilise `playSoundEffect(effectType, volume)`, la variante à volume
  explicite, qui contrairement à `playSoundEffect(effectType)` ne consulte pas le
  réglage « sons au toucher » du téléphone. C'est ce que fait AOSP LatinIME ;
- deux interrupteurs dans l'application, actifs par défaut, coupent l'un ou l'autre
  ([`KeyboardPreferences`](android_keyboard/app/src/main/java/com/example/kreyolkeyboard/KeyboardPreferences.kt)) ;
- toute la politique vit dans [`KeyFeedback`](android_keyboard/app/src/main/java/com/example/kreyolkeyboard/KeyFeedback.kt),
  seul endroit où ces contournements sont écrits.

C'est le comportement de Gboard et de SwiftKey, qui ont leurs propres interrupteurs
pour la même raison.

**Vérification, sur émulateur.** Réglages système coupés, la vibration part quand
même (`status: finished`), ce qui reproduit et corrige le cas Samsung. Interrupteur
de l'application coupé, plus aucune demande n'est émise. Rallumé, elle repart.

**Ce qui reste à vérifier sur appareil réel** : que la vibration se sente et que le
son s'entende. L'émulateur ne vibre pas et tourne sans audio ; c'est justement ce qui
a laissé passer la régression de la 10.11.5.

## 3. Hauteur de touche réglable, et plancher en paysage

**Constat.** `BUTTON_HEIGHT_DP = 48` en portrait (environ 7,6 mm) et
`BUTTON_HEIGHT_LANDSCAPE_DP = BUTTON_MIN_HEIGHT_DP = 32` en paysage (environ
5 mm), sans aucun réglage. 32 dp est nettement sous la cible minimale
recommandée par Android, et inutilisable pour un doigt unique instable ou un
stylet tenu maladroitement.

**Où.** `KeyboardLayoutManager.keyHeightPx()` est le point de passage unique,
donc un seul endroit à modifier.

**Quoi.** Un réglage à trois ou quatre crans (48, 56, 64 dp) appliqué à la
hauteur nominale, et ne pas descendre le plancher paysage sous 44 dp. Attention :
la hauteur totale du clavier est déjà contrainte
(`setAvailableRowsHeight()` puis `coerceIn`), donc agrandir les touches réduit
ce qui reste pour l'application au dessus. C'est un compromis à assumer
explicitement, pas un bug.

**Vérification.** Sur émulateur, les 18 appareils de la campagne multi-appareils
du 16/08/2026, en portrait et en paysage.

## 4. Délai d'appui long réglable : FAIT en 23.0.0

**Constat de départ.** `AccentHandler.LONG_PRESS_DELAY = 500` était fixe, mais
ce n'était pas le seul délai : le minuteur ne partait qu'au long-clic natif
d'Android (400 ms par défaut, jusqu'à 1,5 s selon le réglage « délai de
l'appui prolongé » du téléphone). La popup d'accents demandait donc près d'une
seconde de pression. Le besoin s'est révélé dans les deux sens, sur le clavier
luxembourgeois issu de celui-ci (LuxKeyb 29.5.0, d'où vient ce correctif) : des
utilisateurs l'ont trouvée trop lente, alors que ce document la jugeait trop
rapide pour qui relâche lentement.

**Ce qui a été fait.** Un seul délai, compté depuis l'instant où le doigt se
pose (`KeyboardLayoutManager.addAccentKeyTouch`), et un réglage à quatre crans
dans « Réglages du clavier » : 300 ms (défaut), 500, 800 et 1200 ms
(`KeyboardPreferences.DelaiAppuiLong`). Les deux crans longs reprennent ceux
que proposait ce point. L'action « appui long » de TalkBack est conservée.

**Pas fait.** `KeyboardLayoutManager.SPACE_LONG_PRESS_DELAY = 1000` (sélecteur
de claviers sur la barre d'espace) reste fixe : il ne pose pas de problème de
popup involontaire, la barre d'espace étant déjà la touche la plus large.

**Vérification.** Au cran « Très long », maintenir une touche à accents environ
une seconde et constater qu'aucune popup ne s'ouvre. Au cran « Court », la
popup s'ouvre en moins d'une demi-seconde, et une frappe rapide n'en ouvre
aucune.

## 5. Nombre de propositions affichées

**Constat.** `MAX_SUGGESTIONS = 5` en interne (3 kréyòl et 2 français) et trois
propositions affichées. Ce réglage suppose une frappe rapide et peu coûteuse :
au delà de trois, on suppose qu'il est plus rapide de continuer à taper que de
lire. Pour ce profil l'arbitrage s'inverse, puisque chaque appui est cher.

**Où.** `SuggestionEngine.MAX_SUGGESTIONS` et la construction des rangées dans
`KreyolInputMethodServiceRefactored`.

**Quoi.** Rendre le nombre affiché configurable (3, 4, 5). Point d'attention : la
rangée est déjà en défilement horizontal, donc une quatrième proposition risque
de sortir de l'écran, ce qui la rend pire qu'inexistante puisqu'elle demande un
geste de défilement. Élargir la liste n'a de sens que si tout reste visible sans
défiler, ce qui interagit directement avec le point 1.

**Vérification.** Compter les appuis nécessaires pour écrire la même phrase à 3
puis à 5 propositions.

## 6. Filtre anti-rebond sur les appuis répétés

**Constat.** Rien dans le code ne filtre deux appuis rapprochés sur la même
touche. L'appui rebondi, erreur classique du tremblement et de la spasticité,
écrit donc bien la lettre en double. Le mot reste retrouvé grâce à Levenshtein,
mais **le texte saisi reste faux** jusqu'à ce qu'une proposition soit touchée : la
personne est obligée de passer par la barre pour que sa phrase soit correcte. Les
aides techniques appellent cette fonction « ignorer les touches répétées ».

**Où.** `KeyboardLayoutManager.addTouchAnimation()` pour l'horodatage de
l'`ACTION_DOWN`, `InputProcessor.processKey()` pour le rejet.

**Quoi.** Ignorer un second appui sur la même touche en dessous d'un seuil
réglable (0, 150, 250, 400 ms), désactivé par défaut. À ne surtout pas activer
par défaut : cela casserait la saisie légitime des lettres doublées.

**Vérification.** Taper volontairement en double et constater qu'une seule lettre
est écrite au seuil actif, deux au seuil nul.

## 7. Apprendre les paires de mots, pas seulement les mots

**Constat.** C'est le point le plus lourd, et celui qui rapporterait le plus. Le
comptage d'usage n'influence que le score **lexical** (`calculateDictionaryScore()`
reçoit `usageCountOf(word)`). Les n-grammes, eux, ne s'adaptent jamais : ils
viennent de `creole_ngrams.json`, construit sur un corpus de **textes
littéraires**. Or la fonction la plus rentable pour ce profil est la proposition
du mot suivant sans taper aucune lettre, et c'est justement celle qui est calée
sur une langue écrite littéraire plutôt que sur des conversations. L'adaptation
personnelle est lexicale là où le besoin est contextuel.

**Où.** `SuggestionEngine.generateContextualSuggestions()`, et
`gamification/CreoleDictionaryWithUsage` pour le stockage.

**Quoi.** Enregistrer les paires réellement validées par la personne dans un
fichier de `filesDir`, sur le modèle de `CreoleDictionaryWithUsage`, et les
mélanger aux n-grammes du corpus avec un poids qui monte avec le nombre
d'observations. Deux précautions : l'accès doit rester `synchronized` (le moteur
lit depuis un thread de fond, l'IME écrit sur le thread principal), et le fichier
doit être borné pour ne pas grossir sans fin.

**Vérification.** Écrire vingt fois la même phrase courte et vérifier qu'elle
finit par se composer entièrement par les propositions.

## Vérifier globalement

Aucun de ces points ne se juge sur une capture d'écran. Trois mesures :

1. **Compter les gestes.** Une phrase que la personne écrit vraiment, saisie
   lettre par lettre puis par les propositions. L'écart est la seule mesure qui
   vaille, et elle est propre à chaque personne. Ne pas publier de moyenne.
2. **Compter les validations erronées** en visant délibérément à côté, avant et
   après le point 1.
3. **Faire tester par un ergothérapeute.** Le classement des sept points ci
   dessus est une hypothèse de développeur, pas un constat de terrain. Un seul
   retour professionnel peut le réordonner entièrement, et c'est le but de la
   fiche publique.

## Hors périmètre de ce document

Ce fichier ne traite que le handicap moteur de la main. D'autres chantiers
d'accessibilité existent et ne sont pas couverts ici :

- **basse vision et cécité** : les touches d'icônes portent bien un
  `contentDescription` (`Supprimer`, `Entrée`, `Majuscule`) et les rangées de
  propositions un libellé textuel KR/FR, donc la langue n'est pas signalée par la
  couleur seule ; le retour sonore de frappe existe depuis la 10.11.6, avec le son
  propre à chaque nature de touche (`KeyFeedback`), là où le clavier se contentait
  auparavant du clic d'interface que `performClick()` jouait de lui-même, et laissait
  la barre d'espace muette ; mais aucun test réel sous TalkBack n'a été fait, et les
  hauteurs fixes en dp face à des tailles de texte en sp n'ont pas été vérifiées à
  200 % de police système ;
- **troubles cognitifs et attentionnels** : l'absence de signal intrusif et la
  stabilité du layout jouent déjà en leur faveur, rien n'a été étudié au delà ;
- **aphasie et troubles du langage** : c'est un autre public, servi par les mêmes
  fonctions mais avec des priorités différentes, qui relèvent de l'orthophonie et
  non de l'ergothérapie.
