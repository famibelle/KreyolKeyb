package com.example.kreyolkeyboard

import android.text.InputType

/**
 * Ce que l'application demande d'écrire dans un champ, ramené aux cas qui
 * changent le comportement du clavier.
 *
 * Jusqu'ici le clavier ne regardait le type de champ que pour deux choses : ne
 * rien retenir d'un mot de passe, et ne pas mettre de majuscule automatique dans un
 * champ numérique. Le reste était identique partout : un numéro de téléphone
 * s'ouvrait sur les lettres, une adresse électronique n'avait pas d'arobase sans
 * passer par « 123 », et des suggestions kréyòl s'affichaient sous un mot de
 * passe.
 *
 * La classification ne dépend que de `inputType` : pure, donc testable sans
 * éditeur (voir `FieldKindTest`).
 */
enum class FieldKind {
    /** Texte courant : message, note, recherche. Le seul cas où l'on écrit du kréyòl. */
    TEXT,

    /** Nom de personne : pas de correction, un nom n'est pas un mot du dictionnaire. */
    PERSON_NAME,

    /** Adresse électronique : l'arobase et le tiret bas passent sur les lettres. */
    EMAIL,

    /** Adresse web : la barre oblique et le tiret bas passent sur les lettres. */
    URI,

    /** Nombre : le pavé de chiffres s'ouvre en premier. */
    NUMBER,

    /** Numéro de téléphone : le pavé de chiffres s'ouvre en premier. */
    PHONE,

    /** Date ou heure : le pavé de chiffres s'ouvre en premier. */
    DATETIME,

    /** Mot de passe, code secret : rien de suggéré, rien de corrigé. */
    PASSWORD;

    /** Les champs qui s'ouvrent sur les chiffres plutôt que sur les lettres. */
    val ouvreLesChiffres: Boolean
        get() = this == NUMBER || this == PHONE || this == DATETIME

    /** Les champs où la barre de suggestions a un sens. */
    val proposeDesMots: Boolean
        get() = this == TEXT || this == PERSON_NAME

    /** Les champs où la majuscule de début de phrase serait une erreur. */
    val interditLaMajusculeAuto: Boolean
        get() = this != TEXT && this != PERSON_NAME

    /**
     * Les champs où corriger ce qui a été tapé est légitime : du texte écrit en
     * langue, pas un identifiant, une adresse ou un nom propre.
     */
    val autoriseLesCorrections: Boolean
        get() = this == TEXT

    companion object {
        /**
         * Classe un champ d'après son `inputType`.
         *
         * Un `TYPE_NUMBER_VARIATION_PASSWORD` (code PIN) est un mot de passe avant
         * d'être un nombre : la variation prime sur la classe.
         */
        fun de(inputType: Int): FieldKind {
            val classe = classeDe(inputType)
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            return when (classe) {
                InputType.TYPE_CLASS_NUMBER ->
                    if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) PASSWORD else NUMBER
                InputType.TYPE_CLASS_PHONE -> PHONE
                InputType.TYPE_CLASS_DATETIME -> DATETIME
                InputType.TYPE_CLASS_TEXT -> when (variation) {
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> EMAIL
                    InputType.TYPE_TEXT_VARIATION_URI -> URI
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> PASSWORD
                    InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> PERSON_NAME
                    else -> TEXT
                }
                else -> TEXT
            }
        }

        /**
         * Classe du champ, en lisant comme du texte un champ qui porte des
         * variations ou des drapeaux de texte sans déclarer de classe. Google
         * Contacts déclare ainsi son prénom `0x2060` (nom de personne + majuscule
         * à chaque mot, classe 0) : lu à la lettre, ce ne serait pas un nom et il
         * perdrait sa majuscule. Seul un champ à 0, sans rien (un terminal),
         * reste `TYPE_NULL`.
         */
        internal fun classeDe(inputType: Int): Int {
            val classe = inputType and InputType.TYPE_MASK_CLASS
            return if (classe == InputType.TYPE_NULL && inputType != 0) InputType.TYPE_CLASS_TEXT else classe
        }

        /**
         * La majuscule automatique a-t-elle sa place dans ce champ ?
         *
         * Non pour tout ce que [interditLaMajusculeAuto] exclut (mot de passe,
         * nombre, téléphone, date, adresse), ni pour un champ `TYPE_NULL` (un
         * terminal), un filtre de liste ou une saisie phonétique.
         *
         * Les champs web (`TYPE_TEXT_VARIATION_WEB_EDIT_TEXT`) sont ceux de Chrome
         * et des WebView : le type n'y dit rien, ce sont les drapeaux qui portent
         * la demande de la page (`autocapitalize`). Relevé sur Chrome : `0xc0a1`
         * par défaut (phrases), `0xa0a1` pour « words », `0x80a1` pour « off ».
         * On suit la page, y compris quand elle désactive les majuscules.
         */
        fun accepteLaMajusculeAuto(inputType: Int): Boolean {
            if (inputType == InputType.TYPE_NULL) return false
            if (de(inputType).interditLaMajusculeAuto) return false
            if (classeDe(inputType) != InputType.TYPE_CLASS_TEXT) return false
            return when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_FILTER,
                InputType.TYPE_TEXT_VARIATION_PHONETIC -> false
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT ->
                    inputType and (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                        InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                        InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0
                else -> true
            }
        }

        /**
         * L'éditeur a demandé qu'on ne propose ni ne corrige rien
         * (`TYPE_TEXT_FLAG_NO_SUGGESTIONS`), comme le font les champs de saisie
         * de code ou de recherche exacte.
         */
        fun refuseLesSuggestions(inputType: Int): Boolean =
            (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
                (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
    }
}

/**
 * Les trois aides à l'écriture, telles qu'appliquées dans le champ courant.
 *
 * Calculées une fois à la prise de focus (voir le service) à partir des réglages
 * de l'utilisateur et du type de champ, puis lues par le processeur de saisie à
 * chaque frappe sans rien recalculer.
 *
 * @property restaurerLesAccents rétablit les accents d'un mot tapé sans accent
 * @property doubleEspaceEnPoint deux espaces de suite après un mot posent un point
 * @property retirerEspaceAuto la ponctuation retire l'espace posé par une suggestion
 */
data class AidesEcriture(
    val restaurerLesAccents: Boolean = false,
    val doubleEspaceEnPoint: Boolean = false,
    val retirerEspaceAuto: Boolean = false
) {
    companion object {
        /** Aucune aide : champs numériques, adresses, mots de passe. */
        val AUCUNE = AidesEcriture()

        /**
         * Les aides d'un champ, selon le type et selon les réglages.
         *
         * La correction est en outre coupée quand l'éditeur refuse les
         * suggestions. Le double espace et le retrait de l'espace automatique,
         * eux, ne corrigent rien de ce qui est tapé : ils ne dépendent que du type
         * de champ, pas de ce drapeau.
         */
        fun pour(
            champ: FieldKind,
            refuseLesSuggestions: Boolean,
            accents: Boolean,
            doubleEspace: Boolean,
            espaceAuto: Boolean
        ): AidesEcriture {
            if (!champ.proposeDesMots) return AUCUNE
            return AidesEcriture(
                restaurerLesAccents = accents && champ.autoriseLesCorrections && !refuseLesSuggestions,
                doubleEspaceEnPoint = doubleEspace,
                retirerEspaceAuto = espaceAuto
            )
        }
    }
}
