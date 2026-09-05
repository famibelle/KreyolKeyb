package com.example.kreyolkeyboard

/**
 * À la mémoire de mon père, Saint-Ange Corneille Famibelle
 */

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.HorizontalScrollView
import android.widget.Button
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.KeyEvent
import kotlinx.coroutines.*
import android.os.Handler
import android.os.Looper
import java.util.Timer
import java.util.TimerTask
import com.example.kreyolkeyboard.BilingualSuggestion
import com.example.kreyolkeyboard.SuggestionLanguage
import com.example.kreyolkeyboard.gamification.CreoleDictionaryWithUsage
import com.example.kreyolkeyboard.gamification.CreoleLevels
import com.example.kreyolkeyboard.gamification.LevelUpNotifier
import com.example.kreyolkeyboard.gamification.WordCommitListener

/**
 * Service principal du clavier créole refactorisé
 * Version modulaire utilisant des composants séparés pour une meilleure maintenabilité
 */
class KreyolInputMethodServiceRefactored : InputMethodService(),
    KeyboardLayoutManager.KeyboardInteractionListener,
    SuggestionEngine.SuggestionListener,
    AccentHandler.AccentSelectionListener,
    InputProcessor.InputProcessorListener {
    
    companion object {
        private const val TAG = "KreyolIME-Potomitan™"
        private const val MAX_SUGGESTIONS = 5  // 3 Kreyòl + 2 Français (mode bilingue)
        // Hauteur d'une rangée de suggestions : ce que le clavier consomme en
        // hauteur en dehors des rangées de touches elles-mêmes. Le padding
        // vertical du bloc de touches vit dans KeyboardLayoutManager, qui le pose.
        private const val SUGGESTION_ROW_HEIGHT_DP = 44
        // En paysage les suggestions tiennent sur une seule rangée un peu plus
        // basse : les deux rangées empilées coûtaient 88 dp sur les 359 dp que la
        // fenêtre IME reçoit dans cette orientation (contre 891 dp en portrait).
        private const val SUGGESTION_ROW_HEIGHT_LANDSCAPE_DP = 38
        // Intervalle vide autour d'une puce de suggestion, horizontalement comme
        // verticalement : la marge d'erreur de visée. Cf. ACCESSIBILITE.md, point 1.
        //
        // Cet intervalle n'appartient à aucune vue cliquable, donc un appui qui y
        // tombe ne fait rien, et c'est le bon comportement : un appui perdu coûte un
        // geste, un appui sur le mot voisin en coûte cinq (effacer, retaper,
        // revalider). Mesuré sur Pixel 5 (440 dpi) avant ce changement : 6,9 dp
        // (1,10 mm) entre deux puces d'une même rangée, et surtout **3,6 dp
        // (0,58 mm) entre la rangée kréyòl et la rangée française**, alors que
        // l'imprécision d'un doigt est d'abord verticale. Un demi-millimètre trop
        // bas validait un mot français à la place du mot créole visé.
        //
        // Les 12 dp sont pris sur la hauteur des puces (38 dp → 34 dp), pas sur la
        // hauteur des rangées : SUGGESTION_ROW_HEIGHT_DP ne change pas, donc le
        // clavier occupe exactement la même place et aucune autre rangée ne bouge.
        // L'échange est favorable : 0,4 mm de hauteur de cible contre 1,3 mm de
        // séparation.
        private const val SUGGESTION_CHIP_GAP_DP = 12
        // Largeur minimale d'une puce. Ce n'est pas un élargissement : le style
        // Button de la plateforme impose déjà 88 dp, ce qui rend les puces de
        // « ka » ou « ki » aussi larges que celles des mots longs. La valeur est
        // fixée ici pour que la taille de cible ne dépende plus d'un défaut de
        // thème susceptible de changer sans qu'on s'en aperçoive.
        // Taille du mot proposé dans une puce. Elle valait 14 sp, la plus petite
        // du clavier : 32 px de glyphe quand une lettre de touche en fait 38,
        // alors que c'est ce texte que l'on lit pour décider d'accepter une
        // suggestion. Le fond de la puce mesure 82 px de haut et ne dépend pas
        // d'elle, il restait donc 50 px inutilisés autour du mot. À 18 sp les
        // glyphes rejoignent ceux des touches, et un mot long tient encore dans
        // la rangée réduite du paysage (38 dp).
        // Réserve verticale entre le bord de la barre et la puce, du côté qui
        // touche l'extérieur (haut de la rangée kréyòl, bas de la rangée
        // française). Le côté intérieur, lui, porte l'écart entre les deux
        // rangées : cf. suggestionRowInnerPadDp().
        private const val SUGGESTION_ROW_OUTER_PAD_DP = 4
        private const val SUGGESTION_TEXT_SIZE_SP = 18f
        // Réserve verticale à l'intérieur d'une puce, entre le fond arrondi et le
        // mot. C'est elle qui borne la taille de police réellement affichable :
        // cf. fitTextToChipHeight().
        private const val SUGGESTION_CHIP_PADDING_V_DP = 6
        private const val SUGGESTION_CHIP_MIN_WIDTH_DP = 88

        /**
         * Encastrement latéral du plateau de suggestions (v17.0.0).
         *
         * C'est lui qui fait la margelle : le fond du clavier apparaît de
         * chaque côté, et sans ce rebord visible rien ne porterait l'ombre
         * interne, donc rien ne se lirait comme un creux. Latéral seulement :
         * une marge haute ou basse s'ajouterait à la hauteur consommée sans que
         * `computeAvailableRowsHeight()` la voie, et la dernière rangée de
         * touches se ferait rogner d'autant.
         */
        private const val SUGGESTION_BAR_INSET_DP = 8
        private const val ONBOARDING_PREFS = "kreyol_onboarding_prefs"
        private const val PREF_FIRST_REAL_USE_TIP_SHOWN = "first_real_use_tip_shown"
        private const val PREF_SHARE_CHIP_SHOWN = "share_invite_chip_shown"
        // Mot-dièse séparé du lien par une espace : ce message s'insère au fil
        // du texte de l'utilisateur, il ne peut pas se permettre de saut de ligne.
        private const val SHARE_INVITE_MESSAGE = "An maké mésaj la sa épi Klavyé Kréyòl, " +
            "https://play.google.com/store/apps/details?id=com.potomitan.kreyolkeyboard&pcampaignid=web_share" +
            " ${SettingsActivity.SHARE_HASHTAG}"

        // Passage de niveau. Clé distincte de « last_celebrated_level_index »,
        // qu'utilise SettingsActivity pour sa carte partageable : les deux
        // suivis sont volontairement indépendants, pour que la notification ne
        // consomme pas le franchissement et que l'utilisateur retrouve quand
        // même sa carte en ouvrant l'application.
        private const val GAMIFICATION_PREFS = "kreyol_gamification_prefs"
        private const val PREF_LAST_NOTIFIED_LEVEL = "last_notified_level_index"

        /**
         * Temps d'immobilité au bout duquel un glissement de curseur est
         * considéré terminé, et le mot suivi resynchronisé (v14.0.0).
         *
         * Assez long pour couvrir le vol des derniers `onUpdateSelection`
         * provoqués par les touches directionnelles, assez court pour que les
         * suggestions du mot d'arrivée soient déjà là quand le doigt revient
         * frapper. Un caractère tapé pendant ce délai resynchronise de toute
         * façon par le chemin normal.
         */
        private const val DELAI_SYNC_CURSEUR = 120L

        /**
         * Saisie dont le contenu ne doit jamais être conservé, statistiques de
         * vocabulaire comprises.
         * `internal` (et non private) pour être testable en JVM sans EditorInfo.
         *
         * Couvre les mots de passe sous toutes leurs déclarations (texte masqué,
         * texte visible, formulaire web, code numérique) ainsi que les champs que
         * l'application déclare non mémorisables via IME_FLAG_NO_PERSONALIZED_LEARNING.
         * Le mode « mot de passe visible » compte autant que les autres : c'est
         * bien un mot de passe, seul son affichage diffère.
         */
        internal fun isSensitiveInput(inputType: Int, imeOptions: Int): Boolean {
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val inputClass = inputType and InputType.TYPE_MASK_CLASS

            val isPasswordText = inputClass == InputType.TYPE_CLASS_TEXT && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
            val isPasswordNumber = inputClass == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            val noLearning = (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

            return isPasswordText || isPasswordNumber || noLearning
        }

        // 🔧 FIX SAMSUNG A21S: Détection appareils low-end
        private fun isLowEndDevice(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return activityManager.isLowRamDevice || 
                   activityManager.memoryClass <= 256 // 256MB ou moins = low-end
        }
    }
    
    // Composants modulaires
    private lateinit var keyboardLayoutManager: KeyboardLayoutManager
    private lateinit var suggestionEngine: SuggestionEngine
    private lateinit var accentHandler: AccentHandler
    private lateinit var inputProcessor: InputProcessor
    
    // 🎮 Gamification: Tracking d'utilisation du vocabulaire
    private lateinit var dictionaryWithUsage: CreoleDictionaryWithUsage
    
    // Vues principales
    private var suggestionsView: LinearLayout? = null
    private var kreyolRow: LinearLayout? = null
    private var frenchRow: LinearLayout? = null
    private var frenchRowScroll: HorizontalScrollView? = null
    private var mainKeyboardView: View? = null

    /**
     * Palette avec laquelle la vue d'entrée courante a été construite.
     *
     * C'est la seule façon fiable de savoir s'il faut la reconstruire : les
     * couleurs sont figées dans les widgets au moment de leur création, et rien
     * dans [KeyboardTheme] ne peut répondre à la place du service, l'écran de
     * réglages rafraîchissant la palette globale avant que le service reprenne la
     * main. `null` tant qu'aucune vue n'a été construite.
     */
    private var paletteDeLaVue: KeyboardTheme.Palette? = null
    
    // État du service
    private var isInitialized = false
    
    // 🔧 FIX SAMSUNG A21S: Gestion coroutines liées au cycle de vie
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 🔍 MONITORING MÉMOIRE A21S
    private var memoryMonitoringJob: Job? = null
    private var lastMemoryWarning = 0L
    
    // Gestion suppression par mots (appui long Delete)
    private var deleteTimer: Timer? = null
    private var deleteHandler = Handler(Looper.getMainLooper())
    private var isDeleteLongPressActive = false

    // Glissement du curseur sur la barre d'espace (v14.0.0)
    private val handlerCurseur = Handler(Looper.getMainLooper())
    private var syncCurseurEnAttente: Runnable? = null
    private var glissementCurseur = false

    // Dernière position de curseur rapportée par le framework, conservée pour
    // que la resynchronisation différée d'un glissement travaille sur la
    // position d'arrivée et non sur celle du départ.
    private var dernierSelStart = 0
    private var dernierSelEnd = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== KREYOL IME SERVICE REFACTORISÉ onCreate() ===")
        
        // 🔍 DIAGNOSTIC SAMSUNG A21S: Informations système détaillées
        logSystemInfo()
        
        try {
            initializeComponents()
            Log.d(TAG, "✅ Service initialisé avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERREUR CRITIQUE lors de l'initialisation: ${e.message}", e)
            // Log stack trace complète pour A21s debugging
            Log.e(TAG, "Stack trace complète:", e)
        }
    }
    
    /**
     * 🔍 DIAGNOSTIC A21S: Log des informations système pour debugging
     */
    private fun logSystemInfo() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            
            Log.d(TAG, "📊 DIAGNOSTIC SYSTÈME A21S:")
            Log.d(TAG, "  • RAM totale: ${memInfo.totalMem / (1024 * 1024)}MB")
            Log.d(TAG, "  • RAM disponible: ${memInfo.availMem / (1024 * 1024)}MB")
            Log.d(TAG, "  • Seuil low memory: ${memInfo.threshold / (1024 * 1024)}MB")
            Log.d(TAG, "  • Low RAM device: ${activityManager.isLowRamDevice}")
            Log.d(TAG, "  • Memory class: ${activityManager.memoryClass}MB")
            Log.d(TAG, "  • Large memory class: ${activityManager.largeMemoryClass}MB")
            
            // Informations Android
            Log.d(TAG, "  • Android SDK: ${android.os.Build.VERSION.SDK_INT}")
            Log.d(TAG, "  • Model: ${android.os.Build.MODEL}")
            Log.d(TAG, "  • Manufacturer: ${android.os.Build.MANUFACTURER}")
            Log.d(TAG, "  • Device: ${android.os.Build.DEVICE}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur diagnostic système: ${e.message}", e)
        }
    }
    
    /**
     * 🔍 MONITORING MÉMOIRE A21S: Surveillance continue pour détecter les fuites
     */
    private fun startMemoryMonitoring() {
        memoryMonitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val memInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memInfo)
                    
                    val availableMB = memInfo.availMem / (1024 * 1024)
                    val lowMemThresholdMB = memInfo.threshold / (1024 * 1024)
                    
                    // Alerter si mémoire critique (seulement toutes les 30 secondes)
                    val now = System.currentTimeMillis()
                    if (availableMB < lowMemThresholdMB && (now - lastMemoryWarning) > 30000) {
                        Log.w(TAG, "⚠️ A21S MÉMOIRE CRITIQUE: ${availableMB}MB disponible (seuil: ${lowMemThresholdMB}MB)")
                        lastMemoryWarning = now
                        
                        // Suggestion de nettoyage sur A21s
                        System.gc()
                        Log.d(TAG, "🔧 Garbage collection forcé pour A21s")
                    }
                    
                    delay(10000) // Vérifier toutes les 10 secondes
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur monitoring mémoire A21s: ${e.message}", e)
                    delay(30000) // En cas d'erreur, attendre plus longtemps
                }
            }
        }
    }
    
    /**
     * Initialise tous les composants modulaires
     * 🔧 FIX SAMSUNG A21S: Initialisation adaptative selon les capacités de l'appareil
     */
    private fun initializeComponents() {
        val isLowEnd = isLowEndDevice(this)
        Log.d(TAG, if (isLowEnd) "🔧 Appareil détecté: Low-end (A21s compatible)" else "🚀 Appareil détecté: Standard")
        
        // Créer les gestionnaires
        keyboardLayoutManager = KeyboardLayoutManager(this).apply {
            setInteractionListener(this@KreyolInputMethodServiceRefactored)
        }
        
        suggestionEngine = SuggestionEngine(this).apply {
            setSuggestionListener(this@KreyolInputMethodServiceRefactored)
        }
        
        accentHandler = AccentHandler(this).apply {
            setAccentSelectionListener(this@KreyolInputMethodServiceRefactored)
        }
        // Permet à KeyboardLayoutManager d'afficher un aperçu des options
        // d'appui long dans les coins des touches (v8.3.0)
        keyboardLayoutManager.accentHandler = accentHandler
        
        inputProcessor = InputProcessor(this).apply {
            setInputProcessorListener(this@KreyolInputMethodServiceRefactored)
        }
        
        // 🎮 Gamification: Initialiser le tracking d'utilisation du vocabulaire
        dictionaryWithUsage = CreoleDictionaryWithUsage(this)

        // Les compteurs d'utilisation ne servaient qu'aux écrans de statistiques :
        // le moteur classait les suggestions sur la seule fréquence du corpus, donc
        // sur ce que le kréyòl écrit emploie en général plutôt que sur ce que cet
        // utilisateur-ci écrit. Les rebrancher sur le scoring fait remonter son
        // vocabulaire propre, sans rien envoyer hors de l'appareil.
        suggestionEngine.setUsageCountProvider { word ->
            dictionaryWithUsage.getWordUsageCount(word)
        }

        // Connecter le listener de tracking au InputProcessor
        inputProcessor.setWordCommitListener(object : WordCommitListener {
            override fun onWordCommitted(word: String) {
                Log.d(TAG, "🔍 onWordCommitted appelé avec: '$word'")

                if (isSensitiveField()) {
                    // Champ de mot de passe ou saisie explicitement non
                    // mémorisable : aucun comptage. Sortie avant toute écriture,
                    // y compris l'horodatage du tunnel.
                    Log.d(TAG, "🔒 Champ sensible: mot ignoré")
                    return
                }

                // Tunnel d'activation local : horodater le tout premier mot
                // commité (diagnostic affiché dans À Propos, rien ne sort du
                // téléphone)
                val funnelPrefs = getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
                if (!funnelPrefs.contains("funnel_first_word")) {
                    funnelPrefs.edit().putLong("funnel_first_word", System.currentTimeMillis()).apply()
                }

                // Tracker le mot dans le dictionnaire (seulement si présent)
                val result = dictionaryWithUsage.trackWordUsage(word)
                Log.d(TAG, "🎯 Résultat tracking '$word': ${result.tracked}")

                if (result.tracked) {
                    Log.d(TAG, "🎮 Gamification: Mot tracké '$word'")
                }

                // Un passage de niveau ne peut survenir que si le mot vient
                // d'être découvert : inutile de refaire le calcul à chaque mot
                // validé, ce qui se verrait à la frappe.
                if (result.newlyDiscovered) {
                    maybeNotifyLevelUp()
                }
            }
        })
        
        Log.d(TAG, "✅ Gamification initialisée avec tracking du vocabulaire")
        
        // 🔧 FIX SAMSUNG A21S: Initialisation adaptative selon les capacités
        serviceScope.launch {
            try {
                if (isLowEnd) {
                    // Sur A21s: Initialisation graduelle pour éviter les pics de mémoire
                    Log.d(TAG, "🔧 Initialisation optimisée A21s - Chargement graduel")
                    delay(500) // Laisser le service se stabiliser
                    suggestionEngine.initialize()
                    delay(200) // Pause entre les étapes
                } else {
                    // Appareils standard: Initialisation normale
                    suggestionEngine.initialize()
                }
                isInitialized = true
                Log.d(TAG, "✅ Moteur de suggestions initialisé (mode: ${if (isLowEnd) "A21s optimisé" else "standard"})")

                // 🟢🔵 Support bilingue Kreyòl + Français (Kreyòl-first, Français dès 3 lettres)
                suggestionEngine.enableBilingualSupport()
                Log.d(TAG, "🎯 Mode suggestions bilingue avec AccentTolerantMatching activé")
                
                // Démarrer monitoring mémoire sur A21s
                if (isLowEnd) {
                    startMemoryMonitoring()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur initialisation suggestions: ${e.message}", e)
                // Sur A21s, réessayer avec un mode plus conservateur
                if (isLowEnd && !isInitialized) {
                    Log.d(TAG, "🔧 Tentative de récupération pour A21s...")
                    delay(1000)
                    try {
                        suggestionEngine.initialize()
                        isInitialized = true
                        suggestionEngine.enableBilingualSupport()
                        Log.d(TAG, "✅ Récupération A21s réussie + support bilingue activé")
                    } catch (e2: Exception) {
                        Log.e(TAG, "❌ Échec récupération A21s: ${e2.message}", e2)
                    }
                }
            }
        }
    }
    
    override fun onCreateInputView(): View? {
        Log.d(TAG, "onCreateInputView() appelée")
        
        // 🅰️ FORCER LE MODE ALPHABÉTIQUE AU DÉMARRAGE
        keyboardLayoutManager.forceAlphabeticMode()
        Log.d(TAG, "✅ Mode alphabétique forcé lors de la création du clavier")

        // La palette est résolue avant de poser la moindre couleur, et retenue :
        // c'est elle que onStartInputView() comparera pour savoir si la vue
        // gardée en cache par InputMethodService est encore à la bonne couleur.
        KeyboardTheme.refresh(this)
        paletteDeLaVue = KeyboardTheme.palette()
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(KeyboardTheme.palette().fondClavier)
        }
        
        // Créer la zone de suggestions
        createSuggestionsArea(mainLayout)
        
        // 📱 Conteneur dont le bas se décale de ce que la barre de navigation
        // masque réellement du clavier, mesuré après la mise en page (voir
        // adjustForNavigationBarOverlap). Zéro tant que rien ne prouve un
        // recouvrement : le système place déjà la fenêtre de saisie au-dessus de
        // la barre, et réserver la place une seconde fois laissait une bande
        // vide entre la dernière rangée et le bas de l'écran.
        val keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            // Aucun fond ici : celui de mainLayout suffit. C'est la barre de
            // suggestions qui porte le relief depuis la v17.0.0, en creux, et
            // le fond du clavier lui sert de margelle.
        }
        mainLayout.post { adjustForNavigationBarOverlap(mainLayout, keyboardContainer) }

        // Créer le clavier principal, dimensionné pour la place que la fenêtre
        // IME accordera réellement (voir availableRowsHeightPx)
        keyboardLayoutManager.setAvailableRowsHeight(computeAvailableRowsHeight())
        val keyboardLayout = keyboardLayoutManager.createKeyboardLayout()
        keyboardContainer.addView(keyboardLayout)
        mainLayout.addView(keyboardContainer)
        mainKeyboardView = keyboardContainer

        return mainLayout
    }
    
    /**
     * Crée la zone des suggestions : deux rangées empilées (Kreyòl puis Français) pour
     * que le français reste toujours entièrement visible, sans scroll ni troncature,
     * même quand les suggestions kreyòl sont longues ("Bonmaten-la"). La rangée
     * française reste toujours réservée en hauteur (INVISIBLE, jamais GONE) même
     * quand elle est vide : un GONE/VISIBLE dynamique décale toutes les rangées du
     * clavier en dessous pendant la frappe (ex. au franchissement du seuil de 3
     * lettres qui active le fallback français), ce qui fait atterrir un tap en cours
     * sur la touche de la rangée voisine — bug constaté et corrigé le 23/07/2026.
     */
    private fun createSuggestionsArea(parentLayout: LinearLayout) {
        val rowHeightPx = dpToPx(suggestionRowHeightDp())

        val suggestionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // Voir SUGGESTION_BAR_INSET_DP : latéral seulement, la hauteur
                // consommée doit rester celle que le budget vertical prévoit.
                marginStart = dpToPx(SUGGESTION_BAR_INSET_DP)
                marginEnd = dpToPx(SUGGESTION_BAR_INSET_DP)
            }
            background = KeyboardTheme.cuvetteSuggestions(this@KreyolInputMethodServiceRefactored)
        }

        val kreyolScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                rowHeightPx
            )
            // Le padding bas porte la moitié de l'intervalle qui sépare une puce
            // kréyòl de la puce française juste en dessous, l'autre moitié venant du
            // padding haut de la rangée française. En paysage cette rangée n'existe
            // pas : rien à séparer, et la hauteur disponible y est trop courte pour
            // la dépenser en vide.
            setPadding(
                dpToPx(8),
                dpToPx(SUGGESTION_ROW_OUTER_PAD_DP),
                dpToPx(8),
                dpToPx(suggestionRowInnerPadDp())
            )
        }
        kreyolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        kreyolScroll.addView(kreyolRow)
        suggestionsContainer.addView(kreyolScroll)

        // En paysage la seconde rangée n'est pas construite du tout : les suggestions
        // françaises rejoignent la rangée kreyòl, qui défile horizontalement et où
        // l'étiquette de langue et la couleur des puces les distinguent déjà. Le
        // décalage des rangées de touches que la réserve permanente évitait ne peut
        // pas se produire ici, puisque la mise en page est figée pour l'orientation :
        // rien n'apparaît ni ne disparaît pendant la frappe.
        // Le service survit aux rotations : sans cette remise à null, ces champs
        // gardent la rangée française construite lors d'une mise en page portrait
        // précédente, vue détachée de la hiérarchie affichée. Les suggestions
        // françaises y étaient bien ajoutées, mais dans le vide, et disparaissaient
        // de l'écran dès la première rotation.
        frenchRow = null
        frenchRowScroll = null

        if (!isLandscape()) {
            val frScroll = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    rowHeightPx
                )
                // Moitié haute de l'intervalle entre les deux rangées, cf. kreyolScroll.
                setPadding(
                    dpToPx(8),
                    dpToPx(suggestionRowInnerPadDp()),
                    dpToPx(8),
                    dpToPx(SUGGESTION_ROW_OUTER_PAD_DP)
                )
                visibility = View.INVISIBLE
            }
            frenchRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            frScroll.addView(frenchRow)
            frenchRowScroll = frScroll
            suggestionsContainer.addView(frScroll)
        }

        // Alias historique : les modes non-bilingues (prédictions contextuelles) affichent
        // dans la rangée du haut, la rangée française reste masquée dans ce cas.
        suggestionsView = kreyolRow

        parentLayout.addView(suggestionsContainer)
    }

    /** Hauteur d'une rangée de suggestions, réduite en paysage. */
    private fun suggestionRowHeightDp(): Int =
        if (isLandscape()) SUGGESTION_ROW_HEIGHT_LANDSCAPE_DP else SUGGESTION_ROW_HEIGHT_DP

    /** Nombre de rangées de suggestions réellement empilées : une seule en paysage. */
    private fun suggestionRowCount(): Int = if (isLandscape()) 1 else 2

    /**
     * Réserve verticale du côté intérieur d'une rangée : la moitié de l'écart qui
     * sépare les deux rangées empilées. En paysage il n'y a qu'une rangée, donc
     * rien à séparer, et la hauteur y est trop courte pour la dépenser en vide.
     */
    private fun suggestionRowInnerPadDp(): Int =
        if (suggestionRowCount() > 1) SUGGESTION_CHIP_GAP_DP / 2 else 2

    /**
     * Hauteur réellement occupée par une puce : la rangée moins ce que son
     * conteneur réserve en haut et en bas. Les puces sont en MATCH_PARENT, donc
     * c'est aussi la place dont dispose le mot qu'elles portent.
     */
    private fun suggestionChipHeightPx(): Int =
        dpToPx(suggestionRowHeightDp()) - dpToPx(SUGGESTION_ROW_OUTER_PAD_DP) - dpToPx(suggestionRowInnerPadDp())

    /**
     * Ramène la police d'une puce à ce que sa hauteur peut afficher en entier.
     *
     * La taille demandée est en sp : elle suit donc l'échelle de police du
     * système, alors que la puce, elle, est en dp et ne bouge pas. Au réglage
     * « Grande » d'Android, la ligne de texte devenait plus haute que la puce et
     * TextView la rognait à hauteur du padding : mesuré sur émulateur à
     * l'échelle 1,3, il restait 29 px de vide au-dessus du mot contre 16 en
     * dessous, jambages de « j », « q » et « g » coupés net. Le mot paraissait
     * posé trop bas dans sa puce alors qu'il était simplement trop grand pour
     * elle.
     *
     * Les touches du clavier règlent le même problème en dérivant leur police de
     * la hauteur de touche (cf. KeyboardLayoutManager) ; ici on garde la taille
     * en sp tant qu'elle tient, et on ne la réduit qu'au-delà, pour respecter le
     * réglage de l'utilisateur aussi loin que la barre le permet.
     *
     * La hauteur de ligne se lit sur la police elle-même (ascent/descent, sans
     * la réserve de police puisque includeFontPadding est désactivé), et non sur
     * un ratio écrit en dur qui vieillirait mal si la police changeait.
     */
    private fun fitTextToChipHeight(view: TextView) {
        val dispoPx = suggestionChipHeightPx() - 2 * dpToPx(SUGGESTION_CHIP_PADDING_V_DP)
        val metrics = view.paint.fontMetricsInt
        val lignePx = metrics.descent - metrics.ascent
        if (lignePx > dispoPx && dispoPx > 0) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize * dispoPx / lignePx)
        }
    }

    /**
     * Demi-intervalle porté par chaque puce de suggestion. Deux puces voisines
     * portent chacune la leur, donc le vide effectif entre elles vaut
     * SUGGESTION_CHIP_GAP_DP.
     */
    private fun suggestionChipHalfGapPx(): Int = dpToPx(SUGGESTION_CHIP_GAP_DP) / 2

    private fun isLandscape(): Boolean = KeyboardLayoutManager.isLandscape(this)
    
    // ===== IMPLÉMENTATION KeyboardInteractionListener =====
    
    override fun onKeyPress(key: String) {
        Log.d(TAG, "=== TOUCHE PRESSÉE: '$key' ===")
        
        // 🌐 BOUTON GLOBE - TEMPORAIREMENT DÉSACTIVÉ (bug système Android)
        // TODO: Réactiver quand le problème système sera résolu
        /*
        if (key == "🌐") {
            Log.d(TAG, "🌐 BOUTON GLOBE DÉTECTÉ - INTERCEPTION DIRECTE!")
            try {
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showInputMethodPicker()
                Log.d(TAG, "✅ Globe intercepté avec succès: InputMethod Picker affiché")
                return // Arrêter ici pour éviter le traitement normal qui cause le crash
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors de l'interception du bouton Globe: ${e.message}", e)
                return // Même en cas d'erreur, ne pas continuer pour éviter le crash
            }
        }
        */
        
        if (accentHandler.isLongPressActive()) {
            Log.d(TAG, "Appui long actif - ignorer l'appui court")
            return
        }
        
        Log.d(TAG, "InputProcessor initialisé: ${::inputProcessor.isInitialized}")
        Log.d(TAG, "Traitement de la touche par InputProcessor")
        inputProcessor.processKeyPress(key)
        Log.d(TAG, "=== FIN TRAITEMENT TOUCHE ===")
    }
    
    override fun onLongPress(key: String, button: View) {
        Log.d(TAG, "🔗 Appui long sur: $key")
        
        when (key) {
            "⌫" -> {
                // Suppression par mots avec appui long sur Delete
                Log.d(TAG, "🗑️ Démarrage suppression par mots (Delete)")
                startWordDeletion()
            }
            " " -> {
                // 🌐 Appui long sur barre d'espace : afficher le sélecteur de claviers
                // (plutôt que de basculer silencieusement vers le "prochain" clavier,
                // ce qui surprenait l'utilisateur en changeant d'IME sans prévenir)
                Log.d(TAG, "🌐 Appui long sur barre d'espace - Affichage du sélecteur de claviers")

                val shouldSwitch = inputProcessor.processSpaceLongPress()
                if (shouldSwitch) {
                    showKeyboardPicker()
                }
            }
            else -> {
                // Gestion des accents pour les autres touches
                if (accentHandler.hasAccents(key)) {
                    accentHandler.startLongPressTimer(key, button)
                }
            }
        }
    }
    
    override fun onKeyRelease() {
        // Arrêter la suppression par mots si active
        stopWordDeletion()
        
        // Arrêter les accents
        accentHandler.cancelLongPress()
    }

    /**
     * Le doigt glisse sur la barre d'espace : le curseur suit, caractère par
     * caractère (v14.0.0).
     */
    override fun onSpaceCursorMove(steps: Int) {
        glissementCurseur = true
        inputProcessor.moveCursorBy(steps)
    }

    /**
     * Le doigt se lève : on resynchronise le mot suivi une seule fois, sur la
     * position d'arrivée.
     *
     * Le rappel est différé plutôt qu'immédiat parce que les événements de
     * touche directionnelle émis par [InputProcessor.moveCursorBy] sont
     * asynchrones : au moment où le doigt se lève, le dernier `onUpdateSelection`
     * du glissement est encore en vol, et resynchroniser tout de suite prendrait
     * l'avant-dernière position.
     */
    override fun onSpaceCursorEnd() {
        planifierSyncCurseur()
    }

    /**
     * Programme l'unique resynchronisation qui clôt un glissement, en écrasant
     * celle éventuellement en attente.
     *
     * Sans cette temporisation, chaque caractère franchi relançait un calcul de
     * suggestions : traverser une phrase en faisait défiler trente, et la barre
     * clignotait pendant tout le geste alors que la seule position qui compte
     * est celle où le doigt s'arrête.
     */
    private fun planifierSyncCurseur() {
        syncCurseurEnAttente?.let { handlerCurseur.removeCallbacks(it) }
        val tache = Runnable {
            syncCurseurEnAttente = null
            glissementCurseur = false
            if (::inputProcessor.isInitialized) {
                inputProcessor.syncWordWithCursor(dernierSelStart, dernierSelEnd)
            }
        }
        syncCurseurEnAttente = tache
        handlerCurseur.postDelayed(tache, DELAI_SYNC_CURSEUR)
    }
    
    // ===== IMPLÉMENTATION SuggestionListener =====
    
    override fun onSuggestionsReady(suggestions: List<String>) {
        // 📝 RETOUR AUX SUGGESTIONS SIMPLES - Couleurs d'origine
        Log.d(TAG, "📝 Affichage suggestions simples: ${suggestions.joinToString(", ")}")
        displaySuggestions(suggestions)
    }
    
    override fun onBilingualSuggestionsReady(suggestions: List<BilingualSuggestion>) {
        Log.d(TAG, "🎯 Affichage suggestions bilingues: ${suggestions.joinToString(", ") { "${it.word}(${it.language})" }}")
        displayBilingualSuggestions(suggestions)
    }
    
    override fun onDictionaryLoaded(wordCount: Int) {
        Log.d(TAG, "🟢 Dictionnaire kreyòl chargé: $wordCount mots")
    }
    
    override fun onFrenchDictionaryLoaded(wordCount: Int) {
        Log.d(TAG, "🔵 Dictionnaire français chargé: $wordCount mots")
    }
    
    override fun onNgramModelLoaded() {
        Log.d(TAG, "🟢 Modèle N-gram kreyòl chargé")
    }
    
    override fun onModeChanged(newMode: SuggestionEngine.SuggestionMode) {
        Log.d(TAG, "Mode de suggestion changé: $newMode")
        // Ici on pourrait mettre à jour l'interface si nécessaire
    }
    
    // ===== IMPLÉMENTATION AccentSelectionListener =====
    
    override fun onAccentSelected(accent: String, baseCharacter: String) {
        Log.d(TAG, "🎯 onAccentSelected appelé - accent: '$accent', base: '$baseCharacter'")
        
        val inputConnection = currentInputConnection
        if (inputConnection != null) {
            val textBefore = inputConnection.getTextBeforeCursor(10, 0)?.toString() ?: ""
            Log.d(TAG, "📝 Texte avant accent: '$textBefore'")
            
            // ✅ BUG FIX CORRECT: Ajouter l'accent directement 
            // Le caractère de base n'a pas été ajouté à cause de l'appui long
            inputConnection.commitText(accent, 1)
            Log.d(TAG, "✅ Accent '$accent' ajouté (remplace '$baseCharacter' conceptuel)")

            // Mettre à jour le mot courant en ajoutant l'accent — uniquement
            // pour un vrai caractère de mot (lettre accentuée, digraphe...).
            // Depuis l'ajout du panneau emoji (v10.1.0), cette même popup sert
            // aussi à choisir un ton de peau ; un emoji n'est pas une lettre et
            // ne doit pas polluer le suivi du mot en cours utilisé par les
            // suggestions (sinon prochaine recherche dictionnaire faite avec
            // un préfixe du genre "🥭ka").
            if (accent.all { it.isLetter() }) {
                val currentWord = inputProcessor.getCurrentWord()
                val updatedWord = currentWord + accent
                // setCurrentWord() et non une mise à jour muette : c'est le
                // rappel onWordChanged() qui régénère les suggestions, exactement
                // comme pour une lettre tapée normalement. Sans lui, un mot
                // commencé par un digraphe choisi en appui long ("tj", "dj",
                // "ch"...) n'obtenait jamais de propositions.
                inputProcessor.setCurrentWord(updatedWord)
                Log.d(TAG, "✅ Mot mis à jour: '$currentWord' + '$accent' → '$updatedWord'")
            } else {
                // Un emoji, donc un ton de peau choisi en appui long dans le
                // panneau : il ne passe pas par onEmojiSelected, il faut le
                // retenir ici sans quoi la variante choisie ne rejoindrait
                // jamais les récents, seule la variante par défaut le ferait.
                EmojiRecents.enregistrer(this, accent)
                inputProcessor.finalizeCurrentWordFromEmoji()
            }
            
            // 🔍 DIAGNOSTIC: Vérifier l'état final
            val textAfter = inputConnection.getTextBeforeCursor(10, 0)?.toString() ?: ""
            Log.d(TAG, "📝 Texte après accent: '$textAfter'")
        }
        
        Log.d(TAG, "✅ onAccentSelected terminé - BUG FIX v2 appliqué")
    }
    
    override fun onLongPressStarted(baseKey: String) {
        Log.d(TAG, "Appui long démarré pour: $baseKey")
    }
    
    override fun onLongPressCancelled() {
        Log.d(TAG, "Appui long annulé")
    }
    
    // ===== IMPLÉMENTATION InputProcessorListener =====
    
    override fun onWordChanged(word: String) {
        Log.d(TAG, "onWordChanged appelé avec: '$word'")
        if (word.isNotEmpty() && isInitialized) {
            Log.d(TAG, "� Génération suggestions SIMPLES pour: '$word'")
            suggestionEngine.setSuggestionMode(SuggestionEngine.SuggestionMode.DICTIONARY)
            suggestionEngine.generateDictionarySuggestions(word)  // Retour méthode simple
        } else {
            Log.d(TAG, "Affichage de suggestions vides (mot vide ou non initialisé)")
            displaySuggestions(emptyList())
        }
    }
    
    override fun onWordCompleted(word: String) {
        Log.d(TAG, "Mot complété: '$word' - Ajout à l'historique")
        suggestionEngine.addWordToHistory(word)
        
        // 🔧 FIX SAMSUNG A21S: Utiliser serviceScope et réduire le délai
        serviceScope.launch {
            delay(100) // Délai réduit pour A21s (performance limitée)
            Log.d(TAG, "Génération suggestions contextuelles après '$word'")
            suggestionEngine.setSuggestionMode(SuggestionEngine.SuggestionMode.CONTEXTUAL)
            suggestionEngine.generateContextualSuggestions()
        }
    }
    
    override fun onModeChanged(isNumeric: Boolean, isEmoji: Boolean, isCapital: Boolean, isCapsLock: Boolean) {
        // ✅ Vérifier si le mode numérique ou emoji a changé (avant de mettre à jour les états)
        val currentNumericMode = keyboardLayoutManager.isNumericMode()
        val currentEmojiMode = keyboardLayoutManager.isEmojiMode()
        val needsLayoutRefresh = currentNumericMode != isNumeric || currentEmojiMode != isEmoji

        // ✅ CORRECTION: Mettre à jour les états AVANT l'affichage
        keyboardLayoutManager.updateKeyboardStates(isNumeric, isEmoji, isCapital, isCapsLock)
        
        // Mettre à jour l'état du mode majuscule dans AccentHandler
        accentHandler.isCapitalMode = isCapital || isCapsLock
        
        // Mettre à jour l'affichage du clavier
        keyboardLayoutManager.updateKeyboardDisplay()
        
        // Si on change vers le mode numérique, recréer le layout
        if (needsLayoutRefresh) {
            refreshKeyboardLayout()
        }
    }
    
    override fun onSpecialKeyPressed(key: String) {
        Log.d(TAG, "Touche spéciale: $key")
        // Traitement supplémentaire si nécessaire
    }
    
    /**
     * Affiche les suggestions dans la barre de suggestions (mode simple : dictionnaire
     * hors bilingue, ou prédictions contextuelles n-gram — toutes deux Kreyòl uniquement).
     * Réutilise le même style de puce pleine arrondie que le mode bilingue, pour éviter
     * qu'un second look (l'ancien rectangle bleu pastel) ne cohabite avec le premier.
     */
    private fun displaySuggestions(suggestions: List<String>) {
        Log.d(TAG, "displaySuggestions appelé avec ${suggestions.size} suggestions: ${suggestions.joinToString(", ")}")
        // Mode simple : pas de français, la 2e rangée reste masquée (mais réservée en hauteur)
        frenchRow?.removeAllViews()
        frenchRowScroll?.visibility = View.INVISIBLE
        suggestionsView?.let { container ->
            Log.d(TAG, "Container de suggestions trouvé, vidage des vues existantes")
            container.removeAllViews()

            suggestions.take(MAX_SUGGESTIONS).forEachIndexed { rang, suggestion ->
                // Un filet à partir de la troisième : il sépare deux mots nus,
                // pas une pastille de son voisin, dont la forme suffit.
                if (rang >= 2) addChipDivider(container)
                addSuggestionChip(
                    container,
                    BilingualSuggestion(suggestion, 0f, SuggestionLanguage.KREYOL),
                    premier = rang == 0
                )
            }
        }
    }
    
    /**
     * 🎯 Affiche les suggestions bilingues sur deux rangées empilées (Kreyòl en haut,
     * Français en bas) : le français reste toujours entièrement visible, sans scroll
     * ni troncature, même quand une suggestion kreyòl est longue ("Bonmaten-la"). La
     * rangée française se masque (hauteur nulle) quand elle est vide.
     */
    private fun displayBilingualSuggestions(suggestions: List<BilingualSuggestion>) {
        Log.d(TAG, "displayBilingualSuggestions appelé avec ${suggestions.size} suggestions bilingues")
        val kreyolContainer = kreyolRow ?: return
        // En paysage il n'y a qu'une rangée : les deux langues la partagent.
        val frenchContainer = frenchRow ?: kreyolContainer

        kreyolContainer.removeAllViews()
        frenchContainer.removeAllViews()

        val kreyolSuggestions = suggestions.filter { it.language == SuggestionLanguage.KREYOL }
        val frenchSuggestions = suggestions.filter { it.language == SuggestionLanguage.FRENCH }

        if (kreyolSuggestions.isNotEmpty()) {
            addLanguageLabel(kreyolContainer, kreyolSuggestions.first().getShortLabel())
            kreyolSuggestions.forEachIndexed { rang, suggestion ->
                if (rang >= 2) addChipDivider(kreyolContainer)
                addSuggestionChip(kreyolContainer, suggestion, premier = rang == 0)
            }
        }

        if (frenchSuggestions.isNotEmpty()) {
            addLanguageLabel(frenchContainer, frenchSuggestions.first().getShortLabel())
            // Chaque rangée a son propre rang : la première proposition
            // française est pleine elle aussi, ce qui garde à l'écran les deux
            // couleurs de langue.
            frenchSuggestions.forEachIndexed { rang, suggestion ->
                if (rang >= 2) addChipDivider(frenchContainer)
                addSuggestionChip(frenchContainer, suggestion, premier = rang == 0)
            }
        }
        // INVISIBLE (jamais GONE) : garder la hauteur de la rangée réservée en permanence
        // évite que l'apparition/disparition des suggestions françaises ne décale les
        // rangées du clavier en dessous pendant la frappe (cf. bug du 23/07/2026).
        frenchRowScroll?.visibility = if (frenchSuggestions.isNotEmpty()) View.VISIBLE else View.INVISIBLE

        Log.d(TAG, "✅ ${suggestions.size} suggestions bilingues affichées (${kreyolSuggestions.size} Kreyòl / ${frenchSuggestions.size} Français)")
    }

    /**
     * Ajoute le micro-label (KR/FR) en tête d'une rangée de suggestions
     */
    private fun addLanguageLabel(container: LinearLayout, label: String) {
        val groupLabel = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(KeyboardTheme.palette().encreEtiquette)
            setPadding(dpToPx(4), 0, dpToPx(2), 0)
            // Le centrage vertical se joue ici, sur la vue : la vue occupe toute la
            // hauteur de la rangée (MATCH_PARENT), donc le layout_gravity posé sur
            // ses LayoutParams n'a rien à décaler et l'étiquette restait collée en
            // haut de la rangée, à côté de puces dont le mot, lui, est centré.
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(groupLabel)
    }

    /**
     * Le filet vertical qui sépare deux propositions nues (v17.0.0).
     *
     * À mi-hauteur de la puce, pas sur toute la rangée : un trait pleine
     * hauteur découperait le plateau en cases au lieu de séparer deux mots.
     */
    private fun addChipDivider(container: LinearLayout) {
        val trait = View(this).apply {
            setBackgroundColor(KeyboardTheme.palette().bordure)
            layoutParams = LinearLayout.LayoutParams(
                maxOf(1, dpToPx(1)),
                suggestionChipHeightPx() / 2
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
        }
        container.addView(trait)
    }

    /**
     * Ajoute une proposition dans une rangée.
     *
     * Depuis la v17.0.0, **le remplissage dit le rang et non la langue**. La
     * barre encodait la langue trois fois, par la rangée, par l'étiquette KR/FR
     * et par la couleur de la pastille, et n'encodait le rang nulle part, alors
     * que c'est tout le travail de [SuggestionEngine] : fréquence du corpus,
     * n-grammes, usage personnel, tolérance aux fautes de frappe. La première
     * proposition, celle qui a gagné ce calcul, ressemblait à la troisième.
     *
     * Seule la première de chaque rangée garde donc sa pastille pleine ; les
     * suivantes sont posées à nu sur le plateau, avec la même zone tactile. La
     * langue reste dite par la rangée et par l'étiquette, qui deviennent du
     * coup porteuses : les retirer redeviendrait ambigu.
     */
    private fun addSuggestionChip(
        container: LinearLayout,
        bilingualSuggestion: BilingualSuggestion,
        premier: Boolean
    ) {
        val suggestionButton = Button(this).apply {
            text = bilingualSuggestion.word
            textSize = SUGGESTION_TEXT_SIZE_SP
            // Sans cela, la ligne réserve au-dessus et au-dessous du mot les
            // parties de la police qu'aucune lettre latine n'atteint, et comme
            // cette réserve est plus épaisse en haut qu'en bas, le mot centré
            // paraît posé trop bas dans sa puce (mesuré : 29 px de vide au-dessus
            // contre 14 en dessous).
            includeFontPadding = false

            if (premier) {
                val bgColor = bilingualSuggestion.getColor()
                setTextColor(KeyboardColors.CHIP_TEXT)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(16).toFloat()
                    setColor(bgColor)
                }
                val colorHex = String.format("#%06X", 0xFFFFFF and bgColor)
                Log.d(TAG, "🎨 '${bilingualSuggestion.word}' en tête : ${bilingualSuggestion.getLanguageName()} → fond $colorHex")
            } else {
                // Nu sur le plateau. `background = null` et non un fond
                // transparent : le style Button pose sinon son propre relief,
                // qui redonnerait à la proposition l'aspect d'une pastille.
                setTextColor(KeyboardTheme.palette().encre)
                background = null
            }

            setPadding(
                dpToPx(14),
                dpToPx(SUGGESTION_CHIP_PADDING_V_DP),
                dpToPx(14),
                dpToPx(SUGGESTION_CHIP_PADDING_V_DP)
            )
            // Après le padding, qui borne la place restante pour le mot.
            fitTextToChipHeight(this)
            // Le son de frappe est joué par KeyFeedback : sans cette ligne,
            // performClick() ajouterait son clic d'interface et la puce sonnerait deux fois.
            isSoundEffectsEnabled = false

            minWidth = dpToPx(SUGGESTION_CHIP_MIN_WIDTH_DP)
            minimumWidth = dpToPx(SUGGESTION_CHIP_MIN_WIDTH_DP)
            gravity = android.view.Gravity.CENTER

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                // La moitié de l'intervalle de chaque côté : deux puces voisines
                // portent chacune la sienne, et leur somme fait l'écart voulu.
                setMargins(suggestionChipHalfGapPx(), 0, suggestionChipHalfGapPx(), 0)
            }

            setOnClickListener { chip ->
                // v10.11.6 : la vibration manquait ici. Les touches en donnaient une,
                // pas les puces, alors que valider une proposition est l'action
                // principale de qui écrit à un ou deux appuis par mot : le geste le
                // plus important était le seul sans confirmation tactile.
                //
                // Au clic et non au toucher, contrairement aux touches : glisser hors
                // d'une puce avant de relâcher annule la sélection, et cette sortie de
                // secours ne doit pas vibrer comme si le mot avait été écrit. La
                // vibration signifie ici « le mot est écrit », et rien d'autre.
                // Vérifié sur émulateur en comptant les vibrations enregistrées par
                // dumpsys vibrator_manager : un geste annulé n'en produit aucune.
                //
                // Nuance mesurée le même jour : l'annulation fonctionne pour tout
                // glissement qui reste dans la fenêtre du clavier (puce voisine,
                // intervalle entre deux puces, rangées de touches), mais **pas** vers
                // le haut hors du clavier. Le doigt quitte alors la fenêtre, qui ne
                // reçoit plus d'événements de déplacement, donc la vue ne se sait
                // jamais quittée et le clic part au relâchement.
                KeyFeedback.onKeyPress(chip)

                inputProcessor.processSuggestionSelection(bilingualSuggestion.word)

                // 🔧 FIX SAMSUNG A21S: Performance optimisée
                serviceScope.launch {
                    delay(150)
                    suggestionEngine.setSuggestionMode(SuggestionEngine.SuggestionMode.CONTEXTUAL)
                    suggestionEngine.generateContextualSuggestions()
                }
            }
        }

        container.addView(suggestionButton)
    }

    /**
     * Actualise le layout du clavier en préservant le conteneur avec padding
     */
    private fun refreshKeyboardLayout() {
        mainKeyboardView?.let { containerView ->
            // mainKeyboardView est le conteneur avec padding, pas le clavier directement
            if (containerView is LinearLayout && containerView.childCount > 0) {
                // Retirer l'ancien clavier du conteneur
                val oldKeyboard = containerView.getChildAt(0)
                containerView.removeView(oldKeyboard)
                
                // Créer et ajouter le nouveau clavier dans le même conteneur
                val newKeyboard = keyboardLayoutManager.createKeyboardLayout()
                containerView.addView(newKeyboard)
                
                Log.d(TAG, "🔄 Clavier actualisé (padding préservé: ${containerView.paddingBottom}px)")
            } else {
                Log.w(TAG, "⚠️ mainKeyboardView n'est pas un conteneur LinearLayout valide")
            }
        }
    }
    
    // ===== MÉTHODES DE CYCLE DE VIE =====
    
    /**
     * Champ dont le contenu ne doit jamais être conservé, statistiques de
     * vocabulaire comprises : mots de passe (visibles ou masqués, texte ou
     * numériques), et champs que l'application déclare non mémorisables via
     * IME_FLAG_NO_PERSONALIZED_LEARNING.
     */
    private fun isSensitiveField(): Boolean {
        val editorInfo = currentInputEditorInfo ?: return true
        return isSensitiveInput(editorInfo.inputType, editorInfo.imeOptions)
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        Log.d(TAG, "onStartInput - restarting: $restarting")
        
        inputProcessor.resetState()
        suggestionEngine.clearHistory()
        displaySuggestions(emptyList())
    }
    
    /**
     * Le framework signale ici tout déplacement du curseur : tap dans le texte,
     * sélection, effacement remontant dans un mot déjà validé, ou modification
     * faite par l'application elle-même. C'est le seul point où l'on apprend que
     * le texte a bougé sans passer par nos touches.
     *
     * Absent de ce service jusqu'ici (il n'existait que dans le service legacy
     * KreyolInputMethodService), ce qui laissait le mot suivi par InputProcessor
     * dériver du texte réel : revenir éditer un mot existant ne produisait alors
     * plus aucune suggestion, et taper après un déplacement de curseur en
     * produisait pour le mot précédent.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )

        if (!::inputProcessor.isInitialized) return

        dernierSelStart = newSelStart
        dernierSelEnd = newSelEnd

        // Pendant un glissement sur la barre d'espace, chaque caractère franchi
        // passe par ici. On laisse la position filer et on ne resynchronise
        // qu'une fois le doigt arrêté : voir planifierSyncCurseur().
        if (glissementCurseur) {
            planifierSyncCurseur()
            return
        }

        inputProcessor.syncWordWithCursor(newSelStart, newSelEnd)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView - restarting: $restarting")

        // Réglages du retour de frappe relus à chaque prise de focus : le service
        // survit au passage dans l'écran de l'application, donc un interrupteur
        // changé là-bas doit s'appliquer dès le retour dans un champ de saisie.
        KeyFeedback.refresh(this)

        // Même moment, même raison, pour les emojis récents : un mot de passe
        // ne doit pas laisser d'emoji derrière lui, exactement comme il ne
        // laisse pas de mot dans les statistiques de vocabulaire.
        EmojiRecents.setEnregistrementAutorise(!isSensitiveField())

        // Le thème se relit au même moment et pour la même raison, mais lui ne
        // suffit pas à se relire : les couleurs sont posées sur les vues à leur
        // construction, et InputMethodService garde la vue d'entrée en cache d'une
        // saisie à l'autre. Un changement de palette impose de la reconstruire,
        // qu'il vienne de l'écran de réglages ou du mode sombre du téléphone.
        //
        // La comparaison porte sur la palette avec laquelle la vue a été construite,
        // et non sur ce que renverrait un « refresh a-t-il changé quelque chose ? ».
        // L'écran de réglages partage ce processus : au moment du clic il a déjà
        // rafraîchi la palette globale, si bien qu'un tel booléen serait toujours
        // faux ici et que le clavier garderait ses anciennes couleurs.
        KeyboardTheme.refresh(this)
        if (paletteDeLaVue !== KeyboardTheme.palette()) {
            Log.d(TAG, "Thème changé : reconstruction de la vue d'entrée")
            setInputView(onCreateInputView())
        }

        // 🅰️ S'ASSURER QUE LE MODE ALPHABÉTIQUE EST ACTIF À CHAQUE FOIS
        if (!restarting) {
            keyboardLayoutManager.forceAlphabeticMode()
            keyboardLayoutManager.updateKeyboardDisplay()
            Log.d(TAG, "✅ Mode alphabétique garanti lors du démarrage de la saisie")
        }

        maybeShowFirstRealUseTip(info)
    }

    /**
     * Publie la pastille d'icône si l'utilisateur vient de franchir un palier.
     *
     * Rien n'est affiché par-dessus l'application en cours : la notification
     * est silencieuse et sans bandeau (voir [LevelUpNotifier]), l'utilisateur
     * découvre la pastille sur son écran d'accueil quand il y revient.
     *
     * Au tout premier appel, le niveau courant est mémorisé sans rien publier :
     * un utilisateur qui installe cette version avec déjà des centaines de mots
     * à son actif ne doit pas être notifié rétroactivement. Même règle que la
     * célébration existante dans SettingsActivity.
     */
    private fun maybeNotifyLevelUp() {
        try {
            val totalWords = dictionaryWithUsage.getTotalWords()
            if (totalWords <= 0) return

            val discovered = dictionaryWithUsage.getDiscoveredWordsCount()
            val currentIndex = CreoleLevels.indexFor(discovered, totalWords)

            val prefs = getSharedPreferences(GAMIFICATION_PREFS, Context.MODE_PRIVATE)
            val lastNotified = prefs.getInt(PREF_LAST_NOTIFIED_LEVEL, -1)

            if (lastNotified == -1) {
                prefs.edit().putInt(PREF_LAST_NOTIFIED_LEVEL, currentIndex).apply()
                return
            }
            if (currentIndex <= lastNotified) return

            // La pastille de la barre d'onglets est posée quoi qu'il arrive :
            // elle ne dépend pas de la permission de notification, et reste donc
            // le signal de repli quand celle-ci a été refusée.
            prefs.edit()
                .putInt(PREF_LAST_NOTIFIED_LEVEL, currentIndex)
                .putBoolean(SettingsActivity.PREF_LEVEL_BADGE_PENDING, true)
                .apply()
            LevelUpNotifier.notifyLevelUp(this, CreoleLevels.LEVELS[currentIndex].label)
        } catch (e: Exception) {
            // Un échec de notification ne doit jamais perturber la frappe
            Log.e(TAG, "Erreur lors de la notification de niveau", e)
        }
    }

    /**
     * Confirmation de succès + astuce accents, affichée une seule fois, la
     * première fois que le clavier est réellement utilisé en dehors de
     * l'écran de test intégré à l'app (Klavyé Kréyòl elle-même) — un
     * utilisateur qui bascule directement vers Messages après avoir
     * sélectionné le clavier ne voyait jusqu'ici aucun signal de succès.
     */
    private fun maybeShowFirstRealUseTip(info: EditorInfo?) {
        val targetPackage = info?.packageName ?: return
        if (targetPackage == packageName) return // écran de test intégré à l'app, pas un vrai usage

        val prefs = getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(PREF_FIRST_REAL_USE_TIP_SHOWN, false)) {
            Toast.makeText(
                this,
                "Apiyé lontan asi on lèt pou wè aksan la (é, è, ò...)",
                Toast.LENGTH_LONG
            ).show()
            prefs.edit().putBoolean(PREF_FIRST_REAL_USE_TIP_SHOWN, true).apply()
        }

        // Puce de partage : état séparé de l'astuce accents ci-dessus, retentée
        // à chaque champ tant qu'elle n'a pas pu s'afficher sur un vrai champ
        // de composition (action clavier "Envoyer") — sinon un premier focus
        // sur un champ destinataire/recherche de contact (action "Suivant"/
        // "Rechercher", qui réinterprète le texte collé comme une requête de
        // contact et le tronque — repro sur l'écran "Nouvelle conversation" de
        // Messages, testé le 02/08/2026) consommerait l'occasion pour toujours.
        if (!prefs.getBoolean(PREF_SHARE_CHIP_SHOWN, false) &&
            info.imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEND
        ) {
            showShareInviteChip()
            prefs.edit().putBoolean(PREF_SHARE_CHIP_SHOWN, true).apply()
        }
    }

    /**
     * Puce unique invitant à partager le clavier, affichée dans la barre de
     * suggestions au moment du tout premier usage réel (avant même que
     * l'utilisateur ait tapé une lettre de son propre message). Un tap
     * insère directement le message prêt-à-envoyer dans le champ en cours
     * (SMS, WhatsApp...) via commitText : contrairement à une suggestion
     * normale, ce n'est pas un mot à finaliser, donc on n'appelle pas
     * inputProcessor.processSuggestionSelection() ici. Dès que l'utilisateur
     * tape son propre texte, displaySuggestions()/displayBilingualSuggestions()
     * vide le conteneur et la puce disparaît naturellement.
     */
    private fun showShareInviteChip() {
        val container = kreyolRow ?: return
        lateinit var chip: Button
        chip = Button(this).apply {
            text = "📤 Envoyer un mot à un ami"
            textSize = 14f
            setTextColor(KeyboardColors.CHIP_TEXT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#FF8A00"))
            }
            // Même réserve verticale et même garde-fou que les puces de suggestion :
            // cette puce partage leur rangée, elle doit tenir dans la même hauteur.
            includeFontPadding = false
            setPadding(
                dpToPx(14),
                dpToPx(SUGGESTION_CHIP_PADDING_V_DP),
                dpToPx(14),
                dpToPx(SUGGESTION_CHIP_PADDING_V_DP)
            )
            fitTextToChipHeight(this)
            // Le son de frappe est joué par KeyFeedback : sans cette ligne,
            // performClick() ajouterait son clic d'interface et la puce sonnerait deux fois.
            isSoundEffectsEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                // Même intervalle que les puces de suggestion : une seule règle pour
                // tout ce qui se touche dans cette rangée.
                setMargins(suggestionChipHalfGapPx(), 0, suggestionChipHalfGapPx(), 0)
            }
            setOnClickListener {
                // Même règle que les puces de suggestion : ce qui écrit du texte se
                // sent, et se sent au moment où le texte part.
                KeyFeedback.onKeyPress(chip)
                currentInputConnection?.commitText(SHARE_INVITE_MESSAGE, 1)
                container.removeView(chip)
            }
        }
        container.addView(chip, 0)
    }
    
    override fun onFinishInput() {
        // Sauter super.onFinishInput() cassait le cycle de vie interne du service :
        // après un changement d'app (et retour), le framework ne rappelait plus
        // jamais onStartInput()/onStartInputView(), laissant le clavier invisible
        // indéfiniment. onEvaluateInputViewShown() (plus bas) retourne déjà
        // toujours true, ce qui est la bonne façon de garder le clavier affiché.
        super.onFinishInput()
        Log.d(TAG, "onFinishInput")

        accentHandler.dismissAccentPopup()
        inputProcessor.resetState()
    }
    
    /**
     * MÉTHODE TEMPORAIREMENT DÉSACTIVÉE - Gère le changement vers le prochain clavier IME (bouton Globe 🌐)
     * TODO: Réactiver quand le problème système Android sera résolu
     */
    /*
    override fun switchInputMethod(imeSubtypeToken: String?) {
        Log.d(TAG, "🌐 switchInputMethod appelé avec token: $imeSubtypeToken")
        try {
            // Méthode 1: Utiliser la méthode standard du système
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val token = window.window?.attributes?.token
            
            if (token != null) {
                // Utiliser switchToNextInputMethod avec le bon token de fenêtre
                val switched = inputMethodManager.switchToNextInputMethod(token, false)
                Log.d(TAG, "✅ switchToNextInputMethod réussi: $switched")
                if (!switched) {
                    // Fallback: afficher le sélecteur
                    inputMethodManager.showInputMethodPicker()
                    Log.d(TAG, "✅ Fallback: InputMethod Picker affiché")
                }
            } else {
                // Si pas de token, utiliser le sélecteur directement
                inputMethodManager.showInputMethodPicker()
                Log.d(TAG, "✅ Token null: InputMethod Picker affiché")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur lors du changement de clavier: ${e.message}", e)
            // Dernier recours: super.switchInputMethod()
            try {
                super.switchInputMethod(imeSubtypeToken)
                Log.d(TAG, "✅ Super.switchInputMethod réussi")
            } catch (superException: Exception) {
                Log.e(TAG, "❌ Super.switchInputMethod également échoué: ${superException.message}", superException)
            }
        }
    }
    */
    
    /**
     * MÉTHODE TEMPORAIREMENT DÉSACTIVÉE - Interception directe des touches système, notamment le bouton Globe
     * TODO: Réactiver quand le problème système Android sera résolu
     */
    /*
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "🔧 onKeyDown appelé avec keyCode: $keyCode (Globe = ${KeyEvent.KEYCODE_LANGUAGE_SWITCH})")
        
        // Intercepter spécifiquement le bouton Globe
        if (keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH) {
            Log.d(TAG, "🌐 INTERCEPTION DIRECTE du bouton Globe!")
            try {
                // Utiliser directement InputMethodManager sans passer par switchInputMethod()
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showInputMethodPicker()
                Log.d(TAG, "✅ Globe intercepté: InputMethod Picker affiché avec succès")
                return true // Consommer l'événement pour éviter le traitement par défaut
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur lors de l'interception Globe: ${e.message}", e)
                return false // Laisser le système traiter en cas d'erreur
            }
        }
        
        // Pour toutes les autres touches, utiliser le comportement par défaut
        return super.onKeyDown(keyCode, event)
    }
    */
    
    override fun onDestroy() {
        Log.d(TAG, "=== DESTRUCTION DU SERVICE ===")
        
        try {
            // 🎮 Gamification: Sauvegarder les changements non sauvegardés
            if (::dictionaryWithUsage.isInitialized) {
                dictionaryWithUsage.onDestroy()
                Log.d(TAG, "✅ Gamification: Sauvegarde finale effectuée")
            }
            
            // 🔧 FIX SAMSUNG A21S: Arrêter monitoring et annuler coroutines
            memoryMonitoringJob?.cancel()
            serviceScope.cancel()
            Log.d(TAG, "✅ Monitoring mémoire et coroutines annulés pour A21s")
            
            // Arrêter la suppression par mots si active
            stopWordDeletion()

            // Une resynchronisation de curseur encore en attente s'exécuterait
            // sur un service détruit
            syncCurseurEnAttente?.let { handlerCurseur.removeCallbacks(it) }
            syncCurseurEnAttente = null
            
            // Nettoyage des composants dans l'ordre inverse de création
            accentHandler.cleanup()
            // inputProcessor.setInputProcessorListener(null) // À commenter pour éviter l'erreur
            suggestionEngine.cleanup()
            keyboardLayoutManager.cleanup()
            
            // Nettoyage des vues
            suggestionsView?.removeAllViews()
            suggestionsView = null
            frenchRow?.removeAllViews()
            frenchRow = null
            kreyolRow = null
            frenchRowScroll = null
            mainKeyboardView = null
            
            Log.d(TAG, "Nettoyage terminé avec succès - Compatible A21s")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du nettoyage: ${e.message}", e)
        } finally {
            super.onDestroy()
        }
    }
    
    // ===== SUPPRESSION PAR MOTS (APPUI LONG DELETE) =====
    
    /**
     * Démarre la suppression continue par mots avec appui long
     */
    private fun startWordDeletion() {
        if (isDeleteLongPressActive) return
        
        isDeleteLongPressActive = true
        Log.d(TAG, "🔥 Début suppression par mots avec appui long")
        
        // Première suppression immédiate d'un mot
        deleteWordBeforeCursor()
        
        // Puis suppression continue toutes les 300ms
        deleteTimer = Timer()
        deleteTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                deleteHandler.post {
                    if (isDeleteLongPressActive) {
                        deleteWordBeforeCursor()
                    }
                }
            }
        }, 500, 300) // Délai initial 500ms, puis toutes les 300ms
    }
    
    /**
     * Arrête la suppression continue par mots
     */
    private fun stopWordDeletion() {
        if (!isDeleteLongPressActive) return
        
        isDeleteLongPressActive = false
        deleteTimer?.cancel()
        deleteTimer = null
        Log.d(TAG, "🛑 Arrêt suppression par mots")
    }
    
    /**
     * Supprime le mot précédent en utilisant les espaces comme délimiteurs
     */
    private fun deleteWordBeforeCursor() {
        val inputConnection = currentInputConnection ?: return
        
        try {
            // Récupérer le texte avant le curseur (jusqu'à 100 caractères)
            val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""
            
            if (textBeforeCursor.isEmpty()) {
                Log.d(TAG, "Aucun texte avant le curseur")
                return
            }
            
            // Trouver le dernier mot (délimité par des espaces)
            var deleteCount = 0
            var i = textBeforeCursor.length - 1
            
            // Ignorer les espaces en fin
            while (i >= 0 && textBeforeCursor[i].isWhitespace()) {
                deleteCount++
                i--
            }
            
            // Compter les caractères du mot
            while (i >= 0 && !textBeforeCursor[i].isWhitespace()) {
                deleteCount++
                i--
            }
            
            if (deleteCount > 0) {
                inputConnection.deleteSurroundingText(deleteCount, 0)
                Log.d(TAG, "🗑️ Supprimé $deleteCount caractères (mot complet)")
                
                // Optionnel: Le processeur d'entrée se mettra à jour automatiquement
                // via les prochaines interactions utilisateur
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la suppression par mots: ${e.message}")
            // Fallback: suppression caractère par caractère
            inputConnection.deleteSurroundingText(1, 0)
        }
    }
    
    // ===== CHANGEMENT DE CLAVIER (APPUI LONG BARRE D'ESPACE) =====
    
    /**
     * 🌐 Affiche le sélecteur système de clavier (liste des IME installés)
     * Utilisé lors de l'appui long sur la barre d'espace.
     *
     * On affiche toujours le sélecteur plutôt que de basculer directement vers le
     * "prochain" clavier : un changement silencieux d'IME (ex. bascule vers Gboard sans
     * confirmation) après un simple appui d'1 seconde surprend l'utilisateur, qui peut
     * se retrouver sur un autre clavier sans comprendre pourquoi. Le sélecteur laisse
     * le choix explicitement, comme le globe des claviers Android standards.
     */
    private fun showKeyboardPicker() {
        try {
            Log.d(TAG, "🌐 Affichage du sélecteur de claviers")
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showInputMethodPicker()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur affichage sélecteur de claviers: ${e.message}", e)
        }
    }

    // ===== MÉTHODES D'ÉVALUATION =====
    
    override fun onEvaluateFullscreenMode(): Boolean {
        return false // Toujours en mode compact pour une meilleure UX
    }
    
    override fun onEvaluateInputViewShown(): Boolean {
        return true
    }
    
    override fun isExtractViewShown(): Boolean {
        return false
    }
    
    // ===== MÉTHODES UTILITAIRES =====
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    /**
     * 📏 Hauteur restant aux quatre rangées de touches une fois retiré tout ce que
     * le clavier consomme par ailleurs.
     *
     * `screenHeightDp` donne la hauteur d'écran barre de navigation déduite, mais
     * pas la barre d'état, sous laquelle la fenêtre IME commence : il faut la
     * retirer aussi, sans quoi le compte est trop généreux d'exactement sa hauteur
     * et la rangée du bas reste rognée (mesuré sur l'émulateur, 72 px de trop).
     * En portrait la place dépasse largement le besoin et la hauteur nominale des
     * touches est conservée ; en paysage (288 dp sur un 1080x2400 en 480 dpi) elle
     * est inférieure aux 332 dp de la mise en page nominale, et sans ce calcul la
     * rangée du bas est coupée en deux au lieu d'être simplement plus basse.
     *
     * Rien n'est réservé ici pour la barre de navigation : le système la déduit
     * déjà de la fenêtre de saisie, et le cas contraire est rattrapé après coup
     * par adjustForNavigationBarOverlap(), qui dispose alors d'autant de hauteur
     * supplémentaire puisque la fenêtre descend jusqu'au bas de l'écran.
     */
    private fun computeAvailableRowsHeight(): Int {
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarPx = if (statusBarId > 0) {
            resources.getDimensionPixelSize(statusBarId)
        } else {
            dpToPx(24)
        }
        val windowHeightPx = dpToPx(resources.configuration.screenHeightDp) - statusBarPx
        val suggestionsPx = dpToPx(suggestionRowHeightDp()) * suggestionRowCount()
        val keyboardPaddingPx = dpToPx(KeyboardLayoutManager.verticalPaddingDp(this)) * 2
        // Marge d'arrondi : le calcul tombe sinon au pixel près sur le bas de la
        // fenêtre, où la moindre différence de mesure ronge à nouveau la dernière rangée
        val safetyPx = dpToPx(4)
        val available = windowHeightPx - suggestionsPx - keyboardPaddingPx - safetyPx
        Log.d(TAG, "📏 Hauteur fenêtre ${windowHeightPx}px (barre d'état ${statusBarPx}px déduite), " +
            "dont suggestions ${suggestionsPx}px, padding clavier ${keyboardPaddingPx}px " +
            "→ ${available}px pour les rangées")
        return available
    }

    /**
     * 📏 Décale le bas du clavier de ce que la barre de navigation lui masque
     * réellement, mesuré une fois la mise en page faite.
     *
     * L'ancienne version estimait ce décalage à partir du mode de navigation et
     * du dimen système `navigation_bar_height`, sans vérifier qu'il servait à
     * quelque chose. Or le système place déjà la fenêtre de saisie au-dessus de
     * la barre : la réserve était payée deux fois et laissait une bande vide
     * sous la dernière rangée (constaté en paysage sur émulateur Android 13).
     * Elle exposait aussi le clavier aux ROM qui renvoient une valeur aberrante
     * pour ce dimen (signalé sur un "P300", clavier poussé hors de l'écran).
     *
     * Comparer la position réelle du clavier à celle de la barre traite les deux
     * cas sans rien supposer : zéro quand le système a déjà fait le travail, le
     * recouvrement exact quand il ne l'a pas fait.
     */
    private fun adjustForNavigationBarOverlap(root: View, container: View) {
        val insets = window?.window?.decorView?.rootWindowInsets ?: return
        val navBarPx = if (android.os.Build.VERSION.SDK_INT >= 30) {
            insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }

        val displayHeightPx = if (android.os.Build.VERSION.SDK_INT >= 30) {
            (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .maximumWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }

        val position = IntArray(2)
        root.getLocationOnScreen(position)
        val keyboardBottomPx = position[1] + root.height
        val overlapPx = keyboardBottomPx - (displayHeightPx - navBarPx)

        val paddingPx = if (overlapPx > 0) overlapPx else 0
        Log.d(TAG, "📏 Bas du clavier ${keyboardBottomPx}px, barre de navigation à " +
            "${displayHeightPx - navBarPx}px → recouvrement ${overlapPx}px, padding ${paddingPx}px")
        if (container.paddingBottom != paddingPx) {
            container.setPadding(0, 0, 0, paddingPx)
        }
    }

}
