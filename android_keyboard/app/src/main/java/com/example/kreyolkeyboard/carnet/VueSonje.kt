package com.example.kreyolkeyboard.carnet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * L'écran d'une session de révision.
 *
 * Posé sur la vue racine du carnet, comme le voile de `montrerCarte`, et non
 * dans un second `DialogFragment` : la révision est un mode du carnet, pas une
 * destination de plus. La barre d'onglets en porte quatre et `REAL_COUNT`
 * pilote le modulo du pager cyclique ; le hub, lui, défile déjà depuis sa
 * septième carte de jeu.
 *
 * ## La carte a deux faces, et l'écran est bâti autour d'elles
 *
 * L'écran était un flux : une consigne, un énoncé, un pavé, un bouton, le tout
 * dans un `ScrollView`, et la carte n'arrivait qu'à la révélation. Le
 * retournement était donc un faux — la carte partait de `rotationY = -85°`
 * sans que rien n'occupe ces -85°.
 *
 * Il est maintenant vrai. La question est écrite sur [DosRevision], le
 * retournement se joue en deux temps comme celui de la pochette, et le recto
 * est la carte du carnet. D'où la disposition en trois bandes :
 *
 * - l'en-tête, fixe ;
 * - **la scène**, qui prend tout ce qui reste et ne contient que le carton ;
 * - **le bas**, qui porte ce qui n'est pas la carte : les boutons.
 *
 * C'est la scène qui décide de la taille du carton, via
 * [Carton.ajusteALaHauteur].
 *
 * ## Une flashcard, pour toutes les boîtes
 *
 * Le dos porte le mot seul ; on retourne la carte d'un appui ou d'un balayage
 * sur elle, ou par le bouton, et on se note « Je savais » ou « Pas su ». De la 22.0.0 à la
 * 23.0.0, les boîtes 2 et plus faisaient taper le mot sur un pavé, dans la
 * phrase du corpus trouée : c'est retiré, la révision reste une flashcard.
 *
 * La carte révélée est celle du carnet ([CarteCarnet.complete]), pas un recto
 * écrit pour l'occasion. C'est la même carte que le joueur a gagnée, avec sa
 * glose, sa phrase, sa famille et sa rareté : la révision montre la
 * collection, elle ne la double pas.
 */
class VueSonje(
    private val hote: ViewGroup,
    private val paquet: List<ContenuCarte>,
    private val monteesParLeClavier: List<String>,
    private val surNotation: (forme: String, verdict: Verdict) -> Unit,
    private val surFin: () -> Unit
) {

    private val ctx: Context = hote.context
    private val d = ctx.resources.displayMetrics.density
    private val session = SessionSonje(paquet)

    private val racine = FrameLayout(ctx).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#F5F5F5"))
        isClickable = true
    }
    private val colonne = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }

    /**
     * La scène : le carton, et rien d'autre.
     *
     * Elle ne bouge pas d'une question à l'autre, ce qui est ce qui permet
     * d'y armer [Inclinaison] une seule fois : le suivi s'applique à la scène,
     * le retournement à la carte qui est dedans, et les deux se composent sans
     * s'écrire l'un sur l'autre.
     */
    private val scene = FrameLayout(ctx)
    private val bas = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }
    private lateinit var tvProgres: TextView

    private var dos: DosRevision? = null

    private fun dp(v: Float) = (v * d).toInt()

    /**
     * Ouvre la session.
     *
     * File vide **et** rien monté par le clavier : il n'y a rien à dire, on ne
     * s'affiche pas. File vide mais des cartes montées par la frappe : on
     * s'ouvre directement sur le bilan, sinon le joueur ne saurait pas pourquoi
     * son paquet a fondu.
     */
    fun ouvrir() {
        if (session.fini && monteesParLeClavier.isEmpty()) {
            surFin()
            return
        }
        colonne.addView(enTete())
        scene.setPadding(dp(16f), dp(12f), dp(16f), dp(8f))
        colonne.addView(scene, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        bas.setPadding(dp(16f), dp(4f), dp(16f), dp(16f))
        colonne.addView(bas, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        racine.addView(colonne, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        hote.addView(racine)
        racine.alpha = 0f
        racine.animate().alpha(1f).setDuration(160).start()
        // Armé sur la scène et non sur la carte : la scène survit à toutes les
        // questions, donc le capteur n'est enregistré qu'une fois, et le
        // retournement garde `rotationY` de la carte pour lui seul.
        Inclinaison.suivre(scene)
        afficherQuestion()
    }

    private fun fermer() {
        racine.animate().alpha(0f).setDuration(160).withEndAction {
            hote.removeView(racine)
            surFin()
        }.start()
    }

    private fun enTete() = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Carnet.COULEUR)
        setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        addView(TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            text = "🔁  Sonjé"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        tvProgres = TextView(ctx).apply {
            textSize = 14f
            setTextColor(0xFFE8E0FF.toInt())
        }
        addView(tvProgres)
        addView(TextView(ctx).apply {
            text = "✕"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(18f), 0, dp(2f), 0)
            isClickable = true
            // Quitter en cours de session garde ce qui a été noté : une carte
            // répondue est notée à la réponse, pas au bilan.
            setOnClickListener { fermer() }
        })
    }

    // ------------------------------------------------------------------ états

    /**
     * La question, écrite sur le dos du carton : la consigne sur la plaque de
     * nom, le mot au-dessus d'elle.
     */
    private fun afficherQuestion() {
        val q = session.courante ?: return afficherBilan()
        tvProgres.text = "${session.rang} / ${session.total}"

        val carton = DosRevision(ctx)
        carton.posee(
            ligne("Vous souvenez-vous ?", taille = 13f, couleur = ENCRE, gras = true),
            Ornement.PLAQUE
        )
        // Le mot ne prend **pas** la couleur de son jeu : sur un dos, elle
        // trahirait la provenance de la carte, et le paquet mélange les sept.
        carton.posee(
            bloc(q.mot, 32f, ENCRE, gras = true).apply { maxLines = 2 },
            Ornement.ENONCE_DOS
        )

        poserCarton(carton)
        // Le dos entre sans animation : rien n'écrit dans sa rotation, il
        // peut prendre le doigt tout de suite. Les deux faces y répondent —
        // une question qui suivrait la main et une réponse qui n'y
        // répondrait plus seraient deux objets, pas un carton retourné.
        carton.sensibleAuDoigt = true
        dos = carton

        bas.removeAllViews()
        val retourner = { revelation(q) }
        bas.addView(bouton("Retourner la carte", Carnet.COULEUR) { retourner() })
        retournementAuPouce(carton, retourner)
    }

    /**
     * Le retournement au pouce, sur le carton lui-même.
     *
     * Le bouton reste — il nomme le geste, il est la cible d'un lecteur
     * d'écran, et c'est lui qu'on trouve sans rien savoir. Mais une carte
     * qu'on ne peut retourner qu'en visant un bouton posé sous elle n'est pas
     * tout à fait une carte : le pouce est déjà dessus, il en suit le relief
     * et en déplace la lumière, et le seul geste qu'il ne pouvait pas faire
     * était celui qu'on fait à une carte.
     *
     * Quatre choses le tiennent à l'écart de ce que le carton fait déjà :
     *
     * - **L'écouteur rend toujours la main** (`false`). Un `OnTouchListener`
     *   qui consommerait le geste couperait [Carton.onTouchEvent], c'est-à-dire
     *   l'appui, la tranche et le reflet sous le doigt — on retournerait la
     *   carte au prix de tout ce qui la rend touchable.
     * - **Un appui bref, ou un balayage franc et horizontal.** Le pouce qui
     *   se promène sur le carton en déplace la lumière, et un pouce posé la
     *   retient : ni l'un ni l'autre ne sont une demande. Un appui compte donc
     *   s'il reste sous le `scaledTouchSlop` et le délai d'appui long ; un
     *   balayage compte s'il parcourt au moins [PART_BALAYAGE] de la largeur de
     *   la carte, deux fois plus en largeur qu'en hauteur. Entre les deux, le
     *   geste appartient à la surface : sans ce partage, admirer le dos
     *   retournerait la carte.
     * - **Le glissement appartient à la carte.** Le pager des onglets de
     *   l'application intercepte tout glissement horizontal ; le carton le lui
     *   interdit dès que le doigt se pose, sans quoi le balayage n'arrivait
     *   jamais qu'en `ACTION_CANCEL`.
     * - **Un seul retournement.** `dos` est lâché dès l'entrée de [revelation],
     *   pour la raison qui y est écrite ; le comparer ici suffit à ce qu'un
     *   deuxième appui pendant l'animation ne relance rien.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun retournementAuPouce(carton: DosRevision, action: () -> Unit) {
        val ecart = ViewConfiguration.get(ctx).scaledTouchSlop
        val delaiLong = ViewConfiguration.getLongPressTimeout()
        var departX = 0f
        var departY = 0f
        var promene = false
        carton.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    departX = e.rawX
                    departY = e.rawY
                    promene = false
                    // Le pager des onglets intercepte sinon tout glissement
                    // horizontal pour changer d'onglet, et le carton ne reçoit
                    // qu'un ACTION_CANCEL.
                    carton.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE ->
                    if (hypot(e.rawX - departX, e.rawY - departY) > ecart) promene = true
                MotionEvent.ACTION_UP -> {
                    // En coordonnées d'écran : le carton s'incline sous le doigt,
                    // et ses coordonnées à lui passent par cette perspective.
                    val dx = abs(e.rawX - departX)
                    val dy = abs(e.rawY - departY)
                    val appui = !promene && e.eventTime - e.downTime < delaiLong
                    val balayage = dx > carton.width * PART_BALAYAGE && dx > 2f * dy
                    if ((appui || balayage) && dos === carton) action()
                }
            }
            false
        }
    }

    /** La carte révélée : le joueur se note lui-même après l'avoir vue. */
    private fun revelation(q: QuestionRevision) {
        val poser = {
            bas.removeAllViews()
            bas.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = pleineLargeur()
                    addView(bouton("Pas su", 0xFFB0575E.toInt()) { noter(q, Verdict.FAUX) }.apply {
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = 0; weight = 1f; rightMargin = dp(6f)
                        }
                    })
                    addView(bouton("Je savais", 0xFF2E7D32.toInt()) { noter(q, Verdict.EXACT) }.apply {
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = 0; weight = 1f; leftMargin = dp(6f)
                        }
                    })
                })

            val recto = CarteCarnet.complete(ctx, q.contenu)
            poserCarton(recto)
            val armer = { (recto as? Carton)?.sensibleAuDoigt = true }
            if (recto is Carton && !Pochette.animationsReduites(ctx)) {
                recto.rotationY = -90f
                // Le second temps n'est lancé qu'une fois la scène remesurée.
                recto.post {
                    recto.animate().rotationY(0f).setDuration(220)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction { armer() }
                        .start()
                }
            } else armer()
        }

        // Lâché tout de suite : un second appui pendant le retournement
        // relancerait sinon un deuxième geste sur la même vue.
        val sortant = dos
        dos = null
        if (sortant == null || Pochette.animationsReduites(ctx)) {
            poser()
            return
        }
        // Le doigt vient peut-être de quitter le dos : son rebond dure trois
        // cents millisecondes et écrit lui aussi dans la rotation. Il rend la
        // main avant que le retournement ne commence.
        sortant.reposer()
        // Premier temps : le dos se met de profil. C'est là, et pas ailleurs,
        // que la carte change de taille — de profil, elle est invisible.
        sortant.animate().rotationY(90f).setDuration(170)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { poser() }
            .start()
    }

    /** Pose un carton au centre de la scène, ajusté à ce qui reste de hauteur. */
    private fun poserCarton(carton: View) {
        scene.removeAllViews()
        (carton as? Carton)?.ajusteALaHauteur = true
        carton.cameraDistance = 12000f * d
        scene.addView(carton, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
    }

    /** Enregistre la réponse, et passe à la carte suivante. */
    private fun noter(q: QuestionRevision, verdict: Verdict) {
        if (session.repondre(verdict == Verdict.EXACT)) {
            surNotation(q.mot, verdict)
        }
        afficherQuestion()
    }

    /**
     * Le bilan, et la seule mention du clavier.
     *
     * Les cartes montées par la frappe ne sont pas passées par une question :
     * les annoncer ici est la seule façon de dire au joueur pourquoi son paquet
     * était plus court que son arriéré.
     */
    private fun afficherBilan() {
        tvProgres.text = ""
        dos = null
        scene.removeAllViews()
        bas.removeAllViews()

        val feuille = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply {
                layoutParams = pleineLargeur()
                text = "Session terminée"
                textSize = 22f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                setTextColor(Carnet.COULEUR)
            })
            addView(TextView(ctx).apply {
                layoutParams = pleineLargeur().apply { topMargin = dp(10f) }
                text = "${session.reussies.size} sur ${session.total} retrouvés."
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#424242"))
            })
            if (session.ratees.isNotEmpty()) {
                addView(TextView(ctx).apply {
                    layoutParams = pleineLargeur().apply { topMargin = dp(14f) }
                    text = "À revoir demain : " + session.ratees.joinToString(", ")
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#757575"))
                    setLineSpacing(0f, 1.2f)
                })
            }
            if (monteesParLeClavier.isNotEmpty()) {
                addView(TextView(ctx).apply {
                    layoutParams = pleineLargeur().apply { topMargin = dp(18f) }
                    text = "Vous avez écrit vous-même " +
                        monteesParLeClavier.joinToString(", ") +
                        " depuis la dernière fois : ces cartes n'avaient rien à prouver."
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF2E7D32.toInt())
                    setLineSpacing(0f, 1.2f)
                })
            }
        }
        scene.addView(feuille, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        bas.addView(bouton("Fermer", Carnet.COULEUR) { fermer() })
    }

    // ------------------------------------------------------------- fabriques

    private fun pleineLargeur() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    /**
     * Une ligne posée dans un emplacement du carton.
     *
     * La taille est exprimée **en unités de carte** et voyage dans le `tag`,
     * exactement comme celles de [CarteCarnet] : c'est [Carton] qui la
     * convertit en pixels une fois qu'il connaît sa largeur réelle. Sans ça,
     * la question serait illisible sur le petit carton d'une question tapée.
     */
    private fun ligne(
        contenu: String,
        taille: Float,
        couleur: Int,
        gras: Boolean
    ): TextView = TextView(ctx).apply {
        text = contenu
        setTextColor(couleur)
        if (gras) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        tag = floatArrayOf(taille, 0f)
    }

    /** Un texte qui a le droit de revenir à la ligne, centré dans sa case. */
    private fun bloc(
        contenu: String,
        taille: Float,
        couleur: Int,
        gras: Boolean
    ): TextView = TextView(ctx).apply {
        text = contenu
        setTextColor(couleur)
        if (gras) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.14f)
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        tag = floatArrayOf(taille, 0f)
    }

    private fun bouton(libelle: String, couleur: Int, action: () -> Unit) = TextView(ctx).apply {
        layoutParams = pleineLargeur()
        text = libelle
        textSize = 15f
        gravity = Gravity.CENTER
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
        background = GradientDrawable().apply {
            cornerRadius = 24f * d
            setColor(couleur)
        }
        isClickable = true
        setOnClickListener { if (isEnabled) action() }
    }

    private companion object {
        const val ENCRE = 0xFF1B1610.toInt()

        /** La part de la largeur de la carte qu'un balayage doit parcourir. */
        const val PART_BALAYAGE = 0.3f
    }
}
