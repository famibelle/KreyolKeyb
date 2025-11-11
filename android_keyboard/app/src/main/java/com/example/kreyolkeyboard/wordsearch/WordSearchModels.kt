package com.example.kreyolkeyboard.wordsearch

/**
 * Classes de données pour le système de mots mêlés
 */

data class WordSearchPuzzle(
    val theme: String,
    val grid: Array<CharArray>,
    val words: List<WordSearchWord>,
    val gridSize: Int,
    val difficulty: WordSearchDifficulty
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WordSearchPuzzle

        if (theme != other.theme) return false
        if (!grid.contentDeepEquals(other.grid)) return false
        if (words != other.words) return false
        if (gridSize != other.gridSize) return false
        if (difficulty != other.difficulty) return false

        return true
    }

    override fun hashCode(): Int {
        var result = theme.hashCode()
        result = 31 * result + grid.contentDeepHashCode()
        result = 31 * result + words.hashCode()
        result = 31 * result + gridSize
        result = 31 * result + difficulty.hashCode()
        return result
    }
}

data class WordSearchWord(
    val word: String,
    val startRow: Int,
    val startCol: Int,
    val direction: WordDirection,
    var isFound: Boolean = false
)

enum class WordDirection {
    HORIZONTAL,
    VERTICAL,
    DIAGONAL_DOWN_RIGHT,
    DIAGONAL_DOWN_LEFT,
    HORIZONTAL_REVERSE,
    VERTICAL_REVERSE,
    DIAGONAL_UP_RIGHT,
    DIAGONAL_UP_LEFT
}

enum class WordSearchDifficulty {
    EASY,      // 6x6, 4 mots, horizontal/vertical seulement
    NORMAL,    // 8x8, 6 mots, + diagonales
    HARD,      // 10x10, 8 mots, toutes directions + mots inversés
    EXPERT     // 12x12, 10 mots, mots qui se croisent
}

/**
 * Thèmes de mots créoles disponibles
 */
object WordSearchThemes {
    
    val ANIMAUX = listOf(
        "krab", "kochon", "bèf", "chat", "chyen", 
        "kolibri", "malfini", "ti-nèg", "zanimo", "koq"
    )
    
    val FRUITS = listOf(
        "zanana", "korosòl", "mango", "papay", 
        "zaboka", "sitwon", "zorany", "figbanan", "kannèl"
    )
    
    val FAMILLE = listOf(
        "manman", "papa", "granmoun", "timoun", 
        "sè", "frè", "kouzen", "kouzin", "nènèn"
    )
    
    val COULEURS = listOf(
        "wouj", "vè", "jòn", "ble", "nwa", 
        "blan", "woz", "violè", "mawonn"
    )
    
    val METEO = listOf(
        "soley", "lapli", "van", "cyclone", 
        "chalè", "frè", "nouaj", "loraj", "koukou"
    )
    
    val CORPS = listOf(
        "tèt", "je", "bouch", "nen", "zòrèy", 
        "kou", "bra", "men", "janm", "pye"
    )
    
    val MAISON = listOf(
        "kay", "chanm", "kizin", "salon", 
        "lakou", "fenèt", "pòt", "twati", "galri"
    )
    
    val TRANSPORT = listOf(
        "machin", "bis", "moto", "bisiklèt", 
        "bato", "avyon", "kamyon", "taksì"
    )
    
    fun getThemeWords(theme: String): List<String> {
        return when (theme.lowercase()) {
            "animaux" -> ANIMAUX
            "fruits" -> FRUITS
            "famille" -> FAMILLE
            "couleurs" -> COULEURS
            "météo", "meteo" -> METEO
            "corps" -> CORPS
            "maison" -> MAISON
            "transport" -> TRANSPORT
            else -> ANIMAUX // Par défaut
        }
    }
    
    fun getThemeDisplayName(theme: String): String {
        return when (theme.lowercase()) {
            "animaux" -> "🐾 Animaux"
            "fruits" -> "🥭 Fruits"
            "famille" -> "👨‍👩‍👧‍👦 Famille"
            "couleurs" -> "🌈 Couleurs"
            "météo", "meteo" -> "🌤️ Météo"
            "corps" -> "👤 Corps Humain"
            "maison" -> "🏠 Maison"
            "transport" -> "🚗 Transport"
            else -> "🎯 Thème"
        }
    }
    
    fun getAllThemes(): List<String> {
        return listOf(
            "animaux", "fruits", "famille", "couleurs",
            "météo", "corps", "maison", "transport"
        )
    }
}