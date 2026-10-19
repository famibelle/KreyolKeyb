package com.example.kreyolkeyboard.carnet

import com.example.kreyolkeyboard.R
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.kreyolkeyboard.TranslationDictionary
import com.example.kreyolkeyboard.applicatifDansLaLangue

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
class BoiteFragment : Fragment(), com.example.kreyolkeyboard.SettingsActivity.JeuAuClavier {

    private lateinit var racine: FrameLayout
    private lateinit var boite: BoiteLeitner
    private var eventail: EventailCasier? = null
    private var casierOuvert = -1
    private var lecteur: LecteurCartes? = null

    /** Le casier choisi aux flèches d'un clavier physique. -1 : aucun. */
    private var casierClavier = -1

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

    /**
     * La séance de révision en cours, qui survit à une rotation.
     *
     * Tourner l'écran le recrée, et la séance disparaissait avec lui. La
     * rouvrir en redemandant la file au carnet ne suffisait pas : les cartes
     * déjà notées n'y étaient plus, mais l'arriéré prenait leur place, et le
     * compteur repartait à 1 sur une séance qui ne finissait plus. On garde
     * donc la session elle-même, son paquet et ses réponses.
     */
    class Seance : androidx.lifecycle.ViewModel() {
        var ouverte = false
        var paquet: List<ContenuCarte> = emptyList()
        var monteesParLeClavier: List<String> = emptyList()
        var session: SessionSonje? = null
    }

    private val seance by lazy {
        androidx.lifecycle.ViewModelProvider(this)[Seance::class.java]
    }

    private val retour = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                lecteur != null -> lecteur?.fermer()
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

        // Une seule fois : l'argument est consommé, pour qu'un retour sur la
        // boîte (fin de séance, recréation) ne relance pas une séance de plus.
        // Rien à revoir, et c'est la boîte seule qui s'affiche.
        if (savedInstanceState == null && arguments?.getBoolean(ARG_REVISION_DIRECTE) == true) {
            arguments?.remove(ARG_REVISION_DIRECTE)
            if (Carnet.aRevoir(requireContext()) > 0) view.post { if (isAdded) lancerRevision() }
        } else if (seance.ouverte) {
            // Une séance était ouverte avant la rotation : on la reprend.
            view.post { if (isAdded) reprendreRevision() }
        }
    }

    private fun majRetour() {
        retour.isEnabled = lecteur != null || eventail != null
    }

    private fun chargerEnFond() {
        val ctx = requireContext().applicatifDansLaLangue()
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
        val ctx = requireContext().applicatifDansLaLangue()
        Thread {
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            val tous = cartes.map { CarteCarnet.contenu(ctx, it) }
            FilDeRendu.handler.post { poserContenus(tous, gen) }
        }.start()
    }

    /**
     * Rend les rectos posés sur les piles, sur le fil de rendu (appelé par
     * [chargerContenus]), puis remet tout au fil principal. Ils se rendaient à
     * l'arrivée sur le fil principal, qu'ils bloquaient. À la taille où la
     * boîte les montre : sur tablette la face d'un casier fait le double des
     * 64 dp, et un recto agrandi y floutait.
     */
    private fun poserContenus(tous: List<ContenuCarte>, gen: Int) {
        val ctx = context ?: return
        val parForme = tous.associateBy { it.carte.forme }
        val rectos = try {
            val cote = maxOf(
                (COTE_RECTO * ctx.resources.displayMetrics.density).toInt(),
                boite.faceDuCasier(0).width().toInt()
            )
            boite.cartesVisibles()
                .mapNotNull { c ->
                    parForme[c.forme]?.let { contenu ->
                        c.forme to rendreRecto(contenu, cote) { refaireRectoDePile(contenu, cote, gen) }
                    }
                }
                .toMap()
        } catch (e: Throwable) {
            emptyMap()
        }
        activity?.runOnUiThread {
            if (!isAdded || gen != generation) return@runOnUiThread
            contenus.clear()
            tous.forEach { contenus[it.carte.forme] = it }
            contenusPrets = true
            boite.poserRectos(rectos)
            eventail?.let { ev ->
                if (ev.enAttente && casierOuvert >= 0) ev.poserCartes(cartesDuCasier(casierOuvert))
            }
        }
    }

    /**
     * Refait le recto d'une pile quand son paysage arrive. Comme l'éventail, la
     * boîte garde une photo de la vignette, qui sans cela restait au ciel seul
     * sur toutes les piles.
     */
    private fun refaireRectoDePile(c: ContenuCarte, cote: Int, gen: Int) {
        FilDeRendu.handler.post {
            val recto = try {
                rendreRecto(c, cote)
            } catch (e: Throwable) {
                return@post
            }
            activity?.runOnUiThread {
                if (isAdded && gen == generation) boite.poserRecto(c.carte.forme, recto)
            }
        }
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
        val titre = if (acquis) getString(R.string.boite_acquis)
        else getString(R.string.boite_revient_dans, rythmeLong(ctx, Sonje.INTERVALLES[i]))
        val sousTitre = when {
            n == 0 -> getString(R.string.casier_aucune_carte)
            dues > 0 -> getString(R.string.boite_a_revoir, resources.getQuantityString(R.plurals.cartes, n, n), dues)
            else -> resources.getQuantityString(R.plurals.cartes, n, n)
        }
        val vide = when {
            acquis -> getString(R.string.casier_vide_acquis)
            i == 0 -> getString(R.string.casier_vide_premier)
            else -> getString(R.string.casier_vide_suivant)
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
        FigerOrientation.tantQue(ev)
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
        boite.eclairer(casierClavier)
        majRetour()
    }

    private fun lancerRevision() {
        seance.ouverte = true
        val ctx = requireContext().applicatifDansLaLangue()
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
                seance.paquet = aDemander
                seance.monteesParLeClavier = ecrites.toList()
                seance.session = SessionSonje(aDemander)
                ouvrirRevision()
            }
        }.start()
    }

    /** Après une rotation : la même session, à la carte où elle en était. */
    private fun reprendreRevision() {
        if (seance.session == null) lancerRevision() else ouvrirRevision()
    }

    private fun ouvrirRevision() {
        val ctx = requireContext().applicatifDansLaLangue()
        VueSonje(
            hote = racine,
            paquet = seance.paquet,
            monteesParLeClavier = seance.monteesParLeClavier,
            surNotation = { forme, verdict -> Carnet.noter(ctx, forme, verdict) },
            surFin = {
                seance.ouverte = false
                seance.session = null
                seance.paquet = emptyList()
                seance.monteesParLeClavier = emptyList()
                if (isAdded) chargerEnFond()
            },
            session = seance.session ?: SessionSonje(seance.paquet)
        ).ouvrir()
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
    private fun rendreRecto(
        c: ContenuCarte,
        cible: Int,
        quandLieuArrive: () -> Unit = { eventail?.rafraichir(c.carte.forme) }
    ): Bitmap {
        val d = resources.displayMetrics.density
        // Jamais plus petite que la cible : réduire est net, agrandir floute,
        // et l'éventail d'une tablette demande des cartes de 220 dp.
        val largeur = maxOf((160 * d).toInt(), cible)
        // Appelée en fond (éventail, piles) : `context` et non `requireContext`,
        // l'appelant rattrape l'échec d'un fragment déjà détaché.
        val vue = CarteCarnet.vignette(context ?: error("fragment détaché"), c, largeur)
        // Le paysage d'une vignette arrive en différé : tant qu'il manque, la
        // photo n'a que le ciel. Quand il est là, l'éventail la refait, et le
        // paysage, désormais en cache, y entre. Régression de la 36.3.0.
        (vue as? CarteOrnee)?.quandLieuArrive = quandLieuArrive
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

    /**
     * La carte en grand, feuilletable dans l'ordre du casier ouvert : passer à
     * la voisine fait tourner l'éventail derrière, si bien qu'en reposant la
     * carte on retrouve la main là où la lecture s'est arrêtée.
     */
    private fun ouvrirCarte(contenu: ContenuCarte) {
        if (context == null) return
        val liste = if (casierOuvert >= 0) cartesDuCasier(casierOuvert) else listOf(contenu)
        val rang = liste.indexOfFirst { it.carte.forme == contenu.carte.forme }
        lecteur = LecteurCartes(
            racine,
            if (rang >= 0) liste else listOf(contenu),
            rang.coerceAtLeast(0),
            surChangement = { eventail?.centrerSur(it) },
            surFermeture = {
                lecteur = null
                majRetour()
            }
        ).also { it.ouvrir() }
        majRetour()
    }

    /**
     * Un clavier physique, sur une tablette à étui-clavier : ← → choisissent un
     * casier, Entrée l'ouvre (ou lance la révision si aucun n'est choisi), puis
     * ← → font tourner l'éventail et Entrée ouvre la carte du centre ; dans la
     * carte ouverte, ← → passent à la voisine. Échap remonte d'un cran, comme
     * le bouton retour. La séance de révision garde ses touches pour elle.
     */
    override fun surToucheClavier(event: KeyEvent): Boolean {
        if (seance.ouverte || !isResumed) return false
        val code = event.keyCode
        val sens = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            else -> 0
        }
        val valider = code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            code == KeyEvent.KEYCODE_SPACE || code == KeyEvent.KEYCODE_DPAD_CENTER
        val echap = code == KeyEvent.KEYCODE_ESCAPE
        if (sens == 0 && !valider && !echap) return false
        // La levée est consommée avec la descente, sans rien faire.
        if (event.action != KeyEvent.ACTION_DOWN) return true

        val l = lecteur
        val ev = eventail
        when {
            l != null -> when {
                sens != 0 -> l.feuilleter(sens)
                echap -> l.fermer()
            }
            ev != null -> when {
                sens != 0 -> ev.tourner(sens)
                valider -> ev.ouvrirCentre()
                echap -> ev.fermer()
            }
            else -> when {
                sens != 0 -> {
                    casierClavier = if (casierClavier < 0) (if (sens > 0) 0 else BoiteLeitner.CASIERS - 1)
                    else (casierClavier + sens).coerceIn(0, BoiteLeitner.CASIERS - 1)
                    boite.eclairer(casierClavier)
                }
                valider && casierClavier >= 0 -> ouvrirCasier(casierClavier)
                valider -> if (Carnet.aRevoir(requireContext()) > 0) lancerRevision()
                echap && casierClavier >= 0 -> {
                    casierClavier = -1
                    boite.eclairer(-1)
                }
                else -> return false
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        (activity as? com.example.kreyolkeyboard.SettingsActivity)?.jeuAuClavier = this
    }

    override fun onPause() {
        (activity as? com.example.kreyolkeyboard.SettingsActivity)?.let {
            if (it.jeuAuClavier === this) it.jeuAuClavier = null
        }
        super.onPause()
    }

    companion object {
        /** Le côté d'un recto dans la boîte, en dp : environ une fois et demie la fente. */
        private const val COTE_RECTO = 64f
        private const val ARG_REVISION_DIRECTE = "revision_directe"

        /**
         * La boîte, séance de Sonjé lancée dès l'ouverture. C'est ce qu'ouvre
         * « Réviser maintenant » sur l'accueil : l'utilisateur a déjà dit ce
         * qu'il voulait, lui refaire toucher le bouton de révision dans la
         * boîte serait une étape pour rien. La boîte reste derrière la séance
         * et reprend la main à la fin, comme après une révision lancée d'ici.
         */
        fun pourRevision(): BoiteFragment = BoiteFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_REVISION_DIRECTE, true) }
        }
    }
}
