package com.example.kreyolkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Gestionnaire responsable de la création et du stylisme des layouts de clavier
 * Sépare la logique de création des touches du service principal
 */
class KeyboardLayoutManager(private val context: Context) {
    
    companion object {
        private const val BUTTON_HEIGHT_DP = 48
        private const val BUTTON_MARGIN_DP = 2
        private const val CORNER_RADIUS_DP = 8f
        private const val TEXT_SIZE_SP = 16f
        private const val HINT_TEXT_SIZE_SP = 8f
        private const val SHADOW_RADIUS = 4f
        private const val TAG = "KeyboardLayoutManager"
        
        // 🌐 Délai pour l'appui long sur la barre d'espace (1 seconde)
        private const val SPACE_LONG_PRESS_DELAY = 1000L
    }
    
    // État du clavier
    private var isCapitalMode = false
    private var isCapsLock = false
    private var isNumericMode = false // FORCE ALPHABÉTIQUE PAR DÉFAUT
    private val keyboardButtons = mutableListOf<View>() // Changé de TextView à View pour supporter ImageButton

    // Référence optionnelle pour prévisualiser les options d'appui long dans
    // les coins des touches (v8.3.0). Laissé à null par le clavier de démo
    // (SettingsActivity), qui n'a pas d'AccentHandler et n'affiche donc aucun indice.
    var accentHandler: AccentHandler? = null
    
    // 🌐 Handler pour l'appui long personnalisé de la barre d'espace
    private val spaceLongPressHandler = Handler(Looper.getMainLooper())
    private var spaceLongPressRunnable: Runnable? = null
    private var isSpaceLongPressTriggered = false
    
    init {
        // Garantir que le clavier démarre toujours en mode alphabétique
        ensureAlphabeticMode()
    }
    
    // Callbacks pour l'interaction avec les touches
    interface KeyboardInteractionListener {
        fun onKeyPress(key: String)
        fun onLongPress(key: String, button: View) // Changé de TextView à View
        fun onKeyRelease()
    }
    
    private var interactionListener: KeyboardInteractionListener? = null
    
    fun setInteractionListener(listener: KeyboardInteractionListener) {
        this.interactionListener = listener
    }
    
    /**
     * Crée le layout principal du clavier avec toutes les rangées
     */
    fun createKeyboardLayout(): LinearLayout {
        Log.d("KeyboardLayoutManager", "🎯 createKeyboardLayout - isNumericMode: $isNumericMode")
        
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpToPx(8), dpToPx(8), 
                dpToPx(8), dpToPx(8)
            )
        }
        
        // Créer les différentes rangées selon le mode
        when {
            isNumericMode -> {
                Log.d("KeyboardLayoutManager", "🔢 Création du layout NUMÉRIQUE")
                createNumericLayout(mainLayout)
            }
            else -> {
                Log.d("KeyboardLayoutManager", "🔤 Création du layout ALPHABÉTIQUE")
                createAlphabeticLayout(mainLayout)
            }
        }
        
        return mainLayout
    }
    
    /**
     * Crée le layout alphabétique (AZERTY créole)
     */
    private fun createAlphabeticLayout(mainLayout: LinearLayout) {
        val row1 = arrayOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "ò", "p")
        val row2 = arrayOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m")
        val row3 = arrayOf("⇧", "w", "x", "c", "v", "b", "n", "⌫")
        // v8.6.0 : "-" ajouté en touche dédiée (21,7% des mots créoles en
        // contiennent un, fréquence cumulée supérieure à celle de "ò")
        val row4 = arrayOf("123", ",", "é", "-", " ", "è", ".", "'", "⏎")
        
        mainLayout.addView(createKeyboardRow(row1))
        mainLayout.addView(createKeyboardRow(row2))
        mainLayout.addView(createKeyboardRow(row3))
        mainLayout.addView(createKeyboardRow(row4))
    }
    
    /**
     * Crée le layout numérique
     */
    private fun createNumericLayout(mainLayout: LinearLayout) {
        val row1 = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val row2 = arrayOf("-", "/", ":", ";", "(", ")", "€", "&", "@", "\"")
        val row3 = arrayOf("=", ".", ",", "?", "!", "'", "\"", "_", "⌫")
        val row4 = arrayOf("ABC", " ", "⏎")
        
        mainLayout.addView(createKeyboardRow(row1))
        mainLayout.addView(createKeyboardRow(row2))
        mainLayout.addView(createKeyboardRow(row3))
        mainLayout.addView(createKeyboardRow(row4))
    }
    
    /**
     * Crée une rangée de touches
     */
    private fun createKeyboardRow(keys: Array<String>): LinearLayout {
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(2), 0, dpToPx(2))
            }
        }
        
        val totalWeight = calculateRowWeight(keys)
        
        for (key in keys) {
            // createKeyButton() alimente déjà keyboardButtons avec la touche
            // interactive brute (avant l'éventuel enrobage des indices de coin) ;
            // un second ajout ici dupliquait chaque touche dans la liste.
            val button = createKeyButton(key, totalWeight)
            rowLayout.addView(button)
        }
        
        return rowLayout
    }
    
    /**
     * Crée un bouton de touche individuel (Button ou ImageButton selon le type)
     */
    private fun createKeyButton(key: String, totalWeight: Float): View {
        // Déterminer si on utilise une icône Material Design
        val useIcon = key in listOf("⌫", "⏎", "⇧")
        
        val button: View = if (useIcon) {
            // Créer un ImageButton pour les touches avec icônes
            android.widget.ImageButton(context).apply {
                // Définir l'icône selon la touche
                setImageResource(when (key) {
                    "⌫" -> R.drawable.ic_backspace
                    "⏎" -> R.drawable.ic_keyboard_return
                    "⇧" -> if (isCapsLock) R.drawable.ic_shift_caps
                           else if (isCapitalMode) R.drawable.ic_shift_on
                           else R.drawable.ic_shift_off
                    else -> R.drawable.ic_backspace // Fallback
                })
                
                // Teinter l'icône en blanc pour visibilité sur fond coloré
                setColorFilter(Color.WHITE)
                
                // Configurer la taille et le padding de l'icône (différent selon la touche)
                val iconPadding = when (key) {
                    "⏎" -> dpToPx(8)  // Moins de padding pour l'icône Enter (plus grande)
                    "⌫" -> dpToPx(10) // Padding moyen pour Backspace
                    "⇧" -> dpToPx(12) // Padding normal pour Shift
                    else -> dpToPx(12)
                }
                setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                
                // Description pour accessibilité
                contentDescription = when (key) {
                    "⌫" -> "Supprimer"
                    "⏎" -> "Entrée"
                    "⇧" -> "Majuscule"
                    else -> key
                }
                
                // Stocker la clé dans le tag pour identification
                tag = key
                
                // Calcul du poids selon le type de touche
                val weight = getKeyWeight(key)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dpToPx(BUTTON_HEIGHT_DP),
                    weight
                ).apply {
                    setMargins(
                        dpToPx(BUTTON_MARGIN_DP), 0, 
                        dpToPx(BUTTON_MARGIN_DP), 0
                    )
                }
            }
        } else {
            // Créer un Button classique pour les autres touches
            Button(context).apply {
                text = getDisplayText(key)
                // Le thème AppCompat d'une activité impose textAllCaps=true
                // aux Button : les touches doivent refléter exactement l'état
                // shift, quel que soit le contexte (IME ou clavier d'essai)
                isAllCaps = false
                // Button a une élévation/StateListAnimator implicite qui le
                // fait dessiner par-dessus ses voisins ajoutés après lui dans
                // un FrameLayout, quel que soit l'ordre d'ajout (constaté en
                // testant les indices d'appui long v8.3.0 : un enfant ajouté
                // après restait invisible tant que ceci n'était pas neutralisé).
                elevation = 0f
                stateListAnimator = null
                // Taille de police personnalisée pour Potomitan™ branding discret
                textSize = if (key == " ") TEXT_SIZE_SP * 0.75f else TEXT_SIZE_SP
                setTypeface(typeface, Typeface.BOLD)
                
                // Calcul du poids selon le type de touche
                val weight = getKeyWeight(key)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dpToPx(BUTTON_HEIGHT_DP),
                    weight
                ).apply {
                    setMargins(
                        dpToPx(BUTTON_MARGIN_DP), 0, 
                        dpToPx(BUTTON_MARGIN_DP), 0
                    )
                }
            }
        }
        
        // Application du style Guadeloupe
        applyGuadeloupeStyleToView(button, key)
        
        // Ajouter le bouton à la liste de suivi
        keyboardButtons.add(button)
        
        // Configuration des événements tactiles
        setupButtonInteractions(button, key)

        // Aperçu des options d'appui long dans les coins de la touche (v8.3.0)
        val hints = accentHandler?.takeIf { it.hasAccents(key) }?.getCornerHintsForKey(key)
        return if (!hints.isNullOrEmpty()) {
            val onStartSide = accentHandler?.isCornerHintOnStartSide(key) == true
            wrapWithLongPressHints(button, hints, onStartSide)
        } else {
            button
        }
    }

    /**
     * Enveloppe une touche dans un FrameLayout pour superposer, en haut et en
     * bas d'un même côté (droit par défaut, gauche si onStartSide), un aperçu
     * des deux premières options d'appui long. La touche d'origine garde
     * exactement sa zone tactile, son style et son ancrage pour la popup
     * d'accents (le FrameLayout se contente de prendre sa place dans la
     * rangée) ; keyboardButtons ne référence jamais ce FrameLayout, seulement la
     * touche brute qu'il contient.
     */
    private fun wrapWithLongPressHints(inner: View, hints: List<String>, onStartSide: Boolean): FrameLayout {
        val outerParams = inner.layoutParams
        inner.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        val horizontalGravity = if (onStartSide) Gravity.START else Gravity.END

        return FrameLayout(context).apply {
            layoutParams = outerParams
            addView(inner)
            addView(createHintLabel(hints[0], Gravity.TOP or horizontalGravity, onStartSide))
            if (hints.size > 1) {
                addView(createHintLabel(hints[1], Gravity.BOTTOM or horizontalGravity, onStartSide))
            }
        }
    }

    private fun createHintLabel(hintText: String, gravity: Int, onStartSide: Boolean): TextView {
        return TextView(context).apply {
            text = hintText
            textSize = HINT_TEXT_SIZE_SP
            setTextColor(Color.parseColor("#99333333"))
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                gravity
            ).apply {
                if (onStartSide) {
                    setMargins(dpToPx(3), dpToPx(2), 0, dpToPx(2))
                } else {
                    setMargins(0, dpToPx(2), dpToPx(3), dpToPx(2))
                }
            }
        }
    }
    
    /**
     * Applique le style visuel spécifique à la Guadeloupe (supporte Button et ImageButton)
     */
    private fun applyGuadeloupeStyleToView(view: View, key: String) {
        val drawable = GradientDrawable().apply {
            cornerRadius = dpToPx(CORNER_RADIUS_DP.toInt()).toFloat()
            
            when (key) {
                "⇧" -> {
                    // Touche Shift avec nuance de blanc/gris
                    val colors = when {
                        isCapsLock -> intArrayOf(Color.parseColor("#E8E8E8"), Color.parseColor("#D0D0D0")) // Gris moyen activé
                        isCapitalMode -> intArrayOf(Color.parseColor("#F0F0F0"), Color.parseColor("#E0E0E0")) // Gris clair actif
                        else -> intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#F8F8F8")) // Blanc neutre
                    }
                    setColors(colors)
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "⌫" -> {
                    // Touche Supprimer avec couleur semi-transparente
                    setColors(intArrayOf(
                        Color.parseColor("#CCFFFFFF"), // Blanc semi-transparent
                        Color.parseColor("#C0F0F0F0")  // Gris très clair semi-transparent
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "⏎" -> {
                    // Touche Entrée avec vert tropical
                    setColors(intArrayOf(
                        Color.parseColor("#00C853"), // Vert tropical vif
                        Color.parseColor("#00A843")  // Vert tropical foncé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                ",", ".", "'", "-" -> {
                    // Touches virgule, point, apostrophe et trait d'union avec orange caraïbe
                    setColors(intArrayOf(
                        Color.parseColor("#FF8C00"), // Orange caraïbe vif
                        Color.parseColor("#FF7000")  // Orange caraïbe foncé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "123", "ABC" -> {
                    // Touches de mode avec vert tropical
                    setColors(intArrayOf(
                        Color.parseColor("#00C853"), // Vert tropical vif
                        Color.parseColor("#00A843")  // Vert tropical foncé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                "à", "è", "ò", "é", "ù", "ì", "ç" -> {
                    // Touches créoles avec nuance de blanc/gris
                    setColors(intArrayOf(
                        Color.parseColor("#FFFFFF"), // Blanc
                        Color.parseColor("#F8F8F8")  // Blanc cassé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                " " -> {
                    // Barre d'espace avec bleu caraïbe
                    setColors(intArrayOf(
                        Color.parseColor("#1E90FF"), // Bleu caraïbe
                        Color.parseColor("#0000FF")  // Bleu pour dégradé
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
                else -> {
                    // Touches normales avec gradient blanc/gris
                    setColors(intArrayOf(
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#F5F5F5")
                    ))
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                }
            }
            
            // Bordure subtile
            setStroke(dpToPx(1), Color.parseColor("#D0D0D0"))
        }
        
        view.background = drawable
        
        // Couleur du texte (seulement pour Button, pas ImageButton)
        if (view is Button) {
            view.setTextColor(when (key) {
                "⇧" -> if (isCapsLock || isCapitalMode) Color.parseColor("#666666") else Color.parseColor("#333333")
                ",", ".", "'", "-" -> Color.WHITE // Texte blanc sur fond orange caraïbe
                "⏎", "123", "ABC" -> Color.WHITE // Texte blanc sur fond vert tropical
                "à", "è", "ò", "é", "ù", "ì", "ç" -> Color.parseColor("#333333") // Texte gris foncé sur fond blanc
                " " -> Color.parseColor("#CCFFFFFF") // Blanc semi-transparent pour Potomitan™ - discret mais lisible
                else -> Color.parseColor("#333333")
            })
            
            // Ombre portée pour l'effet de profondeur
            view.setShadowLayer(SHADOW_RADIUS, 0f, dpToPx(1).toFloat(), Color.parseColor("#40000000"))
        }
        
        // Teinte de l'icône pour ImageButton
        if (view is android.widget.ImageButton) {
            // Couleur des icônes selon le type de touche
            when (key) {
                "⇧" -> view.setColorFilter(if (isCapsLock || isCapitalMode) Color.parseColor("#666666") else Color.parseColor("#333333"))
                "⌫" -> view.setColorFilter(Color.parseColor("#333333")) // Icône gris foncé sur fond blanc
                "⏎" -> view.setColorFilter(Color.WHITE) // Icône blanche sur fond vert tropical
                else -> view.setColorFilter(Color.WHITE)
            }
        }
    }
    
    /**
     * Applique le style visuel spécifique à la Guadeloupe (compatibilité avec ancien code)
     */
    private fun applyGuadeloupeStyle(button: Button, key: String) {
        applyGuadeloupeStyleToView(button, key)
    }
    
    /**
     * Configure les interactions tactiles pour un bouton
     */
    private fun setupButtonInteractions(button: View, key: String) {
        // 🌐 Appui long personnalisé pour la barre d'espace (1 seconde)
        if (key == " ") {
            // Pas de setOnClickListener ici : setupSpaceLongPress() gère déjà le clic
            // court via son OnTouchListener. Les deux coexistant provoquaient un double
            // appel à onKeyPress() (l'OnTouchListener ne consomme jamais l'événement, donc
            // le clic natif se déclenchait aussi) → double espace inséré à chaque frappe.
            button.setOnLongClickListener(null) // Désactiver le listener par défaut
            setupSpaceLongPress(button, key)
        } else {
            button.setOnClickListener {
                interactionListener?.onKeyPress(key)
            }
            button.setOnLongClickListener {
                interactionListener?.onLongPress(key, button)
                true
            }
            // Animation tactile pour les touches autres que la barre d'espace
            addTouchAnimation(button)
        }
    }
    
    /**
     * 🌐 Configure l'appui long personnalisé de 1 seconde pour la barre d'espace
     */
    private fun setupSpaceLongPress(button: View, key: String) {
        button.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isSpaceLongPressTriggered = false
                    
                    // Animation d'appui (100ms)
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start()
                    
                    // Feedback haptique
                    performHapticFeedback(view)
                    
                    // Démarrer le timer de 1 seconde pour l'appui long
                    spaceLongPressRunnable = Runnable {
                        isSpaceLongPressTriggered = true
                        Log.d(TAG, "⏱️ Appui long 1s détecté sur barre d'espace")
                        interactionListener?.onLongPress(key, button)
                    }
                    spaceLongPressHandler.postDelayed(spaceLongPressRunnable!!, SPACE_LONG_PRESS_DELAY)
                    
                    false
                }
                android.view.MotionEvent.ACTION_UP -> {
                    // Annuler le timer si relâché avant 1 seconde
                    spaceLongPressRunnable?.let { spaceLongPressHandler.removeCallbacks(it) }
                    
                    // Animation de relâchement (120ms)
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                    
                    interactionListener?.onKeyRelease()
                    
                    // Si relâché rapidement (pas d'appui long), c'est un clic normal
                    if (!isSpaceLongPressTriggered) {
                        interactionListener?.onKeyPress(key)
                    }
                    
                    false
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Annuler le timer en cas d'annulation
                    spaceLongPressRunnable?.let { spaceLongPressHandler.removeCallbacks(it) }
                    
                    // Animation de relâchement (120ms)
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                    
                    interactionListener?.onKeyRelease()
                    false
                }
                else -> false
            }
        }
    }
    
    /**
     * Ajoute une animation tactile et feedback haptique au bouton
     */
    /**
     * Ajoute une animation tactile et feedback haptique au bouton
     */
    private fun addTouchAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Animation d'appui (100ms comme l'original)
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .start()
                    
                    // 📳 FEEDBACK HAPTIQUE MODERNE
                    performHapticFeedback(v)
                    
                    false
                }
                android.view.MotionEvent.ACTION_UP, 
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Animation de relâchement (120ms comme l'original)
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()
                    
                    interactionListener?.onKeyRelease()
                    false
                }
                else -> false
            }
        }
    }
    
    /**
     * Exécute le feedback haptique classique (comme dans la version originale)
     */
    private fun performHapticFeedback(view: android.view.View) {
        try {
            // Feedback haptique léger (identique à la version originale)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                view.performHapticFeedback(
                    android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
        } catch (e: Exception) {
            // Silencieusement ignorer si feedback haptique non supporté
            Log.d(TAG, "Feedback haptique non disponible: ${e.message}")
        }
    }
    
    /**
     * Met à jour l'affichage du clavier selon l'état actuel
     */
    
    /**
     * Met à jour les états internes du clavier
     */
    fun updateKeyboardStates(isNumeric: Boolean, isCapital: Boolean, isCapsLock: Boolean) {
        Log.e("SHIFT_REAL_DEBUG", "🚨 UPDATING KEYBOARD STATES! isCapital=$isCapital, isCapsLock=$isCapsLock")
        this.isNumericMode = isNumeric
        this.isCapitalMode = isCapital
        this.isCapsLock = isCapsLock
    }

    fun updateKeyboardDisplay() {
        Log.e("SHIFT_REAL_DEBUG", "🚨🚨🚨 updateKeyboardDisplay() CALLED! 🚨🚨🚨")
        
        val shiftButtons = keyboardButtons.filter { getKeyFromButton(it) == "⇧" }
        Log.e("SHIFT_REAL_DEBUG", "🔢 NOMBRE DE BOUTONS SHIFT TROUVÉS: ${shiftButtons.size}")
        Log.e("SHIFT_REAL_DEBUG", "📊 ÉTAT ACTUEL: isCapitalMode=$isCapitalMode, isCapsLock=$isCapsLock")
        
        keyboardButtons.forEach { button ->
            val key = getKeyFromButton(button)
            
            // Mise à jour du style pour la touche Shift
            if (key == "⇧") {
                Log.e("SHIFT_REAL_DEBUG", "🚨 UPDATING SHIFT BUTTON! isCapitalMode=$isCapitalMode, isCapsLock=$isCapsLock")
                
                // Si c'est un ImageButton, mettre à jour l'icône
                if (button is android.widget.ImageButton) {
                    val newIcon = if (isCapsLock) R.drawable.ic_shift_caps
                                  else if (isCapitalMode) R.drawable.ic_shift_on
                                  else R.drawable.ic_shift_off
                    button.setImageResource(newIcon)
                    Log.e("SHIFT_REAL_DEBUG", "🎨 ICON UPDATED TO: ${if (isCapsLock) "CAPS" else if (isCapitalMode) "ON" else "OFF"}")
                } else if (button is Button) {
                    // Si c'est un Button classique, mettre à jour le texte
                    button.text = getDisplayText(key)
                    applyGuadeloupeStyle(button, key)
                }
                
                Log.e("SHIFT_REAL_DEBUG", "🚨 SHIFT STYLE APPLIED!")
            } else if (button is Button) {
                // Pour les autres touches, mettre à jour le texte normalement
                button.text = getDisplayText(key)
            }
        }
    }
    
    /**
     * Commute entre les modes majuscule/minuscule
     */
    fun toggleCapsMode(): Boolean {
        when {
            !isCapitalMode && !isCapsLock -> {
                isCapitalMode = true
                isCapsLock = false
            }
            isCapitalMode && !isCapsLock -> {
                isCapitalMode = true
                isCapsLock = true
            }
            else -> {
                isCapitalMode = false
                isCapsLock = false
            }
        }
        // ❌ SUPPRIMÉ: updateKeyboardDisplay() - déjà appelé par InputProcessor
        Log.e("SHIFT_REAL_DEBUG", "🚨 toggleCapsMode: isCapital=$isCapitalMode, isCapsLock=$isCapsLock")
        return isCapitalMode
    }
    
    /**
     * Commute entre mode alphabétique et numérique
     */
    fun switchKeyboardMode(): Boolean {
        isNumericMode = !isNumericMode
        return isNumericMode
    }
    
    /**
     * Retourne l'état actuel du mode numérique sans le modifier
     */
    fun isNumericMode(): Boolean {
        return isNumericMode
    }
    
    /**
     * Force le mode alphabétique (pour l'initialisation)
     */
    fun switchKeyboardModeToAlphabetic() {
        isNumericMode = false
        Log.d("KeyboardLayoutManager", "🔤 MODE FORCÉ À ALPHABÉTIQUE")
    }
    
    /**
     * Garantit que le clavier démarre en mode alphabétique
     */
    private fun ensureAlphabeticMode() {
        isNumericMode = false
        isCapitalMode = false
        isCapsLock = false
        Log.d("KeyboardLayoutManager", "🚀 INITIALISATION : Mode alphabétique garanti")
    }
    
    /**
     * Force publiquement le retour au mode alphabétique
     */
    fun forceAlphabeticMode() {
        ensureAlphabeticMode()
        Log.d("KeyboardLayoutManager", "🔄 FORCE : Retour au mode alphabétique")
    }
    
    /**
     * Nettoie les ressources
     */
    fun cleanup() {
        keyboardButtons.forEach { button ->
            cleanupView(button)
        }
        keyboardButtons.clear()
        interactionListener = null
    }
    
    // Méthodes utilitaires privées
    
    private fun getDisplayText(key: String): String {
        return when (key) {
            " " -> "Potomitan™"
            "⇧" -> "⇧"
            "⌫" -> "⌫"
            "⏎" -> "⏎"
            "123" -> if (isNumericMode) "ABC" else "123"
            // Caractères accentués créoles - respecter le mode majuscule/minuscule
            "à", "è", "ò", "é", "ù", "ì", "ç" -> if (isCapitalMode) key.uppercase() else key
            else -> if (isCapitalMode) key.uppercase() else key.lowercase()
        }
    }
    
    private fun getKeyWeight(key: String): Float {
        return when (key) {
            " " -> 4.0f      // Barre d'espace plus large
            "⇧", "⌫" -> 1.5f // Touches de fonction plus larges
            else -> 1.0f     // Touches normales
        }
    }
    
    private fun calculateRowWeight(keys: Array<String>): Float {
        return keys.sumOf { getKeyWeight(it).toDouble() }.toFloat()
    }
    
    private fun getKeyFromButton(button: View): String {
        // Version simple : récupérer depuis le texte affiché ou le tag
        return when (button) {
            is Button -> button.text.toString().lowercase()
            is android.widget.ImageButton -> (button.tag as? String)?.lowercase() ?: ""
            else -> ""
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    private fun cleanupView(view: View) {
        view.setOnClickListener(null)
        view.setOnLongClickListener(null)
        view.setOnTouchListener(null)
        view.background = null
        
        // Nettoyer les animations en cours
        view.animate().cancel()
        view.clearAnimation()
        
        // Nettoyer les références du parent
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
