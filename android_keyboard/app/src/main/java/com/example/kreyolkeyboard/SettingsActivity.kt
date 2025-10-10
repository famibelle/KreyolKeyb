package com.example.kreyolkeyboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class SettingsActivity : AppCompatActivity() {
    private var currentTab = "home"
    private lateinit var contentContainer: FrameLayout
    private lateinit var tabBar: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Masquer la barre d'action (bandeau noir)
        supportActionBar?.hide()
        
        Log.d("SettingsActivity", "Création de l'activité principale Kréyòl Karukera")
        
        // Layout principal horizontal : contenu à gauche, tabs à droite
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        // Container pour le contenu (Accueil ou Statistiques)
        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        }
        
        // Créer la barre d'onglets verticale à droite
        tabBar = createTabBar()
        
        mainLayout.addView(contentContainer)
        mainLayout.addView(tabBar)
        
        setContentView(mainLayout)
        
        // Afficher l'accueil par défaut
        showHomeTab()
        
        Log.d("SettingsActivity", "Interface avec tabs créée avec succès")
    }
    
    private fun createTabBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(90, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            
            // Spacer du haut pour centrer verticalement
            addView(View(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            })
            
            // Tab Accueil
            addView(createTab("home", "🏠"))
            
            // Tab Statistiques
            addView(createTab("stats", "📊"))
            
            // Spacer du bas pour centrer verticalement
            addView(View(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            })
        }
    }
    
    private fun createTab(tabName: String, emoji: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            // Emoji du tab
            val label = TextView(this@SettingsActivity).apply {
                text = emoji
                textSize = 32f
                gravity = Gravity.CENTER
            }
            
            addView(label)
            
            // Indicateur orange si tab actif
            if (tabName == currentTab) {
                val indicator = View(this@SettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        6
                    ).apply {
                        topMargin = 8
                    }
                    setBackgroundColor(Color.parseColor("#FF8C00"))
                }
                addView(indicator)
            }
            
            setOnClickListener {
                when (tabName) {
                    "home" -> showHomeTab()
                    "stats" -> showStatsTab()
                }
            }
        }
    }
    
    private fun updateTabBar() {
        tabBar.removeAllViews()
        
        // Spacer du haut
        tabBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        })
        
        // Tabs
        tabBar.addView(createTab("home", "🏠"))
        tabBar.addView(createTab("stats", "📊"))
        
        // Spacer du bas
        tabBar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        })
    }
    
    private fun showHomeTab() {
        currentTab = "home"
        updateTabBar()
        
        contentContainer.removeAllViews()
        val scrollView = ScrollView(this)
        scrollView.addView(createHomeContent())
        contentContainer.addView(scrollView)
    }
    
    private fun showStatsTab() {
        currentTab = "stats"
        updateTabBar()
        
        contentContainer.removeAllViews()
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            isFillViewport = true  // Utilise tout l'espace disponible
        }
        scrollView.addView(createStatsContent())
        contentContainer.addView(scrollView)
    }
    
    private fun createHomeContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        // En-tête avec design Guadeloupe
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 32)
            setBackgroundColor(Color.parseColor("#0080FF"))
        }
        
        val logoImage = ImageView(this).apply {
            setImageResource(R.drawable.logo_potomitan)
            layoutParams = LinearLayout.LayoutParams(200, 80)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 8)
        }
        
        val appTitle = TextView(this).apply {
            text = "Klavyé Kréyòl Karukera 🇸🇷"
            textSize = 28f
            setTextColor(Color.parseColor("#F8F8FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        
        headerLayout.addView(logoImage)
        headerLayout.addView(appTitle)
        
        // Description principale
        val descriptionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.WHITE)
        }
        
        val missionTitle = TextView(this).apply {
            text = "🌟 Notre Mission"
            textSize = 20f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val missionText = TextView(this).apply {
            text = "Ce clavier a été spécialement conçu pour préserver et promouvoir le Kréyòl Guadeloupéen (Karukera). Il met à disposition de tous un outil moderne pour écrire dans notre belle langue créole avec :\n\n" +
                    "💡 Suggestions de mots en Kréyòl\n" +
                    "🔢 Mode numérique intégré\n" +
                    "🌈 Design aux couleurs de la Guadeloupe\n" +
                    "🇸🇷 Identité guadeloupéenne forte"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.2f)
        }
        
        descriptionCard.addView(missionTitle)
        descriptionCard.addView(missionText)
        
        // Instructions d'installation
        val installCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#E8F4FD"))
        }
        
        val installTitle = TextView(this).apply {
            text = "📱 Comment activer le clavier ?"
            textSize = 18f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        
        val installSteps = TextView(this).apply {
            text = "1️⃣ Appuyez sur 'Activer le clavier' ci-dessous\n" +
                    "2️⃣ Dans les paramètres, activez 'Klavyé Kréyòl Karukera'\n" +
                    "3️⃣ Revenez ici et testez le clavier\n" +
                    "4️⃣ Changez de clavier en appuyant sur l'icône clavier dans la barre de notifications"
            textSize = 15f
            setTextColor(Color.parseColor("#444444"))
            setLineSpacing(0f, 1.3f)
        }
        
        installCard.addView(installTitle)
        installCard.addView(installSteps)
        
        // Boutons d'action
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        
        val activateButton = Button(this).apply {
            text = "🔧 Activer le clavier dans les paramètres"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#0080FF"))
            setTextColor(Color.parseColor("#F8F8FF"))
            setPadding(20, 16, 20, 16)
            setOnClickListener {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                startActivity(intent)
            }
        }
        
        val testTitle = TextView(this).apply {
            text = "✍️ Zone de test du clavier"
            textSize = 18f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        }
        
        val testDescription = TextView(this).apply {
            text = "Tapez dans le champ ci-dessous pour tester le clavier Kréyòl :"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        
        val testEditText = EditText(this).apply {
            hint = "Ékri an Kréyòl la... (Écrivez en créole...)"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            minHeight = 120
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.parseColor("#1C1C1C"))
            setHintTextColor(Color.parseColor("#999999"))
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(8, 8, 8, 8)
            this.layoutParams = layoutParams
        }
        
        val switchButton = Button(this).apply {
            text = "🔄 Basculer vers Klavyé Kréyòl"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#228B22"))
            setTextColor(Color.parseColor("#F8F8FF"))
            setPadding(16, 12, 16, 12)
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        
        // Section Sources littéraires
        val sourcesCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F0F8E8"))
        }
        
        val sourcesTitle = TextView(this).apply {
            text = "📚 Sources littéraires créoles"
            textSize = 18f
            setTextColor(Color.parseColor("#228B22"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        
        val sourcesText = TextView(this).apply {
            text = "Les suggestions de mots en Kréyòl sont construites sur les travaux des défenseurs du Kréyòl :\n\n" +
                    "✍️ Sylviane Telchid, Sonny Rupaire, Robert Fontes, Max Rippon, Alain Rutil, Alain Vérin, Katel, Esnard Boisdur, Pierre Édouard Décimus,\n\n" +
                    "Grâce à leur riche contributions, ce clavier vous propose des suggestions authentiques et fidèles à notre créole guadeloupéen."
            textSize = 14f
            setTextColor(Color.parseColor("#2F5233"))
            setLineSpacing(0f, 1.3f)
        }
        
        sourcesCard.addView(sourcesTitle)
        sourcesCard.addView(sourcesText)
        
        // Footer
        val footerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F8F9FA"))
        }
        
        val footerText = TextView(this).apply {
            text = "🏝️ Fait avec ❤️ pour la Guadeloupe\n" +
                    "Préservons notre langue créole pour les générations futures !\n\n" +
                    "© Potomitan™ - Clavier Kréyòl Karukera\n" +
                    "Design aux couleurs authentiques de nos îles"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }
        
        footerCard.addView(footerText)
        
        // Assembler
        buttonLayout.addView(activateButton)
        buttonLayout.addView(testTitle)
        buttonLayout.addView(testDescription)
        buttonLayout.addView(testEditText)
        buttonLayout.addView(switchButton)
        
        mainLayout.addView(headerLayout)
        mainLayout.addView(descriptionCard)
        mainLayout.addView(installCard)
        mainLayout.addView(buttonLayout)
        mainLayout.addView(sourcesCard)
        mainLayout.addView(footerCard)
        
        return mainLayout
    }
    
    private fun createStatsContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.WHITE)
        }
        
        val stats = loadVocabularyStats()
        
        // En-tête minimaliste
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 24)
            setBackgroundColor(Color.WHITE)
        }
        
        val headerTitle = TextView(this).apply {
            text = "Mon Kreyòl"
            textSize = 20f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        
        val separator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                topMargin = 24
            }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        
        header.addView(headerTitle)
        header.addView(separator)
        
        // Container principal
        val statsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
        }
        
        // === Niveau - Badge minimaliste ===
        val level = getCurrentLevel(stats.wordsDiscovered)
        val levelParts = level.split(" ")
        val levelEmoji = levelParts[0]
        val levelName = if (levelParts.size > 1) levelParts.drop(1).joinToString(" ") else ""
        
        val levelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 40)
        }
        
        val levelBadge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
        }
        
        val levelEmojiText = TextView(this).apply {
            text = levelEmoji
            textSize = 32f
            setPadding(0, 0, 12, 0)
        }
        
        val levelNameText = TextView(this).apply {
            text = levelName
            textSize = 18f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
        }
        
        levelBadge.addView(levelEmojiText)
        levelBadge.addView(levelNameText)
        
        val percentageText = TextView(this).apply {
            text = "${String.format("%.1f", stats.coveragePercentage)}%"
            textSize = 64f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        
        val percentageLabel = TextView(this).apply {
            text = "du dictionnaire exploré"
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        }
        
        levelContainer.addView(levelBadge)
        levelContainer.addView(percentageText)
        levelContainer.addView(percentageLabel)
        
        // === Mot du Jour - Design épuré ===
        val (wordOfDay, usageCount) = getWordOfTheDay()
        
        val wordContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 40, 24, 40)
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }
        
        val wordLabel = TextView(this).apply {
            text = "MOT DU JOUR"
            textSize = 12f
            setTextColor(Color.parseColor("#FF8C00"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
            setPadding(0, 0, 0, 16)
        }
        
        val wordText = TextView(this).apply {
            text = wordOfDay
            textSize = 48f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val wordUsage = TextView(this).apply {
            text = if (usageCount > 0) "utilisé $usageCount fois" else "nouveau mot à découvrir"
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        }
        
        wordContainer.addView(wordLabel)
        wordContainer.addView(wordText)
        wordContainer.addView(wordUsage)
        
        // === Top 5 - Liste simple ===
        val top5Container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 40)
        }
        
        val top5Title = TextView(this).apply {
            text = "Mots les plus utilisés"
            textSize = 16f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        
        top5Container.addView(top5Title)
        
        stats.topWords.take(5).forEachIndexed { index, word ->
            val wordRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 16)
            }
            
            val rank = TextView(this).apply {
                text = "${index + 1}."
                textSize = 16f
                setTextColor(Color.parseColor("#FF8C00"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 16, 0)
            }
            
            val wordName = TextView(this).apply {
                text = word.first
                textSize = 16f
                setTextColor(Color.parseColor("#1C1C1C"))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            val wordCount = TextView(this).apply {
                text = "${word.second}"
                textSize = 16f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.END
            }
            
            wordRow.addView(rank)
            wordRow.addView(wordName)
            wordRow.addView(wordCount)
            top5Container.addView(wordRow)
        }
        
        // === Statistiques - Grille 2x2 ===
        val statsGridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 40)
        }
        
        val statsGridTitle = TextView(this).apply {
            text = "Statistiques globales"
            textSize = 16f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        
        statsGridContainer.addView(statsGridTitle)
        
        // Ligne 1: Découverts | Total utilizations
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 32)
        }
        
        row1.addView(createStatBlock("${stats.wordsDiscovered}", "Mots découverts"))
        row1.addView(createStatBlock("${stats.totalUsages}", "Utilisations"))
        
        // Ligne 2: Maîtrisés | Dictionnaire
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        row2.addView(createStatBlock("${stats.masteredWords}", "Mots maîtrisés"))
        row2.addView(createStatBlock("${stats.totalWords}", "Dictionnaire"))
        
        statsGridContainer.addView(row1)
        statsGridContainer.addView(row2)
        
        // === Bouton rafraîchir ===
        val refreshButton = Button(this).apply {
            text = "⟳ Actualiser"
            textSize = 14f
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.parseColor("#1C1C1C"))
            setPadding(32, 16, 32, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.CENTER
            params.topMargin = 32
            layoutParams = params
            setOnClickListener {
                showStatsTab()
            }
        }
        
        // Assembler
        statsContainer.addView(levelContainer)
        statsContainer.addView(wordContainer)
        statsContainer.addView(top5Container)
        statsContainer.addView(statsGridContainer)
        statsContainer.addView(refreshButton)
        
        mainLayout.addView(header)
        mainLayout.addView(statsContainer)
        
        return mainLayout
    }
    
    private fun createStatBlock(number: String, label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            
            val numText = TextView(this@SettingsActivity).apply {
                text = number
                textSize = 36f
                setTextColor(Color.parseColor("#1C1C1C"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 8)
            }
            
            val labelText = TextView(this@SettingsActivity).apply {
                text = label
                textSize = 12f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.CENTER
            }
            
            addView(numText)
            addView(labelText)
        }
    }
    
    // === Fonctions de chargement de données ===
    
    data class VocabularyStats(
        val totalWords: Int,
        val wordsDiscovered: Int,
        val totalUsages: Int,
        val masteredWords: Int,
        val topWords: List<Pair<String, Int>>,
        val coveragePercentage: Float
    )
    
    private fun loadVocabularyStats(): VocabularyStats {
        return try {
            // D'abord essayer le fichier avec usage
            val usageFile = File(filesDir, "creole_dict_with_usage.json")
            
            if (usageFile.exists()) {
                val jsonString = usageFile.readText()
                val jsonObject = JSONObject(jsonString)
                
                var totalWords = 0
                var wordsDiscovered = 0
                var totalUsages = 0
                var masteredWords = 0
                val wordUsages = mutableListOf<Pair<String, Int>>()
                
                jsonObject.keys().forEach { word ->
                    totalWords++
                    // Lire l'objet avec frequency et user_count
                    val wordData = jsonObject.getJSONObject(word)
                    val userCount = wordData.optInt("user_count", 0)
                    
                    if (userCount > 0) {
                        wordsDiscovered++
                        totalUsages += userCount
                        wordUsages.add(Pair(word, userCount))
                        if (userCount >= 10) {
                            masteredWords++
                        }
                    }
                }
                
                val topWords = wordUsages.sortedByDescending { it.second }.take(5)
                val coverage = if (totalWords > 0) (wordsDiscovered.toFloat() / totalWords * 100) else 0f
                
                return VocabularyStats(
                    totalWords,
                    wordsDiscovered,
                    totalUsages,
                    masteredWords,
                    topWords,
                    coverage
                )
            }
            
            // Sinon charger depuis les assets et créer le fichier avec usage
            val jsonString = assets.open("creole_dict.json").bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            
            var totalWords = jsonArray.length()
            
            // Créer un fichier avec usage à 0 pour tous les mots
            val usageObject = JSONObject()
            for (i in 0 until jsonArray.length()) {
                val word = jsonArray.getString(i)
                usageObject.put(word, 0)
            }
            
            // Sauvegarder le fichier pour usage futur
            usageFile.writeText(usageObject.toString())
            
            VocabularyStats(
                totalWords = totalWords,
                wordsDiscovered = 0,
                totalUsages = 0,
                masteredWords = 0,
                topWords = emptyList(),
                coveragePercentage = 0f
            )
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur chargement stats: ${e.message}")
            VocabularyStats(0, 0, 0, 0, emptyList(), 0f)
        }
    }
    
    private fun getCurrentLevel(wordsDiscovered: Int): String {
        return when {
            wordsDiscovered >= 500 -> "👑 LÉGENDE"
            wordsDiscovered >= 300 -> "🌟 EXPERT"
            wordsDiscovered >= 150 -> "⭐ AVANCÉ"
            wordsDiscovered >= 75 -> "💎 INTERMÉDIAIRE"
            wordsDiscovered >= 30 -> "🔥 PROGRESSANT"
            wordsDiscovered >= 10 -> "🌱 APPRENTI"
            else -> "🌍 DÉBUTANT"
        }
    }
    
    private fun getWordOfTheDay(): Pair<String, Int> {
        return try {
            val usageFile = File(filesDir, "creole_dict_with_usage.json")
            
            val allWords: List<String>
            val usageCount: Int
            
            if (usageFile.exists()) {
                val jsonString = usageFile.readText()
                val jsonObject = JSONObject(jsonString)
                
                allWords = mutableListOf<String>().apply {
                    jsonObject.keys().forEach { word -> add(word) }
                }
                
                if (allWords.isEmpty()) {
                    return Pair("Bonjou", 0)
                }
                
                // Utiliser la date comme seed pour avoir le même mot toute la journée
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateString = dateFormat.format(Date())
                val seed = dateString.hashCode().toLong()
                val random = Random(seed)
                
                val selectedWord = allWords[random.nextInt(allWords.size)]
                // Lire le user_count depuis l'objet JSON
                val wordData = jsonObject.getJSONObject(selectedWord)
                usageCount = wordData.optInt("user_count", 0)
                
                return Pair(selectedWord, usageCount)
            } else {
                // Charger depuis les assets
                val jsonString = assets.open("creole_dict.json").bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(jsonString)
                
                allWords = mutableListOf<String>().apply {
                    for (i in 0 until jsonArray.length()) {
                        add(jsonArray.getString(i))
                    }
                }
                
                if (allWords.isEmpty()) {
                    return Pair("Bonjou", 0)
                }
                
                // Utiliser la date comme seed
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateString = dateFormat.format(Date())
                val seed = dateString.hashCode().toLong()
                val random = Random(seed)
                
                val selectedWord = allWords[random.nextInt(allWords.size)]
                
                return Pair(selectedWord, 0)
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur mot du jour: ${e.message}")
            Pair("Bonjou", 0)
        }
    }
}
