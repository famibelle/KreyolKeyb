package com.example.kreyolkeyboard.carnet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.kreyolkeyboard.TranslationDictionary

/**
 * La boîte de Leitner comme jeu à part entière dans Spiller.
 *
 * Un [Fragment] qui affiche [BoiteLeitner] et gère les interactions : taper
 * un casier en étale les cartes en éventail ([EventailCasier]), taper une carte
 * de l'éventail l'ouvre en grand, taper la plaque de laiton lance une session de
 * révision si des cartes sont dues.
 *
 * ## Le bouton retour remonte d'un cran à la fois
 *
 * Carte ouverte, puis éventail, puis la boîte elle-même. Sans rappel propre, le
 * retour allait droit à celui de `GamesFragment` et faisait quitter la boîte
 * depuis un casier ouvert : on voulait ranger un paquet, on se retrouvait devant
 * la liste des jeux. Le rappel d'ici est enregistré après le sien, donc consulté
 * avant, et n'est actif que tant qu'il y a quelque chose à refermer.
 */
class BoiteFragment : Fragment() {

    private lateinit var racine: FrameLayout
    private lateinit var boite: BoiteLeitner
    private var eventail: EventailCasier? = null
    private var casierOuvert = -1
    private var voileCarte: View? = null

    /**
     * Le contenu de chaque carte du carnet, par forme, lu et écrit sur le fil
     * principal seulement.
     *
     * Préchargé à l'ouverture de la boîte plutôt qu'au toucher d'un casier : le
     * contenu demande le dictionnaire, et l'attendre au toucher laissait deux
     * secondes sans rien à l'écran, de quoi croire le toucher raté.
     */
    private val contenus = HashMap<String, ContenuCarte>()
    private var contenusPrets = false

    /** Écarte un chargement dépassé par un plus récent, après une révision par exemple. */
    private var generation = 0

    private val retour = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                voileCarte != null -> fermerCarte()
                eventail != null -> eventail?.fermer()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        racine = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val colonne = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        boite = BoiteLeitner(ctx).apply {
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply { setMargins(dp(12f), dp(8f), dp(12f), dp(8f)) }
            surCasier = { boiteNo -> ouvrirCasier(boiteNo) }
            surRevision = { lancerRevision() }
        }
        colonne.addView(boite)

        racine.addView(colonne)

        return racine
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, retour)
        chargerEnFond()
    }

    private fun majRetour() {
        retour.isEnabled = voileCarte != null || eventail != null
    }

    private fun chargerEnFond() {
        val ctx = requireContext().applicationContext
        val gen = ++generation
        contenusPrets = false
        Thread {
            // La boîte ne lit que la boîte et l'échéance de chaque carte : elle
            // s'affiche tout de suite, en cartons unis, et les rectos suivent.
            val cartes = Carnet.cartes(ctx)
            val aujourd = Sonje.aujourdHui()
            activity?.runOnUiThread {
                if (!isAdded || gen != generation) return@runOnUiThread
                boite.poser(cartes, aujourd)
                chargerContenus(cartes, gen)
            }
        }.start()
    }

    /**
     * Charge le contenu de toute la collection, puis donne aux cartes de la
     * boîte leur recto, celui du carnet.
     *
     * En deux temps, et c'est voulu : le recto demande la fiche du dictionnaire
     * (rareté, blason), dont le premier chargement prend plusieurs secondes. Les
     * cartons se retournent en rectos quand ils sont prêts, au lieu que la boîte
     * les attende à blanc. Un éventail ouvert pendant l'attente reçoit ses cartes
     * au même moment.
     */
    private fun chargerContenus(cartes: List<CarteMot>, gen: Int) {
        val ctx = requireContext().applicationContext
        Thread {
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            val tous = cartes.map { CarteCarnet.contenu(ctx, it) }
            activity?.runOnUiThread {
                if (!isAdded || gen != generation) return@runOnUiThread
                contenus.clear()
                tous.forEach { contenus[it.carte.forme] = it }
                contenusPrets = true

                val cote = (COTE_RECTO * resources.displayMetrics.density).toInt()
                boite.poserRectos(
                    boite.cartesVisibles()
                        .mapNotNull { c -> contenus[c.forme]?.let { c.forme to rendreRecto(it, cote) } }
                        .toMap()
                )
                eventail?.let { ev ->
                    if (ev.enAttente && casierOuvert >= 0) ev.poserCartes(cartesDuCasier(casierOuvert))
                }
            }
        }.start()
    }

    /** Les cartes d'un casier, celles à revoir d'abord : c'est l'ordre de la pile. */
    private fun cartesDuCasier(i: Int): List<ContenuCarte> {
        val aujourdHui = Sonje.aujourdHui()
        return contenus.values
            .filter { it.carte.boite.coerceIn(0, Sonje.BOITE_ACQUISE) == i }
            .sortedWith(
                compareBy<ContenuCarte> { !Sonje.estDue(it.carte.boite, it.carte.jourEcheance, aujourdHui) }
                    .thenBy { it.carte.forme.lowercase() }
            )
    }

    private fun ouvrirCasier(i: Int) {
        if (eventail != null) return
        val ctx = context ?: return

        val n = boite.combienDans(i)
        val dues = boite.duesDans(i)
        val acquis = i >= Sonje.BOITE_ACQUISE
        val titre = if (acquis) "Acquis" else "Revient dans ${rythmeLong(Sonje.INTERVALLES[i])}"
        val sousTitre = when {
            n == 0 -> "Aucune carte"
            dues > 0 -> "${if (n == 1) "1 carte" else "$n cartes"} · $dues à revoir"
            else -> if (n == 1) "1 carte" else "$n cartes"
        }
        val vide = when {
            acquis -> "Aucune carte n'est encore acquise. Une carte arrive ici après " +
                "six révisions réussies, la dernière à trois mois d'intervalle."
            i == 0 -> "Ce casier est vide pour le moment. Les cartes gagnées dans " +
                "les jeux arrivent ici, et y reviennent après une erreur."
            else -> "Ce casier est vide pour le moment. Les cartes y montent depuis " +
                "le casier précédent, une révision réussie à la fois."
        }

        // La face avant de la pile, ramenée dans les coordonnées de la racine :
        // c'est de là que les cartes partent, et là qu'elles reviennent.
        val depart = boite.faceDuCasier(i).apply {
            offset(boite.left.toFloat(), boite.top.toFloat())
        }

        val ev = EventailCasier(
            ctx, titre, sousTitre, vide, depart, Sonje.aujourdHui(),
            rendre = { c, largeur -> rendreRecto(c, largeur) },
            surCarte = { ouvrirCarte(it) },
            surFermeture = { fermerEventail() }
        ).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        racine.addView(ev)
        eventail = ev
        casierOuvert = i
        boite.eclairer(i)
        majRetour()
        ev.ouvrir()

        when {
            n == 0 -> ev.poserCartes(emptyList())
            contenusPrets -> ev.poserCartes(cartesDuCasier(i))
            // Sinon chargerContenus les posera à son retour.
        }
    }

    private fun fermerEventail() {
        eventail?.let { racine.removeView(it) }
        eventail = null
        casierOuvert = -1
        boite.eclairer(-1)
        majRetour()
    }

    private fun lancerRevision() {
        val ctx = requireContext().applicationContext
        val principal = Handler(Looper.getMainLooper())
        boite.isEnabled = false
        Thread {
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            Carnet.planifier(ctx)
            val file = Carnet.file(ctx)
            val ecrites = PreuveDeFrappe.ecritesDepuisLaDerniereFois(ctx, file.map { it.forme })
            ecrites.forEach { Carnet.noter(ctx, it, Verdict.EXACT) }
            val aDemander = file.filter { it.forme !in ecrites }
                .map { CarteCarnet.contenu(ctx, it) }
            principal.post {
                if (!isAdded) return@post
                boite.isEnabled = true
                VueSonje(
                    hote = racine,
                    paquet = aDemander,
                    monteesParLeClavier = ecrites.toList(),
                    surNotation = { forme, verdict -> Carnet.noter(ctx, forme, verdict) },
                    surFin = { if (isAdded) chargerEnFond() }
                ).ouvrir()
            }
        }.start()
    }

    /**
     * La vignette du carnet, rendue hors écran dans un bitmap de [cible] pixels
     * de large.
     *
     * Mesurée à la largeur qu'elle a dans la grille du carnet, et non à celle de
     * la cible : ses textes et son ornement sont proportionnés à cette taille, et
     * les bitmaps de cadre qu'[Ornement] met en cache par largeur sont ainsi
     * partagés avec la grille. Le dessin est ensuite réduit à la cible.
     */
    private fun rendreRecto(c: ContenuCarte, cible: Int): Bitmap {
        val d = resources.displayMetrics.density
        val largeur = (160 * d).toInt()
        val vue = CarteCarnet.vignette(requireContext(), c, largeur)
        vue.measure(
            View.MeasureSpec.makeMeasureSpec(largeur, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        vue.layout(0, 0, vue.measuredWidth, vue.measuredHeight)
        val echelle = cible / vue.measuredWidth.toFloat()
        val image = Bitmap.createBitmap(
            cible, (vue.measuredHeight * echelle).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
        )
        Canvas(image).apply {
            scale(echelle, echelle)
            vue.draw(this)
        }
        return image
    }

    private fun ouvrirCarte(contenu: ContenuCarte) {
        val ctx = context ?: return
        val d = resources.displayMetrics.density

        val voile = VoileGlissable(ctx) { sensX, sensY -> fermerCarte(sensX, sensY) }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xCC000000.toInt())
            isClickable = true
        }

        val defilement = ScrollView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply { setMargins((22 * d).toInt(), 0, (22 * d).toInt(), 0) }
            isVerticalScrollBarEnabled = false
        }
        val carte = CarteCarnet.complete(ctx, contenu)
        defilement.addView(carte)
        voile.addView(defilement)

        voile.setOnClickListener { fermerCarte() }
        carte.isClickable = true

        voileCarte = voile
        racine.addView(voile)
        majRetour()
        voile.alpha = 0f
        voile.animate().alpha(1f).setDuration(160).start()

        carte.cameraDistance = 9000f * d
        carte.rotationY = -85f
        carte.animate().rotationY(0f)
            .setDuration(if (contenu.rarete.distinguee) 470L else 360L)
            .setInterpolator(
                if (contenu.rarete.distinguee) OvershootInterpolator(1.4f)
                else DecelerateInterpolator()
            )
            // Comme dans le carnet : la carte suit la main une fois retournée,
            // pas avant, pour ne pas écrire dans `rotationY` pendant le
            // retournement. Le voile reprend le geste dès qu'il devient un
            // glissé, et le carton reçoit alors un `CANCEL` qui le relève.
            .withEndAction {
                Inclinaison.suivre(carte)
                (carte as? Carton)?.sensibleAuDoigt = true
            }
            .start()
    }

    /** [sensX] ou [sensY] non nul : la carte a été chassée d'un glissé, elle sort par ce côté. */
    private fun fermerCarte(sensX: Int = 0, sensY: Int = 0) {
        val voile = voileCarte ?: return
        voileCarte = null
        majRetour()
        if ((sensX != 0 || sensY != 0) && voile is ViewGroup && voile.childCount > 0) {
            voile.getChildAt(0).animate()
                .translationX(sensX * voile.width.toFloat())
                .translationY(sensY * voile.height.toFloat())
                .setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        }
        voile.animate().alpha(0f).setDuration(160)
            .withEndAction { racine.removeView(voile) }.start()
    }

    private companion object {
        /** Le côté d'un recto dans la boîte, en dp : environ une fois et demie la fente. */
        const val COTE_RECTO = 64f
    }
}
