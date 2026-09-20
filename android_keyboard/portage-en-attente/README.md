# Portage en attente

Du code porté du fork luxembourgeois (Lëtzebuergesch Clavier), **retiré de
`app/src/main/java/` le 20 septembre 2026** parce qu'il compilait dans l'APK
sans être appelé nulle part. Rien ici n'est perdu : il suffit de remettre un
fichier à sa place dans l'arborescence du paquet pour le réactiver.

Un code mort qui compile fait croire qu'une fonction existe alors qu'elle n'est
pas branchée. C'est exactement ce qui avait motivé la suppression des deux
services de saisie morts en 10.4.2 (voir `CLAUDE.md`).

## `BloomFilter.kt`

Structure compacte répondant à « ce mot existe-t-il ? », pour le correcteur
orthographique.

**Pourquoi elle dort.** Elle résout un problème que le créole n'a pas. Le
dictionnaire luxembourgeois compte environ 150 000 formes, parce que la langue
décline et conjugue : le filtre y remplace plusieurs mégaoctets par 175 Ko. Le
créole en compte 5 296, plus 668 mots français, et ne fléchit presque pas. Ses
chaînes pèsent 34 Ko ; le filtre correspondant ferait 7 Ko, soit 27 Ko gagnés
sur les 53 Mo mesurés en saisie, cinq centièmes de pour cent.

Surtout, il ne remplacerait pas le dictionnaire. `KreyolSpellCheckerService`
appelle `isKnownWord()`, que le filtre saurait faire, **et**
`getSpellingSuggestions()`, qui calcule une distance d'édition sur la liste
réelle des mots. Cette liste doit rester en mémoire : le filtre serait un ajout,
pas une économie. Il coûterait en plus environ 1 % de faux positifs, donc des
graphies fausses acceptées au hasard, dans une langue dont la norme
orthographique est déjà discutée.

**Il est de toute façon inutilisable en l'état** : son commentaire renvoie à
`Dictionnaires/bloom.py`, qui n'existe pas dans ce dépôt.

**Quand le reprendre.** Si le dictionnaire français passait de ses 668 mots à un
lexique complet, le calcul s'inverserait.

## `MotsEcartes.kt`

Liste des formes que l'application ne proposerait jamais d'elle-même, dans les
jeux et les suggestions automatiques. **Sa liste est vide**, et le fichier le
dit lui-même : la constituer demande un travail linguistique que personne n'a
fait, et `CLAUDE.md` note que cette liste n'a pas été portée en créole.

## `wuertriet/WuertrietModels.kt`

Modèles du jeu *Wuertriet*, le Wordle luxembourgeois. Le créole a déjà le sien,
*Mo an Karénaj*, dans le paquet `mokarenaj`. Reste de portage sans emploi.
