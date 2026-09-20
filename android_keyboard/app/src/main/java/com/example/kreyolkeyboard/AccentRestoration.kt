package com.example.kreyolkeyboard

/**
 * Rétablit les accents d'un mot tapé sans accent, et rien d'autre.
 *
 * C'est la seule correction automatique du clavier, et elle est bornée à dessein.
 * Écrire « kreyol » ou « pale » sans chercher l'accent est le geste le plus courant
 * de qui écrit vite, et le dictionnaire sait presque toujours quelle graphie
 * accentuée est visée : sur 2 662 groupes de mots qui ne diffèrent que par leurs
 * accents, 2 515 n'ont qu'une seule graphie accentuée attestée.
 *
 * ### Ce que la règle s'interdit
 *
 * Elle **ne remplace jamais un mot par un autre**. Une correction automatique
 * classique choisit le mot le plus proche au sens de la distance d'édition : c'est
 * ce qui, dans l'essai du 19 septembre 2026, a changé `onpil` en `anpil`,
 * `vitman` en `vitamin` ou `kontré` en `montré` sur un autre clavier. Ici le mot
 * de départ et le mot d'arrivée ont exactement les mêmes lettres, aux accents
 * près : la seule chose qui change est ce que l'utilisateur n'a pas tapé.
 *
 * Elle ne touche pas non plus à une graphie où l'utilisateur a mis un accent, même
 * faux : quelqu'un qui tape « é » sait ce qu'il fait.
 *
 * ### Les cas où elle s'abstient
 *
 * - **Un mot du lexique français.** Le clavier est bilingue et `se`, `le` ou
 *   `de` sont aussi des mots kréyòl à accents (`sé`, `lè`, `dé`) : les corriger
 *   changerait du français correct.
 * - **Deux graphies accentuées rivales à peu près à égalité.** `pé` et `pè`
 *   coexistent dans le corpus (244 et 90 occurrences) : choisir entre elles est une
 *   question de norme orthographique, que ce clavier n'a pas à trancher. La graphie
 *   majoritaire ne l'emporte que si elle domine d'au moins cinq fois la suivante.
 *   Une rivale vue moins de trois fois fait exception : à ce volume, c'est une
 *   coquille du corpus et non une norme concurrente, et elle est écartée quand la
 *   graphie de tête est vue au moins quatre fois et la double (`dòktè`, 2, contre
 *   `doktè`, 4).
 * - **La forme sans accent est elle-même attestée et porte un sens propre.** Elle
 *   n'est corrigée que si la graphie accentuée la domine d'au moins huit fois,
 *   faute de quoi l'utilisateur a peut-être écrit exactement ce qu'il voulait.
 *   « Une forme nue qui porte un sens » se reconnaît à deux signes : elle est
 *   glosée dans la table livrée (`bo`, le baiser, en face de `bò`, le côté), ou
 *   elle ne fait que deux lettres, longueur où les homographes se concentrent
 *   (`mo` le mot, `zo` l'os, `ko`, `fo`, `es`). La table de gloses ne couvre que
 *   622 mots : la longueur rattrape ce qu'elle ne dit pas.
 *
 *   Hors de ces deux cas, une forme nue de trois lettres ou plus est une graphie
 *   sans accents plutôt qu'un mot : `bef` en face de `bèf` (13 contre 7),
 *   `siklon` en face de `siklòn` (8 contre 7). Il suffit alors que la graphie
 *   accentuée soit la plus fréquente, et vue au moins quatre fois. Ce plancher
 *   écarte les cas trop minces pour conclure, où le français risquerait d'y
 *   passer : `grandè` vu 3 fois ne supplante pas « grande », ni `sitè` vu 2 fois
 *   « site ».
 * - **Un mot trop court ou trop rare.** Un seul caractère, ou une graphie vue moins
 *   de deux fois dans le corpus (probable coquille), ne justifie pas de réécrire.
 *
 * Cette logique ne connaît ni le dictionnaire ni Android : elle reçoit le groupe de
 * graphies à comparer, ce qui la rend testable sans contexte
 * (voir `AccentRestorationTest`).
 */
object AccentRestoration {

    /** En dessous, un mot est trop ambigu pour qu'on le réécrive. */
    const val LONGUEUR_MINIMALE = 2

    /** Une graphie accentuée vue moins souvent dans le corpus est écartée. */
    const val FREQUENCE_MINIMALE = 2

    /** Avance requise de la graphie accentuée majoritaire sur la suivante. */
    const val DOMINANCE_ENTRE_ACCENTUEES = 5

    /** Avance requise de la graphie accentuée sur la forme sans accent attestée. */
    const val DOMINANCE_SUR_LA_FORME_NUE = 8

    /**
     * En dessous, une graphie accentuée rivale est tenue pour une coquille du
     * corpus plutôt que pour une norme concurrente.
     */
    const val FREQUENCE_COQUILLE = 3

    /** Avance requise sur une rivale assez rare pour être une coquille. */
    const val DOMINANCE_SUR_LA_COQUILLE = 2

    /**
     * Vue moins souvent, une graphie accentuée ne supplante ni une rivale rare ni
     * une forme nue attestée : le corpus n'en dit pas assez pour trancher.
     */
    const val FREQUENCE_POUR_SUPPLANTER = 4

    /**
     * En deçà, une forme sans accent est tenue pour un mot à part entière même
     * sans glose : `mo`, `bo`, `zo`, `ko`, `fo`, `es`.
     */
    const val LONGUEUR_FORME_NUE_MUETTE = 3

    /**
     * La graphie à substituer à [tape], ou `null` s'il faut laisser le mot tel quel.
     *
     * @param tape le mot tel que tapé, dans sa casse d'origine
     * @param groupe les graphies du dictionnaire qui ne diffèrent de [tape] que par
     *        leurs accents, chacune avec sa fréquence dans le corpus
     * @param estMotFrancais vrai si [tape] figure au lexique français du clavier
     * @param nueAUnSensPropre vrai si la forme sans accent est elle-même glosée
     */
    fun choisir(
        tape: String,
        groupe: List<Pair<String, Int>>,
        estMotFrancais: Boolean,
        nueAUnSensPropre: Boolean = false
    ): String? {
        if (tape.length < LONGUEUR_MINIMALE || estMotFrancais) return null
        if (!tape.all { it.isLetter() }) return null

        val minuscule = tape.lowercase()
        // Un accent tapé est un choix : on ne le réécrit pas, même s'il est faux.
        if (AccentTolerantMatcher.hasAccents(minuscule)) return null

        var accentuees = groupe
            .filter { AccentTolerantMatcher.hasAccents(it.first) && it.second >= FREQUENCE_MINIMALE }
            .sortedByDescending { it.second }
        val meilleure = accentuees.firstOrNull() ?: return null

        if (accentuees.size > 1 && meilleure.second >= FREQUENCE_POUR_SUPPLANTER) {
            accentuees = listOf(meilleure) + accentuees.drop(1).filterNot { rivale ->
                rivale.second < FREQUENCE_COQUILLE &&
                    meilleure.second >= DOMINANCE_SUR_LA_COQUILLE * rivale.second
            }
        }

        if (accentuees.size > 1 &&
            meilleure.second < DOMINANCE_ENTRE_ACCENTUEES * accentuees[1].second
        ) return null

        val frequenceNue = groupe.firstOrNull { it.first == minuscule }?.second ?: 0
        if (frequenceNue > 0) {
            val nueMuette = minuscule.length >= LONGUEUR_FORME_NUE_MUETTE &&
                !nueAUnSensPropre &&
                meilleure.second >= FREQUENCE_POUR_SUPPLANTER
            if (nueMuette) {
                if (meilleure.second <= frequenceNue) return null
            } else if (meilleure.second < DOMINANCE_SUR_LA_FORME_NUE * frequenceNue) {
                return null
            }
        }

        return SuggestionEngine.applyCasingPattern(tape, meilleure.first)
    }
}
