package com.example.kreyolkeyboard

import android.graphics.Color

/**
 * Représente une suggestion avec sa langue et couleur
 */
data class BilingualSuggestion(
    val word: String,
    val score: Float,
    val language: SuggestionLanguage,
    val source: SuggestionSource = SuggestionSource.DICTIONARY
) {
    /**
     * Obtient la couleur associée à cette suggestion
     */
    fun getColor(): Int {
        return when (language) {
            SuggestionLanguage.KREYOL -> KeyboardColors.KREYOL_GREEN
            SuggestionLanguage.FRENCH -> KeyboardColors.FRENCH_BLUE
        }
    }
    
    /**
     * Obtient le nom lisible de la langue
     */
    fun getLanguageName(): String {
        return when (language) {
            SuggestionLanguage.KREYOL -> "Kreyòl"
            SuggestionLanguage.FRENCH -> "Français"
        }
    }

    /**
     * Micro-label affiché avant le mot dans la puce de suggestion (KR/FR)
     */
    fun getShortLabel(): String {
        return when (language) {
            SuggestionLanguage.KREYOL -> "KR"
            SuggestionLanguage.FRENCH -> "FR"
        }
    }
}

/**
 * Types de langues supportées
 */
enum class SuggestionLanguage {
    KREYOL,
    FRENCH
}

/**
 * Sources des suggestions
 */
enum class SuggestionSource {
    DICTIONARY,    // Dictionnaire statique
    NGRAM,        // Modèle N-gram
    LEARNED,      // Apprentissage utilisateur
    HYBRID        // Combinaison de sources
}

/**
 * Couleurs du clavier
 */
object KeyboardColors {
    // Ces trois couleurs ne figurent pas dans KeyboardTheme et n'ont pas à y
    // figurer : les puces de suggestion sont des pastilles pleines, lisibles
    // telles quelles sur le fond clair comme sur le fond sombre de la barre.
    // Tout ce qui bascule vit dans KeyboardTheme.
    //
    // Depuis la v17.0.0 elles ne colorent plus que la **première** proposition
    // de chaque rangée, le remplissage disant désormais le rang. Elles restent
    // la couleur de langue du clavier, sur un support réduit.

    // 🟢 Vert pour Guadeloupéen/Kreyòl (fond plein, texte blanc)
    //
    // Assombri en 17.0.0 : le #2E9E5B d'origine ne donnait que **3,41:1** avec
    // du blanc. Une puce se lit à 18 sp, ce qui est du texte courant au sens
    // WCAG et non du texte large, donc le seuil applicable est 4,5:1 et il
    // n'était pas atteint. La langue pour laquelle ce clavier existe avait
    // ainsi la puce la moins lisible des deux, le bleu français étant lui à
    // 4,93:1. Même teinte (H = 144°) à 85 % de valeur : 4,56:1, pour un écart
    // de couleur que personne ne voit.
    //
    // Le 2,71:1 documenté dans KeyboardTheme pour un glyphe sur le vert des
    // touches n'est pas comparable : un glyphe de touche est énorme et se
    // reconnaît au lieu de se lire.
    val KREYOL_GREEN = Color.parseColor("#27864D")

    // 🔵 Bleu pour Français (fond plein, texte blanc). 4,93:1, conforme.
    val FRENCH_BLUE = Color.parseColor("#3B6FC4")

    val CHIP_TEXT = Color.parseColor("#FFFFFF")           // Texte des puces (fond plein)

    // BACKGROUND_NEUTRAL, BORDER_LIGHT, TEXT_PRIMARY et TEXT_SECONDARY ont été
    // retirés avec l'arrivée du thème : les trois premiers n'avaient aucun
    // appelant, et le quatrième colorait l'étiquette de langue de la barre de
    // suggestions, qui lit désormais KeyboardTheme.palette().encreEtiquette, sans
    // quoi elle serait restée gris moyen sur le fond sombre.
}

/**
 * Configuration du mode bilingue
 */
data class BilingualConfig(
    val frenchActivationThreshold: Int = 3,        // Activer français à partir de 3 lettres
    val maxKreyolSuggestions: Int = 3,             // Maximum 3 suggestions kreyòl
    val maxFrenchSuggestions: Int = 2,             // Maximum 2 suggestions françaises
    val kreyolPriorityBoost: Float = 1.5f,         // Bonus score pour kreyòl (+50%)
    val frenchPenalty: Float = 0.8f,               // Malus pour français (-20%)
    val enableFrenchSupport: Boolean = true,       // Support français activé
    val kreyolOnlyMode: Boolean = false,           // Mode 100% kreyòl
    val showLanguageIndicators: Boolean = true      // Afficher couleurs langues
) {
    /**
     * Vérifie si le français doit être activé pour cette saisie
     */
    fun shouldActivateFrench(input: String): Boolean {
        return enableFrenchSupport && 
               !kreyolOnlyMode && 
               input.length >= frenchActivationThreshold
    }
    
    /**
     * Calcule le score ajusté selon la langue
     */
    fun adjustScoreByLanguage(score: Float, language: SuggestionLanguage): Float {
        return when (language) {
            SuggestionLanguage.KREYOL -> score * kreyolPriorityBoost
            SuggestionLanguage.FRENCH -> score * frenchPenalty
        }
    }
}