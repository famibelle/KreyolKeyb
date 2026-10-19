package com.example.kreyolkeyboard.carnet

import com.example.kreyolkeyboard.R
import androidx.annotation.StringRes
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.kreyolkeyboard.TranslationDictionary
import com.example.kreyolkeyboard.applicatifDansLaLangue
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Le carnet : toutes les cartes gagnées, consultables en dehors d'une partie.
 *
 * C'est ce qui manquait à « Ce que vous avez gagné », qui repartait de zéro à
 * chaque grille et se lisait comme un journal. Ici la collection est
 * permanente, elle a un lieu, et chaque mot y est un objet.
 *
 * Quatre décisions d'écran :
 *
 * - **Un `DialogFragment` plein écran**, comme le Guide et À Propos : pas
 *   d'entrée au manifeste, pas de cycle de vie d'activité en plus, et surtout
 *   pas un cinquième onglet — la barre en porte quatre et `REAL_COUNT` pilote
 *   le modulo du pager cyclique.
 * - **Le chargement est fait sur un fil de fond.** Ouvrir le carnet demande de
 *   relever les rangs de fréquence (balayage de `creole_dict.json`, 1,27
 *   Mo) et de charger les phrases du corpus (188 Ko) : sur le fil
 *   principal, c'est un gel visible au moment précis où l'écran doit
 *   apparaître.
 * - **Une grille de vignettes, et la carte entière au toucher**, qui se
 *   retourne pour arriver. La vignette fait la collection, la carte fait la
 *   leçon ; tout mettre dans la vignette rendrait la grille illisible, tout
 *   mettre dans la carte supprimerait la collection.
 * - **La méthode est un objet, pas une pastille.** [BoiteLeitner] remplace le
 *   bouton « Réviser N cartes », qui disait le compte et rien d'autre : sept
 *   casiers de bois, les cartes dues soulevées dans leur fente, et le compte
 *   gravé sur une plaque de laiton. Les casiers se **consultent** ; la révision
 *   reste une cible unique, décidée par l'échéance. Laisser choisir son casier
 *   ferait réviser des cartes pas encore dues et fausserait le calendrier.
 * - **Un filtre par jeu, et seulement sur les jeux déjà joués.** Depuis que les
 *   sept alimentent le carnet, « d'où vient cette carte » est une vraie
 *   question ; mais afficher sept filtres à qui n'a joué qu'à un seul jeu
 *   transforme une collection en formulaire. Les filtres apparaissent au fur et
 *   à mesure que les jeux donnent des cartes, et la ligne disparaît tant qu'il
 *   n'y en a qu'un.
 */
class CarnetFragment : DialogFragment() {

    companion object {
        /**
         * Ce que le chargement attend au plus les paysages du premier écran,
         * en millisecondes : au-delà, la grille paraît quand même, et les
         * paysages en retard arrivent sur leur ciel comme les autres.
         */
        private const val ATTENTE_PAYSAGES = 600L

        /**
         * Ce que le chargement attend au plus les vignettes construites
         * d'avance ; au-delà, la grille construit elle-même celles qui manquent.
         */
        private const val ATTENTE_VIGNETTES = 1500L

        /** Les rangées de la vue Séries posées à chaque trame, une fois le premier écran rempli. */
        private const val ETAPES_PAR_TRAME = 3

        /** Les clés de la vue choisie, gardée à la rotation. */
        private const val ETAT_TRI = "tri"
        private const val ETAT_FILTRE = "filtre"
        private const val ETAT_RARETE = "filtreRarete"

        /**
         * Largeur visée pour une vignette, en dp — celle mesurée en portrait
         * sur un téléphone (deux colonnes sur ~393dp de large). Le nombre de
         * colonnes en dérive plutôt que de rester figé à deux : à l'italienne
         * la largeur disponible triple sans que la hauteur d'écran suive, et
         * des vignettes toujours carrées sur deux colonnes débordent en bas
         * de l'écran avant même de montrer le nom du mot.
         */
        private const val LARGEUR_CIBLE_VIGNETTE_DP = 165f

        /** La case d'une page de série, plus serrée qu'une vignette de la grille. */
        private const val LARGEUR_CASE_SERIE_DP = 110f

        /** Couleur des pages de l'album : un papier crème, pas le gris de l'écran. */
        private val PAPIER = Color.parseColor("#FFFBF2")
        /** La couverture de l'album, sur laquelle les pages sont posées. */
        private val COUVERTURE = Color.parseColor("#5D4037")
    }

    /**
     * Les quatre façons de regarder la collection.
     *
     * [ETAGERE] n'est pas un tri mais une disposition : elle range les cartes
     * par boîte de Leitner, casier par casier, avec les casiers vides. C'est le
     * seul endroit où la méthode se voit — la vignette porte déjà la boîte
     * d'*une* carte en six pastilles, mais rien ne disait où en était la
     * collection, ni combien de mots attendaient dans chaque casier.
     *
     * Elle recoupe désormais [BoiteLeitner], qui ouvre un casier à la fois, et
     * la redondance est voulue : l'étagère déplie les sept casiers d'un seul
     * tenant pour qu'on les compare, la boîte en ouvre un pour qu'on le lise.
     * Ce ne sont pas deux chemins vers le même écran mais deux questions,
     * « où en suis-je » et « qu'y a-t-il là-dedans ».
     *
     * [SERIES] est une disposition aussi : les pages d'album à emplacements
     * vides de [Series]. Voir [remplirSeries].
     */
    private enum class Tri(@StringRes val libelle: Int) {
        RECENT(R.string.tri_recent), ALPHA(R.string.tri_alpha), RARETE(R.string.tri_rarete),
        ETAGERE(R.string.tri_etagere), SERIES(R.string.tri_series)
    }

    private var tri = Tri.RECENT

    /** Change à chaque remplissage de la grille : voir [poserParEtapes]. */
    private var generationGrille = 0
    private var filtre: JeuCarte? = null

    /** Le palier touché dans la légende du résumé, `null` pour tous. */
    private var filtreRarete: Rarete? = null
    private var contenus: List<ContenuCarte> = emptyList()

    /**
     * Les vignettes déjà construites, par forme et par taille, reprises quand
     * la grille se refait (tri, filtre, étagère) au lieu d'être reconstruites.
     * Refaire les 64 vignettes d'un carnet bloquait l'écran 0,2 s à chaque
     * changement de tri (Galaxy A21s, 2026-10-12) : leur construction, texte
     * compris, pèse bien plus que leur déplacement. La référence au contenu
     * vérifie qu'on reprend la vignette de la même carte, pas d'une homonyme
     * d'un chargement précédent.
     */
    private val vignettesFaites = HashMap<String, Pair<ContenuCarte, View>>()

    /**
     * La carte entière ouverte, s'il y en a une : elle passe avant le casier.
     * On la feuillette dans l'ordre de l'écran d'où on l'a ouverte.
     */
    private var lecteur: LecteurCartes? = null

    /** Le défilement de la grille, et l'album qui le remplace sur tablette. */
    private lateinit var defilementGrille: ScrollView
    private lateinit var zoneAlbum: FrameLayout
    private var pagesAlbum: ViewPager2? = null

    private lateinit var racine: FrameLayout
    private lateinit var conteneurGrille: LinearLayout
    private lateinit var tvResume: TextView
    private lateinit var ligneTri: LinearLayout
    private lateinit var ligneJeux: LinearLayout
    private lateinit var defilementJeux: HorizontalScrollView

    private val accent = Carnet.COULEUR

    // ------------------------------------------------------------ la cession

    /**
     * Ce que le prochain scan doit lire : la demande de l'autre téléphone
     * (on donne), ou la remise qu'il a préparée pour nous (on reçoit).
     */
    private enum class Scan { DEMANDE, REMISE }

    private var scanEnCours = Scan.REMISE

    /** La carte qu'on s'apprête à céder, le temps de scanner la demande. */
    private var aCeder: ContenuCarte? = null

    /** « À remettre » : visible tant qu'une carte cédée n'a pas été dite arrivée. */
    private var boutonARemettre: TextView? = null

    private var vueCession: VueCession? = null

    private val lanceurScan = registerForActivityResult(ScanContract()) { resultat ->
        resultat.contents?.let { surScan(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        // La vue choisie survit à la rotation : tourner la tablette ramenait
        // « Séries » à « Récent » (vu sur tablette 10 pouces, 2026-10-19).
        savedInstanceState?.let { etat ->
            etat.getString(ETAT_TRI)?.let { n -> Tri.values().find { it.name == n } }?.let { tri = it }
            filtre = etat.getString(ETAT_FILTRE)?.let { n -> JeuCarte.values().find { it.name == n } }
            filtreRarete = etat.getString(ETAT_RARETE)?.let { n -> Rarete.values().find { it.name == n } }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ETAT_TRI, tri.name)
        outState.putString(ETAT_FILTRE, filtre?.name)
        outState.putString(ETAT_RARETE, filtreRarete?.name)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        val colonne = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        colonne.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(accent)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            // Le dialogue s'étend sous la barre d'état : le bandeau la colore
            // et son titre s'en écarte, au lieu de s'écrire sous l'heure.
            setOnApplyWindowInsetsListener { v, insets ->
                val haut = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
                } else {
                    @Suppress("DEPRECATION") insets.systemWindowInsetTop
                }
                v.setPadding(dp(16f), dp(14f) + haut, dp(16f), dp(14f))
                insets
            }
            addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                text = "📔  Sanblé"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            // Recevoir une carte qu'un autre téléphone cède : voir [Cession].
            addView(TextView(ctx).apply {
                text = getString(R.string.carnet_recevoir)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
                background = GradientDrawable().apply {
                    cornerRadius = 18f * d
                    setStroke(dp(1.5f), 0xAAFFFFFF.toInt())
                }
                isClickable = true
                setOnClickListener { montrerDemande() }
            })
            addView(TextView(ctx).apply {
                text = "✕"
                textSize = 22f
                setTextColor(Color.WHITE)
                setPadding(dp(20f), 0, dp(4f), 0)
                isClickable = true
                setOnClickListener { dismiss() }
            })
        })

        tvResume = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16f), dp(12f), dp(16f), dp(6f))
            textSize = 14f
            setTextColor(Color.parseColor("#424242"))
            text = getString(R.string.carnet_ouverture)
            // La légende des paliers se touche : voir [legende].
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
        }
        // Une carte cédée qui attend d'être scannée : un bandeau sous le titre,
        // et non un bouton de plus dans l'en-tête, où il écrasait « Mäi Carnet »
        // sur un écran de 360 dp. Voir [Cession].
        colonne.addView(TextView(ctx).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(assombrir(Carnet.COULEUR, 0.25f))
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            isClickable = true
            visibility = View.GONE
            setOnClickListener { montrerRemiseEnAttente() }
            boutonARemettre = this
        })
        colonne.addView(tvResume)


        // Le filtre par jeu défile : cinq jeux et un « Tous » ne tiennent pas
        // sur la largeur d'un téléphone, et une ligne qui se replie en deux
        // repousserait la collection hors de l'écran.
        ligneJeux = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12f), 0, dp(12f), dp(6f))
        }
        defilementJeux = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            addView(ligneJeux)
        }
        colonne.addView(defilementJeux)

        ligneTri = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12f), 0, dp(12f), dp(8f))
            visibility = View.GONE
        }
        Tri.values().forEach { t ->
            ligneTri.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4f), 0, dp(4f), 0) }
                setText(t.libelle)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
                tag = t
                isClickable = true
                setOnClickListener {
                    tri = t
                    surlignerTri()
                    remplirGrille()
                    // Une autre vue repart d'en haut : descendu dans les
                    // séries, « Étagère » s'ouvrait au milieu, sans son titre.
                    remonterEnHaut()
                }
            })
        }
        // La ligne de tri défile comme celle des jeux : « Étagère » est le
        // quatrième bouton, et quatre puces dépassent la largeur d'un petit
        // téléphone. Le `ScrollView` se replie tout seul quand `ligneTri` passe
        // en `GONE`, il n'y a donc rien de plus à piloter.
        colonne.addView(HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(ligneTri)
        })

        conteneurGrille = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, dp(12f), dp(24f))
            // L'ombre portée des cartes rares déborde de leur vignette : sans
            // ces deux drapeaux, elle est rognée au ras du cadre et le relief
            // disparaît exactement là où il devait se voir.
            clipToPadding = false
            clipChildren = false
            // Après chaque mise en page, et pas avant : sans position, toutes
            // les vignettes sembleraient à l'écran et prendraient leur couche.
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> couchesPresDeLEcran() }
        }
        defilementGrille = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            clipToPadding = false
            clipChildren = false
            addView(conteneurGrille)
            viewTreeObserver.addOnScrollChangedListener { couchesPresDeLEcran() }
        }
        colonne.addView(defilementGrille)

        zoneAlbum = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            visibility = View.GONE
        }
        colonne.addView(zoneAlbum)

        racine = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(colonne)
        }

        chargerEnFond()
        return racine
    }

    /**
     * Assemble les contenus hors du fil principal, puis rend la main.
     *
     * `chargerExemples` a son propre verrou et `lireRangs` balaye 1,27 Mo :
     * les deux sont faits ici, une fois, avant que quoi que ce soit
     * s'affiche.
     */
    /** [ouvrir] : la forme dont la carte s'ouvre une fois le carnet rechargé (une carte reçue). */
    private fun chargerEnFond(ouvrir: String? = null) {
        val ctx = requireContext().applicatifDansLaLangue()
        val principal = Handler(Looper.getMainLooper())
        // Le métal des vignettes se prépare pendant que le carnet se charge :
        // voir [Ornement.prechauffer]. Celui de la vue Séries ensuite, à ses propres tailles : sans eux, le
        // premier passage sur cette vue recalculait tout sur le fil principal.
        Thread({ Paysages.prechauffer() }, "prechauffe").start()
        val (colonnes, cote) = grilleDeVignettes()
        // Ce que le premier écran de la grille montrera, lu ici, sur le fil
        // principal : le tri et le filtre peuvent changer pendant le chargement.
        val triOuvert = tri
        val filtreOuvert = filtre
        val premierEcran = if (enAlbum() || triOuvert == Tri.ETAGERE || triOuvert == Tri.SERIES) 0
        else (resources.displayMetrics.heightPixels / cote.coerceAtLeast(1)) * colonnes
        val cotesSeries = Serie.values().map { caseDeSerie(it).second }.distinct() - cote
        // Le contexte de l'activité, pas celui de l'application : les vignettes
        // construites d'avance doivent être celles que la grille construirait.
        val ctxVues = requireContext()
        val album = enAlbum()
        Thread {
            Ornement.prechauffer(ctx, cote)
            cotesSeries.forEach { Ornement.prechauffer(ctx, it) }
        }.start()
        Thread {
            // Des chargements qui ne dépendent pas l'un de l'autre, menés de
            // front. L'un après l'autre, ils faisaient côté LuxKeyb l'essentiel
            // des 9,5 s d'« Ouverture du carnet… » sur un Galaxy A21s
            // (2026-10-12). Le kréyòl n'a ni familles de flexions ni
            // catégories grammaticales à charger.
            val deFront = listOf(
                Thread { TranslationDictionary.charger(ctx) },
                Thread { TranslationDictionary.chargerExemples(ctx) },
                Thread { Armorial.precharger(ctx) },
                Thread { Carnet.preparer(ctx) }
            )
            deFront.forEach { it.start() }
            deFront.forEach { it.join() }
            // Donner une échéance aux cartes qui n'en ont pas, ici et non à la
            // capture : c'est ce qui étale un carnet déjà rempli au lieu de le
            // rendre entièrement dû le même jour. Voir [Carnet.planifier].
            Carnet.planifier(ctx)
            val prets = Carnet.cartes(ctx).map { CarteCarnet.contenu(ctx, it) }
            // Pendant que le premier écran se peint ici, le fil principal
            // dessine une carte complète invisible : voir [LecteurCartes.prechauffer].
            prets.firstOrNull()?.let { premiere ->
                principal.post { if (isAdded) LecteurCartes.prechauffer(racine, premiere) }
            }
            // Les vignettes elles-mêmes se construisent sur le fil de rendu
            // pendant que le premier écran se peint : la grille n'a plus qu'à
            // les poser. Construites à son arrivée, sur le fil principal, les
            // 64 vignettes d'un carnet la figeaient 0,3 s au moment même où
            // elle paraissait (Galaxy A21s, 2026-10-12).
            val faites = java.util.concurrent.ConcurrentHashMap<String, Pair<ContenuCarte, View>>()
            val construites = java.util.concurrent.CountDownLatch(1)
            if (album) construites.countDown()
            else FilDeRendu.handler.post {
                try {
                    for (c in prets) {
                        faites["${c.carte.forme}|$cote"] = c to CarteCarnet.vignette(ctxVues, c, cote)
                    }
                } catch (e: Throwable) {
                    // La grille construira ce qui manque, comme avant.
                } finally {
                    construites.countDown()
                }
            }
            peindrePremierEcran(prets, filtreOuvert, triOuvert, premierEcran, cote)
            construites.await(ATTENTE_VIGNETTES, java.util.concurrent.TimeUnit.MILLISECONDS)
            principal.post {
                if (!isAdded) return@post
                contenus = prets
                vignettesFaites.clear()
                vignettesFaites.putAll(faites)
                ligneTri.visibility = if (prets.isEmpty()) View.GONE else View.VISIBLE
                construireFiltres()
                surlignerTri()
                remplirGrille()
                majARemettre()
                ouvrir?.let { f -> prets.firstOrNull { it.carte.forme == f }?.let { montrerCarte(it) } }
            }
        }.start()
    }

    /**
     * Les paysages des vignettes du premier écran, rendus avant que la grille
     * paraisse, sur le fil de chargement, qui attend au plus [ATTENTE_PAYSAGES] ms.
     *
     * Les vignettes demandaient leur paysage à leur premier dessin : la grille
     * paraissait, puis ses cartes se remplissaient sous les yeux, deux par
     * deux, une seconde durant (Galaxy A21s, 2026-10-12, filmé). Rendus ici,
     * sur les trois fils de [Paysages], ils arrivent avec la grille. Les
     * vignettes plus bas suivent le chemin ordinaire.
     */
    private fun peindrePremierEcran(
        prets: List<ContenuCarte>, filtre: JeuCarte?, tri: Tri, combien: Int, cote: Int
    ) {
        if (combien <= 0) return
        val visibles = filtre?.let { jeu -> prets.filter { jeu in it.carte.jeux } } ?: prets
        val premieres = ordonner(visibles, tri).take(combien)
        if (premieres.isEmpty()) return
        val restantes = java.util.concurrent.CountDownLatch(premieres.size)
        premieres.forEach { c ->
            try {
                CarteCarnet.preparerVignette(c, cote) { restantes.countDown() }
            } catch (e: Exception) {
                restantes.countDown()
            }
        }
        restantes.await(ATTENTE_PAYSAGES, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /**
     * L'état du carnet, remis dans la boîte.
     *
     * La boîte compte elle-même ce qui est dû, à partir des cartes qu'elle
     * affiche : c'est le seul moyen que sa plaque et ses cartes soulevées ne se
     * contredisent jamais. Un second décompte tiré de [Carnet.aRevoir]
     * annoncerait un jour quatre cartes là où aucun casier n'en soulève, et ce
     * serait lu comme un bogue et non comme une échéance.
     */

    /**
     * Les filtres, un par jeu qui a déjà donné une carte, plus « Tous ».
     *
     * Un seul jeu représenté ne mérite pas de filtre : le bouton « Tous » et le
     * bouton du jeu diraient la même chose.
     */
    private fun construireFiltres() {
        val ctx = context ?: return
        val d = resources.displayMetrics.density
        ligneJeux.removeAllViews()

        val presents = JeuCarte.values().filter { jeu ->
            contenus.any { jeu in it.carte.jeux }
        }
        if (presents.size < 2) {
            defilementJeux.visibility = View.GONE
            filtre = null
            return
        }
        defilementJeux.visibility = View.VISIBLE

        fun puce(libelle: String, jeu: JeuCarte?) = TextView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins((4 * d).toInt(), 0, (4 * d).toInt(), 0) }
            text = libelle
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
            tag = jeu
            isClickable = true
            setOnClickListener {
                filtre = jeu
                surlignerFiltres()
                remplirGrille()
                remonterEnHaut()
            }
        }

        ligneJeux.addView(puce(getString(R.string.carnet_tous), null))
        presents.forEach { ligneJeux.addView(puce("${it.emoji} ${it.libelle(ctx)}", it)) }
        if (filtre != null && filtre !in presents) filtre = null
        surlignerFiltres()
    }

    private fun surlignerFiltres() {
        val d = resources.displayMetrics.density
        for (i in 0 until ligneJeux.childCount) {
            val vue = ligneJeux.getChildAt(i) as TextView
            val jeu = vue.tag as? JeuCarte
            val actif = jeu == filtre
            // Chaque filtre prend la couleur de son jeu : c'est la même que
            // celle de la carte du hub et que celle du mot sur la vignette.
            val couleur = jeu?.couleur ?: accent
            vue.setTextColor(if (actif) Color.WHITE else couleur)
            vue.background = GradientDrawable().apply {
                cornerRadius = 20f * d
                setColor(if (actif) couleur else Color.WHITE)
                setStroke((1 * d).toInt(), couleur)
            }
        }
    }

    private fun surlignerTri() {
        for (i in 0 until ligneTri.childCount) {
            val vue = ligneTri.getChildAt(i) as TextView
            val actif = vue.tag == tri
            vue.setTextColor(if (actif) Color.WHITE else Color.parseColor("#616161"))
            vue.background = GradientDrawable().apply {
                cornerRadius = 20f * resources.displayMetrics.density
                setColor(if (actif) accent else Color.WHITE)
                setStroke(
                    (1 * resources.displayMetrics.density).toInt(),
                    if (actif) accent else Color.parseColor("#D0D0D0")
                )
            }
        }
    }

    /**
     * Seules les vignettes proches de l'écran se dessinent, chacune dans sa
     * couche graphique.
     *
     * La grille du téléphone ne recycle rien, et à chaque trame de défilement
     * Android rejoue le dessin de **toutes** ses vignettes, celles hors de
     * l'écran comprises : face, rayons, paysage, tracé, métal. Mesuré sur le
     * Galaxy A21s (2026-10-12), 26 à 28 ms par trame pour 16,7 ms de budget,
     * et une image sur deux à huit ratée, sur un carnet de 36 cartes ; le
     * coût grandit avec le carnet. Deux gestes le ramènent à 19 ms et à
     * moins d'une image ratée sur cent :
     *
     * - hors d'une bande d'un demi-écran de part et d'autre de ce qu'on voit,
     *   la vignette est `INVISIBLE` : elle garde sa place et ne coûte plus
     *   rien ;
     * - dans la bande, elle a sa couche, une image toute prête qu'Android
     *   pose sans rejouer le dessin. Chaque couche coûte environ 1 Mo de
     *   mémoire graphique : sur tout le carnet, l'appli finirait tuée, d'où
     *   la bande. Une couche sur chaque vignette donnait aussi 14 ms ; une
     *   couche dans la bande sans masquer le reste n'y changeait rien.
     */
    private fun couchesPresDeLEcran() {
        if (!::defilementGrille.isInitialized) return
        val hauteur = defilementGrille.height
        if (hauteur <= 0) return
        val haut = defilementGrille.scrollY - hauteur / 2
        val bas = defilementGrille.scrollY + hauteur + hauteur / 2
        fun parcourir(groupe: ViewGroup, decalage: Int, dansGroupe: Boolean) {
            for (i in 0 until groupe.childCount) {
                val v = groupe.getChildAt(i)
                val y = decalage + v.top
                val dedans = dansGroupe && y + v.height > haut && y < bas
                if (v is CarteOrnee) regler(v, dedans)
                else if (v is ViewGroup) parcourir(v, y, dedans)
            }
        }
        parcourir(conteneurGrille, 0, true)
    }

    private fun regler(v: CarteOrnee, dedans: Boolean) {
        val visibilite = if (dedans) View.VISIBLE else View.INVISIBLE
        val couche = if (dedans) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        if (v.visibility != visibilite) v.visibility = visibilite
        if (v.layerType != couche) v.setLayerType(couche, null)
    }

    /**
     * Ramène la grille en haut, élan compris.
     *
     * `scrollTo(0, 0)` seul ne suffisait pas : touché pendant que la liste
     * filait encore sur l'élan d'un glissé, il la remettait à 0, puis l'élan
     * reprenait et la ramenait au milieu de la nouvelle vue (vu sur un Galaxy
     * A21s le 2026-10-12, de 0 à 3 351 px dans la trame suivante). Un `fling`
     * de vitesse nulle remplace l'élan en cours et s'arrête là où il part :
     * il se lance donc après la remontée, sinon il ramène la liste où elle
     * était.
     */
    private fun remonterEnHaut() {
        defilementGrille.scrollTo(0, 0)
        defilementGrille.fling(0)
    }

    /** Les cartes dans l'ordre de la grille, pour les tris qui en ont un. */
    private fun ordonner(visibles: List<ContenuCarte>, tri: Tri): List<ContenuCarte> = when (tri) {
        Tri.ALPHA -> visibles.sortedBy { it.carte.forme.lowercase() }
        Tri.RARETE -> visibles.sortedWith(
            compareByDescending<ContenuCarte> { it.rarete.ordinal }
                .thenBy { it.carte.forme.lowercase() }
        )
        else -> visibles.sortedByDescending { it.carte.numero }
    }

    /** Le nombre de colonnes de la grille, et le côté d'une vignette en pixels. */
    private fun grilleDeVignettes(): Pair<Int, Int> {
        val d = resources.displayMetrics.density
        val gouttiere = (10 * d).toInt()
        val dispo = resources.displayMetrics.widthPixels - (12 * d).toInt() * 2
        val cible = (LARGEUR_CIBLE_VIGNETTE_DP * d).toInt()
        val colonnes = ((dispo + gouttiere) / (cible + gouttiere)).coerceAtLeast(2)
        return colonnes to (dispo - (colonnes - 1) * gouttiere) / colonnes
    }

    /**
     * La légende des paliers, chacun avec son compte : « ✦ 5 très rares ».
     *
     * Les symboles seuls ne disaient rien à qui ne les connaissait pas, et
     * « ✦ 5 » ne s'apprenait nulle part. Chaque groupe se touche : il ne
     * montre plus que ce palier, et le toucher à nouveau rend tout. Un groupe
     * ne se coupe jamais en fin de ligne, et chacun porte un fond léger qui dit
     * qu'il se touche ; le palier choisi prend sa couleur.
     */
    private fun legende(texte: android.text.SpannableStringBuilder, parRarete: Map<Rarete, Int>) {
        Rarete.values().reversed()
            .filter { (parRarete[it] ?: 0) > 0 }
            .forEachIndexed { i, palier ->
                if (i > 0) texte.append("  ")
                val n = parRarete[palier] ?: 0
                val groupe = "\u00A0${palier.symbole}\u00A0" +
                    resources.getQuantityString(palier.compte, n, n).replace(' ', '\u00A0') + "\u00A0"
                val debut = texte.length
                texte.append(groupe)
                val fin = texte.length
                val actif = palier == filtreRarete
                val drapeaux = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                texte.setSpan(object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: View) {
                        filtreRarete = if (filtreRarete == palier) null else palier
                        remplirGrille()
                    }
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        ds.isUnderlineText = false
                        ds.color = if (actif) Color.WHITE else Color.parseColor("#424242")
                    }
                }, debut, fin, drapeaux)
                texte.setSpan(
                    android.text.style.BackgroundColorSpan(
                        if (actif) palier.couleur else avecOpaciteRarete(palier.couleur, 0x26)
                    ), debut, fin, drapeaux
                )
                if (actif) texte.setSpan(android.text.style.StyleSpan(Typeface.BOLD), debut, fin, drapeaux)
            }
    }

    private fun avecOpaciteRarete(couleur: Int, alpha: Int) = (couleur and 0x00FFFFFF) or (alpha shl 24)

    private fun remplirGrille() {
        val ctx = context ?: return
        val d = resources.displayMetrics.density
        conteneurGrille.removeAllViews()
        generationGrille++

        if (contenus.isEmpty()) {
            tvResume.text = getString(R.string.carnet_vide)
            conteneurGrille.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (40 * d).toInt() }
                text = getString(R.string.carnet_vide_explication)
                textSize = 15f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
                setTextColor(Color.parseColor("#757575"))
                setPadding((24 * d).toInt(), 0, (24 * d).toInt(), 0)
            })
            return
        }

        val parJeu = filtre?.let { jeu -> contenus.filter { jeu in it.carte.jeux } }
            ?: contenus
        val parRarete = parJeu.groupingBy { it.rarete }.eachCount()
        // Un palier vidé par le filtre de jeu ne filtre plus rien.
        if (filtreRarete != null && (parRarete[filtreRarete] ?: 0) == 0) filtreRarete = null
        val visibles = filtreRarete?.let { r -> parJeu.filter { it.rarete == r } } ?: parJeu

        tvResume.text = android.text.SpannableStringBuilder().apply {
            val mots = resources.getQuantityString(R.plurals.mots, visibles.size, visibles.size)
            append(filtre?.let { getString(R.string.carnet_mots_dans, mots, it.libelle(ctx)) } ?: mots)
            append("\n")
            legende(this, parRarete)
        }

        // Largeur calculée, colonnes adaptées à ce qu'elle laisse : les
        // vignettes visent LARGEUR_CIBLE_VIGNETTE_DP et restent carrées à la
        // marge près, sinon leurs illustrations n'ont pas la même hauteur
        // d'une ligne à l'autre et la grille ondule. Fixer les colonnes à
        // deux, comme avant, grossissait les vignettes avec la largeur de
        // l'écran plutôt que d'en tenir compte : à l'italienne, sur un
        // téléphone, elles débordaient de la hauteur disponible et
        // masquaient jusqu'au nom du mot sans un défilement.
        val gouttiere = (10 * d).toInt()
        // Toute la largeur de l'écran : le carnet s'ouvre par-dessus les
        // onglets, rail compris, il n'a pas à en retirer la place. On n'en
        // retire que les marges de la grille, 12 dp de part et d'autre : en
        // ôter 24 laissait à droite une bande deux fois plus large qu'à gauche.
        val (colonnes, cote) = grilleDeVignettes()

        if (tri == Tri.ETAGERE) {
            // Le résumé change de sujet avec la disposition : par casiers, ce
            // qui compte n'est plus la rareté mais l'avancement, et le prix à
            // payer pour une carte acquise est ce qu'aucun écran ne disait.
            val acquises = visibles.count { it.carte.acquise }
            tvResume.text = resources.getQuantityString(
                R.plurals.carnet_acquis_sur, visibles.size, acquises, visibles.size
            )
            montrerAlbum(false)
            remplirEtagere(ctx, visibles, cote, colonnes)
            return
        }

        if (tri == Tri.SERIES) {
            // Les séries portent sur toute la collection : un filtre par jeu
            // viderait la série des sept jeux de six emplacements sur sept.
            montrerAlbum(false)
            defilementJeux.visibility = View.GONE
            remplirSeries(ctx)
            return
        }
        // Le filtre revient en quittant les séries, s'il a lieu d'être.
        if (defilementJeux.visibility == View.GONE && ligneJeux.childCount > 0) {
            defilementJeux.visibility = View.VISIBLE
        }

        val ordonnes = ordonner(visibles, tri)
        if (enAlbum()) {
            montrerAlbum(true)
            quandMesuree(zoneAlbum) { if (isAdded) construireAlbum(ordonnes) }
            return
        }
        montrerAlbum(false)
        emettreVignettes(ctx, ordonnes, cote, colonnes, ordre = ordonnes)
    }

    // ---------------------------------------------------------------- l'album

    /**
     * Sur tablette, la collection se feuillette comme un album au lieu de
     * défiler : des pages qui tiennent chacune sur l'écran, deux à deux quand
     * l'écran est couché, et qu'on tourne d'un glissé.
     *
     * Le défilement d'une grille convient au téléphone, où l'on cherche une
     * carte. Sur un grand écran posé devant soi, on regarde sa collection, et
     * c'est la page qui en fait un objet : une place pour chaque carte, celles
     * de la dernière page qui attendent encore la leur, et le nombre de pages
     * qui dit à lui seul l'ampleur de ce qu'on a gagné.
     *
     * L'étagère garde son défilement : ses casiers sont des chapitres de
     * longueurs inégales, que des pages fixes couperaient n'importe où.
     */
    private fun enAlbum(): Boolean = resources.configuration.smallestScreenWidthDp >= 600

    /**
     * Lance [action] une fois que [vue] a une taille : la zone de l'album sort
     * de `GONE` au moment où on la remplit, et ses pages se calculent sur sa
     * hauteur, qu'elle n'a pas encore.
     */
    private fun quandMesuree(vue: View, action: () -> Unit) {
        if (vue.width > 0 && vue.height > 0 && !vue.isLayoutRequested) {
            action()
            return
        }
        vue.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int
            ) {
                if (v.width <= 0 || v.height <= 0) return
                v.removeOnLayoutChangeListener(this)
                // Hors de la passe de mise en page en cours : l'album y ajoute
                // des vues.
                v.post(action)
            }
        })
    }

    private fun montrerAlbum(oui: Boolean) {
        defilementGrille.visibility = if (oui) View.GONE else View.VISIBLE
        zoneAlbum.visibility = if (oui) View.VISIBLE else View.GONE
        if (!oui) {
            zoneAlbum.removeAllViews()
            pagesAlbum = null
        }
    }

    private fun construireAlbum(liste: List<ContenuCarte>) {
        val ctx = context ?: return
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        zoneAlbum.removeAllViews()

        val largeur = zoneAlbum.width
        val hauteur = zoneAlbum.height
        if (largeur <= 0 || hauteur <= 0) return

        // Deux pages côte à côte dès que l'écran est plus large que haut.
        val doublePage = largeur > hauteur
        val marge = dp(16f)
        val reliure = dp(14f)
        // Le carnet s'étend sous la barre de navigation : le pied s'en écarte,
        // sinon ses flèches passent sous les boutons du système.
        val insets = zoneAlbum.rootWindowInsets
        val barreNavigation = when {
            insets == null -> 0
            android.os.Build.VERSION.SDK_INT >= 30 ->
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            else -> @Suppress("DEPRECATION") insets.systemWindowInsetBottom
        }
        val hauteurPied = dp(48f) + barreNavigation
        val largeurPage = if (doublePage) (largeur - 2 * marge - 2 * reliure - reliure) / 2
            else largeur - 2 * marge - 2 * reliure
        val hauteurPage = hauteur - hauteurPied - 2 * reliure - dp(8f)

        // Les vignettes visent la taille de la grille, puis s'ajustent pour
        // remplir la page exactement : ni bande vide, ni rangée coupée.
        val interieur = dp(18f)
        val gouttiere = dp(12f)
        val utileL = largeurPage - 2 * interieur
        val utileH = hauteurPage - 2 * interieur - dp(20f)
        // Parmi les grilles possibles, la plus garnie dont les vignettes ne
        // descendent pas sous 80 % de leur taille de grille ; à nombre égal,
        // la plus grande. Arrondir chaque côté à part laissait un tiers de la
        // page vide, faute de place pour une rangée entière à pleine taille.
        // À 85 %, une tablette de 10 pouces couchée perdait sa troisième
        // rangée pour quelques pixels et gardait une bande vide sous la deuxième.
        val plancher = LARGEUR_CIBLE_VIGNETTE_DP * d * 0.80f
        fun coteDe(c: Int, r: Int) = minOf(
            (utileL - (c - 1) * gouttiere) / c,
            (utileH - (r - 1) * gouttiere) / r
        )
        val (colonnes, rangees) = (2..8).flatMap { c -> (2..6).map { r -> c to r } }
            .filter { (c, r) -> coteDe(c, r) >= plancher }
            .maxWithOrNull(compareBy<Pair<Int, Int>>({ it.first * it.second }, { coteDe(it.first, it.second) }))
            ?: (2 to 2)
        val cote = coteDe(colonnes, rangees)
        val parPage = colonnes * rangees
        val pages = liste.chunked(parPage).ifEmpty { listOf(emptyList()) }
        val parVue = if (doublePage) 2 else 1
        val vues = (pages.size + parVue - 1) / parVue

        val pied = TextView(ctx).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#616161"))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }

        val pager = ViewPager2(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                hauteur - hauteurPied
            )
            offscreenPageLimit = 1
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = vues
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
                    object : RecyclerView.ViewHolder(FrameLayout(ctx).apply {
                        layoutParams = RecyclerView.LayoutParams(
                            RecyclerView.LayoutParams.MATCH_PARENT,
                            RecyclerView.LayoutParams.MATCH_PARENT
                        )
                    }) {}
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val cadre = holder.itemView as FrameLayout
                    cadre.removeAllViews()
                    cadre.addView(doublePageDeLAlbum(
                        ctx, pages, position * parVue, parVue, liste,
                        largeurPage, hauteurPage, reliure, interieur, gouttiere,
                        cote, colonnes, rangees
                    ), FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    ))
                }
            }
            // On tourne la page autour de la reliure, et non en la faisant
            // glisser comme un écran : c'est tout ce qui distingue un album
            // d'une liste qui défile de côté.
            setPageTransformer { page, position ->
                page.cameraDistance = 20000f * d
                page.pivotY = page.height / 2f
                page.pivotX = if (position < 0) page.width.toFloat() else 0f
                page.rotationY = (position * 55f).coerceIn(-90f, 90f)
                page.alpha = 1f - (kotlin.math.abs(position) * 0.4f).coerceAtMost(1f)
            }
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val premiere = position * parVue + 1
                    val derniere = minOf(premiere + parVue - 1, pages.size)
                    val numeros = if (derniere > premiere) "$premiere–$derniere" else "$premiere"
                    pied.text = "$numeros / ${pages.size}"
                }
            })
        }
        pagesAlbum = pager
        zoneAlbum.addView(pager)

        // Le pied : le numéro des pages, entre deux flèches qui les tournent
        // aussi, pour qui ne pense pas à glisser.
        fun fleche(signe: String, @StringRes description: Int, pas: Int) = TextView(ctx).apply {
            text = signe
            textSize = 26f
            setTextColor(Carnet.COULEUR)
            gravity = Gravity.CENTER
            contentDescription = getString(description)
            layoutParams = LinearLayout.LayoutParams(dp(56f), dp(48f))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val cible = (pager.currentItem + pas).coerceIn(0, vues - 1)
                pager.setCurrentItem(cible, true)
            }
        }
        zoneAlbum.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(48f), Gravity.BOTTOM
            ).apply { bottomMargin = barreNavigation }
            addView(fleche("‹", R.string.album_page_precedente, -1))
            addView(pied, LinearLayout.LayoutParams(dp(140f), dp(48f)))
            addView(fleche("›", R.string.album_page_suivante, 1))
        })
        pied.text = "${if (doublePage && pages.size > 1) "1–2" else "1"} / ${pages.size}"
    }

    /**
     * Une vue de l'album : une page, ou deux posées sur la couverture de part
     * et d'autre de la reliure.
     */
    private fun doublePageDeLAlbum(
        ctx: Context,
        pages: List<List<ContenuCarte>>,
        premiere: Int,
        parVue: Int,
        ordre: List<ContenuCarte>,
        largeurPage: Int,
        hauteurPage: Int,
        reliure: Int,
        interieur: Int,
        gouttiere: Int,
        cote: Int,
        colonnes: Int,
        rangees: Int
    ): View {
        val d = resources.displayMetrics.density
        val couverture = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(reliure, reliure, reliure, reliure)
            background = GradientDrawable().apply {
                cornerRadius = 16f * d
                setColor(COUVERTURE)
            }
            elevation = 6f * d
        }
        for (k in 0 until parVue) {
            val indice = premiere + k
            val gauche = parVue == 2 && k == 0
            val page = if (indice < pages.size) {
                pageDeLAlbum(ctx, pages[indice], indice + 1, ordre, largeurPage, hauteurPage,
                    interieur, gouttiere, cote, colonnes, rangees,
                    derniere = indice == pages.lastIndex,
                    ombre = if (parVue == 2) (if (gauche) Gravity.END else Gravity.START) else null)
            } else {
                // Le verso blanc d'une dernière page de droite.
                View(ctx).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = 8f * d
                        setColor(PAPIER)
                    }
                }
            }
            couverture.addView(page, LinearLayout.LayoutParams(largeurPage, hauteurPage).apply {
                if (gauche) rightMargin = reliure
            })
        }
        return couverture
    }

    /**
     * Une page : les vignettes à leur place, des pochettes vides pour celles
     * qui restent à gagner sur la dernière, et le numéro de la page en bas.
     */
    private fun pageDeLAlbum(
        ctx: Context,
        cartes: List<ContenuCarte>,
        numero: Int,
        ordre: List<ContenuCarte>,
        largeurPage: Int,
        hauteurPage: Int,
        interieur: Int,
        gouttiere: Int,
        cote: Int,
        colonnes: Int,
        rangees: Int,
        derniere: Boolean,
        ombre: Int?
    ): View {
        val d = resources.displayMetrics.density
        val page = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = 8f * d
                setColor(PAPIER)
            }
            clipChildren = false
        }

        val grille = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }
        for (r in 0 until rangees) {
            val ligne = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
            }
            for (k in 0 until colonnes) {
                val i = r * colonnes + k
                val parametres = LinearLayout.LayoutParams(cote, cote).apply {
                    if (k < colonnes - 1) rightMargin = gouttiere
                }
                val c = cartes.getOrNull(i)
                when {
                    c != null -> ligne.addView(CarteCarnet.vignette(ctx, c, cote).apply {
                        isClickable = true
                        setOnClickListener { montrerCarte(c, ordre) }
                    }, parametres)
                    // Sur la dernière page seulement : les places qui restent
                    // à prendre. Ailleurs une page est pleine par construction.
                    derniere && cartes.isNotEmpty() -> ligne.addView(View(ctx).apply {
                        background = GradientDrawable().apply {
                            cornerRadius = 10f * d
                            setStroke((2 * d).toInt(), Color.parseColor("#DCD0B8"), 9f * d, 6f * d)
                        }
                    }, parametres)
                    else -> ligne.addView(View(ctx), parametres)
                }
            }
            grille.addView(ligne, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (r < rangees - 1) bottomMargin = gouttiere })
        }
        page.addView(grille, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            // Centrée dans ce qui reste au-dessus du numéro de page : le reste
            // se partage en haut et en bas au lieu de tomber sous la grille.
            topMargin = interieur
            bottomMargin = interieur + (20 * d).toInt()
        })

        // Le creux de la reliure : une ombre douce le long du bord intérieur.
        if (ombre != null) {
            page.addView(View(ctx).apply {
                background = GradientDrawable(
                    if (ombre == Gravity.END) GradientDrawable.Orientation.LEFT_RIGHT
                    else GradientDrawable.Orientation.RIGHT_LEFT,
                    intArrayOf(Color.TRANSPARENT, Color.parseColor("#26000000"))
                )
            }, FrameLayout.LayoutParams(
                (22 * d).toInt(), FrameLayout.LayoutParams.MATCH_PARENT, ombre
            ))
        }

        page.addView(TextView(ctx).apply {
            text = "$numero"
            textSize = 12f
            setTextColor(Color.parseColor("#A1887F"))
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = (8 * d).toInt() })
        return page
    }

    /**
     * La collection rangée par casier, casiers vides compris.
     *
     * Les sept casiers sont posés même quand ils sont vides, et c'est tout
     * l'intérêt : un casier vide est encore un casier, il montre le chemin qui
     * reste. Les masquer donnerait une liste de trois lignes qui ne dit plus
     * qu'il y a une échelle.
     *
     * À l'intérieur d'un casier, l'ordre est alphabétique. Ni la capture ni la
     * rareté n'ont de sens ici — le casier *est* déjà l'ordre, celui de la
     * mémoire, et on vient y chercher un mot précis.
     */
    private fun remplirEtagere(
        ctx: Context,
        visibles: List<ContenuCarte>,
        cote: Int,
        colonnes: Int
    ) {
        val aujourdHui = Sonje.aujourdHui()
        val parCasier = visibles.groupBy {
            it.carte.boite.coerceIn(0, Sonje.BOITE_ACQUISE)
        }
        val casiers = (0..Sonje.BOITE_ACQUISE).map { boite ->
            parCasier[boite].orEmpty().sortedBy { it.carte.forme.lowercase() }
        }
        // On feuillette l'étagère d'un casier au suivant, sans s'arrêter aux
        // cloisons.
        val ordre = casiers.flatten()
        casiers.forEachIndexed { boite, dedans ->
            val dues = dedans.count {
                Sonje.estDue(it.carte.boite, it.carte.jourEcheance, aujourdHui)
            }
            conteneurGrille.addView(etiquetteCasier(ctx, boite, dedans.size, dues))
            emettreVignettes(ctx, dedans, cote, colonnes, ordre = ordre)
        }
    }

    /**
     * L'étiquette d'un casier, et sa planche.
     *
     * Le libellé dit le **rythme**, jamais le numéro de boîte : « revu chaque
     * semaine » se comprend sans rien savoir de Leitner, « boîte 2 » demande
     * qu'on ait lu la documentation. La planche, elle, est la barre de six
     * segments que chaque vignette porte déjà sur son cadre (voir
     * [Ornement.dessinerBoite]) — le casier et les cartes qu'on y trouve
     * disent donc la même chose de la même façon, ce qui est la seule raison de
     * ne pas avoir inventé un autre indicateur ici.
     */


    /**
     * Les séries, page après page : un titre et son compte, puis la grille où
     * les cartes gagnées occupent leur place et les autres attendent la leur.
     *
     * Une carte se feuillette parmi les cartes de sa série. Un emplacement
     * rempli par plusieurs cartes (un dessin partagé) montre la première
     * gagnée : les autres sont dans le carnet, la série n'a qu'une place.
     */
    /**
     * Le nombre de colonnes d'une série dans la vue Séries, et le côté de ses
     * cases en pixels. Lu aussi par [chargerEnFond], qui prépare d'avance le
     * métal des vignettes à ces tailles.
     */
    private fun caseDeSerie(serie: Serie): Pair<Int, Int> {
        val d = resources.displayMetrics.density
        val gouttiere = (8 * d).toInt()
        val dispo = resources.displayMetrics.widthPixels - (12 * d).toInt() * 2
        val colonnes = ((dispo + gouttiere) / ((LARGEUR_CASE_SERIE_DP * d).toInt() + gouttiere))
            .coerceAtLeast(3)
        // Les paysages : une rangée par lieu, du matin à la nuit, ou deux
        // lieux par rangée quand l'écran les tient (tablette couchée, où
        // quatre cases de 600 px étalaient l'album sur neuf écrans).
        val colonnesSerie = when {
            serie == Serie.PAYSAGES -> if (colonnes >= 8) 8 else 4
            serie != Serie.NOMBRES -> colonnes
            colonnes >= 10 -> 10
            else -> 5
        }
        return colonnesSerie to (dispo - (colonnesSerie - 1) * gouttiere) / colonnesSerie
    }

    private fun remplirSeries(ctx: Context) {
        val d = resources.displayMetrics.density
        val gouttiere = (8 * d).toInt()
        // Une page d'album serre ses cases : à la largeur des vignettes de la
        // grille, les cent quatre dessins feraient cinquante rangées. Trois
        // colonnes au moins sur un téléphone, plus sur un écran large.
        val dispo = resources.displayMetrics.widthPixels - (12 * d).toInt() * 2
        val colonnes = ((dispo + gouttiere) / ((LARGEUR_CASE_SERIE_DP * d).toInt() + gouttiere))
            .coerceAtLeast(3)
        val cote = (dispo - (colonnes - 1) * gouttiere) / colonnes
        val series = Series.toutes(ctx, contenus)
        val remplis = series.sumOf { it.remplis }
        val total = series.sumOf { it.total }
        tvResume.text = getString(R.string.series_resume, remplis, total)

        // Chaque titre et chaque rangée est une étape : voir [poserParEtapes].
        val etapes = mutableListOf<Pair<Int, () -> Unit>>()
        series.forEach { a ->
            etapes += (40 * d).toInt() to { conteneurGrille.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, (16 * d).toInt(), 0, (10 * d).toInt()) }
                addView(TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = getString(a.serie.titre)
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(accent)
                })
                addView(TextView(ctx).apply {
                    text = if (a.complete) getString(R.string.serie_complete_court)
                    else getString(R.string.serie_progression, a.remplis, a.total)
                    textSize = 13f
                    setTypeface(null, if (a.complete) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(if (a.complete) accent else Color.parseColor("#616161"))
                })
            }) }

            // Les cent un nombres se rangent par dizaines : cinq colonnes, une
            // dizaine sur deux rangées, ou dix quand l'écran les tient. Une
            // rangée de trois couperait 20 de 21 et mêlerait les dizaines.
            val (colonnesSerie, coteSerie) = caseDeSerie(a.serie)
            val ordre = a.emplacements.mapNotNull { it.cartes.firstOrNull() }
            a.emplacements.chunked(colonnesSerie).forEach { rangee ->
                etapes += (coteSerie + gouttiere) to {
                    val ligne = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        clipChildren = false
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = gouttiere }
                    }
                    conteneurGrille.addView(ligne)
                    rangee.forEachIndexed { posColonne, e ->
                        val premiere = e.cartes.firstOrNull()
                        val vue = if (premiere != null) {
                            CarteCarnet.vignette(ctx, premiere, coteSerie).apply {
                                isClickable = true
                                setOnClickListener { montrerCarte(premiere, ordre) }
                            }
                        } else {
                            EmplacementVide(ctx, a.serie, e.cle)
                        }
                        ligne.addView(vue.apply {
                            layoutParams = LinearLayout.LayoutParams(
                                coteSerie, LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { if (posColonne != colonnesSerie - 1) rightMargin = gouttiere }
                        })
                    }
                }
            }
        }
        poserParEtapes(etapes)
    }

    /**
     * Pose les [etapes] de la vue Séries : celles qui remplissent le premier
     * écran tout de suite, les autres par paquets de [ETAPES_PAR_TRAME], une
     * trame après l'autre. Chaque étape annonce la hauteur qu'elle ajoute.
     *
     * Les 248 emplacements construits d'un coup, 53 cartes et 195 cases vides,
     * figeaient l'écran 0,7 s au passage sur la vue (Galaxy A21s, 2026-10-12) :
     * 0,26 s à les construire, 0,22 s à les mesurer et les poser. Le premier
     * écran n'en demande qu'une vingtaine ; le reste arrive pendant qu'on
     * regarde, bien avant qu'on ait défilé jusque-là. Changer de vue entre-
     * temps abandonne ce qui restait : [generationGrille] a changé.
     */
    private fun poserParEtapes(etapes: List<Pair<Int, () -> Unit>>) {
        val generation = generationGrille
        val ecran = resources.displayMetrics.heightPixels
        var hauteur = 0
        var i = 0
        while (i < etapes.size && hauteur < ecran) {
            hauteur += etapes[i].first
            etapes[i].second()
            i++
        }
        fun suite(depuis: Int) {
            if (depuis >= etapes.size) return
            conteneurGrille.post {
                if (generation != generationGrille || !isAdded) return@post
                val jusqua = minOf(depuis + ETAPES_PAR_TRAME, etapes.size)
                for (k in depuis until jusqua) etapes[k].second()
                suite(jusqua)
            }
        }
        suite(i)
    }

    /** Referme la carte ouverte. Rend `false` s'il n'y en avait aucune. */
    private fun fermerCarte(): Boolean {
        val l = lecteur?.takeIf { it.ouvert } ?: return false
        l.fermer()
        return true
    }

    /** Les vignettes d'une liste, deux par ligne, dans [hote]. */
    private fun emettreVignettes(
        ctx: Context,
        liste: List<ContenuCarte>,
        cote: Int,
        colonnes: Int,
        hote: LinearLayout = conteneurGrille,
        ordre: List<ContenuCarte> = liste
    ) {
        val d = resources.displayMetrics.density
        val gouttiere = (10 * d).toInt()
        var ligne: LinearLayout? = null
        liste.forEachIndexed { i, c ->
            val posColonne = i % colonnes
            if (posColonne == 0) {
                ligne = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = gouttiere }
                }
                hote.addView(ligne)
            }
            ligne?.addView(vignette(ctx, c, cote).apply {
                layoutParams = LinearLayout.LayoutParams(
                    cote, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (posColonne != colonnes - 1) rightMargin = gouttiere }
                isClickable = true
                setOnClickListener { montrerCarte(c, ordre) }
            })
        }
    }

    /** La vignette de [c] à la taille [cote], reprise si elle existe déjà : voir [vignettesFaites]. */
    private fun vignette(ctx: Context, c: ContenuCarte, cote: Int): View {
        val cle = "${c.carte.forme}|$cote"
        val faite = vignettesFaites[cle]?.takeIf { it.first === c }?.second
        if (faite != null) {
            (faite.parent as? ViewGroup)?.removeView(faite)
            return faite
        }
        return CarteCarnet.vignette(ctx, c, cote).also { vignettesFaites[cle] = c to it }
    }

    /** La carte entière, feuilletable dans l'ordre de l'écran d'où on l'ouvre : voir [LecteurCartes]. */
    private fun montrerCarte(c: ContenuCarte, ordre: List<ContenuCarte> = listOf(c)) {
        if (context == null) return
        val feuillet = ordre.ifEmpty { listOf(c) }
        lecteur = LecteurCartes(
            racine, feuillet, feuillet.indexOf(c).coerceAtLeast(0),
            surFermeture = { lecteur = null },
            surCeder = { proposerCession(it) }
        ).also { it.ouvrir() }
    }

    /** Explique l'échange, puis ouvre le scanner sur la demande de l'autre téléphone. */
    private fun proposerCession(c: ContenuCarte) {
        val ctx = context ?: return
        android.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.ceder_titre, c.carte.forme))
            .setMessage(R.string.ceder_message)
            .setPositiveButton(R.string.ceder_oui) { _, _ ->
                aCeder = c
                scanner(Scan.DEMANDE)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun cession(): VueCession = vueCession ?: VueCession(racine).also { vueCession = it }

    /**
     * Ouvre le scanner. Un build de debug lit plutôt `files/scan_debug.txt`
     * s'il existe : l'émulateur n'a pas de caméra qui voie un autre écran.
     * Jamais en release.
     */
    private fun scanner(mode: Scan) {
        val ctx = context ?: return
        scanEnCours = mode
        if (com.example.kreyolkeyboard.BuildConfig.DEBUG) {
            val essai = java.io.File(ctx.filesDir, "scan_debug.txt")
            if (essai.exists()) {
                val texte = essai.readText()
                essai.delete()
                surScan(texte)
                return
            }
        }
        // Android demande l'accès en disant « prendre des photos et
        // enregistrer des vidéos », ce qui inquiète à juste titre. Une phrase
        // dit avant à quoi il sert, comme l'écran qui précède la dictée ; elle
        // ne revient plus une fois l'accès donné.
        val accorde = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (accorde) return lancerScan()
        android.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.camera_pourquoi_titre)
            .setMessage(R.string.camera_pourquoi_message)
            .setPositiveButton(R.string.camera_pourquoi_ok) { _, _ -> lancerScan() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun lancerScan() {
        lanceurScan.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.scan_invite))
            setBeepEnabled(false)
            setOrientationLocked(false)
        })
    }

    private fun surScan(texte: String) {
        when (scanEnCours) {
            Scan.DEMANDE -> ceder(texte)
            Scan.REMISE -> recevoir(texte)
        }
    }

    private fun dire(message: String) {
        val ctx = context ?: return
        android.widget.Toast.makeText(ctx, message, android.widget.Toast.LENGTH_LONG).show()
    }

    /**
     * Le donneur a scanné la demande de l'autre téléphone : la carte quitte
     * son carnet **tout de suite**, et la remise s'affiche. C'est le cœur de
     * la version 2 de [Cession] : la carte part avant d'arriver, donc elle
     * n'existe jamais des deux côtés.
     */
    private fun ceder(texte: String) {
        val ctx = context?.applicatifDansLaLangue() ?: return
        val c = aCeder ?: return
        aCeder = null
        if (Cession.estAncienneVersion(texte)) return dire(getString(R.string.cession_version))
        val jeton = Cession.lireDemande(texte) ?: return dire(getString(R.string.demande_invalide))
        val remise = Cession.Remise(jeton, c.carte.forme, c.carte.nombre, System.currentTimeMillis() / 1000)
        lecteur?.fermer()
        Thread {
            // La remise d'abord : si l'appli s'arrêtait entre les deux, mieux
            // vaut une carte encore au carnet et déjà remettable qu'une carte
            // sortie sans code pour la donner.
            Cession.ajouterRemise(ctx, remise)
            Carnet.retirer(ctx, remise.forme)
            PreuveDeFrappe.retirerPlume(ctx, remise.forme)
            Handler(Looper.getMainLooper()).post {
                if (!isAdded) return@post
                montrerRemise(remise)
                chargerEnFond()
            }
        }.start()
    }

    /** La remise d'une carte cédée, à faire scanner par le téléphone qui l'a demandée. */
    private fun montrerRemise(r: Cession.Remise) {
        val ctx = context ?: return
        cession().montrer(
            titre = getString(R.string.remise_titre, r.forme),
            etapes = getString(R.string.remise_etapes),
            code = Cession.remise(r),
            principal = getString(R.string.remise_faite),
            surPrincipal = {
                Cession.oublierRemise(ctx, r.jeton)
                vueCession?.fermer()
                majARemettre()
            },
            secondaire = getString(R.string.remise_plus_tard),
            surSecondaire = {
                vueCession?.fermer()
                majARemettre()
            }
        )
    }

    private fun montrerRemiseEnAttente() {
        val ctx = context ?: return
        Cession.remisesEnAttente(ctx).firstOrNull()?.let { montrerRemise(it) } ?: majARemettre()
    }

    private fun majARemettre() {
        val ctx = context ?: return
        val premiere = Cession.remisesEnAttente(ctx).firstOrNull()
        boutonARemettre?.apply {
            visibility = if (premiere != null) View.VISIBLE else View.GONE
            premiere?.let { text = getString(R.string.a_remettre_bandeau, it.forme) }
        }
    }

    /** Celui qui reçoit montre sa demande, puis scanne la remise que le donneur affichera. */
    private fun montrerDemande() {
        val ctx = context ?: return
        val jeton = Cession.nouvelleDemande(ctx)
        cession().montrer(
            titre = getString(R.string.demande_titre),
            etapes = getString(R.string.demande_etapes),
            code = Cession.demande(jeton),
            principal = getString(R.string.demande_scanner),
            surPrincipal = { scanner(Scan.REMISE) },
            secondaire = getString(android.R.string.cancel),
            surSecondaire = { vueCession?.fermer() }
        )
    }

    /**
     * Le receveur a scanné une remise : elle n'ouvre que si elle répond à une
     * demande de ce téléphone, et une seule fois.
     */
    private fun recevoir(texte: String) {
        val ctx = context?.applicatifDansLaLangue() ?: return
        if (Cession.estAncienneVersion(texte)) return dire(getString(R.string.cession_version))
        val r = Cession.lireRemise(texte) ?: return dire(getString(R.string.remise_invalide))
        if (Cession.dejaRecue(ctx, r.jeton)) return dire(getString(R.string.remise_deja, r.forme))
        if (!Cession.demandeEnAttente(ctx, r.jeton)) return dire(getString(R.string.remise_autre))
        if (!cartePossible(r)) return dire(getString(R.string.reception_impossible))
        Thread {
            Carnet.ajouter(ctx, r.forme, JeuCarte.CADEAU)
            Cession.noterRecue(ctx, r.jeton)
            Handler(Looper.getMainLooper()).post {
                if (!isAdded) return@post
                vueCession?.fermer()
                dire(getString(R.string.reception_titre, r.forme))
                chargerEnFond(ouvrir = r.forme)
            }
        }.start()
    }

    /**
     * Une remise ne peut porter qu'un mot que le carnet accepte : un mot
     * glosé, la règle de [Carnet.ajouter]. Rien ne la signe : ce contrôle
     * empêche de fabriquer une carte que le carnet refuserait, pas de
     * fabriquer une carte. Les deux téléphones ont la même table de gloses
     * (l'appli n'a qu'une langue), donc une carte cédée ne se perd pas en
     * route pour cette raison.
     */
    private fun cartePossible(r: Cession.Remise): Boolean =
        r.nombre == null && TranslationDictionary.estProposable(requireContext(), r.forme)

    /**
     * Le bouton Retour dépile : l'offre de cession, puis la carte ouverte,
     * puis le carnet.
     *
     * Il passait par `setOnKeyListener`, qui ne le reçoit plus depuis
     * Android 16 : une application qui vise l'API 36 a le retour prédictif
     * d'office, le système ne livre plus `KEYCODE_BACK` au dialogue, et Retour
     * fermait tout le carnet depuis une carte ouverte. Le rappel du
     * `OnBackPressedDispatcher` du `ComponentDialog` est appelé dans les deux
     * mondes, geste ou touche, ancien Android ou nouveau.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog =
        ComponentDialog(requireContext(), theme).also { d ->
            d.onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        vueCession?.ouverte == true -> {
                            // Une remise fermée reste sous « À remettre ».
                            vueCession?.fermer()
                            majARemettre()
                        }
                        fermerCarte() -> Unit
                        else -> dismiss()
                    }
                }
            })
        }

    override fun onStart() {
        super.onStart()
        // Sans cela le dialogue s'ajuste à son contenu et laisse l'activité
        // visible sur les bords : la collection mérite tout l'écran.
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Les flèches d'un clavier physique passent par les touches ; le bouton
        // Retour, lui, n'y passe plus : voir [onCreateDialog].
        dialog?.setOnKeyListener { _, code, evenement ->
            // Les flèches d'un clavier physique feuillettent : la carte
            // ouverte, sinon les pages de l'album.
            val pas = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                else -> 0
            }
            when {
                pas != 0 && lecteur != null -> {
                    if (evenement.action == KeyEvent.ACTION_DOWN) lecteur?.feuilleter(pas)
                    true
                }
                pas != 0 && pagesAlbum != null -> {
                    if (evenement.action == KeyEvent.ACTION_DOWN) {
                        pagesAlbum?.let { it.setCurrentItem(it.currentItem + pas, true) }
                    }
                    true
                }
                else -> false
            }
        }
    }
}
