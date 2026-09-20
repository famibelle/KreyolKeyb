package com.example.kreyolkeyboard.carnet

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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.kreyolkeyboard.TranslationDictionary

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
         * Largeur visée pour une vignette, en dp — celle mesurée en portrait
         * sur un téléphone (deux colonnes sur ~393dp de large). Le nombre de
         * colonnes en dérive plutôt que de rester figé à deux : à l'italienne
         * la largeur disponible triple sans que la hauteur d'écran suive, et
         * des vignettes toujours carrées sur deux colonnes débordent en bas
         * de l'écran avant même de montrer le nom du mot.
         */
        private const val LARGEUR_CIBLE_VIGNETTE_DP = 165f
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
     */
    private enum class Tri(val libelle: String) {
        RECENT("Récent"), ALPHA("A → Z"), RARETE("Rareté"), ETAGERE("Étagère")
    }

    private var tri = Tri.RECENT
    private var filtre: JeuCarte? = null
    private var contenus: List<ContenuCarte> = emptyList()

    /** La carte entière ouverte, s'il y en a une : elle passe avant le casier. */
    private var voileCarte: View? = null

    private lateinit var racine: FrameLayout
    private lateinit var conteneurGrille: LinearLayout
    private lateinit var tvResume: TextView
    private lateinit var ligneTri: LinearLayout
    private lateinit var ligneJeux: LinearLayout
    private lateinit var defilementJeux: HorizontalScrollView

    private val accent = Carnet.COULEUR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
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
            addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                text = "📔  Sanblé"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
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
            text = "Ouverture du carnet…"
        }
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
                text = t.libelle
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
                tag = t
                isClickable = true
                setOnClickListener {
                    tri = t
                    surlignerTri()
                    remplirGrille()
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
        }
        colonne.addView(ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            clipToPadding = false
            clipChildren = false
            addView(conteneurGrille)
        })

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
    private fun chargerEnFond() {
        val ctx = requireContext().applicationContext
        val principal = Handler(Looper.getMainLooper())
        Thread {
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            // Donner une échéance aux cartes qui n'en ont pas, ici et non à la
            // capture : c'est ce qui étale un carnet déjà rempli au lieu de le
            // rendre entièrement dû le même jour. Voir [Carnet.planifier].
            Carnet.planifier(ctx)
            val prets = Carnet.cartes(ctx).map { CarteCarnet.contenu(ctx, it) }
            principal.post {
                if (!isAdded) return@post
                contenus = prets
                ligneTri.visibility = if (prets.isEmpty()) View.GONE else View.VISIBLE
                construireFiltres()
                surlignerTri()
                remplirGrille()
            }
        }.start()
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
            }
        }

        ligneJeux.addView(puce("Tous", null))
        presents.forEach { ligneJeux.addView(puce("${it.emoji} ${it.nom}", it)) }
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

    private fun remplirGrille() {
        val ctx = context ?: return
        val d = resources.displayMetrics.density
        conteneurGrille.removeAllViews()

        if (contenus.isEmpty()) {
            tvResume.text = "Le carnet est vide."
            conteneurGrille.addView(TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (40 * d).toInt() }
                text = "Chaque mot gagné dans l'un des six jeux devient une " +
                    "carte et vient s'ajouter ici. Trouvez un mot, devinez-en " +
                    "un, écrivez-en un : la première carte est à une partie " +
                    "d'ici."
                textSize = 15f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.25f)
                setTextColor(Color.parseColor("#757575"))
                setPadding((24 * d).toInt(), 0, (24 * d).toInt(), 0)
            })
            return
        }

        val visibles = filtre?.let { jeu -> contenus.filter { jeu in it.carte.jeux } }
            ?: contenus

        val parRarete = visibles.groupingBy { it.rarete }.eachCount()
        tvResume.text = buildString {
            append("${visibles.size} mot")
            if (visibles.size > 1) append("s")
            filtre?.let { append(" dans ${it.nom}") }
            append(" — ")
            append(
                Rarete.values().reversed()
                    .filter { (parRarete[it] ?: 0) > 0 }
                    .joinToString("   ") { "${it.symbole} ${parRarete[it]}" }
            )
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
        val dispo = resources.displayMetrics.widthPixels - (24 * d).toInt() * 2
        val cible = (LARGEUR_CIBLE_VIGNETTE_DP * d).toInt()
        val colonnes = ((dispo + gouttiere) / (cible + gouttiere)).coerceAtLeast(2)
        val cote = (dispo - (colonnes - 1) * gouttiere) / colonnes

        if (tri == Tri.ETAGERE) {
            // Le résumé change de sujet avec la disposition : par casiers, ce
            // qui compte n'est plus la rareté mais l'avancement, et le prix à
            // payer pour une carte acquise est ce qu'aucun écran ne disait.
            val acquises = visibles.count { it.carte.acquise }
            tvResume.text = "$acquises acquis sur ${visibles.size} — six " +
                "révisions réussies par carte, étalées sur cinq mois."
            remplirEtagere(ctx, visibles, cote, colonnes)
            return
        }

        val ordonnes = when (tri) {
            Tri.ALPHA -> visibles.sortedBy { it.carte.forme.lowercase() }
            Tri.RARETE -> visibles.sortedWith(
                compareByDescending<ContenuCarte> { it.rarete.ordinal }
                    .thenBy { it.carte.forme.lowercase() }
            )
            else -> visibles.sortedByDescending { it.carte.numero }
        }
        emettreVignettes(ctx, ordonnes, cote, colonnes)
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
        for (boite in 0..Sonje.BOITE_ACQUISE) {
            val dedans = parCasier[boite].orEmpty()
                .sortedBy { it.carte.forme.lowercase() }
            val dues = dedans.count {
                Sonje.estDue(it.carte.boite, it.carte.jourEcheance, aujourdHui)
            }
            conteneurGrille.addView(etiquetteCasier(ctx, boite, dedans.size, dues))
            emettreVignettes(ctx, dedans, cote, colonnes)
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


    /** Referme la carte ouverte. Rend `false` s'il n'y en avait aucune. */
    private fun fermerCarte(): Boolean {
        val voile = voileCarte ?: return false
        voileCarte = null
        voile.animate().alpha(0f).setDuration(160)
            .withEndAction { racine.removeView(voile) }.start()
        return true
    }

    /** Les vignettes d'une liste, deux par ligne, dans [hote]. */
    private fun emettreVignettes(
        ctx: Context,
        liste: List<ContenuCarte>,
        cote: Int,
        colonnes: Int,
        hote: LinearLayout = conteneurGrille
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
            ligne?.addView(CarteCarnet.vignette(ctx, c, cote).apply {
                layoutParams = LinearLayout.LayoutParams(
                    cote, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (posColonne != colonnes - 1) rightMargin = gouttiere }
                isClickable = true
                setOnClickListener { montrerCarte(c) }
            })
        }
    }

    /**
     * La carte entière, posée au-dessus de la grille et **retournée pour
     * arriver** : c'est le geste qui fait la carte à collectionner, et il ne
     * coûte qu'une rotation de vue.
     */
    private fun montrerCarte(c: ContenuCarte) {
        val ctx = context ?: return
        val d = resources.displayMetrics.density

        val voile = FrameLayout(ctx).apply {
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
        val carte = CarteCarnet.complete(ctx, c)
        defilement.addView(carte)
        voile.addView(defilement)

        voile.setOnClickListener { fermerCarte() }
        // La carte ne ferme pas : on peut la lire et la faire défiler.
        carte.isClickable = true

        voileCarte = voile
        racine.addView(voile)
        voile.alpha = 0f
        voile.animate().alpha(1f).setDuration(160).start()

        // Une carte rare se retourne plus lentement et dépasse légèrement son
        // aplomb avant de se poser : le même geste, mais qui prend son temps.
        // C'est la seule chose que la durée d'une animation sait dire, et elle
        // le dit sans un mot.
        carte.cameraDistance = 9000f * d
        carte.rotationY = -85f
        carte.animate().rotationY(0f)
            .setDuration(if (c.rarete.distinguee) 470L else 360L)
            .setInterpolator(
                if (c.rarete.distinguee) OvershootInterpolator(1.4f)
                else DecelerateInterpolator()
            )
            // Une fois posée, la carte suit la main : le suivi ne s'arme qu'ici
            // pour ne pas écrire dans `rotationY` pendant le retournement. Le
            // doigt attend le même moment, et pour la même raison. Le
            // `ScrollView` lui reprendra le geste dès qu'il partira vers le
            // haut ou le bas — le carton reçoit alors un `CANCEL` et se
            // relève, le défilement d'une fiche haute n'est pas sacrifié.
            .withEndAction {
                Inclinaison.suivre(carte)
                (carte as? Carton)?.sensibleAuDoigt = true
            }
            .start()
    }

    override fun onStart() {
        super.onStart()
        // Sans cela le dialogue s'ajuste à son contenu et laisse l'activité
        // visible sur les bords : la collection mérite tout l'écran.
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Le bouton Retour dépile : la carte ouverte, puis le casier ouvert,
        // puis le carnet. Il fermait le dialogue entier depuis n'importe quelle
        // profondeur, ce qui passait tant que le carnet n'avait aucune
        // navigation et devient faux depuis que la boîte en a une.
        //
        // Les deux actions sont consommées, pas seulement `ACTION_UP` :
        // `Dialog` annule sur la levée mais retient la descente, et ne
        // consommer que l'une des deux laisse un retour fantôme au prochain
        // appui.
        dialog?.setOnKeyListener { _, code, evenement ->
            when {
                code != KeyEvent.KEYCODE_BACK -> false
                voileCarte == null -> false
                else -> {
                    if (evenement.action == KeyEvent.ACTION_UP) {
                        fermerCarte()
                    }
                    true
                }
            }
        }
    }
}
