# Réactivité des touches, mesures et pistes

Compte rendu de la campagne de mesure du 2026-09-05, déclenchée par un ressenti
de « léger délai » à l'appui sur la touche `123`. Les mesures ont été faites sur
un appareil réel, pas sur émulateur.

## Résumé

Sur un Samsung Galaxy A21s (le mobile bas de gamme que le code cible déjà via
`isLowEndDevice`), la touche `123` / `ABC` est **le seul geste courant qui bloque
le fil principal pendant 3 à 4 rafraîchissements d'écran** (environ 45 à 55 ms de
travail, 55 à 90 ms avant que le nouveau clavier soit affiché). Toutes les autres
touches usuelles (lettre, ponctuation, `⇧`, `⏎`, `⌫`, espace) tiennent dans une
frame, soit environ 12 ms.

La cause est architecturale : `onModeChanged` **détruit et reconstruit tout
l'arbre de vues du clavier** à chaque changement de mode. Le coût est amplifié
par le rendu logiciel forcé sur chaque touche (`LAYER_TYPE_SOFTWARE`, introduit
pour un bug d'affichage Honor 200).

Un défaut secondaire a été trouvé au passage : la liste `keyboardButtons` de
`KeyboardLayoutManager` fuit, elle grossit d'environ 13 à 34 entrées à chaque
bascule de mode et n'est jamais vidée pendant la vie du process.

## Contexte et méthode

| Paramètre | Valeur |
|---|---|
| Appareil | Samsung Galaxy A21s (SM-A217F), connecté en Wi-Fi ADB |
| OS | Android 12 |
| Écran | 720 x 1600, 60 Hz, soit un budget de 16,7 ms par frame |
| Build installé | `com.potomitan.kreyolkeyboard` 12.0.1 |
| Champ de saisie | zone de composition de Samsung Messages |
| Outil | `dumpsys gfxinfo <paquet> framestats` |

`framestats` donne, pour chaque frame rendue par le process, l'horodatage
nanoseconde de chaque étape du pipeline HWUI : réception de l'événement, mesure
et placement (`PerformTraversals`), enregistrement des listes d'affichage
(`Draw`), synchronisation et upload GPU (`Sync`), échange de tampons
(`SwapBuffers`), fin de frame (`FrameCompleted`).

Deux grandeurs sont rapportées :

- **frame** = temps où le fil principal est monopolisé pour produire l'image
  (`FrameCompleted` moins le début de la frame). C'est ce qui fige le clavier.
- **latence** = délai entre la frame prévue et son affichage effectif
  (`FrameCompleted` moins `IntendedVsync`). C'est ce que l'oeil perçoit.

Au-delà de 16,7 ms de frame, une image est sautée. Une frame de 50 ms se traduit
par trois images identiques à l'écran avant que le changement apparaisse.

Chaque touche a été mesurée 15 à 40 fois, dans une boucle serrée sur une seule
touche, avec `dumpsys gfxinfo reset` avant chaque appui. Le protocole exact est
en annexe.

## Résultats

| Touche | frame (fil principal) | frames sautées | latence perçue | ressenti |
|---|---|---|---|---|
| lettre (a à z) | ~12 ms | 0 à 1 | 12 à 20 ms | instantané |
| virgule, `.`, `-`, apostrophe | ~12 ms | 0 à 1 | 12 à 25 ms | instantané |
| `⇧` majuscule | ~12 à 15 ms | 0 à 1 | 10 à 28 ms | instantané |
| `⏎` entrée, `⌫` retour arrière | ~12 ms | 0 à 1 | 12 à 20 ms | instantané |
| chiffre ou symbole (en mode num) | ~12 ms | 0 à 1 | 12 à 22 ms | instantané |
| espace | ~15 ms, puis recalcul des suggestions ~100 ms plus tard | 1 | 18 à 55 ms | correct |
| **`123` / `ABC`** | **~45 à 55 ms** (médiane 52 ms sur 11 bascules consécutives) | **3 à 4** | **55 à 90 ms** | **petit gel visible** |
| `😀` première ouverture du panneau emoji | 60 à 145 ms | 4 à 9 | 60 à 145 ms | net |
| `😀` bascule emoji vers ABC ensuite | 15 à 25 ms | 1 à 2 | 15 à 30 ms | léger |
| premier appui après ouverture du clavier | ~60 ms | 3 à 4 | ~60 ms | une seule fois |

### Observations

- **`123` est le seul geste courant à coûter systématiquement 3 à 4 frames.**
  Le chiffre est stable : 40 bascules consécutives donnent une moyenne de 40 ms
  de travail et 78 ms de latence (maximum 114 ms). Sur un process IME
  fraîchement redémarré, donc avec la liste interne vide, les premières bascules
  sont déjà à 43 ms de travail et 60 ms de latence. Ce n'est pas un effet de
  démarrage à froid, c'est le coût nominal.

- **Le premier appui sur n'importe quelle touche après l'ouverture du clavier
  coûte ~60 ms** (caches de glyphes et de textures froids). Cela n'arrive
  qu'une fois par apparition du clavier et se confond avec l'animation
  d'ouverture, donc peu gênant.

- **L'espace déclenche une seconde frame ~100 ms après l'appui**, quand
  `onWordCompleted` relance la génération des suggestions contextuelles
  (`serviceScope.launch { delay(100) ... }`). Le caractère espace, lui,
  s'insère dans la première frame et reste sous les 16 ms.

- **La première ouverture du panneau emoji est la plus lente** (jusqu'à
  145 ms), car `EmojiPickerView` (ViewPager2 plus RecyclerView) est construit à
  ce moment. Les ouvertures suivantes sont plus rapides mais l'instance est
  quand même recréée à chaque fois.

- **Dérive sur session longue** : sur 40 bascules `123` d'affilée, la latence
  passe de ~60 ms à ~95 ms. Cohérent avec la fuite décrite plus bas (pression
  sur le ramasse-miettes).

## Analyse : pourquoi `123` coûte cher

### Le chemin de code

`InputProcessor.handleModeSwitch()` bascule `isNumericMode` puis notifie le
service, qui exécute `onModeChanged` :

```
KreyolInputMethodServiceRefactored.kt:871  onModeChanged(isNumeric, isEmoji, ...)
  -> keyboardLayoutManager.updateKeyboardDisplay()      // parcourt toute la liste keyboardButtons
  -> refreshKeyboardLayout()                            // si le mode num ou emoji a changé
       KreyolInputMethodServiceRefactored.kt:1116
       -> retire l'ancien clavier du conteneur
       -> keyboardLayoutManager.createKeyboardLayout()  // KeyboardLayoutManager.kt:232
            -> createNumericLayout()                    // 4 rangées, ~34 touches
                 -> pour chaque touche : createKeyButton()      // :400
                      -> new Button / new ImageButton
                      -> applyGuadeloupeStyleToView()           // :758
                           -> new GradientDrawable, setStroke
                           -> setLayerType(LAYER_TYPE_SOFTWARE)  // :791
                           -> setShadowLayer()
                      -> keyboardButtons.add(button)            // :558, jamais retiré
       -> conteneur.addView(nouveauClavier)
```

Tout est synchrone, dans le traitement de l'événement tactile. Une lettre ou une
majuscule ne passe jamais par là : `updateKeyboardDisplay` (`:1015`) se contente
de réassigner `button.text` sur les touches existantes, sans réallocation ni
`requestLayout` profond.

### Décomposition d'une frame de bascule `123`

Mesure représentative (bascule à chaud, liste `keyboardButtons` déjà pleine) :

| Étape | Durée | Ce qui se passe |
|---|---|---|
| traitement de l'événement | ~0,2 ms | le clic, la bascule d'état, l'appel à `refreshKeyboardLayout` |
| mesure et placement | ~2 ms | layout des 4 rangées et des ~34 touches neuves |
| enregistrement des listes d'affichage | ~12 ms | 34 `RenderNode` neufs, mise en page du texte de 34 libellés |
| synchronisation et upload GPU | ~18 ms | rastérisation logicielle des 34 fonds de touche puis upload en texture (`Slow bitmap uploads` dans gfxinfo) |
| échange de tampons et fin | ~10 ms | |
| **total fil principal** | **~42 ms** | soit 2,5 frames de budget dépassées |

Les deux postes lourds sont l'enregistrement des listes d'affichage et l'upload
GPU. Ils sont proportionnels au nombre de `Button` construits : le pavé numérique
en a 34, la rangée de contrôle du panneau emoji seulement 4, ce qui explique que
la bascule vers l'emoji (hors première fois) soit trois à quatre fois moins
chère.

### Le rôle de `LAYER_TYPE_SOFTWARE`

`KeyboardLayoutManager.kt:791`, ajouté par le commit `caa64aca` (2026-08-06) :

```kotlin
if (key != " ") {
    view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    view.setShadowLayer(SHADOW_RADIUS, 0f, dpToPx(1).toFloat(), Color.parseColor("#40000000"))
}
```

Motivation d'origine : sur Honor 200 (Android 16), `setShadowLayer` combiné au
rendu matériel sur ~38 touches simultanées rendait le texte invisible selon le
pilote GPU. Le rendu logiciel écarte ce bug.

Conséquence sur tous les autres appareils : chaque touche devient un calque
bitmap rendu par le CPU, qui doit être re-rastérisé et re-uploadé en texture GPU
à chaque fois que quelque chose la concernant change. C'est le multiplicateur de
coût de toute reconstruction du clavier, et aussi une part du coût de chaque
appui sur `⇧` (qui restyle les touches shift via `applyGuadeloupeStyleToView`).

## Défaut secondaire : fuite de `keyboardButtons`

`KeyboardLayoutManager` tient une liste `private val keyboardButtons`
(`:136`). Elle est alimentée par `createKeyButton` (`:558`) et n'est vidée que
dans `cleanup()` (`:1153`), lui-même appelé uniquement depuis `onDestroy()` du
service.

Or `onDestroy` d'un IME n'arrive quasiment jamais : le service survit aux
changements d'application, de champ, de mode, pendant des jours. `keyboardLayoutManager`
est instancié une seule fois, dans `onCreate` via `initializeComponents()`.

Résultat : à **chaque** bascule `123` / `ABC` / emoji, `createKeyboardLayout`
reconstruit le clavier et `createKeyButton` **ajoute** 13 à 34 nouvelles vues à
la liste, sans jamais retirer les anciennes, devenues détachées. Après une
journée d'usage la liste peut contenir plusieurs centaines de `Button`, chacun
portant son `GradientDrawable` et son calque bitmap logiciel.

`updateKeyboardDisplay` (`:1015`, appelé à chaque `⇧` et à chaque changement de
mode) parcourt la liste entière, vues détachées comprises. L'impact direct sur
la latence est modéré, une vue détachée ne relance pas de layout, mais :

- c'est une vraie fuite mémoire pour la durée de vie du process,
- elle explique la dérive mesurée (latence de bascule qui monte de 60 à 95 ms
  sur 40 bascules).

## Recommandations

Classées par rapport gain sur effort. Les points 1 et 3 se tiennent ensemble et
constituent le correctif minimal utile.

> Les quatre points ont été appliqués le 2026-09-06. Voir « Corrections
> appliquées » plus bas pour ce qui a réellement été fait et les mesures après
> coup. Cette section garde l'analyse d'origine.

### 1. Ne plus reconstruire le clavier à chaque bascule de mode

**Gain** : la bascule `123` passe de ~50 ms à ~1 frame. C'est le gros du sujet.

**Approche** : construire les layouts alphabétique et numérique une seule fois
dans `onCreateInputView()`, les garder tous les deux dans l'arbre, et basculer
leur `visibility` entre `VISIBLE` et `GONE` dans `onModeChanged`. Le panneau
emoji, plus lourd et plus rare, peut rester en construction paresseuse mais son
instance doit être conservée entre deux ouvertures.

**Points d'attention** :

- les deux layouts n'ont pas la même structure de rangées (10 / 10 / 9 / 9
  touches en alpha, 10 / 10 / 10 / 4 en numérique), donc c'est bien un échange de
  sous-arbres entiers, pas une mutation touche par touche.
- `computeAvailableRowsHeight()` et `setAvailableRowsHeight()` doivent voir les
  deux layouts à la même hauteur.
- le thème : `onStartInputView` reconstruit déjà la vue quand la palette change
  (`setInputView(onCreateInputView())`), ce chemin reste valable et reconstruit
  les deux layouts d'un coup.
- vérifier que `keyboardButtons` pointe vers les touches du layout actif après
  bascule, pour que `updateKeyboardDisplay` et la popup d'accents visent les
  bonnes vues.

**Effort** : moyen. C'est le coeur de `KeyboardLayoutManager`, mais le
changement est localisé et testable au banc.

### 2. Restreindre `LAYER_TYPE_SOFTWARE` aux appareils concernés

**Gain** : divise par environ deux le coût de toute reconstruction, du premier
appui à froid, et de chaque `⇧`. Utile même si le point 1 est fait, car le
premier rendu du clavier reste concerné.

**Approche** : ne forcer le rendu logiciel que sur les familles d'appareils où
le bug d'origine se manifeste, par exemple un test sur `Build.MANUFACTURER` ou
`Build.SOC_MANUFACTURER` pour Honor et Magic UI, ou à partir d'un niveau d'API
donné. Ailleurs, conserver `setShadowLayer` en rendu matériel, ou renoncer à
l'ombre du texte : les touches sont déjà détachées du fond par leur dégradé et
leur bordure.

**Points d'attention** : ce garde-fou a été ajouté suite à un signalement
utilisateur réel. Ne pas le retirer aveuglément, le conditionner. Tester sur le
Honor si possible, sinon documenter le risque résiduel.

**Effort** : faible.

### 3. Vider `keyboardButtons` avant chaque reconstruction

**Gain** : corrige la fuite mémoire et la dérive de latence sur session longue.

**Approche** : appeler `keyboardButtons.clear()` en tête de
`createKeyboardLayout()` (ou dans `refreshKeyboardLayout()` juste avant de
retirer l'ancien clavier). Attention à ne pas nettoyer les listeners des
anciennes vues si elles sont encore affichées à cet instant, faire le clear
après le `removeView`.

Si le point 1 est retenu, ce défaut disparaît en grande partie de lui-même,
puisqu'il n'y a plus de reconstruction, mais un `clear` au bon endroit reste une
sécurité.

**Effort** : très faible.

### 4. Conserver l'instance `EmojiPickerView`

**Gain** : la première ouverture du panneau emoji passe de ~100 à 145 ms à
quelque chose de comparable aux ouvertures suivantes.

**Approche** : instancier `EmojiPickerView` une fois et la réutiliser, plutôt
que de la recréer dans `createEmojiLayout` à chaque entrée en mode emoji. La
liste des catégories est déjà figée à la construction (voir la note dans
`CLAUDE.md`), ce point est cohérent avec l'existant. Le seul recalcul nécessaire
à chaque ouverture est celui de la catégorie « Récents ».

**Effort** : faible à moyen.

### Non retenu

- Passer la reconstruction en tâche asynchrone : ne résout rien, le layout et le
  draw doivent de toute façon avoir lieu sur le fil principal. Il faut réduire
  le travail, pas le déplacer.
- Réduire le nombre de touches du pavé numérique : les rangées à 10 touches
  alignent les largeurs sur le clavier alpha, les rogner dégraderait la visée.

## Corrections appliquées (2026-09-06)

### Ce qui a été fait

**`KeyboardLayoutManager`**

- `createKeyboardLayout()` ne construit plus le clavier du mode courant mais un
  `FrameLayout` contenant les deux panneaux, alpha et numérique, montés une seule
  fois. Il renvoie ce conteneur (type de retour passé de `LinearLayout` à
  `View`). `keyboardButtons` et `emojiPanelButtons` sont vidées en tête (point 3).
- `applyMode()` (nouvelle méthode publique) affiche le panneau du mode courant et
  masque les autres. Alpha et numérique se masquent en **`INVISIBLE`** et non
  `GONE` : les deux font quatre rangées de même hauteur, rien ne se remet en
  page, et la liste d'affichage du panneau masqué reste enregistrée, si bien que
  la bascule n'est plus qu'un redessin. Le panneau emoji, de hauteur différente,
  est monté à la demande (`rebuildEmojiPanel`) et retiré en sortie
  (`dropEmojiPanel`), ce qui garde « Récents » recalculé à chaque ouverture
  comme le veut le commentaire de `EmojiPickerView`. Les touches de sa rangée de
  contrôle sont suivies dans `emojiPanelButtons` et retirées de `keyboardButtons`
  au démontage, pour ne pas rouvrir la fuite d'un cran à chaque passage.
- `applyGuadeloupeStyleToView` : `setLayerType(LAYER_TYPE_SOFTWARE)` n'est plus
  posé que si `forcerRenduLogiciel` est vrai, c'est-à-dire sur les ROM Honor et
  Huawei (`Build.MANUFACTURER`). `setShadowLayer` reste posé partout : l'ombre est
  identique, seul le calque logiciel disparaît ailleurs. C'est le comportement
  d'avant le commit `caa64aca`, qui avait tourné sans incident sur toute la flotte
  sauf ce Honor 200.
- `cleanup()` remet à `null` les références de panneaux.

**`KreyolInputMethodServiceRefactored`**

- `refreshKeyboardLayout()` ne retire plus l'ancien clavier pour en poser un neuf,
  il appelle `keyboardLayoutManager.applyMode()`.
- `onStartInputView()` : après `forceAlphabeticMode()`, appelle `applyMode()`. Les
  drapeaux de mode ne suffisent plus à eux seuls à changer ce qui est visible, il
  faut rendre le bon panneau visible quand on revient sur un champ après avoir
  quitté le précédent en mode 123 ou emoji.

**`SettingsActivity`** (clavier de démonstration de l'onglet Démarrage)

- Les bascules « 123 » et « EMOJI » appellent `manager.applyMode()` au lieu de
  vider puis re-remplir le conteneur.

### Mesures après coup

Toutes sur le **Samsung Galaxy A21s réel**, en réinstallant successivement le
build debug avec puis sans les corrections, même appareil, même champ.

#### Bascule `123` / `ABC`

22 à 24 bascules consécutives, pire frame de chaque (méthode de l'annexe).

| | avant | après |
|---|---|---|
| médiane | ~72 ms | **~25 ms** |
| moyenne | ~72 ms | **~25 ms** |
| plage | 57 à 89 ms | 19 à 40 ms |
| frames sautées à 60 Hz | ~4 à 5 | **~1 à 2** |
| dérive sur 24 bascules | légère | nulle (fuite fermée) |

Soit près de **trois fois plus rapide** sur le matériel visé. La bascule passe
d'un gel nettement perceptible à un accroc à peine visible. Deux séries à une
heure d'écart : la première mesurait ~74 → ~32 ms, la seconde ~72 → ~25 ms une
fois le processus complètement en régime.

Émulateur (rendu logiciel swiftshader, pire cas) pour mémoire : ~90 ms → ~40 ms,
et surtout la dérive de la fuite disparaît (avant : 84 → 184 ms sur 20 bascules ;
après : plate).

Le plancher restant (~25 ms sur l'A21s) est la ré-inscription de la liste
d'affichage et la rastérisation des ~34 `GradientDrawable` quand le panneau
`INVISIBLE` repasse `VISIBLE`. Le supprimer demanderait une vue clavier unique
qui peint ses touches elle-même (à la façon d'AOSP), soit une réécriture bien
plus lourde, non entreprise ici.

#### Saisie de phrases

Trois phrases kréyòl tirées de `creole_cloze.json` (auteurs guadeloupéens),
86 caractères, tapées touche par touche à 130 ms d'intervalle, trois passes de
chaque côté. Agrégat `gfxinfo` de la session de frappe :

| | avant | après |
|---|---|---|
| frame médiane (p50) | ~25 ms | ~25 ms |
| p90 | ~33 ms | ~31 ms |
| p95 | ~37 ms | ~33 ms |
| **p99 (pire 1 %)** | **~60 ms** | **~39 ms** |
| **« Slow bitmap uploads »** | **5 à 6 par passe** | **0** |
| frames en retard sur la vsync | 18 à 62 | 3 à 13 |
| durée de frappe | ~37 s | ~37 s |

La frappe lettre par lettre était déjà correcte des deux côtés : une frappe = un
redessin de la barre de suggestions, ~1 frame, inchangé par le correctif qui
vise le changement de mode. Ce que le correctif enlève, c'est la queue de
distribution : le p99 tombe de ~60 à ~39 ms, et surtout les uploads GPU de
bitmaps disparaissent. La `baseline` re-rastérise et ré-uploade les 34 calques
logiciels des touches à chaque redessin du clavier (capitalisation automatique
après « . », bascule Maj) ; hors Honor et Huawei le correctif ne le fait plus.
Le jank est divisé par environ quatre. Le débit de frappe est borné par la
cadence du test, pas par le clavier, dans les deux cas.

Phrases : « An ka voyé on sms ba-w. », « Ka kouté tousa, ka louké tousa. »,
« Nou ni èvè mori poulé zé fwomaj. ».

#### Validation fonctionnelle

Sur A21s réel : bascule 123 aller-retour, pavé numérique complet, panneau emoji
complet, majuscule, frappe des trois phrases, IME stable. Sur émulateur en plus :
popup d'accents, catégorie « Récents » à jour, suggestions. Tests unitaires au
vert.

## Annexe : reproduire la mesure

```bash
PKG=com.potomitan.kreyolkeyboard
D=<serie-adb-de-l-appareil>

# 1. garder l'écran allumé, sinon les mesures deviennent vides sans erreur
adb -s $D shell svc power stayon true

# 2. ouvrir un champ et afficher le clavier
adb -s $D shell am start -a android.intent.action.SENDTO -d sms:0590000000
adb -s $D shell input tap 350 1440       # focus du champ
# verifier :
adb -s $D shell dumpsys input_method | grep mInputShown   # doit dire true

# 3. pour chaque touche, en boucle serree :
adb -s $D shell dumpsys gfxinfo $PKG reset
sleep 0.2
adb -s $D shell input tap <x> <y>        # la touche a mesurer
sleep 0.7
adb -s $D shell dumpsys gfxinfo $PKG framestats
```

Dépouillement du bloc `---PROFILEDATA---`, colonnes utiles sur cet appareil
(l'ordre varie selon la version d'Android, toujours vérifier l'en-tête) :

| Index | Champ | Usage |
|---|---|---|
| 3 | IntendedVsync | début du budget de la frame |
| 4 | Vsync | |
| 6 | HandleInputStart | début du travail lié à l'événement |
| 8 | PerformTraversalsStart | |
| 9 | DrawStart | |
| 14 | SyncStart | fin de l'enregistrement des listes d'affichage |
| 15 | IssueDrawCommandsStart | fin de la synchro et de l'upload GPU |
| 16 | SwapBuffers | |
| 17 | FrameCompleted | fin de frame |

- frame = `(FrameCompleted - Vsync) / 1e6` en ms
- latence = `(FrameCompleted - IntendedVsync) / 1e6` en ms

Pour le volet **saisie de phrases**, la mesure ne porte plus sur une frame mais
sur la session entière : `dumpsys gfxinfo $PKG reset`, taper les phrases touche
par touche (`input tap` sur chaque lettre, ~130 ms d'intervalle, champ vidé au
`⌫` à l'écran entre deux phrases), puis relire l'agrégat de `dumpsys gfxinfo
$PKG` (hors `framestats`) : `Total frames rendered`, `Janky frames`, les
centiles `50th`/`90th`/`95th`/`99th percentile`, `Number Slow bitmap uploads`,
`Number Missed Vsync`. Le champ « Nom » des contacts met les mots en majuscule
tout seul, on tape donc en minuscule. Le `%` de jank est trompeur sur une frappe
scriptée (les `input tap` ne s'alignent pas sur la vsync comme un vrai doigt) :
comparer les centiles et les compteurs, pas le pourcentage brut.

Pièges rencontrés :

- Le buffer `framestats` de cet appareil ne retient que deux frames. La seule
  méthode fiable est `reset` juste avant l'appui, puis lecture immédiate.
- Ne pas intercaler de `KEYCODE_*` (par exemple `KEYCODE_DEL` pour vider le
  champ) entre deux appuis mesurés : après deux ou trois, la capture ne renvoie
  plus rien. Rester sur des `input tap` sur les touches à l'écran.
- `/proc/uptime` n'est pas la même horloge que `framestats` (l'un inclut les
  périodes de veille, l'autre non), ne pas croiser les deux.
- Si l'écran s'éteint en cours de série, toutes les mesures suivantes sont
  vides, sans message. D'où le `svc power stayon true`.
