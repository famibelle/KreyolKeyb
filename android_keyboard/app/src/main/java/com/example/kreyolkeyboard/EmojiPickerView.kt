package com.example.kreyolkeyboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Panneau emoji exhaustif (v10.1.0) : onglets de catégories en haut, pages
 * défilables latéralement (ViewPager2) en dessous, chaque page étant une
 * grille verticale virtualisée (RecyclerView) pour rester fluide même avec
 * ~1900 emojis chargés au total — sans virtualisation, ~1900 vues créées
 * d'un coup aurait un vrai coût mémoire/jank sur les téléphones bas de
 * gamme visés par ce projet (cf. commentaires Samsung ULTRA ailleurs dans
 * le code). Les cellules sont des TextView, pas des Button : le style
 * Button par défaut impose un minWidth/ellipsize qui tronque les glyphes
 * sur écran étroit (constaté sur Galaxy A21s).
 *
 * Le swipe horizontal change de catégorie (ViewPager2) ; le swipe vertical
 * défile dans la catégorie courante (comportement par défaut d'un
 * RecyclerView/GridLayoutManager) : les deux gestes sont orthogonaux et
 * coexistent sans code de désambiguïsation supplémentaire.
 *
 * Depuis la v15.0.0, une catégorie « Récents » ouvre la liste quand
 * [EmojiRecents] n'est pas vide, et le panneau s'ouvre dessus.
 */
class EmojiPickerView(
    context: Context,
    private val accentHandler: AccentHandler?
) : LinearLayout(context) {

    companion object {
        private const val GRID_COLUMNS = 10
        private const val CELL_HEIGHT_DP = 44
        private const val VISIBLE_ROWS = 3
        private const val TAB_HEIGHT_DP = 40
        private const val EMOJI_TEXT_SIZE_SP = 20f
    }

    var onEmojiSelected: ((String) -> Unit)? = null

    private val emojiData = EmojiData.load(context)
    private val tabViews = mutableListOf<TextView>()
    private lateinit var viewPager: ViewPager2

    /**
     * Les catégories affichées : celles de l'actif, précédées des emojis
     * récents quand il y en a (v15.0.0).
     *
     * La liste est figée à la construction et non tenue à jour pendant que le
     * panneau est ouvert. C'est délibéré : réordonner la grille sous le doigt
     * de quelqu'un qui enchaîne trois emojis lui ferait manquer le troisième.
     * Le panneau étant reconstruit à chaque passage en mode emoji (voir
     * `KeyboardLayoutManager.createEmojiLayout`), la liste est de toute façon
     * à jour à la prochaine ouverture.
     *
     * Aucun onglet « Récents » tant que rien n'a été employé : un premier
     * onglet vide serait une impasse au tout premier usage, qui est justement
     * celui où l'on découvre le panneau.
     */
    private val categories: List<EmojiCategory> = EmojiRecents.lire(context).let { recents ->
        if (recents.isEmpty()) emojiData.categories
        else listOf(EmojiCategory("Récents", "🕒", recents)) + emojiData.categories
    }

    init {
        orientation = VERTICAL
        accentHandler?.loadEmojiSkinTones(emojiData.skinTones)
        addView(createTabRow())
        addView(createPager())
    }

    private fun createTabRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(TAB_HEIGHT_DP))

            categories.forEachIndexed { index, category ->
                // TextView plutôt que Button : le style Button par défaut
                // (Material/AppCompat, hérité même après remise à zéro du
                // fond) impose un minWidth et un ellipsize sur une seule
                // ligne. Sur un écran étroit (constaté sur Galaxy A21s), 9
                // onglets à largeur égale passent sous ce minimum et
                // l'icône emoji se fait tronquer en "…". TextView n'a
                // aucun de ces minimums par défaut.
                val tab = TextView(context).apply {
                    text = category.icon
                    textSize = 16f
                    gravity = Gravity.CENTER
                    minWidth = 0
                    minHeight = 0
                    isSingleLine = true
                    ellipsize = null
                    setPadding(0, 0, 0, 0)
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { viewPager.setCurrentItem(index, true) }
                }
                tabViews.add(tab)
                addView(tab)
            }
        }
    }

    private fun createPager(): ViewPager2 {
        viewPager = ViewPager2(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                dpToPx(VISIBLE_ROWS * CELL_HEIGHT_DP)
            )
            adapter = CategoryPagerAdapter()
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateTabHighlight(position)
                }
            })
        }
        updateTabHighlight(0)
        return viewPager
    }

    private fun updateTabHighlight(selectedIndex: Int) {
        tabViews.forEachIndexed { index, tab ->
            tab.background = if (index == selectedIndex) {
                GradientDrawable().apply {
                    setColor(KeyboardTheme.palette().emojiOngletActif)
                    cornerRadius = dpToPx(6).toFloat()
                }
            } else {
                null
            }
        }
    }

    private inner class CategoryPagerAdapter : RecyclerView.Adapter<CategoryPagerAdapter.PageHolder>() {

        inner class PageHolder(val recyclerView: RecyclerView) : RecyclerView.ViewHolder(recyclerView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val recyclerView = RecyclerView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                layoutManager = GridLayoutManager(parent.context, GRID_COLUMNS)
                setHasFixedSize(true)
            }
            return PageHolder(recyclerView)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.recyclerView.adapter = EmojiGridAdapter(categories[position].emojis)
        }

        override fun getItemCount() = categories.size
    }

    private inner class EmojiGridAdapter(
        private val emojis: List<String>
    ) : RecyclerView.Adapter<EmojiGridAdapter.EmojiHolder>() {

        inner class EmojiHolder(val label: TextView) : RecyclerView.ViewHolder(label)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiHolder {
            // TextView plutôt que Button : voir le commentaire équivalent
            // dans createTabRow (minWidth/ellipsize par défaut de Button
            // tronquant les glyphes sur écran étroit, constaté sur A21s).
            val label = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(CELL_HEIGHT_DP)
                )
                textSize = EMOJI_TEXT_SIZE_SP
                gravity = Gravity.CENTER
                minWidth = 0
                minHeight = 0
                isSingleLine = true
                ellipsize = null
                setPadding(0, 0, 0, 0)
                isClickable = true
                isFocusable = true
            }
            return EmojiHolder(label)
        }

        override fun onBindViewHolder(holder: EmojiHolder, position: Int) {
            val emoji = emojis[position]
            holder.label.text = emoji

            // v10.11.6 : un emoji choisi écrit du texte, donc il se sent et s'entend
            // comme une touche. Le son est joué par KeyFeedback avec l'effet du
            // clavier, d'où le clic d'interface coupé pour ne pas l'entendre deux fois.
            holder.label.isSoundEffectsEnabled = false
            holder.label.setOnClickListener { vue ->
                KeyFeedback.onKeyPress(vue)
                onEmojiSelected?.invoke(emoji)
            }

            // Même double-délai (timeout natif Android + LONG_PRESS_DELAY
            // d'AccentHandler) que les autres touches à appui long du
            // clavier : cohérence de timing avec le reste de l'app.
            holder.label.setOnLongClickListener {
                if (accentHandler?.hasAccents(emoji) == true) {
                    accentHandler.startLongPressTimer(emoji, holder.label)
                }
                true
            }
            holder.label.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    accentHandler?.cancelLongPress()
                }
                false
            }
        }

        override fun getItemCount() = emojis.size
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
