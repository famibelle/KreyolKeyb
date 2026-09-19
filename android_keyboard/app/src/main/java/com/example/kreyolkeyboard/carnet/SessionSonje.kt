package com.example.kreyolkeyboard.carnet

/**
 * Une question posée : une carte, montrée par son mot seul.
 *
 * La révision est une **flashcard**, pour toutes les boîtes : le dos porte le
 * mot, on retourne la carte, on se note « Je savais » ou « Pas su ». Choix du
 * propriétaire, le 18 septembre 2026 : de la 22.0.0 à la 23.0.0, les boîtes 2
 * et plus faisaient taper le mot dans une phrase trouée, et c'est retiré.
 */
data class QuestionRevision(val contenu: ContenuCarte) {
    /** Le mot écrit au dos, et qui identifie la carte dans le carnet. */
    val mot: String get() = contenu.carte.forme
}

/** Ce que le joueur a répondu à une carte. */
enum class Verdict { EXACT, FAUX }

/**
 * Une session de révision : la file, la question courante, et le sort de chaque
 * carte.
 *
 * Toute la logique est ici et **aucune vue ne l'est**, pour la même raison que
 * `ChasseCroiseSession` : ce sont les règles qui se cassent en silence, pas les
 * pixels. `SonjeSessionTest` les rejoue.
 *
 * Deux règles qui ne se voient pas mais qui font la session :
 *
 * - **Un échec repasse une fois, en fin de session.** Sans cela on échoue sur un
 *   mot et on ne le revoit que le lendemain : la session n'aurait rien appris,
 *   elle aurait seulement constaté. Le repassage ne change pas la boîte (elle
 *   est déjà retombée à zéro au premier échec) : il existe pour que le joueur
 *   parte en ayant retrouvé le mot, pas pour rattraper le point perdu.
 * - **Une carte n'est jamais vue deux fois autrement.** Le plafond de la file
 *   vaut douze cartes ; les rejouer en boucle jusqu'à réussite ferait durer une
 *   session d'une minute et demie jusqu'à l'abandon.
 */
class SessionSonje(file: List<ContenuCarte>) {

    private val restantes: ArrayDeque<QuestionRevision> = ArrayDeque(file.map { QuestionRevision(it) })
    private val dejaRepassees = HashSet<String>()

    /** Cartes prévues au départ, repassages non comptés. */
    val total: Int = file.size

    var courante: QuestionRevision? = restantes.removeFirstOrNull()
        private set

    /** Formes réussies, dans l'ordre, le repassage d'un échec exclu. */
    val reussies: MutableList<String> = ArrayList()

    /** Formes ratées, dans l'ordre. Une forme ne peut y figurer qu'une fois. */
    val ratees: MutableList<String> = ArrayList()

    val fini: Boolean get() = courante == null

    /** Rang de la question courante, pour l'afficher « 3 / 12 ». */
    val rang: Int get() = (reussies.size + ratees.size + 1).coerceAtMost(total)

    /**
     * Enregistre la réponse à la question courante et passe à la suivante.
     *
     * Retourne vrai si la carte doit être notée dans le carnet, c'est-à-dire
     * hors repassage : un repassage a déjà été noté à son échec.
     */
    fun repondre(reussi: Boolean): Boolean {
        val q = courante ?: return false
        val forme = q.mot
        val repassage = forme in dejaRepassees

        if (!repassage) {
            if (reussi) reussies.add(forme) else ratees.add(forme)
            if (!reussi) {
                dejaRepassees.add(forme)
                // En fin de file, pas tout de suite : redemander le mot qu'on
                // vient de révéler ne teste que la mémoire de l'instant.
                restantes.addLast(q)
            }
        }

        courante = restantes.removeFirstOrNull()
        return !repassage
    }
}
