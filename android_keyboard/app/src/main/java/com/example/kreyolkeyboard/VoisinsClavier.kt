package com.example.kreyolkeyboard

/**
 * Quelles touches se touchent sur l'écran (v23.0.0, repris de LuxKeyb 37.2.0).
 *
 * Sur un téléphone, la faute de frappe la plus courante est le doigt qui tombe
 * sur la touche d'à côté. La distance d'édition seule ne le sait pas : deux
 * corrections à une lettre près se départageaient à la seule fréquence, même
 * quand la lettre de l'une touche celle qui a été tapée et que celle de l'autre
 * en est loin. Une correction qui ne fait que remplacer des lettres par leurs
 * voisines reçoit donc un bonus dans le classement (voir
 * [SuggestionEngine.calculateDictionaryScore]).
 *
 * Les voisines sont calculées depuis les rangées de [KeyboardLayoutManager]
 * ([KeyboardLayoutManager.RANGEE_1] à 3), jamais écrites à la main : une
 * rangée modifiée les recalcule d'elle-même. Les accents sont repliés
 * ([AccentTolerantMatcher.normalize]) des deux côtés, si bien qu'une lettre
 * dont seul l'accent diffère n'est ni pénalisée ni récompensée ici : c'est le
 * travail du passage normalisé.
 *
 * LuxKeyb a deux dispositions et calcule une table par disposition ; ce
 * clavier n'a que l'AZERTY créole, d'où une seule table.
 */
class VoisinsClavier private constructor(private val paires: Set<Int>) {

    fun sontVoisines(a: Char, b: Char): Boolean = cle(a, b) in paires

    /**
     * `true` quand [mot] ne diffère de [saisie] que par des lettres remplacées
     * par une voisine, à la même place, et qu'il en diffère au moins par une.
     */
    fun parVoisines(saisie: String, mot: String): Boolean {
        if (saisie.length != mot.length) return false
        var differences = 0
        for (i in saisie.indices) {
            val a = replie(saisie[i])
            val b = replie(mot[i])
            if (a == b) continue
            if (!sontVoisines(a, b)) return false
            differences++
        }
        return differences > 0
    }

    companion object {
        /** La table de l'AZERTY créole, calculée une fois. */
        val AZERTY: VoisinsClavier by lazy {
            calculer(listOf(
                KeyboardLayoutManager.RANGEE_1,
                KeyboardLayoutManager.RANGEE_2,
                KeyboardLayoutManager.RANGEE_3
            ))
        }

        private fun cle(a: Char, b: Char): Int = (a.code shl 16) or b.code

        private fun replie(c: Char): Char {
            val r = AccentTolerantMatcher.normalize(c.toString())
            return if (r.length == 1) r[0] else c.lowercaseChar()
        }

        /**
         * Centre de chaque lettre en largeurs de touche, ligne par ligne. Les
         * rangées n'ont pas toutes le même nombre d'unités (la troisième porte
         * ⇧ et ⌫, plus larges) mais chacune occupe toute la largeur de l'écran :
         * l'abscisse est donc rapportée à la largeur de sa rangée, puis
         * exprimée dans l'unité de la première.
         */
        internal fun calculer(rangees: List<Array<String>>): VoisinsClavier {
            val poids = { touche: String -> KeyboardLayoutManager.poidsTouche(touche).toDouble() }
            val unite = rangees.first().sumOf(poids)
            data class Touche(val lettre: Char, val x: Double, val rangee: Int)
            val touches = ArrayList<Touche>()
            rangees.forEachIndexed { r, rangee ->
                val total = rangee.sumOf(poids)
                var gauche = 0.0
                for (touche in rangee) {
                    val p = poids(touche)
                    // Une touche accentuée serait laissée de côté : repliée, elle
                    // prêterait ses voisines à la lettre de base. Aucune n'est
                    // dans ces trois rangées (é et è sont sur la quatrième).
                    val lettre = touche.singleOrNull()
                    if (lettre != null && lettre.isLetter() && replie(lettre) == lettre.lowercaseChar()) {
                        val centre = (gauche + p / 2) / total * unite
                        touches.add(Touche(lettre.lowercaseChar(), centre, r))
                    }
                    gauche += p
                }
            }
            val paires = HashSet<Int>()
            for (a in touches) for (b in touches) {
                if (a === b || kotlin.math.abs(a.rangee - b.rangee) > 1) continue
                // Une largeur de touche, plus une marge pour les rangées dont
                // les touches ne tombent pas pile l'une sous l'autre.
                if (kotlin.math.abs(a.x - b.x) > 1.05) continue
                val ra = replie(a.lettre)
                val rb = replie(b.lettre)
                if (ra != rb) paires.add(cle(ra, rb))
            }
            return VoisinsClavier(paires)
        }
    }
}
