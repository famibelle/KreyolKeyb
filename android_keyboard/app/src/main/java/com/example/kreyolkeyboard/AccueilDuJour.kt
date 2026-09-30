package com.example.kreyolkeyboard

import com.example.kreyolkeyboard.gamification.CreoleLevels
import java.text.NumberFormat
import java.util.Locale

/** Ce que montre l'onglet Dékolaj. */
enum class ModeAccueil {
    /** Le parcours d'installation : le clavier n'est pas (ou plus) actif. */
    INSTALLATION,

    /** La journée : cartes à revoir, mot du jour, dernier jeu, progression. */
    DU_JOUR
}

/**
 * La logique de l'accueil de l'onglet Dékolaj qui ne dépend pas d'Android (22.5.0,
 * reprise de LuxKeyb 29.4.0), testée par `AccueilDuJourTest`. L'affichage est
 * dans `SettingsActivity`.
 */
object AccueilDuJour {

    /**
     * L'accueil du jour ne remplace l'installation qu'une fois celle-ci menée à
     * terme au moins une fois, et tant que le clavier est activé **et**
     * sélectionné. Un clavier désélectionné par une mise à jour ramène au
     * parcours, avec la carte « Revenir au clavier kréyòl » en tête.
     */
    fun mode(installationTermineeUneFois: Boolean, active: Boolean, selectionne: Boolean): ModeAccueil =
        if (installationTermineeUneFois && active && selectionne) ModeAccueil.DU_JOUR
        else ModeAccueil.INSTALLATION

    /** Entier avec le séparateur des milliers français : « 5 296 » et non « 5296 ». */
    fun nombre(n: Int): String = NumberFormat.getIntegerInstance(Locale.FRANCE).format(n)

    /**
     * « Encore N mots avant X », partagée par l'accueil et « Kréyòl an mwen ».
     *
     * X est le nom du palier suivant tel que [CreoleLevels] le donne, jamais un
     * nom inventé. Le huitième niveau reste un secret (« Un huitième existe : à
     * vous de le découvrir », dit l'astuce) : depuis Potomitan, la phrase
     * annonce « le huitième niveau » sans le nommer.
     */
    fun phraseProgression(motsDecouverts: Int, totalMots: Int): String {
        val index = CreoleLevels.indexFor(motsDecouverts, totalMots)
        if (index == CreoleLevels.MAX_INDEX) return "Vous avez atteint le plus haut niveau. 👑"
        val (suivant, reste) = CreoleLevels.nextLevelInfo(motsDecouverts, totalMots)
        val nom = if (index + 1 == CreoleLevels.MAX_INDEX) "le huitième niveau" else suivant
        return "Encore ${nombre(reste)} mot${if (reste > 1) "s" else ""} avant $nom"
    }
}
