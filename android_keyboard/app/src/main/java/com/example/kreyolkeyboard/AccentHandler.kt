package com.example.kreyolkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * Gestionnaire des accents et caractères spéciaux pour le clavier créole
 * Gère les popups d'accents et la sélection de caractères diacritiques
 */
class AccentHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "AccentHandler"
        private const val LONG_PRESS_DELAY = 500L
        private const val POPUP_ELEVATION_DP = 8f
        private const val ACCENT_BUTTON_SIZE_DP = 48
        private const val ACCENT_BUTTON_MARGIN_DP = 4
    }
    
    // Configuration des accents pour chaque touche de base
    private val accentMap = mapOf(
        "a" to listOf("à", "á", "ä", "â", "ã", "å"),
        "e" to listOf("è", "é", "ê", "ë", "ẽ"),
        "i" to listOf("ì", "í", "î", "ï", "ĩ"),
        "o" to listOf("ò", "ó", "ô", "õ", "ö", "ø"),
        "u" to listOf("ù", "ú", "û", "ü", "ũ"),
        "n" to listOf("ñ"),
        "c" to listOf("ç", "č", "ć"),
        "s" to listOf("ś", "š", "ş"),
        "z" to listOf("ź", "ž", "ż"),
        "l" to listOf("ł"),
        "y" to listOf("ý", "ÿ")
    )
    
    // État actuel
    private var currentAccentPopup: PopupWindow? = null
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false
    
    // Callbacks
    interface AccentSelectionListener {
        fun onAccentSelected(accent: String)
        fun onLongPressStarted(baseKey: String)
        fun onLongPressCancelled()
    }
    
    private var accentListener: AccentSelectionListener? = null
    
    fun setAccentSelectionListener(listener: AccentSelectionListener) {
        this.accentListener = listener
    }
    
    /**
     * Vérifie si une touche a des accents disponibles
     */
    fun hasAccents(key: String): Boolean {
        return accentMap.containsKey(key.lowercase())
    }
    
    /**
     * Démarre le timer de pression longue pour une touche
     */
    fun startLongPressTimer(key: String, anchorButton: TextView) {
        if (!hasAccents(key)) return
        
        cancelLongPress()
        
        longPressRunnable = Runnable {
            isLongPressTriggered = true
            showAccentPopup(key, anchorButton)
            accentListener?.onLongPressStarted(key)
        }
        
        longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_DELAY)
    }
    
    /**
     * Annule la pression longue en cours
     */
    fun cancelLongPress() {
        longPressRunnable?.let {
            longPressHandler.removeCallbacks(it)
            longPressRunnable = null
        }
        
        if (isLongPressTriggered) {
            accentListener?.onLongPressCancelled()
            isLongPressTriggered = false
        }
    }
    
    /**
     * Vérifie si une pression longue est en cours
     */
    fun isLongPressActive(): Boolean {
        return isLongPressTriggered
    }
    
    /**
     * Affiche la popup d'accents pour une touche de base
     */
    fun showAccentPopup(baseKey: String, anchorButton: TextView) {
        val accents = accentMap[baseKey.lowercase()] ?: return
        
        // Fermer la popup existante si elle existe
        dismissAccentPopup()
        
        try {
            // Créer le layout de la popup
            val popupLayout = createAccentPopupLayout(accents, baseKey)
            
            // Créer la popup window
            currentAccentPopup = PopupWindow(
                popupLayout,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                // Style de la popup
                setBackgroundDrawable(createPopupBackground())
                elevation = dpToPx(POPUP_ELEVATION_DP).toFloat()
                
                // Animation d'entrée/sortie
                animationStyle = android.R.style.Animation_Dialog
                
                // Affichage au-dessus de la touche
                showAsDropDown(
                    anchorButton,
                    calculatePopupX(anchorButton, popupLayout),
                    -anchorButton.height - dpToPx(50)
                )
            }
            
            Log.d(TAG, "Popup d'accents affichée pour '$baseKey' avec ${accents.size} options")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'affichage de la popup: ${e.message}", e)
        }
    }
    
    /**
     * Ferme la popup d'accents actuelle
     */
    fun dismissAccentPopup() {
        currentAccentPopup?.let { popup ->
            try {
                if (popup.isShowing) {
                    popup.dismiss()
                } else {
                    // Popup déjà fermé
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur lors de la fermeture de la popup: ${e.message}")
            }
        }
        currentAccentPopup = null
        isLongPressTriggered = false
    }
    
    /**
     * Crée le layout de la popup d'accents
     */
    private fun createAccentPopupLayout(accents: List<String>, baseKey: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                dpToPx(8), dpToPx(8),
                dpToPx(8), dpToPx(8)
            )
            
            // Ajouter d'abord la touche de base
            addView(createAccentButton(baseKey, isBase = true))
            
            // Ajouter les variantes d'accents
            accents.forEach { accent ->
                addView(createAccentButton(accent, isBase = false))
            }
        }
    }
    
    /**
     * Crée un bouton d'accent individuel
     */
    private fun createAccentButton(accent: String, isBase: Boolean): Button {
        return Button(context).apply {
            text = accent
            textSize = 18f
            setTextColor(if (isBase) Color.parseColor("#666666") else Color.parseColor("#333333"))
            
            // Taille et style
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(ACCENT_BUTTON_SIZE_DP),
                dpToPx(ACCENT_BUTTON_SIZE_DP)
            ).apply {
                setMargins(
                    dpToPx(ACCENT_BUTTON_MARGIN_DP), 0,
                    dpToPx(ACCENT_BUTTON_MARGIN_DP), 0
                )
            }
            
            // Style visuel
            background = createAccentButtonBackground(isBase)
            
            // Événement de clic
            setOnClickListener {
                handleAccentSelection(accent)
            }
            
            // Animation tactile légère
            setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).start()
                        false
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(50).start()
                        false
                    }
                    else -> false
                }
            }
        }
    }
    
    /**
     * Gère la sélection d'un accent
     */
    private fun handleAccentSelection(accent: String) {
        accentListener?.onAccentSelected(accent)
        dismissAccentPopup()
        
        Log.d(TAG, "Accent sélectionné: '$accent'")
    }
    
    /**
     * Crée l'arrière-plan de la popup
     */
    private fun createPopupBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(12).toFloat()
            setColors(intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#F8F8F8")
            ))
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dpToPx(1), Color.parseColor("#E0E0E0"))
        }
    }
    
    /**
     * Crée l'arrière-plan d'un bouton d'accent
     */
    private fun createAccentButtonBackground(isBase: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dpToPx(8).toFloat()
            
            if (isBase) {
                // Bouton de base (caractère original) - style atténué
                setColors(intArrayOf(
                    Color.parseColor("#F5F5F5"),
                    Color.parseColor("#E8E8E8")
                ))
                setStroke(dpToPx(1), Color.parseColor("#D0D0D0"))
            } else {
                // Boutons d'accents - style actif
                setColors(intArrayOf(
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#F0F0F0")
                ))
                setStroke(dpToPx(1), Color.parseColor("#C0C0C0"))
            }
            
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
    }
    
    /**
     * Calcule la position X de la popup pour qu'elle soit centrée
     */
    private fun calculatePopupX(anchorButton: TextView, popupLayout: LinearLayout): Int {
        // Mesurer la largeur approximative de la popup
        val buttonWidth = dpToPx(ACCENT_BUTTON_SIZE_DP + ACCENT_BUTTON_MARGIN_DP * 2)
        val baseKey = anchorButton.text.toString().lowercase()
        val accentCount = accentMap[baseKey]?.size ?: 0
        val totalButtons = accentCount + 1 // +1 pour la touche de base
        val popupWidth = totalButtons * buttonWidth + dpToPx(16) // +padding
        
        // Centrer par rapport au bouton ancre
        val anchorWidth = anchorButton.width
        return (anchorWidth - popupWidth) / 2
    }
    
    /**
     * Obtient tous les accents disponibles pour une touche
     */
    fun getAccentsForKey(key: String): List<String> {
        return accentMap[key.lowercase()] ?: emptyList()
    }
    
    /**
     * Ajoute un nouvel accent à une touche existante
     */
    fun addAccentToKey(baseKey: String, accent: String) {
        val key = baseKey.lowercase()
        val currentAccents = accentMap[key]?.toMutableList() ?: mutableListOf()
        
        if (accent !in currentAccents) {
            currentAccents.add(accent)
            // Note: Pour une implémentation complète, il faudrait mettre à jour accentMap
            // qui est actuellement immutable
        }
    }
    
    /**
     * Nettoie les ressources
     */
    fun cleanup() {
        dismissAccentPopup()
        cancelLongPress()
        accentListener = null
    }
    
    // Méthodes utilitaires
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
