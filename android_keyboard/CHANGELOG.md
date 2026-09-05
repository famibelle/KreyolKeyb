# Changelog

Toutes les modifications notables de ce projet seront documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/),
et ce projet adhère au [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [12.0.0] - 2026-09-05

### ✨ Ajouté

- **Un onglet « Dictionnaire », et la traduction française des mots kréyòl.**
  On y cherche un mot dans les deux sens : « kaz » comme « maison ». Le sens de
  la requête est déduit et non demandé, ce qui évite le piège des mots que les
  deux langues partagent : chercher « livre » remontait sinon l'homographe
  kréyòl avant le mot cherché. Toucher un résultat ouvre sa fiche — sens,
  prononciation, exemples, source — et un appui long le copie.
- **Les quatre jeux disent enfin ce que veut dire le mot qu'ils font
  chercher.** Reconstituer « KAPTÈ » sans savoir qu'il s'agit d'un capteur
  exerçait l'orthographe et rien d'autre. La traduction apparaît au moment où
  elle ne dévoile plus rien : à la fin de la partie pour Mo an Karénaj, une
  fois le mot reconstitué pour Mots Mélangés, sous chaque mot de la liste pour
  Mots Mêlés, avec la réponse pour Mots à Trous.
- **Le mot du jour porte sa traduction**, annoncée « en français : … » plutôt
  que posée sous le mot : une glose seule ne dit pas dans quelle langue on la
  lit. La liste des mots les plus utilisés en fait autant.
- **1 145 formes traduites**, dont 513 des 5 296 mots du dictionnaire, soit
  36 % de tout ce que le corpus écrit. Les 632 autres sont des mots absents du
  corpus : ils ne servent qu'à la recherche. Les sources sont Kreyolopedia et
  le Wiktionnaire francophone, toutes deux en CC BY-SA 4.0 ; elles sont créditées
  dans la fiche de chaque mot et en pied de l'onglet.

### 🔧 Modifié

- **La barre d'onglets passe de trois à quatre.** Les quatre libellés tiennent
  encore sur une ligne, y compris « Kréyòl an mwen », le plus long, et jusqu'aux
  écrans de 360 dp. Il passe à deux lignes quand la police système est
  agrandie, sans jamais être coupé.
- **Les jeux ne tirent plus que parmi les mots traduits.** Ils y perdent en
  réserve — Mo an Karénaj passe de 882 mots de cinq lettres à 93 — et y gagnent
  de ne plus jamais finir une partie sur un mot dont ils ne peuvent rien dire.
  Une proposition du joueur, elle, reste acceptée dès lors qu'elle est au
  dictionnaire, traduite ou non.

### ⚠️ Limites connues

- **Un mot kréyòl sur dix seulement est traduit**, et `sé`, troisième mot le
  plus fréquent de la langue, n'est glosé par aucune source libre. Les
  dictionnaires de référence sont sous droit d'auteur ; ce qui manque ici
  manque partout ailleurs en accès libre. La couverture montera quand
  Kreyolopedia et le Wiktionnaire grandiront, sans que l'application change.

## [11.0.0] - 2026-09-05

### ✨ Ajouté

- **Un quatrième jeu : « Mots à Trous ».** Une vraie phrase kréyòl s'affiche, un
  mot lui manque, et quatre propositions sont offertes dont une seule est celle
  qu'a écrite l'auteur. Les trois autres sont des mots que le corpus atteste au
  même endroit : elles sonnent juste tant qu'on ne lit pas toute la phrase. 389
  phrases, réparties sur trois niveaux de difficulté, avec la source de chacune
  affichée sous elle.

### 🔧 Modifié

- **La barre d'onglets passe de sept à trois.** Les quatre jeux se rejoignent
  derrière un onglet « Jé ». À sept onglets, chacun disposait d'un septième de
  la largeur et les libellés se coupaient en plein milieu d'un mot ; les trois
  destinations restantes — Démarrage, Kréyòl an mwen, Jé — respirent, et la
  police des libellés remonte de 9 à 11 sp.
- **Le guide et « À Propos » quittent la barre pour le pied de l'onglet
  Démarrage.** Ce sont des pages qu'on lit une fois, et elles coûtaient deux
  septièmes de la largeur à chaque ouverture. Elles s'ouvrent maintenant en
  plein écran, depuis l'endroit où l'on vient déjà quand on cherche à
  comprendre.
- **Les jeux et les statistiques s'ouvrent sans attendre la configuration
  complète du clavier.** Il fallait jusqu'ici que le clavier soit activé *et*
  sélectionné ; quelqu'un qui en avait essayé un autre, ou qu'une mise à jour du
  téléphone avait désélectionné, se retrouvait renvoyé dans le tunnel
  d'installation alors qu'il connaissait déjà l'application. Les onglets
  reviennent dès la première activation, et ne repartent plus.

### 🐛 Corrigé

- **Un bouton pour revenir au clavier kréyòl.** Quand un autre clavier a pris la
  main, l'onglet Démarrage l'annonce en tête d'écran et propose de rechoisir le
  kréyòl en un appui. Le clavier n'est ni désinstallé ni désactivé dans ce cas,
  et le message le dit : il n'y a qu'à le rechoisir. Le second chemin est
  rappelé juste en dessous — la petite icône de clavier en bas de l'écran,
  pendant que vous écrivez, ouvre le même choix sans quitter l'application où
  vous êtes.
- La carte « Configuration rapide » n'annonce plus une mise à jour du téléphone
  quand le clavier a simplement changé : elle dit ce qui est, sans en supposer
  la cause.
- **Le raccourci « Découvrez vos statistiques » n'atterrit plus au bord du
  monde.** Il affichait le bon contenu, mais déposait à un endroit d'où plus
  rien ne se balayait vers la gauche.
- Les pastilles de mots de l'onglet « Kréyòl an mwen » retrouvent leur fond
  pastel. Il était brunâtre : une opacité concaténée à une couleur ne produit
  pas une transparence, mais une autre teinte, opaque.

## [10.14.8] - 2026-08-26

### 🤫 Le guide révélait le huitième niveau

La section « Progression » annonçait « huit niveaux culturels » et les nommait
tous. Le huitième est un easter egg : il n'a pas à être annoncé, `ASTUCES.md`
le dit depuis longtemps et l'onglet « Kréyòl an mwen » le respecte déjà, ne
montrant jamais que le niveau atteint et le suivant.

Le guide s'aligne : sept niveaux nommés, de Pipirit à Potomitan, et un
huitième à trouver.

## [10.14.7] - 2026-08-26

### 📖 Le guide décrivait une application qui avait changé sous lui

Six passages de l'onglet « Guide » ne correspondaient plus à ce que
l'utilisateur a sous les yeux. Deux comptaient vraiment.

Le geste des accents, d'abord : le guide demandait de glisser le doigt vers
l'accent voulu puis de relâcher. C'est le geste de Gboard, pas le nôtre. Les
touches de la fenêtre d'accents ne répondent qu'au clic ; un geste continu
parti de la lettre n'insère rien. La fenêtre reste ouverte au relâchement, et
c'est un second appui qui choisit l'accent : le guide le dit maintenant.

L'étape 4, ensuite : le correcteur orthographique a été sorti du parcours
numéroté pour ne pas laisser croire à une quatrième étape face à un anneau qui
en annonce trois. Le guide y envoyait toujours. Il renvoie désormais à la carte
« Corriger l'orthographe partout », sous « Pou ay pli lwen ».

Le reste est du vocabulaire à réaligner : les boutons « Ouvrir les réglages
Android » et « Ouvrir le sélecteur de claviers » étaient cités sous leurs
anciens noms, le parcours était décrit avec deux étapes au lieu de trois, et
les jeux étaient annoncés au nombre de deux alors que Mo an Karénaj les a
portés à trois en 10.3.0.

Rien ne change dans le clavier lui-même.

## [10.14.6] - 2026-08-26

### 👆 La carte du correcteur se touche, elle n'a plus de bouton

Le correcteur orthographique est une option, pas une étape du parcours
d'installation. Son bouton bleu pleine largeur lui donnait pourtant le même
poids visuel que les trois étapes qui, elles, conditionnent l'usage du clavier.
Il disparaît : la carte entière devient la cible du toucher, signalée par un
chevron, et se replie de moitié.

Reste visible la promesse, et une ligne sur ce qu'Android va demander. Ce
dernier point ne peut pas être replié : le système prévient qu'un correcteur
« peut collecter tout le texte que vous tapez, y compris des données
personnelles comme les mots de passe », et c'est là que l'utilisateur non
prévenu renonce. La marche à suivre, elle, se déplie sous « Ce qu'Android va
vous demander », un intitulé qui annonce ce qu'on y trouve.

La promesse s'énonce enfin par ce qu'elle apporte : « Faites reconnaître vos
mots kréyòl dans Messages, Notes et ailleurs, sans trait rouge dessous », là où
la formulation précédente commençait par une privation. Le trait rouge reste
nommé, c'est à lui que la gêne se reconnaît.

Une fois le correcteur choisi, la carte tient en deux lignes, badge et coche
verts.

## [10.14.5] - 2026-08-26

### ✂️ La section optionnelle de l'onglet Démarrage, deux fois plus courte

Les deux cartes qui suivent la configuration en trois étapes étaient devenues
des pavés : celle du correcteur orthographique tenait en onze lignes, dont une
marche à suivre numérotée, et celle de la bascule entre claviers en cinq
paragraphes. Sur un écran de téléphone, la première remplissait tout ce qui
restait sous le titre de section.

Les deux sont resserrées sans rien perdre de ce qui débloque réellement
l'utilisateur : pour le correcteur, le sous-menu « Correcteur par défaut »
que rien ne signale et l'avertissement d'Android sur l'accès au texte saisi ;
pour la bascule, l'asymétrie du geste de retour, l'appui long sur la barre
d'espace des autres claviers ne changeant que leur propre langue. Ce qui part,
ce sont les noms de claviers cités en exemple et les redites.

Chaque carte tient maintenant dans un écran, bouton compris.

## [10.14.4] - 2026-08-26

### 🧹 Un seul chemin d'écriture pour les compteurs de vocabulaire

L'écran des réglages portait un second système d'enregistrement des compteurs
d'usage, complet mais sans appelant : son commentaire annonçait qu'il était
« appelé depuis le clavier », ce qui n'était pas le cas. Il écrivait le même
fichier que le clavier, mais dans un format différent, qui n'aurait pas conservé
les fréquences du corpus. Rebranché un jour par inadvertance, il aurait effacé
en silence une information que rien ne permet de reconstituer sur l'appareil.

Il est supprimé. Le clavier garde son unique chemin d'écriture, inchangé, et
l'onglet « Kréyòl an mwen » continue de relire le fichier à chaque ouverture.

Aucun changement visible : mêmes compteurs, mêmes statistiques, mêmes niveaux.

## [10.14.3] - 2026-08-26

### 🔒 Le clavier ne demande plus que la notification

Deux autorisations quittent le manifeste : l'écriture dans le dictionnaire
personnel d'Android et la lecture du stockage. Ni l'une ni l'autre n'avait
d'appelant dans le code. La première avait été ajoutée pour le dictionnaire
personnel de la 10.5.0, fonction supprimée dès la 10.6.0 ; la seconde n'a
jamais servi. L'attribut `requestLegacyExternalStorage` part avec elles :
il n'accompagnait que la seconde, et il est sans effet depuis targetSdk 30.

Une autorisation déclarée s'affiche sur la fiche Play et doit être justifiée
dans la politique de confidentialité. En garder une dont rien ne se sert coûte
de la confiance sans rien apporter. Il reste `POST_NOTIFICATIONS`, la seule que
le clavier exerce, pour annoncer un nouveau niveau de vocabulaire.

Rien ne change à la frappe, aux suggestions, aux accents ni aux jeux.

### 📄 La politique de confidentialité dit ce que l'application fait

Elle décrivait encore le dictionnaire personnel de la 10.5.0, à cinq endroits,
et omettait `POST_NOTIFICATIONS`, la seule autorisation réellement demandée.
Elle documente désormais ce qui existe : des compteurs d'usage portant
uniquement sur les mots déjà présents dans le dictionnaire livré, un mot absent
n'étant jamais enregistré.

Le site accueille par ailleurs une page de questions fréquentes.

## [10.14.2] - 2026-08-26

### 🔤 Les lettres des touches perdent leur gras

Les libellés des touches étaient affichés en gras depuis les premières versions.
À la taille de police que le clavier calcule à partir de la hauteur de touche,
cette graisse couvrait un tiers de matière sombre en plus (6,5 % de pixels
sombres contre 4,7 % sur les trois rangées de lettres) et refermait les blancs
internes du g et du m, jusqu'à donner l'impression d'un texte surchargé.

Les touches passent en graisse normale. Elles restent distinguées par leur fond
et leur ombre, qui font déjà le travail de séparation, et la lettre respire dans
sa touche. Les icônes (majuscule, retour arrière, emoji) ne sont pas concernées :
ce sont des dessins, pas du texte.

## [10.14.1] - 2026-08-25

### ⬇️ Le clavier d'essai passe sous les étapes de configuration

Sur un premier lancement, le clavier à essayer occupait toute la hauteur
visible : le bouton qui ouvre les réglages Android, pourtant dans l'étape
dépliée juste en dessous, ne se voyait qu'après avoir fait défiler l'écran.
L'action attendue de l'utilisateur est désormais visible dès l'ouverture, et le
clavier d'essai reste la première chose que l'on rencontre en descendant.

## [10.14.0] - 2026-08-25

### 📍 L'écran de démarrage tient sur une hauteur d'écran

Les trois étapes de configuration occupaient chacune une carte pleine, sous une
carte d'accueil et sa barre de progression : cinq blocs à faire défiler avant la
première action. Elles deviennent trois lignes dans une carte « Configuration
rapide », avec un anneau qui affiche ce qu'il reste à faire plutôt que le chemin
parcouru.

Une seule étape est ouverte à la fois, celle qui reste à faire. Elle garde tout
ce dont elle a besoin : ce qu'il faut chercher dans l'écran Android qui va
s'ouvrir, l'avertissement de sécurité que le téléphone affiche pour tous les
claviers tiers, et le rappel des deux validations successives quand un
aller-retour dans les réglages n'a rien activé. Les étapes déjà faites se
replient sur une ligne, et se rouvrent d'une touche pour refaire le chemin.

Une fois les trois étapes faites, la carte se réduit à son en-tête : le haut de
l'écran cesse d'être occupé par une liste de cases cochées.

### ✍️ « Essayer le clavier » se coche quand vous avez écrit

La troisième étape se validait toute seule dès que le clavier était sélectionné,
alors qu'il n'y avait encore rien eu à essayer. Elle attend désormais le premier
mot réellement écrit avec le clavier, dans le champ d'essai ou dans n'importe
quelle application, et se coche pendant la frappe sans interrompre la saisie.

Le bandeau vert le dit dans les mêmes termes : « Clavier en place » tant que rien
n'a été écrit, « Tout est prêt » ensuite.

## [10.13.1] - 2026-08-25

### ⚙️ L'engrenage des réglages redevient lisible sur le bandeau bleu

Retour utilisateur : l'accès aux réglages du clavier, en haut à droite de
l'écran d'accueil, ne se voyait pas. L'icône était l'emoji « ⚙️ », que la
police du téléphone dessine en gris bleuté : posée sur le bandeau bleu de
l'application, elle s'y fondait au lieu de s'en détacher.

C'est désormais une icône dessinée, en blanc plein, comme le titre à côté
d'elle. Son contraste ne dépend plus de la police emoji installée, donc elle
ressort de la même façon sur tous les téléphones. La zone tactile reste de
48 dp et le bandeau garde exactement la même hauteur.

## [10.13.0] - 2026-08-24

### 🌙 Le clavier passe en thème sombre

Le clavier restait blanc quelle que soit l'heure et quel que soit le réglage du
téléphone : dans une conversation affichée en sombre, il éclairait l'écran à
chaque saisie. Il suit désormais le mode sombre du système, et ses touches de
lettres passent alors du blanc à l'anthracite.

Le vert, l'orange et le bleu de la charte ne changent pas d'un thème à l'autre :
ce sont eux qui rendent ce clavier reconnaissable, et leurs contrastes ne
dépendent pas de ce qui les entoure. Ne basculent que les touches de lettres et
ce qui en découle : l'encre, le contour, le fond du clavier, la barre de
propositions, la fenêtre d'appui long et le panneau emoji. Le clavier clair ne
change pas d'un pixel.

### 🎚️ Trois positions dans les réglages du clavier

Une carte « Apparence » ouvre le choix entre « Comme le téléphone », « Toujours
clair » et « Toujours sombre ». Les deux dernières existent pour la même raison
que les interrupteurs de vibration et de son : sur plusieurs surcouches, le
réglage jour/nuit du téléphone ne descend pas jusqu'aux claviers tiers, et sans
elles l'utilisateur n'aurait aucun moyen d'obtenir le clavier qu'il veut. Le
choix s'applique dès le retour dans un champ de saisie.

## [10.12.15] - 2026-08-23

### #️⃣ La touche « # » arrive en mode 123

Le hashtag ne pouvait pas s'écrire avec ce clavier. La page de symboles du mode
123 est la seule qu'il propose, il n'y a pas de seconde page de caractères comme
sur d'autres claviers, donc un caractère absent de cette page est un caractère
qu'il faut aller chercher ailleurs. « # » y figure désormais, en troisième
rangée, sous « @ ».

L'ajout ne coûte rien aux touches voisines : cette rangée n'en portait que neuf
là où les deux du dessus en portent dix. La dixième aligne les trois rangées sur
la même largeur de touche.

## [10.12.14] - 2026-08-22

### 🔤 « Ba kréyòl-la lanmou a-w » et vocabulaire enrichi

La carte Partage de l'onglet À Propos écrivait « Ba kréyòl la lanmou'w ». La
forme correcte est « Ba kréyòl-la lanmou a-w » : le déterminant se lie au nom
par un trait d'union, et le possessif s'écrit « a-w » plutôt que collé au nom.
La formule a été reprise dans l'application, dans le pack ASO du Play Store et
dans la capture d'écran publiée sur le site.

Le dictionnaire et le modèle n-gram embarqués sont régénérés à chaque build
depuis le dataset POTOMITAN/PawolKreyol-gfc : cette version reprend le
vocabulaire ajouté au corpus depuis la précédente.

## [10.12.13] - 2026-08-22

### 🔄 Comment revenir au clavier kréyòl, expliqué dans l'onglet Démarrage

Une fois le clavier activé et sélectionné, une carte explique comment passer d'un
clavier à l'autre. Le point important est que le geste n'est pas symétrique :
l'appui long sur la barre d'espace quitte bien le clavier créole, mais sur Gboard
et la plupart des autres claviers le même geste ne change que leur propre langue
interne et ne ramène nulle part. Le chemin de retour est l'icône de clavier de la
barre de navigation, affichée tant qu'un clavier est ouvert. La carte le dit, et
son bouton ouvre le même sélecteur pour les téléphones qui n'affichent pas cette
icône. Le guide du site le documentait déjà, pas l'application. Vérifié sur
émulateur Pixel 5.

Deux textes qui disaient l'inverse ont été repris au passage. La question
« le clavier créole n'apparaît pas quand je tape ? » de l'onglet Guide
renvoyait à l'appui long sur la barre d'espace, ce qui laissait croire que ce
geste ramène au kréyòl depuis n'importe quel clavier. Et le message affiché
quand le sélecteur refuse de s'ouvrir renvoyait à la barre de notifications,
qui n'a plus de ligne « choisir la méthode de saisie » depuis Android 8.

## [10.12.12] - 2026-08-21

### 🔤 « on bèl kréyòl » dans le champ d'essai

L'invite du champ « Tester le clavier » disait « Koumansé maké an bèl kréyòl
isit... ». La formule retenue pour la marque est « on bèl kréyòl », et c'était sa
dernière occurrence dans l'application : les supports du site, les affiches, les
tracts, le triptyque et les visuels du Play Store ont été repris en même temps.
Vérifié sur émulateur Pixel 7 Pro.

## [10.12.11] - 2026-08-20

### 🔤 Le mot d'une suggestion tient à nouveau dans sa puce

La taille du texte des puces est demandée en sp : elle suit donc l'échelle de
police du système, alors que la puce, elle, est en dp et ne bouge pas. Au réglage
« Grande » d'Android, la ligne devenait plus haute que la puce et `TextView` la
rognait à hauteur de sa réserve intérieure. Mesuré sur émulateur à l'échelle 1,3 :
29 px de vide au-dessus du mot contre 16 en dessous, jambages de « j », « q » et
« g » coupés net. Le mot paraissait posé trop bas dans sa puce alors qu'il était
simplement trop grand pour elle.

La police est maintenant ramenée à ce que la hauteur de puce peut afficher en
entier, et seulement quand elle déborde : à l'échelle normale rien ne change, au
delà le mot reste lisible et complet plutôt que tronqué. La hauteur de ligne est
lue sur la police elle-même plutôt que sur un ratio écrit en dur. C'est la règle
déjà appliquée aux touches, dont la police se déduit de la hauteur de touche pour
la même raison. Vérifié en portrait et en paysage, aux échelles 1,0 et 1,3.

### 🏷️ Les étiquettes KR et FR retrouvent le milieu de leur rangée

Le centrage vertical était posé sur les `LayoutParams` de l'étiquette, alors que
la vue occupe déjà toute la hauteur de la rangée : il n'avait rien à décaler et le
texte restait collé en haut, à côté de puces dont le mot, lui, est centré.

## [10.12.10] - 2026-08-19

### 🧪 Le banc d'affichage cesse de mentir sur le paysage

Aucun changement dans le clavier. Cette version corrige l'outillage qui le vérifie,
qui déclarait dix appareils sur dix-huit testés en paysage alors que la capture
analysée était un portrait, aux dimensions exactes de la capture portrait du même
appareil. Trois causes, toutes constatées sur émulateur.

Le banc n'acceptait que `ROTATION_90`, quand le système sert le paysage
indifféremment en 90 ou en 270. La rotation était par ailleurs demandée pendant que
le launcher, verrouillé en portrait, tenait encore l'écran : elle est bien
enregistrée mais le display la refuse, et rien ne la rejoue ensuite, Android ne
réévaluant l'orientation que sur événement. Enfin le verrou lui-même n'est pas
immédiat : `accelerometer_rotation` passe à 0 dans les réglages tandis que le
gestionnaire de fenêtres reste un temps en `USER_ROTATION_FREE`, où la rotation
demandée est simplement ignorée.

Le banc attend désormais la résumption réelle de l'activité, puis le verrou effectif,
avant de demander la rotation en repassant par l'orientation opposée, seule façon de
déclencher une réévaluation. Surtout, il refuse là où il se taisait : l'appareil est
abandonné si la rotation n'est pas obtenue, et la capture jetée si la forme de
l'image dément son nom. La synthèse affiche les captures manquantes au lieu de les
omettre du tableau, un « 0 échec » ne pouvant plus reposer sur une orientation
jamais vue.

### 🌐 La signature discrète redevient mesurable

Peinte depuis la 10.12.9 en blanc très dilué sur le dégradé de la barre d'espace,
« Potomitan™ » était devenue trop pâle pour le seuil de blanc franc du banc, qui ne
relevait plus que les bords de la touche et aurait donc laissé passer une signature
réduite à « … » ou absente. Elle est maintenant mesurée par son écart au fond de la
rangée, et rapportée à la hauteur de touche plutôt qu'à la largeur de la barre
d'espace : ce rapport vaut 1,1 en portrait comme en paysage, quand le précédent
variait du simple au triple selon l'orientation.

Le contrôle a été étalonné contre deux témoins fabriqués depuis une capture réelle :
signature effacée donne 0,00, réduite à « … » donne 0,17, intacte donne 1,06 à 1,14
selon l'appareil, pour un seuil placé à 0,55.

Campagne complète sur les dix-huit appareils testables, portrait et paysage : trente-six
captures, aucun contrôle en échec.

## [10.12.9] - 2026-08-19

### ⏎ La touche entrée retrouve sa taille

Sa flèche était deux fois plus petite que ses voisines : 25 pixels de haut quand la
corbeille en fait 58 et la flèche majuscule 62. Deux causes se cumulaient. Le dessin
n'occupait que la moitié de son cadre, et ce vide était agrandi avec lui ; sa touche
est par ailleurs la plus étroite des trois porteuses d'icône, si bien que sa marge
intérieure lui coûtait double, en largeur comme en hauteur. Le cadre est recadré sur
le trait et la marge réduite : la flèche passe à 62 sur 38 pixels, au même niveau que
le reste de la rangée. Son dessin n'a pas changé.

### 🌐 La signature de la barre d'espace se fait discrète

« Potomitan™ » s'affichait en gras, souligné d'une ombre portée, dans le blanc franc
et à la taille des libellés « 123 » et « ABC ». Ce sont des traitements faits pour des
touches qu'on vise du pouce, appliqués à un mot qui n'appelle aucune action. Il garde
sa place et reste lisible de près, mais passe en graisse normale, sans ombre et à un
tiers de son contraste précédent. L'indice 🌐 du coin, lui, ne bouge pas : il annonce
un appui long, la signature non.

## [10.12.8] - 2026-08-19

### ✍️ Les digraphes proposent enfin des mots

Commencer un mot par un digraphe choisi en appui long, « tj », « dj » ou « ch », ne
donnait aucune suggestion, et l'insérer au milieu d'un mot laissait la barre figée
sur les propositions du préfixe précédent. Le caractère était bien écrit, mais le
moteur de suggestions n'était jamais prévenu qu'il venait s'ajouter au mot en cours.
Taper « t » puis « tj » en appui long propose maintenant tjè, tjwé et tjuit, et un
accent inséré en cours de mot rafraîchit la liste comme une lettre ordinaire.

### ⌨️ Quatre digraphes retirés de l'appui long

« ng », « ny », « gn » et « gy » disparaissent, ce qui laisse les touches « n » et
« g » sans appui long. Ils avaient été retenus sur un comptage de leurs occurrences
n'importe où dans le mot, ce qui ne dit rien de l'utilité d'un raccourci : ces
quatre-là s'écrivent sans hésitation avec les deux touches voisines, contrairement
aux caractères accentués absents du clavier. « ch », « dj » et « tj » restent sous
« c », « d » et « t ».

### 👁️ Les aperçus d'appui long lisibles sur toutes les touches

Ceux des touches « , » et « . » se confondaient avec leur fond orange : leur couleur
était recopiée du libellé de la touche, blanc, alors que du blanc sur cet orange ne
dépasse pas 2:1 de contraste. Elle est désormais choisie en comparant les deux encres
possibles, sombre et claire, contre le fond réel de la touche : l'aperçu passe à
4,8:1, mesuré sur capture d'écran. Les touches lettres, déjà largement lisibles,
gardent exactement leur gris discret.

Leur taille suit par ailleurs la hauteur de la touche au lieu de 8 points figés,
comme les lettres depuis la 10.12.2 : ils en faisaient 24 pixels quand la lettre de
la même touche en fait 49. Et les aperçus de la touche « o » rejoignent le côté
droit, où se trouvent ceux de toutes les autres touches.

## [10.12.7] - 2026-08-19

### ⇧ La touche majuscule reprend sa taille

Sa flèche paraissait plus petite que le reste du clavier, et elle l'était : 36 pixels de haut quand la corbeille en fait 51 et un « b » 54. Elle passait inaperçue tant que les lettres étaient petites, l'écart n'apparaissant qu'une fois celles-ci agrandies. La flèche retrouve le gabarit de ses voisines, dans ses trois états : au repos, en majuscule ponctuelle et en verrouillage.

## [10.12.6] - 2026-08-19

### 🧪 Un banc d'essai pour l'affichage des touches

Rien ne change dans le clavier. Cette version verse dans le dépôt le banc d'essai
monté pour la précédente : il démarre chaque téléphone émulé, y installe le
clavier, l'affiche en portrait puis en paysage et vérifie sur l'image que chaque
touche est à sa place et que chaque libellé est entier. Les deux défauts corrigés
en 10.12.5 avaient échappé à toute vérification automatique jusqu'à ce qu'un œil
humain les remarque ; ils seraient désormais signalés.

## [10.12.5] - 2026-08-19

### 😀 La touche emoji réapparaît

Elle affichait « … » à la place du visage souriant. La taille des caractères ne tenait compte que de la hauteur de la touche depuis la 10.12.2, or un emoji est dessiné dans un carré, bien plus large qu'une lettre : il réclamait plus de place que sa touche n'en offrait, et Android le remplaçait alors par des points de suspension. La taille des libellés tient désormais compte de la largeur de la touche autant que de sa hauteur, ce qui protège aussi les lettres sur les téléphones à écran étroit.

### 💬 Les mots des suggestions bien au milieu de leur puce

Le mot proposé paraissait posé trop bas dans sa puce. Une ligne de texte réserve au-dessus et au-dessous d'elle la place des accents et des jambages les plus extrêmes de la police, que le français n'atteint jamais, et cette réserve est plus épaisse en haut. Elle n'est plus comptée : le mot retrouve le milieu de sa puce, et les lettres celui de leurs touches.

## [10.12.4] - 2026-08-19

### 💬 Les mots proposés se lisent enfin d'un coup d'œil

Le texte des puces de suggestions était le plus petit du clavier, plus petit encore que les lettres des touches, alors que c'est justement ce qu'on lit pour décider d'accepter un mot. Il grandit d'un quart et atteint la taille des lettres du clavier.

Rien ne bouge autour : la puce garde sa taille, les trois propositions tiennent toujours côte à côte, et un mot long reste entier même en paysage, où la rangée est plus basse.

## [10.12.3] - 2026-08-19

### 🔤 La touche « 123 » se réaligne, et les lettres grandissent encore

La touche « 123 » flottait quelques pixels plus bas que ses voisines de la rangée du bas, un défaut discret qui s'était accentué avec les lettres agrandies de la version précédente. En cause, une règle d'affichage d'Android : dans une rangée, les touches s'alignaient sur la ligne d'écriture de leur libellé plutôt que sur leur cadre, si bien qu'un libellé écrit plus petit que les autres, comme « 123 » ou « ABC », se retrouvait poussé vers le bas. Les touches s'alignent maintenant sur leur cadre.

Les lettres gagnent au passage 19 % de hauteur supplémentaire. La marge restante a été mesurée palier par palier sur un Galaxy A15 : au-delà, c'est la largeur des touches qui manque, le « m » venant toucher les aperçus d'appui long affichés dans les coins.

## [10.12.2] - 2026-08-18

### 🔠 Des lettres plus grandes sur les touches

Les lettres n'occupaient guère plus du tiers de la hauteur de leur touche, nettement moins que sur les autres claviers du téléphone : mesurées sur le même écran, celles de Gboard sont presque une fois et demie plus hautes. Elles gagnent 60 % de hauteur et remplissent maintenant leur touche.

Leur taille suit désormais celle de la touche au lieu d'être figée, si bien que le clavier reste lisible en paysage, où les touches sont plus basses, et qu'une police système agrandie ne peut plus faire déborder une lettre de sa touche. Les libellés qui tiennent dans la largeur au caractère près, « 123 », « ABC » et la barre d'espace, gardent leur taille pour ne pas être tronqués. Les aperçus d'appui long dans les coins des touches restent lisibles, et la hauteur du clavier ne change pas.

## [10.12.1] - 2026-08-18

### ⚙️ Les réglages du clavier ont leur propre écran

Ils étaient arrivés la veille dans une carte de l'onglet À Propos, une page de présentation où personne ne cherche un interrupteur. Un engrenage apparaît maintenant en haut à droite de l'application et ouvre un écran « Réglages du clavier », ce qui est la convention sur Android et laisse la place aux réglages à venir : taille des touches, délai de l'appui long, nombre de propositions affichées.

### 🏷️ Les noms des onglets sont enfin lisibles

La barre du haut identifiait ses sept destinations par des emojis seuls. Les noms existaient pourtant dans le code, mais ils étaient rognés hors de la vue : la barre réservait 140 pixels de hauteur, quand l'emoji seul en réclamait 165. On lit de nouveau Démarrage, Kréyòl an mwen, Mots Mêlés, Mots Mélangés, Mo an Karénaj, Guide et À Propos sous leur icône.

Deux corrections de fond expliquent que ça ait pu passer inaperçu si longtemps. La barre était construite à deux endroits différents du code, en copies qui avaient divergé : celle qui s'affichait n'était pas celle que l'on modifiait. Il n'y en a plus qu'une. Et le nom de chaque onglet tient désormais sur deux lignes au besoin, au lieu de déborder.

## [10.11.7] - 2026-08-18

### 🔔 Le clavier était devenu muet sur Samsung, il reprend la main

Signalé sur un Samsung en 10.11.6 : plus aucune vibration ni aucun son à la frappe. La cause vient de la version 10.11.5, qui avait fait obéir le clavier au réglage de retour tactile du téléphone au lieu de décider lui-même. L'intention était bonne, mais elle repose sur une supposition fausse : **sur One UI, « Vibration au toucher » ne gouverne que le clavier Samsung**, et rien n'est proposé pour les claviers installés. Le clavier restait donc silencieux, sans aucun moyen de le rallumer.

Le clavier reprend donc la main sur son retour de frappe, comme le font Gboard et SwiftKey, qui ont eux aussi leurs propres interrupteurs plutôt que de dépendre du réglage générique du téléphone. Vibration et son repartent, quel que soit l'état des réglages système.

### ⚙️ Deux interrupteurs dans l'application

Puisque le clavier ne suit plus le téléphone, il doit offrir lui-même de quoi le faire taire. L'onglet À Propos accueille une carte **Réglages du clavier** avec deux interrupteurs, actifs par défaut :

- **Vibration à la frappe**
- **Son de frappe**

Le choix s'applique dès le retour dans un champ de saisie, sans redémarrer le téléphone. Sans ces interrupteurs, forcer la vibration redeviendrait un blocage sans issue pour une hypersensibilité sensorielle, ou quand la vibration perturbe le geste d'une main qui tremble.

## [10.11.6] - 2026-08-17

### 📳 Le clavier se sent et s'entend comme les autres claviers

Le retour de frappe était incomplet et incohérent. Les touches vibraient, la barre d'espace aussi, mais les propositions et les emojis non. Côté son, personne n'en avait jamais écrit une ligne : le clavier laissait Android jouer son clic de bouton générique, celui d'une case à cocher ou d'un menu, et la barre d'espace restait carrément muette. La touche la plus frappée du clavier ne faisait aucun bruit.

Tout ce qui écrit du texte donne maintenant les deux retours, vibration et son : les lettres, la ponctuation, la barre d'espace, la suppression, l'entrée, les propositions de mots, les caractères accentués de la fenêtre d'appui long, les emojis, et la puce de partage.

Le son est celui du clavier et non celui d'un bouton. Android en embarque quatre, et le clavier choisit désormais le bon : un son de frappe pour les lettres, un autre pour la barre d'espace, un autre pour la suppression, un autre pour la touche entrée. C'est ce que fait n'importe quel clavier soigné, et c'est ce qui rend la frappe reconnaissable à l'oreille sans regarder l'écran.

Un détail de synchronisation : sur les touches, vibration et son partent à l'instant où le doigt se pose, comme partout ailleurs. Sur les propositions, ils partent au moment où le mot est réellement écrit. La différence est voulue : une proposition touchée par erreur puis abandonnée en glissant à côté n'écrit rien, et ne doit donc rien confirmer.

Vibration et son suivent chacun le réglage correspondant du téléphone, retour tactile d'un côté, sons tactiles de l'autre. Qui n'en veut pas les coupe une fois, dans les réglages Android, pour toutes les applications.

### 📝 Une correction dans la fiche destinée aux ergothérapeutes

La page du site affirmait que glisser en dehors d'une proposition avant de relâcher annule toujours la sélection. La mesure montre que c'est vrai en restant sur le clavier, vers une proposition voisine, vers l'espace qui les sépare ou vers les touches, mais faux vers le haut hors du clavier, où le doigt quitte la zone surveillée et la sélection part quand même. La page le dit maintenant précisément, et signale aussi que la vibration peut être coupée depuis la 10.11.5, ce qu'elle donnait encore comme une limite du clavier.

## [10.11.5] - 2026-08-17

### ♿ La vibration du clavier obéit enfin au téléphone

Le clavier vibrait à chaque appui même quand le retour tactile était désactivé dans les réglages du téléphone : il posait un drapeau qui demande explicitement au système de passer outre le choix de l'utilisateur. Vibrations coupées partout ailleurs, celle du clavier restait, et rien ne permettait de l'éteindre. Pour une hypersensibilité sensorielle, ou quand la vibration perturbe le geste d'une main qui tremble, c'était un blocage sans issue.

Ce drapeau est retiré. Le clavier se comporte désormais comme n'importe quelle autre application : il suit le réglage de retour tactile du téléphone, qui se trouve dans les réglages Android, à la rubrique des sons et des vibrations.

Aucun réglage n'est ajouté dans l'application, volontairement. Le téléphone en a déjà un, il fonctionne dans les deux sens, et un clavier n'a pas à dupliquer l'interrupteur du système.

## [10.11.4] - 2026-08-17

### ✍️ L'apostrophe retrouve une touche

Écrire `l'`, `d'`, `j'` ou `qu'` demandait un appui long sur la virgule, et il fallait le savoir : l'apostrophe était en troisième position de cette liste, alors que seuls les deux premiers caractères s'affichent en indice de coin. Rien ne l'annonçait donc sur le clavier.

Elle redevient une touche visible, en troisième rangée, juste après le `n`. C'est la rangée qui pouvait le mieux l'accueillir : ses touches sont les plus larges du clavier et elle ne porte que 17,5 % de la frappe. La place est prise sur les touches majuscule et suppression, dont la largeur passe de 8,9 à 7,2 mm. Elles restent les plus larges de leur rangée, ce qui compte puisqu'elles sont aux deux extrémités, là où la visée est la plus mauvaise. Les lettres de la rangée ne perdent qu'un tiers de millimètre, la barre d'espace et les autres rangées ne bougent pas, et l'apostrophe hérite d'une cible de 5,7 mm, contre 4,6 mm si elle avait rejoint la rangée du bas.

La version 9.1.0 avait retiré cette touche au motif qu'aucun mot de `creole_dict.json` ne contient d'apostrophe. Le constat était exact, mais il ne mesurait pas le bon corpus : l'orthographe GEREC écrit l'élision avec un trait d'union, `ba-w`, `an-nou`, donc un dictionnaire de mots créoles ne peut pas en contenir. En français, que ce clavier écrit aussi et pour lequel il propose des suggestions, l'apostrophe vaut 5,5 caractères pour mille, soit autant que la virgule et le point, qui ont tous deux une touche dédiée.

Elle reste accessible sous l'appui long de la virgule, comme `é` et `è` restent sous celui de `e` malgré leur touche dédiée.

### 🧪 Le simulateur du site rattrape trois écarts

Il montrait encore l'apostrophe dans la rangée du bas, là où l'application a une touche emoji depuis la version 9.1.0, et lui gardait un appui long vers les guillemets que l'application n'a plus. Sa disposition, ses poids de touches et ses appuis longs suivent maintenant ceux du clavier livré. Le panneau emoji reste la seule chose qu'il n'imite pas.

## [10.11.3] - 2026-08-17

### ⌨️ Les lettres les plus tapées avaient les touches les plus étroites

La rangée du haut portait onze touches, `a z e r t y u i o ò p`, là où les autres en ont dix. Ses touches étaient donc les plus étroites du clavier, 4,6 mm contre 5,1 mm en deuxième rangée et 5,9 mm en troisième, alors que c'est de loin la plus sollicitée : les lettres qu'elle porte représentent près de la moitié de la frappe en créole, contre 23 % pour la deuxième rangée et 17 % pour la troisième. La largeur des touches était donc inversement proportionnelle à leur usage.

La touche `ò` quitte cette rangée, qui repasse à dix touches de 5,1 mm, désormais identiques à celles de la rangée du dessous. Les lettres les plus fréquentes du créole, `a`, `o`, `i`, `t`, `u`, `p`, gagnent chacune un tiers de millimètre.

`ò` reste accessible en appui long sur `o`, où il figure en première position juste après la lettre sans accent, et l'indice affiché dans le coin de la touche continue de l'annoncer. Le caractère ne pèse que 1,24 % des lettres écrites, contre 8 % pour le `o` seul : l'échange se fait donc contre un appui long sur un caractère rare, déjà atteignable autrement. C'est le même raisonnement que pour `é` et `è`, qui gardent une touche dédiée en quatrième rangée, où elle ne coûte aucune largeur aux lettres.

Un mot contenant `ò` reste par ailleurs trouvable sans taper l'accent du tout, les suggestions étant tolérantes aux accents : taper `bon` propose déjà `bòn`.

Le simulateur du site suit la même disposition, pour continuer de montrer le clavier réellement livré.

## [10.11.2] - 2026-08-17

### ♿ Un mot français se validait à la place du mot créole visé

Les deux rangées de suggestions n'étaient séparées que de 0,58 mm. Un appui un demi-millimètre trop bas validait « Partie » à la place de « Paré ». Comme l'imprécision d'un doigt est d'abord verticale, c'était l'erreur la plus facile à commettre, et la plus coûteuse à réparer : il faut effacer le mot, le retaper, puis le revalider.

L'intervalle vide autour de chaque puce passe à 1,85 mm dans toutes les directions, entre deux puces voisines comme entre les deux rangées. Cet espace n'appartient à aucun bouton, donc un appui qui y tombe ne fait rien, et c'est voulu : un appui perdu coûte un geste, un appui sur le mot voisin en coûte cinq.

La place est prise sur la hauteur des puces, qui passe de 6,1 à 5,4 mm, et non sur celle des rangées. Le clavier occupe donc exactement la même hauteur qu'avant et aucune touche ne bouge. En paysage, où les suggestions tiennent déjà sur une seule rangée, la géométrie ne change pas du tout : il n'y a rien à séparer et pas de hauteur à dépenser en vide.

Les mesures ont été relevées au pixel dans des captures d'écran, avant et après, sur un Pixel 5 à 440 dpi. Elles ont démenti l'hypothèse de départ, qui visait la largeur des puces : celle-ci est déjà de 14 mm pour tous les mots, y compris les plus courts. Le défaut était ailleurs.

### 📄 Une fiche pour les ergothérapeutes

Le site accueille une fiche destinée aux professionnels qui accompagnent des personnes gênées dans le geste de la main, après un AVC notamment : ce que le clavier fait réellement gagner en nombre d'appuis, ce qu'il ne fait pas, ses limites connues, et un protocole pour compter les appuis en séance sans rien installer. Elle ne revendique aucun effet thérapeutique.

Les six autres chantiers d'accessibilité identifiés pour ce profil, avec pour chacun le point du code concerné et la façon de le vérifier, sont recensés dans `ACCESSIBILITE.md`, à la racine du dépôt.

## [10.11.1] - 2026-08-15

### 📐 Le clavier prenait tout l'écran en paysage

Écran couché, le clavier occupait 87 % de la hauteur et ne laissait que la moitié du champ de saisie visible, sans jamais se resserrer : il gardait la mise en page du portrait, où la même hauteur ne représente que 40 % de l'écran.

Trois choses changent, en paysage seulement : les touches passent de 48 à 32 dp, la marge du bloc de touches de 8 à 4 dp, et les suggestions tiennent sur une seule rangée au lieu de deux. Les suggestions françaises rejoignent alors la rangée kreyòl, où l'étiquette de langue et la couleur des puces les distinguent déjà, la rangée défilant si les mots sont nombreux. Le clavier retombe à 58 % de l'écran, l'application récupère plus de trois fois la place qu'elle avait, et le portrait ne bouge pas d'un pixel.

Les 32 dp sont le seuil de visée en dessous duquel le clavier refuse déjà de descendre quand il manque de place : couché, chaque touche est deux fois plus large qu'en portrait, ce qui compense sa hauteur réduite, et les lettres comme les indices d'appui long tiennent encore entièrement.

Les icônes des touches majuscule, retour et entrée suivent maintenant la hauteur de leur touche : leur marge intérieure était écrite en dur, ce qui les rapetissait de moitié dès que la touche se réduisait.

### 🐛 Une bande vide séparait le clavier du bas de l'écran

Le clavier réservait lui-même la hauteur de la barre de navigation, alors que le système la déduit déjà de la fenêtre de saisie. La place était donc payée deux fois et laissait une bande vide sous la dernière rangée, très visible en paysage. Cette réserve est maintenant mesurée : le clavier compare sa position réelle à celle de la barre et ne se décale que de ce qui est effectivement recouvert, c'est-à-dire rien dans le cas courant.

Cette mesure remplace une estimation tirée du mode de navigation et de la hauteur système déclarée, qu'il fallait déjà plafonner parce que certaines ROM annoncent une valeur aberrante et poussaient le clavier hors de l'écran. Les touches y gagnent 5 dp en paysage, la place rendue leur revenant.

### 🐛 La rangée du bas était coupée en deux en paysage

Le clavier réclamait 332 dp de hauteur là où la fenêtre de saisie n'en accorde que 288 en paysage sur un écran 1080x2400 : la rangée des touches 123, virgule, é, tiret, espace, è, point, emoji et entrée était tranchée à mi-hauteur, et ces touches devenaient inatteignables. La hauteur des touches se calcule désormais à partir de la place réellement disponible, plafonnée à sa valeur habituelle de 48 dp. Le portrait ne change donc pas d'un pixel ; en paysage les touches passent à 35 dp et le clavier tient entier.

Le calcul retranche aussi la barre d'état, sous laquelle la fenêtre de saisie commence, ce que la hauteur d'écran annoncée par le système ne fait pas.

### 🐛 Les jambages des lettres étaient rognés sur les touches basses

Sur une touche réduite, les lettres q, g, j, p et y perdaient leur jambage. Les touches héritaient de 30 px de marge intérieure sur chaque bord, venue du style de bouton d'origine, ce qui ne laissait que 45 px à une ligne de texte qui en fait 57. Cette marge n'a aucun rôle ici, l'apparence des touches venant entièrement du dégradé dessiné par l'application.

### 🐛 La touche « 123 » n'affichait que « 12 »

Le même héritage, horizontalement cette fois, tronquait le libellé de la touche de mode en portrait. Il s'affiche à nouveau en entier, sur une seule ligne, à la taille déjà utilisée pour la barre d'espace.

## [10.9.3] - 2026-08-15

### ⇧ La touche majuscule montre enfin où elle en est

Les trois états de la touche ⇧ (minuscules, majuscule pour une lettre, verrouillage) étaient prévus mais ne se voyaient pas. Le fond gris destiné à signaler la majuscule enclenchée n'était appliqué qu'aux touches de texte : la touche ⇧ portant une icône, elle n'y passait jamais et restait blanche dans les trois cas.

Le fond suit désormais l'état, et les icônes ont été refaites autour d'une silhouette unique qui ne change plus de forme d'un état à l'autre : flèche creuse au repos, pleine pour une majuscule, pleine avec une barre de verrouillage pour le mode verrouillé. L'état repos empruntait jusqu'ici le chevron vers le bas, le même dessin que le bouton système « masquer le clavier » affiché au même instant dans la barre de navigation.

### 🐛 La barre d'espace passait en majuscules avec le reste du clavier

Activer la majuscule transformait le libellé de la barre d'espace en « POTOMITAN™ », et la touche de retour à l'alphabétique en « abc » puis « ABC » au gré du shift. Le clavier retrouvait la touche derrière chaque bouton en relisant son libellé affiché, ce qui ne fonctionne que pour les touches dont le libellé est la touche elle-même. Chaque bouton porte maintenant sa touche d'origine, et ces libellés restent stables.

## [10.9.2] - 2026-08-08

### 🔖 Un mot-dièse commun à tous les partages

Les messages envoyés depuis l'application partaient chacun de leur côté, sans rien qui permette de les retrouver entre eux sur les réseaux. Ils se terminent désormais tous par **#KlavyéKréyòl** : la carte de niveau, la carte d'activation, le partage de l'application depuis l'onglet Informations, et la puce « Envoyer un mot à un ami » du clavier.

Le mot-dièse est posé en dernier, après une ligne vide. Collé juste derrière le lien Play Store, certaines applications l'aspireraient dans l'adresse.

Le texte à partager de la page Ambassade du site le porte aussi.

## [10.9.1] - 2026-08-08

### ⚡ La frappe ne paie plus l'écriture du dictionnaire

Chaque mot validé déclenchait l'enregistrement complet du dictionnaire d'usage, de façon synchrone, sur le thread qui dessine le clavier. Mesuré sur émulateur : **116 à 500 ms de blocage à chaque espace tapé**, de quoi faire sauter des images en pleine phrase. Le verrou pris pendant ce temps bloquait en plus les lectures du moteur de suggestions.

L'écriture passe sur un thread dédié. Les demandes qui arrivent pendant qu'une écriture est en cours sont fusionnées, si bien que rien n'est différé ni perdu : un mot tapé juste avant que le clavier soit tué se retrouve bien enregistré. Le fichier n'est plus indenté non plus, ce qui le fait passer de 318 à 218 Ko.

Après correctif, les mêmes huit mots coûtent **0 à 16 ms**.

### 🐛 La pastille d'onglet ne s'affichait qu'au démarrage à froid

Un palier franchi pendant que l'application dormait dans la pile des tâches ne se voyait pas au retour : la pastille orange n'était lue qu'à la construction de la barre d'onglets. Il fallait fermer puis rouvrir l'application pour la découvrir. Quand les notifications sont refusées, cette pastille est le seul signal existant, et le franchissement passait donc totalement inaperçu.

### 🐛 La pastille d'icône restait après avoir été vue

Ouvrir l'application depuis l'écran d'accueil, voir ses félicitations et sa carte n'effaçait pas la notification : la pastille restait sur l'icône jusqu'à ce qu'on pense à balayer la notification. Elle s'éteint désormais dès que la progression a été consultée, quel que soit le chemin emprunté pour y arriver.

## [10.9.0] - 2026-08-08

### 📤 « Plus tard » ne fait plus perdre sa carte

La carte de niveau à partager n'était atteignable que par le bouton de la boîte de félicitations. Répondre « Plus tard » la fermait définitivement : le palier était déjà enregistré comme vu, la boîte ne revenait jamais, et plus rien dans l'application ne permettait de retrouver sa carte.

Un bouton **« 📤 Partager ma carte de niveau »** est désormais posé en permanence dans l'onglet « Kréyòl an mwen ». La carte du niveau en cours se reconstruit à la demande, autant de fois qu'on veut. « Plus tard » redevient un report.

*Une des astuces de l'onglet Démarrage promettait déjà de pouvoir partager sa carte depuis cet onglet. Elle dit maintenant vrai.*

### 🔔 L'onglet signale un niveau non vu

Les félicitations ne se déclenchent qu'en affichant l'onglet « Kréyòl an mwen ». Qui ouvrait l'application et restait sur Démarrage n'apprenait donc rien de sa progression.

Une **pastille orange** se pose sur l'onglet tant qu'un palier franchi n'a pas été consulté, et s'éteint dès qu'on y est passé. Elle apparaît même si les notifications ont été refusées : c'est alors le seul signal restant.

## [10.8.0] - 2026-08-08

### 🌱 Le passage de niveau se signale enfin

Franchir un palier de vocabulaire ne se voyait que dans l'onglet « Kréyòl an mwen ». Autrement dit, il fallait déjà revenir dans l'application pour apprendre qu'on avait progressé, et celui qui écrit en kréyòl tous les jours sans jamais rouvrir l'application ne l'apprenait jamais.

Le franchissement est désormais repéré au moment où les mots sont tapés, et annoncé par une **pastille sur l'icône de l'application**.

Rien ne vient déranger la frappe. La notification est silencieuse, sans son, sans vibration et sans bandeau par-dessus la conversation en cours : Android la range parmi les notifications discrètes. On la découvre en revenant à son écran d'accueil, au moment qu'on choisit. Un appui ouvre directement « Kréyòl an mwen », où la carte de niveau à partager attend.

*Si la permission de notification est refusée, le clavier se comporte exactement comme avant.*

### 🐛 L'onglet demandé s'ouvrait toujours sur Démarrage

Un défaut plus ancien, trouvé en chemin : l'onglet actif n'était jamais restauré. Après une rotation de l'écran ou une recréation de l'application, on retombait systématiquement sur Démarrage, quel que soit l'onglet quitté.

## [10.7.2] - 2026-08-07

### 🗣️ Un proverbe pour refermer l'appel au partage

La carte « Ba kréyòl la lanmou'w ! » de l'onglet Informations invite à faire connaître le clavier autour de soi, puis s'arrêtait sur ses deux boutons.

Un proverbe la conclut désormais, en italique sous « Noter l'application » :

> *« Dé mòn pa ka jwenn, mé dé moun toujou ka jwenn »*

Deux mornes ne se rencontrent pas, deux personnes toujours. Il dit en une ligne, et en kréyòl, ce que le paragraphe au-dessus explique.

## [10.7.1] - 2026-08-07

### 🔤 Karukéra reprend son accent

Le nom caraïbe de la Guadeloupe s'écrit **Karukéra**, avec un accent aigu. L'application l'écrivait sans, partout où elle se nommait elle-même.

La graphie est corrigée dans les textes que l'utilisateur lit :

- Les libellés affichés par Android, donc le nom du clavier dans la liste des claviers, le sous-type de saisie et le correcteur orthographique
- Les onglets et l'onboarding de l'application, l'aide du correcteur, l'écran À propos, la mention de copyright, les messages de partage et le pied de page de la carte de niveau

Les deux allaient de pair : les étapes d'activation demandent de repérer le clavier sous le nom exact qu'Android affiche.

*Le site, la fiche Play Store et les supports imprimés seront repris séparément.*

## [10.7.0] - 2026-08-07

### 💡 L'astuce du jour devient l'astuce de la semaine

La carte d'astuce de l'onglet Démarrage affichait la même phrase depuis toujours : l'appui long pour les accents. Utile une fois, elle occupait ensuite la place sans plus rien apprendre à personne.

Elle change désormais chaque lundi et puise dans **36 astuces** couvrant tout ce que le clavier sait faire sans le dire :

- **Saisie** : les trois états de la touche majuscule, les digraphes du kréyòl cachés sous ch, dj, tj, ng et ny, les accents annoncés dans le coin des touches, la touche emoji et ses tons de peau, la touche Entrée qui s'adapte au champ
- **Suggestions** : taper sans accent fonctionne, une lettre oubliée n'empêche rien, le clavier propose la suite d'après les deux mots précédents, et les mots que vous employez le plus remontent d'eux-mêmes
- **Progression et jeux** : le mot du jour, la part du dictionnaire déjà employée, les règles de Mo an Karénaj, le partage de la carte de niveau
- **Correcteur** : où le choisir dans les réglages Android, et pourquoi il peut rester muet jusqu'au redémarrage après une mise à jour

Les astuces se suivent dans l'ordre plutôt qu'au hasard : aucune ne se répète avant que les 35 autres soient passées.

*Chaque astuce décrit une fonctionnalité réellement présente dans l'application. La liste et la source de chacune sont documentées dans `ASTUCES.md`.*

## [10.6.1] - 2026-08-07

### 🔤 Activer le correcteur devient suivable jusqu'au bout

L'étape « Corriger l'orthographe partout » de l'onglet Démarrage ouvrait déjà le bon écran système, mais s'arrêtait à son seuil. Trois obstacles y attendaient l'utilisateur, chacun suffisant pour le faire abandonner :

- La sélection se fait dans un sous-menu, « Correcteur par défaut », que rien ne signale à l'arrivée. Le texte demandait de choisir « Correcteur Kréyòl Karukera », un libellé qui n'apparaît pas d'emblée à l'écran
- Android intercale un avertissement indiquant qu'un correcteur peut recueillir l'ensemble du texte saisi, mots de passe et numéros de carte compris. Surgissant sans prévenir, il fait annuler
- Rien ne répondait à l'inquiétude que cet avertissement soulève

Les trois étapes sont maintenant énumérées dans l'ordre où elles se présentent, l'avertissement est annoncé à l'avance comme une étape normale, et une phrase précise ce que fait notre correcteur : il compare les mots au dictionnaire kréyòl livré avec l'application, n'en conserve aucun et n'envoie rien.

L'étape reste optionnelle.

*Rappel du chemin, corrigé au passage dans toute la documentation : Réglages › Système › **Clavier** › Correcteur orthographique. Il ne se trouve pas sous « Langues ».*

## [10.6.0] - 2026-08-07

### 🗑️ Retrait de « le clavier retient vos mots »

- La fonction introduite en 10.5.0 apprenait les mots absents du corpus pour les suggérer. Elle est **retirée** : un clavier qui conserve des mots tapés par l'utilisateur, si encadré soit-il, reste un clavier qui conserve ce qu'on écrit. Ce n'est pas ce qu'on attend d'un clavier, et le doute que cela installe coûte plus que le service rendu
- **Ce qui avait été écrit est effacé** : les appareils passés par la 10.5.0 voient leur fichier de mots retenus supprimé au premier démarrage du clavier. Retirer le code ne suffisait pas
- Le clavier ne conserve donc plus aucun mot qui ne soit déjà dans le dictionnaire kréyòl livré avec l'application

### 🔒 Ce qui est conservé de la 10.5.0

- Le garde-fou sur les champs sensibles reste en place, et protège désormais les statistiques de vocabulaire : rien de ce qui est tapé dans un champ de mot de passe (masqué, affiché en clair, formulaire web, code numérique) ou dans un champ déclaré non mémorisable n'est compté. Ces champs n'étaient auparavant pas distingués des autres

## [10.5.0] - 2026-08-07

### 📔 Le clavier retient vos mots

- **Constat** : un mot absent du corpus littéraire (prénom, toponyme, nom de famille, néologisme) n'était jamais suggéré et restait souligné comme faute, quel que soit le nombre de fois qu'on le tapait. La fonction prévue pour cela existait dans le moteur mais n'était appelée nulle part, et ne conservait rien puisque le dictionnaire ne vit qu'en mémoire
- **Un mot est retenu après deux emplois** : une faute de frappe se répète rarement à l'identique, alors qu'un prénom revient vite. Une fois retenu, il est suggéré immédiatement et cesse d'être souligné, sans attendre le redémarrage du clavier
- Sa fréquence reste plafonnée bien en dessous du vocabulaire courant du kréyòl : c'est l'usage réel qui le fait remonter, grâce au classement personnel introduit en 10.4.0

### 🔒 Ce que le clavier n'apprend jamais

Un dictionnaire qui apprend tout seul ne doit jamais voir passer un mot de passe. Les garde-fous, dans l'esprit de ceux qui protégeaient déjà les statistiques de vocabulaire :

- **Rien n'est retenu d'un champ sensible** : mots de passe sous toutes leurs formes (texte masqué, texte affiché en clair, formulaire web, code numérique) et champs que l'application déclare non mémorisables. La vérification a lieu avant toute écriture
- **Rien contenant un chiffre**, aucune adresse e-mail, aucune URL, aucun mot de moins de trois lettres
- **Les mots en attente du seuil ne sont comptés qu'en mémoire** : une chaîne tapée une seule fois n'est jamais écrite sur le disque
- Tout reste dans l'espace privé de l'application, invisible aux autres applications, et rien ne quitte l'appareil

*Note : l'effacement des mots retenus est implémenté mais pas encore accessible depuis l'interface. Il le sera avec l'écran de réglages du clavier.*

## [10.4.2] - 2026-08-07

### 🧹 Dette technique

Aucun changement visible à l'usage : nettoyage interne et remise en accord de la documentation avec le code.

- **1577 lignes de code mort supprimées** : `KreyolInputMethodService.kt` (l'ancien service monolithique) et `TestInputMethodService.kt`. Ni l'un ni l'autre n'était déclaré au manifeste, donc ni l'un ni l'autre ne pouvait être instancié, mais tous deux étaient compilés dans l'APK. Ils laissaient surtout croire que des fonctionnalités existaient alors qu'elles étaient inertes : `onUpdateSelection()` n'était présent que dans le service legacy pendant que le service actif en manquait, ce qui a retardé le diagnostic du correctif de la 10.4.0
- **Filtre CI corrigé** : `build-apk.yml` filtrait sur `Dictionnaries/**`, dossier qui n'existe pas. Une modification du pipeline dictionnaire ou du corpus ne déclenchait donc aucun build
- **Documentation** : `CLAUDE.md` annonçait 1867 mots pour 5296 et `targetSdk` 35 pour 36, décrivait `CreoleDictionaryWithUsage` comme un « Kotlin actor » alors que c'est une classe ordinaire, et présentait `clavier_creole/` comme inerte alors que le pipeline y lit et y écrit. `README.md` présentait le correcteur orthographique comme un « placeholder à implémenter » alors qu'il fonctionne depuis la 10.4.1, et renvoyait à deux scripts inexistants. `BRANDING.md`, `NGRAMS.md` et `android_keyboard/README.md` désignaient tous le fichier supprimé comme le service principal
- Documentation ajoutée sur les deux pièges qui désactivent silencieusement le correcteur orthographique, et sur le repli local silencieux du pipeline dictionnaire en l'absence de `HF_TOKEN`

## [10.4.1] - 2026-08-07

### 🐛 Les mots kréyòl étaient soulignés en rouge partout

- **Constat** : dans toutes les applications, le correcteur du système marquait les mots kréyòl comme des fautes, alors que `KreyolSpellCheckerService` existe depuis longtemps et sait les reconnaître
- **Deux causes indépendantes**, chacune suffisante à neutraliser le service à elle seule :
  - Le service ne déclarait qu'un sous-type de locale `ht` (créole haïtien), que ne porte jamais un téléphone en Guadeloupe. Android choisit un correcteur en cherchant un sous-type correspondant à la locale du champ : faute de correspondance, la session n'était jamais créée. Le service était bien sélectionnable dans les réglages, mais jamais appelé. `fr` et `gcf` (code ISO du kréyòl guadeloupéen) sont désormais déclarés
  - Les réponses ne reportaient pas le couple (cookie, séquence) de la demande, sans lequel l'application ne peut pas rattacher un verdict au mot analysé. Une fois la session créée, les fautes étaient bien détectées mais rien n'était souligné
- **Réserve volontaire** : déclarer `fr` substitue notre correcteur à celui du système sur tout le texte français, alors que notre couverture du français est mince (662 mots contre 5296 en kréyòl). Un mot inconnu n'est donc signalé que si une correction plausible existe. Un mot français absent de notre dictionnaire n'a aucun voisin kréyòl proche et passe sans être marqué, tandis qu'une faute de frappe kréyòl reste détectée et corrigée
- **Vérifié sur émulateur** en locale `fr-FR` : « Bonjou », « an » et « ka » ne sont plus soulignés, « bonjuo » l'est et propose « bonjou »
- **À noter** : Android n'autorise qu'un seul correcteur orthographique et aucune application ne peut se l'attribuer. Il reste à sélectionner Klavyé Kréyòl dans Réglages › Système › Clavier › Correcteur orthographique

## [10.4.0] - 2026-08-07

### 🎯 Les suggestions suivent enfin le curseur

- **Constat** : revenir éditer un mot déjà écrit ne produisait plus aucune suggestion. Effacer l'espace après un mot, taper au milieu d'un mot, ou simplement déplacer le curseur puis continuer à écrire : dans tous ces cas la barre restait vide, ou proposait des mots pour le préfixe précédent
- **Cause** : `onUpdateSelection()` n'existait que dans l'ancien service `KreyolInputMethodService`. Le service actif ne le surchargeait pas, donc le mot suivi par `InputProcessor` n'était alimenté que par les frappes et divergeait silencieusement du texte réel dès que le curseur bougeait autrement
- **Corrigé** : le mot est relu depuis l'`InputConnection` à chaque déplacement signalé. Quand la valeur relue est celle attendue (le cas de très loin le plus fréquent, nos propres modifications de texte déclenchant aussi ce rappel), rien n'est touché : la frappe normale reste inchangée, ce qui a été vérifié compteur en main
- Choisir une suggestion en plein milieu d'un mot retire aussi la fin du mot, au lieu d'insérer la suggestion devant le reliquat
- **Au passage** : une majuscule accentuée (« É » en début de phrase, fréquent en kréyòl) était traitée comme un séparateur et coupait le mot en cours

### 📊 Le clavier apprend votre vocabulaire

- Les compteurs d'utilisation alimentés par les statistiques de vocabulaire ne servaient qu'à l'affichage. Ils entrent désormais dans le classement des suggestions : le mot que vous employez réellement remonte, même s'il est moins courant dans la littérature créole
- Tout reste sur l'appareil, rien n'est envoyé nulle part, et seuls les mots déjà présents au dictionnaire sont comptés
- Il faut une quinzaine d'emplois pour qu'un mot dépasse un concurrent nettement plus fréquent, et le bonus est plafonné : une correction orthographique reste toujours prioritaire, et les mots les plus courants du kréyòl ne sont jamais délogés

### 🧠 Prédictions contextuelles sur deux mots

- Le modèle ne prédisait la suite qu'à partir du dernier mot saisi, alors que le pipeline calculait déjà les trigrammes avant de les jeter
- Le contexte porte maintenant sur les deux derniers mots, avec repli sur un seul quand la paire est inconnue. Après « an ka », le clavier propose kwè, vwè, travay, là où « ka » seul donnait fè, di, pran
- 4251 contextes à deux mots ajoutés, pour 62 Ko compressés dans l'APK

### 🐛 Fréquences du dictionnaire faussées par le pipeline

- **Constat** : `creer_dictionnaire()` ajoutait le comptage du corpus à la fréquence déjà stockée, laquelle provenait déjà d'un passage sur ce même corpus. Chaque exécution gonflait donc le dictionnaire d'un corpus supplémentaire, et les fréquences mesuraient le nombre de lancements du script plutôt que le kréyòl écrit. Le facteur d'inflation mesuré était d'environ douze
- La CI lançant le pipeline à chaque build sans committer le résultat, la version publiée et le dépôt divergeaient d'un cran à chaque fois
- **Corrigé** : le comptage remplace la valeur stockée, et deux exécutions de suite donnent le même dictionnaire. Les mots absents du corpus restent conservés, leur fréquence ramenée à l'échelle courante

### 📖 Corpus rafraîchi

- `Textes_kreyol.json` datait du 30 avril et ne contenait plus que 504 lignes pour 20 251 caractères, contre 2519 textes et 191 732 caractères dans le dataset Hugging Face. Le repli local du pipeline aurait reconstruit le dictionnaire sur un corpus neuf fois plus petit en cas d'échec du téléchargement
- Les textes présents en local et absents du dataset sont conservés

## [10.3.2] - 2026-08-06

### 🐛 Clavier partiellement vierge signalé sur Honor 200 (Android 16)

- **Constat** : un utilisateur sur Honor HONOR 200 (SDK 36) a signalé un clavier cassé à l'activation : les 2 premières rangées de lettres sans texte, la 3ème rangée correcte, et toutes les touches colorées (fonction) sans aucun affichage
- **Non reproduit** sur l'émulateur `honor_x9c_test` (même profil Honor 200, Android 16) malgré des tests systématiques : rendu par défaut, mode sombre forcé, police système à 1.3x et densité d'affichage à 560dpi affichent tous le clavier correctement (seul un défaut cosmétique préexistant et sans rapport, le label « 123 » tronqué en « 12 » à très forte densité, a été observé)
- **Cause probable** : `KeyboardLayoutManager.applyGuadeloupeStyleToView()` applique un `Paint.setShadowLayer()` (ombre décorative) à chacune des ~38 touches du clavier simultanément. Cette combinaison (rendu accéléré matériellement + `setShadowLayer()` sur de nombreuses vues) est une source connue de texte/icônes invisibles selon le driver GPU du SoC, un phénomène propre à certains modèles/OEM et impossible à reproduire sur un émulateur en rendu logiciel (swiftshader) comme celui utilisé ici
- **Corrigé** : `view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)` forcé sur chaque touche juste avant l'application de l'ombre, pour écarter cette classe de bug sans changer le rendu visuel (identique en émulateur avant/après). Coût de performance négligeable : ces vues ne se redessinent qu'au changement d'état (appui, bascule majuscule, changement de mode), jamais en boucle
- **Vérifié sur émulateur** (`honor_x9c_test`, Android 16) : rendu du clavier strictement identique au correctif près (ombre, couleurs, icônes), saisie fonctionnelle. Correctif préventif non confirmé sur un Honor 200 physique, faute d'accès à l'appareil du rapporteur

## [10.3.1] - 2026-08-05

### 🐛 Le scroll se bloquait quand le clavier apparaissait

- Sur l'onglet Réglages (hébergeant tous les jeux et le Guide), le clavier virtuel déclenchait un mode `adjustPan` : la fenêtre entière était translatée pour garder le champ de saisie visible, au lieu d'être redimensionnée — ce qui neutralisait le scroll de la `ScrollView` pendant la saisie, en particulier sur l'onglet Mo an Karénaj
- Corrigé : passage de `SettingsActivity` en `adjustResize|stateHidden` dans `AndroidManifest.xml`, vérifié sur émulateur (le contenu défile de nouveau normalement, clavier ouvert)

## [10.3.0] - 2026-08-04

### ✨ Nouveau jeu : Mo an Karénaj (Wordle créole)

- Ajout d'un cinquième jeu dans l'onglet de démarrage : deviner un mot kréyòl de 5 lettres en 6 essais, sur le principe du Wordle, avec retour couleur (vert = bien placé, orange = présent ailleurs, gris = absent)
- Les mots à deviner et la validation des propositions s'appuient directement sur `creole_dict.json`, comme les autres jeux (Mots Mêlés, Mots Mélangés) — aucune source de données séparée
- Grille de résultat construite en `LinearLayout` plutôt qu'en `GridView` : une `GridView` imbriquée dans le `ScrollView` de l'onglet interceptait systématiquement le geste de défilement vertical (conflit connu entre `AbsListView` et `ScrollView` sous Android), rendant la page injouable une fois le clavier ouvert
- Message d'erreur (mot trop court / hors dictionnaire) ancré en haut de l'écran via `Snackbar` plutôt qu'un `Toast` standard, pour ne pas être masqué par le clavier virtuel
- Règles du jeu affichées sous la zone de jeu

## [10.2.9] - 2026-08-04

### ✨ Nouvelle section « Installation et activation » dans le Guide

- L'onglet Guide ne couvrait jusqu'ici que l'usage du clavier une fois actif (accents, suggestions, chiffres) — rien sur l'activation elle-même, alors que c'est le point de friction le plus élevé du parcours (deux avertissements système consécutifs)
- Ajout de 4 captures d'écran réelles (émulateur `kreyol_test`, Android 14, état pristine reproduit par désinstallation/réinstallation) illustrant pas à pas : l'écran système « Clavier à l'écran », le premier avertissement Android (collecte de données), le sélecteur système de mode de saisie, et l'écran final « Configuration terminée »
- Texte organisé en 4 étapes numérotées, en complément du tunnel interactif déjà présent dans l'onglet Démarrage — utile pour s'y référer après coup (réinstallation, changement de téléphone) sans repasser par tout le flux

## [10.2.8] - 2026-08-03

### 🐛 Les suggestions de mots n'apparaissaient plus sur les APK/AAB publiés

- **Constat** : sur un Samsung A21s en v10.2.6, les propositions de mots avaient disparu — aucune suggestion, aucun plantage visible
- **Cause** : `KreyolComplet.py` écrit correctement deux formats pour `creole_dict.json` : un objet `{mot: fréquence}` pour l'ancien prototype Flutter, et un tableau `[[mot, fréquence], ...]` directement dans `android_keyboard/app/src/main/assets/`, format attendu par `JSONArray(...)` dans `SuggestionEngine.kt`. L'étape « Copy Dictionary to Android Assets Directory » de `build-apk.yml` écrasait ensuite ce second fichier avec le premier (objet Flutter) — un `cp` redondant resté en place depuis avant même le fix du 10.2.6. `JSONArray(...)` levait alors une `JSONException` que `catch (e: IOException)` ne pouvait pas intercepter, laissant le dictionnaire vide sans le moindre signal d'erreur
- **Vérifié** : téléchargement de la release GitHub `v10.2.6` publiée et inspection de `assets/creole_dict.json` embarqué : bien au format objet, pas tableau — confirmé sur l'APK réellement distribué, pas seulement en théorie
- **Corrigé** : suppression du `cp` fautif dans `build-apk.yml` (le script écrit déjà le bon format au bon endroit) ; élargissement de `catch (e: IOException)` à `catch (e: Exception)` dans `SuggestionEngine.loadDictionary()` pour qu'un futur format inattendu échoue de façon journalisée plutôt que silencieuse

### ✨ Indice visuel pour changer de clavier

- Un petit 🌐 semi-transparent apparaît maintenant dans le coin de la barre d'espace : l'appui long (1s) pour ouvrir le sélecteur de clavier système existait déjà mais restait indécouvrable, sans ajouter de touche dédiée à une rangée du bas déjà dense (9 touches)

## [10.2.6] - 2026-08-03

### 🐛 Les nouveaux mots du corpus Hugging Face n'atteignaient jamais l'APK construit

- **Constat** : des mots récemment ajoutés au dataset `POTOMITAN/PawolKreyol-gfc` (ex. `fwiyapen`, `krik`, `mistikrik`, `mistikrak`, `karukera`, `klavyé`, `sentanj`, `tchè`) n'apparaissaient dans aucune suggestion, alors que les logs du job `generate-dictionary` de `build-apk.yml` montraient bien leur détection et leur ajout
- **Cause** : ce job régénère le dictionnaire dans son propre espace de travail éphémère, mais chacun des 4 jobs de build (`build-debug-apk`, `build-debug-aab`, `build-release-apk`, `build-release-aab`) effectue son propre `actions/checkout` indépendant. Le `needs: generate-dictionary` garantit uniquement l'ordre d'exécution, pas le partage de fichiers entre jobs : les builds réutilisaient donc systématiquement `creole_dict.json`/`creole_ngrams.json` tels que committés dans git, jamais la version fraîchement régénérée depuis Hugging Face
- **Vérifié** : téléchargement de l'APK release publié sur la GitHub Release `v10.2.5` et inspection de `assets/creole_dict.json` embarqué : taille et nombre de mots strictement identiques au fichier committé (aucune trace des 8 nouveaux mots), confirmant que la régénération CI était bien silencieusement ignorée depuis la mise en place du pipeline
- **Corrigé** : ajout d'un `actions/upload-artifact` en sortie de `generate-dictionary` et d'un `actions/download-artifact` en tête de chacun des 4 jobs de build, pour écraser la version committée par la version fraîchement régénérée avant compilation
- **Effet de bord corrigé au passage** : régénération locale du dictionnaire/n-grams committés dans le dépôt (`android_keyboard/app/src/main/assets/`, `clavier_creole/assets/`), qui étaient eux aussi figés depuis le 30/07 malgré l'ajout de ces mots sur Hugging Face entre-temps

## [10.2.5] - 2026-08-03

### 📌 Bandeau d'installation ancré en bas pendant l'onboarding

- **Constat** : le seul rappel "Ça vous plaît ? Installez-le →" était un bouton intégré dans la carte de démo du clavier, invisible dès que l'utilisateur scrollait ailleurs dans l'onboarding
- **Ajouté** : un bandeau bleu superposé, ancré en bas de tout l'écran (`FrameLayout` racine), visible dès l'arrivée sur l'onboarding tant que le clavier n'est ni activé ni sélectionné, indépendant du scroll ; en complément du bouton existant dans la carte de démo, pas en remplacement
- Même style et même action que le bouton existant (fond bleu, texte blanc, clic → interstitiel d'avertissement avant activation)
- Disparaît en fondu (300 ms) dès que l'onboarding se termine, en même temps que la barre d'onglets réapparaît
- **Vérifié sur émulateur** (`kreyol_test`, Android 14, headless puis fenêtré) : bandeau visible dès le premier lancement, clic fonctionnel, disparition confirmée une fois le clavier activé + sélectionné

## [10.2.4] - 2026-08-03

### 🐛 Log de diagnostic trompeur dans le pipeline dictionnaire

- **Constat** : le résumé de chargement du corpus affichait toujours "🌐 Source: Local", y compris après un téléchargement Hugging Face réussi
- **Cause** : la détection de source devinait "Hugging Face" en cherchant ce mot dans le champ `Source` des textes eux-mêmes (métadonnée d'auteur, jamais égale à ça)
- **Corrigé** : suivi explicite de la branche empruntée (Hugging Face vs fallback local) pendant le chargement, au lieu de deviner après coup

### 🛡️ Garde-fou CI : la release échoue si le CHANGELOG n'est pas à jour

- **Constat** : les notes de release des tags `v10.2.1` et `v10.2.3` avaient publié en silence le contenu de `10.1.2` : le job de release prenait toujours la première section du CHANGELOG sans vérifier qu'elle correspondait au tag poussé
- **Corrigé** : `build-apk.yml` compare désormais la version du tag à celle en tête de `CHANGELOG.md` et fait échouer explicitement le job en cas de décalage, au lieu de publier des notes périmées

## [10.2.3] - 2026-08-03

### 🎉 Carte de succès partageable à la fin de l'activation

- **Constat** : l'onboarding se terminait sans aucun retour positif après un parcours d'activation (interstitiel d'avertissement Android + réglages système) identifié comme un point de friction pour les utilisateurs peu technophiles
- **Ajouté** : une carte de félicitation "🎉 Klavyé Kréyòl aktivé !" à la fin de l'onboarding, avec un bouton de partage natif Android (sélecteur système)
- **Confidentialité** : le message proposé au partage est entièrement fixe et pré-rédigé, écrit avant que l'utilisateur ait tapé quoi que ce soit avec le clavier ; aucun contenu personnel n'est lu ni réutilisé
- Affichée une seule fois (flag dédié dans les préférences d'onboarding)

### 🐛 Le pipeline dictionnaire ignorait le token Hugging Face en CI

- **Constat** : chaque build CI se connectait en anonyme à Hugging Face, échouait, et basculait silencieusement sur un corpus local de secours (504 textes) sans jamais régénérer le dictionnaire depuis les données fraîches
- **Cause** : la lecture de `HF_TOKEN` dans `Dictionnaires/KreyolComplet.py` était imbriquée dans un bloc conditionné à la présence d'un fichier `.env` local, jamais vrai en CI où le secret est injecté directement comme variable d'environnement
- **Corrigé** : la lecture du token s'exécute désormais dans tous les cas
- **Effet de bord découvert en vérifiant** : le dataset `POTOMITAN/PawolKreyol-gfc` contenait lui-même une virgule JSON manquante le rendant illisible, corrigée directement sur Hugging Face ; le pipeline régénère maintenant le dictionnaire depuis des données réelles (vérifié par relance manuelle du workflow)

### 🧹 Nettoyage

- Retrait de l'emoji 🏝️ (cliché touristique) des textes utilisateur : écran "À propos", message de partage de l'application, image et message de partage de niveau

## [10.2.1] - 2026-08-02

### 🎯 Interstitiel d'avertissement avant l'activation du clavier

- **Constat** : suite à l'analyse du funnel Play Console (1,91k acquisitions pour seulement 54 premières ouvertures), l'étape d'activation dans les réglages système Android était identifiée comme un point d'abandon majeur
- **Ajouté** : un interstitiel montrant la vraie capture de l'avertissement Android avant d'envoyer l'utilisateur dans les réglages système, pour désamorcer la surprise à l'avance plutôt que de la décrire dans une carte qu'il pourrait ne pas lire
- Textes d'onboarding agrandis (13/14sp → 16sp) pour la lisibilité
- Ajout d'un jalon funnel local (SharedPreferences, rien ne quitte le téléphone) pour mesurer l'abandon à cette étape

### 📤 Puce de partage à la première utilisation réelle du clavier

- **Ajouté** : une puce ponctuelle "Envoyer un mot à un ami" dans la barre de suggestions, affichée la première fois que le clavier sert réellement hors de l'app ; un tap insère un message prêt-à-envoyer avec le lien Play Store
- **Piège évité** : la puce n'apparaît que sur un champ dont l'action clavier est "Envoyer" ; le champ destinataire de Google Messages tronque le texte inséré en le réinterprétant comme une recherche de contact (reproduit en test)

## [10.1.2] - 2026-08-02

### 🐛 Notification "mot trouvé" recouvrait le mot dans la liste (jeu de recherche de mots cachés)

- **Constat** : dans le jeu de recherche de mots cachés, la notification de succès ("✅ Mot trouvé : ...") s'affichait environ aux deux tiers de la hauteur de l'écran, pile sur la liste "Mots à trouver", masquant le mot qui venait de passer en vert
- **Premier essai infructueux** : `Toast.setGravity(Gravity.TOP, ...)` n'a aucun effet, le système ignore le positionnement personnalisé des toasts depuis Android 11+ (confirmé par test sur émulateur Android 14)
- **Corrigé** : remplacement des deux `Toast` de `WordSearchFragment.onWordFound()` par une `Snackbar` (vue applicative, non soumise à cette restriction) ancrée en haut de l'écran via `FrameLayout.LayoutParams.gravity`
- **Vérifié sur émulateur** (`kreyol_test`, Android 14) : la notification s'affiche désormais sous la barre de titre, la liste des mots reste entièrement visible au moment où un mot est trouvé

## [10.1.1] - 2026-08-02

### 🐛 Onglets du panneau emoji tronqués en "…" sur Galaxy A21s

- **Constat** : sur Galaxy A21s (écran plus étroit/moins dense que les appareils de test), les icônes des onglets de catégorie du panneau emoji s'affichaient tronquées en "…" au lieu du glyphe emoji
- **Cause** : les onglets et les cellules de la grille utilisaient `Button`, dont le style Material/AppCompat par défaut impose une largeur minimale et un ellipsize sur une ligne ; avec 9 onglets à largeur égale (`écran / 9`), un écran étroit passe sous ce minimum et l'icône se fait tronquer — invisible sur l'émulateur de test (écran plus large, plus dense)
- **Corrigé** : `EmojiPickerView` utilise désormais `TextView` (sans les minimums de style imposés par `Button`) pour les onglets et les cellules de la grille, avec `minWidth`/`minHeight` à 0 et l'ellipsize explicitement désactivé
- **Vérifié sur émulateur** : aucune régression (onglets, surbrillance, swipe, grille, sélection toujours fonctionnels) ; correctif basé sur le diagnostic du style `Button`, non confirmé directement sur l'appareil A21s à l'origine du rapport

## [10.1.0] - 2026-08-01

### 😀 Panneau emoji : jeu exhaustif, catégories par onglets, swipe latéral

- **Constat** : le panneau curé de 80 emojis (v10.0.0) restait limité ; besoin d'un vrai jeu exhaustif avec navigation par catégorie, comme les claviers Android standards
- **Remplacé** : le `ScrollView` vertical de 80 emojis cède la place à `EmojiPickerView`, un widget dédié combinant onglets de catégories et pages défilables latéralement (`ViewPager2`), chaque page étant une grille virtualisée (`RecyclerView`/`GridLayoutManager`) — indispensable pour rester fluide avec ~1900 emojis chargés sur les téléphones bas de gamme visés par ce projet (construire ~1900 `Button` d'un coup, sans virtualisation, aurait un vrai coût mémoire/jank)
- **Jeu de données** : `assets/emoji_data.json` (48 Ko), généré depuis le fichier officiel `emoji-test.txt` d'Unicode 16.0 — 1906 emojis de base (entrées *fully-qualified*, groupe "Component" exclu), répartis sur les 9 catégories CLDR (Smileys & Emotion, People & Body, Animals & Nature, Food & Drink, Travel & Places, Activities, Objects, Symbols, Flags)
- **Tons de peau systématisés** : les 316 emojis concernés (gestes, personnes) s'affichent par défaut au ton foncé (cohérent avec le choix fait en v10.0.0) ; l'appui long ouvre désormais un vrai sélecteur (les 4 autres tons + le jaune neutre), au lieu d'être figés — réutilise le popup d'accents existant (`AccentHandler`), étendu avec une table de tons chargée dynamiquement (`loadEmojiSkinTones`)
- **Nouvelles dépendances** : `androidx.recyclerview:1.3.2`, `androidx.viewpager2:1.1.0`
- **Bug corrigé en cours de route** : `onAccentSelected` (popup d'accents/tons) ajoutait systématiquement le caractère choisi au mot en cours de frappe (utilisé pour les suggestions dictionnaire) ; correct pour une lettre accentuée, faux pour un emoji ou une ponctuation — désormais restreint aux vraies lettres (`accent.all { it.isLetter() }`)
- **Vérifié sur émulateur** (Android 16, `honor_x9c_test`) : swipe latéral change de catégorie (onglet mis à jour), défilement vertical dans une catégorie, sélection de ton via appui long (les 6 options), insertion et suppression propres (y compris après un ton neutre choisi), tap direct sur les onglets

## [10.0.0] - 2026-08-01

### 😀 Panneau emoji intégré au clavier

- **Constat** : aucun moyen natif d'insérer un emoji, seul recours l'aller-retour vers un autre clavier système via le sélecteur d'IME
- **Ajouté** : nouveau layout emoji (`KeyboardLayoutManager.createEmojiLayout`), sélection curée de 80 emojis (visages, gestes/réactions, cœurs, nature/animaux, quotidien, activités, météo/divers) défilable verticalement dans un `ScrollView` (3 rangées visibles à la fois, le reste accessible en swipe) + rangée de contrôle fixe (`abc`, `⌫`, espace, `⏎`) ; accessible en un tap direct depuis la rangée 4 du clavier alphabétique (à l'emplacement libéré par l'apostrophe, voir ci-dessous) ainsi que depuis le mode 123
- **Représentativité** : les emojis gestuels (👍 👎 🙏 💪 ✌️ 👏 🤝 🤞 👋, natation, cyclisme) utilisent par défaut le ton de peau foncé, plus représentatif pour le public créole guadeloupéen
- **Bug corrigé en cours de route** : `InputProcessor.handleBackspace()` ne supprimait qu'une unité UTF-16, corrompant tout emoji hors plan de base (paire de surrogates) en un glyphe cassé (❓) ; étendu pour gérer aussi les séquences à deux points de code (emoji + modificateur de ton de peau, ex. 💪🏿) en une seule pression
- **Rangée 4 réorganisée** : l'apostrophe `'` (0 occurrence dans `creole_dict.json`, contre 1088 mots pour le tiret `-`) retirée de sa touche dédiée au profit de la touche emoji ; reste accessible en appui long sur `,` (aux côtés de `;` et `:`)
- **Vérifié sur émulateur** (Android 16, `honor_x9c_test`) : ouverture du panneau depuis les deux points d'entrée, défilement vertical jusqu'aux dernières rangées, insertion et chaînage d'emojis sans espace forcé (y compris après scroll), suppression propre au backspace (y compris tons de peau), retour à `abc`, apostrophe en appui long fonctionnelle

## [9.0.0] - 2026-08-01

### 🏗️ Migration vers AGP 9.3.1 / Gradle 9.6.1 (Kotlin natif)

- **Constat** : Google Play signalait que la configuration R8 pouvait entraîner une utilisation mémoire plus élevée et des performances plus faibles, recommandant de passer au plug-in Android Gradle (AGP) version 9.0 ou ultérieure
- **AGP 8.6.0 → 9.3.1** et **Gradle 8.8 → 9.6.1** (exigence minimale d'AGP 9.3.1)
- **Kotlin natif** : suppression du plugin `org.jetbrains.kotlin.android`, incompatible avec la nouvelle DSL AGP 9+ ; Kotlin est désormais compilé nativement par AGP (jvmTarget hérité de `compileOptions`)
- **API de nommage des APK modernisée** : remplacement de l'API dépréciée `applicationVariants.configureEach` par `androidComponents.onVariants`
- **CI alignée** : `build-apk.yml` mis à jour sur Gradle 9.6.1 (4 occurrences)
- **Bug latent corrigé** : `AccentHandler.kt` était encodé en UTF-16 (au lieu d'UTF-8), toléré par l'ancien compilateur Kotlin mais faisant échouer la compilation avec le nouveau compilateur intégré à AGP 9
- **`.toLowerCase()` → `.lowercase()`** dans `KreyolInputMethodService.kt` : la dépréciation Kotlin est désormais bloquante avec le compilateur plus récent
- **Vérifié** : `assembleDebug`, tests unitaires, `assembleRelease` (R8/minification) réussis en local ; build CI GitHub Actions vert sur tous les jobs (Debug/Release APK et AAB), release publiée avec succès

## [8.8.5] - 2026-08-01

### ⌨️ La touche « * » remplace le tiret bas orphelin sur le clavier numérique

- **Constat** : la touche « * », pourtant courante (calculs, mots de passe, mise en forme), était absente du clavier ; la rangée numérique avait un « _ » redondant avec le trait d'union déjà présent sur le clavier alphabétique
- **Corrigé** : `_` remplacé par `*` dans la rangée `= . , ? ! ' + * ⌫` du clavier numérique (`KeyboardLayoutManager.kt`)
- **Vérifié sur émulateur** : la touche s'affiche et s'insère correctement (Android 16 et Android 34)

## [8.8.4] - 2026-07-31

### ✏️ « Ékri » remplacé par « Maké » dans l'interface

- **Changé** : les textes d'invite du champ de test clavier (onboarding et démo) et le message de partage utilisent désormais « Maké » plutôt que « Ékri »

## [8.8.0] - 2026-07-23

### 🎯 Cible Android 16 (API 36) pour rester conforme Google Play

- **Constat** : Google Play signalait que l'appli ciblait encore Android 15 (API 35), non conforme à l'exigence d'API cible récente (deadline 31 oct. 2026 avant blocage des mises à jour)
- **Corrigé** : `compileSdk` et `targetSdk` passés à 36 dans `app/build.gradle`
- **Vérifié** : builds debug et release reconstruits avec succès (AGP 8.6.0, plateforme SDK 36 déjà installée localement), R8/lint release sans erreur

## [8.7.4] - 2026-07-22

### 📦 Règles ProGuard resserrées pour une vraie optimisation R8

- **Constat** : Google Play recommandait d'« améliorer la mémoire et les performances avec R8 » malgré `minifyEnabled = true` déjà actif ; les règles `-keep class androidx.** { *; }` et `-keep class kotlin.** { *; }` neutralisaient le shrinking/obfuscation sur toute la stdlib Kotlin et AndroidX, de même que les `-keep ... { public *; }` sur les classes internes de l'app (Dictionary, Suggestion, AccentHandler, etc.)
- **Corrigé** : suppression des règles trop larges, aucune n'étant justifiée (pas de réflexion sur ces classes, confirmé par recherche dans le code) ; seul le nom des classes Activity/Service/InputMethodService reste protégé, car référencé par `AndroidManifest.xml`
- **Vérifié** : build release reconstruit, mapping R8 confirme le renommage et la suppression effective de centaines de classes AndroidX/Kotlin auparavant intactes

## [8.7.3] - 2026-07-22

### ⌨️ Trois digraphes GEREC manquants ajoutés à l'appui long (n, g, t)

- **Constat** : les digraphes de consonnes palatalisées de la graphie GEREC (ch, dj, tj, ng, ny, gn, gy) n'étaient couverts que partiellement (ch sur "c", dj sur "d", ng sur "n") ; un comptage des occurrences cumulées dans `creole_dict.json` et `french_simple_dict.json` a révélé plusieurs manques significatifs
- **Touche "n"** : gagne "ny" (/ɲ/, 1353 occurrences, 47 mots) en plus de "ng", déjà plus fréquent que "dj" (74) présent depuis v8.2.0
- **Touche "g"** : gagne "gn" (2915 occurrences, digraphe français : montagne, campagne) et "gy" (221, variante créole rare), touche qui n'avait jusqu'ici aucun appui long
- **Touche "t"** : gagne "tj" (/tʃ/, 184 occurrences), complétant la série des occlusives palatalisées ch/dj/tj/ng aux côtés des touches c/d/n déjà couvertes
- **Build vérifié** : compilation Kotlin réussie (`compileDebugKotlin`)

## [8.7.2] - 2026-07-22

### 🔎 Aperçus en coin et ordre d'appui long recalés sur la fréquence réelle (e, o)

- **Touche "e"** : appui long enrichi de é et è (déjà touches dédiées par ailleurs), classés par fréquence décroissante dans `creole_dict.json` : é (86 743, 1603 mots) > è (45 490, 992 mots) > ê (15, 1 mot). Indices de coin : `è` en haut-droit, `é` en bas-droit
- **Touche "o"** : appui long enrichi de ò et ó, ordre choisi `ò, ô, ó, œ`. Indices de coin déplacés à gauche : `ò` en haut-gauche, `ó` en bas-gauche (à la place de ô/œ précédemment affichés à droite)
- **Mécanisme d'aperçu en coin généralisé** (`KeyboardLayoutManager`/`AccentHandler`) : chaque touche peut désormais définir un ordre et un côté (gauche/droit) d'affichage indépendants de l'ordre du popup d'appui long, sans changer le comportement par défaut (droite) des autres touches accentuées (a, i, u, c, `,` `.` `'`)
- **Vérifié sur émulateur** : capture zoomée confirmant les 4 coins (`è`/`é` sur "e", `ò`/`ó` sur "o") et l'ordre exact du popup d'appui long sur chaque touche

## [8.6.1] - 2026-07-22

### 🎨 La touche trait d'union reprend la couleur orange caraïbe

- **Bug visuel corrigé** : le trait d'union (`-`), devenu touche dédiée en v8.6.0, avait été classé par erreur avec les voyelles accentuées (fond blanc) au lieu de rejoindre `,` et `.` avec qui il partage la même rangée
- **Corrigé** : fond et texte alignés sur le groupe ponctuation (orange caraïbe), cohérent visuellement avec ses voisines de rangée
- **Vérifié sur émulateur** : capture d'écran confirmant la couleur corrigée dans l'app Messages

## [8.6.0] - 2026-07-22

### ➖ Le trait d'union devient une touche dédiée

- **Suite du constat v8.5.0** : le trait d'union (21,7% des mots créoles, fréquence cumulée 26 623) dépasse l'usage de la touche dédiée « ò » (18 699) — il méritait mieux qu'un appui long
- **Nouvelle touche directe** dans la rangée du bas, entre `é` et la barre d'espace : `123 , é - [espace] è . ' ⏎` (9 touches au lieu de 8, chacune environ 9% plus étroite mais toujours confortablement tapable)
- **Retiré de l'appui long sur `.`** (redondant désormais), qui repasse à `! ? …`
- **Vérifié sur émulateur** : « a-y » (le mot avec trait d'union le plus fréquent du dictionnaire, 1906 occurrences) se tape directement et déclenche la bonne suggestion dès la première lettre
- **`é` et `è` conservés intacts** : tous deux plus utilisés que le trait d'union (86 743 et 45 490 contre 26 623), aucune raison de les sacrifier

## [8.5.0] - 2026-07-21

### ➖ Le trait d'union remonté en priorité sur l'appui long du point

- **Constat chiffré** : le trait d'union apparaît dans 21,7% des mots du dictionnaire créole (1068 mots sur 4911), avec une fréquence d'usage cumulée (26 623) supérieure à celle de la touche dédiée « ò » (18 699) — c'est le marqueur d'élision créole le plus productif (« a-y », « ba-w », « an-nou », « fi-la »…), et pourtant il n'apparaissait qu'en 4ᵉ et dernière position de l'appui long sur `.`, donc invisible dans l'indice de coin introduit en v8.3.0
- **Réordonné en tête** : appui long sur `.` propose désormais `- ! ? …` (au lieu de `! ? … -`), et l'indice de coin affiche directement `-` en haut à droite
- **`ç` conservé** malgré une justification faible (0 mot créole, 1 seul mot français : « français ») : le coût de le garder est nul, contrairement au bénéfice de pouvoir taper ce mot sans détour

## [8.4.0] - 2026-07-21

### 📊 Table des accents recalée sur l'usage réel des dictionnaires

- **Analyse chiffrée** de `creole_dict.json` (4911 mots) et `french_simple_dict.json` (662 mots) : comptage de chaque caractère diacritique, mot par mot, pour vérifier que la table d'appui long (v8.2.0/v8.3.0) correspond à un usage réel plutôt qu'à un gabarit générique
- **ë et ü retirés** de l'appui long sur e/u : zéro occurrence dans les deux dictionnaires réunis, aucune justification créole ni française
- **œ ajouté** en appui long sur o (aux côtés de ô) : présent dans le dictionnaire français avec une fréquence notable (« œil », « cœur »), jusqu'ici totalement impossible à taper
- Confirmé au passage : é/è/ò concentrent 98% de l'usage réel des diacritiques créoles (1603, 992 et 378 mots sur 4911), ce qui valide leur statut de touches dédiées

## [8.3.0] - 2026-07-21

### 🔎 Aperçu des options d'appui long directement sur les touches

- **Ponctuation ajoutée en appui long** sur trois touches déjà visibles en mode alphabétique : virgule → point-virgule/deux-points, point → !/?/…/trait d'union, apostrophe → guillemets droits/guillemets français « ». Évite l'aller-retour vers le mode 123 pour la ponctuation la plus fréquente
- **Petits indices en haut-droit et bas-droit de chaque touche concernée** (lettres accentuées et ponctuation) : un aperçu discret des deux premières options d'appui long, sans changer la zone tactile ni le style de la touche
- **Bug découvert et corrigé en testant cet affichage** : `Button` porte une élévation/`StateListAnimator` implicite qui le fait toujours dessiner par-dessus ses voisins ajoutés après lui dans un conteneur superposé, quel que soit l'ordre d'ajout — les indices restaient invisibles tant que l'élévation n'était pas explicitement neutralisée sur la touche. Corrigé, vérifié par capture d'écran zoomée sur émulateur
- **Correctif de fond au passage** : la liste interne des touches (`keyboardButtons`, utilisée pour la casse majuscule/minuscule) recevait chaque touche deux fois depuis l'origine ; sans le corriger, les nouvelles touches enrobées y auraient laissé des entrées inertes
- Le clavier d'essai interactif (v8.0.0) n'affiche pas ces indices, n'ayant pas de gestion d'accents — comportement inchangé

## [8.2.0] - 2026-07-21

### ⌨️ Appuis longs recentrés sur le kréyòl et le français

- **Table des accents nettoyée** : la liste de caractères proposés en appui long sur chaque touche venait visiblement d'un gabarit générique — elle mélangeait des lettres polonaises, turques et nordiques (č, š, ć, ř, ž, ł, ÿ…) qui ne servent ni au kréyòl guadeloupéen ni au français. Retirées : 25 caractères sans usage sur les touches a, e, i, o, u, n, c, s, z, l, y
- **Doublons avec le clavier de base supprimés** : é, è et ò sont déjà des touches dédiées du clavier (rangées 1 et 4) — inutile de les proposer une seconde fois via appui long sur e/o
- **Digraphes créoles ajoutés** : appui long sur c → ch, sur d → dj, sur n → ng, trois groupes de lettres fréquents en graphie créole GEREC (chapo, djòl, moun…), absents jusqu'ici de tout raccourci
- Résultat : 39 propositions réparties sur 11 touches → 14 propositions réparties sur 8 touches, popup d'accents plus rapide à parcourir
- ⚠️ Les digraphes ch/dj/ng sont une proposition à valider à l'usage par des locuteurs kréyòl ; à ajuster si le retour terrain montre un découpage différent plus naturel

## [8.1.0] - 2026-07-20

### 🌉 Le pont entre l'essai et l'installation, et trois points d'hygiène

- **Bouton d'installation dans la carte d'essai** : la démo (v8.0.0) créait la motivation mais ne la convertissait pas — après avoir tapé « bonjou » et vu les suggestions, l'utilisateur devait comprendre seul qu'il fallait redescendre vers l'étape 1. Un bouton « Ça vous plaît ? Installez-le → » apparaît désormais dès la première touche pressée ou la première suggestion touchée, et enchaîne directement vers l'activation système
- **Correctif de décalage découvert en testant ce bouton** : le faire apparaître poussait tout le contenu vers le bas (le clavier de démo se décalait sous les doigts dès la première frappe), ce qui aurait fait rater les touches suivantes tapées de mémoire. Corrigé en réservant sa place dès la création de la carte (`INVISIBLE` plutôt que `GONE`) : plus aucun décalage, vérifié par un test automatisé qui reproduisait le problème avant correctif
- **Nouveau jalon dans le tunnel d'activation** : « Premier essai (clavier de démo) », horodaté dès la première touche pressée dans la démo, avant même l'activation système — visible dans la carte Diagnostic de l'onglet À Propos
- **Le correcteur orthographique sort du parcours numéroté** : il s'affichait comme une « étape 4 » alors que la barre de progression annonce 3 étapes (il est en réalité indépendant, utilisable sans même avoir activé le clavier Kréyòl). Nouvelle section « 🚀 Pou ay pli lwen (optionnel) » avec un badge distinct (✚), pour ne plus laisser croire à une étape supplémentaire obligatoire
- **Le moteur de suggestions de la démo est libéré** dès que la configuration aboutit (`onOnboardingCompleted()`) plutôt que de rester chargé en mémoire indéfiniment après la disparition de sa carte — pertinent sur les appareils d'entrée de gamme
- **Vérifié à grande police** (`font_scale` 1.3, réglage courant chez les utilisateurs seniors) : tout le wizard tient sans chevauchement ; seul le libellé de la touche « 123 » se tronque visuellement en « 12 » (touche pleinement fonctionnelle, défaut cosmétique mineur laissé en l'état)

## [8.0.0] - 2026-07-19

### 🎹 Essayez le clavier avant de l'installer

Changement majeur de logique d'accueil, d'où le saut de version : jusqu'ici, l'installation demandait un acte de foi (accepter des avertissements système pour un clavier jamais essayé). L'ordre s'inverse : on essaie d'abord, on installe ensuite.

- **Un vrai clavier interactif dans l'écran d'accueil** : le wizard de première ouverture embarque désormais un clavier Kréyòl complet et jouable — les mêmes composants que le vrai clavier (disposition AZERTY créole, moteur de suggestions bilingues, dictionnaires complets), branchés sur un champ de démonstration. L'utilisateur tape « bonjou », voit les suggestions Kréyòl (vertes) et Français (bleues) apparaître en direct, touche une suggestion pour compléter le mot : il ressent la valeur du clavier en dix secondes, avant tout passage par les réglages système
- **Aucune activation requise pour l'essai** : tout tourne à l'intérieur de l'app ; le champ de démonstration n'ouvre jamais le clavier système et désactive le correcteur orthographique système (qui soulignait les mots créoles en rouge, à rebours de ce que la démo veut montrer)
- **Shift, verrouillage majuscules, mode 123, retour arrière** fonctionnent dans la démo ; les accents é, è, ò sont des touches directes. L'image statique d'aperçu introduite en 7.1.6 est remplacée par cette démo vivante
- **Correctif au passage** : les touches s'affichaient en MAJUSCULES dans le contexte d'une activité (le thème AppCompat impose `textAllCaps` aux boutons) ; les touches reflètent désormais exactement l'état shift quel que soit le contexte

## [7.1.10] - 2026-07-19

### 💡 Rattrapage des activations inachevées

- **Détection du retour infructueux des réglages** : le cas d'échec le plus probable de toute l'installation est désormais rattrapé. Android affiche deux avertissements successifs à l'activation d'un clavier tiers, et s'arrêter au premier annule silencieusement l'activation (erreur commise deux fois pendant nos propres tests pilotés). L'app horodate le départ vers les réglages ; si l'utilisateur revient sans clavier activé, la carte d'information générique est remplacée par un encouragement ciblé : « Presque ! ... Android demande de valider deux avertissements l'un après l'autre : s'arrêter au premier annule l'activation. Rouvrez les paramètres et validez-les tous »
- **La carte disparaît d'elle-même** dès que l'activation aboutit (détection instantanée par le ContentObserver), et l'horodatage est nettoyé pour ne jamais réapparaître à tort plus tard
- **Piste du surlignage abandonnée après prototype** : l'extra `:settings:fragment_args_key`, qui fait défiler et clignoter une ligne précise sur certains écrans de réglages AOSP, est ignoré par l'écran « Clavier à l'écran » (vérifié image par image sur émulateur API 34 : aucune animation). Les lignes de cet écran sont construites dynamiquement sans clés de préférence. Documenté ici pour éviter de réexplorer la piste

## [7.1.9] - 2026-07-19

### 🔎 Diagnostic local du parcours d'activation

- **Quatre jalons horodatés en local** : première ouverture de l'app, activation du clavier, sélection, premier mot tapé. Chaque jalon n'est enregistré qu'une fois, en SharedPreferences — rien ne quitte le téléphone, conformément à la promesse « zéro collecte » de l'app
- **Carte « Diagnostic d'activation » dans À Propos** : affiche la date de première ouverture puis, pour chaque jalon suivant, le délai écoulé (« moins d'une minute après l'ouverture », « 2 h après l'ouverture »...) ou « pas encore ». Utile pour comprendre où le parcours accroche quand un utilisateur en difficulté montre son téléphone, et pour vérifier soi-même que tout est en place
- **Premier mot horodaté par le clavier lui-même** au moment où un mot est réellement commité (suggestion tapée ou espace), champ de test compris — c'est le moment « aha » que tout le parcours cherche à atteindre

## [7.1.8] - 2026-07-19

### ✍️ Premier mot guidé et rappel en cas de désélection

- **Micro-tâche concrète au premier essai** : l'étape 3 ne dit plus vaguement « tapez quelques mots » mais propose « Essayez d'écrire “Bonjou tout moun” et regardez les suggestions vous aider » — un objectif précis qui fait rencontrer immédiatement la vraie valeur du clavier : les suggestions bilingues et les accents créoles
- **Rappel clair quand le clavier n'est plus actif** : après une mise à jour système ou un changement de réglages, Android peut désélectionner le clavier sans prévenir. L'utilisateur qui rouvre l'app ne retombe plus sur le « Bienvenue ! » de première installation : il voit « 🔔 Le clavier Kréyòl n'est plus sélectionné » (ou « n'est plus actif ») avec l'explication probable et l'étape exacte à refaire
- **Pas de redite pour ceux qui connaissent** : l'aperçu du clavier (image de motivation destinée aux nouveaux) n'est plus montré aux utilisateurs qui avaient déjà tout configuré, et la barre d'onglets reste accessible — seul le tout premier setup est en mode concentré

## [7.1.7] - 2026-07-19

### 📝 Instructions formulées par objectif, valables sur tous les téléphones

- **Des instructions qui décrivent le but, pas un chemin d'écran** : chaque constructeur (Samsung, Xiaomi...) réorganise les écrans de réglages à sa façon, donc décrire un chemin précis (« dans la liste Clavier à l'écran ») peut ne pas correspondre à ce que voit l'utilisateur. Les cartes disent maintenant quoi chercher : « Trouvez 'Klavyé Kréyòl Karukera' dans l'écran qui s'ouvre, activez l'interrupteur, puis revenez ici » — le libellé de l'app est la seule constante affichée partout
- **Préparation aux avertissements système** : l'activation d'un clavier tiers déclenche un ou deux dialogues de confirmation selon les téléphones, et abandonner en cours de route annule l'activation (constaté en test : valider le premier puis revenir en arrière laisse le clavier désactivé). La carte d'information annonce désormais « un ou deux avertissements de sécurité : validez-les tous pour terminer »
- **Fin des Toasts d'instruction** : les messages flottants qui s'affichaient par-dessus les écrans système (position et durée non maîtrisables) sont supprimés au profit des cartes, lisibles avant de partir vers les réglages. Seule exception conservée : l'écran de repli du correcteur orthographique, différent de celui attendu, garde son message d'orientation
- **Étape 4 plus claire** : la carte du correcteur indique directement quoi choisir dans l'écran (« choisissez 'Correcteur Kréyòl Karukera' »), instruction qui n'existait auparavant que dans un Toast fugace

## [7.1.6] - 2026-07-19

### 🚀 Première ouverture concentrée sur l'essentiel

- **Mode « première ouverture »** : tant que le clavier n'a jamais été entièrement configuré, la barre d'onglets (jeux, stats, guide...) et le swipe entre onglets sont masqués — l'utilisateur qui vient d'installer l'app voit uniquement le parcours de configuration, sans distraction. La navigation se révèle en fondu au moment où la configuration aboutit, comme une petite récompense
- **Le flag ne se pose qu'une seule fois** (`onboarding_completed` en local) : un utilisateur déjà configuré qui met à jour l'app ne voit jamais le mode restreint, et celui dont le clavier se retrouve désélectionné plus tard (changement de téléphone, mise à jour système) garde l'accès à tous les onglets
- **Aperçu du clavier avant l'effort** : en tête du parcours de configuration, une image du vrai clavier montre ce que l'utilisateur va obtenir — suggestions bilingues « Bonjou » (Kréyòl) / « Bonjour » (Français) au-dessus du clavier AZERTY créole avec ses accents ò, é, è. La motivation précède la demande d'aller accepter les avertissements système. L'aperçu disparaît une fois le clavier configuré

## [7.1.5] - 2026-07-19

### ⚡ Détection instantanée des changements de clavier

- **Réaction immédiate à la sélection du clavier** : l'onboarding sondait l'état du clavier toutes les 2 secondes (Handler périodique), donc après avoir choisi « Klavyé Kréyòl Karukera » dans le sélecteur, l'écran « Tout est prêt ! » et l'apparition automatique du clavier pouvaient traîner jusqu'à 2 secondes. Le polling est remplacé par un `ContentObserver` sur les réglages système (`DEFAULT_INPUT_METHOD`, `ENABLED_INPUT_METHODS`, correcteur orthographique) : la réaction est désormais instantanée, vérifié sur émulateur (interface à jour moins de 0,9 s après le tap, focus et clavier compris)
- **Moins de travail en arrière-plan** : plus de Handler qui interroge le système toutes les 2 secondes tant que l'onglet Démarrage est visible ; l'app ne fait plus rien tant qu'un réglage ne change pas réellement. L'observation démarre au `onResume` et s'arrête au `onPause` du fragment
- Ces réglages sont des clés publiques stables présentes sur tout Android (aucune dépendance à un constructeur particulier)

## [7.1.4] - 2026-07-19

### ⚡ Onboarding fluidifié : sélecteur immédiat et enchaînement automatique

- **Le sélecteur de clavier s'ouvre immédiatement** : le bouton « Ouvrir le sélecteur » attendait 2,2 secondes (le temps qu'un Toast d'instruction disparaisse) avant d'afficher le sélecteur système. Ce temps mort invitait au double-tap, avec sélection accidentelle d'un clavier possible (reproduit en test : le second tap atterrissait sur le dialogue en train de s'ouvrir). Le Toast, l'attente et l'EditText invisible qui servait de contexte de saisie sont supprimés — l'instruction est déjà portée par la carte de l'étape 2, visible derrière le dialogue
- **Enchaînement automatique des étapes** : au retour des réglages système avec le clavier fraîchement activé, le sélecteur s'ouvre tout seul ; une fois « Klavyé Kréyòl Karukera » sélectionné, le champ de test reçoit automatiquement le focus et le clavier Kréyòl apparaît — l'utilisateur peut taper son premier mot sans aucun tap de navigation
- **Robustesse** : l'appel `showInputMethodPicker()` est silencieusement ignoré par Android tant que l'activité n'a pas repris le focus fenêtre (`InputMethodManagerService: Ignoring showInputMethodPickerFromClient`, vérifié dans logcat). Nouveau garde `runWhenWindowFocused()` : attente du focus avec retries bornés avant l'appel. Parcours complet vérifié sur émulateur API 34 depuis un état vierge

## [7.1.3] - 2026-07-17

### 🐛 Toast recouvrant le sélecteur de clavier

- **Le message d'aide de l'onboarding recouvrait la liste des claviers** : signalé par un utilisateur (« le toaster de proposition de choix de clavier couvre le choix du clavier »), reproduit en testant avec seulement 2 claviers installés, ce qui place « Klavyé Kréyòl Karukera » en dernière position de la liste système, pile là où le Toast d'aide s'affichait. `openInputMethodPicker()` ouvrait le sélecteur seulement 100ms après avoir affiché le Toast (`LENGTH_LONG`, ~3,5s), donc les deux se chevauchaient forcément pendant plusieurs secondes. Le sélecteur ne s'ouvre désormais qu'une fois le Toast (`LENGTH_SHORT`, ~2s) complètement disparu (délai porté à 2200ms). Tentative de `setGravity(TOP)` pour repositionner le Toast en haut de l'écran : sans effet vérifié sur Android 14/API 34, laissé en place par précaution pour d'éventuels appareils plus anciens mais ce n'est plus le mécanisme de protection réel

## [7.1.2] - 2026-07-17

### 🐛 Corrections issues d'une campagne de tests approfondie sur émulateur

- **Suggestions kréyòl polluées par le contexte n-gram** : un mot sans aucune correspondance dans le dictionnaire (ex. « Ordinateur ») affichait quand même 3 suggestions kréyòl sans rapport, car le bonus contextuel n-gram (prédiction du mot suivant probable) était appliqué à tous les candidats sans vérifier qu'ils correspondaient au préfixe réellement tapé. `getKreyolSuggestions()` filtre désormais les candidats n-gram par préfixe avant de leur appliquer le bonus
- **Bouton correcteur orthographique ouvrait le mauvais écran** : `openSpellCheckerSettings()` lançait `ACTION_INPUT_METHOD_SETTINGS`, qui ouvre la liste des claviers et non le sélecteur de correcteur orthographique. Lance maintenant directement l'écran standard AOSP (`Settings$SpellCheckersSettingsActivity`), avec repli sur l'ancien comportement si l'écran est absent sur certaines ROM
- **Crash/ANR possible en changeant rapidement d'onglet Jeux** : `WordSearchFragment` et `WordScrambleFragment` planifiaient du travail (`generateNewPuzzle()`, `startNewGame()`) via `post {}`, qui peut s'exécuter après que le fragment a été détaché lors d'un changement d'onglet — provoquant une `IllegalStateException` sur `requireActivity()`/`requireContext()`. Ajout de gardes `isAdded` et remplacement de `requireContext()` par `context?.let {}` dans les blocs catch concernés
- **Score « Mots réussis » du Démêle-mots faussé** : `endGame()` affichait `currentWordIndex` comme nombre de mots réussis, ce qui comptait aussi les mots passés/abandonnés. Nouveau compteur dédié `wordsCorrect`, incrémenté uniquement sur une réponse correcte

## [7.1.1] - 2026-07-15

### 🐛 Correction du compteur de mots découverts

- **Statistiques de vocabulaire corrigées** : l'onglet Stats affichait « 0 mots découverts » malgré des centaines d'utilisations enregistrées, découvert en rejouant une conversation complète sur émulateur. `loadVocabularyStats()` ne comptait un mot comme « découvert » que s'il avait été tapé exactement une fois (`userCount == 1`) ; dès qu'un mot était réutilisé, il disparaissait du compteur et de la liste « Mots Découverts ». Aligné sur la définition déjà correcte utilisée ailleurs dans le code (`CreoleDictionaryWithUsage.getDiscoveredWordsCount()` : un mot est découvert dès qu'il a été utilisé au moins une fois)

## [7.1.0] - 2026-07-15

### 🌍 Bilinguisme Kreyòl + Français

Le clavier propose désormais des suggestions en français en plus du kréyòl, avec un rendu visuel unifié sur tout le clavier. Cette version regroupe et clôt le chantier ouvert en 7.0.12/7.0.13 :

- **Suggestions bilingues actives** : français à partir de 3 lettres, kréyòl toujours prioritaire. Cette fonctionnalité existait dans le code depuis la v5.3.1 mais n'avait jamais été activée — il fallait changer complètement de clavier (Play Store) pour écrire en français
- **Deux rangées séparées** (Kreyòl en haut, Français en dessous) : le français ne peut plus être poussé hors écran par un mot kreyòl long ("Bonmaten-la"), un souci réel du premier rendu à rangée unique
- **Puces pleines à contraste renforcé**, texte blanc, micro-label KR/FR groupé par langue (pas répété sur chaque puce)
- **Prédictions contextuelles unifiées** : le mode « mot suivant » (n-grams) affichait encore l'ancien rectangle bleu pastel, découvert en observant une conversation tapée en direct sur émulateur — même rendu que les suggestions bilingues désormais
- **Dictionnaire français nettoyé** : 700 entrées réduites à 662 mots uniques (38 doublons qui pouvaient faire perdre une suggestion pertinente au profit d'un doublon)

## [7.0.13] - 2026-07-15

### 🎨 Look & feel des suggestions bilingues

- **Puces pleines à contraste renforcé** : les suggestions Kreyòl (vert) et Français (bleu) passent d'un texte coloré sur fond gris à des puces pleines arrondies avec texte blanc — plus lisible en vision périphérique et en plein soleil
- **Micro-label KR/FR groupé** : un seul repère de langue avant chaque groupe de suggestions (pas répété sur chaque puce)
- **Suggestions Français toujours visibles** : la barre de suggestions passe de un à deux rangées empilées (Kreyòl en haut, Français en dessous). Auparavant, un mot kreyòl un peu long ("Bonmaten-la") poussait le français hors de l'écran, derrière un scroll horizontal peu découvrable — le français, censé être mis en avant, restait en pratique invisible. La rangée française se masque automatiquement quand elle est vide (< 3 lettres tapées)

## [7.0.12] - 2026-07-15

### 🌍 Bilinguisme Kreyòl + Français

- **Suggestions bilingues réactivées** : le clavier propose désormais aussi des suggestions en français (en bleu), en plus du kréyòl (en vert), à partir de 3 lettres tapées. Le kréyòl reste toujours prioritaire (3 premières positions). Jusqu'ici cette fonctionnalité existait dans le code depuis la v5.3.1 mais n'avait jamais été activée : il fallait changer complètement de clavier (Play Store) pour écrire en français.
- **Dictionnaire français nettoyé** : 700 entrées réduites à 662 mots uniques (38 doublons supprimés, ex. « dire », « professeur », « riche » comptés deux fois), qui pouvaient faire perdre une suggestion pertinente au profit d'un doublon.

## [7.0.10] - 2026-07-13

### 📣 Croissance et gamification

- **Bouton « Noter l'application »** dans l'onglet À Propos, à côté du partage : ouvre la fiche Play Store (lien direct, avec repli automatique si la Play Store n'est pas disponible)
- **Carte de niveau partageable** : à chaque passage de niveau (Pipirit → Benzo), une carte illustrée générée à la volée célèbre la progression et peut être partagée en un clic
- **Correction du créole** : le titre de la carte de partage disait littéralement « faire l'amour pour le créole ». Remplacé par « Ba kréyòl la lanmou'w ! » (donne ton amour au créole)

## [7.0.9] - 2026-07-12

### ✏️ Correction linguistique

- **Message de partage en créole corrigé** (version validée par un locuteur) : « Mwen ka sèvi épi Klavyé Kréyòl Karukera pou ékri kréyòl asi téléfòn an mwen ! Sé on klavyé Android gratui ki ba'w sigjesyon mo an kréyòl Gwadloup. »

## [7.0.8] - 2026-07-12

### ⭐ Avis et mesure d'audience

- **Demande d'avis Google Play in-app** (API officielle In-App Review) : la boîte de notation s'affiche après un vrai usage du clavier et à partir de la 2ᵉ ouverture de l'app
- **Lien de partage tracké** : le bouton « Partager l'application » ajoute `utm_source=in_app_share` pour mesurer les installations issues du bouche-à-oreille dans la Play Console

## [7.0.7] - 2026-07-12

### 📣 Partage

- **Bouton « Partager l'application »** dans l'onglet À Propos : ouvre le sélecteur de partage natif Android avec un message pré-rempli (créole + lien Play Store), pour encourager le bouche-à-oreille

## [7.0.6] - 2026-07-11

### ✨ Guide illustré et navigation

- **Captures d'écran du clavier fonctionnel** ajoutées au guide de l'utilisateur : popup d'accents, barre de suggestions active, mode chiffres/symboles
- **Onglet À Propos déplacé en dernière position** (après Guide) dans la barre d'onglets

## [7.0.5] - 2026-07-11

### ✨ Guide de l'utilisateur

- **Nouvel onglet « Guide »** (6ᵉ onglet, 📖) : écriture en kréyòl, accents par appui long, suggestions et autocomplétion, correction orthographique système, chiffres/symboles, les 2 jeux de vocabulaire, les 8 niveaux de progression (Pipirit → Benzo), et une FAQ courte (clavier invisible, changer de clavier, confidentialité)

## [7.0.4] - 2026-07-11

### ✨ Tunnel d'activation amélioré

- **Carte explicative avant l'avertissement système Android** : prévient l'utilisateur que l'avertissement de collecte de données est standard pour tout clavier tiers, avec un lien direct vers la politique de confidentialité ("zéro collecte")
- **Lien vers la politique de confidentialité** ajouté également dans l'onglet À Propos
- **Nom du clavier raccourci** dans les paramètres système : n'est plus tronqué dans la liste des claviers ni dans le sélecteur
- **Confirmation + astuce accents au premier usage réel** du clavier en dehors de l'app (une seule fois)
- Nettoyage de code mort (`createActivationBanner`/`createStatusBar`, jamais utilisés)

## [7.0.3] - 2026-07-11

### 🐛 Correction

- **Casse des suggestions sous majuscule automatique corrigée** : taper une suggestion juste après la première lettre d'un message (majuscule automatique active) mettait le mot entier en MAJUSCULES ("B" → "BÈL" au lieu de "Bèl"). Une seule lettre majuscule initiale applique désormais une casse de titre, comme attendu.
- Découvert par une simulation automatisée de frappe s'appuyant exclusivement sur les suggestions (982 mots) : 54 messages sur 134 étaient concernés.

## [7.0.2] - 2026-07-10

### 📚 Dictionnaire enrichi

- **Dictionnaire créole passé de 3 680 à 4 911 mots** (+33%) grâce à un enrichissement du corpus source (427 → 2 383 textes)
- **Vocabulaire de sécurité et premiers secours** ajouté : blesé, doktè, rimèd, évakwasyon, vitman et bien d'autres, pour être compris même dans les situations urgentes
- **Prédictions contextuelles enrichies** : 3 582 → 4 232 suggestions basées sur le contexte de la phrase
- Qualité des suggestions validée sur un test de 50 phrases créoles du quotidien : aucune régression, temps de réponse toujours instantané

## [7.0.0] - 2026-07-03

### ✨ Nouvelles fonctionnalités

#### 🎯 Intégration de la distance de Levenshtein dans le scoring
- **Propagation complète de la distance** : `LevenshteinDistance` retourne désormais `(mot, fréquence, distance)` au lieu de perdre la distance
- **Formule de score améliorée** : `(3-distance)×100000` — une correction à 1 édition bat toujours une correction à 2 éditions
- **Exemple concret** : "mesli" propose désormais "mèsi" (d=1) avant "mésyé" (d=2 plus fréquent)
- **Testabilité** : `calculateDictionaryScore` déplacé dans companion object, 4 tests `SuggestionScoringTest` ajoutés

### 🚀 Performances

#### ⚡ Optimisations du moteur de suggestions
- **Normalisation accents optimisée** : Table char→char au lieu de regex (~37 000 compilations de regex évitées par frappe)
- **Formes normalisées précalculées** : au chargement du dictionnaire
- **Bonus préfixe insensible aux accents** : "fe" favorise désormais "fè"
- **Annulation des suggestions précédentes** : à chaque frappe (plus de résultats périmés)
- **Suggestions dès la 1ère lettre** : `MIN_WORD_LENGTH` passé de 2 à 1 (ka, an, sé…)
- **Tests retirés du démarrage production** : remplacés par des tests JVM (`AccentTolerantMatcherTest`)

### 📚 Documentation

- **Rapport d'audit complet** : Analyse du pipeline de suggestions (bugs, performance, confidentialité, qualité prédictive) avec addendum sur les quick wins appliqués
- **CLAUDE.md** : Guide pour Claude Code (architecture, commandes de build, pièges du build local)

### 🧹 Nettoyage

- **Code mort supprimé** : stratégies bigram/trigram (modèle unigramme uniquement), cache Levenshtein, `applyCaseToSuggestion`
- **Suite de tests réparée** : `returnDefaultValues`, dépendance org.json de test, assertions de distance corrigées
- **659 lignes supprimées, 182 ajoutées**

## [6.5.1] - 2025-11-19

### 🐛 Corrections de bugs

#### 🔤 Préservation des majuscules/minuscules dans les suggestions
- **Correction majeure** : Les suggestions respectent maintenant le pattern de casse de votre saisie
  - Si vous tapez "kaBr", les suggestions affichent "kaBrit" (pas "kabrit")
  - Si vous tapez "BONJ", les suggestions affichent "BONJOU" (tout en majuscules)
  - Si vous tapez "Zan", les suggestions affichent "Zanmi" (première lettre en majuscule)
  - La casse est préservée à l'insertion du mot sélectionné
  - Fonctionne dans tous les modes (dictionnaire, bilingue, contextuel)

### 🔧 Améliorations techniques
- Ajout de `applyCasingPattern()` dans `SuggestionEngine.kt` pour appliquer intelligemment la casse
- Correction dans `mergeAndRankSuggestions()`, `getKreyolSuggestions()`, `getFrenchSuggestions()`
- Modification de `InputProcessor.processSuggestionSelection()` pour ne plus écraser la casse

## [6.5.0] - 2025-11-17

### ✨ Nouvelles fonctionnalités

#### 🔤 Correction orthographique intelligente
- **Détection automatique des fautes de frappe** : Le système de suggestions propose maintenant des corrections même quand vous faites des erreurs
  - Algorithme de distance de Levenshtein intégré pour détecter les mots similaires
  - Correction des lettres manquantes : "bonjo" → suggère "bonjou"
  - Correction des lettres en trop : "mesli" → suggère "mèsi"
  - Correction des lettres inversées ou incorrectes : "zanbi" → suggère "zanmi"
  - Combinaison avec la tolérance aux accents : "kreyol" → suggère "kréyòl"
  - Distance maximale de 2 modifications pour éviter les faux positifs
  - Priorisation par fréquence d'utilisation des mots

#### 🎯 Système de suggestions amélioré - Stratégie en cascade
Le moteur de suggestions utilise maintenant une approche intelligente en 6 étapes :

**1️⃣ Capture de saisie** (`InputProcessor.kt`)
- Détection caractère par caractère lors de la frappe
- Construction progressive du mot : "b" → "bo" → "bon" → "bonj" → "bonjo"
- Déclenchement des suggestions via `onWordChanged()`

**2️⃣ Recherche par préfixe** (Étape A - Rapide)
- Recherche de mots commençant par l'input saisi
- Tolérance aux accents intégrée (`AccentTolerantMatcher`)
- Si des résultats trouvés → Retour immédiat

**3️⃣ Correction orthographique** (Étape B - Nouvelle fonctionnalité)
- Activée uniquement si recherche préfixe ne retourne rien ET input ≥ 3 lettres
- Essai 1 : Calcul de distance avec normalisation des accents
- Essai 2 : Calcul de distance sans normalisation
- Algorithme de Levenshtein pour trouver mots à distance ≤ 2 modifications

**4️⃣ Enrichissement contextuel** (N-grams)
- Analyse de l'historique des mots précédemment saisis
- Consultation du modèle `creole_ngrammes.json`
- Bonus de +50 points pour suggestions contextuelles

**5️⃣ Calcul des scores** (Formule avancée)
```
Score = Fréquence du mot
      + 50 (si préfixe exact)
      + (3 - distance_Levenshtein) × 15  [NOUVEAU]
      + 10 (si mot court ≤ 6 lettres)
      - 10 (si mot long > 12 lettres)
      + 5 (si mot avec accents)
```

**6️⃣ Tri et affichage**
- Fusion dictionnaire + N-grams + corrections
- Tri par score décroissant
- Limitation aux meilleures suggestions (5-10 résultats)
- Affichage dans la barre de suggestions

**Optimisations de performance** :
- ⚡ Pré-filtrage par longueur de mot (±2 caractères)
- 🔄 Traitement asynchrone (`CoroutineScope`) pour ne pas bloquer l'UI
- 💾 Cache pour calculs répétés (`calculateCached()`)
- 🎯 Recherche intelligente : rapide d'abord, puissante ensuite

### 🧪 Tests et qualité

#### ✅ Suite de tests complète
- **16 tests unitaires** validant la correction orthographique avec le dictionnaire créole réel
- Tests de fautes courantes : lettres manquantes, en trop, inversées
- Tests de mots typiques : salutations, famille, verbes, mots courants
- Validation des performances (< 100ms pour recherche)
- Couverture des cas limites et edge cases

### 🛠️ Technique

#### 📦 Nouveaux fichiers
- `LevenshteinDistance.kt` : Utilitaire de calcul de distance et recherche de corrections
- `SimpleDictionaryTest.kt` : Suite de tests basée sur le dictionnaire créole
- Dépendances de test ajoutées : JUnit 4.13.2, Kotlin Test 1.9.22

#### 🔧 Modifications
- `SuggestionEngine.kt` : Intégration de la correction orthographique automatique
- `build.gradle` : Incrémentation de version et ajout des dépendances de test

## [6.4.1] - 2025-11-14

### ✨ Nouvelles fonctionnalités

#### 🔤 Jeu de mots mélangés (Word Scramble)
- **Nouveau jeu intégré** : Retrouve l'ordre des lettres pour former des mots créoles
  - Sélection aléatoire de 10 mots parmi les 3,680 du dictionnaire
  - 3 niveaux de difficulté : Facile (4-5 lettres), Normal (5-7 lettres), Difficile (7-10 lettres)
  - Indices visuels : première et dernière lettre pré-remplies automatiquement
  - Score simple : 100 points par mot réussi
  - Système d'indices : révèle la prochaine lettre (-20 points)
  - Interface épurée sans pression temporelle

#### 🎲 Amélioration jeu de mots cachés (Word Search)
- **Interface optimisée** : Expérience de jeu améliorée
  - Fix sélection diagonale : possibilité de croiser des mots déjà trouvés
  - Meilleure réactivité tactile sur la grille 8×8

### 📊 Statistiques et Progression

#### 🔧 Corrections critiques
- **Comptage précis du dictionnaire** : Affichage correct de "3,680 mots" au lieu de "0 mots"
  - Le total est maintenant toujours chargé depuis `creole_dict.json`
  - Plus de confusion avec le fichier d'usage utilisateur vide
- **Niveau initial correct** : Fix affichage "Pipirit" avec 0 mots découverts
  - Avant : affichait "Benzo (niveau maximum)" à tort
  - Après : affiche correctement "Pipirit" et "55 mots restants pour Ti moun"
- **Message de progression intelligent** : 
  - Affiche "niveau maximum atteint" uniquement si vraiment à Benzo (100% du dictionnaire)
  - Sinon affiche le niveau actuel avec progression vers le suivant

### 🎨 Interface et Navigation

#### 🔧 Réorganisation des onglets
- **Nouvel ordre** : Démarrage → Kréyòl an mwen → Mots Mêlés → Mots Mélangés → À Propos
  - L'onglet "À Propos" déplacé en dernière position pour meilleure ergonomie
  - Les jeux regroupés au centre pour faciliter l'accès
- **Navigation améliorée** : 5 onglets avec swipe cyclique maintenu

### 🧹 Refactoring

#### 🎯 Code
- **Suppression du timer** : Jeu de mots mélangés sans contrainte de temps
  - Retrait de `CountDownTimer` et toutes ses références
  - Simplification du scoring (plus de bonus de temps)
  - Interface header épurée : score centré uniquement
- **Optimisation mémoire** : Meilleure gestion des lettres pré-remplies
  - Les lettres de début et fin ne sont plus dupliquées dans les choix
  - Fix restauration correcte après validation incorrecte
- **Code cleaning** : -98 lignes, +70 insertions
  - Suppression du code redondant lié au timer
  - Simplification de la logique de validation

## [6.4.0] - 2025-11-12

### ✨ Nouvelles fonctionnalités

#### 🔤 Jeu de mots cachés (Word Search)
- **Intégration dictionnaire** : Les mots sont maintenant pris directement depuis `creole_dict.json`
  - Sélection aléatoire parmi les 14,722 mots disponibles
  - Filtrage automatique des mots de 3 à 8 lettres pour compatibilité avec la grille 8×8
  - Cache en mémoire pour optimiser les performances
- **Interface simplifiée** : Affichage unifié "🎯 Mots Créoles"
  - Suppression du système de catégorisation par thèmes
  - Variété maximale grâce à la sélection aléatoire dans tout le dictionnaire

### 🧹 Refactoring

#### 🎯 Word Search
- **Nettoyage code** : Simplification de l'architecture
  - Suppression des listes de mots statiques par thème (ANIMAUX, FRUITS, etc.)
  - Suppression des fonctions `getAllThemes()` et `getThemeDisplayName()`
  - Fusion de `loadWordsFromDictionary()` dans `getThemeWords()`
  - Retrait de la logique de filtrage par mots-clés
  - Résultat : code plus simple, maintenance facilitée, variété maximale

## [6.3.0] - 2025-11-06

### ✨ Nouvelles fonctionnalités

#### 🎮 Système de gamification dynamique
- **Niveaux adaptatifs** : Les seuils de progression s'ajustent automatiquement à la taille du dictionnaire
  - Ti moun : 1.5% du dictionnaire (~55 mots pour 3680 mots)
  - Débrouya : 5% (~184 mots)
  - An mitan : 12% (~441 mots)
  - Kompè Lapen : 25% (~920 mots)
  - Kompè Zamba : 45% (~1656 mots)
  - Potomitan : 70% (~2576 mots)
  - Benzo : 100% (tous les mots !)
- **Progression motivante** : Écarts entre niveaux croissants (facile au début, plus difficile à la fin)
- **Évolutivité** : Si le dictionnaire grandit (ex: 5000 mots), les seuils restent proportionnels

### 🔧 Corrections

#### 🐛 Suggestions N-grams
- **Fix regression critique** : Correction du format JSON incompatible avec le moteur de suggestions
  - Suppression du wrapper `"predictions"` attendu mais absent dans le fichier
  - Correction de la clé `"prob"` → `"probability"` dans 3 emplacements
  - Les suggestions contextuelles fonctionnent à nouveau correctement

### 🧹 Refactoring

#### 🎯 Gamification
- **Nettoyage code** : Suppression de 2 systèmes de niveaux redondants
  - Retrait de `MasteryLevel` enum dans VocabularyStats.kt (6 niveaux)
  - Retrait de `getCurrentLevel()` dans VocabularyStatsActivity.kt (7 niveaux)
  - Conservation du système Gaussian dans SettingsActivity.kt (8 niveaux Créoles)
  - Résultat : -50 lignes de code, logique unifiée

## [6.2.9] - 2025-11-05

### 🎨 Interface et UX

#### 🧹 Améliorations
- **Réduction des espaces blancs** : Suppression des espaces blancs inutilisés
  - Retrait de l'espace au-dessus de "Mots à Découvrir"
  - Réduction de l'espace au-dessus de "Mots les plus utilisés"
  - Interface plus compacte et mieux organisée

#### 🔧 Technique
- Suppression du conteneur vide `buttonsContainer` avec padding de 32dp
- Réduction du padding supérieur de `top5Container` de 16dp à 0dp

## [6.2.8] - 2025-11-05

### 🎨 Interface et UX

#### ✨ Nouveau
- **Navigation cyclique** : Swipe infini entre les onglets
  - Swipe vers la droite sur "À Propos" → retour à "Démarrage"
  - Swipe vers la gauche sur "Démarrage" → accès à "À Propos"
  - Navigation fluide dans les deux sens sans limite
- **Réintégration bandeau bleu** : Retour du header "Klavyé Kréyòl" en haut de l'écran pour une meilleure identification de l'app

#### 🔧 Technique
- Implémentation d'un adapter avec nombre virtuel de pages (`Int.MAX_VALUE`)
- Utilisation du modulo pour mapper les positions virtuelles aux 3 onglets réels
- Démarrage au milieu de la plage virtuelle pour permettre le swipe bidirectionnel
- Calcul intelligent de la distance la plus courte lors des clics sur onglets
- Conservation de l'animation Tinder swipe sur tous les déplacements

## [6.2.7] - 2025-11-04

### 🎨 Interface et UX

#### ✨ Nouveau
- **Animation Tinder swipe** : Effet de swipe style Tinder entre les onglets avec :
  - Rotation dynamique -15° à +15° pendant le swipe
  - Translation verticale (carte qui se soulève)
  - Scale progressif jusqu'à 80%
  - Fade out doux avec élévation
  - Animation fluide et moderne pour une navigation tactile plus engageante

#### 🧹 Interface épurée
- **Suppression bandeau bleu** : Retrait du header "Klavyé Kréyòl" en haut de l'écran
- **Suppression logo Potomitan** : Retrait du logo dans l'onglet "À Propos"
- **Design minimaliste** : Interface focalisée sur le contenu essentiel avec navigation par onglets uniquement

#### 🔧 Technique
- Ajout de la classe `TinderSwipeTransformer` implémentant `ViewPager2.PageTransformer`
- Application du transformer via `setPageTransformer()` sur le ViewPager2
- Transformation basée sur 6 propriétés animées : rotation, translationX, translationY, scale, alpha, elevation

## [6.2.3] - 2025-10-29

### 🔧 Corrections

#### 📊 Onglet Statistiques
- **Espacement optimisé** : Suppression du padding top (24dp) dans `createWordListSection()` pour éliminer l'espace vide entre "Mots à Découvrir" et "Mots les plus utilisés"
- **Lisibilité améliorée** : Augmentation de la taille du texte de 16f à 20f dans la liste des top 5 mots (rang, nom du mot et compteur)

Ces ajustements rendent l'onglet "Kréyòl an mwen" plus compact et lisible.

## [6.2.2] - 2025-10-28

### 🔧 Corrections

#### 🎯 Ergonomie et défilement
- **Scroll fonctionnel dans tous les onglets** : Ajout des `LayoutParams` appropriés (MATCH_PARENT, WRAP_CONTENT) dans les 3 méthodes de création de contenu
- **ScrollView optimisé** : Configuration de `isFillViewport=true` pour permettre le calcul correct de la zone défilante
- **Gestion du clavier virtuel** : 
  - Ajout de `windowSoftInputMode="adjustPan|stateHidden"` dans AndroidManifest.xml
  - Le clavier ne couvre plus le contenu important
  - Scroll automatique vers l'EditText de test quand il obtient le focus
- **Interface simplifiée** : 
  - Suppression de la barre de statut redondante (verte/rouge)
  - Carte de progression compacte avec layout horizontal
  - Design plus épuré et moderne

#### 🛠️ Technique
- `createOnboardingContent()` : LayoutParams + OnFocusChangeListener sur EditText
- `createStatsContent()` : LayoutParams pour permettre le scroll
- `createAboutContent()` : LayoutParams pour permettre le scroll
- `OnboardingFragment` : ScrollView avec isFillViewport=true
- `AndroidManifest.xml` : windowSoftInputMode pour SettingsActivity

## [6.2.1] - 2025-10-27

###  Corrections

####  Interface d'onboarding
- **Sélecteur de clavier fonctionnel** : Le bouton "Ouvrir le sélecteur" affiche maintenant correctement la liste des claviers Android
- **Rafraîchissement dynamique** : L'interface se met à jour automatiquement quand on revient à l'app après avoir sélectionné le clavier
- **Détection d'état en temps réel** : 
  - La barre de statut passe instantanément au vert  après sélection
  - Le bouton devient " Sélectionné" automatiquement
  - L'étape 3 se déverrouille immédiatement
  - La barre de progression atteint 100% sans recharger l'app

####  Technique
- Restauration du `onResume()` dans `SettingsActivity` avec délai de 300ms
- Ajout du `onResume()` dans `OnboardingFragment` pour recréer le contenu dynamiquement
- Amélioration de la détection des changements d'état clavier
# Changelog

Toutes les modifications notables de ce projet seront documentées dans ce fichier.

Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/),
et ce projet adhère au [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [6.2.0] - 2025-10-26

### 🎮 Gamification - Distribution Gaussienne

#### ✨ Nouveau
- **Système de niveaux dynamique** : Les seuils de niveaux s'adaptent automatiquement à la taille du dictionnaire
- **Distribution gaussienne** : Répartition mathématiquement correcte des niveaux basée sur une courbe normale
- **8 niveaux équilibrés** :
  - 🌍 Pipirit (< -3σ): ~0.15% - Les tout premiers pas (~4 mots)
  - 🌱 Ti moun (-3σ à -2σ): ~2% - Débutant (~57 mots)
  - 🔥 Débrouya (-2σ à -1σ): ~14% - Débutant avancé (~396 mots)
  - 💎 An mitan (-1σ à 0): ~34% - Intermédiaire (~963 mots)
  - 🐇 Kompè Lapen (0 à +1σ): ~34% - Avancé (~963 mots)
  - 🐘 Kompè Zamba (+1σ à +2σ): ~14% - Très avancé (~396 mots)
  - 👑 Potomitan (+2σ à +3σ): ~2% - Expert absolu (~57 mots)
  - 🧙🏿‍♀️ Benzo (+3σ): ~0.15% - Niveau secret - Tous les mots! (~4 mots)

#### 🔧 Amélioré
- **Cache du dictionnaire** : Comptage des mots mis en cache pour optimiser les performances
- **Calcul des seuils** : Basé sur une vraie distribution normale (μ = 50%, σ = 16.67%)
- **Adaptation automatique** : Si le dictionnaire évolue, les niveaux s'ajustent sans modification de code
- **Documentation enrichie** : Commentaires détaillés avec les pourcentages et approximations pour chaque niveau

#### 📊 Technique
- Nouvelle fonction `calculateGaussianThresholds()` : Calcule dynamiquement les 8 seuils (-3σ à +3σ)
- Nouvelle fonction `getTotalDictionaryWords()` : Récupère le nombre total de mots avec cache
- Modification de `getCurrentLevel()` : Utilise les seuils gaussiens au lieu de valeurs fixes
- Modification de `getNextLevelInfo()` : S'adapte aux seuils dynamiques
- Basé sur ~2833 mots actuellement dans le dictionnaire

### 🎨 Design

#### ✨ Nouveau
- **Page d'onboarding bêta-testeurs** : Nouvelle page `beta_onboarding.html` pour recruter des testeurs
  - Design cohérent avec `feedbacks_form.html`
  - Formulaire Formspree intégré
  - Switch FR/GCF (français par défaut)
  - Gradient rouge/violet thématique
  - Responsive mobile

#### 🔧 Amélioré
- **Switch de langue optimisé** : Taille réduite et positionné en bas à droite
- **Ergonomie** : Plus de superposition entre le titre et les contrôles
- **Accessibilité** : Checkbox de consentement clairement visible

### 🔐 Sécurité

#### 🔧 Corrigé
- **Rotation des mots de passe du keystore** : Changement des mots de passe après exposition accidentelle dans l'historique git
- **GitHub Secrets mis à jour** : STORE_PASSWORD, KEY_PASSWORD, KEYSTORE_BASE64 actualisés
- **Protection renforcée** : `.gitignore` mis à jour pour exclure `*keystore*base64*.txt`

#### 📝 Note de sécurité
- Le certificat de signature reste identique (aucun impact sur Google Play)
- Les anciens mots de passe exposés sont désormais inutilisables
- Historique git contient encore les traces (nettoyage optionnel disponible)

## [6.1.7] - 2025-10-20

### 🐛 Corrigé
- **Touche ENTRÉE** : Résolution du problème critique où la touche ENTRÉE fermait le clavier et provoquait une perte de focus
  - Respect du flag `IME_FLAG_NO_ENTER_ACTION` : Le clavier détecte maintenant quand une application souhaite que ENTRÉE insère une nouvelle ligne plutôt que d'exécuter une action
  - Détection des champs multilignes : Amélioration de la détection des champs de texte multiligne pour insérer correctement les nouvelles lignes
  - Fix validé sur l'application Potomitan et autres applications utilisant des champs multilignes
  - Plus de fermeture intempestive du clavier
  - Plus de perte de focus sur le champ de texte
  - Plus de redirection vers d'autres applications

### 📝 Technique
- Modification de `handleEnter()` dans `InputProcessor.kt` :
  - Vérification du flag `IME_FLAG_NO_ENTER_ACTION` avant d'exécuter les actions IME
  - Détection du flag `TYPE_TEXT_FLAG_MULTI_LINE` pour les champs multilignes
  - Logs détaillés pour faciliter le diagnostic futur
- Documentation complète :
  - `DIAGNOSTIC_TOUCHE_ENTREE.md` : Analyse des causes racines
  - `QUICK_FIX_ENTREE.md` : Documentation de l'implémentation
  - `tests/diagnostic-enter-key.ps1` : Script de diagnostic
  - `tests/reports/quick-fix-enter-test-report.md` : Rapport de validation

## [1.2.0] - 2025-09-07

### 🎉 Ajouté
- **Dictionnaire enrichi** : 1 867 mots créoles (+390 mots)
- **Sources littéraires** : Intégration de textes créoles authentiques
- **Script d'enrichissement** : `EnrichirDictionnaire.py` pour l'évolution du dictionnaire
- **Textes de Gisèle Pineau** : "L'Exil selon Julia"
- **Poésie de Sonny Rupaire** : "Cette igname brisée qu'est ma terre natale"
- **Chansons traditionnelles** : "La voix des Grands-Fonds"

### 🔧 Amélioré
- **Qualité des suggestions** : Plus précises grâce au corpus enrichi
- **Couverture lexicale** : +26% de mots créoles supportés
- **Performance** : Optimisation du chargement du dictionnaire

### 📚 Données
- **Mots les plus ajoutés** : ka, an, té, on, pou, nou, ou, sé
- **Format conservé** : Liste de listes [mot, fréquence]
- **Validation** : Tests sur textes littéraires créoles

## [1.1.0] - 2025-09-06

### 🎨 Ajouté
- **Design Guadeloupéen** : Palette de couleurs du drapeau
- **Logo Potomitan™** : Intégration respectueuse du branding culturel
- **Thème authentique** : Couleurs Caribbean (bleu, jaune, rouge, vert)

### 🔧 Amélioré
- **Interface utilisateur** : Plus moderne et culturellement appropriée
- **Visibilité** : Contraste optimisé pour tous les thèmes Android
- **Accessibilité** : Meilleure lisibilité des touches et suggestions

### 🐛 Corrigé
- **Texte blanc sur fond blanc** : Problème de contraste résolu
- **Affichage suggestions** : Visibilité améliorée
- **Icônes** : Restauration des icônes manquantes

## [1.0.0] - 2025-09-05

### 🎉 Première Version
- **Clavier AZERTY** : Layout français adapté au créole
- **1 477 mots créoles** : Dictionnaire initial basé sur le corpus Potomitan
- **Suggestions intelligentes** : Prédiction de texte en temps réel
- **Accents créoles** : Support complet des caractères spéciaux
- **Mode numérique** : Basculement alphabétique/numérique
- **Service IME** : Intégration native Android

### ⌨️ Fonctionnalités Clavier
- **Appui long** : Accès aux accents (à, è, ò, etc.)
- **Suggestions contextuelles** : Prédiction basée sur la fréquence
- **Interface native** : InputMethodService Android
- **Compatibilité** : Android 7.0+ (API 24)

### 📱 Applications Testées
- **Messagerie** : WhatsApp, Telegram, SMS
- **Email** : Gmail, Outlook
- **Réseaux sociaux** : Facebook, Twitter
- **Productivité** : Notes, Documents Google

### 🏗️ Architecture
- **Kotlin** : Langage de développement moderne
- **Material Design** : Guidelines UI/UX respectées
- **JSON** : Format optimisé pour le dictionnaire
- **Gradle** : Build system standard Android

### 📊 Métriques Initiales
- **Taille APK** : ~8 MB
- **RAM** : ~15 MB en utilisation
- **Démarrage** : <500ms chargement dictionnaire
- **Latence** : <50ms suggestions

## [Versions Futures]

### 🔮 Prévu v1.3.0
- [ ] **Mode hors-ligne complet**
- [ ] **Apprentissage personnalisé**
- [ ] **Sync cloud dictionnaire**
- [ ] **Thèmes personnalisables**
- [ ] **Raccourcis gestuels**

### 🌟 Roadmap v2.0.0
- [ ] **Support vocal**
- [ ] **Traduction français ↔ créole**
- [ ] **Correction orthographique**
- [ ] **API développeurs**
- [ ] **Extension autres créoles caribéens**

---

### Notes de Version

#### Format des Versions
- **Major.Minor.Patch** (SemVer)
- **Major** : Changements incompatibles
- **Minor** : Nouvelles fonctionnalités compatibles
- **Patch** : Corrections de bugs

#### Types de Changements
- **🎉 Ajouté** : Nouvelles fonctionnalités
- **🔧 Amélioré** : Fonctionnalités existantes
- **🐛 Corrigé** : Corrections de bugs
- **🚨 Déprécié** : Fonctionnalités obsolètes
- **❌ Supprimé** : Fonctionnalités retirées
- **🔒 Sécurité** : Correctifs de sécurité
