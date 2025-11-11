package com.example.kreyolkeyboard.wordsearch

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.GridView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.kreyolkeyboard.R
import android.util.Log

/**
 * Activity pour le jeu de mots mêlés créoles
 * Intégré avec le système de gamification existant
 */
class WordSearchActivity : AppCompatActivity() {
    
    private val TAG = "WordSearchActivity"
    
    private lateinit var gridView: GridView
    private lateinit var wordsListContainer: LinearLayout
    private lateinit var tvTheme: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvScore: TextView
    
    private var currentPuzzle: WordSearchPuzzle? = null
    private var startTime: Long = 0
    private var wordsFound = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_search)
        
        // Configuration de la barre d'action
        supportActionBar?.apply {
            title = "Mots Mêlés Kreyòl"
            setDisplayHomeAsUpEnabled(true)
        }
        
        initializeViews()
        setupButtons()
        generateNewPuzzle()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun initializeViews() {
        gridView = findViewById(R.id.wordSearchGrid)
        wordsListContainer = findViewById(R.id.wordsListContainer)
        tvTheme = findViewById(R.id.tvTheme)
        tvTimer = findViewById(R.id.tvTimer)
        tvScore = findViewById(R.id.tvScore)
    }
    
    private fun setupButtons() {
        findViewById<Button>(R.id.btnNewGame)?.setOnClickListener {
            generateNewPuzzle()
        }
        
        findViewById<Button>(R.id.btnHint)?.setOnClickListener {
            showHint()
        }
        
        findViewById<Button>(R.id.btnThemes)?.setOnClickListener {
            showThemeSelector()
        }
        
        findViewById<Button>(R.id.btnClose)?.setOnClickListener {
            finish()
        }
    }
    
    private fun generateNewPuzzle() {
        try {
            // Test rapide pour vérifier le système
            WordSearchTest.runBasicTest()
            
            // Générer une nouvelle grille avec des mots créoles (8x8 max)
            currentPuzzle = WordSearchGenerator.generatePuzzle(
                theme = getCurrentTheme(),
                gridSize = 8,
                difficulty = WordSearchDifficulty.NORMAL
            )
            
            // Afficher la grille
            displayPuzzle(currentPuzzle!!)
            
            // Réinitialiser le timer et le score
            startTime = System.currentTimeMillis()
            wordsFound = 0
            updateUI()
            
            Log.d(TAG, "Nouvelle grille générée: ${currentPuzzle?.words?.size} mots")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la génération: ${e.message}", e)
            showError("Impossible de générer une nouvelle grille")
        }
    }
    
    private fun displayPuzzle(puzzle: WordSearchPuzzle) {
        // Configurer l'adaptateur de la grille
        val adapter = WordSearchGridAdapter(this, puzzle)
        adapter.setOnWordFoundListener { word ->
            onWordFound(word)
        }
        gridView.adapter = adapter
        gridView.numColumns = puzzle.gridSize
        
        // Afficher la liste des mots à trouver
        displayWordsList(puzzle.words)
        
        tvTheme.text = "🎯 ${WordSearchThemes.getThemeDisplayName(puzzle.theme)}"
    }
    
    private fun displayWordsList(words: List<WordSearchWord>) {
        wordsListContainer.removeAllViews()
        
        words.forEach { word ->
            val wordView = TextView(this).apply {
                text = "📝 ${word.word.uppercase()}"
                textSize = 16f
                setPadding(16, 8, 16, 8)
                setTextColor(resources.getColor(android.R.color.white, null))
                setBackgroundResource(R.drawable.word_item_background)
                
                // Marquer comme trouvé si c'est le cas
                if (word.isFound) {
                    setTextColor(resources.getColor(android.R.color.holo_green_light, null))
                    text = "✅ ${word.word.uppercase()}"
                }
            }
            
            wordsListContainer.addView(wordView)
        }
    }
    
    private fun onWordFound(word: String) {
        wordsFound++
        
        // Mettre à jour la liste des mots
        currentPuzzle?.words?.find { it.word.equals(word, ignoreCase = true) }?.isFound = true
        displayWordsList(currentPuzzle?.words ?: emptyList())
        
        // Calculer les points (gamification)
        val points = calculatePoints(word)
        updateScore(points)
        
        // Vérifier si toutes les mots sont trouvés
        if (wordsFound >= (currentPuzzle?.words?.size ?: 0)) {
            onPuzzleCompleted()
        }
        
        Log.d(TAG, "Mot trouvé: $word (+$points points)")
    }
    
    private fun calculatePoints(word: String): Int {
        val basePoints = 10
        val lengthBonus = word.length * 2
        val timeBonus = if (getElapsedTimeSeconds() < 60) 5 else 0
        
        return basePoints + lengthBonus + timeBonus
    }
    
    private fun onPuzzleCompleted() {
        val totalTime = getElapsedTimeSeconds()
        val totalPoints = calculateFinalScore(totalTime)
        
        // Sauvegarder le score et l'XP
        saveGameResults(totalPoints, totalTime)
        
        // Afficher le message de félicitations
        showCompletionDialog(totalPoints, totalTime)
    }
    
    private fun saveGameResults(points: Int, timeSeconds: Int) {
        // TODO: Intégrer avec le système de gamification existant
        // - Ajouter les points XP
        // - Mettre à jour les statistiques
        // - Débloquer de nouveaux thèmes si nécessaire
    }
    
    private fun updateUI() {
        val elapsed = getElapsedTimeSeconds()
        tvTimer.text = "⏱️ ${formatTime(elapsed)}"
        tvScore.text = "🏆 $wordsFound/${currentPuzzle?.words?.size ?: 0}"
    }
    
    private fun getElapsedTimeSeconds(): Int {
        return ((System.currentTimeMillis() - startTime) / 1000).toInt()
    }
    
    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }
    
    private fun getCurrentTheme(): String {
        // Thèmes disponibles selon le niveau du joueur
        val themes = listOf(
            "animaux", "fruits", "famille", "couleurs", 
            "météo", "corps", "maison", "transport"
        )
        return themes.random()
    }
    
    private fun showHint() {
        // Afficher un indice pour le prochain mot
        currentPuzzle?.words?.find { !it.isFound }?.let { word ->
            val hint = "💡 Cherchez un mot de ${word.word.length} lettres"
            // TODO: Afficher dans un toast ou un dialog
        }
    }
    
    private fun showThemeSelector() {
        // TODO: Afficher un sélecteur de thèmes
    }
    
    private fun updateScore(points: Int) {
        // TODO: Animer l'ajout de points
    }
    
    private fun calculateFinalScore(timeSeconds: Int): Int {
        // Score final basé sur le temps et les mots trouvés
        val baseScore = wordsFound * 50
        val timeBonus = maxOf(0, 300 - timeSeconds) // Bonus si < 5 minutes
        return baseScore + timeBonus
    }
    
    private fun showCompletionDialog(points: Int, timeSeconds: Int) {
        // TODO: Afficher dialog de félicitations avec partage
    }
    
    private fun showError(message: String) {
        // TODO: Afficher message d'erreur
    }
}