package com.example.kreyolkeyboard

/**
 * 🎯 AccentTolerantMatcher - Recherche insensible aux accents pour le créole guadeloupéen
 * 
 * Permet aux utilisateurs de taper "kre" et obtenir des suggestions pour "kréyòl"
 * Essentiel pour une expérience fluide du clavier créole sans chercher les accents
 * 
 * Fonctionnalités:
 * - Normalisation des accents créoles (à, é, è, ò, etc.)
 * - Recherche flexible dans le dictionnaire
 * - Support des caractères spéciaux guadeloupéens
 * 
 * @author Médhi Famibelle - Potomitan™
 */
object AccentTolerantMatcher {

    // Table char → char précalculée : normalize() est appelé sur le chemin chaud
    // (chaque frappe), toute compilation de Regex ici est interdite
    private val NORMALIZATION_MAP: Map<Char, Char> = buildMap {
        "àáâäãåāăą".forEach { put(it, 'a') }
        "èéêëēėęě".forEach { put(it, 'e') }
        "ìíîïīįĩ".forEach { put(it, 'i') }
        "òóôöõøōőœ".forEach { put(it, 'o') }
        "ùúûüūůũűų".forEach { put(it, 'u') }
        "ýÿŷ".forEach { put(it, 'y') }
        put('ç', 'c')
        put('ñ', 'n')
    }

    /**
     * Normalise une chaîne en supprimant tous les accents
     * Optimisé pour le créole guadeloupéen
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return text

        val result = StringBuilder(text.length)
        for (c in text) {
            val lower = c.lowercaseChar()
            if (lower == 'ß') {
                result.append("ss")
            } else {
                result.append(NORMALIZATION_MAP[lower] ?: lower)
            }
        }
        return result.toString()
    }
    
    /**
     * Vérifie si deux mots correspondent après normalisation des accents
     */
    fun matches(input: String, target: String): Boolean {
        return normalize(input) == normalize(target)
    }
    
    /**
     * Vérifie si un mot du dictionnaire commence par l'input normalisé
     */
    fun startsWith(input: String, dictionaryWord: String): Boolean {
        val normalizedInput = normalize(input)
        val normalizedWord = normalize(dictionaryWord)
        return normalizedWord.startsWith(normalizedInput)
    }
    
    /**
     * Vérifie si un mot contient des accents
     */
    fun hasAccents(word: String): Boolean {
        return word != normalize(word)
    }
}