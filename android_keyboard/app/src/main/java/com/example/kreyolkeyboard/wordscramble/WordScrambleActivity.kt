package com.example.kreyolkeyboard.wordscramble

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.kreyolkeyboard.R

class WordScrambleActivity : AppCompatActivity() {
    
    private lateinit var tvTimer: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvWordNumber: TextView
    private lateinit var gridScrambled: GridView
    private lateinit var gridAnswer: GridView
    private lateinit var btnValidate: Button
    private lateinit var btnSkip: Button
    private lateinit var btnHint: Button
    private lateinit var btnClear: Button
    private lateinit var progressBar: ProgressBar
    
    private lateinit var scrambledAdapter: ScrambledLettersAdapter
    private lateinit var answerAdapter: AnswerLettersAdapter
    
    private var currentWord: String = ""
    private var scrambledLetters: List<Char> = listOf()
    private val currentAnswer = mutableListOf<Char?>()
    private val selectedPositions = mutableListOf<Int>()
    
    private var gameWords: List<String> = listOf()
    private var currentWordIndex = 0
    private var score = 0
    private var difficulty = ScrambleDifficulty.NORMAL
    
    private var countDownTimer: CountDownTimer? = null
    private var timeRemaining = 30
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_scramble)
        
        initViews()
        setupDifficulty()
        startNewGame()
    }
    
    private fun initViews() {
        tvTimer = findViewById(R.id.tvTimer)
        tvScore = findViewById(R.id.tvScore)
        tvWordNumber = findViewById(R.id.tvWordNumber)
        gridScrambled = findViewById(R.id.gridScrambled)
        gridAnswer = findViewById(R.id.gridAnswer)
        btnValidate = findViewById(R.id.btnValidate)
        btnSkip = findViewById(R.id.btnSkip)
        btnHint = findViewById(R.id.btnHint)
        btnClear = findViewById(R.id.btnClear)
        progressBar = findViewById(R.id.progressBar)
        
        btnValidate.setOnClickListener { validateAnswer() }
        btnSkip.setOnClickListener { skipWord() }
        btnHint.setOnClickListener { showHint() }
        btnClear.setOnClickListener { clearAnswer() }
        
        // Clic sur les lettres mélangées
        gridScrambled.setOnItemClickListener { _, _, position, _ ->
            if (!selectedPositions.contains(position)) {
                addLetterToAnswer(position)
            }
        }
        
        // Clic sur la réponse pour retirer une lettre
        gridAnswer.setOnItemClickListener { _, _, position, _ ->
            removeLetterFromAnswer(position)
        }
    }
    
    private fun setupDifficulty() {
        // Récupérer la difficulté depuis l'intent ou utiliser NORMAL par défaut
        val difficultyName = intent.getStringExtra("difficulty") ?: "NORMAL"
        difficulty = ScrambleDifficulty.valueOf(difficultyName)
    }
    
    private fun startNewGame() {
        score = 0
        currentWordIndex = 0
        
        // Charger les mots depuis le dictionnaire
        gameWords = WordScrambleData.loadWords(this, difficulty)
        
        if (gameWords.isEmpty()) {
            Toast.makeText(this, "Erreur de chargement du dictionnaire", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        progressBar.max = gameWords.size
        loadNextWord()
    }
    
    private fun loadNextWord() {
        if (currentWordIndex >= gameWords.size) {
            endGame()
            return
        }
        
        currentWord = gameWords[currentWordIndex]
        scrambledLetters = WordScrambleData.scrambleWord(currentWord)
        
        currentAnswer.clear()
        selectedPositions.clear()
        repeat(currentWord.length) { currentAnswer.add(null) }
        
        // Mettre à jour les adaptateurs
        scrambledAdapter = ScrambledLettersAdapter(this, scrambledLetters)
        answerAdapter = AnswerLettersAdapter(this, currentAnswer)
        
        gridScrambled.adapter = scrambledAdapter
        gridAnswer.adapter = answerAdapter
        
        gridScrambled.numColumns = minOf(scrambledLetters.size, 5)
        gridAnswer.numColumns = minOf(currentWord.length, 5)
        
        // Mettre à jour l'interface
        tvWordNumber.text = "Mot ${currentWordIndex + 1}/${gameWords.size}"
        tvScore.text = "Score: $score"
        progressBar.progress = currentWordIndex
        
        // Démarrer le timer
        startTimer()
    }
    
    private fun startTimer() {
        countDownTimer?.cancel()
        timeRemaining = WordScrambleData.getTimeForDifficulty(difficulty)
        
        countDownTimer = object : CountDownTimer((timeRemaining * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = (millisUntilFinished / 1000).toInt()
                tvTimer.text = "⏱️ ${timeRemaining}s"
                
                // Changer la couleur si temps faible
                if (timeRemaining <= 5) {
                    tvTimer.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                } else {
                    tvTimer.setTextColor(resources.getColor(android.R.color.black, null))
                }
            }
            
            override fun onFinish() {
                tvTimer.text = "⏱️ 0s"
                Toast.makeText(this@WordScrambleActivity, "Temps écoulé!", Toast.LENGTH_SHORT).show()
                skipWord()
            }
        }.start()
    }
    
    private fun addLetterToAnswer(position: Int) {
        // Trouver la première position vide
        val emptyIndex = currentAnswer.indexOfFirst { it == null }
        if (emptyIndex != -1) {
            currentAnswer[emptyIndex] = scrambledLetters[position]
            selectedPositions.add(position)
            
            scrambledAdapter.markAsSelected(position)
            answerAdapter.updateLetters(currentAnswer)
            
            // Vérifier si le mot est complet
            if (currentAnswer.all { it != null }) {
                btnValidate.isEnabled = true
            }
        }
    }
    
    private fun removeLetterFromAnswer(position: Int) {
        if (position < currentAnswer.size && currentAnswer[position] != null) {
            currentAnswer[position] = null
            
            // Retirer aussi de selectedPositions
            if (position < selectedPositions.size) {
                selectedPositions.removeAt(position)
            }
            
            // Réorganiser currentAnswer pour enlever les trous
            val nonNullLetters = currentAnswer.filterNotNull().toMutableList()
            currentAnswer.clear()
            currentAnswer.addAll(nonNullLetters)
            repeat(currentWord.length - nonNullLetters.size) { currentAnswer.add(null) }
            
            scrambledAdapter.clearSelections()
            selectedPositions.forEachIndexed { index, pos ->
                if (index < selectedPositions.size) {
                    scrambledAdapter.markAsSelected(pos)
                }
            }
            
            answerAdapter.updateLetters(currentAnswer)
            btnValidate.isEnabled = false
        }
    }
    
    private fun validateAnswer() {
        val answer = currentAnswer.filterNotNull().joinToString("")
        
        if (answer.equals(currentWord, ignoreCase = true)) {
            // Bonne réponse!
            val timeBonus = timeRemaining * 10
            score += 100 + timeBonus
            
            countDownTimer?.cancel()
            
            Toast.makeText(this, "✅ Correct! +${100 + timeBonus} points", Toast.LENGTH_SHORT).show()
            
            currentWordIndex++
            loadNextWord()
        } else {
            // Mauvaise réponse
            Toast.makeText(this, "❌ Essaie encore!", Toast.LENGTH_SHORT).show()
            clearAnswer()
        }
    }
    
    private fun skipWord() {
        countDownTimer?.cancel()
        Toast.makeText(this, "Le mot était: $currentWord", Toast.LENGTH_SHORT).show()
        currentWordIndex++
        loadNextWord()
    }
    
    private fun showHint() {
        // Révéler la première lettre non placée
        val firstEmpty = currentAnswer.indexOfFirst { it == null }
        if (firstEmpty != -1) {
            val correctLetter = currentWord[firstEmpty]
            
            // Trouver cette lettre dans scrambledLetters
            val posInScrambled = scrambledLetters.indexOfFirst { 
                it == correctLetter && !selectedPositions.contains(scrambledLetters.indexOf(it))
            }
            
            if (posInScrambled != -1) {
                addLetterToAnswer(posInScrambled)
                score -= 20 // Pénalité pour l'indice
                tvScore.text = "Score: $score"
                Toast.makeText(this, "Indice utilisé (-20 points)", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun clearAnswer() {
        currentAnswer.clear()
        selectedPositions.clear()
        repeat(currentWord.length) { currentAnswer.add(null) }
        
        scrambledAdapter.clearSelections()
        answerAdapter.updateLetters(currentAnswer)
        btnValidate.isEnabled = false
    }
    
    private fun endGame() {
        countDownTimer?.cancel()
        
        AlertDialog.Builder(this)
            .setTitle("🎉 Partie terminée!")
            .setMessage("Score final: $score\nMots réussis: ${currentWordIndex}/${gameWords.size}")
            .setPositiveButton("Rejouer") { _, _ ->
                startNewGame()
            }
            .setNegativeButton("Quitter") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
