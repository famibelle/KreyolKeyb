package com.example.kreyolkeyboard.carnet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * La pochette de fin de partie : les cartes gagnées se retournent une à une.
 *
 * C'est le geste qui manquait entre la partie et la collection. Le carnet
 * conserve, mais il ne raconte rien : on y va pour consulter. La pochette,
 * elle, **paie la partie au moment où elle se termine** — on ne consulte pas
 * ses cartes, on les découvre.
 *
 * Elle sert les cinq jeux : une manche de Fraz a twou, une grille de Mokwaré
 * et une partie de Mo an Karénaj se
 * terminent toutes sur le même geste, aux couleurs du jeu qu'on vient de
 * quitter.
 *
 * Quatre choix qui portent le reste :
 *
 * - **Toutes les cartes de la partie, pas seulement les neuves.** Un paquet
 *   dont on connaît déjà la moitié reste un paquet ; n'ouvrir que les
 *   nouveautés ferait des parties sans récompense dès que le joueur commence à
 *   connaître le vocabulaire, c'est-à-dire exactement quand il progresse. Les
 *   neuves passent devant et portent un bandeau.
 * - **Une carte se retourne toute seule, un glissement passe à la suivante.**
 *   Deux appuis par carte auraient donné le rythme authentique d'un paquet, et
 *   une corvée sur une grille de neuf mots. Le retournement est la récompense,
 *   il n'a pas à se mériter une deuxième fois.
 * - **Le paquet se parcourt dans les deux sens.** On glisse vers la gauche
 *   pour avancer, vers la droite pour revenir — bilan compris, d'où l'on
 *   ressort vers la dernière carte. Une carte déjà retournée se retrouve
 *   telle qu'on l'a laissée : la cérémonie appartient à la découverte, pas à
 *   la consultation.
 * - **« Passer » est toujours offert**, dès la première carte. La pochette est
 *   un cadeau, pas un péage entre le joueur et la grille suivante.
 * - **Elle n'arrive jamais après « Solution ».** Une grille révélée ne verse
 *   rien au carnet, donc elle n'ouvre pas de pochette : la récompense suit ce
 *   qui a été gagné, pas ce qui a été montré. Même règle partout : un mot
 *   passé dans Mots Mélangés, une réponse fausse dans Fraz a twou, un Mo an Karénaj perdu
 *   ne donnent pas de carte.
 */
object Booster {

    /**
     * Ouvre la pochette au-dessus de [hote].
     *
     * [jeu] est celui d'où sortent ces cartes : il donne sa couleur au dos et
     * au bouton du carnet, pour que la pochette appartienne visiblement à la
     * partie qu'on vient de finir.
     *
     * [nouvelles] porte les formes que le carnet n'avait jamais vues ; elles
     * sont montrées d'abord et signalées. [surFin] est appelé à la fermeture,
     * quelle qu'en soit la manière — dernier appui, « Passer », ou voile
     * touché — pour que l'appelant puisse rendre la main sans compter les
     * chemins.
     */
    fun ouvrir(
        hote: ViewGroup,
        jeu: JeuCarte,
        contenus: List<ContenuCarte>,
        nouvelles: Set<String>,
        animations: Boolean,
        surCarnet: () -> Unit,
        surFin: () -> Unit
    ): View? {
        if (contenus.isEmpty()) {
            surFin()
            return null
        }
        val ctx = hote.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        // Les neuves d'abord : c'est ce qu'on veut voir, et cela évite que la
        // seule carte inédite d'une grille arrive après six déjà connues.
        val ordre = contenus.sortedByDescending { it.carte.forme in nouvelles }

        val voile = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xE6000000.toInt())
            isClickable = true
        }

        val colonne = LinearLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20f), dp(18f), dp(20f), dp(16f))
        }

        val titre = TextView(ctx).apply {
            text = "🎁 Votre pochette — ${jeu.nom}"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        colonne.addView(titre)

        val progres = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f); bottomMargin = dp(10f) }
            textSize = 13f
            setTextColor(0xFFB0BEC5.toInt())
        }
        colonne.addView(progres)

        val bandeau = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
            text = "✨  NOUVELLE CARTE"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            background = GradientDrawable().apply {
                cornerRadius = 20f * d
                setColor(0xFFFB8C00.toInt())
            }
            visibility = View.INVISIBLE
        }
        colonne.addView(bandeau)

        // La scène : les cartes y défilent, elle ne bouge pas. C'est elle qui
        // lit le geste, et pas la carte — voir [ScenePochette].
        val scene = ScenePochette(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        colonne.addView(scene)

        val boutons = LinearLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12f) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val passer = TextView(ctx).apply {
            text = "Passer"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFFB0BEC5.toInt())
            setPadding(dp(22f), dp(11f), dp(22f), dp(11f))
            isClickable = true
        }
        boutons.addView(passer)

        // La sortie vers le carnet, offerte seulement au bilan : proposée dès
        // la première carte, elle inviterait à quitter la pochette avant de
        // l'avoir ouverte.
        val versCarnet = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(10f) }
            text = "📔 Sanblé"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(20f), dp(11f), dp(20f), dp(11f))
            background = GradientDrawable().apply {
                cornerRadius = 24f * d
                setColor(jeu.couleur)
            }
            isClickable = true
            visibility = View.GONE
        }
        boutons.addView(versCarnet)
        colonne.addView(boutons)

        voile.addView(colonne)
        hote.addView(voile)

        // Le paquet a une page de plus qu'il n'a de cartes : le bilan en est
        // la dernière. Le ranger dans la même suite que les cartes est ce qui
        // permet d'en revenir d'un geste — un bilan posé à part serait un
        // cul-de-sac, et c'est la moitié du défaut qu'on corrige ici.
        val pages = ordre.size + 1

        /** Les cartes déjà retournées : on ne recommence pas une cérémonie. */
        val vues = HashSet<Int>()

        var rang = -1
        var ferme = false

        /** La page à l'écran. Elle seule suit le doigt ; la scène ne bouge pas. */
        var plateau: FrameLayout? = null

        fun largeur(): Float = (
            scene.width.takeIf { it > 0 } ?: ctx.resources.displayMetrics.widthPixels
            ).toFloat()

        fun fermer() {
            if (ferme) return
            ferme = true
            voile.animate().alpha(0f).setDuration(180)
                .withEndAction {
                    (voile.parent as? ViewGroup)?.removeView(voile)
                    surFin()
                }.start()
        }

        /**
         * Le titre, le compteur et les boutons, remis d'accord avec [index].
         *
         * Tout ce qui entoure la scène doit pouvoir revenir en arrière comme
         * elle : un bilan qui laisserait « Fermer » écrit sur le bouton après
         * qu'on est retourné voir ses cartes mentirait sur ce que le bouton
         * fait.
         */
        fun chrome(index: Int) {
            bandeau.visibility = View.INVISIBLE
            val neuves = contenus.count { it.carte.forme in nouvelles }
            if (index >= ordre.size) {
                progres.text = if (ordre.size > 1)
                    "‹ glissez à droite pour revoir vos cartes" else ""
                titre.text = "🎁 " + contenus.size + " carte" +
                    (if (contenus.size > 1) "s" else "") +
                    (if (neuves > 0) " · $neuves nouvelle" +
                        (if (neuves > 1) "s" else "") else "")
            } else {
                progres.text = "${index + 1} / ${ordre.size}" +
                    (if (ordre.size > 1) "   ·   glissez pour parcourir ›" else "")
                titre.text = "🎁 Votre pochette — ${jeu.nom}"
            }
            // « Fermer » dès que tout a été vu, où qu'on soit dans le paquet :
            // « Passer » ne veut plus rien dire quand il ne reste rien à
            // passer. Même bascule pour la sortie vers le carnet, qui n'était
            // cachée que pour ne pas inviter à partir avant d'avoir ouvert.
            val tout = vues.size >= ordre.size
            passer.text = if (tout) "Fermer" else "Passer"
            passer.setTextColor(if (tout) Color.WHITE else 0xFFB0BEC5.toInt())
            versCarnet.visibility = if (tout) View.VISIBLE else View.GONE
        }

        /** Le bilan, une fois la dernière carte vue. */
        fun bilan(page: FrameLayout) {
            val neuves = contenus.count { it.carte.forme in nouvelles }
            page.addView(TextView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                text = if (neuves > 0)
                    "Elles sont rangées dans votre carnet."
                else
                    "Vous les aviez déjà toutes : elles restent dans votre carnet."
                textSize = 15f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
                setTextColor(0xFFECEFF1.toInt())
            })
        }

        /**
         * Remplit [page] avec la page [index].
         *
         * [decouverte] dit si c'est la première fois qu'on la voit. Elle seule
         * a droit au dos et au retournement : revenir sur une carte déjà
         * ouverte la rend telle qu'on l'a laissée, parce que rejouer la
         * cérémonie ferait d'un aller-retour une attente.
         */
        fun remplir(page: FrameLayout, index: Int, decouverte: Boolean) {
            if (index >= ordre.size) {
                bilan(page)
                return
            }
            val c = ordre[index]
            val rarete = c.rarete
            val neuve = c.carte.forme in nouvelles

            val face = ScrollView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                isVerticalScrollBarEnabled = false
                addView(CarteCarnet.complete(ctx, c))
            }

            if (!animations || !decouverte) {
                // Carte déjà retournée, ou animations coupées : elle arrive
                // face visible, mais elle arrive quand même sur son halo — la
                // lueur devient un état plutôt qu'un mouvement.
                if (rarete.distinguee) page.addView(HaloRarete(ctx, rarete).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                    alpha = 0.4f
                })
                page.addView(face)
                if (neuve) bandeau.visibility = View.VISIBLE
                // `suivre` se tait tout seul quand les animations sont coupées.
                Inclinaison.suivre(face)
                if (animations) feter(page, rarete, c.carte.forme)
                return
            }

            val dos = DosDeCarte(ctx, jeu, rarete).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    (ctx.resources.displayMetrics.heightPixels * 0.44f).toInt(),
                    Gravity.CENTER
                )
            }

            // La lueur qui précède une carte rare.
            //
            // C'est le seul endroit du carnet où la rareté se sait **avant**
            // d'être vue, et c'est voulu : l'attente est ce qui transforme un
            // retournement en événement. Une commune n'en a pas, et ne perd
            // rien — c'est de ne pas l'avoir qui dit ce qu'elle est.
            val halo = if (rarete.distinguee) HaloRarete(ctx, rarete).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
                alpha = 0f
            } else null

            halo?.let { page.addView(it) }
            page.addView(dos)

            // La cérémonie appartient à cette page-là. Si le joueur glisse
            // pendant qu'elle se joue, la page quitte la scène et tout ce qui
            // suit doit s'arrêter : `page.parent` est ce qui le dit.
            fun abandonnee() = ferme || page.parent == null

            // Le dos arrive, puis se retourne de lui-même. Le retournement est
            // la récompense : la faire attendre un appui de plus ne la rend
            // pas plus douce, elle rend la pochette longue. Une rare le fait
            // attendre un peu, une très rare un peu plus : la durée est la
            // seule chose qu'une animation sache dire, et elle le dit sans mot.
            dos.alpha = 0f
            dos.scaleX = 0.9f
            dos.scaleY = 0.9f
            dos.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()

            val attente = when (rarete) {
                Rarete.TRES_RARE -> 900L
                Rarete.RARE -> 620L
                else -> 380L
            }
            halo?.animate()?.alpha(1f)?.setDuration(attente)?.start()

            dos.postDelayed({
                if (abandonnee()) return@postDelayed
                dos.cameraDistance = 12000f * d
                face.cameraDistance = 12000f * d
                dos.animate().rotationY(90f).setDuration(170)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        if (abandonnee()) return@withEndAction
                        page.removeView(dos)
                        // Le halo reste derrière la carte révélée et s'efface
                        // doucement : la lueur ne s'éteint pas au moment où
                        // elle a enfin quelque chose à éclairer.
                        halo?.animate()?.alpha(0.35f)?.setDuration(900)?.start()
                        page.addView(face)
                        face.rotationY = -90f
                        face.animate().rotationY(0f)
                            .setDuration(if (rarete.distinguee) 300L else 210L)
                            .setInterpolator(
                                if (rarete.distinguee) OvershootInterpolator(1.2f)
                                else DecelerateInterpolator()
                            )
                            .withEndAction {
                                if (abandonnee()) return@withEndAction
                                // La carte a fini de se poser : elle suit
                                // maintenant l'inclinaison de l'appareil.
                                Inclinaison.suivre(face)
                                if (rarete == Rarete.TRES_RARE) eclater(page, rarete)
                                feter(page, rarete, c.carte.forme)
                                if (neuve) {
                                    bandeau.visibility = View.VISIBLE
                                    bandeau.alpha = 0f
                                    bandeau.scaleX = 0.8f
                                    bandeau.scaleY = 0.8f
                                    bandeau.animate().alpha(1f).scaleX(1f).scaleY(1f)
                                        .setDuration(220).start()
                                }
                            }
                            .start()
                    }.start()
            }, attente)
        }

        /**
         * Amène la page [cible] à l'écran.
         *
         * [sens] dit d'où elle vient : `+1` de la droite — on avance —, `-1`
         * de la gauche, `0` pour la toute première, qui ne vient de nulle part.
         */
        fun poser(cible: Int, sens: Int) {
            if (ferme || cible < 0 || cible >= pages) return
            val ancien = plateau
            rang = cible
            val decouverte = cible !in vues
            if (cible < ordre.size) vues.add(cible)

            val neuf = FrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            plateau = neuf
            scene.addView(neuf)
            chrome(cible)
            remplir(neuf, cible, decouverte)

            if (sens == 0 || !animations) {
                ancien?.let { scene.removeView(it) }
                return
            }
            val large = largeur()
            neuf.translationX = sens * large
            neuf.alpha = 0f
            neuf.animate().translationX(0f).alpha(1f).setDuration(230)
                .setInterpolator(DecelerateInterpolator()).start()
            // L'ancienne sort moins loin qu'elle n'est entrée : c'est ce
            // décalage qui donne le sens du paquet, la nouvelle recouvrant la
            // précédente plutôt que les deux se croisant à égalité.
            ancien?.let { vieux ->
                vieux.animate().translationX(-sens * large * 0.55f).alpha(0f)
                    .setDuration(190).setInterpolator(AccelerateInterpolator())
                    .withEndAction { scene.removeView(vieux) }.start()
            }
        }

        /** Le plateau ramené à sa place, quand le glissement n'a pas suffi. */
        fun rebondir() {
            plateau?.animate()?.translationX(0f)?.rotation(0f)?.alpha(1f)
                ?.setDuration(180)?.setInterpolator(DecelerateInterpolator())?.start()
        }

        scene.actif = { !ferme }

        // Le doigt emporte la page, un peu moins vite que lui — elle pivote et
        // s'efface à mesure, comme une carte qu'on écarte de la main.
        scene.surGlisse = { dx ->
            plateau?.let { p ->
                val bout = (rang == 0 && dx > 0) || (rang == pages - 1 && dx < 0)
                // Aux deux bouts du paquet, le glissement résiste : la carte
                // suit encore le doigt, mais du tiers, et dit ainsi qu'il n'y
                // a rien derrière sans avoir à l'écrire.
                val course = if (bout) dx * 0.3f else dx * 0.9f
                p.translationX = course
                p.rotation = course / 70f
                p.alpha = 1f - (abs(course) / (largeur() * 1.7f)).coerceAtMost(0.5f)
            }
        }

        scene.surLache = { dx, vitesse ->
            val emporte = abs(dx) > largeur() * 0.22f || abs(vitesse) > 1000f
            val vers = if (dx < 0) 1 else -1
            val cible = rang + vers
            if (emporte && cible in 0 until pages) poser(cible, vers) else rebondir()
        }

        // L'appui simple reste ce qu'il était : il passe à la suivante, et
        // ferme une fois le bilan atteint. Il ne marchait plus depuis que la
        // carte vit dans un `ScrollView` — voir [ScenePochette].
        scene.surTape = { if (rang >= ordre.size) fermer() else poser(rang + 1, 1) }
        voile.setOnClickListener { if (rang >= ordre.size) fermer() else poser(rang + 1, 1) }
        passer.setOnClickListener { fermer() }
        versCarnet.setOnClickListener {
            fermer()
            surCarnet()
        }

        voile.alpha = 0f
        voile.animate().alpha(1f).setDuration(200).start()
        poser(0, 0)
        return voile
    }

    /**
     * Les feux d'artifice qui saluent une carte distinguée.
     *
     * Ils partent **chaque fois que la carte se pose** : au retournement qui
     * la découvre comme au glissement qui y revient. C'est voulu — la rareté
     * n'est pas une nouvelle qu'on annonce une fois puis qu'on oublie, c'est
     * une propriété que la carte garde, et parcourir sa pochette doit valoir
     * le geste. Une commune n'en a pas : c'est de ne pas en avoir qui dit ce
     * qu'elle est.
     *
     * [graine] est la forme du mot. Les gerbes sont tirées au hasard, mais
     * d'un hasard que la carte fixe : deux passages sur la même carte donnent
     * le même bouquet, deux cartes voisines n'en donnent jamais deux pareils.
     * Le hasard sert la variété, jamais l'instabilité.
     */
    private fun feter(hote: FrameLayout, rarete: Rarete, graine: String) {
        if (!rarete.distinguee) return
        val feu = FeuxArtifice(hote.context, rarete, graine.hashCode().toLong()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        hote.addView(feu)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (rarete == Rarete.TRES_RARE) 1250L else 980L
            addUpdateListener { feu.avancement = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    (feu.parent as? ViewGroup)?.removeView(feu)
                }
            })
            start()
        }
    }

    /**
     * L'éclat qui accompagne l'arrivée d'une très rare.
     *
     * Il ne dure pas : une demi-seconde, et la vue se retire d'elle-même. Un
     * effet permanent aurait fini par gêner la lecture de la carte, qui reste
     * ce qu'on est venu voir.
     */
    private fun eclater(scene: FrameLayout, rarete: Rarete) {
        val eclat = EclatCarte(scene.context, rarete).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        scene.addView(eclat)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520L
            addUpdateListener { eclat.avancement = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    (eclat.parent as? ViewGroup)?.removeView(eclat)
                }
            })
            start()
        }
    }
}

/**
 * La scène de la pochette : elle porte les cartes, et c'est elle qui lit le
 * geste.
 *
 * ## Le défaut qu'elle répare
 *
 * Une carte ouverte vit dans un [ScrollView] — sa glose peut dépasser la
 * hauteur disponible. Or un `ScrollView` **consomme** l'appui qu'il reçoit,
 * y compris quand il n'a rien à faire défiler : son `onTouchEvent` retourne
 * `true` dès `ACTION_DOWN` et n'appelle jamais `performClick`. Tant que le
 * passage d'une carte à l'autre a tenu à un `setOnClickListener` posé sur le
 * voile, l'appui sur la carte — c'est-à-dire sur presque tout l'écran —
 * n'arrivait donc jamais jusqu'à lui : la pochette restait sur sa première
 * carte, et les suivantes n'existaient que dans le compteur. Ce n'était pas
 * un manque de découvrabilité mais une impasse, la deuxième carte n'étant
 * atteignable qu'en visant les quelques millimètres de marge autour d'elle.
 *
 * ## Les deux gestes
 *
 * La scène voit tout passer avant ses enfants, et en tire :
 *
 * - **Le glissement horizontal**, qu'elle intercepte dès qu'il se déclare —
 *   la carte reçoit alors un `ACTION_CANCEL` et cesse de défiler. Le seuil
 *   compare `|dx|` à `|dy|` : un glissement vertical reste au `ScrollView`,
 *   qui garde donc sa glose lisible. Et dès que celui-ci se met à défiler, il
 *   demande lui-même qu'on ne l'interrompe plus : le défilement gagne, ce qui
 *   est la bonne priorité.
 * - **L'appui simple**, qu'elle reconnaît **sans jamais l'intercepter** :
 *   `onInterceptTouchEvent` est appelé pour chaque événement du geste tant
 *   qu'elle laisse faire, relâchement compris, et elle n'agit que si le doigt
 *   n'a ni bougé ni traîné. L'appui reste donc entièrement disponible pour
 *   l'enfant, qui l'a peut-être consommé pour de bonnes raisons.
 *
 * Les deux chemins ne se marchent pas dessus : si un enfant prend le geste,
 * seul `onInterceptTouchEvent` verra le relâchement ; s'il ne le prend pas,
 * la scène l'a consommé dès l'appui et c'est [onTouchEvent] qui le voit. Un
 * appui ne peut donc pas compter deux fois.
 *
 * Les coordonnées sont lues en `raw` : la page glisse sous le doigt pendant
 * le geste, et des coordonnées locales suivraient ce déplacement au lieu de
 * mesurer celui de la main.
 */
class ScenePochette(context: Context) : FrameLayout(context) {

    /** La pochette accepte-t-elle encore les gestes ? (Fermée, elle n'écoute plus.) */
    var actif: () -> Boolean = { true }

    /** Appui simple, sans déplacement. */
    var surTape: () -> Unit = {}

    /** Le doigt glisse : l'écart horizontal depuis l'appui, en pixels. */
    var surGlisse: (Float) -> Unit = {}

    /** Le doigt se lève : l'écart final, et la vitesse en pixels par seconde. */
    var surLache: (Float, Float) -> Unit = { _, _ -> }

    private val ecart = ViewConfiguration.get(context).scaledTouchSlop
    private val vitesseMax =
        ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()

    private var departX = 0f
    private var departY = 0f
    private var departT = 0L
    private var glisse = false
    private var vitesses: VelocityTracker? = null

    private fun debut(ev: MotionEvent) {
        departX = ev.rawX
        departY = ev.rawY
        departT = System.currentTimeMillis()
        glisse = false
        vitesses?.recycle()
        vitesses = VelocityTracker.obtain().also { it.addMovement(ev) }
    }

    /**
     * Le geste s'est-il déclaré horizontal ?
     *
     * Le facteur sur `dy` fait pencher les diagonales du côté du défilement :
     * une carte qu'on voulait lire et qui s'en va vaut bien pire qu'un
     * glissement qu'il faut refaire plus franchement.
     */
    private fun horizontal(ev: MotionEvent): Boolean {
        val dx = ev.rawX - departX
        val dy = ev.rawY - departY
        return abs(dx) > ecart && abs(dx) > abs(dy) * 1.2f
    }

    private fun tape(ev: MotionEvent): Boolean =
        !glisse &&
            System.currentTimeMillis() - departT < DUREE_APPUI &&
            abs(ev.rawX - departX) <= ecart &&
            abs(ev.rawY - departY) <= ecart

    private fun relacher() {
        glisse = false
        vitesses?.recycle()
        vitesses = null
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!actif()) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> debut(ev)
            MotionEvent.ACTION_MOVE -> {
                vitesses?.addMovement(ev)
                if (!glisse && horizontal(ev)) {
                    glisse = true
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val appui = tape(ev)
                relacher()
                if (appui) performClick()
            }
            MotionEvent.ACTION_CANCEL -> relacher()
        }
        return false
    }

    /**
     * L'appui, quelle que soit sa provenance.
     *
     * Passer par là plutôt que d'appeler [surTape] directement donne le geste
     * aux services d'accessibilité, qui déclenchent un clic sans jamais
     * produire de `MotionEvent` : sans cela, la pochette n'aurait pas eu de
     * carte suivante sous TalkBack.
     */
    override fun performClick(): Boolean {
        super.performClick()
        surTape()
        return true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!actif()) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> debut(ev)
            MotionEvent.ACTION_MOVE -> {
                vitesses?.addMovement(ev)
                if (!glisse && horizontal(ev)) glisse = true
                if (glisse) surGlisse(ev.rawX - departX)
            }
            MotionEvent.ACTION_UP -> {
                val emporte = glisse
                val vitesse = vitesses?.let {
                    it.computeCurrentVelocity(1000, vitesseMax)
                    it.xVelocity
                } ?: 0f
                val appui = tape(ev)
                val dx = ev.rawX - departX
                relacher()
                if (emporte) surLache(dx, vitesse) else if (appui) performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                val emporte = glisse
                val dx = ev.rawX - departX
                relacher()
                if (emporte) surLache(dx, 0f)
            }
        }
        return true
    }

    private companion object {
        /** Au-delà, le doigt s'est posé — il n'a pas frappé. */
        const val DUREE_APPUI = 400L
    }
}

/**
 * Le dos d'une carte.
 *
 * Volontairement identique pour toutes les cartes d'une même pochette : un dos
 * qui trahirait la carte supprimerait le seul instant que la pochette fabrique.
 * Ce qu'il dit, c'est **le jeu** — sa couleur et son emoji — et non le mot
 * caché dessous : on sait d'où vient le paquet, jamais ce qu'il contient.
 *
 * Le motif ne change donc pas avec [rarete] : seul le liseré s'allume. Le mot
 * reste secret, mais le paquet a le droit de faire savoir qu'il tient quelque
 * chose — c'est ce que fait n'importe quel joueur qui hésite avant de
 * retourner une carte, et ce que le halo derrière le dos raconte déjà.
 */
class DosDeCarte(
    context: Context,
    private val jeu: JeuCarte,
    private val rarete: Rarete = Rarete.COMMUN
) : View(context) {

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinceauTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private var fond: LinearGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        fond = LinearGradient(
            0f, 0f, w * 0.4f, h.toFloat(),
            eclaircir(jeu.couleur, 0.22f), assombrir(jeu.couleur, 0.45f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val d = resources.displayMetrics.density
        val r = 14f * d

        pinceau.shader = fond
        pinceau.alpha = 255
        canvas.drawRoundRect(0f, 0f, w, h, r, r, pinceau)
        pinceau.shader = null

        // Le liseré, comme sur la face — et allumé quand la carte est rare.
        pinceau.style = Paint.Style.STROKE
        pinceau.strokeWidth = 3f * d
        pinceau.color = if (rarete.distinguee)
            eclaircir(rarete.couleur, 0.55f) else Color.WHITE
        pinceau.alpha = if (rarete == Rarete.TRES_RARE) 190
        else if (rarete == Rarete.RARE) 130
        else 60
        canvas.drawRoundRect(
            2f * d, 2f * d, w - 2f * d, h - 2f * d, r, r, pinceau
        )

        // Des anneaux concentriques, très effacés : de la matière, pas un motif
        // qu'on cherche à lire.
        pinceau.strokeWidth = 1.5f * d
        pinceau.alpha = 26
        for (i in 1..5) {
            canvas.drawCircle(w / 2f, h / 2f, h * 0.10f * i, pinceau)
        }
        pinceau.style = Paint.Style.FILL

        // Quatre cases, comme une grille, et l'emoji du jeu au centre : le
        // motif est le même pour les sept, la marque change.
        val cote = h * 0.075f
        val ecart = cote * 2.6f
        val gx = w / 2f - cote - ecart / 2f
        val gy = h / 2f - cote - ecart / 2f
        pinceau.color = Color.WHITE
        for (l in 0..1) for (c in 0..1) {
            pinceau.alpha = if ((l + c) % 2 == 0) 200 else 96
            val x = gx + c * (cote + ecart)
            val y = gy + l * (cote + ecart)
            canvas.drawRoundRect(
                RectF(x, y, x + cote, y + cote), 4f * d, 4f * d, pinceau
            )
        }

        pinceauTexte.textSize = h * 0.10f
        val mesure = Paint.FontMetrics().also { pinceauTexte.getFontMetrics(it) }
        canvas.drawText(
            jeu.emoji, w / 2f, h / 2f - (mesure.ascent + mesure.descent) / 2f,
            pinceauTexte
        )
    }
}

/**
 * Le halo qui monte derrière une carte rare avant qu'elle ne se retourne.
 *
 * Un simple dégradé radial, aux couleurs du palier. Il tient sa place parce
 * qu'il ne dit **rien du mot** : le joueur apprend qu'il tient quelque chose,
 * pas quoi. C'est la promesse, et la carte est le paiement.
 */
class HaloRarete(context: Context, private val rarete: Rarete) : View(context) {

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lueur: RadialGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        val vif = eclaircir(rarete.couleur, 0.30f)
        val coeur = if (rarete == Rarete.TRES_RARE) 150 else 96
        lueur = RadialGradient(
            w * 0.5f, h * 0.5f, maxOf(w, h) * 0.55f,
            intArrayOf(
                Color.argb(coeur, Color.red(vif), Color.green(vif), Color.blue(vif)),
                Color.argb(coeur / 3, Color.red(vif), Color.green(vif), Color.blue(vif)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val shader = lueur ?: return
        pinceau.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pinceau)
    }
}

/**
 * L'éclat d'une très rare, au moment où elle se pose.
 *
 * Douze rais qui partent du centre et un anneau qui s'ouvre, tous deux
 * s'effaçant à mesure qu'ils s'écartent. Les angles sont réguliers et non
 * tirés au hasard : un éclat qui change de forme à chaque carte se remarque,
 * et ce n'est pas lui qu'on doit remarquer.
 *
 * [avancement] va de 0 à 1 et n'est piloté que par l'animateur de [Booster] :
 * la vue ne connaît pas le temps, elle ne connaît que sa position dedans.
 */
class EclatCarte(context: Context, private val rarete: Rarete) : View(context) {

    var avancement: Float = 0f
        set(valeur) {
            field = valeur
            invalidate()
        }

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || avancement <= 0f) return
        val d = resources.displayMetrics.density
        val cx = w / 2f
        val cy = h / 2f
        val portee = minOf(w, h) * 0.48f
        val reste = 1f - avancement

        // Les rais : ils partent d'un cercle qui grandit et gardent une
        // longueur fixe, ce qui donne l'impression qu'ils fuient le centre.
        pinceau.color = eclaircir(rarete.couleur, 0.62f)
        pinceau.strokeWidth = 2.5f * d
        pinceau.alpha = (reste * 220).toInt().coerceIn(0, 255)
        val rais = 12
        val depart = portee * (0.35f + 0.55f * avancement)
        val longueur = portee * 0.16f * reste
        for (i in 0 until rais) {
            val angle = (2.0 * Math.PI * i / rais).toFloat()
            val dx = kotlin.math.cos(angle)
            val dy = kotlin.math.sin(angle)
            canvas.drawLine(
                cx + dx * depart, cy + dy * depart,
                cx + dx * (depart + longueur), cy + dy * (depart + longueur),
                pinceau
            )
        }

        // L'anneau, plus discret, qui donne l'échelle de l'éclat.
        pinceau.color = Color.WHITE
        pinceau.strokeWidth = 1.5f * d
        pinceau.alpha = (reste * 120).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, portee * (0.30f + 0.62f * avancement), pinceau)
    }
}

/**
 * Les feux d'artifice d'une carte distinguée.
 *
 * ## Ce qu'ils font que [EclatCarte] ne fait pas
 *
 * L'éclat est un instant : douze rais réguliers qui partent du centre au
 * moment précis où la carte se pose, et qui disent « voilà ». Les feux, eux,
 * durent une seconde et se répondent — trois ou cinq gerbes décalées dans le
 * temps, ailleurs sur l'écran, qui montent, s'ouvrent et retombent. L'un
 * ponctue le retournement, les autres fêtent la carte. C'est pour cela qu'ils
 * cohabitent sur une très rare, la gerbe reprenant là où l'éclat s'éteint.
 *
 * ## Les quatre décisions
 *
 * - **Un hasard fixé par la carte.** [graine] vient de la forme du mot :
 *   revenir sur une carte redonne exactement son bouquet. Un tirage libre
 *   ferait un décor différent à chaque passage, et ce qui change sans raison
 *   se remarque au lieu de se regarder — c'est la même règle que pour
 *   [EclatCarte], appliquée dans l'autre sens : lui garde des angles réguliers
 *   parce qu'il est bref, eux peuvent se permettre du désordre parce qu'il ne
 *   varie pas d'une fois sur l'autre.
 * - **De la pesanteur.** Les étoiles retombent, en `t²`. Sans elle on n'a
 *   qu'une roue qui s'écarte, et l'œil ne lit pas des feux d'artifice — il lit
 *   un chargement.
 * - **Des traînées, pas des points.** Chaque étoile est un court segment entre
 *   sa position d'avant et celle de maintenant. Un point de deux pixels sur un
 *   écran dense ne se voit pas ; la traînée donne la vitesse, qui est la
 *   moitié de l'effet.
 * - **Elles s'éteignent avant la fin.** L'alpha tombe en `(1 - t)²` : les
 *   dernières étoiles disparaissent bien avant d'atteindre le bord, comme
 *   dans un vrai bouquet, et surtout la carte redevient lisible tout de suite.
 *   Ce qu'on est venu voir, c'est le mot.
 *
 * [avancement] va de 0 à 1 et n'est piloté que par l'animateur de [Booster] :
 * la vue ne connaît pas le temps, elle ne connaît que sa position dedans.
 */
class FeuxArtifice(
    context: Context,
    rarete: Rarete,
    graine: Long
) : View(context) {

    var avancement: Float = 0f
        set(valeur) {
            field = valeur
            invalidate()
        }

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * Une gerbe : un point de départ, un moment, une couleur.
     *
     * Tout est en fractions de la vue — le tirage a lieu à la construction,
     * bien avant qu'on connaisse sa taille, et une gerbe placée en pixels
     * sauterait à la première rotation d'écran.
     */
    private class Gerbe(
        val x: Float,
        val y: Float,
        val depart: Float,
        val duree: Float,
        val portee: Float,
        val couleur: Int,
        val etoiles: Int,
        val biais: Float
    )

    private val gerbes: List<Gerbe>

    init {
        val sort = Random(graine)
        val nombre = if (rarete == Rarete.TRES_RARE) 5 else 3
        // Le palier donne la teinte, l'or et le blanc font le reste : une
        // gerbe monochrome se lit comme un effet, trois teintes comme une fête.
        val teintes = intArrayOf(
            eclaircir(rarete.couleur, 0.45f),
            eclaircir(rarete.couleur, 0.15f),
            0xFFFFD54F.toInt(),
            Color.WHITE
        )
        gerbes = (0 until nombre).map { i ->
            Gerbe(
                // Réparties de part et d'autre de l'axe, sans jamais s'y
                // poser : une gerbe pile au centre serait cachée par la carte.
                x = 0.20f + 0.60f * sort.nextFloat(),
                y = 0.16f + 0.42f * sort.nextFloat(),
                // Les gerbes se suivent au lieu de partir ensemble : c'est le
                // décalage qui fait un bouquet plutôt qu'une explosion. La
                // dernière part avant la moitié, pour avoir le temps de
                // retomber avant que l'animateur ne s'arrête.
                depart = (i.toFloat() / nombre) * 0.44f + sort.nextFloat() * 0.06f,
                duree = 0.42f + 0.16f * sort.nextFloat(),
                portee = 0.17f + 0.11f * sort.nextFloat(),
                couleur = teintes[sort.nextInt(teintes.size)],
                etoiles = 9 + sort.nextInt(5),
                biais = sort.nextFloat() * 6.283f
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || avancement <= 0f) return
        val d = resources.displayMetrics.density
        val echelle = minOf(w, h)

        for (g in gerbes) {
            val t = (avancement - g.depart) / g.duree
            if (t <= 0f || t >= 1f) continue
            val reste = 1f - t
            val cx = g.x * w
            val cy = g.y * h
            val portee = g.portee * echelle

            // Le rayon décélère — la gerbe s'ouvre vite puis s'essouffle —
            // pendant que la pesanteur, elle, accélère.
            val rayon = portee * (1f - reste * reste)
            val chute = portee * 0.75f * t * t
            // La traînée est la distance parcourue depuis l'image d'avant,
            // approchée par la dérivée : elle s'allonge au départ et se
            // résorbe à l'arrivée, exactement comme une étincelle qui ralentit.
            val trainee = portee * 0.34f * reste

            pinceau.color = g.couleur
            pinceau.strokeWidth = (2.6f - 1.2f * t) * d
            pinceau.alpha = (reste * reste * 255f).toInt().coerceIn(0, 255)

            for (i in 0 until g.etoiles) {
                val angle = g.biais + 6.283f * i / g.etoiles
                val dx = cos(angle)
                val dy = sin(angle)
                // Une étoile sur deux part un peu moins loin : un cercle
                // parfait se lit comme un anneau, pas comme une gerbe.
                val sien = if (i % 2 == 0) rayon else rayon * 0.72f
                val queue = (sien - trainee).coerceAtLeast(0f)
                canvas.drawLine(
                    cx + dx * queue, cy + dy * queue + chute * (queue / portee),
                    cx + dx * sien, cy + dy * sien + chute,
                    pinceau
                )
            }

            // Le cœur de la gerbe, qui s'éteint le premier : c'est lui qui
            // donne l'instant du départ, avant même que les étoiles s'écartent.
            if (t < 0.35f) {
                pinceau.style = Paint.Style.FILL
                pinceau.alpha = ((1f - t / 0.35f) * 200f).toInt().coerceIn(0, 255)
                canvas.drawCircle(cx, cy, (1f - t / 0.35f) * 5f * d, pinceau)
                pinceau.style = Paint.Style.STROKE
            }
        }
    }
}
