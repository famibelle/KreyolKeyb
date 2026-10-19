package com.example.kreyolkeyboard.carnet

import com.example.kreyolkeyboard.KeyFeedback
import com.example.kreyolkeyboard.R
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        /** Les séries que ces cartes viennent de compléter : voir [Series.aFeter]. */
        seriesCompletees: List<Serie> = emptyList(),
        /** Le palier de collection que cette pochette franchit : voir [Etapes]. */
        etape: Int? = null,
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

        // La première rare et la première très rare du carnet ont droit à la
        // version longue de leur cérémonie, une seule fois chacune.
        val premieres = if (!animations) emptySet() else premieres(
            ordre.map { it.carte.forme to it.rarete },
            nouvelles,
            runCatching { Carnet.paliersPossedes(ctx, nouvelles) }.getOrDefault(Rarete.values().toSet())
        )

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
            text = ctx.getString(R.string.pochette_titre, jeu.libelle(ctx))
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
            text = ctx.getString(R.string.pochette_nouvelle_carte)
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

        // Le nom du palier, pour les rares et très rares seulement. La
        // cérémonie le faisait sentir sans jamais le dire : il fallait
        // connaître le code des bordures et des heures pour savoir ce qu'on
        // venait de gagner. Une commune n'a pas d'annonce, comme elle n'a pas
        // de halo.
        val annonce = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8f) }
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            visibility = View.GONE
        }

        val bandeaux = LinearLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(bandeau)
            addView(annonce)
        }
        colonne.addView(bandeaux)

        /** Montre l'annonce du palier de [rarete], avec un petit bond si [anime]. */
        fun annoncer(rarete: Rarete, anime: Boolean) {
            if (!rarete.distinguee) return
            annonce.text = ctx.getString(
                if (rarete == Rarete.TRES_RARE) R.string.pochette_carte_tres_rare
                else R.string.pochette_carte_rare
            )
            annonce.background = GradientDrawable().apply {
                cornerRadius = 20f * d
                setColor(rarete.couleur)
            }
            annonce.visibility = View.VISIBLE
            if (anime) {
                val ampleur = if (rarete == Rarete.TRES_RARE) 0.5f else 0.7f
                annonce.alpha = 0f
                annonce.scaleX = ampleur
                annonce.scaleY = ampleur
                annonce.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(if (rarete == Rarete.TRES_RARE) 420L else 260L)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            annonce.announceForAccessibility(annonce.text)
        }

        // La scène : les cartes y défilent, elle ne bouge pas. C'est elle qui
        // lit le geste, et pas la carte — voir [ScenePochette].
        val scene = ScenePochette(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        colonne.addView(scene)

        // Sous la première rare et la première très rare : comment on les
        // reconnaît. C'est le seul moment où le code des bordures et des
        // heures est dit ; ensuite, la carte le rappelle d'elle-même.
        val legende = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) }
            textSize = 14f
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setTextColor(0xFFECEFF1.toInt())
            visibility = View.GONE
        }
        colonne.addView(legende)

        // Sa place est retenue dès l'ouverture d'une pochette qui en aura une :
        // apparaître en cours de route rétrécissait la scène, et la carte se
        // refaisait à sa nouvelle taille, paysage compris. Sur un Galaxy A21s
        // (2026-10-17), 0,85 s d'arrêt juste après la pose, là où une très
        // rare ordinaire n'en a aucun.
        val legendeAttendue = premieres.isNotEmpty()
        if (legendeAttendue) {
            legende.text = ctx.getString(
                if (ordre.any { it.carte.forme in premieres && it.rarete == Rarete.TRES_RARE })
                    R.string.pochette_premiere_tres_rare else R.string.pochette_premiere_rare
            )
            legende.visibility = View.INVISIBLE
        }

        /** La légende de la première [rarete], qui monte doucement si [anime]. */
        fun expliquer(rarete: Rarete, anime: Boolean) {
            legende.text = ctx.getString(
                if (rarete == Rarete.TRES_RARE) R.string.pochette_premiere_tres_rare
                else R.string.pochette_premiere_rare
            )
            legende.visibility = View.VISIBLE
            if (anime) {
                legende.alpha = 0f
                legende.translationY = 12f * d
                legende.animate().alpha(1f).translationY(0f)
                    .setStartDelay(500L).setDuration(500L).start()
            }
        }

        val boutons = LinearLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12f) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val passer = TextView(ctx).apply {
            text = ctx.getString(R.string.pochette_passer)
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
            text = ctx.getString(R.string.pochette_mon_carnet)
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

        // La fenêtre est bord à bord (voir [com.example.kreyolkeyboard.BordABord]) :
        // sans cet écart, le titre passait sous la barre d'état et « Passer »
        // sous la barre de navigation.
        ViewCompat.getRootWindowInsets(hote)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )?.let { b ->
            colonne.setPadding(
                colonne.paddingLeft + b.left, colonne.paddingTop + b.top,
                colonne.paddingRight + b.right, colonne.paddingBottom + b.bottom
            )
        }

        voile.addView(colonne)
        hote.addView(voile)

        // Voir [FigerOrientation] : une rotation emportait la pochette.
        FigerOrientation.tantQue(voile)

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
            annonce.animate().cancel()
            annonce.visibility = View.GONE
            legende.animate().cancel()
            legende.visibility = if (legendeAttendue) View.INVISIBLE else View.GONE
            val neuves = contenus.count { it.carte.forme in nouvelles }
            if (index >= ordre.size) {
                progres.text = if (ordre.size > 1)
                    ctx.getString(R.string.pochette_revoir) else ""
                val res = ctx.resources
                titre.text = "🎁 " + res.getQuantityString(R.plurals.cartes, contenus.size, contenus.size) +
                    (if (neuves > 0) " · " + res.getQuantityString(R.plurals.pochette_nouvelles, neuves, neuves) else "")
            } else {
                progres.text = "${index + 1} / ${ordre.size}" +
                    (if (ordre.size > 1) "   ·   " + ctx.getString(R.string.pochette_parcourir) else "")
                titre.text = ctx.getString(R.string.pochette_titre, jeu.libelle(ctx))
            }
            // « Fermer » dès que tout a été vu, où qu'on soit dans le paquet :
            // « Passer » ne veut plus rien dire quand il ne reste rien à
            // passer. Même bascule pour la sortie vers le carnet, qui n'était
            // cachée que pour ne pas inviter à partir avant d'avoir ouvert.
            val tout = vues.size >= ordre.size
            passer.text = ctx.getString(if (tout) R.string.fermer else R.string.pochette_passer)
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
                // Une étape de collection et une série complétée passent avant
                // le bilan ordinaire : ce sont les événements de la partie, et
                // chacun n'arrive qu'une fois.
                text = (listOfNotNull(etape?.let { "🎉 " + ctx.getString(R.string.etape_atteinte, it) }) +
                    seriesCompletees.map {
                        "🏆 " + ctx.getString(R.string.serie_complete, ctx.getString(it.titre))
                    } + ctx.getString(
                    if (neuves > 0) R.string.pochette_bilan_neuves
                    else R.string.pochette_bilan_deja
                )).joinToString("\n\n")
                textSize = 15f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
                setTextColor(0xFFECEFF1.toInt())
            })
            // L'éclat d'une très rare, une fois : un événement se voit autant
            // qu'il se lit. Pas de feu d'artifice (propriétaire, 2026-10-19).
            if (animations && (etape != null || seriesCompletees.isNotEmpty())) {
                eclater(page, Rarete.TRES_RARE)
            }
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
            val premiere = c.carte.forme in premieres

            val carte = CarteCarnet.complete(ctx, c)
            // Une carte ornée se règle sur la hauteur de la scène, comme dans
            // le lecteur du carnet. Dans un défilement, elle prenait toute la
            // largeur : à l'italienne, elle faisait trois écrans de haut et ne
            // se dessinait jamais, ni retournement ni inclinaison (2026-10-12).
            // Sa fenêtre vit au temps de sa saison, comme dans le lecteur :
            // la découverte est le moment où ça compte le plus. Elle en a été
            // privée jusqu'en 37.3.1 par simple oubli.
            (carte as? CarteOrnee)?.avecAmbiance()
            val face: ViewGroup = if (carte is Carton) {
                carte.ajusteALaHauteur = true
                FrameLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                    addView(carte, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    ))
                }
            } else ScrollView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                isVerticalScrollBarEnabled = false
                addView(carte)
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
                annoncer(rarete, anime = false)
                if (premiere) expliquer(rarete, anime = false)
                // `suivre` se tait tout seul quand les animations sont coupées.
                Inclinaison.suivre(face)
                return
            }

            // Sa taille, il la règle lui-même sur la scène : voir [DosDeCarte.onMeasure].
            val dos = DosDeCarte(ctx, jeu, rarete).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
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

            // La face se pose tout de suite, cachée sous le dos : son paysage
            // et son métal se calculent en fond pendant que le dos attend
            // (voir [CarteOrnee.attendreSaMatiere]). Elle se construisait au
            // moment de tourner ; sur un Galaxy A21s (2026-10-12), chaque carte
            // marquait alors 0,4 à 0,9 s d'arrêt, puis tournait à 27 images par
            // seconde, tracée à chaque angle.
            var prete = carte !is CarteOrnee
            var tourner: (() -> Unit)? = null
            (carte as? CarteOrnee)?.attendreSaMatiere {
                prete = true
                tourner?.invoke()
                tourner = null
            }
            face.visibility = View.INVISIBLE
            face.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            page.addView(face)
            page.addView(dos)

            // La cérémonie appartient à cette page-là. Si le joueur glisse
            // pendant qu'elle se joue, la page quitte la scène et tout ce qui
            // suit doit s'arrêter : `page.parent` est ce qui le dit.
            fun abandonnee() = ferme || page.parent == null

            // Le dos arrive, puis se retourne de lui-même. Le retournement est
            // la récompense : la faire attendre un appui de plus ne la rend
            // pas plus douce, elle rend la pochette longue. Chaque palier a
            // ensuite son propre geste, et pas seulement plus d'intensité
            // (maquettes validées par le propriétaire, 2026-10-12) : la peu
            // commune reçoit un reflet, la rare se devine avant d'apparaître
            // (le dos frémit) puis se signe (le poinçon se frappe), la très
            // rare change toute la scène (la nuit tombe, la carte monte, un
            // premier retournement éclair ne laisse voir qu'un éclat).
            dos.cameraDistance = 12000f * d
            face.cameraDistance = 12000f * d
            dos.setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // La première fois, l'attente s'allonge : la très rare monte plus
            // lentement, la rare frémit une seconde fois, plus fort.
            val attente = when (rarete) {
                Rarete.TRES_RARE -> {
                    val lenteur = if (premiere) LENTEUR_PREMIERE else 1f
                    preluderTresRare(page, dos, halo, rarete, lenteur)
                    (PRELUDE_TRES_RARE * lenteur).toLong()
                }
                Rarete.RARE -> {
                    arriverDos(dos)
                    fremir(dos)
                    if (premiere) {
                        fremir(dos, delai = 1150L, force = 1.6f)
                        2000L
                    } else 1050L
                }
                else -> {
                    arriverDos(dos)
                    380L
                }
            }
            if (rarete != Rarete.TRES_RARE) halo?.animate()?.alpha(1f)?.setDuration(attente)?.start()

            val lente = rarete == Rarete.TRES_RARE
            dos.postDelayed({
                if (abandonnee()) return@postDelayed
                // L'éclair d'une très rare se joue sur le dos, par une chaîne
                // d'animations. Sur un téléphone lent, ou derrière un arrêt,
                // elle finissait après ce retournement-ci : son dernier
                // `rotationY(360f)` l'annulait, et la carte restait face
                // cachée pour de bon, « Fermer » affiché (vu sur l'émulateur,
                // 2026-10-18, avec la 37.4.7). Le retournement coupe la chaîne
                // et part d'un dos remis droit.
                dos.retourne = true
                dos.animate().cancel()
                if (lente) {
                    dos.rotationY = 360f
                    dos.scaleX = 1f
                    dos.scaleY = 1f
                }
                dos.animate().rotationY(dos.rotationY + 90f).setDuration(if (lente) 350L else 170L)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        if (abandonnee()) return@withEndAction
                        page.removeView(dos)
                        // Le halo reste derrière la carte révélée et s'efface
                        // doucement : la lueur ne s'éteint pas au moment où
                        // elle a enfin quelque chose à éclairer.
                        halo?.animate()?.alpha(0.35f)?.setDuration(900)?.start()
                        face.rotationY = -90f
                        face.visibility = View.VISIBLE
                        val retourner = {
                            if (!abandonnee()) face.animate().rotationY(0f)
                                .setDuration(
                                    when (rarete) {
                                        Rarete.TRES_RARE -> 650L
                                        Rarete.RARE -> 300L
                                        else -> 210L
                                    }
                                )
                                .setInterpolator(
                                    when (rarete) {
                                        Rarete.TRES_RARE -> OvershootInterpolator(1.5f)
                                        Rarete.RARE -> OvershootInterpolator(1.2f)
                                        else -> DecelerateInterpolator()
                                    }
                                )
                                .withEndAction {
                                    if (abandonnee()) return@withEndAction
                                    // La carte a fini de se poser : elle suit
                                    // maintenant l'inclinaison de l'appareil.
                                    Inclinaison.suivre(face)
                                    when (rarete) {
                                        Rarete.PEU_COMMUN -> {
                                            refleter(page, face, carte)
                                            // Un seul tic, quand le reflet part.
                                            vibrer(page, 0.25f, 130L)
                                        }
                                        Rarete.RARE -> frapperPoincon(page, face, carte, rarete)
                                        Rarete.TRES_RARE -> {
                                            rayonner(page, face, rarete)
                                            eclater(page, rarete)
                                            // Le coup plein de l'éclat, puis son rebond.
                                            vibrer(page, 1f)
                                            vibrer(page, 0.7f, 170L)
                                        }
                                        else -> {}
                                    }
                                    annoncer(rarete, anime = true)
                                    if (premiere) {
                                        expliquer(rarete, anime = true)
                                        // Un second éclat pour la première très
                                        // rare, quand le premier s'éteint.
                                        if (rarete == Rarete.TRES_RARE) page.postDelayed({
                                            if (abandonnee()) return@postDelayed
                                            eclater(page, rarete)
                                            vibrer(page, 0.6f)
                                        }, 900L)
                                    }
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
                        }
                        if (prete) retourner() else tourner = retourner
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

    /** Le prélude d'une très rare, en millisecondes : nuit, montée, éclair. */
    private const val PRELUDE_TRES_RARE = 2900L

    /** Combien la première très rare du carnet étire son prélude. */
    private const val LENTEUR_PREMIERE = 1.4f

    /**
     * Les formes qui ont droit à la version longue : dans l'ordre de la
     * pochette, la première neuve de chaque palier distingué que le carnet ne
     * possédait pas encore. [possedes] sont les paliers déjà au carnet hors
     * de cette pochette. Une seule par palier : deux rares neuves dans la
     * même partie n'en font qu'une « première ».
     */
    internal fun premieres(
        ordre: List<Pair<String, Rarete>>,
        nouvelles: Set<String>,
        possedes: Set<Rarete>
    ): Set<String> = Rarete.values()
        .filter { it.distinguee && it !in possedes }
        .mapNotNullTo(HashSet()) { palier ->
            ordre.firstOrNull { (forme, r) -> r == palier && forme in nouvelles }?.first
        }

    /** Le dos d'une commune, d'une peu commune ou d'une rare arrive (0,2 s). */
    private fun arriverDos(dos: View) {
        dos.alpha = 0f
        dos.scaleX = 0.9f
        dos.scaleY = 0.9f
        dos.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    /**
     * Le dos d'une rare frémit trois fois, de plus en plus fort, chaque fois
     * avec une petite vibration : on devine qu'on tient quelque chose avant
     * de le voir.
     */
    private fun fremir(dos: View, delai: Long = 0L, force: Float = 1f) {
        val d = dos.resources.displayMetrics.density
        val amplitudes = floatArrayOf(3f, 5f, 8f).map { it * force }
        var dernier = -1
        ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 250L + delai
            duration = 810L
            addUpdateListener {
                val u = it.animatedValue as Float * 3f
                val k = u.toInt().coerceAtMost(2)
                val v = u - k
                if (k != dernier) {
                    dernier = k
                    vibrer(dos, (FREMISSEMENTS[k] * force).coerceAtMost(1f))
                }
                val actif = v < 0.6f
                val a = if (actif) amplitudes[k] * sin(v / 0.6f * Math.PI.toFloat() * 4f) else 0f
                dos.rotation = a
                dos.translationX = a * 1.5f * d
                val e = if (actif) 1f + 0.025f * sin(v / 0.6f * Math.PI.toFloat()) else 1f
                dos.scaleX = e
                dos.scaleY = e
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    dos.rotation = 0f
                    dos.translationX = 0f
                    dos.scaleX = 1f
                    dos.scaleY = 1f
                }
            })
            start()
        }
    }

    /**
     * Le prélude d'une très rare : le ciel s'éteint en nuit étoilée, le dos
     * monte du bas en tournant sur lui-même, le halo bat, puis un premier
     * retournement éclair ne montre qu'un éclat violet avant que le dos
     * revienne. Le vrai retournement suit, plus lent.
     */
    private fun preluderTresRare(
        page: FrameLayout,
        dos: View,
        halo: View?,
        rarete: Rarete,
        /** 1 d'ordinaire ; plus pour la première très rare, qui prend son temps. */
        lenteur: Float = 1f
    ) {
        val ctx = page.context
        fun t(ms: Long) = (ms * lenteur).toLong()
        val nuit = NuitEtoilee(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            alpha = 0f
        }
        page.addView(nuit, 0)
        nuit.animate().alpha(1f).setDuration(t(500)).start()

        dos.translationY = ctx.resources.displayMetrics.heightPixels * 0.6f
        dos.scaleX = 0.7f
        dos.scaleY = 0.7f
        dos.animate().translationY(0f).scaleX(1f).scaleY(1f).rotationY(360f)
            .setStartDelay(t(300)).setDuration(t(1200))
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()

        // Le téléphone suit la montée : un coup quand le dos arrive, un quand
        // le halo bat, un plus franc à l'éclair.
        vibrer(dos, 0.3f, t(1500))
        vibrer(dos, 0.4f, t(1800))
        vibrer(dos, 0.6f, t(2200))

        // Le halo bat pendant l'attente.
        halo?.let { h ->
            h.postDelayed({
                h.animate().alpha(0.9f).setDuration(300).withEndAction {
                    h.animate().alpha(0.5f).setDuration(300).withEndAction {
                        h.animate().alpha(0.9f).setDuration(250).start()
                    }.start()
                }.start()
            }, t(1500))
        }

        // L'éclair : le dos tourne d'un quart, une face violette passe et
        // repart, le dos revient. 0,3 s en tout.
        val eclair = EclairCarte(ctx, rarete).apply {
            // Sa vraie taille arrive avec l'éclair : le dos ne connaît la sienne
            // qu'une fois mesuré sur la scène.
            layoutParams = FrameLayout.LayoutParams(0, 0, Gravity.CENTER)
            visibility = View.INVISIBLE
            cameraDistance = dos.cameraDistance
        }
        page.addView(eclair)
        // Chaque maillon vérifie que le vrai retournement n'a pas commencé :
        // voir [DosDeCarte.retourne].
        fun coupee(): Boolean {
            if ((dos as? DosDeCarte)?.retourne != true) return false
            page.removeView(eclair)
            return true
        }
        dos.postDelayed({
            if (dos.parent == null || coupee()) return@postDelayed
            eclair.layoutParams = FrameLayout.LayoutParams(dos.width, dos.height, Gravity.CENTER)
            dos.animate().rotationY(450f).setStartDelay(0).setDuration(100)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    if (coupee()) return@withEndAction
                    eclair.rotationY = -90f
                    eclair.visibility = View.VISIBLE
                    eclair.animate().rotationY(0f).setDuration(100).withEndAction {
                        if (coupee()) return@withEndAction
                        eclair.animate().rotationY(-90f).setDuration(100).withEndAction {
                            page.removeView(eclair)
                            if (coupee()) return@withEndAction
                            dos.animate().rotationY(360f).setDuration(100)
                                .setInterpolator(DecelerateInterpolator())
                                .withEndAction {
                                    if (coupee()) return@withEndAction
                                    dos.animate().scaleX(1.02f).scaleY(1.02f).setDuration(200)
                                        .withEndAction {
                                            if (coupee()) return@withEndAction
                                            dos.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                                        }.start()
                                }.start()
                        }.start()
                    }.start()
                }.start()
        }, t(2100))
    }

    /** Où la carte se trouve dans [page], sachant qu'elle vit dans le défilement [face]. */
    private fun cadre(face: View, carte: View) = RectF(
        (face.left + carte.left).toFloat(),
        (face.top + carte.top - face.scrollY).toFloat(),
        (face.left + carte.right).toFloat(),
        (face.top + carte.bottom - face.scrollY).toFloat()
    )

    /** Un passage de lumière sur une peu commune, 0,13 s après qu'elle s'est posée. */
    private fun refleter(page: FrameLayout, face: View, carte: View) {
        val reflet = RefletCarte(page.context, cadre(face, carte)).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        page.addView(reflet)
        animerPuisRetirer(reflet, 600L, 130L) { reflet.avancement = it }
    }

    /**
     * Le poinçon d'une rare se frappe : il s'allume dans la marge, la carte
     * s'enfonce d'un rien, une onde part de lui, et le téléphone donne un
     * coup. C'est la carte qui se signe.
     */
    private fun frapperPoincon(page: FrameLayout, face: View, carte: View, rarete: Rarete) {
        val boite = cadre(face, carte)
        val u = boite.width() / Ornement.LARGEUR
        val z = Ornement.saison(rarete)
        val poincon = RectF(
            boite.left + z.left * u, boite.top + z.top * u,
            boite.left + z.right * u, boite.top + z.bottom * u
        )
        val frappe = FrappePoincon(page.context, poincon, rarete).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        page.postDelayed({
            if (page.parent == null) return@postDelayed
            page.addView(frappe)
            animerPuisRetirer(frappe, 700L, 0L) { frappe.avancement = it }
            vibrer(page, 0.75f)
            face.animate().scaleX(0.985f).scaleY(0.985f).translationY(3f * u).setDuration(90)
                .withEndAction {
                    face.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(120).start()
                }.start()
        }, 350L)
    }

    /** Les rayons d'une très rare, derrière la carte, le temps qu'elle se pose. */
    private fun rayonner(page: FrameLayout, face: View, rarete: Rarete) {
        val rayons = RayonsRarete(page.context, rarete).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        page.addView(rayons, page.indexOfChild(face).coerceAtLeast(0))
        animerPuisRetirer(rayons, 2600L, 0L) { rayons.avancement = it }
    }

    private fun animerPuisRetirer(vue: View, duree: Long, delai: Long, pas: (Float) -> Unit) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = delai
            duration = duree
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { pas(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    (vue.parent as? ViewGroup)?.removeView(vue)
                }
            })
            start()
        }
    }

    /** Les trois frémissements du dos d'une rare, de plus en plus forts. */
    private val FREMISSEMENTS = floatArrayOf(0.2f, 0.35f, 0.5f)

    /**
     * Un coup de [force] (0 à 1) dans [delai] millisecondes, si [v] est encore
     * à l'écran : une page quittée en cours de cérémonie se tait.
     *
     * Chaque palier a sa partition, et elle ne joue qu'à la découverte, jamais
     * au retour sur une carte ni animations coupées : commune, rien ; peu
     * commune, un tic ; rare, trois frémissements puis le poinçon ; très rare,
     * la montée du prélude, puis l'éclat et ses crépitements.
     *
     * Passe par [KeyFeedback.onRevelation], qui choisit ce que ce téléphone
     * sait rendre. Jusqu'en 37.4.0 c'était `performHapticFeedback` en direct,
     * muet sur un Galaxy A21s.
     */
    private fun vibrer(v: View, force: Float, delai: Long = 0L) {
        if (delai == 0L) {
            if (v.isAttachedToWindow) KeyFeedback.onRevelation(v, force)
            return
        }
        v.postDelayed({ if (v.isAttachedToWindow) KeyFeedback.onRevelation(v, force) }, delai)
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

    /**
     * Le vrai retournement a commencé : l'éclair du prélude, s'il n'a pas fini,
     * ne doit plus toucher au dos. Voir [Booster.preluderTresRare].
     */
    var retourne = false

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinceauTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private var fond: LinearGradient? = null

    /**
     * 44 % de la hauteur de l'écran, sans dépasser la scène, et jamais plus
     * large que haut : à l'italienne, la largeur de la scène en faisait un
     * bandeau qui débordait en bas. En portrait, rien ne change.
     */
    override fun onMeasure(largeurSpec: Int, hauteurSpec: Int) {
        super.onMeasure(largeurSpec, hauteurSpec)
        val h = minOf(measuredHeight, (resources.displayMetrics.heightPixels * 0.44f).toInt())
        setMeasuredDimension(minOf(measuredWidth, h), h)
    }

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
 * Le reflet d'une peu commune : une bande de lumière qui traverse la carte en
 * diagonale, une fois. [cadre] est la carte dans la page ; la bande y est
 * découpée, coins arrondis compris.
 */
class RefletCarte(context: Context, private val cadre: RectF) : View(context) {
    var avancement: Float = 0f
        set(valeur) {
            field = valeur
            invalidate()
        }
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chemin = android.graphics.Path()

    override fun onDraw(canvas: Canvas) {
        if (cadre.width() <= 0f) return
        val u = cadre.width() / Ornement.LARGEUR
        chemin.reset()
        chemin.addRoundRect(cadre, Ornement.RAYON * u, Ornement.RAYON * u, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(chemin)
        // La bande avance le long de la diagonale montante.
        val l = cadre.width() + cadre.height() * 0.55f
        val centre = -0.15f + 1.3f * avancement
        val x = cadre.left + centre * l
        val demi = 0.09f * l
        pinceau.shader = LinearGradient(
            x - demi, cadre.top, x + demi, cadre.top + demi * 0.55f,
            intArrayOf(0x00FFFFFF, 0xB0FFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(cadre, pinceau)
        canvas.restore()
    }
}

/**
 * La frappe du poinçon d'une rare : le cartouche s'allume puis s'éteint, et
 * une onde part de lui jusqu'au-delà de la carte. [poincon] est le cartouche
 * dans la page.
 */
class FrappePoincon(context: Context, private val poincon: RectF, private val rarete: Rarete) : View(context) {
    var avancement: Float = 0f
        set(valeur) {
            field = valeur
            invalidate()
        }
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val reste = 1f - avancement
        val cx = poincon.centerX()
        val cy = poincon.centerY()
        // Le cartouche, éclairé, un peu plus grand que la gravure.
        val m = 2f * d
        val r = RectF(poincon.left - m, poincon.top - m, poincon.right + m, poincon.bottom + m)
        val eclat = (1f - avancement / 0.7f).coerceIn(0f, 1f)
        pinceau.style = Paint.Style.FILL
        pinceau.color = Color.WHITE
        pinceau.alpha = (eclat * 210).toInt()
        canvas.drawRoundRect(r, 3f * d, 3f * d, pinceau)
        pinceau.style = Paint.Style.STROKE
        pinceau.strokeWidth = 2f * d
        pinceau.color = rarete.couleur
        pinceau.alpha = (eclat * 255).toInt()
        canvas.drawRoundRect(r, 3f * d, 3f * d, pinceau)
        // L'onde.
        val rayon = 10f * d + 210f * d * (1f - reste * reste)
        pinceau.color = eclaircir(rarete.couleur, 0.6f)
        pinceau.strokeWidth = 5f * d * reste + 1f
        pinceau.alpha = (reste * 220).toInt()
        canvas.drawOval(RectF(cx - rayon, cy - rayon * 0.8f, cx + rayon, cy + rayon * 0.8f), pinceau)
    }
}

/**
 * La nuit d'une très rare : un ciel violet sombre, des étoiles qui scintillent
 * et quatre étoiles filantes. Le scintillement s'arrête au bout de sept
 * secondes, le ciel reste : une animation sans fin coûterait sur un petit
 * téléphone tant que la carte reste à l'écran.
 */
class NuitEtoilee(context: Context) : View(context) {
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private var ciel: LinearGradient? = null
    private val hasard = Random(7)
    private val etoiles = Array(110) {
        floatArrayOf(hasard.nextFloat(), hasard.nextFloat(), 0.7f + hasard.nextFloat() * 1.4f,
            hasard.nextFloat() * 6.28f, 1.5f + hasard.nextFloat() * 2.5f)
    }
    /** Départ (s), x, y (en part de l'écran) et sens de chaque étoile filante. */
    private val filantes = arrayOf(
        floatArrayOf(1.9f, 0.1f, 0.16f, 1f), floatArrayOf(2.9f, 0.9f, 0.11f, -1f),
        floatArrayOf(3.7f, 0.15f, 0.7f, 1f), floatArrayOf(4.6f, 0.85f, 0.6f, -1f)
    )
    private var debut = 0L

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        debut = android.os.SystemClock.uptimeMillis()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        ciel = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0E0828.toInt(), 0xFF3C145A.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        val t = (android.os.SystemClock.uptimeMillis() - debut) / 1000f
        val vivant = t < 7f
        pinceau.style = Paint.Style.FILL
        pinceau.shader = ciel
        canvas.drawRect(0f, 0f, w, h, pinceau)
        pinceau.shader = null
        pinceau.color = 0xFFFFFAEB.toInt()
        val tt = if (vivant) t else 7f
        for (e in etoiles) {
            pinceau.alpha = (255 * (0.45f + 0.55f * (0.5f + 0.5f * sin(tt * e[4] + e[3])))).toInt()
            canvas.drawCircle(e[0] * w, e[1] * h, e[2] * d, pinceau)
        }
        if (vivant) {
            pinceau.color = Color.WHITE
            pinceau.strokeCap = Paint.Cap.ROUND
            for (f in filantes) {
                val u = (t - f[0]) / 0.7f
                if (u < 0f || u > 1f) continue
                val x = f[1] * w + f[3] * 210f * d * u
                val y = f[2] * h + 115f * d * u
                val force = sin(u * Math.PI.toFloat())
                for (k in 0 until 10) {
                    val q = k / 10f
                    pinceau.alpha = (230 * (1f - q) * force).toInt()
                    pinceau.strokeWidth = (2.5f * (1f - q) + 0.5f) * d
                    canvas.drawLine(
                        x - f[3] * 9f * d * k, y - 5f * d * k,
                        x - f[3] * 9f * d * (k + 1), y - 5f * d * (k + 1), pinceau
                    )
                }
            }
            postInvalidateOnAnimation()
        }
    }
}

/**
 * Les rayons d'une très rare : dix-huit pinceaux de lumière qui tournent
 * lentement derrière la carte, montent puis s'éteignent avec [avancement].
 */
class RayonsRarete(context: Context, private val rarete: Rarete) : View(context) {
    var avancement: Float = 0f
        set(valeur) {
            field = valeur
            invalidate()
        }
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chemin = android.graphics.Path()
    private var fondu: RadialGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val c = eclaircir(rarete.couleur, 0.7f)
        fondu = RadialGradient(
            w / 2f, h / 2f, maxOf(w, h) * 0.6f,
            intArrayOf(c, c and 0x00FFFFFF), floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val montee = (avancement / 0.15f).coerceIn(0f, 1f)
        val descente = ((1f - avancement) / 0.55f).coerceIn(0f, 1f)
        val force = minOf(montee, descente)
        if (force <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val r = maxOf(width, height).toFloat()
        chemin.reset()
        val n = 18
        val tour = avancement * 50f
        for (k in 0 until n) {
            val a = Math.toRadians((tour + k * 360f / n).toDouble())
            val da = Math.toRadians(360.0 / n * 0.28)
            chemin.moveTo(cx, cy)
            chemin.lineTo(cx + r * cos(a - da).toFloat(), cy + r * sin(a - da).toFloat())
            chemin.lineTo(cx + r * cos(a + da).toFloat(), cy + r * sin(a + da).toFloat())
            chemin.close()
        }
        pinceau.shader = fondu
        pinceau.alpha = (force * 200).toInt()
        canvas.drawPath(chemin, pinceau)
    }
}

/**
 * L'éclair du premier retournement d'une très rare : une face violette sans
 * mot, qu'on n'aperçoit que le temps d'un aller-retour.
 */
class EclairCarte(context: Context, private val rarete: Rarete) : View(context) {
    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = resources.displayMetrics.density
        pinceau.shader = RadialGradient(
            w / 2f, h / 2f, maxOf(w, h) * 0.7f,
            intArrayOf(0xFFFFFFFF.toInt(), eclaircir(rarete.couleur, 0.45f), rarete.couleur),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 14f * d, 14f * d, pinceau)
    }
}
