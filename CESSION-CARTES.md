# Céder une carte : de la main à la main, par deux codes QR

Reprise de LuxKeyb (35.0.0 à 36.0.0), portée en 23.0.0 avec la cession
elle-même. La note d'origine date du 10 octobre 2026 et a été écrite avant le
code ; elle est gardée telle quelle, sauf le préfixe des codes et ce qui
concerne le jeu de nombres luxembourgeois (Zuelwuert), sans équivalent ici.
À la racine, elle ne déclenche pas le build APK.

---

## 1. Ce que veut le propriétaire

Une carte doit pouvoir **se céder** : elle quitte le carnet du donneur et
arrive dans celui d'un autre téléphone, comme une carte physique qui passe
d'une main à l'autre. Le partage en image (`PartageCarte`) montre une carte ;
la cession la **donne**.

## 2. Pourquoi de la main à la main, et pas par un lien

Une carte du carnet est une ligne de données, et une donnée se copie. Céder,
c'est garantir qu'elle ne se retrouve pas en double.

- **À distance (WhatsApp, lien)**, il faut un tiers qui garde la carte pendant
  le trajet et refuse un second retrait : sans lui, le même lien ouvert sur
  trois téléphones fait trois cartes. Ce tiers est un serveur, donc un
  hébergement, une politique de confidentialité réécrite (« rien ne quitte le
  téléphone »), une fiche de sécurité des données refaite et des questions
  RGPD. Écarté.
- **De la main à la main**, les deux téléphones sont côte à côte et peuvent
  se confirmer l'échange l'un à l'autre. Pas de serveur, rien sur Internet.
  Retenu par le propriétaire le 10 octobre 2026.

## 3. Le protocole : deux codes QR, la carte part avant d'arriver

**Version 2, depuis la 35.1.0.** La version 1 (35.0.0 à 35.0.2) faisait entrer
la carte chez le receveur *avant* qu'elle quitte le donneur, qui devait encore
scanner une confirmation. L'essai réel du 10 octobre 2026 (A21s et émulateur)
l'a montré : le receveur a fermé sa confirmation trop tôt, et la carte a existé
sur les deux téléphones. Deux autres trous restaient : un donneur qui part
avant d'avoir scanné garde sa carte, et une même offre scannée par trois
téléphones en donne trois. Le propriétaire veut qu'une carte cédée le soit
**définitivement** ; l'ordre a donc été inversé.

1. Le **receveur** touche « Recevoir » dans son carnet. Son téléphone tire un
   jeton, le note comme demande en attente, et affiche le **code de demande**.
2. Le **donneur** ouvre la carte, touche « Céder », lit l'explication et
   scanne la demande. La carte **quitte son carnet à cet instant**, et son
   téléphone affiche le **code de remise**, lié au jeton de la demande.
3. Le receveur scanne la remise. Elle ne s'ouvre que si son jeton est une
   demande en attente de **ce** téléphone, et une seule fois. La carte entre.

```
KREYOLKEYB:D:2:<jeton>                       demande
KREYOLKEYB:C:2:<jeton>:<nombre>:<forme>      remise
```

- `<jeton>` : 16 caractères hexadécimaux tirés au hasard par le **receveur**.
  Une demande vit une semaine (`VIE_DEMANDE_S`), puis ne reçoit plus rien.
- `<forme>` vient en dernier parce qu'elle peut contenir « : ». `<nombre>`
  porte côté LuxKeyb la valeur d'un numéral de Zuelwuert ; il reste vide ici,
  le format étant gardé identique.
- Le préfixe est propre à chaque clavier (`LUXKEYB`, `KREYOLKEYB`) : le code
  d'une carte luxembourgeoise n'est pas lu comme une carte kréyòl.
- Plus d'échéance de dix minutes : une remise n'est pas une offre qu'on
  pourrait rejouer ailleurs, elle ne sert qu'au téléphone qui l'a demandée.
- Un code de la version 1 est reconnu et refusé avec « l'autre téléphone a une
  ancienne version », plutôt que « ce n'est pas une carte ».

### Ce que le protocole garantit, et ce qu'il ne garantit pas

- **La carte n'existe jamais des deux côtés.** Elle quitte le donneur avant
  qu'aucune remise n'existe.
- **Une remise ne sert qu'une fois, et qu'à un téléphone** : celui dont le
  jeton est en attente, qui le clôt en recevant. Trois téléphones qui scannent
  la même remise : un seul reçoit, les deux autres lisent « ce code a été
  préparé pour un autre téléphone ».
- **Le prix est l'inverse du doublon : une remise jamais scannée est une carte
  perdue.** Pour que cela n'arrive pas par simple fermeture d'écran, la remise
  est écrite sur le disque *avant* que la carte quitte le carnet, et elle reste
  remontrable par un bandeau en haut du carnet (« attend d'être scannée par
  l'autre téléphone ») tant que le donneur n'a pas touché « C'est fait, elle
  est arrivée », ou pendant trente jours (`VIE_REMISE_S`).
- **Le receveur ne refuse que ce que l'appli ne propose jamais** : un mot
  écarté par `MotsEcartes`, ou un numéral dont l'écriture ne correspond pas à
  sa valeur. Il n'exige plus que le mot soit traduit : un téléphone réglé en
  anglais ne glose pas 3 351 mots qu'un téléphone en français fait gagner, et
  refuser serait perdre la carte en route.
- **Une remise peut toujours être fabriquée** par qui lit cette note, à
  condition de connaître un jeton en attente du receveur : rien ne la signe.
  L'enjeu, un mot de plus dans un carnet, ne vaut pas un serveur.

## 4. Ce qui voyage avec la carte

- **Le mot.**
- **Rien de l'histoire du donneur** : ni le sceau, ni la plume, ni la boîte de
  révision, ni le compteur de rencontres. Apprendre un mot et l'avoir écrit
  appartiennent à celui qui l'a fait. Le receveur part de zéro sur ce mot.
- **Une provenance « Cadeau »** (🎁), qui s'ajoute aux sept jeux sans compter
  dans la série des sept jeux, comme la carte de bienvenue. Aucun nom : rien ne
  dit qui a donné la carte.

Si le receveur a **déjà** la carte, elle devient un doublon : son compteur de
rencontres monte et la provenance « Cadeau » s'ajoute. Le donneur la perd
quand même : c'est un cadeau.

## 5. Ce qui change dans le carnet

- **Le carnet ne fait plus que grandir.** C'était un invariant écrit ; il tombe
  par décision du propriétaire. Céder une carte de série rouvre la case chez le
  donneur, et c'est ce qui donne sa valeur à l'échange.
- **Le numéro de série devient stocké.** Il était dérivé de la position dans la
  liste ; retirer une carte aurait renuméroté toutes les suivantes. Il est
  écrit dans le blob (`k`), et les cartes déjà là reçoivent à la lecture le
  numéro qu'elles affichaient. Une carte reçue prend le numéro suivant du
  carnet du receveur : c'est son ordre d'entrée chez lui.
- **La plume d'une carte cédée est effacée** de `carnet_plumes.json` : si le mot
  revient un jour, il devra la regagner.

## 6. Lire et dessiner les codes

- **Dessiner** : `com.google.zxing:core`, l'encodeur seul, en pur Java.
- **Lire** : `com.journeyapps:zxing-android-embedded`, qui décode sur
  l'appareil. **L'appareil photo n'est demandé qu'au moment du scan**, jamais à
  l'installation. Le scanner de Google (ML Kit) éviterait cette autorisation,
  mais il contacte les serveurs de Google pour ses modèles et ses statistiques
  d'usage, ce que la politique de confidentialité exclut.
- **Pour tester sur l'émulateur**, qui n'a pas de vraie caméra, un build de
  debug lit le contenu à scanner dans `files/scan_debug.txt` s'il existe, au
  lieu d'ouvrir la caméra. Jamais en release (`BuildConfig.DEBUG`).

## 7. Ce qu'il reste à faire hors code

- **La politique de confidentialité** doit mentionner l'appareil photo :
  utilisé uniquement pour lire le code d'une carte, aucune image conservée ni
  envoyée.
- **La fiche Play Store** : l'autorisation caméra apparaît dans la liste des
  autorisations.
