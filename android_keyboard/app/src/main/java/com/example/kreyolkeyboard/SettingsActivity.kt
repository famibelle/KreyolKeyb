package com.example.kreyolkeyboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.random.Random
import com.example.kreyolkeyboard.wordsearch.WordSearchActivity
import com.example.kreyolkeyboard.wordsearch.WordSearchGenerator
import com.example.kreyolkeyboard.wordsearch.WordSearchPuzzle
import com.example.kreyolkeyboard.wordsearch.WordSearchWord
import com.example.kreyolkeyboard.wordsearch.WordSearchDifficulty
import com.example.kreyolkeyboard.wordsearch.WordSearchThemes
import com.example.kreyolkeyboard.wordsearch.WordSearchGridAdapter
import android.widget.Toast
import android.widget.GridView
import android.widget.ScrollView

class SettingsActivity : AppCompatActivity() {
    private var currentTab = 0 // 0 = démarrage, 1 = stats, 2 = à propos, 3 = mots mêlés, 4 = mots mélangés
    private lateinit var viewPager: ViewPager2
    private lateinit var tabBar: LinearLayout
    
    // 🔧 FIX CRITIQUE: Scope lié au lifecycle de l'activité
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        // Système de cache ultra-léger pour les modifications
        private val pendingUpdates = ConcurrentHashMap<String, Int>(16, 0.75f, 1)
        private var lastSaveTime = 0L
        private const val SAVE_INTERVAL_MS = 30000L // 30 secondes
        private const val MAX_PENDING_UPDATES = 50 // Limite pour éviter l'accumulation
        
        private var saveExecutor: ScheduledExecutorService? = null
        
        // Fonction statique pour mettre à jour l'usage d'un mot (appelée depuis le clavier)
        @JvmStatic
        fun updateWordUsage(context: Context, word: String) {
            // Filtrer les mots trop courts ou invalides
            if (word.length < 2 || word.isBlank()) return
            
            // Incrémenter dans le cache (thread-safe)
            pendingUpdates.merge(word.lowercase().trim(), 1) { old, new -> old + new }
            
            // Si trop d'updates en attente, forcer une sauvegarde
            if (pendingUpdates.size >= MAX_PENDING_UPDATES) {
                flushPendingUpdates(context)
            }
            
            // Programmer une sauvegarde différée si pas déjà programmée
            scheduleDelayedSave(context)
        }
        
        // Sauvegarde différée pour optimiser les I/O
        private fun scheduleDelayedSave(context: Context) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSaveTime < SAVE_INTERVAL_MS) return
            
            if (saveExecutor == null) {
                saveExecutor = Executors.newSingleThreadScheduledExecutor()
            }
            
            saveExecutor?.schedule({
                flushPendingUpdates(context)
            }, SAVE_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
        
        // Vider le cache en mémoire vers le fichier
        @JvmStatic
        fun flushPendingUpdates(context: Context, scope: CoroutineScope? = null) {
            if (pendingUpdates.isEmpty()) return
            
            // Copie atomique du cache pour libérer rapidement la mémoire
            val updatesToSave = HashMap<String, Int>(pendingUpdates)
            pendingUpdates.clear()
            lastSaveTime = System.currentTimeMillis()
            
            // 🔧 FIX CRITIQUE: Utiliser le scope fourni ou créer un scope avec SupervisorJob
            // Cela évite les JobCancellationException et fuites mémoire
            val executionScope = scope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
            
            executionScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        saveUpdatesToFile(context, updatesToSave)
                    }
                } catch (e: CancellationException) {
                    Log.d("SettingsActivity", "💾 Sauvegarde annulée, rollback des updates")
                    // En cas d'annulation, remettre les updates dans le cache
                    updatesToSave.forEach { (word, count) ->
                        pendingUpdates.merge(word, count) { old, new -> old + new }
                    }
                    throw e // Important: re-throw CancellationException
                } catch (e: Exception) {
                    Log.e("SettingsActivity", "❌ Erreur sauvegarde: ${e.message}")
                    // En cas d'erreur, remettre les updates dans le cache
                    updatesToSave.forEach { (word, count) ->
                        pendingUpdates.merge(word, count) { old, new -> old + new }
                    }
                }
            }
        }
        
        // Sauvegarde optimisée par lecture partielle
        private suspend fun saveUpdatesToFile(context: Context, updates: Map<String, Int>) {
            val usageFile = File(context.filesDir, "creole_dict_with_usage.json")
            
            if (!usageFile.exists()) {
                // Créer le fichier s'il n'existe pas
                createInitialUsageFile(context)
            }
            
            // Lecture streaming pour économiser la mémoire
            val existingData = try {
                usageFile.bufferedReader().use { reader ->
                    val sb = StringBuilder(8192) // Buffer fixe
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    JSONObject(sb.toString())
                }
            } catch (e: Exception) {
                Log.w("SettingsActivity", "Fichier corrompu, recréation")
                createInitialUsageFile(context)
                JSONObject()
            }
            
            // Appliquer seulement les modifications nécessaires
            var hasChanges = false
            updates.forEach { (word, incrementCount) ->
                if (existingData.has(word)) {
                    val currentCount = existingData.optInt(word, 0)
                    existingData.put(word, currentCount + incrementCount)
                    hasChanges = true
                } else {
                    // Nouveau mot, l'ajouter seulement s'il est dans le dictionnaire
                    if (isWordInDictionary(context, word)) {
                        existingData.put(word, incrementCount)
                        hasChanges = true
                    }
                }
            }
            
            // Sauvegarder seulement si des changements ont été faits
            if (hasChanges) {
                // Écriture atomique pour éviter la corruption
                val tempFile = File(context.filesDir, "creole_dict_with_usage.json.tmp")
                tempFile.bufferedWriter().use { writer ->
                    writer.write(existingData.toString())
                }
                tempFile.renameTo(usageFile)
                
                val motsSauvegardes = updates.map { "${it.key}(+${it.value})" }.joinToString(", ")
                Log.d("SettingsActivity", "Sauvegardé ${updates.size} mots: $motsSauvegardes")
            }
        }
        
        // Vérification rapide si un mot existe dans le dictionnaire
        private fun isWordInDictionary(context: Context, word: String): Boolean {
            return try {
                context.assets.open("creole_dict.json").bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.contains("\"$word\"", ignoreCase = true)) {
                            return true
                        }
                    }
                }
                false
            } catch (e: Exception) {
                false
            }
        }
        
        // Création optimisée du fichier initial
        private fun createInitialUsageFile(context: Context) {
            val usageFile = File(context.filesDir, "creole_dict_with_usage.json")
            
            // Créer un fichier complètement vide sans aucune donnée de démonstration
            val emptyUsageObject = JSONObject()
            usageFile.writeText(emptyUsageObject.toString())
        }
        
        // Nettoyage des ressources
        @JvmStatic
        fun cleanup() {
            saveExecutor?.shutdown()
            saveExecutor = null
            pendingUpdates.clear()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Restaurer l'onglet actif si l'activité a été recréée
        currentTab = savedInstanceState?.getInt("currentTab", 0) ?: 0
        
        // Masquer la barre d'action (bandeau noir)
        supportActionBar?.hide()
        
        Log.d("SettingsActivity", "Création de l'activité principale Kréyòl Karukera")
        
        // Layout principal vertical : Tabs en haut, puis ViewPager
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        // Créer la barre d'onglets horizontale
        tabBar = createTabBar()
        
        // ViewPager2 pour le contenu avec navigation swipe
        viewPager = ViewPager2(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            adapter = SettingsPagerAdapter(this@SettingsActivity)
            
            // 🎨 Effet de swipe style Tinder
            setPageTransformer(TinderSwipeTransformer())
            
            // Callback pour synchroniser avec la barre d'onglets
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    // Calculer la position réelle (0, 1 ou 2) avec modulo
                    currentTab = position % SettingsPagerAdapter.REAL_COUNT
                    updateTabBar()
                }
            })
            
            // 🔄 Démarrer au milieu de la plage virtuelle pour permettre le swipe dans les deux sens
            post {
                val startPosition = SettingsPagerAdapter.START_POSITION - (SettingsPagerAdapter.START_POSITION % SettingsPagerAdapter.REAL_COUNT) + currentTab
                setCurrentItem(startPosition, false)
            }
        }
        
        mainLayout.addView(tabBar)
        mainLayout.addView(viewPager)
        
        setContentView(mainLayout)
        
        Log.d("SettingsActivity", "Interface avec tabs en haut et swipe cyclique créée avec succès")
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Sauvegarder l'onglet actif avant que l'activité soit recréée
        outState.putInt("currentTab", currentTab)
        Log.d("SettingsActivity", "💾 Sauvegarde de l'onglet actif: $currentTab")
    }
    
    override fun onDestroy() {
        // 🔧 FIX CRITIQUE: Sauvegarder avec le scope de l'activité avant annulation
        flushPendingUpdates(this, activityScope)
        
        // 🔧 FIX CRITIQUE: Annuler toutes les coroutines de l'activité
        activityScope.cancel()
        Log.d("SettingsActivity", "✅ Coroutines de l'activité annulées proprement")
        
        super.onDestroy()
    }
    
    /**
     * 🔧 FIX CRITIQUE: Ajouter délai avant fermeture pour éviter "Consumer closed input channel"
     * Laisse le temps aux derniers événements tactiles d'être traités
     */
    override fun onBackPressed() {
        // Délai de 100ms pour traiter les événements en cours
        Handler(Looper.getMainLooper()).postDelayed({
            super.onBackPressed()
        }, 100)
    }
    
    private fun createTabBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.WHITE)
            elevation = 4f // Ombre légère pour séparer du contenu
            
            // Bandeau bleu en haut
            val appHeader = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.parseColor("#0080FF"))
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
            }
            
            val appTitle = TextView(this@SettingsActivity).apply {
                text = "Klavyé Kréyòl"
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            
            appHeader.addView(appTitle)
            
            // Container pour les onglets
            val tabContainer = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    140
                )
                gravity = Gravity.CENTER
            }
            
            // Tab Démarrage
            val startTab = createTab(0, "🚀", "Démarrage")
            tabContainer.addView(startTab)
            Log.d("SettingsActivity", "Onglet Démarrage créé et ajouté")
            
            // Tab Statistiques  
            val statsTab = createTab(1, "📊", "Kréyòl an mwen")
            tabContainer.addView(statsTab)
            Log.d("SettingsActivity", "Onglet Statistiques créé et ajouté")
            
            // Tab Mots Mêlés
            val wordSearchTab = createTab(2, "🎲", "Mots Mêlés")
            tabContainer.addView(wordSearchTab)
            Log.d("SettingsActivity", "Onglet Mots Mêlés créé et ajouté")
            
            // Tab Mots Mélangés
            val wordScrambleTab = createTab(3, "🔤", "Mots Mélangés")
            tabContainer.addView(wordScrambleTab)
            Log.d("SettingsActivity", "Onglet Mots Mélangés créé et ajouté")
            
            // Tab À Propos
            val aboutTab = createTab(4, "ℹ️", "À Propos")
            tabContainer.addView(aboutTab)
            Log.d("SettingsActivity", "Onglet À Propos créé et ajouté")
            
            // Ligne de séparation en bas (fine)
            val separator = View(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2
                )
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
            
            addView(appHeader)
            addView(tabContainer)
            addView(separator)
        }
    }
    
    private fun createTab(tabIndex: Int, emoji: String, label: String): LinearLayout {
        Log.d("SettingsActivity", "Création onglet $tabIndex: $emoji $label")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 12, 24, 12) // Padding vertical augmenté
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            // Background légèrement coloré si onglet actif
            setBackgroundColor(
                if (tabIndex == currentTab) 
                    Color.parseColor("#FFF5E6") // Beige clair orangé
                else 
                    Color.WHITE
            )
            
            // Emoji du tab
            val emojiView = TextView(this@SettingsActivity).apply {
                text = emoji
                textSize = 32f // Augmenté encore plus
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 2)
                // Emoji légèrement teinté si actif pour plus de cohérence visuelle
                alpha = if (tabIndex == currentTab) 1.0f else 0.6f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                // Conversion pixels en DP pour meilleur affichage
                val density = resources.displayMetrics.density
                minHeight = (60 * density).toInt() // 60dp en pixels
            }
            
            // Label du tab
            val labelView = TextView(this@SettingsActivity).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 2)
                setTextColor(
                    if (tabIndex == currentTab) 
                        Color.parseColor("#FF8C00") 
                    else 
                        Color.GRAY
                )
                setTypeface(null, if (tabIndex == currentTab) Typeface.BOLD else Typeface.NORMAL)
            }
            
            addView(emojiView)
            addView(labelView)
            
            // Indicateur orange en bas si tab actif
            if (tabIndex == currentTab) {
                val indicator = View(this@SettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        60,
                        4
                    ).apply {
                        topMargin = 6
                    }
                    setBackgroundColor(Color.parseColor("#FF8C00"))
                }
                addView(indicator)
            }
            
            setOnClickListener {
                // Calculer la position virtuelle la plus proche pour le tabIndex demandé
                val currentPosition = viewPager.currentItem
                val currentRealTab = currentPosition % SettingsPagerAdapter.REAL_COUNT
                val targetRealTab = tabIndex
                
                // Calculer la distance la plus courte en tenant compte du cycle
                val forwardDistance = (targetRealTab - currentRealTab + SettingsPagerAdapter.REAL_COUNT) % SettingsPagerAdapter.REAL_COUNT
                val backwardDistance = (currentRealTab - targetRealTab + SettingsPagerAdapter.REAL_COUNT) % SettingsPagerAdapter.REAL_COUNT
                
                // Choisir la direction la plus courte
                val targetPosition = if (forwardDistance <= backwardDistance) {
                    currentPosition + forwardDistance
                } else {
                    currentPosition - backwardDistance
                }
                
                viewPager.setCurrentItem(targetPosition, true)
            }
        }
    }
    
    private fun updateTabBar() {
        tabBar.removeAllViews()
        
        // Bandeau bleu en haut
        val appHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#0080FF"))
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }
        
        val appTitle = TextView(this).apply {
            text = "Klavyé Kréyòl"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        
        appHeader.addView(appTitle)
        
        // Container pour les onglets
        val tabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
            )
            gravity = Gravity.CENTER
        }
        
        // Tabs avec les 5 onglets
        tabContainer.addView(createTab(0, "🚀", "Démarrage"))
        tabContainer.addView(createTab(1, "📊", "Kréyòl an mwen"))
        tabContainer.addView(createTab(2, "🎲", "Mots Mêlés"))
        tabContainer.addView(createTab(3, "🔤", "Mots Mélangés"))
        tabContainer.addView(createTab(4, "ℹ️", "À Propos"))
        
        // Ligne de séparation en bas
        val separator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            )
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        
        tabBar.addView(appHeader)
        tabBar.addView(tabContainer)
        tabBar.addView(separator)
    }
    
    // Onglet 1 : Démarrage / Onboarding
    fun createOnboardingContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val isEnabled = isKeyboardEnabled()
        val isSelected = isKeyboardSelected()
        
        // 🔍 Log pour déboguer l'état du clavier
        Log.d("SettingsActivity", "📋 État du clavier: isEnabled=$isEnabled, isSelected=$isSelected")
        
        // Hero Section - Bienvenue avec progression (carte compacte)
        val heroCard = createCard("#FFFFFF")
        
        // Layout horizontal pour icône + texte
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        
        val welcomeIcon = TextView(this).apply {
            text = when {
                isEnabled && isSelected -> "✅"
                isEnabled -> "🎯"
                else -> "🚀"
            }
            textSize = 32f
            setPadding(0, 0, 16, 0)
        }
        
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        val welcomeTitle = TextView(this).apply {
            text = when {
                isEnabled && isSelected -> "Tout est prêt !"
                isEnabled -> "Vous y êtes presque !"
                else -> "Bienvenue sur Klavyé Kréyòl !"
            }
            textSize = 18f
            setTextColor(when {
                isEnabled && isSelected -> Color.parseColor("#4CAF50")
                isEnabled -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#0080FF")
            })
            setTypeface(null, Typeface.BOLD)
        }
        
        val welcomeSubtitle = TextView(this).apply {
            text = when {
                isEnabled && isSelected -> "Vous pouvez taper en Kréyòl partout !"
                isEnabled -> "Sélectionnez le clavier pour l'utiliser"
                else -> "Configurez votre clavier en 3 étapes ⏱️"
            }
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
        }
        
        textContainer.addView(welcomeTitle)
        textContainer.addView(welcomeSubtitle)
        
        headerLayout.addView(welcomeIcon)
        headerLayout.addView(textContainer)
        
        heroCard.addView(headerLayout)
        
        // Barre de progression compacte
        val progressBar = createProgressBar(isEnabled, isSelected)
        heroCard.addView(progressBar)
        
        mainLayout.addView(heroCard)
        mainLayout.addView(createSpacing(16))
        
        // Section "En 3 étapes"
        val stepsTitle = TextView(this).apply {
            text = "📍 Configuration"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        mainLayout.addView(stepsTitle)
        
        // ÉTAPE 1 : Activer le clavier
        val step1Card = createStepCard(
            stepNumber = 1,
            isCompleted = isEnabled,
            isLocked = false,
            icon = "⚙️",
            title = "Activer le clavier",
            description = "Activez 'Klavyé Kréyòl Karukera' dans les paramètres système",
            buttonText = if (isEnabled) "✓ Activé" else "Ouvrir les paramètres",
            buttonEnabled = !isEnabled,
            buttonAction = {
                openKeyboardSettings()
            }
        )
        mainLayout.addView(step1Card)
        mainLayout.addView(createSpacing(12))
        
        // ÉTAPE 2 : Sélectionner le clavier
        val step2Card = createStepCard(
            stepNumber = 2,
            isCompleted = isSelected,
            isLocked = !isEnabled,
            icon = "🔄",
            title = "Sélectionner le clavier",
            description = if (!isEnabled) "Complétez d'abord l'étape 1" else "Choisissez le clavier Kréyòl quand vous tapez du texte",
            buttonText = when {
                !isEnabled -> "🔒 Verrouillé"
                isSelected -> "✓ Sélectionné"
                else -> "Ouvrir le sélecteur"
            },
            buttonEnabled = isEnabled && !isSelected,
            buttonAction = {
                openInputMethodPicker()
            }
        )
        mainLayout.addView(step2Card)
        mainLayout.addView(createSpacing(12))
        
        // ÉTAPE 3 : Tester le clavier
        val step3Card = createCard("#FFFFFF")
        
        val isStep3Locked = !isEnabled || !isSelected
        
        val step3Header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        
        val step3Badge = TextView(this).apply {
            text = if (isStep3Locked) "🔒" else "3"
            textSize = 20f
            setTextColor(
                when {
                    isStep3Locked -> Color.parseColor("#999999")
                    else -> Color.parseColor("#0080FF")
                }
            )
            setTypeface(null, Typeface.BOLD)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(
                when {
                    isStep3Locked -> Color.parseColor("#F5F5F5")
                    else -> Color.parseColor("#E3F2FD")
                }
            )
        }
        
        val step3Icon = TextView(this).apply {
            text = "✍️"
            textSize = 24f
            setPadding(16, 0, 12, 0)
            alpha = if (isStep3Locked) 0.5f else 1.0f
        }
        
        val step3TitleText = TextView(this).apply {
            text = "Tester le clavier"
            textSize = 18f
            setTextColor(if (isStep3Locked) Color.parseColor("#999999") else Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        step3Header.addView(step3Badge)
        step3Header.addView(step3Icon)
        step3Header.addView(step3TitleText)
        
        val step3Desc = TextView(this).apply {
            text = if (isStep3Locked) "Complétez les étapes 1 et 2 pour débloquer" else "Tapez quelques mots pour essayer les suggestions en Kréyòl"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, 12)
            setLineSpacing(0f, 1.3f)
        }
        
        val testEditText = EditText(this).apply {
            hint = if (isStep3Locked) "🔒 Verrouillé" else "Ékri an Kréyòl la..."
            textSize = 16f
            setPadding(16, 16, 16, 16)
            minHeight = 100
            setBackgroundColor(if (isStep3Locked) Color.parseColor("#EEEEEE") else Color.parseColor("#F9F9F9"))
            setTextColor(Color.parseColor("#1C1C1C"))
            setHintTextColor(Color.parseColor("#999999"))
            this.isEnabled = !isStep3Locked
            alpha = if (isStep3Locked) 0.5f else 1.0f
            
            // Force le scroll vers ce champ quand il obtient le focus
            if (!isStep3Locked) {
                setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        // Post avec délai pour laisser le clavier s'ouvrir
                        Handler(Looper.getMainLooper()).postDelayed({
                            view.parent?.requestChildFocus(view, view)
                        }, 300)
                    }
                }
            }
        }
        
        step3Card.addView(step3Header)
        step3Card.addView(step3Desc)
        step3Card.addView(testEditText)
        
        mainLayout.addView(step3Card)
        mainLayout.addView(createSpacing(24))
        
        // Section "Astuce" si tout est configuré
        if (isEnabled && isSelected) {
            val tipCard = createCard("#FFF9E6")
            
            val tipHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 8)
            }
            
            val tipIcon = TextView(this).apply {
                text = "💡"
                textSize = 24f
                setPadding(0, 0, 12, 0)
            }
            
            val tipTitle = TextView(this).apply {
                text = "Astuce du jour"
                textSize = 16f
                setTextColor(Color.parseColor("#F57C00"))
                setTypeface(null, Typeface.BOLD)
            }
            
            tipHeader.addView(tipIcon)
            tipHeader.addView(tipTitle)
            
            val tipText = TextView(this).apply {
                text = "Appuyez longuement sur une lettre pour accéder aux accents et caractères spéciaux (é, è, à, ò, etc.)"
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setLineSpacing(0f, 1.3f)
            }
            
            tipCard.addView(tipHeader)
            tipCard.addView(tipText)
            
            mainLayout.addView(tipCard)
            mainLayout.addView(createSpacing(16))
            
            // Lien vers statistiques
            val statsLinkCard = createCard("#E8F5E9")
            
            val statsLinkLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            
            val statsIcon = TextView(this).apply {
                text = "📊"
                textSize = 32f
                setPadding(0, 0, 16, 0)
            }
            
            val statsTextContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            val statsTitle = TextView(this).apply {
                text = "Découvrez vos statistiques"
                textSize = 16f
                setTextColor(Color.parseColor("#2E7D32"))
                setTypeface(null, Typeface.BOLD)
            }
            
            val statsDesc = TextView(this).apply {
                text = "Suivez votre progression et montez en niveau !"
                textSize = 13f
                setTextColor(Color.parseColor("#558B2F"))
            }
            
            statsTextContainer.addView(statsTitle)
            statsTextContainer.addView(statsDesc)
            
            val statsArrow = TextView(this).apply {
                text = "→"
                textSize = 24f
                setTextColor(Color.parseColor("#2E7D32"))
            }
            
            statsLinkLayout.addView(statsIcon)
            statsLinkLayout.addView(statsTextContainer)
            statsLinkLayout.addView(statsArrow)
            
            statsLinkCard.addView(statsLinkLayout)
            statsLinkCard.setOnClickListener {
                viewPager.currentItem = 1 // Naviguer vers l'onglet Stats
            }
            
            mainLayout.addView(statsLinkCard)
        }
        
        // Message si clavier non activé
        if (!isEnabled) {
            mainLayout.addView(createSpacing(16))
            
            val helpCard = createCard("#FFF3E0")
            
            val helpText = TextView(this).apply {
                text = "❓ Besoin d'aide ? Suivez les étapes ci-dessus dans l'ordre pour configurer votre clavier."
                textSize = 14f
                setTextColor(Color.parseColor("#E65100"))
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.3f)
            }
            
            helpCard.addView(helpText)
            mainLayout.addView(helpCard)
        }
        
        return mainLayout
    }
    
    // Fonction pour créer la barre de statut dynamique
    private fun createStatusBar(isEnabled: Boolean, isSelected: Boolean): LinearLayout {
        val statusBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 16, 20, 16)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(
                when {
                    isEnabled && isSelected -> Color.parseColor("#4CAF50") // Vert
                    isEnabled -> Color.parseColor("#FFA726") // Orange
                    else -> Color.parseColor("#FF6B35") // Rouge-orange
                }
            )
        }
        
        val icon = TextView(this).apply {
            text = when {
                isEnabled && isSelected -> "✅"
                isEnabled -> "🔄"
                else -> "⚠️"
            }
            textSize = 24f
            setPadding(0, 0, 16, 0)
        }
        
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        val title = TextView(this).apply {
            text = when {
                isEnabled && isSelected -> "Tout est prêt !"
                isEnabled -> "Presque prêt !"
                else -> "Action requise"
            }
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        
        val subtitle = TextView(this).apply {
            text = when {
                isEnabled && isSelected -> "Vous pouvez taper en Kréyòl partout"
                isEnabled -> "Sélectionnez le clavier pour l'utiliser"
                else -> "Activez le clavier pour commencer"
            }
            textSize = 13f
            setTextColor(Color.WHITE)
            alpha = 0.9f
        }
        
        textContainer.addView(title)
        textContainer.addView(subtitle)
        
        statusBar.addView(icon)
        statusBar.addView(textContainer)
        
        // Bouton d'action si nécessaire
        if (!isEnabled || !isSelected) {
            val actionButton = Button(this).apply {
                text = if (!isEnabled) "Activer →" else "Sélectionner →"
                textSize = 14f
                setBackgroundColor(Color.WHITE)
                setTextColor(if (!isEnabled) Color.parseColor("#FF6B35") else Color.parseColor("#FFA726"))
                setPadding(20, 10, 20, 10)
                setTypeface(null, Typeface.BOLD)
                setOnClickListener {
                    if (!isEnabled) {
                        openKeyboardSettings()
                    } else {
                        openInputMethodPicker()
                    }
                }
            }
            statusBar.addView(actionButton)
        }
        
        return statusBar
    }
    
    // Fonction pour créer la barre de progression
    private fun createProgressBar(isEnabled: Boolean, isSelected: Boolean): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }
        
        val progressText = TextView(this).apply {
            text = when {
                isEnabled && isSelected -> "Configuration terminée ✓"
                isEnabled -> "Étape 2 sur 3"
                else -> "Étape 1 sur 3"
            }
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
            setPadding(0, 0, 0, 6)
        }
        
        val progressBarContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                8
            )
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }
        
        val filledPart = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                when {
                    isEnabled && isSelected -> 3f
                    isEnabled -> 2f
                    else -> 1f
                }
            )
            setBackgroundColor(when {
                isEnabled && isSelected -> Color.parseColor("#4CAF50")
                isEnabled -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#0080FF")
            })
        }
        
        val emptyPart = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                when {
                    isEnabled && isSelected -> 0f
                    isEnabled -> 1f
                    else -> 2f
                }
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        
        progressBarContainer.addView(filledPart)
        if (emptyPart.layoutParams.width != 0) {
            progressBarContainer.addView(emptyPart)
        }
        
        container.addView(progressText)
        container.addView(progressBarContainer)
        
        return container
    }
    
    // Fonction pour créer une card d'étape
    private fun createStepCard(
        stepNumber: Int,
        isCompleted: Boolean,
        isLocked: Boolean,
        icon: String,
        title: String,
        description: String,
        buttonText: String,
        buttonEnabled: Boolean,
        buttonAction: () -> Unit
    ): LinearLayout {
        val card = createCard("#FFFFFF")
        
        // Appliquer une opacité si verrouillé
        if (isLocked) {
            card.alpha = 0.6f
        }
        
        // Header avec numéro et icône
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        
        val badge = TextView(this).apply {
            text = stepNumber.toString()
            textSize = 20f
            setTextColor(
                when {
                    isLocked -> Color.parseColor("#999999")
                    isCompleted -> Color.parseColor("#4CAF50")
                    else -> Color.parseColor("#0080FF")
                }
            )
            setTypeface(null, Typeface.BOLD)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(
                when {
                    isLocked -> Color.parseColor("#F5F5F5")
                    isCompleted -> Color.parseColor("#E8F5E9")
                    else -> Color.parseColor("#E3F2FD")
                }
            )
        }
        
        val iconText = TextView(this).apply {
            text = icon
            textSize = 24f
            setPadding(16, 0, 12, 0)
        }
        
        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(if (isLocked) Color.parseColor("#999999") else Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        if (isCompleted) {
            val checkIcon = TextView(this).apply {
                text = "✓"
                textSize = 24f
                setTextColor(Color.parseColor("#4CAF50"))
                setTypeface(null, Typeface.BOLD)
            }
            header.addView(badge)
            header.addView(iconText)
            header.addView(titleText)
            header.addView(checkIcon)
        } else if (isLocked) {
            val lockIcon = TextView(this).apply {
                text = "🔒"
                textSize = 20f
            }
            header.addView(badge)
            header.addView(iconText)
            header.addView(titleText)
            header.addView(lockIcon)
        } else {
            header.addView(badge)
            header.addView(iconText)
            header.addView(titleText)
        }
        
        val descText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, 16)
            setLineSpacing(0f, 1.3f)
        }
        
        val button = Button(this).apply {
            text = buttonText
            textSize = 15f
            setBackgroundColor(
                when {
                    isLocked -> Color.parseColor("#EEEEEE")
                    isCompleted -> Color.parseColor("#E0E0E0")
                    buttonEnabled -> Color.parseColor("#0080FF")
                    else -> Color.parseColor("#BDBDBD")
                }
            )
            setTextColor(
                when {
                    isLocked -> Color.parseColor("#999999")
                    isCompleted -> Color.parseColor("#757575")
                    else -> Color.WHITE
                }
            )
            setPadding(24, 16, 24, 16)
            this.isEnabled = buttonEnabled && !isCompleted && !isLocked
            setOnClickListener {
                if (!isCompleted && !isLocked) {
                    buttonAction()
                }
            }
        }
        
        card.addView(header)
        card.addView(descText)
        card.addView(button)
        
        return card
    }
    
    // Onglet 3 : À Propos
    fun createAboutContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Mission
        val missionCard = createCard("#FFFFFF")
        
        val missionTitle = TextView(this).apply {
            text = "🌟 Notre Mission"
            textSize = 20f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val missionText = TextView(this).apply {
            text = "Ce clavier a été spécialement conçu pour préserver et promouvoir le Kréyòl Guadeloupéen (Karukera). " +
                    "Il met à disposition de tous un outil moderne pour écrire dans notre belle langue créole avec :\n\n" +
                    "💡 Suggestions de mots en Kréyòl\n" +
                    "🌈 Design aux couleurs de la Guadeloupe\n" +
                    "🪘 Identité guadeloupéenne forte"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.3f)
        }
        
        missionCard.addView(missionTitle)
        missionCard.addView(missionText)
        mainLayout.addView(missionCard)
        mainLayout.addView(createSpacing(16))
        
        // Sources littéraires
        val sourcesCard = createCard("#F0F8E8")
        
        val sourcesTitle = TextView(this).apply {
            text = "📚 Sources littéraires"
            textSize = 18f
            setTextColor(Color.parseColor("#228B22"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        
        val sourcesText = TextView(this).apply {
            text = "Les suggestions de mots en Kréyòl sont construites sur les travaux des défenseurs du Kréyòl :\n\n" +
                    "✍️ Sylviane Telchid, Sonny Rupaire, Robert Fontes, Max Rippon, Alain Rutil, Alain Vérin, Katel, " +
                    "Esnard Boisdur, Pierre Édouard Décimus, Corinne Famibelle\n\n" +
                    "Grâce à leurs riches contributions, ce clavier vous propose des suggestions authentiques et fidèles à notre créole guadeloupéen."
            textSize = 14f
            setTextColor(Color.parseColor("#2F5233"))
            setLineSpacing(0f, 1.3f)
        }
        
        sourcesCard.addView(sourcesTitle)
        sourcesCard.addView(sourcesText)
        mainLayout.addView(sourcesCard)
        mainLayout.addView(createSpacing(16))
        
        // Informations app
        val infoCard = createCard("#F8F9FA")
        
        val infoTitle = TextView(this).apply {
            text = "ℹ️ Informations"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 0, 0, 24)
        }

        val versionText = TextView(this).apply {
            text = "Version : 6.4.0\n" +
                    "© Potomitan™ - Clavier Kréyòl Karukera\n\n" +
                    "🏝️ Fait avec ❤️ pour la Guadeloupe\n" +
                    "Préservons notre langue créole pour les générations futures !"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.3f)
            gravity = Gravity.CENTER
        }
        
        infoCard.addView(infoTitle)
        infoCard.addView(versionText)
        mainLayout.addView(infoCard)
        
        return mainLayout
    }
    
    // Helpers pour créer les éléments UI
    private fun createCard(backgroundColor: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor(backgroundColor))
        }
    }
    
    private fun createSpacing(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * resources.displayMetrics.density).toInt()
            )
        }
    }
    
    private fun createChecklistItem(isChecked: Boolean, title: String, description: String): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        
        val checkbox = TextView(this).apply {
            text = if (isChecked) "✅" else "⚠️"
            textSize = 24f
            setPadding(0, 0, 16, 0)
        }
        
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(if (isChecked) Color.parseColor("#228B22") else Color.parseColor("#FF6B35"))
            setTypeface(null, Typeface.BOLD)
        }
        
        val descText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.2f)
        }
        
        textContainer.addView(titleText)
        textContainer.addView(descText)
        
        item.addView(checkbox)
        item.addView(textContainer)
        
        return item
    }
    
    private fun createGuideCard(icon: String, title: String, description: String): LinearLayout {
        val card = createCard("#FFFFFF")
        
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val iconText = TextView(this).apply {
            text = icon
            textSize = 28f
            setPadding(0, 0, 16, 0)
        }
        
        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        header.addView(iconText)
        header.addView(titleText)
        
        val descText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.3f)
            setPadding(0, 8, 0, 0)
        }
        
        card.addView(header)
        card.addView(descText)
        
        return card
    }
    
    
    // Fonction pour créer la bannière d'activation
    private fun createActivationBanner(): LinearLayout {
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#FF6B35")) // Orange vif
            setPadding(24, 16, 24, 16)
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val icon = TextView(this).apply {
            text = "⚠️"
            textSize = 24f
            setPadding(0, 0, 16, 0)
        }
        
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        
        val title = TextView(this).apply {
            text = "Clavier non activé"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val subtitle = TextView(this).apply {
            text = "Activez le clavier pour commencer à taper"
            textSize = 13f
            setTextColor(Color.parseColor("#FFFFFF"))
            alpha = 0.9f
        }
        
        val activateButton = Button(this).apply {
            text = "Activer maintenant"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.parseColor("#FF6B35"))
            setPadding(24, 12, 24, 12)
            setOnClickListener {
                openKeyboardSettings()
            }
        }
        
        textContainer.addView(title)
        textContainer.addView(subtitle)
        banner.addView(icon)
        banner.addView(textContainer)
        banner.addView(activateButton)
        
        return banner
    }
    
    // Fonction pour vérifier si le clavier est activé
    fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledIMEs = imm.enabledInputMethodList
        val myPackageName = packageName
        
        return enabledIMEs.any { it.packageName == myPackageName }
    }
    
    // Fonction pour vérifier si le clavier est sélectionné comme clavier actif
    fun isKeyboardSelected(): Boolean {
        try {
            val currentIme = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            return currentIme?.contains(packageName) == true
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur vérification clavier sélectionné: ${e.message}")
            return false
        }
    }
    
    // Fonction pour ouvrir les paramètres de clavier
    private fun openKeyboardSettings() {
        try {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            
            Toast.makeText(this, 
                "Activez 'Klavyé Kréyòl Karukera' dans la liste", 
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur ouverture paramètres clavier: ${e.message}")
            // Fallback vers paramètres généraux
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Impossible d'ouvrir les paramètres", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Fonction pour ouvrir le sélecteur de méthode d'entrée (clavier)
    private fun openInputMethodPicker() {
        try {
            Log.d("SettingsActivity", "🔄 Ouverture du sélecteur de clavier")
            
            // Créer un EditText temporaire invisible pour avoir un contexte d'entrée
            val tempEditText = EditText(this).apply {
                visibility = View.GONE
                isFocusable = true
                isFocusableInTouchMode = true
            }
            
            // Ajouter temporairement à la vue racine
            val rootView = window.decorView.rootView as android.view.ViewGroup
            rootView.addView(tempEditText)
            
            // Demander le focus
            tempEditText.requestFocus()
            
            // Ouvrir le sélecteur après un court délai pour laisser le focus s'établir
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
                
                // Nettoyer après un délai supplémentaire
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    rootView.removeView(tempEditText)
                }, 1000)
            }, 100)
            
            Toast.makeText(this, 
                "Sélectionnez 'Klavyé Kréyòl Karukera' dans la liste", 
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur ouverture sélecteur clavier: ${e.message}")
            Toast.makeText(this, 
                "Impossible d'ouvrir le sélecteur. Utilisez la barre de notification pour changer de clavier.", 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    fun createStatsContent(): LinearLayout {
        Log.d("SettingsActivity", "Création du contenu des statistiques")
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val stats = loadVocabularyStats()
        Log.d("SettingsActivity", "Stats chargées: ${stats.wordsDiscovered} mots découverts, ${stats.totalUsages} utilisations")
        
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
        
        // Calcul des mots restants pour le niveau suivant
        val (nextLevelName, wordsRemaining) = getNextLevelInfo(stats.wordsDiscovered)
        
        // 🔍 DEBUG: Log pour vérifier les calculs
        val thresholdsDebug = calculateGaussianThresholds()
        Log.d("SettingsActivity", "📊 DEBUG Niveau: wordsDiscovered=${stats.wordsDiscovered}, " +
                "levelName=$levelName, nextLevelName=$nextLevelName, wordsRemaining=$wordsRemaining")
        Log.d("SettingsActivity", "📊 DEBUG Seuils: ${thresholdsDebug.joinToString(", ")}")
        
        val levelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 40)
        }
        
        // Message de progression vers le niveau suivant
        val progressMessage = TextView(this).apply {
            text = if (wordsRemaining > 0) {
                "Votre niveau actuel est $levelName, plus que $wordsRemaining mot${if (wordsRemaining > 1) "s" else ""} restant${if (wordsRemaining > 1) "s" else ""} à découvrir pour passer au niveau suivant ($nextLevelName)"
            } else if (levelName == "Benzo") {
                "Vous avez atteint le niveau maximum : $levelName ! 👑"
            } else {
                "Votre niveau actuel est $levelName"
            }
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(16, 0, 16, 24)
            setLineSpacing(6f, 1f)
        }
        
        levelContainer.addView(progressMessage)
        
        val levelBadge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
        }
        
        val levelEmojiText = TextView(this).apply {
            text = levelEmoji
            textSize = 48f
            setPadding(0, 0, 16, 0)
        }
        
        val levelNameText = TextView(this).apply {
            text = levelName
            textSize = 28f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
        }
        
        levelBadge.addView(levelEmojiText)
        levelBadge.addView(levelNameText)
        
        val percentageText = TextView(this).apply {
            text = "${String.format("%.1f", stats.coveragePercentage)}%"
            textSize = 32f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        
        val percentageLabel = TextView(this).apply {
            text = "${stats.wordsDiscovered} mots découverts sur les ${stats.totalWords} mots du dictionnaire Kréyòl"
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
            setPadding(24, 0, 24, 40)
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
                textSize = 20f
                setTextColor(Color.parseColor("#FF8C00"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 16, 0)
            }
            
            val wordName = TextView(this).apply {
                text = word.first
                textSize = 20f
                setTextColor(Color.parseColor("#1C1C1C"))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            val wordCount = TextView(this).apply {
                text = "${word.second}"
                textSize = 20f
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
        
        // Ligne unique: Découverts | Utilisations
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 32)
        }
        
        statsRow.addView(createStatBlock("${stats.wordsDiscovered}", "Mots découverts"))
        statsRow.addView(createStatBlock("${stats.totalUsages}", "Utilisations"))
        
        statsGridContainer.addView(statsRow)
        
        // === Mots à Découvrir ===
        val wordsToDiscoverContainer = createWordListSection(
            "🌟 Mots à Découvrir",
            stats.wordsToDiscover,
            "#2196F3"
        )
        
        // === Mots Découverts ===
        val discoveredWordsContainer = createWordListSection(
            "🔍 Mots Découverts (${stats.discoveredWordsList.size})",
            stats.discoveredWordsList,
            "#4CAF50"
        )
        
        // Assembler
        statsContainer.addView(levelContainer)
        statsContainer.addView(wordContainer)
        statsContainer.addView(wordsToDiscoverContainer)
        statsContainer.addView(top5Container)
        statsContainer.addView(statsGridContainer)
        statsContainer.addView(discoveredWordsContainer)
        
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
    
    private fun createWordListSection(title: String, words: List<String>, accentColor: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            
            // Titre de la section
            val sectionTitle = TextView(this@SettingsActivity).apply {
                text = title
                textSize = 24f  // Augmenté de 1.5x (16f * 1.5)
                setTextColor(Color.parseColor("#1C1C1C"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            }
            addView(sectionTitle)
            
            if (words.isEmpty()) {
                // Message si aucun mot
                val emptyMessage = TextView(this@SettingsActivity).apply {
                    text = "Aucun mot dans cette catégorie pour le moment"
                    textSize = 21f  // Augmenté de 1.5x (14f * 1.5)
                    setTextColor(Color.parseColor("#999999"))
                    setTypeface(null, Typeface.ITALIC)
                    setPadding(16, 12, 16, 12)
                    setBackgroundColor(Color.parseColor("#F5F5F5"))
                }
                addView(emptyMessage)
            } else {
                // Conteneur pour les mots avec scroll
                val scrollView = ScrollView(this@SettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        450 // Hauteur maximale augmentée de 1.5x (300 * 1.5)
                    )
                }
                
                // Container avec retour à la ligne automatique (FlowLayout simulé)
                val wordsContainer = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12, 12, 12, 12)
                    setBackgroundColor(Color.parseColor("#FAFAFA"))
                }
                
                // Créer des lignes dynamiques qui s'adaptent à la largeur
                var currentRow = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 6
                    }
                }
                wordsContainer.addView(currentRow)
                
                var currentRowWidth = 0
                // Calculer la largeur disponible: largeur écran - padding container (24) - padding statsContainer (48) - marges (24)
                val screenWidth = resources.displayMetrics.widthPixels - 96
                
                words.forEach { word ->
                    // Créer le chip du mot
                    val wordChip = TextView(this@SettingsActivity).apply {
                        text = word
                        textSize = 19.5f  // Augmenté de 1.5x (13f * 1.5)
                        setTextColor(Color.parseColor(accentColor))
                        setPadding(15, 7, 15, 7)  // Augmenté de 1.5x (10, 5, 10, 5)
                        setBackgroundColor(Color.parseColor("${accentColor}20"))
                        setSingleLine(true)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            rightMargin = 5
                            bottomMargin = 5
                        }
                    }
                    
                    // Mesurer la largeur du mot avant de l'ajouter
                    wordChip.measure(
                        View.MeasureSpec.UNSPECIFIED,
                        View.MeasureSpec.UNSPECIFIED
                    )
                    val wordWidth = wordChip.measuredWidth + 10 // +marge droite + espace sécurité
                    
                    // Si le mot ne rentre pas dans la ligne actuelle, créer une nouvelle ligne
                    if (currentRowWidth + wordWidth > screenWidth && currentRowWidth > 0) {
                        currentRow = LinearLayout(this@SettingsActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = 6
                            }
                        }
                        wordsContainer.addView(currentRow)
                        currentRowWidth = 0
                    }
                    
                    currentRow.addView(wordChip)
                    currentRowWidth += wordWidth
                }
                
                scrollView.addView(wordsContainer)
                addView(scrollView)
            }
        }
    }
    
    // === Fonctions de chargement de données ===
    
    data class VocabularyStats(
        val totalWords: Int,
        val wordsDiscovered: Int,
        val totalUsages: Int,
        val topWords: List<Pair<String, Int>>,
        val coveragePercentage: Float,
        val discoveredWordsList: List<String>,
        val wordsToDiscover: List<String>
    )
    
    private fun loadVocabularyStats(): VocabularyStats {
        Log.d("SettingsActivity", "🔍 Chargement des statistiques du vocabulaire")
        return try {
            // Toujours charger le total depuis le dictionnaire source
            val totalDictWords = getTotalDictionaryWords()
            
            // Essayer le fichier avec usage
            val usageFile = File(filesDir, "creole_dict_with_usage.json")
            Log.d("SettingsActivity", "📂 Fichier usage existe: ${usageFile.exists()}")
            Log.d("SettingsActivity", "📂 Chemin fichier: ${usageFile.absolutePath}")
            
            if (usageFile.exists()) {
                val jsonString = usageFile.readText()
                Log.d("SettingsActivity", "📄 Contenu fichier (${jsonString.length} chars): ${jsonString.take(200)}...")
                val jsonObject = JSONObject(jsonString)
                Log.d("SettingsActivity", "🔑 Clés JSON trouvées: ${jsonObject.keys().asSequence().toList().size}")
                
                var wordsDiscovered = 0
                var totalUsages = 0
                val wordUsages = mutableListOf<Pair<String, Int>>()
                val discoveredWords = mutableListOf<String>()
                
                val motsTrouves = mutableListOf<String>()
                jsonObject.keys().forEach { word ->
                    // Gérer les deux formats possibles
                    val userCount = try {
                        val rawValue = jsonObject.get(word)
                        when (rawValue) {
                            is Int -> {
                                // Format simplifié: "mot": 1
                                rawValue
                            }
                            is JSONObject -> {
                                // Format complet: "mot": {"frequency": X, "user_count": Y}
                                rawValue.optInt("user_count", 0)
                            }
                            else -> 0
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Erreur lecture '$word': ${e.message}")
                        0
                    }
                    
                    if (userCount > 0) {
                        totalUsages += userCount
                        wordUsages.add(Pair(word, userCount))
                        motsTrouves.add("$word($userCount)")
                        
                        // Compter comme "découvert" seulement si utilisé exactement 1 fois
                        if (userCount == 1) {
                            wordsDiscovered++
                            // Ne garder que les mots de 3 lettres ou plus pour l'affichage
                            if (word.length >= 3) {
                                discoveredWords.add(word)
                            }
                        }
                    }
                }
                
                Log.d("SettingsActivity", "Mots avec usage > 0: ${motsTrouves.joinToString(", ")}")
                Log.d("SettingsActivity", "Total: $totalDictWords mots, Usage: $totalUsages, Découverts: $wordsDiscovered")
                
                val topWords = wordUsages.filter { it.first.length >= 3 }.sortedByDescending { it.second }.take(5)
                val coverage = if (totalDictWords > 0) (wordsDiscovered.toFloat() / totalDictWords * 100) else 0f
                
                // Générer les mots à découvrir (utilisations <= 2 et longueur >= 3)
                val wordsToDiscoverCandidates = jsonObject.keys().asSequence().toList().filter { word ->
                    val count = jsonObject.optInt(word, 0)
                    count <= 2 && word.length >= 3
                }
                val wordsToDiscoverList = wordsToDiscoverCandidates.shuffled().take(5)
                
                return VocabularyStats(
                    totalDictWords,
                    wordsDiscovered,
                    totalUsages,
                    topWords,
                    coverage,
                    discoveredWords.sorted(),
                    wordsToDiscoverList
                )
            }
            
            // Sinon créer un fichier vide pour la première installation
            val emptyUsageObject = JSONObject()
            usageFile.writeText(emptyUsageObject.toString())
            
            // Retourner des statistiques avec le vrai total de mots du dictionnaire
            return VocabularyStats(
                totalWords = totalDictWords,
                wordsDiscovered = 0,
                totalUsages = 0,
                topWords = emptyList(),
                coveragePercentage = 0f,
                discoveredWordsList = emptyList(),
                wordsToDiscover = emptyList()
            )
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur chargement stats: ${e.message}")
            VocabularyStats(0, 0, 0, emptyList(), 0f, emptyList(), emptyList())
        }
    }
    
    private fun getCurrentLevel(wordsDiscovered: Int): String {
        val thresholds = calculateGaussianThresholds()
        return when {
            wordsDiscovered >= thresholds[7] -> "🧙🏿‍♀️ Benzo"          // +3σ (0.15% - ~4 mots)
            wordsDiscovered >= thresholds[6] -> "👑 Potomitan"          // +2σ à +3σ (2% - ~57 mots)
            wordsDiscovered >= thresholds[5] -> "🐘 Kompè Zamba"        // +1σ à +2σ (14% - ~396 mots)
            wordsDiscovered >= thresholds[4] -> "🐇 Kompè Lapen"        // 0 à +1σ (34% - ~963 mots)
            wordsDiscovered >= thresholds[3] -> "💎 An mitan"            // -1σ à 0 (34% - ~963 mots)
            wordsDiscovered >= thresholds[2] -> "🔥 Débrouya"            // -2σ à -1σ (14% - ~396 mots)
            wordsDiscovered >= thresholds[1] -> "🌱 Ti moun"              // -3σ à -2σ (2% - ~57 mots)
            else -> "🌍 Pipirit"                                          // < -3σ (0.15% - ~4 mots)
        }
    }
    
    private fun getNextLevelInfo(wordsDiscovered: Int): Pair<String, Int> {
        val thresholds = calculateGaussianThresholds()
        return when {
            wordsDiscovered >= thresholds[7] -> Pair("Benzo", 0) // Niveau maximum atteint!
            wordsDiscovered >= thresholds[6] -> Pair("Benzo", thresholds[7] - wordsDiscovered)
            wordsDiscovered >= thresholds[5] -> Pair("Potomitan", thresholds[6] - wordsDiscovered)
            wordsDiscovered >= thresholds[4] -> Pair("Kompè Zamba", thresholds[5] - wordsDiscovered)
            wordsDiscovered >= thresholds[3] -> Pair("Kompè Lapen", thresholds[4] - wordsDiscovered)
            wordsDiscovered >= thresholds[2] -> Pair("An mitan", thresholds[3] - wordsDiscovered)
            wordsDiscovered >= thresholds[1] -> Pair("Débrouya", thresholds[2] - wordsDiscovered)
            else -> Pair("Ti moun", thresholds[1] - wordsDiscovered)
        }
    }
    
    /**
     * Calcule les seuils de niveau de façon dynamique selon la taille du dictionnaire
     * 
     * Progression motivante basée sur des pourcentages du dictionnaire total:
     * - Pipirit (début): 0% - démarrage
     * - Ti moun: 1.5% - premiers pas (rapide à atteindre!)
     * - Débrouya: 5% - débrouillard
     * - An mitan: 12% - au milieu du chemin
     * - Kompè Lapen: 25% - bon niveau
     * - Kompè Zamba: 45% - niveau avancé
     * - Potomitan: 70% - expert
     * - Benzo: 100% - maître absolu (tous les mots!)
     * 
     * Avantages:
     * - S'adapte automatiquement à la croissance du dictionnaire
     * - Progression douce au début (1.5% pour Ti moun)
     * - Écarts progressifs entre niveaux (motivant!)
     * - Benzo reste l'objectif ultime (100%)
     * 
     * Exemples pour 3680 mots:
     * - Ti moun: 55 mots, Débrouya: 184 mots, An mitan: 442 mots
     * - Kompè Lapen: 920 mots, Kompè Zamba: 1656 mots
     * - Potomitan: 2576 mots, Benzo: 3680 mots
     * 
     * @return IntArray avec 8 seuils calculés dynamiquement
     */
    private fun calculateGaussianThresholds(): IntArray {
        val totalWords = getTotalDictionaryWords()
        
        // Pourcentages progressifs pour chaque niveau
        val percentages = doubleArrayOf(
            0.0,    // 0: Pipirit (démarrage)
            0.015,  // 1: Ti moun (1.5% - premiers pas encourageants)
            0.05,   // 2: Débrouya (5% - débrouillard)
            0.12,   // 3: An mitan (12% - au milieu)
            0.25,   // 4: Kompè Lapen (25% - quart du chemin)
            0.45,   // 5: Kompè Zamba (45% - presque la moitié)
            0.70,   // 6: Potomitan (70% - expert confirmé)
            1.0     // 7: Benzo (100% - tous les mots!)
        )
        
        // Convertir les pourcentages en nombres de mots
        return IntArray(8) { index ->
            if (index == 7) {
                totalWords  // Dernier niveau = tous les mots exactement
            } else {
                (totalWords * percentages[index]).toInt()
            }
        }
    }
    
    /**
     * Récupère le nombre total de mots dans le dictionnaire
     * Utilise un cache pour éviter de relire le fichier à chaque fois
     */
    private var cachedTotalWords: Int? = null
    
    private fun getTotalDictionaryWords(): Int {
        // Retourner depuis le cache si disponible
        cachedTotalWords?.let { return it }
        
        return try {
            // Toujours charger le dictionnaire source depuis assets
            // car creole_dict_with_usage.json peut être vide (nouveau install)
            val jsonString = assets.open("creole_dict.json").bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            val count = jsonArray.length()
            
            cachedTotalWords = count
            Log.d("SettingsActivity", "📊 Total mots dictionnaire: $count")
            count
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur comptage mots: ${e.message}")
            14722 // Fallback sur la valeur connue du dictionnaire complet
        }
    }
    
    // Adapter pour ViewPager2 avec swipe cyclique
    private class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        companion object {
            const val REAL_COUNT = 5 // Nombre réel d'onglets (ajout mots mélangés)
            const val VIRTUAL_COUNT = Int.MAX_VALUE // Nombre virtuel pour simuler l'infini
            const val START_POSITION = VIRTUAL_COUNT / 2 // Position de départ au milieu
        }
        
        override fun getItemCount(): Int = VIRTUAL_COUNT
        
        override fun createFragment(position: Int): Fragment {
            // Utiliser le modulo pour revenir aux 5 vraies pages
            val realPosition = position % REAL_COUNT
            return when (realPosition) {
                0 -> OnboardingFragment()
                1 -> StatsFragment()
                2 -> WordSearchFragment()
                3 -> WordScrambleFragment()
                4 -> AboutFragment()
                else -> OnboardingFragment()
            }
        }
    }
    
    // Fragment pour le démarrage / onboarding
    class OnboardingFragment : Fragment() {
        private var rootView: ScrollView? = null
        private var refreshHandler: Handler? = null
        private var refreshRunnable: Runnable? = null
        
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            rootView = ScrollView(activity).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = true
            }
            refreshContent()
            return rootView!!
        }
        
        override fun onResume() {
            super.onResume()
            // 🔧 FIX: Rafraîchir le contenu quand le fragment redevient visible
            // Cela met à jour l'état du clavier (activé/sélectionné)
            refreshContent()
            
            // 🔧 FIX: Démarrer une vérification périodique de l'état du clavier
            startPeriodicRefresh()
        }
        
        override fun onPause() {
            super.onPause()
            // Arrêter la vérification périodique quand le fragment n'est plus visible
            stopPeriodicRefresh()
        }
        
        private fun startPeriodicRefresh() {
            stopPeriodicRefresh() // S'assurer qu'il n'y a pas de refresh en cours
            
            refreshHandler = Handler(Looper.getMainLooper())
            refreshRunnable = object : Runnable {
                override fun run() {
                    // Vérifier si l'état a changé et rafraîchir si nécessaire
                    val activity = requireActivity() as SettingsActivity
                    val currentEnabled = activity.isKeyboardEnabled()
                    val currentSelected = activity.isKeyboardSelected()
                    
                    // Rafraîchir uniquement si l'état a changé
                    if (shouldRefresh(currentEnabled, currentSelected)) {
                        refreshContent()
                    }
                    
                    // Reprogrammer la vérification dans 2 secondes
                    refreshHandler?.postDelayed(this, 2000)
                }
            }
            refreshHandler?.postDelayed(refreshRunnable!!, 2000)
        }
        
        private fun stopPeriodicRefresh() {
            refreshRunnable?.let { refreshHandler?.removeCallbacks(it) }
            refreshHandler = null
            refreshRunnable = null
        }
        
        private var lastKnownEnabled = false
        private var lastKnownSelected = false
        
        private fun shouldRefresh(currentEnabled: Boolean, currentSelected: Boolean): Boolean {
            val hasChanged = currentEnabled != lastKnownEnabled || currentSelected != lastKnownSelected
            lastKnownEnabled = currentEnabled
            lastKnownSelected = currentSelected
            return hasChanged
        }
        
        private fun refreshContent() {
            val activity = requireActivity() as SettingsActivity
            lastKnownEnabled = activity.isKeyboardEnabled()
            lastKnownSelected = activity.isKeyboardSelected()
            rootView?.removeAllViews()
            rootView?.addView(activity.createOnboardingContent())
            Log.d("SettingsActivity", "🔄 Contenu de l'onboarding rafraîchi (enabled=$lastKnownEnabled, selected=$lastKnownSelected)")
        }
    }
    
    // Fragment pour l'à propos
    class AboutFragment : Fragment() {
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            val scrollView = ScrollView(activity)
            scrollView.addView(activity.createAboutContent())
            return scrollView
        }
    }
    
    // Fragment pour les statistiques
    class StatsFragment : Fragment() {
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            Log.d("SettingsActivity", "Création de la vue StatsFragment")
            val activity = requireActivity() as SettingsActivity
            
            // Créer le SwipeRefreshLayout pour le Pull-to-Refresh
            val swipeRefreshLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(activity).apply {
                setColorSchemeColors(
                    Color.parseColor("#0080FF"), // Bleu principal
                    Color.parseColor("#4CAF50"), // Vert
                    Color.parseColor("#FF9800")  // Orange
                )
                setProgressBackgroundColorSchemeColor(Color.WHITE)
                
                // Configurer l'action de rafraîchissement
                setOnRefreshListener {
                    Log.d("SettingsActivity", "🔄 Pull-to-Refresh déclenché")
                    
                    // Afficher un message
                    Toast.makeText(activity, "Actualisation des statistiques...", Toast.LENGTH_SHORT).show()
                    
                    // Forcer la sauvegarde des données en attente
                    flushPendingUpdates(activity, activity.activityScope)
                    
                    // Attendre un peu puis recréer l'activité
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d("SettingsActivity", "🔄 Rechargement de l'activité après pull-to-refresh")
                        activity.recreate() // Redémarre complètement l'activité
                    }, 500) // Attendre 500ms
                }
            }
            
            val scrollView = ScrollView(activity).apply {
                setBackgroundColor(Color.WHITE)
                isFillViewport = true
            }
            val statsContent = activity.createStatsContent()
            scrollView.addView(statsContent)
            
            // Ajouter le ScrollView dans le SwipeRefreshLayout
            swipeRefreshLayout.addView(scrollView)
            
            Log.d("SettingsActivity", "StatsFragment créé avec Pull-to-Refresh")
            return swipeRefreshLayout
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
                // Lire directement l'entier
                usageCount = jsonObject.optInt(selectedWord, 0)
                
                return Pair(selectedWord, usageCount)
            } else {
                Log.d("SettingsActivity", "Fichier usage n'existe pas, création depuis assets")
                // Charger depuis les assets
                val jsonString = assets.open("creole_dict.json").bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(jsonString)
                Log.d("SettingsActivity", "Dictionnaire chargé: ${jsonArray.length()} mots")
                
                allWords = mutableListOf<String>().apply {
                    for (i in 0 until jsonArray.length()) {
                        val wordArray = jsonArray.getJSONArray(i)
                        add(wordArray.getString(0))  // Premier élément = le mot
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
    
    /**
     * 🎨 Transformateur personnalisé pour effet Tinder Swipe
     * 
     * Caractéristiques :
     * - Rotation de -15° à +15° selon la direction du swipe
     * - Translation verticale : la carte se soulève légèrement
     * - Scale : la carte rétrécit un peu en s'éloignant
     * - Fade out progressif
     * - Élévation : la page courante est au-dessus
     */
    private class TinderSwipeTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            page.apply {
                when {
                    position < -1 -> { // [-Infinity,-1)
                        // Page complètement à gauche, hors écran
                        alpha = 0f
                        translationX = 0f
                        translationY = 0f
                        rotation = 0f
                        scaleX = 1f
                        scaleY = 1f
                    }
                    position <= 1 -> { // [-1,1]
                        // Page visible ou en transition
                        
                        // 🎯 Effet Tinder : rotation + translation + scale
                        val absPosition = Math.abs(position)
                        
                        // Rotation de -15° à +15° selon la direction du swipe
                        rotation = -15f * position
                        
                        // Translation verticale : la carte se soulève légèrement
                        translationY = -Math.abs(position) * 50f
                        
                        // Translation horizontale pour accentuer le mouvement
                        translationX = position * width * 0.3f
                        
                        // Scale : la carte rétrécit un peu en s'éloignant
                        val scale = 1f - absPosition * 0.2f
                        scaleX = scale
                        scaleY = scale
                        
                        // Alpha : fade out progressif
                        alpha = 1f - absPosition * 0.5f
                        
                        // Élévation : la page courante est au-dessus
                        elevation = (1f - absPosition) * 10f
                    }
                    else -> { // (1,+Infinity]
                        // Page complètement à droite, hors écran
                        alpha = 0f
                        translationX = 0f
                        translationY = 0f
                        rotation = 0f
                        scaleX = 1f
                        scaleY = 1f
                    }
                }
            }
        }
    }
    
    // Fragment pour les mots mêlés
    class WordSearchFragment : Fragment() {
        
        private var currentPuzzle: WordSearchPuzzle? = null
        private var startTime: Long = 0
        private var wordsFound = 0
        private lateinit var gridView: GridView
        private lateinit var wordsListContainer: LinearLayout
        private lateinit var tvTheme: TextView
        private lateinit var tvScore: TextView
        
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            
            // ScrollView pour permettre le défilement si nécessaire
            return ScrollView(activity).apply {
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                
                val mainLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 8, 8, 8) // Réduction du padding de 16 à 8
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    
                    // En-tête avec thème et score
                    val headerLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(8, 4, 8, 8) // Réduction du padding
                        gravity = Gravity.CENTER_VERTICAL
                        
                        tvTheme = TextView(activity).apply {
                            text = "� Chargement..."
                            textSize = 16f
                            setTextColor(Color.parseColor("#9C27B0"))
                            setTypeface(null, Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }
                        addView(tvTheme)
                        
                        tvScore = TextView(activity).apply {
                            text = "⭐ 0"
                            textSize = 16f
                            setTextColor(Color.parseColor("#FF9800"))
                            setTypeface(null, Typeface.BOLD)
                            gravity = Gravity.END
                        }
                        addView(tvScore)
                    }
                    addView(headerLayout)
                    
                    // Grille de mots mêlés
                    gridView = GridView(activity).apply {
                        // Calculer la taille disponible pour la grille
                        val screenWidth = resources.displayMetrics.widthPixels
                        val availableWidth = screenWidth - 48
                        
                        // La grille est toujours 8x8
                        val gridSize = 8
                        // Calculer la taille d'une cellule en fonction de la largeur
                        val cellSize = availableWidth / gridSize
                        // Hauteur de la grille = 8 cellules + espacements + padding
                        val gridHeight = (cellSize * gridSize) + (4 * (gridSize - 1)) + 24
                        
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            gridHeight
                        )
                        setPadding(12, 12, 12, 12)
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        setBackgroundColor(Color.parseColor("#F5F5F5")) // Fond gris très clair
                        verticalSpacing = 4 // Espacement vertical entre les lignes
                        horizontalSpacing = 4 // Espacement horizontal entre les colonnes
                        
                        // 🔧 FIX: Gérer les touches au niveau de la GridView pour permettre le swipe entre cellules
                        setOnTouchListener { view, event ->
                            // Demander au parent de ne pas intercepter les événements
                            parent?.requestDisallowInterceptTouchEvent(true)
                            
                            // Calculer quelle cellule est touchée
                            val position = pointToPosition(event.x.toInt(), event.y.toInt())
                            
                            if (position != android.widget.AdapterView.INVALID_POSITION) {
                                val adapter = adapter as? WordSearchGridAdapter
                                adapter?.handleTouchEvent(position, event)
                            }
                            
                            // Réactiver l'interception après ACTION_UP ou ACTION_CANCEL
                            if (event.action == android.view.MotionEvent.ACTION_UP ||
                                event.action == android.view.MotionEvent.ACTION_CANCEL) {
                                parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            
                            true // Consommer l'événement
                        }
                    }
                    addView(gridView)
                    
                    // Bouton nouvelle grille
                    val btnNewGame = Button(activity).apply {
                        text = "🔄 Nouvelle Grille"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.parseColor("#9C27B0"))
                        setPadding(24, 10, 24, 10) // Réduction du padding vertical
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 8, 0, 8) // Réduction des marges de 16 à 8
                        }
                        setOnClickListener {
                            generateNewPuzzle()
                        }
                    }
                    addView(btnNewGame)
                    
                    // Liste des mots à trouver
                    val wordsTitle = TextView(activity).apply {
                        text = "📝 Mots à trouver :"
                        textSize = 14f // Réduction de 16 à 14
                        setTextColor(Color.parseColor("#333333"))
                        setTypeface(null, Typeface.BOLD)
                        setPadding(8, 4, 8, 4) // Réduction du padding
                    }
                    addView(wordsTitle)
                    
                    wordsListContainer = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8, 4, 8, 8) // Réduction du padding
                        setBackgroundColor(Color.parseColor("#FFFFFF"))
                    }
                    addView(wordsListContainer)
                }
                
                addView(mainLayout)
                
                // Générer la première grille après que la vue soit créée
                post {
                    generateNewPuzzle()
                }
            }
        }
        
        private fun generateNewPuzzle() {
            try {
                val activity = requireActivity() as SettingsActivity
                
                // Générer une nouvelle grille 8x8 avec des mots aléatoires du dictionnaire
                currentPuzzle = WordSearchGenerator.generatePuzzle(
                    context = activity,
                    theme = "kreyol", // Thème unique
                    gridSize = 8,
                    difficulty = WordSearchDifficulty.NORMAL
                )
                
                // Afficher la grille
                displayPuzzle(currentPuzzle!!)
                
                // Réinitialiser
                startTime = System.currentTimeMillis()
                wordsFound = 0
                updateScore(0)
                
                Log.d("WordSearchFragment", "Nouvelle grille générée: ${currentPuzzle?.words?.size} mots")
                
            } catch (e: Exception) {
                Log.e("WordSearchFragment", "Erreur génération: ${e.message}", e)
                Toast.makeText(requireContext(), "Erreur lors de la génération", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun displayPuzzle(puzzle: WordSearchPuzzle) {
            val activity = requireActivity() as SettingsActivity
            
            // Configurer l'adaptateur de la grille
            val adapter = WordSearchGridAdapter(activity, puzzle)
            adapter.setOnWordFoundListener { word ->
                onWordFound(word)
            }
            gridView.adapter = adapter
            gridView.numColumns = puzzle.gridSize
            
            // Afficher le titre simple sans thème
            tvTheme.text = "🎯 Mots Créoles"
            
            // Afficher la liste des mots
            displayWordsList(puzzle.words)
        }
        
        private fun displayWordsList(words: List<WordSearchWord>) {
            wordsListContainer.removeAllViews()
            val activity = requireActivity() as SettingsActivity
            
            words.forEach { word ->
                val wordView = TextView(activity).apply {
                    text = if (word.isFound) "✅ ${word.word.uppercase()}" else "📝 ${word.word.uppercase()}"
                    textSize = 14f
                    setPadding(12, 8, 12, 8)
                    setTextColor(if (word.isFound) Color.parseColor("#4CAF50") else Color.parseColor("#333333"))
                    setTypeface(null, if (word.isFound) Typeface.BOLD else Typeface.NORMAL)
                }
                wordsListContainer.addView(wordView)
            }
        }
        
        private fun onWordFound(word: String) {
            wordsFound++
            
            // Mettre à jour la liste
            currentPuzzle?.words?.find { it.word.equals(word, ignoreCase = true) }?.isFound = true
            displayWordsList(currentPuzzle?.words ?: emptyList())
            
            // Calculer les points
            val points = word.length * 10
            updateScore(points)
            
            // Vérifier si tous les mots sont trouvés
            if (wordsFound == currentPuzzle?.words?.size) {
                Toast.makeText(requireContext(), "🎉 Félicitations ! Tous les mots trouvés !", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "✅ Mot trouvé : $word (+$points pts)", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun updateScore(points: Int) {
            val currentScore = tvScore.text.toString().replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0
            val newScore = currentScore + points
            tvScore.text = "⭐ $newScore"
        }
    }
    
    // Fragment pour le jeu de mots mélangés
    class WordScrambleFragment : Fragment() {
        private var rootView: ScrollView? = null
        
        private lateinit var tvScore: TextView
        private lateinit var tvWordNumber: TextView
        private lateinit var gridScrambled: GridView
        private lateinit var gridAnswer: GridView
        private lateinit var btnValidate: Button
        private lateinit var btnSkip: Button
        private lateinit var btnHint: Button
        private lateinit var btnClear: Button
        private lateinit var progressBar: ProgressBar
        
        private var scrambledAdapter: com.example.kreyolkeyboard.wordscramble.ScrambledLettersAdapter? = null
        private var answerAdapter: com.example.kreyolkeyboard.wordscramble.AnswerLettersAdapter? = null
        
        private var currentWord: String = ""
        private var scrambledLetters: List<Char> = listOf()
        private val currentAnswer = mutableListOf<Char?>()
        private val selectedPositions = mutableListOf<Int>()
        
        private var gameWords: List<String> = listOf()
        private var currentWordIndex = 0
        private var score = 0
        private var difficulty = com.example.kreyolkeyboard.wordscramble.ScrambleDifficulty.NORMAL
        
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            
            rootView = ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                isFillViewport = true
                
                val mainLayout = LinearLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 16, 32, 16)
                    
                    // En-tête avec score
                    val headerLayout = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        setBackgroundColor(Color.WHITE)
                        setPadding(24, 24, 24, 24)
                        elevation = 8f
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 32
                        
                        tvScore = TextView(activity).apply {
                            text = "Score: 0"
                            textSize = 24f
                            setTypeface(null, Typeface.BOLD)
                            gravity = Gravity.CENTER
                            setTextColor(Color.parseColor("#4CAF50"))
                        }
                        addView(tvScore)
                    }
                    addView(headerLayout)
                    
                    // Numéro du mot et progression
                    val progressLayout = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 32
                        
                        tvWordNumber = TextView(activity).apply {
                            text = "Mot 1/10"
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setPadding(0, 0, 0, 16)
                        }
                        addView(tvWordNumber)
                        
                        progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                16
                            )
                            max = 10
                            progress = 0
                        }
                        addView(progressBar)
                    }
                    addView(progressLayout)
                    
                    // Titre
                    val title = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = "🔤 Remets les lettres dans l'ordre !"
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#1976D2"))
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 32
                    }
                    addView(title)
                    
                    // Label lettres disponibles
                    val labelScrambled = TextView(activity).apply {
                        text = "Lettres disponibles :"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, 0, 0, 16)
                    }
                    addView(labelScrambled)
                    
                    // Grille des lettres mélangées
                    gridScrambled = GridView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        numColumns = 5
                        verticalSpacing = 16
                        horizontalSpacing = 16
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 48
                        
                        setOnItemClickListener { _, _, position, _ ->
                            if (!selectedPositions.contains(position)) {
                                addLetterToAnswer(position)
                            }
                        }
                    }
                    addView(gridScrambled)
                    
                    // Label réponse
                    val labelAnswer = TextView(activity).apply {
                        text = "Ta réponse :"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, 0, 0, 16)
                    }
                    addView(labelAnswer)
                    
                    // Grille de la réponse
                    gridAnswer = GridView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        numColumns = 5
                        verticalSpacing = 16
                        horizontalSpacing = 16
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 48
                        
                        setOnItemClickListener { _, _, position, _ ->
                            removeLetterFromAnswer(position)
                        }
                    }
                    addView(gridAnswer)
                    
                    // Boutons d'action ligne 1
                    val buttonRow1 = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 24
                        
                        btnClear = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = "🔄 Effacer"
                            setBackgroundColor(Color.parseColor("#FF9800"))
                            setTextColor(Color.WHITE)
                            setOnClickListener { clearAnswer() }
                        }
                        addView(btnClear)
                        
                        btnHint = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = "💡 Indice"
                            setBackgroundColor(Color.parseColor("#FFC107"))
                            setTextColor(Color.WHITE)
                            setOnClickListener { showHint() }
                        }
                        addView(btnHint)
                    }
                    addView(buttonRow1)
                    
                    // Boutons d'action ligne 2
                    val buttonRow2 = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        
                        btnValidate = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = "✅ Valider"
                            setBackgroundColor(Color.parseColor("#4CAF50"))
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            isEnabled = false
                            setOnClickListener { validateAnswer() }
                        }
                        addView(btnValidate)
                        
                        btnSkip = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = "⏭️ Passer"
                            setBackgroundColor(Color.parseColor("#9E9E9E"))
                            setTextColor(Color.WHITE)
                            setOnClickListener { skipWord() }
                        }
                        addView(btnSkip)
                    }
                    addView(buttonRow2)
                }
                
                addView(mainLayout)
                
                post {
                    startNewGame()
                }
            }
            
            return rootView!!
        }
        
        private fun startNewGame() {
            score = 0
            currentWordIndex = 0
            
            gameWords = com.example.kreyolkeyboard.wordscramble.WordScrambleData.loadWords(requireContext(), difficulty)
            
            if (gameWords.isEmpty()) {
                Toast.makeText(requireContext(), "Erreur de chargement", Toast.LENGTH_SHORT).show()
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
            var allScrambledLetters = com.example.kreyolkeyboard.wordscramble.WordScrambleData.scrambleWord(currentWord)
            
            currentAnswer.clear()
            selectedPositions.clear()
            repeat(currentWord.length) { currentAnswer.add(null) }
            
            // Pré-remplir la première et la dernière lettre
            if (currentWord.isNotEmpty()) {
                currentAnswer[0] = currentWord[0]
                if (currentWord.length > 1) {
                    currentAnswer[currentWord.length - 1] = currentWord[currentWord.length - 1]
                }
                
                // Retirer la première et dernière lettre des lettres mélangées
                val lettersToRemove = mutableListOf<Char>()
                lettersToRemove.add(currentWord[0])
                if (currentWord.length > 1) {
                    lettersToRemove.add(currentWord[currentWord.length - 1])
                }
                
                scrambledLetters = allScrambledLetters.toMutableList().apply {
                    lettersToRemove.forEach { letter ->
                        remove(letter)
                    }
                }
            } else {
                scrambledLetters = allScrambledLetters
            }
            
            scrambledAdapter = com.example.kreyolkeyboard.wordscramble.ScrambledLettersAdapter(requireContext(), scrambledLetters)
            answerAdapter = com.example.kreyolkeyboard.wordscramble.AnswerLettersAdapter(requireContext(), currentAnswer)
            
            gridScrambled.adapter = scrambledAdapter
            gridAnswer.adapter = answerAdapter
            
            gridScrambled.numColumns = minOf(scrambledLetters.size, 5)
            gridAnswer.numColumns = minOf(currentWord.length, 5)
            
            // Ajuster la hauteur des grilles
            val numRowsScrambled = (scrambledLetters.size + 4) / 5
            val numRowsAnswer = (currentWord.length + 4) / 5
            gridScrambled.layoutParams.height = numRowsScrambled * 136 // 120 + 16 spacing
            gridAnswer.layoutParams.height = numRowsAnswer * 136
            
            tvWordNumber.text = "Mot ${currentWordIndex + 1}/${gameWords.size}"
            tvScore.text = "Score: $score"
            progressBar.progress = currentWordIndex
        }

        
        private fun addLetterToAnswer(position: Int) {
            val emptyIndex = currentAnswer.indexOfFirst { it == null }
            if (emptyIndex != -1) {
                currentAnswer[emptyIndex] = scrambledLetters[position]
                selectedPositions.add(position)
                
                scrambledAdapter?.markAsSelected(position)
                answerAdapter?.updateLetters(currentAnswer)
                
                if (currentAnswer.all { it != null }) {
                    btnValidate.isEnabled = true
                }
            }
        }
        
        private fun removeLetterFromAnswer(position: Int) {
            if (position < currentAnswer.size && currentAnswer[position] != null) {
                currentAnswer[position] = null
                
                if (position < selectedPositions.size) {
                    selectedPositions.removeAt(position)
                }
                
                val nonNullLetters = currentAnswer.filterNotNull().toMutableList()
                currentAnswer.clear()
                currentAnswer.addAll(nonNullLetters)
                repeat(currentWord.length - nonNullLetters.size) { currentAnswer.add(null) }
                
                scrambledAdapter?.clearSelections()
                selectedPositions.forEachIndexed { index, pos ->
                    if (index < selectedPositions.size) {
                        scrambledAdapter?.markAsSelected(pos)
                    }
                }
                
                answerAdapter?.updateLetters(currentAnswer)
                btnValidate.isEnabled = false
            }
        }
        
        private fun validateAnswer() {
            val answer = currentAnswer.filterNotNull().joinToString("")
            
            if (answer.equals(currentWord, ignoreCase = true)) {
                score += 100
                
                Toast.makeText(requireContext(), "✅ Correct! +100 pts", Toast.LENGTH_SHORT).show()
                
                currentWordIndex++
                loadNextWord()
            } else {
                Toast.makeText(requireContext(), "❌ Essaie encore!", Toast.LENGTH_SHORT).show()
                clearAnswer()
            }
        }
        
        private fun skipWord() {
            Toast.makeText(requireContext(), "Le mot était: $currentWord", Toast.LENGTH_SHORT).show()
            currentWordIndex++
            loadNextWord()
        }
        
        private fun showHint() {
            val firstEmpty = currentAnswer.indexOfFirst { it == null }
            if (firstEmpty != -1) {
                val correctLetter = currentWord[firstEmpty]
                
                val posInScrambled = scrambledLetters.indexOfFirst { 
                    it == correctLetter && !selectedPositions.contains(scrambledLetters.indexOf(it))
                }
                
                if (posInScrambled != -1) {
                    addLetterToAnswer(posInScrambled)
                    score -= 20
                    tvScore.text = "Score: $score"
                    Toast.makeText(requireContext(), "Indice (-20 pts)", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        private fun clearAnswer() {
            currentAnswer.clear()
            selectedPositions.clear()
            repeat(currentWord.length) { currentAnswer.add(null) }
            
            // Re-pré-remplir la première et dernière lettre
            if (currentWord.isNotEmpty()) {
                currentAnswer[0] = currentWord[0]
                if (currentWord.length > 1) {
                    currentAnswer[currentWord.length - 1] = currentWord[currentWord.length - 1]
                }
            }
            
            scrambledAdapter?.clearSelections()
            answerAdapter?.updateLetters(currentAnswer)
            btnValidate.isEnabled = false
        }
        
        private fun endGame() {
            AlertDialog.Builder(requireContext())
                .setTitle("🎉 Partie terminée!")
                .setMessage("Score final: $score\nMots réussis: $currentWordIndex/${gameWords.size}")
                .setPositiveButton("Rejouer") { _, _ ->
                    startNewGame()
                }
                .setNegativeButton("OK", null)
                .show()
        }
        
        override fun onDestroyView() {
            super.onDestroyView()
            rootView = null
        }
    }
}
