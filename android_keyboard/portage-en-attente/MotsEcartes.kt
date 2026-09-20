package com.example.kreyolkeyboard

/**
 * Formes que l'application ne propose jamais **d'elle-même**.
 *
 * Pour KreyolKeyb, cette liste est actuellement vide mais peut être remplie
 * avec des mots à exclure des jeux et des suggestions automatiques.
 *
 * **Ce n'est pas un filtre de saisie.** Ces mots restent dans le dictionnaire,
 * dans les suggestions, dans le correcteur et dans la recherche du dictionnaire.
 * Un clavier qui refuse des mots n'est pas neutre, il est cassé.
 */
object MotsEcartes {

    /**
     * Liste des mots à exclure (pour l'instant vide pour le créole).
     * Peut être remplie avec des mots inappropriés pour les jeux.
     */
    private val FORMES: Set<String> = emptySet()

    /** Vrai si l'application doit s'abstenir de proposer ce mot. */
    fun estEcarte(mot: String): Boolean =
        AccentTolerantMatcher.normalize(mot) in FORMES

    /**
     * Vrai si le mot relève du registre obscène.
     * Portée plus large que [estEcarte] : celui-ci ne régit que ce que
     * l'application montre d'elle-même, celui-là s'applique en plus aux
     * suggestions du clavier.
     */
    fun estGrossier(mot: String): Boolean = false

    /**
     * Vrai si une phrase ne doit pas être montrée.
     */
    fun phraseEcartee(phrase: String): Boolean = false
}
