package com.example.kreyolkeyboard.carnet

import com.example.kreyolkeyboard.R
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes

/**
 * La carte entière, posée au-dessus de l'écran d'où on l'ouvre, et **retournée
 * pour arriver** : c'est le geste qui fait la carte à collectionner.
 *
 * Partagée par le carnet et la boîte de Leitner, qui avaient chacun la leur et
 * ont divergé : celle de la boîte, à la largeur de l'écran dans un défilement,
 * ne montrait sur tablette que l'illustration.
 *
 * Elle tient entière sur l'écran. On passe à la voisine d'un glissé de côté,
 * des flèches sur tablette ou de celles d'un clavier ([feuilleter]), dans
 * l'ordre de [cartes] ; un glissé vers le haut ou le bas, ou un toucher à côté,
 * la repose. [surChangement] dit à l'appelant quelle carte est montrée, pour
 * que l'éventail de la boîte suive.
 */
internal class LecteurCartes(
    private val hote: FrameLayout,
    private val cartes: List<ContenuCarte>,
    private var rang: Int,
    private val surChangement: (Int) -> Unit = {},
    private val surFermeture: () -> Unit = {},
    /**
     * Céder la carte montrée à un autre téléphone (`CESSION-CARTES.md`).
     * `null` là où la cession n'a pas de sens, comme la boîte de révision :
     * le bouton n'apparaît pas.
     */
    private val surCeder: ((ContenuCarte) -> Unit)? = null
) {
    private val ctx = hote.context
    private val d = ctx.resources.displayMetrics.density
    private val tablette = ctx.resources.configuration.smallestScreenWidthDp >= 600

    private var voile: VoileGlissable? = null
    private var porte: FrameLayout? = null
    private var flechePrecedente: View? = null
    private var flecheSuivante: View? = null

    val ouvert: Boolean get() = voile != null

    /** La carte montrée, et vrai une fois retournée et posée : la visite du carnet l'éclaire par zones. */
    var carteMontree: View? = null
        private set
    var posee = false
        private set
    var boutonPartager: View? = null
        private set
    var boutonCeder: View? = null
        private set

    fun ouvrir() {
        val v = VoileGlissable(ctx) { sensX, sensY ->
            if (sensX != 0 && cartes.size > 1) feuilleter(-sensX) else fermer(sensX, sensY)
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xCC000000.toInt())
            isClickable = true
            setOnClickListener { fermer() }
        }

        // Le porte-carte est le contenu que le voile fait suivre au doigt.
        val marge = ((if (tablette) 88 else 22) * d).toInt()
        val p = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // En bas, la place du bouton de partage : la carte ne passe pas dessous.
            setPadding(marge, (24 * d).toInt(), marge, (84 * d).toInt())
        }
        v.addView(p)
        porte = p

        // Partager la carte montrée : un geste du joueur, la seule sortie du
        // carnet hors de l'appareil avec la cession. Voir [PartageCarte] et
        // [Cession].
        val boutons = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = (22 * d).toInt() }
        }
        boutons.addView(pastille(ctx.getString(R.string.carte_partager)) {
            PartageCarte.partager(ctx, cartes[rang])
        }.also { boutonPartager = it })
        surCeder?.let { ceder ->
            boutons.addView(pastille(ctx.getString(R.string.carte_ceder)) { ceder(cartes[rang]) }.apply {
                (layoutParams as LinearLayout.LayoutParams).marginStart = (12 * d).toInt()
                boutonCeder = this
            })
        }
        v.addView(boutons)

        // Les flèches, sur tablette seulement : un téléphone n'a pas la marge
        // pour les poser sans couvrir la carte, et le glissé y suffit.
        if (tablette && cartes.size > 1) {
            flechePrecedente = fleche("‹", R.string.album_carte_precedente, -1, Gravity.START)
                .also { v.addView(it) }
            flecheSuivante = fleche("›", R.string.album_carte_suivante, 1, Gravity.END)
                .also { v.addView(it) }
        }

        voile = v
        hote.addView(v)
        FigerOrientation.tantQue(v)
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(160).start()
        poser(0)
    }

    private fun pastille(libelle: String, action: () -> Unit) = TextView(ctx).apply {
        text = libelle
        textSize = 15f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        val h = (22 * d).toInt()
        val vert = (11 * d).toInt()
        setPadding(h, vert, h, vert)
        background = GradientDrawable().apply {
            cornerRadius = 24f * d
            setColor(Carnet.COULEUR)
            setStroke((1.5f * d).toInt(), 0x66FFFFFF)
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fleche(signe: String, @StringRes description: Int, pas: Int, cote: Int) =
        TextView(ctx).apply {
            text = signe
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = ctx.getString(description)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33FFFFFF)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { feuilleter(pas) }
            layoutParams = FrameLayout.LayoutParams(
                (60 * d).toInt(), (60 * d).toInt(), Gravity.CENTER_VERTICAL or cote
            ).apply { marginStart = (14 * d).toInt(); marginEnd = (14 * d).toInt() }
        }

    /**
     * Pose la carte de rang [rang]. [sens] vaut 0 à l'ouverture, qui retourne
     * la carte, et ±1 quand on feuillette, où elle arrive du côté d'où l'on
     * tourne.
     */
    private fun poser(sens: Int) {
        val p = porte ?: return
        val c = cartes[rang]

        p.removeAllViews()
        p.translationX = 0f
        p.translationY = 0f
        voile?.background?.alpha = 255

        val carte = CarteCarnet.complete(ctx, c)
        carteMontree = carte
        posee = false
        (carte as? Carton)?.ajusteALaHauteur = true
        (carte as? CarteOrnee)?.avecAmbiance()
        // La carte ne ferme pas : on peut la lire.
        carte.isClickable = true
        p.addView(carte, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))

        flechePrecedente?.alpha = if (rang > 0) 1f else 0.25f
        flecheSuivante?.alpha = if (rang < cartes.lastIndex) 1f else 0.25f

        carte.cameraDistance = 9000f * d
        // La carte tourne d'un bloc, comme une image : retournement, feuilletage
        // et inclinaison ne changent que l'angle, et sans couche chaque angle
        // refaisait tous ses tracés. Sur un Galaxy A21s (2026-10-12), le
        // retournement passait ainsi sous les 25 images par seconde.
        carte.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val armer = {
            // Une fois posée, la carte suit la main : le suivi ne s'arme qu'ici
            // pour ne pas écrire dans `rotationY` pendant le retournement.
            Inclinaison.suivre(carte)
            (carte as? Carton)?.sensibleAuDoigt = true
            posee = true
        }
        val lancer = {
            if (sens == 0) {
                // Une carte rare se retourne plus lentement et dépasse légèrement
                // son aplomb avant de se poser : le même geste, mais qui prend son
                // temps.
                carte.rotationY = -85f
                carte.animate().rotationY(0f)
                    .setDuration(if (c.rarete.distinguee) 470L else 360L)
                    .setInterpolator(
                        if (c.rarete.distinguee) OvershootInterpolator(1.4f)
                        else DecelerateInterpolator()
                    )
                    .withEndAction(armer)
                    .start()
            } else {
                carte.translationX = sens * p.width * 0.35f
                carte.rotationY = sens * 35f
                carte.alpha = 0f
                carte.animate().translationX(0f).rotationY(0f).alpha(1f)
                    .setDuration(240L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction(armer)
                    .start()
            }
        }
        // Le paysage et le métal se préparent en fond : la carte reste cachée,
        // puis se retourne d'un seul geste. Voir [CarteOrnee.attendreSaMatiere].
        //
        // Puis une image est dessinée **avant** le geste, la carte de profil
        // ou presque transparente : la toute première carte ouverte coûtait
        // 0,3 s de préparation graphique à cette image-là (Galaxy A21s,
        // 2026-10-12), et cette pause tombait au début du retournement, qui
        // sautait alors son premier tiers.
        val ornee = carte as? CarteOrnee
        chaud = true
        if (ornee != null) {
            carte.visibility = View.INVISIBLE
            ornee.attendreSaMatiere {
                if (sens == 0) carte.rotationY = -89f
                else carte.alpha = 0.01f
                carte.visibility = View.VISIBLE
                apresUneImage {
                    lancer()
                    preparerVoisines(carte.width)
                }
            }
        } else lancer()
    }

    /** [action] une fois l'image suivante dessinée. */
    private fun apresUneImage(action: () -> Unit) = uneImagePlusTard { if (ouvert) action() }

    /**
     * Prépare en fond les deux cartes voisines de celle qu'on regarde, pour
     * qu'un glissé de côté n'attende pas leur paysage et leur métal : voir
     * [CarteCarnet.preparer].
     */
    private fun preparerVoisines(largeurPx: Int) {
        val voisines = listOf(rang + 1, rang - 1).filter { it in cartes.indices }.map { cartes[it] }
        if (voisines.isEmpty() || largeurPx <= 0) return
        Thread({
            for (c in voisines) {
                try {
                    CarteCarnet.preparer(ctx, c, largeurPx)
                } catch (e: Throwable) {
                    // La voisine se préparera à son tour, au glissé.
                }
            }
        }, "voisines").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** Passe à la carte voisine ; au bout de la liste, la carte revient en place. */
    fun feuilleter(pas: Int) {
        if (!ouvert) return
        val suivant = rang + pas
        if (suivant !in cartes.indices) {
            porte?.animate()?.translationX(0f)?.setDuration(200)?.start()
            voile?.background?.alpha = 255
            return
        }
        rang = suivant
        poser(pas)
        surChangement(rang)
    }

    /** [sensX] ou [sensY] non nul : la carte a été chassée d'un glissé, elle sort par ce côté. */
    fun fermer(sensX: Int = 0, sensY: Int = 0) {
        val v = voile ?: return
        voile = null
        val p = porte
        porte = null
        if ((sensX != 0 || sensY != 0) && p != null) {
            p.animate()
                .translationX(sensX * v.width.toFloat())
                .translationY(sensY * v.height.toFloat())
                .setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        }
        v.animate().alpha(0f).setDuration(160)
            .withEndAction { hote.removeView(v) }.start()
        surFermeture()
    }

    companion object {
        /** Une carte complète a-t-elle déjà été dessinée dans ce processus ? */
        @Volatile
        private var chaud = false

        /**
         * Dessine une fois, presque transparente et sous tout le reste, une
         * carte complète dans [hote], puis la retire.
         *
         * La toute première carte ouverte depuis le lancement de l'appli
         * attendait 0,3 à 0,7 s de préparation graphique avant de se
         * retourner, les suivantes non (Galaxy A21s, 2026-10-12). Le carnet
         * appelle ceci pendant son écran de chargement, où cette attente ne
         * se voit pas.
         */
        fun prechauffer(hote: FrameLayout, c: ContenuCarte) {
            if (chaud) return
            chaud = true
            val ctx = hote.context
            val d = ctx.resources.displayMetrics.density
            val carte = CarteCarnet.complete(ctx, c)
            val ornee = carte as? CarteOrnee ?: return
            ornee.ajusteALaHauteur = true
            ornee.avecAmbiance()
            carte.alpha = 0.01f
            carte.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            carte.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            carte.visibility = View.INVISIBLE
            val marge = (22 * d).toInt()
            hote.addView(carte, 0, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(marge, (24 * d).toInt(), marge, (84 * d).toInt()) })
            ornee.attendreSaMatiere {
                carte.visibility = View.VISIBLE
                uneImagePlusTard { hote.removeView(carte) }
            }
        }
    }
}

/** [action] une fois l'image suivante dessinée. */
private fun uneImagePlusTard(action: () -> Unit) {
    val ch = android.view.Choreographer.getInstance()
    ch.postFrameCallback { ch.postFrameCallback { action() } }
}
