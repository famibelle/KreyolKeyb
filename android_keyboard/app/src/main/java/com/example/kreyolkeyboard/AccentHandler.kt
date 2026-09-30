package com.example.kreyolkeyboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
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
    
    // État du mode majuscule
    var isCapitalMode: Boolean = false
    
    // Configuration des accents pour chaque touche de base
    // Révisé pour le duo kréyòl/français réellement supporté (v8.2.0) :
    // é/è/ò sont déjà des touches dédiées du clavier, donc retirées d'ici.
    // ch/dj/ng sont des digraphes fréquents en graphie créole GEREC.
    // v8.3.0 : ponctuation ajoutée sur les touches , . ' déjà visibles en mode
    // alphabétique, pour éviter l'aller-retour vers le mode 123.
    // v8.4.0 : ë et ü retirés (0 occurrence dans creole_dict.json comme dans
    // french_simple_dict.json) ; œ ajouté (« œil », « cœur » dans le dico
    // français, absent du clavier jusqu'ici), sur la base d'un comptage des
    // diacritiques réellement présents dans les deux dictionnaires.
    // v8.5.0 : trait d'union remonté en tête de l'appui long sur '.' (donc
    // affiché dans l'indice de coin). 21,7% des mots créoles en contiennent
    // un (marqueur d'élision : "a-y", "ba-w", "an-nou"...), fréquence cumulée
    // 26 623, supérieure à celle de la touche dédiée "ò" (18 699).
    // v8.6.0 : "-" devient touche dédiée (KeyboardLayoutManager, rangée 4),
    // retiré d'ici comme é/è/ò l'avaient été en v8.2.0.
    // v8.7.0 : é et è rejoignent l'appui long sur "e" (déjà des touches dédiées
    // par ailleurs), classés par fréquence décroissante dans creole_dict.json :
    // é (86 743, 1603 mots) > è (45 490, 992 mots) > ê (15, 1 mot).
    // v8.7.0 (suite) : ò rejoint l'appui long sur "o" (déjà touche dédiée par
    // ailleurs, même logique que é/è sur "e") ; ordre choisi ò, ô, ó, œ.
    // v8.7.3 : digraphes GEREC ajoutés, sur la base d'un comptage des
    // occurrences cumulées (creole_dict.json + french_simple_dict.json) :
    // "t" gagne "tj" (/tʃ/, 184) aux côtés de ch et dj déjà couverts par c/d.
    // v10.12.8 : ng, ny, gn et gy retirés, laissant les touches "n" et "g"
    // sans appui long. Le comptage de v8.7.3 portait sur les occurrences du
    // digraphe n'importe où dans le mot, or l'appui long n'est utile qu'aux
    // endroits où l'on hésite sur la saisie : ces quatre-là s'écrivent sans
    // difficulté avec les deux touches voisines, contrairement aux caractères
    // accentués absents du clavier.
    private val accentMap = mapOf(
        "a" to listOf("à", "â"),
        "e" to listOf("é", "è", "ê"),
        "i" to listOf("î", "ï"),
        "o" to listOf("ò", "ô", "ó", "œ"),
        "u" to listOf("ù", "û"),
        "c" to listOf("ç", "ch"),
        "d" to listOf("dj"),
        "t" to listOf("tj"),
        // v9.1.0 : "'" n'est plus une touche visible dédiée (0 occurrence dans
        // creole_dict.json) ; rejoint l'appui long sur "," pour libérer une
        // place en rangée 4 pour la touche emoji. Les guillemets/quote qui
        // vivaient sous l'appui long de "'" disparaissent avec elle (aucun
        // usage relevé dans les dictionnaires non plus).
        "," to listOf(";", ":", "'"),
        "." to listOf("!", "?", "…"),
        // Page 123 (v22.4.0, repris de LuxKeyb 29.3.0). C'est la seule page de
        // symboles du clavier, et il y manquait de quoi écrire une adresse
        // (« _ »), une température (« ° »), un prix en dollars ou en livres, des
        // crochets. Rangés en appui long sous la touche la plus proche par la
        // forme ou le sens, plutôt que sur une seconde page : l'aperçu en coin
        // les rend visibles sans rien retirer aux touches existantes. Aucune de
        // ces touches n'avait d'appui long. Le « - » de la rangée des lettres
        // partage l'entrée et gagne « _ » au passage.
        "-" to listOf("_", "–"),
        "/" to listOf("\\", "|"),
        "(" to listOf("[", "{", "<"),
        ")" to listOf("]", "}", ">"),
        "€" to listOf("$", "£", "¥"),
        "%" to listOf("&", "‰"),
        // Le kréyòl s'écrit avec la typographie française : « » d'abord
        "\"" to listOf("«", "»", "„"),
        "=" to listOf("≠", "~", "^"),
        "+" to listOf("±", "×", "÷"),
        "*" to listOf("°", "•"),
        "0" to listOf("°")
    )

    /**
     * Terminaisons proposées en appui long sur « . » dans une adresse
     * électronique ou web, à la place de « ! ? … » qui n'y ont rien à faire.
     * `.com` et `.fr` d'abord, de loin les plus tapées en Guadeloupe ; `.gp`
     * ensuite, le domaine de l'île. Toujours en minuscules, Maj ou pas.
     */
    private val TERMINAISONS_ADRESSE = listOf(".com", ".fr", ".gp", ".org")

    /**
     * Le champ courant est une adresse (courriel ou web) : l'appui long sur
     * « . » y propose les terminaisons de domaine. Posé par le service à chaque
     * prise de focus, avant la construction de la vue, pour que l'aperçu en coin
     * suive.
     */
    var champAdresse: Boolean = false

    /** Options d'appui long de [key] dans le champ courant, `null` s'il n'en a pas. */
    private fun optionsDe(key: String): List<String>? {
        if (champAdresse && key == ".") return TERMINAISONS_ADRESSE
        return accentMap[key.lowercase()] ?: emojiSkinTones[key]
    }

    /**
     * Une option d'appui long suit la touche Maj, sauf une terminaison
     * d'adresse : « .COM » serait juste en théorie et faux à l'œil. Les
     * digraphes (« ch », « dj ») gardent leur passage en capitales.
     */
    private fun casse(option: String): String =
        if (isCapitalMode && !option.startsWith(".")) option.uppercase() else option

    // Ordre d'affichage des aperçus en coin, quand il doit différer de l'ordre
    // du popup d'appui long (v8.7.0) : "e" affiche "è" en haut-droit et "é" en
    // bas-droit, alors que le popup liste é avant è (fréquence décroissante).
    private val cornerHintOverrides = mapOf(
        "e" to listOf("è", "é"),
        "o" to listOf("ò", "ó")
    )

    // Tons de peau pour le panneau emoji exhaustif (v10.1.0), chargés depuis
    // emoji_data.json au démarrage du clavier (EmojiData.skinTones) : clé =
    // emoji au ton foncé par défaut affiché dans la grille, valeur = les 4
    // autres tons + la version neutre/jaune, dans cet ordre. Contrairement à
    // accentMap (fixe, connu à la compilation), c'est une donnée chargée à
    // l'exécution, d'où un champ mutable séparé plutôt qu'une entrée de plus
    // dans accentMap.
    private var emojiSkinTones: Map<String, List<String>> = emptyMap()

    fun loadEmojiSkinTones(skinTones: Map<String, List<String>>) {
        emojiSkinTones = skinTones
    }

    // ├ëtat actuel
    private var currentAccentPopup: PopupWindow? = null
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false
    private var currentBaseCharacter: String? = null
    
    // Callbacks
    interface AccentSelectionListener {
        fun onAccentSelected(accent: String, baseCharacter: String)
        fun onLongPressStarted(baseKey: String)
        fun onLongPressCancelled()
    }
    
    private var accentListener: AccentSelectionListener? = null
    
    fun setAccentSelectionListener(listener: AccentSelectionListener) {
        this.accentListener = listener
    }
    
    /**
     * V├®rifie si une touche a des accents disponibles
     */
    fun hasAccents(key: String): Boolean {
        return optionsDe(key) != null
    }
    
    /**
     * Démarre le timer de pression longue pour une touche
     */
    fun startLongPressTimer(key: String, anchorButton: View) {
        if (!hasAccents(key)) return
        
        cancelLongPress()
        currentBaseCharacter = key  // Stocker le caractère de base
        
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
     * V├®rifie si une pression longue est en cours
     */
    fun isLongPressActive(): Boolean {
        return isLongPressTriggered
    }
    
    /**
     * Affiche la popup d'accents pour une touche de base
     */
    fun showAccentPopup(baseKey: String, anchorButton: View) {
        val accents = optionsDe(baseKey) ?: return
        
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
                false  // ✅ CORRECTION: focusable=false pour éviter fermeture IME
            ).apply {
                // Style de la popup
                setBackgroundDrawable(createPopupBackground())
                elevation = dpToPx(POPUP_ELEVATION_DP).toFloat()
                
                // Configuration pour IME
                isTouchable = true  // ✅ Permet interaction avec popup
                isOutsideTouchable = true  // ✅ Ferme popup si clic extérieur
                
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
                    // Popup d├®j├á ferm├®
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur lors de la fermeture de la popup: ${e.message}")
            }
        }
        currentAccentPopup = null
        isLongPressTriggered = false
    }
    
    /**
     * Cr├®e le layout de la popup d'accents
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
            // Appliquer la majuscule si le mode est actif
            text = casse(accent)
            // Une terminaison d'adresse (« .com ») tient sur une ligne dans les
            // 48 dp de la case : plus petite, et sans la marge interne du bouton
            // (un emoji à ton de peau fait aussi plusieurs caractères : le
            // critère est le point initial, pas la longueur)
            val terminaison = accent.startsWith(".") && accent.length > 1
            textSize = if (terminaison) 14f else 18f
            if (terminaison) {
                setPadding(0, 0, 0, 0)
                minWidth = 0
                isAllCaps = false
                maxLines = 1
            }
            setTextColor(
                KeyboardTheme.palette().let { if (isBase) it.popupBaseEncre else it.popupAccentEncre }
            )
            
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
            
            // ├ëv├®nement de clic
            // Le son de frappe vient de KeyFeedback, avec l'effet du clavier et non le
            // clic d'interface que performClick() ajouterait sinon par-dessus.
            isSoundEffectsEnabled = false

            setOnClickListener { bouton ->
                // v10.11.6 : ces touches écrivent un caractère, elles doivent se sentir
                // et s'entendre comme celles du clavier. Elles sonnaient déjà, par le
                // clic générique du framework, mais ne vibraient pas.
                KeyFeedback.onKeyPress(bouton)
                handleAccentSelection(accent)
            }
            
            // Animation tactile l├®g├¿re
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
     * G├¿re la s├®lection d'un accent
     */
    private fun handleAccentSelection(accent: String) {
        val baseChar = currentBaseCharacter ?: ""
        // Appliquer la majuscule si le mode est actif
        val finalAccent = casse(accent)
        accentListener?.onAccentSelected(finalAccent, baseChar)
        dismissAccentPopup()
        currentBaseCharacter = null  // Nettoyer après usage
        
        Log.d(TAG, "Accent sélectionné: '$finalAccent' pour base: '$baseChar'")
    }
    
    /**
     * Cr├®e l'arri├¿re-plan de la popup
     */
    private fun createPopupBackground(): GradientDrawable {
        val p = KeyboardTheme.palette()
        return GradientDrawable().apply {
            cornerRadius = dpToPx(12).toFloat()
            setColors(intArrayOf(p.popupHaut, p.popupBas))
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            setStroke(dpToPx(1), p.popupBordure)
        }
    }
    
    /**
     * Cr├®e l'arri├¿re-plan d'un bouton d'accent
     */
    private fun createAccentButtonBackground(isBase: Boolean): GradientDrawable {
        val p = KeyboardTheme.palette()
        return GradientDrawable().apply {
            cornerRadius = dpToPx(8).toFloat()
            
            if (isBase) {
                // Bouton de base (caract├¿re original) - style att├®nu├®
                setColors(intArrayOf(p.popupBaseHaut, p.popupBaseBas))
                setStroke(dpToPx(1), p.popupBaseBordure)
            } else {
                // Boutons d'accents - style actif
                setColors(intArrayOf(p.popupAccentHaut, p.popupAccentBas))
                setStroke(dpToPx(1), p.popupAccentBordure)
            }
            
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
    }
    
    /**
     * Calcule la position X de la popup pour qu'elle soit centrée
     */
    private fun calculatePopupX(anchorButton: View, popupLayout: LinearLayout): Int {
        // Mesurer la largeur approximative de la popup
        val buttonWidth = dpToPx(ACCENT_BUTTON_SIZE_DP + ACCENT_BUTTON_MARGIN_DP * 2)
        
        // Récupérer la clé de base depuis le tag (ImageButton) ou le texte (Button)
        val baseKey = when (anchorButton) {
            is Button -> anchorButton.text.toString().lowercase()
            is android.widget.ImageButton -> (anchorButton.tag as? String)?.lowercase() ?: ""
            else -> ""
        }
        
        val accentCount = optionsDe(baseKey)?.size ?: 0
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
        return optionsDe(key) ?: emptyList()
    }

    /**
     * Obtient les accents à afficher en aperçu dans les coins de la touche,
     * dans l'ordre haut-droit puis bas-droit (peut différer de l'ordre du
     * popup d'appui long, voir cornerHintOverrides)
     */
    fun getCornerHintsForKey(key: String): List<String> {
        // « .com » dans un coin de touche serait illisible : pas d'aperçu
        if (champAdresse && key == ".") return emptyList()
        return cornerHintOverrides[key.lowercase()] ?: getAccentsForKey(key)
    }

    /**
     * Ajoute un nouvel accent ├á une touche existante
     */
    fun addAccentToKey(baseKey: String, accent: String) {
        val key = baseKey.lowercase()
        val currentAccents = accentMap[key]?.toMutableList() ?: mutableListOf()
        
        if (accent !in currentAccents) {
            currentAccents.add(accent)
            // Note: Pour une impl├®mentation compl├¿te, il faudrait mettre ├á jour accentMap
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
    
    // M├®thodes utilitaires
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
