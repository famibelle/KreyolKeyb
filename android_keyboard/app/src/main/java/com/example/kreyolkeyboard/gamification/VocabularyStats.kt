package com.example.kreyolkeyboard.gamification

/**
 * Statistiques globales du vocabulaire créole de l'utilisateur
 * 
 * Note: Le système de niveaux de gamification est maintenant centralisé
 * dans SettingsActivity.kt avec la distribution gaussienne et les noms
 * culturels créoles (Pipirit, Ti moun, Débrouya, An mitan, Kompè Lapen,
 * Kompè Zamba, Potomitan, Benzo).
 */
data class VocabularyStats(
    val coveragePercentage: Float,              // Pourcentage du dictionnaire utilisé (0-100)
    val wordsDiscovered: Int,                   // Nombre de mots différents utilisés au moins 1 fois
    val totalWords: Int,                        // Nombre total de mots dans le dictionnaire
    val totalUsages: Int,                       // Somme de tous les compteurs d'utilisation
    val topWords: List<WordUsageStats>,         // Top 10 des mots les plus utilisés
    val recentWords: List<String>,              // Mots récemment découverts (utilisés 1-3 fois)
    val masteredWords: Int                      // Nombre de mots maîtrisés (utilisés 10+ fois)
)
