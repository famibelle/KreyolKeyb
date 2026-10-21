package com.example.kreyolkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * La visite guidée : un voile sombre sur l'écran, percé autour de ce qu'on
 * présente, et une bulle de deux phrases avec « Suivant » et « Passer ».
 *
 * Demandée par le propriétaire (2026-10-21) : sa mère ne comprenait pas toutes
 * les fonctions de l'application. Il y en a deux, montées par leurs hôtes :
 * celle de l'application (les onglets, les réglages, les trois gestes du
 * clavier), dans [SettingsActivity], et celle du carnet (la carte et ses
 * zones, le partage, l'échange), dans `CarnetFragment`.
 *
 * Aucune bibliothèque : une vue posée par-dessus l'hôte, qui dessine le voile
 * et sa découpe. La cible est relue à chaque image, en coordonnées de fenêtre,
 * si bien que la découpe suit une carte qui s'incline ou un onglet du rail
 * d'une tablette sans rien savoir de la mise en page. Les textes ne nomment en
 * kréyòl que des noms de l'application, jamais une phrase composée ici.
 */
object VisiteGuidee {

    /** Le fichier de préférences où les deux visites se notent faites. */
    const val PREFS = "kreyol_onboarding_prefs"
    const val VISITE_APPLICATION = "visite_application_faite"
    const val VISITE_CARNET = "visite_carnet_faite"

    /**
     * L'application se présente-t-elle d'elle-même ?
     *
     * Une fois, et seulement sur l'accueil du jour : tant que le clavier n'est
     * pas installé, c'est l'installation qui passe en premier. Jamais par-dessus
     * la pochette de bienvenue ni un écran ouvert devant l'accueil.
     */
    fun doitSeLancer(dejaFaite: Boolean, accueilDuJour: Boolean, autreChoseOuvert: Boolean): Boolean =
        !dejaFaite && accueilDuJour && !autreChoseOuvert

    /** Le carnet se présente une fois, dès qu'il porte une carte à montrer. */
    fun carnetDoitSeLancer(dejaFaite: Boolean, cartes: Int, carteDemandee: Boolean): Boolean =
        !dejaFaite && cartes > 0 && !carteDemandee

    /**
     * Une zone de la carte, donnée en unités de carte (300 de large), ramenée
     * aux pixels de l'écran d'après la position et la largeur de la carte.
     */
    fun zoneDeCarte(zone: RectF, gauche: Float, haut: Float, largeurPx: Float, largeurUnites: Float): RectF {
        val u = largeurPx / largeurUnites
        return RectF(gauche + zone.left * u, haut + zone.top * u, gauche + zone.right * u, haut + zone.bottom * u)
    }

    /** Le plus petit rectangle qui contient toutes les zones. */
    fun reunion(vararg zones: RectF): RectF = RectF(zones.first()).apply { zones.drop(1).forEach { union(it) } }

    /**
     * L'avancée dans les étapes, sans Android : [VisiteGuideeTest]. L'hôte ne
     * donne que les étapes qu'il peut montrer (la ligne des réglages n'existe
     * que sur l'accueil du jour), si bien que le compteur ne ment jamais.
     */
    class Deroule(private val nombre: Int, depart: Int = 0) {
        var index = depart.coerceIn(0, (nombre - 1).coerceAtLeast(0))
            private set

        val derniere: Boolean get() = index >= nombre - 1

        /** Passe à l'étape suivante ; `false` quand il n'y en a plus. */
        fun suivante(): Boolean {
            if (derniere) return false
            index++
            return true
        }
    }

    /** « 2 / 6 ». */
    fun compteur(rang: Int, montrees: Int): String = "${rang + 1} / $montrees"

    /**
     * Une étape. [preparer] amène l'écran où il faut (un onglet, une carte
     * ouverte) puis appelle sa suite ; [cible] rend la zone à éclairer en
     * coordonnées de fenêtre, ou `null` pour une bulle seule au centre.
     * [autres] ajoute des découpes séparées : la plaque du mot et le chiffre
     * du coin, réunis en un seul rectangle, éclairaient aussi tout le paysage.
     */
    class Etape(
        val titre: String,
        val texte: String,
        val preparer: (suite: () -> Unit) -> Unit = { it() },
        val cible: () -> RectF? = { null },
        val autres: () -> List<RectF> = { emptyList() }
    )

    /** Le rectangle d'une vue en coordonnées de fenêtre, ou `null` si elle n'est pas à l'écran. */
    fun rectDe(vue: View?): RectF? {
        if (vue == null || !vue.isShown || vue.width == 0) return null
        val p = IntArray(2)
        vue.getLocationInWindow(p)
        return RectF(p[0].toFloat(), p[1].toFloat(), (p[0] + vue.width).toFloat(), (p[1] + vue.height).toFloat())
    }

    /** La première vue portant [etiquette] qui soit vraiment à l'écran (le pager en garde d'autres hors champ). */
    fun vueVisible(racine: View, etiquette: String): View? =
        vues(racine, etiquette).firstOrNull { it.getGlobalVisibleRect(android.graphics.Rect()) }

    /**
     * La vue portant [etiquette] dans la page à l'écran, même sous le pli :
     * celle qu'on fait défiler jusqu'à la vue avant de l'éclairer. Les pages
     * voisines du pager sont à côté de l'écran, pas dessous, d'où le seul
     * contrôle horizontal.
     */
    fun vueDeLaPage(racine: View, etiquette: String): View? {
        val p = IntArray(2)
        return vues(racine, etiquette).firstOrNull {
            it.getLocationOnScreen(p)
            val milieu = p[0] + it.width / 2
            milieu in 0 until racine.resources.displayMetrics.widthPixels
        }
    }

    private fun vues(racine: View, etiquette: String): List<View> {
        val trouvees = ArrayList<View>()
        fun parcourir(v: View) {
            if (v.tag == etiquette && v.isShown) trouvees.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) parcourir(v.getChildAt(i))
        }
        parcourir(racine)
        return trouvees
    }

    /**
     * Attend que [pret] soit vrai, au plus [delaiMs], puis appelle [puis] :
     * le temps qu'un onglet glisse ou qu'une carte se retourne.
     */
    fun attendre(pret: () -> Boolean, delaiMs: Long = 2500L, puis: () -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val debut = System.currentTimeMillis()
        fun essayer() {
            if (pret() || System.currentTimeMillis() - debut > delaiMs) puis()
            else main.postDelayed({ essayer() }, 80L)
        }
        main.postDelayed({ essayer() }, 80L)
    }

    /**
     * Le voile lui-même, posé sur [hote]. [surFin] reçoit `true` quand la
     * visite est allée au bout, `false` quand elle a été passée.
     */
    @SuppressLint("ViewConstructor")
    class Voile(
        private val hote: FrameLayout,
        private val etapes: List<Etape>,
        depart: Int = 0,
        private val surEtape: (Int) -> Unit = {},
        private val surFin: (terminee: Boolean) -> Unit
    ) : FrameLayout(hote.context) {

        private val d = resources.displayMetrics.density
        private val deroule = Deroule(etapes.size, depart)
        private val voile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3000000.toInt() }
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * d
            color = Color.parseColor("#FFC94D")
        }
        private val chemin = Path().apply { fillType = Path.FillType.EVEN_ODD }
        /** Toutes les découpes de l'étape, et leur réunion, qui place la bulle. */
        private var trous: List<RectF> = emptyList()
        private var trou: RectF? = null
        private val ici = IntArray(2)
        private var attenteFinie = true
        private var fermee = false

        private val compteur: TextView
        private val titre: TextView
        private val texte: TextView
        private val suivant: TextView
        private val bulle: LinearLayout

        private val suivi = ViewTreeObserver.OnPreDrawListener {
            recalculerTrou(repositionner = false)
            true
        }

        init {
            setWillNotDraw(false)
            isClickable = true
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            elevation = 40f * d

            fun dp(v: Float) = (v * d).toInt()
            bulle = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20f), dp(16f), dp(20f), dp(14f))
                background = GradientDrawable().apply {
                    cornerRadius = 18f * d
                    setColor(Color.WHITE)
                }
                elevation = 6f * d
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                isClickable = true
            }
            compteur = TextView(context).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#6B6B6B"))
            }
            titre = TextView(context).apply {
                textSize = 19f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1C"))
                setPadding(0, dp(4f), 0, dp(6f))
            }
            texte = TextView(context).apply {
                textSize = 16f
                setTextColor(Color.parseColor("#333333"))
                setLineSpacing(0f, 1.2f)
            }
            val passer = TextView(context).apply {
                text = context.getString(R.string.visite_passer)
                textSize = 15f
                setTextColor(Color.parseColor("#6B6B6B"))
                setPadding(dp(4f), dp(12f), dp(16f), dp(12f))
                isClickable = true
                isFocusable = true
                setOnClickListener { finir(terminee = false) }
            }
            suivant = TextView(context).apply {
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(22f), dp(12f), dp(22f), dp(12f))
                minimumHeight = dp(48f)
                background = GradientDrawable().apply {
                    cornerRadius = 24f * d
                    setColor(Color.parseColor("#0080FF"))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { avancer() }
            }
            bulle.addView(compteur)
            bulle.addView(titre)
            bulle.addView(texte)
            bulle.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14f), 0, 0)
                addView(passer)
                addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                addView(suivant)
            })
            addView(bulle)
        }

        /** Pose le voile et montre la première étape. */
        fun ouvrir() {
            hote.addView(this)
            viewTreeObserver.addOnPreDrawListener(suivi)
            alpha = 0f
            animate().alpha(1f).setDuration(200L).start()
            montrer()
        }

        /** Le bouton Retour : la visite est passée, comme avec « Passer ». */
        fun passer() = finir(terminee = false)

        val etapeCourante: Int get() = deroule.index

        private fun avancer() {
            if (!attenteFinie) return
            if (deroule.suivante()) montrer() else finir(terminee = true)
        }

        private fun montrer() {
            val e = etapes[deroule.index]
            surEtape(deroule.index)
            attenteFinie = false
            bulle.animate().alpha(0f).setDuration(120L).start()
            e.preparer {
                if (fermee) return@preparer
                attenteFinie = true
                compteur.text = compteur(deroule.index, etapes.size)
                titre.text = e.titre
                texte.text = e.texte
                suivant.text = context.getString(
                    if (deroule.derniere) R.string.visite_terminer else R.string.visite_suivant
                )
                recalculerTrou(repositionner = true)
                bulle.animate().alpha(1f).setDuration(180L).start()
                bulle.announceForAccessibility("${e.titre}. ${e.texte}")
                suivant.requestFocus()
            }
        }

        private fun recalculerTrou(repositionner: Boolean) {
            if (!attenteFinie || fermee) return
            val e = etapes[deroule.index]
            getLocationInWindow(ici)
            val m = 6f * d
            val nouveaux = (listOfNotNull(e.cible()) + e.autres()).map {
                RectF(it.left - ici[0] - m, it.top - ici[1] - m, it.right - ici[0] + m, it.bottom - ici[1] + m)
            }
            val bouge = nouveaux.size != trous.size || nouveaux.zip(trous).any { (a, b) ->
                abs(a.left - b.left) > 1f || abs(a.top - b.top) > 1f ||
                    abs(a.right - b.right) > 1f || abs(a.bottom - b.bottom) > 1f
            }
            if (bouge) {
                trous = nouveaux
                trou = if (nouveaux.isEmpty()) null else reunion(*nouveaux.toTypedArray())
                invalidate()
            }
            if (repositionner) placerBulle()
        }

        /** Sous la cible si elle est en haut, au-dessus sinon, au centre sans cible. */
        private fun placerBulle() {
            // Une étape sans préparation se montre avant la première mise en
            // page du voile : sans sa hauteur, la bulle partait hors de l'écran.
            if (height == 0) {
                post { recalculerTrou(repositionner = true) }
                return
            }
            val marge = (16 * d).toInt()
            val lp = bulle.layoutParams as LayoutParams
            val largeurMax = (420 * d).toInt()
            lp.width = if (width - 2 * marge > largeurMax) largeurMax else LayoutParams.MATCH_PARENT
            lp.setMargins(marge, 0, marge, 0)
            val t = trou
            when {
                t == null -> {
                    lp.gravity = Gravity.CENTER
                    lp.topMargin = 0
                    lp.bottomMargin = 0
                }
                t.centerY() < height / 2f -> {
                    lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    lp.topMargin = (t.bottom + marge).toInt().coerceAtMost(height - (220 * d).toInt())
                }
                else -> {
                    lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    lp.bottomMargin = (height - t.top + marge).toInt().coerceAtMost(height - (220 * d).toInt())
                }
            }
            bulle.layoutParams = lp
        }

        override fun onDraw(canvas: Canvas) {
            chemin.reset()
            chemin.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            val r = 14f * d
            trous.forEach { chemin.addRoundRect(it, r, r, Path.Direction.CW) }
            canvas.drawPath(chemin, voile)
            trous.forEach { canvas.drawRoundRect(it, r, r, halo) }
        }

        private fun finir(terminee: Boolean) {
            if (fermee) return
            fermee = true
            viewTreeObserver.removeOnPreDrawListener(suivi)
            animate().alpha(0f).setDuration(160L).withEndAction {
                (parent as? ViewGroup)?.removeView(this)
            }.start()
            surFin(terminee)
        }
    }
}
