package com.example.kreyolkeyboard.gamification

import android.content.Context
import android.util.Log
import com.example.kreyolkeyboard.gamification.WordUsageStats
import com.example.kreyolkeyboard.gamification.VocabularyStats
import org.json.JSONObject
import java.io.File

/**
 * Gestionnaire du dictionnaire créole avec tracking d'utilisation utilisateur
 * 
 * RESPECT DE LA VIE PRIVÉE :
 * - Seuls les mots qui existent dans le dictionnaire créole sont trackés
 * - Les mots personnels, mots de passe, etc. sont automatiquement ignorés
 * - Aucun texte complet n'est stocké, seulement les compteurs par mot du dictionnaire
 * - Toutes les données restent sur l'appareil (pas de synchronisation cloud)
 * 
 * Structure des données :
 * {
 *   "bonjou": {"frequency": 450, "user_count": 127},
 *   "kréyòl": {"frequency": 89, "user_count": 45},
 *   ...
 * }
 */
class CreoleDictionaryWithUsage(private val context: Context) {
    
    companion object {
        private const val TAG = "CreoleDictUsage"
        private const val DICT_FILE = "creole_dict_with_usage.json"
        private const val ORIGINAL_DICT = "creole_dict.json"
        private const val MIN_WORD_LENGTH = 3  // Ignorer les mots < 3 lettres
        private const val SAVE_BATCH_SIZE = 10  // Sauvegarder toutes les 10 utilisations
    }
    
    private var dictionary: JSONObject = JSONObject()
    private var unsavedChanges = 0  // Compteur pour sauvegarde par batch
    
    init {
        loadDictionary()
    }
    
    /**
     * Charge le dictionnaire (avec migration automatique si nécessaire)
     */
    private fun loadDictionary() {
        val file = File(context.filesDir, DICT_FILE)
        
        dictionary = if (file.exists()) {
            // Charger le dictionnaire existant avec compteurs
            Log.d(TAG, "📖 Chargement du dictionnaire existant avec compteurs...")
            JSONObject(file.readText())
        } else {
            // Première utilisation : migrer le dictionnaire original
            Log.d(TAG, "🔄 Première utilisation - Migration du dictionnaire...")
            migrateDictionary()
        }
        
        Log.d(TAG, "✅ Dictionnaire chargé : ${dictionary.length()} mots")
    }
    
    /**
     * Migre le dictionnaire original en ajoutant les compteurs user_count
     * Le dictionnaire original est un array: [["mot", frequency], ...]
     */
    private fun migrateDictionary(): JSONObject {
        val migratedDict = JSONObject()
        
        try {
            // Charger le dictionnaire original depuis les assets
            val json = context.assets.open(ORIGINAL_DICT)
                .bufferedReader()
                .use { it.readText() }
            val originalArray = org.json.JSONArray(json)
            
            // Transformer chaque entrée du array en objet
            var count = 0
            for (i in 0 until originalArray.length()) {
                val entry = originalArray.getJSONArray(i)
                val word = entry.getString(0)
                val frequency = entry.getInt(1)
                
                // Créer la nouvelle structure avec user_count à 0
                val wordData = JSONObject().apply {
                    put("frequency", frequency)
                    put("user_count", 0)
                }
                
                migratedDict.put(word, wordData)
                count++
            }
            
            // Sauvegarder le dictionnaire migré
            saveDictionaryToFile(migratedDict)
            Log.d(TAG, "✅ Migration réussie : $count mots transformés depuis array")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de la migration du dictionnaire", e)
        }
        
        return migratedDict
    }
    
    /**
     * Incrémente le compteur d'utilisation d'un mot
     * 
     * @param word Le mot tapé par l'utilisateur
     * @return true si le mot a été tracké, false sinon (mot ignoré)
     */
    fun incrementWordUsage(word: String): Boolean {
        // Normalisation basique (lowercase + trim)
        val normalized = word.lowercase().trim()
        
        // Filtres de sécurité et vie privée
        if (!isValidForTracking(normalized)) {
            Log.d(TAG, "🔒 Mot ignoré (filtres de sécurité): '$normalized'")
            return false
        }
        
        // Vérifier que le mot existe dans le dictionnaire créole
        return if (dictionary.has(normalized)) {
            val wordData = dictionary.getJSONObject(normalized)
            val currentCount = wordData.getInt("user_count")
            wordData.put("user_count", currentCount + 1)
            
            unsavedChanges++
            Log.d(TAG, "✅ '$normalized' utilisé ${currentCount + 1} fois")
            
            // Sauvegarde par batch pour performance
            if (unsavedChanges >= SAVE_BATCH_SIZE) {
                saveDictionary()
                unsavedChanges = 0
            }
            
            true
        } else {
            Log.d(TAG, "🔒 '$normalized' ignoré (pas dans le dictionnaire créole)")
            false
        }
    }
    
    /**
     * Valide si un mot peut être tracké (filtres de vie privée)
     */
    private fun isValidForTracking(word: String): Boolean {
        // Ignorer les mots trop courts (< 3 lettres)
        if (word.length < MIN_WORD_LENGTH) {
            return false
        }
        
        // Ignorer les mots contenant des chiffres (potentiellement des codes/mots de passe)
        if (word.any { it.isDigit() }) {
            return false
        }
        
        // Ignorer les URLs
        if (word.contains("http") || word.contains("www") || word.contains(".com")) {
            return false
        }
        
        // Ignorer les emails
        if (word.contains("@")) {
            return false
        }
        
        return true
    }
    
    /**
     * Obtient le nombre d'utilisations d'un mot
     */
    fun getWordUsageCount(word: String): Int {
        val normalized = word.lowercase().trim()
        return if (dictionary.has(normalized)) {
            dictionary.getJSONObject(normalized).getInt("user_count")
        } else {
            0
        }
    }
    
    /**
     * Obtient la fréquence corpus d'un mot
     */
    fun getWordFrequency(word: String): Int {
        val normalized = word.lowercase().trim()
        return if (dictionary.has(normalized)) {
            dictionary.getJSONObject(normalized).getInt("frequency")
        } else {
            0
        }
    }
    
    /**
     * Calcule le pourcentage de couverture du dictionnaire
     */
    fun getCoveragePercentage(): Float {
        var wordsUsed = 0
        val totalWords = dictionary.length()
        
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = dictionary.getJSONObject(word)
            if (wordData.getInt("user_count") > 0) {
                wordsUsed++
            }
        }
        
        return if (totalWords > 0) {
            (wordsUsed.toFloat() / totalWords) * 100
        } else {
            0f
        }
    }
    
    /**
     * Obtient le nombre de mots découverts (utilisés au moins 1 fois)
     */
    fun getDiscoveredWordsCount(): Int {
        var count = 0
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = dictionary.getJSONObject(word)
            if (wordData.getInt("user_count") > 0) {
                count++
            }
        }
        return count
    }
    
    /**
     * Obtient le nombre total d'utilisations (somme de tous les compteurs)
     */
    fun getTotalUsageCount(): Int {
        var total = 0
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = dictionary.getJSONObject(word)
            total += wordData.getInt("user_count")
        }
        return total
    }
    
    /**
     * Obtient les mots les plus utilisés
     */
    fun getTopUsedWords(limit: Int = 10): List<WordUsageStats> {
        val wordStats = mutableListOf<WordUsageStats>()
        
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = dictionary.getJSONObject(word)
            val userCount = wordData.getInt("user_count")
            
            if (userCount > 0) {
                wordStats.add(
                    WordUsageStats(
                        word = word,
                        userCount = userCount,
                        frequency = wordData.getInt("frequency")
                    )
                )
            }
        }
        
        return wordStats
            .sortedByDescending { it.userCount }
            .take(limit)
    }
    
    /**
     * Obtient les mots récemment découverts (utilisés 1-3 fois)
     */
    fun getRecentlyDiscoveredWords(limit: Int = 5): List<String> {
        val recentWords = mutableListOf<String>()
        
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = dictionary.getJSONObject(word)
            val userCount = wordData.getInt("user_count")
            
            if (userCount in 1..3) {
                recentWords.add(word)
            }
        }
        
        return recentWords.take(limit)
    }
    
    /**
     * Obtient le nombre de mots maîtrisés (utilisés 10+ fois)
     */
    fun getMasteredWordsCount(): Int {
        var count = 0
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = dictionary.getJSONObject(word)
            if (wordData.getInt("user_count") >= 10) {
                count++
            }
        }
        return count
    }
    
    /**
     * Obtient les statistiques complètes du vocabulaire
     */
    fun getVocabularyStats(): VocabularyStats {
        return VocabularyStats(
            coveragePercentage = getCoveragePercentage(),
            wordsDiscovered = getDiscoveredWordsCount(),
            totalWords = dictionary.length(),
            totalUsages = getTotalUsageCount(),
            topWords = getTopUsedWords(),
            recentWords = getRecentlyDiscoveredWords(),
            masteredWords = getMasteredWordsCount()
        )
    }
    
    /**
     * Sauvegarde le dictionnaire sur le disque
     */
    fun saveDictionary() {
        try {
            saveDictionaryToFile(dictionary)
            Log.d(TAG, "💾 Dictionnaire sauvegardé (${unsavedChanges} changements)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors de la sauvegarde", e)
        }
    }
    
    /**
     * Sauvegarde un objet JSON dans le fichier du dictionnaire
     */
    private fun saveDictionaryToFile(dict: JSONObject) {
        val file = File(context.filesDir, DICT_FILE)
        file.writeText(dict.toString(2))  // Indent de 2 pour lisibilité
    }
    
    /**
     * Reset tous les compteurs utilisateur (pour debug/testing uniquement)
     */
    fun resetAllUserCounts() {
        val keys = dictionary.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val wordData = dictionary.getJSONObject(word)
            wordData.put("user_count", 0)
        }
        saveDictionary()
        unsavedChanges = 0
        Log.d(TAG, "🔄 Tous les compteurs réinitialisés")
    }
    
    /**
     * Appelé quand l'app se termine pour sauvegarder les changements non sauvegardés
     */
    fun onDestroy() {
        if (unsavedChanges > 0) {
            saveDictionary()
            Log.d(TAG, "💾 Sauvegarde finale (${unsavedChanges} changements non sauvegardés)")
        }
    }
}
