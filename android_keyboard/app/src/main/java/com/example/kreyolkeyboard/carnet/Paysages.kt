package com.example.kreyolkeyboard.carnet

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Le lieu que la fenêtre d'une carte montre : trente lieux de Guadeloupe,
 * choisis par le propriétaire (quinze le 2026-10-19, quinze de plus le même
 * jour sur maquettes).
 *
 * ## Pourquoi un lieu
 *
 * Un paysage fait deux choses à la fois : il distingue les cartes d'un coup
 * d'œil, en vignette comme en deutéranopie, et il donne une raison de
 * collectionner (l'album des paysages, trente lieux à quatre heures).
 *
 * ## Quel lieu pour quel mot
 *
 * LuxKeyb tire le lieu du champ sémantique du mot ; le kréyòl n'a pas ce
 * classement (`creole_blasons.json` ne porte que des dessins). Le lieu se tire donc **du mot
 * lui-même**, par une empreinte fixe : un mot montre toujours le même lieu,
 * sur tous les téléphones et d'une version à l'autre.
 *
 * L'empreinte est salée par [GRAINE]. Sans elle, deux des cent vingt cases
 * de l'album (trente lieux, quatre raretés) n'avaient aucun mot : il n'y a
 * que 64 mots très rares pour trente lieux. La graine a été choisie parmi
 * vingt mille sur les 622 mots glosés : avec elle chaque case a au moins un
 * mot, et c'est encore vrai dans 28 tirages sur 30 où les rangs du
 * dictionnaire bougent de 10 %, ce que fait une régénération du corpus.
 * `PaysagesAlbumTest` rejoue la mesure sur les actifs livrés.
 *
 * **Ne pas réordonner, retirer ni ajouter d'entrée, ni changer la graine,
 * une fois une version publiée avec ces lieux** : l'ordre, le nombre et la
 * graine fixent l'empreinte, et en changer un déplacerait presque toutes les
 * cartes déjà gagnées. La 23.0.0 ne montrait aucun lieu (`LIEUX_DESSINES`
 * était faux) : c'est ce qui a permis de passer de quinze à trente sans rien
 * déplacer chez personne. Un lieu de plus, après publication, demandera de
 * garder dans la carte le lieu où elle a été gagnée.
 *
 * Les noms sont les noms d'usage, en français, comme sur les cartes routières.
 */
enum class Lieu(val nom: String) {
    GRANDE_ANSE("Grande Anse"),
    LES_SAINTES("Les Saintes"),
    SOUFRIERE("La Soufrière"),
    CHUTES_CARBET("Chutes du Carbet"),
    SOFAIA("Sofaïa"),
    RAVINE_CHAUDE("Ravine Chaude"),
    POINTE_CHATEAUX("Pointe des Châteaux"),
    MARIE_GALANTE("Marie-Galante"),
    DESIRADE("La Désirade"),
    BOUILLANTE("Bouillante"),
    MORNE_A_L_EAU("Morne-à-l'Eau"),
    ILET_MACOU("Îlet Macou"),
    ROCHES_GRAVEES("Roches gravées"),
    PLACE_VICTOIRE("Place de la Victoire"),
    FIGUIER_MAUDIT("Figuier maudit"),
    GRAND_ETANG("Grand Étang"),
    BAINS_JAUNES("Bains Jaunes"),
    CASCADE_ECREVISSES("Cascade aux Écrevisses"),
    SAUT_LEZARDE("Saut de la Lézarde"),
    GRANDE_VIGIE("Pointe de la Grande Vigie"),
    PORTE_ENFER("Porte d'Enfer"),
    PETITE_TERRE("Petite-Terre"),
    ILET_GOSIER("Îlet du Gosier"),
    GRAND_CUL_DE_SAC("Grand Cul-de-Sac Marin"),
    GUEULE_GRAND_GOUFFRE("Gueule Grand Gouffre"),
    TRAVERSEE("Route de la Traversée"),
    GRIVELIERE("Habitation La Grivelière"),
    MATOUBA("Matouba"),
    FORT_DELGRES("Fort Delgrès"),
    MEMORIAL_ACTE("Mémorial ACTe");

    companion object {
        /**
         * Le lieu d'un mot. `String.hashCode` est fixé par la spécification
         * de Java, pas par l'implémentation : l'empreinte ne bouge pas d'un
         * appareil à l'autre. La forme est mise en minuscules et en NFC, pour
         * qu'un « é » composé et un « é » précomposé tombent au même endroit.
         */
        fun du(forme: String): Lieu {
            val cle = java.text.Normalizer.normalize(forme.lowercase(), java.text.Normalizer.Form.NFC)
            return values()[Math.floorMod((GRAINE + cle).hashCode(), values().size)]
        }

        /** Le sel de l'empreinte, voir plus haut : il fige la répartition des mots. */
        const val GRAINE = "8576:"
    }
}

/** Les espèces que les lieux peignent, et que la carte ouverte fait bouger : voir [Paysages.BETES]. */
enum class Espece {
    TOULOULOU, TORTUE, PELICAN_PIEU, PELICAN, PELICAN_QUAI, LIBELLULE, ANOLI, PIC, AGOUTI, RATON,
    SIFFLEUR, PARULINE, BALEINE, ECREVISSE, IGUANE,
    /** Pas une bête : une barque au mouillage, qui se balance comme elles bougent (voir [Paysages.BETES]). */
    BARQUE,
    /** Pas une bête : la guirlande de fanions de la place de la Victoire, qui flotte au vent. */
    FANIONS,
    /** La tourterelle à queue carrée, posée sur les balcons de la place de la Victoire. */
    TOURTERELLE,
    /** Pas une bête : une jardinière de feuillage accrochée à un garde-corps, qui tremble à la secousse. */
    PLANTE,
    /** La même, fleurie de géraniums rouges. */
    FLEURS,
    /** Pas une bête : une mangue du manguier de Marie-Galante, qui tombe quand on secoue le téléphone. */
    MANGUE,
    /** Pas une bête : le bateau de plongée de Bouillante, amarré au ponton, qui tangue. */
    BATEAU,
    /** Une abeille minuscule qui butine autour de sa fleur. */
    ABEILLE,
    /** Pas une bête : une liane qui pend de la voûte, aux Roches gravées, et se balance. */
    LIANE
}

/** Une bête d'un lieu : son espèce, sa place dans la boîte 100 × 66, son plan (0 à 2) et sa taille. */
class Bete(val espece: Espece, val x: Float, val y: Float, val plan: Int = 2, val taille: Float = 1f)

/**
 * L'heure d'une carte, et sa palette : la rareté fait avancer le jour.
 *
 * Commune le matin, peu commune l'après-midi, rare le soir aux fenêtres
 * allumées, très rare la nuit étoilée. La rareté reste fixée par le rang de
 * fréquence ; l'heure ne fait que la rendre visible d'un coup d'œil, sans
 * compter les joyaux.
 *
 * Les couleurs sont en entiers ARGB calculés à la main, sans `android.graphics.Color` :
 * la palette reste ainsi vérifiable sur la JVM des tests.
 */
class Heure private constructor(
    val rang: Int,
    val cielHaut: Int,
    val cielMilieu: Int?,
    val cielBas: Int,
    val astre: Int,
    val astreX: Float,
    val astreY: Float,
    val astreR: Float,
    val loin: Int,
    val milieu: Int,
    val pres: Int,
    val sol: Int,
    val pierre: Int,
    val toit: Int,
    val fenetre: Int,
    val allume: Boolean,
    val eau: Int,
    val nuit: Boolean
) {
    /** Le dégradé nacré des sujets, du clair au sombre : il prend la couleur de l'heure. */
    val nacre: IntArray = NACRES[rang]

    /** Le cœur des nœuds et le liseré des dessins. */
    val encre: Int = ENCRES[rang]

    companion object {
        private val NACRES = arrayOf(
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFEBE7EF.toInt(), 0xFFCDD5DF.toInt()),
            intArrayOf(0xFFFFFAF0.toInt(), 0xFFF6E5CC.toInt(), 0xFFDCC19C.toInt()),
            intArrayOf(0xFFFFF4EE.toInt(), 0xFFF3D6CF.toInt(), 0xFFD8A9A8.toInt()),
            intArrayOf(0xFFFFFBEE.toInt(), 0xFFF6E6B6.toInt(), 0xFFD9B766.toInt())
        )
        private val ENCRES = intArrayOf(
            0xFF7F8A96.toInt(), 0xFF8C6A48.toInt(), 0xFF6B4A5C.toInt(), 0xFF5B4A2A.toInt()
        )

        private fun h(s: Long) = s.toInt()

        /**
         * Les quatre heures en couleurs vives, comme peintes à la gouache
         * (direction validée par le propriétaire le 2026-11-01, palettes de la
         * maquette `vives.js` de la skill `direction-artistique`) : le matin,
         * un ciel bleu franc qui passe au rose puis au jaune pâle vers
         * l'horizon, des verts francs et une eau soutenue ; l'après-midi
         * orangé ; le soir violet, rose et orange ; la nuit bleu profond, où la
         * vivacité vient des lumières dorées.
         */
        private val BASE = arrayOf(
            Heure(0, h(0xFF7EC3F2), h(0xFFF5C3DA), h(0xFFFFE9A6), h(0xFFFFE066), 22f, 15f, 6f,
                h(0xFF6F9BEA), h(0xFF5DBB63), h(0xFF2F8F5B), h(0xFF2A6B45),
                h(0xFFF4CF94), h(0xFFE0674A), h(0xFF4B6A8A), false, h(0xFF3E9DEB), false),
            Heure(1, h(0xFFFFA94D), h(0xFFFFD27A), h(0xFFFFF0B8), h(0xFFFFF39A), 78f, 13f, 7f,
                h(0xFFE38B5C), h(0xFF9CC43F), h(0xFF4E8A2E), h(0xFF3B5E22),
                h(0xFFFFD9A0), h(0xFFD9503A), h(0xFF6A4A2E), false, h(0xFF2FA8DE), false),
            Heure(2, h(0xFF4B3BA8), h(0xFFFF6FA8), h(0xFFFFB25C), h(0xFFFFD36A), 68f, 31f, 10f,
                h(0xFF9B5FB8), h(0xFF6B3F8F), h(0xFF3E2766), h(0xFF2A1A48),
                h(0xFFF0A890), h(0xFF8A2F5A), h(0xFFFFCD48), true, h(0xFFE8789A), false),
            Heure(3, h(0xFF0B1450), null, h(0xFF2D4BB0), h(0xFFFFF4C8), 79f, 12f, 4.6f,
                h(0xFF2F4DA0), h(0xFF22357F), h(0xFF16245A), h(0xFF0C1638),
                h(0xFF6A79B8), h(0xFF3A3F7A), h(0xFFFFD45A), true, h(0xFF2D4FA8), true)
        )

        /**
         * La palette d'une rareté, le ciel légèrement teinté par le champ.
         *
         * Légèrement : 14 % au zénith, 10 % à l'horizon. Assez pour qu'un
         * tiroir garde sa couleur, pas assez pour qu'un matin vire au violet.
         */
        fun de(rarete: Rarete, teinte: Float?): Heure {
            val b = BASE[rarete.ordinal]
            if (teinte == null) return b
            val t = hsl(teinte, 0.55f, 0.62f)
            return Heure(
                b.rang, melange(b.cielHaut, t, 0.14f), b.cielMilieu?.let { melange(it, t, 0.12f) },
                melange(b.cielBas, t, 0.10f), b.astre, b.astreX, b.astreY, b.astreR,
                b.loin, b.milieu, b.pres, b.sol, b.pierre, b.toit, b.fenetre, b.allume, b.eau, b.nuit
            )
        }

        /** Mélange linéaire de deux couleurs opaques, composante par composante. */
        fun melange(a: Int, b: Int, t: Float): Int {
            fun canal(decalage: Int): Int {
                val x = (a shr decalage) and 0xFF
                val y = (b shr decalage) and 0xFF
                return (x + (y - x) * t).roundToInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (canal(16) shl 16) or (canal(8) shl 8) or canal(0)
        }

        /** HSL vers ARGB opaque, comme le `hsl()` de CSS. */
        fun hsl(teinte: Float, s: Float, l: Float): Int {
            val a = s * min(l, 1f - l)
            fun f(n: Int): Int {
                val k = (n + teinte / 30f) % 12f
                val v = l - a * max(-1f, min(k - 3f, min(9f - k, 1f)))
                return (v * 255f).roundToInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (f(0) shl 16) or (f(8) shl 8) or f(4)
        }
    }
}

/**
 * Les trente lieux, dessinés par le code dans une boîte de 100 × 66 unités.
 *
 * Aucun fichier image : chaque lieu est une suite de plans (crête lointaine,
 * colline, bâti, eau, premier plan) et la même suite se repeint aux quatre
 * heures en changeant de palette. Le hasard des fenêtres et des bosquets est
 * **graine fixe**, si bien qu'un lieu est le même sur toutes les cartes.
 *
 * Le rendu est mis en cache par lieu, heure et taille : comme le métal, il ne
 * dépend pas du mot, et trente vignettes d'un même tiroir partagent un bitmap.
 */
object Paysages {

    const val LARGEUR = 100f
    const val HAUTEUR = 66f

    /**
     * Un huitième du tas : un tiroir du carnet montre jusqu'à cent vingt
     * paysages différents (trente lieux, quatre heures) et chacun pèse un demi
     * mégaoctet en vignette. Un cache plus petit que ce qu'une seule page
     * affiche ne cache plus rien, il redessine tout à chaque trame.
     */
    private val CACHE = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(8L * 1024 * 1024, 48L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(cle: String, valeur: Bitmap): Int = valeur.byteCount
    }

    /**
     * Où tombe la boîte 100 × 66 dans une ouverture de [l] × [h] : elle la
     * couvre et se rogne sur les côtés, comme `xMidYMid slice` en SVG.
     * Rend l'échelle, puis le décalage x et y.
     */
    fun cadrage(l: Float, h: Float): FloatArray {
        val e = max(l / LARGEUR, h / HAUTEUR)
        return floatArrayOf(e, (l - LARGEUR * e) / 2f, (h - HAUTEUR * e) / 2f)
    }

    /**
     * Le lieu à l'heure dite, rendu à [largeurPx] × [hauteurPx], habillé par
     * la saison de [souvenir] s'il y en a un (voir [Habillage]).
     *
     * Le souvenir entre dans la clé : trente lieux, quatre heures, deux
     * saisons et cinq fêtes, mais un tiroir n'en montre qu'une poignée à la
     * fois, et deux cartes gagnées le même carême partagent toujours leur rendu.
     */
    fun image(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
              souvenir: Souvenir? = null, facture: Facture = Facture.VIGNETTE, ouverte: Boolean = false): Bitmap? {
        if (largeurPx <= 0 || hauteurPx <= 0) return null
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir, facture, ouverte)
        CACHE.get(cle)?.let { if (!it.isRecycled) return it }
        val bitmap = rendu(lieu, heure, largeurPx, hauteurPx, souvenir, facture, ouverte) ?: return null
        CACHE.put(cle, bitmap)
        return bitmap
    }

    private fun cle(lieu: Lieu, heure: Heure, teinte: Float?, l: Int, h: Int, souvenir: Souvenir?,
                    facture: Facture = Facture.VIGNETTE, ouverte: Boolean = false) =
        "${lieu.ordinal}_${heure.rang}_${teinte}_${souvenir?.saison}_${souvenir?.fete}_${facture.ordinal}_${l}x$h" +
            if (ouverte) "_ouverte" else ""

    /** Le rendu s'il est déjà en cache, sans rien calculer. */
    fun enCache(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
                souvenir: Souvenir? = null, facture: Facture = Facture.VIGNETTE, ouverte: Boolean = false): Bitmap? =
        CACHE.get(cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir, facture, ouverte))?.takeIf { !it.isRecycled }

    /**
     * Rend le lieu hors du fil principal, puis appelle [pret] sur le fil
     * principal. Réservé aux vignettes.
     *
     * Sur le Galaxy A21s (2026-10-12), un paysage de vignette coûte environ
     * 100 ms, surtout la passe de saison de [Habillage]. La grille du
     * téléphone est un `ScrollView` qui ne recycle rien, et Android enregistre
     * le dessin de **toutes** ses vignettes dès l'ouverture, même hors de
     * l'écran : un carnet de 36 cartes figeait l'écran 4 à 5 secondes, et le
     * gel grandit avec le carnet. La vignette montre son ciel le temps que le
     * lieu arrive. La carte ouverte reste synchrone : un seul rendu, et une
     * carte qu'on vient de toucher ne doit pas apparaître vide.
     *
     * Deux vignettes qui demandent le même rendu ne le paient qu'une fois.
     */
    fun demander(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
                 souvenir: Souvenir?, pret: (Bitmap) -> Unit) {
        if (largeurPx <= 0 || hauteurPx <= 0) return
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir)
        synchronized(EN_COURS) {
            val attente = EN_COURS[cle]
            if (attente != null) {
                attente += pret
                return
            }
            EN_COURS[cle] = mutableListOf(pret)
        }
        FILS.execute {
            val bitmap = try {
                rendu(lieu, heure, largeurPx, hauteurPx, souvenir)
            } catch (e: OutOfMemoryError) {
                null
            }
            // En cache dès qu'il est rendu, sans attendre le fil principal :
            // une vignette qui se dessine entre-temps le trouve déjà.
            if (bitmap != null) CACHE.put(cle, bitmap)
            PRINCIPAL.post {
                val qui = synchronized(EN_COURS) { EN_COURS.remove(cle) }.orEmpty()
                if (bitmap != null) qui.forEach { it(bitmap) }
            }
        }
    }

    /**
     * Les demandes en vol. Le carnet en dépose aussi depuis son fil de
     * chargement (voir `CarnetFragment.chargerEnFond`) : d'où le verrou. Les
     * rappels, eux, passent toujours sur le fil principal.
     */
    private val EN_COURS = HashMap<String, MutableList<(Bitmap) -> Unit>>()
    private val PRINCIPAL by lazy { Handler(Looper.getMainLooper()) }

    /**
     * Trois fils de fond : le rendu n'a aucun état partagé, et le A21s a huit
     * cœurs. Le premier écran de la grille, huit vignettes, se peint ainsi en
     * trois tours pendant que le carnet se charge.
     */
    private val FILS by lazy {
        Executors.newFixedThreadPool(3) { tache ->
            Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                tache.run()
            }, "paysages").apply { isDaemon = true }
        }
    }

    /**
     * Fait tourner une fois, en petit et pour rien, le rendu de chaque
     * saison. Fait pour un fil de fond, pendant que le carnet se charge.
     *
     * Le premier passage dans la passe de saison de [Habillage] n'est pas
     * encore compilé : sur un Galaxy A21s (2026-10-12), les trois premiers
     * paysages de vignette y passaient 720 à 760 ms chacun, les suivants 80
     * à 100 ms. Ces trois-là étaient justement ceux du haut de la grille.
     */
    fun prechauffer() {
        val heure = Heure.de(Rarete.COMMUN, null)
        for (saison in Saison.values()) {
            try {
                rendu(Lieu.SOFAIA, heure, 136, 102, Souvenir(saison, null))?.recycle()
            } catch (e: OutOfMemoryError) {
                return
            }
        }
    }

    /**
     * Un rendu hors cache, que l'appelant garde pour lui.
     *
     * [ouverte] : pour la carte ouverte restée à plat (un jour de fête). Ce
     * qui y bouge n'est pas peint, l'averse et les étoiles : [Ambiance] les
     * anime, peints ils resteraient figés (propriétaire, 2026-10-19).
     */
    fun rendu(lieu: Lieu, heure: Heure, largeurPx: Int, hauteurPx: Int, souvenir: Souvenir? = null,
              facture: Facture = Facture.VIGNETTE, ouverte: Boolean = false): Bitmap? {
        if (largeurPx <= 0 || hauteurPx <= 0) return null
        val bitmap = Bitmap.createBitmap(largeurPx, hauteurPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val k = cadrage(largeurPx.toFloat(), hauteurPx.toFloat())
        c.save()
        c.translate(k[1], k[2])
        c.scale(k[0], k[0])
        if (souvenir == null) {
            dessiner(c, lieu, heure, facture = facture, echelle = k[0], etoiles = !ouverte)
            c.restore()
            return bitmap
        }
        // Avec une saison, le lieu est peint à part sur un calque transparent,
        // puis posé sur le ciel : ce calque est aussi le masque dont
        // [Habillage] a besoin, et le lieu n'est peint qu'une fois.
        dessiner(c, lieu, heure, ciel = true, lieuAussi = false, facture = facture, echelle = k[0], etoiles = !ouverte)
        c.restore()
        val calque = Bitmap.createBitmap(largeurPx, hauteurPx, Bitmap.Config.ARGB_8888)
        val cc = Canvas(calque)
        cc.translate(k[1], k[2])
        cc.scale(k[0], k[0])
        val releve = Bitmap.createBitmap(largeurPx, hauteurPx, Bitmap.Config.ALPHA_8)
        dessiner(cc, lieu, heure, ciel = false, facture = facture, echelle = k[0], releve = aLEchelle(releve, k))
        c.drawBitmap(calque, 0f, 0f, null)
        Habillage.appliquer(bitmap, calque, lieu, heure, souvenir, k, averse = !ouverte, releve = releve)
        calque.recycle()
        releve.recycle()
        return bitmap
    }

    /**
     * De combien chaque plan glisse au plus quand la carte penche à fond, en
     * part de la largeur (de la hauteur pour le tangage) : le ciel, les
     * nuages, le lointain, le milieu, le proche, puis les coulisses.
     *
     * C'est le fond qui bouge le plus, comme derrière une vitre : le cadre de
     * la carte est ce qu'il y a de plus près, le dessin du mot est posé
     * devant et ne bouge pas, et plus un plan est loin du verre, plus il
     * glisse quand l'œil change d'angle. Les valeurs sont celles de la
     * maquette `cinq_plans.html` (skill `direction-artistique`, règle 8).
     *
     * Le sixième plan n'existe que sur les rares et les très rares : les
     * coulisses, juste derrière la vitre (voir [nombreDePlans]).
     */
    val GLISSEMENT = floatArrayOf(0.05f, 0.04f, 0.03f, 0.017f, 0.007f, 0.003f)

    /** Le rang des nuages parmi les plans : une bande qui défile, voir [nuages]. */
    const val NUAGES = 1

    /** Le rang du plan du milieu du lieu (son plan 1) parmi les plans. */
    const val MILIEU = 3

    /** Le rang du plan de devant du lieu (son plan 2) parmi les plans. */
    const val DEVANT = 4

    /**
     * Les lieux dont le plan de devant n'est que feuillage : une secousse du
     * téléphone le fait bruisser (`Motif.bruisser`). Ailleurs ce plan porte
     * aussi le sol, qui ne doit pas onduler.
     */
    internal val FEUILLAGE_DEVANT = setOf(Lieu.GRANDE_ANSE)

    /**
     * Cinq cartons pour toutes les cartes, six à partir de rare (décision du
     * propriétaire, 2026-11-01, règle 8 de la skill `direction-artistique`) :
     * une commune, la carte qu'on manipule le plus, a le plein effet de
     * théâtre. Les rares et les très rares gardent en plus leurs coulisses,
     * la seule différence de profondeur qui reste entre les paliers ; la
     * rareté se dit par l'heure, le métal et la plaque, qu'on voit sans
     * pencher la carte.
     */
    fun nombreDePlans(heure: Heure): Int = if (PAPIER) 5 else if (heure.rang >= Rarete.RARE.ordinal) 6 else 5

    /**
     * Le lieu en cinq calques, six pour une rare ([nombreDePlans]), pour la
     * carte ouverte : voir [GLISSEMENT]. Le premier est le ciel et ne laisse
     * rien de vide ; le deuxième est la bande des nuages ([NUAGES]), que
     * [CadreOrne] fait traverser le ciel ; les autres sont transparents autour
     * de ce qu'ils portent.
     *
     * Chaque calque déborde de la fenêtre de quoi glisser sans découvrir son
     * bord : voir [marge], et [CadreOrne] qui le peint centré sur elle.
     *
     * Les calques sont des **cartons de théâtre de papier** (skill
     * `direction-artistique`) : chaque pièce est un carton peint à la gouache
     * ([Facture.GOUACHE]), chaque calque porte une ombre nette sur ceux de
     * derrière et un liseré de lumière au bord haut ([trancher]), et le
     * lointain s'efface par la couleur ([ESTOMPE]), sans flou. Tout cela est
     * peint ici, une fois : pencher la carte ne coûte rien de plus.
     *
     * `null` pour une carte gagnée un jour de fête, qui garde son rendu à
     * plat, sans nuages : les feux, les braises et les lumières s'ajoutent à
     * la lumière du paysage entier, et ne se répartissent pas en calques.
     */
    fun plans(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
              souvenir: Souvenir? = null): Array<Bitmap>? {
        if (largeurPx <= 0 || hauteurPx <= 0 || souvenir?.fete != null) return null
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir)
        val n = nombreDePlans(heure)
        val caches = Array(n) { CACHE.get("${cle}_p$it")?.takeIf { b -> !b.isRecycled } }
        val ombresOk = !PAPIER || (2 until n).all { CACHE.get("${cle}_o$it")?.isRecycled == false }
        if (caches.all { it != null } && ombresOk) return caches.requireNoNulls()
        val ombres = arrayOfNulls<Bitmap>(n)
        val rendus = rendrePlans(lieu, heure, largeurPx, hauteurPx, souvenir, ombres)
        rendus.forEachIndexed { i, b -> CACHE.put("${cle}_p$i", b) }
        ombres.forEachIndexed { i, b -> if (b != null) CACHE.put("${cle}_o$i", b) }
        return rendus
    }

    /**
     * Les ombres portées des plans du papier découpé ([PAPIER]), une par
     * plan à partir du lointain, de la taille de son calque : la silhouette
     * du plan, floutée. [CadreOrne] les pose à chaque trame sur le plan de
     * derrière, si bien que l'ombre glisse avec lui et que l'écart entre la
     * découpe et son ombre change quand on penche la carte, comme dans un
     * théâtre de papier. `null` hors papier ou pour une fête ; une case
     * `null` si le cache l'a perdue.
     */
    fun ombres(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
               souvenir: Souvenir? = null): Array<Bitmap?>? {
        if (!PAPIER || largeurPx <= 0 || hauteurPx <= 0 || souvenir?.fete != null) return null
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir)
        return Array(nombreDePlans(heure)) { CACHE.get("${cle}_o$it")?.takeIf { b -> !b.isRecycled } }
    }

    /**
     * Où tombe l'ombre d'un plan sur celui de derrière, en unités de la
     * boîte : la lumière de la rampe, en bas à gauche, la porte vers le haut
     * et à droite. Légère.
     */
    const val OMBRE_PORTEE_X = 0.55f
    const val OMBRE_PORTEE_Y = -0.85f

    /** La couleur de l'ombre portée d'un plan, à l'heure de la carte. */
    fun couleurOmbrePortee(heure: Heure): Int =
        ((if (heure.nuit) 0.5f else 0.34f) * 255f).roundToInt() shl 24 or (OMBRE and 0xFFFFFF)

    /**
     * Ce que le calque [i] déborde de chaque côté d'une fenêtre de [n] pixels :
     * sa plus grande course, plus un pixel de garde.
     *
     * Jusqu'en 37.4.1 les calques avaient la taille exacte de la fenêtre, le
     * dessin seul était agrandi dedans : en glissant, le bord de l'image
     * découvrait une bande du fond de la fenêtre (vu sur un Galaxy A21s).
     */
    fun marge(i: Int, n: Int): Int = ceil(GLISSEMENT[i] * n).toInt() + 1

    /**
     * La profondeur de chaque calque, en plans, pour l'ombre qu'il reçoit :
     * plus un carton est loin de celui qui la porte, plus elle monte et
     * s'étale. Les nuages n'en reçoivent pas (-1).
     */
    private val PROFONDEUR = intArrayOf(0, -1, 1, 2, 3, 4)

    private fun rendrePlans(lieu: Lieu, heure: Heure, l: Int, h: Int, souvenir: Souvenir?,
                            ombres: Array<Bitmap?> = arrayOfNulls(0)): Array<Bitmap> {
        val n = nombreDePlans(heure)
        val tailles = Array(n) { i -> intArrayOf(l + 2 * marge(i, l), h + 2 * marge(i, h)) }
        val ks = Array(n) { i ->
            cadrage(tailles[i][0].toFloat(), tailles[i][1].toFloat())
        }
        fun toile(i: Int, dessin: (Canvas) -> Unit): Bitmap {
            val b = Bitmap.createBitmap(tailles[i][0], tailles[i][1], Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.translate(ks[i][1], ks[i][2])
            c.scale(ks[i][0], ks[i][0])
            dessin(c)
            return b
        }
        // Le calque [i] porte le plan [i] − 2 du lieu : 0 le lointain, 1 le milieu, 2 le proche.
        // Avec une saison, il dresse aussi le relevé de sa végétation.
        val releves = arrayOfNulls<Bitmap>(n)
        fun calque(i: Int) = toile(i) {
            val releve = if (souvenir != null) {
                Bitmap.createBitmap(tailles[i][0], tailles[i][1], Bitmap.Config.ALPHA_8).also { r -> releves[i] = r }
            } else null
            dessiner(it, lieu, heure, ciel = false, lieuAussi = true, seul = i - 2, faune = false,
                facture = Facture.GOUACHE, echelle = ks[i][0], releve = releve?.let { r -> aLEchelle(r, ks[i]) })
        }
        fun peindre(i: Int, dessin: Peintre.() -> Unit) = toile(i) {
            Peintre(it, heure, facture = Facture.GOUACHE, echelle = ks[i][0]).dessin()
        }
        // Les étoiles n'y sont pas : elles scintillent dans l'ambiance.
        val ciel = toile(0) {
            dessiner(it, lieu, heure, ciel = true, lieuAussi = false, facture = Facture.GOUACHE, echelle = ks[0][0], etoiles = false)
        }
        val loin = calque(2)
        val milieu = calque(3)
        val proche = calque(4)
        // Les silhouettes avant la saison : la neige ou les fleurs que
        // [Habillage] pose par-dessus ne sont pas du carton.
        val formes = arrayOf(loin.extractAlpha(), milieu.extractAlpha(), proche.extractAlpha())
        val unite = ks[0][0]
        if (souvenir != null) {
            // Le ciel aussi, sans masque : l'hiver le pâlit, l'été le réchauffe.
            Habillage.appliquerPlans(arrayOf(ciel, loin, milieu, proche), arrayOf(null, loin, milieu, proche),
                lieu, heure, souvenir, arrayOf(ks[0], ks[2], ks[3], ks[4]), arrayOf(null, releves[2], releves[3], releves[4]))
        }
        releves.forEach { it?.recycle() }
        // Après la saison, qui reconnaît la végétation à ses couleurs d'origine.
        eclaircir(loin, formes[0], heure.cielBas, ESTOMPE[0])
        eclaircir(milieu, formes[1], heure.cielBas, ESTOMPE[1])
        // Les coulisses des rares, posées après la saison : ce sont des
        // châssis de théâtre, pas des arbres du lieu. Deux rangées, étagées :
        // la seconde, un pas en retrait, est collée au carton du proche, dont
        // elle partage la course.
        val coulisses = if (n < 6) null else {
            val fond2 = peindre(4) { coulissesDuFond() }
            Canvas(proche).drawBitmap(fond2, 0f, 0f, null)
            val forme2 = Bitmap.createBitmap(tailles[4][0], tailles[4][1], Bitmap.Config.ALPHA_8)
            Canvas(forme2).apply { drawBitmap(formes[2], 0f, 0f, null); drawBitmap(fond2, 0f, 0f, null) }
            formes[2].recycle()
            formes[2] = forme2
            fond2.recycle()
            peindre(5) { coulisses() }
        }
        val plans = listOfNotNull(ciel, nuages(heure, l, h), loin, milieu, proche, coulisses).toTypedArray()
        val silhouettes = arrayOfNulls<Bitmap>(plans.size)
        silhouettes[2] = formes[0]
        silhouettes[3] = formes[1]
        silhouettes[4] = formes[2]
        if (coulisses != null) silhouettes[5] = coulisses.extractAlpha()
        // Chaque carton porte son ombre sur tous ceux de derrière : là où un
        // carton intermédiaire la cache, c'est en penchant qu'on la découvre,
        // comme dans une vraie boîte. Les nuages n'en reçoivent pas : ils
        // voyagent, et portent la leur sur leur propre carton ([nuages]).
        if (PAPIER) {
            // Chaque plan est peint sur du kraft découpé avec une petite
            // marge, et son ombre est posée à part, à chaque trame.
            for (devant in 2 until plans.size) {
                val forme = silhouettes[devant] ?: continue
                krafter(plans[devant], forme, unite, heure)
                if (devant < ombres.size) ombres[devant] = flouter(forme, unite)
            }
            silhouettes.forEach { it?.recycle() }
            plans.forEach { Grain.poser(it) }
            return plans
        }
        for (devant in 2 until plans.size) {
            val forme = silhouettes[devant] ?: continue
            for (derriere in 0 until devant) {
                if (derriere == NUAGES) continue
                val force = when {
                    derriere == 0 && devant == 2 -> OMBRE_SUR_LE_CIEL
                    derriere == 0 || derriere == 2 -> OMBRE_SUR_LE_FOND
                    else -> 1f
                }
                ombrer(
                    plans[derriere], forme,
                    marge(derriere, l) - marge(devant, l), marge(derriere, h) - marge(devant, h),
                    unite, heure, PROFONDEUR[devant] - PROFONDEUR[derriere], force
                )
            }
            trancher(plans[devant], forme, unite, heure, force = if (devant == 2) 0.6f else 1f)
        }
        silhouettes.forEach { it?.recycle() }
        return plans
    }

    /** Le kraft du plan, qui dépasse tout autour de sa découpe : un liseré brun, un peu plus large en bas. */
    private fun krafter(b: Bitmap, forme: Bitmap, unite: Float, heure: Heure) {
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            color = Heure.melange(0xFFB08A5A.toInt(), heure.pres, if (heure.nuit) 0.55f else 0.15f)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        }
        val r = max(1f, 0.32f * unite)
        val c = Canvas(b)
        for (k in 0 until 12) {
            val a = k / 12f * 2f * PI.toFloat()
            c.drawBitmap(forme, cos(a) * r, sin(a) * r + 0.25f * r, p)
        }
    }

    /** La silhouette [forme] floutée, de la même taille : l'ombre portée d'un plan. */
    private fun flouter(forme: Bitmap, unite: Float): Bitmap {
        val p = Paint().apply { maskFilter = BlurMaskFilter(max(1f, 0.7f * unite), BlurMaskFilter.Blur.NORMAL) }
        val ou = IntArray(2)
        val flou = forme.extractAlpha(p, ou)
        val b = Bitmap.createBitmap(forme.width, forme.height, Bitmap.Config.ALPHA_8)
        Canvas(b).drawBitmap(flou, ou[0].toFloat(), ou[1].toFloat(), null)
        flou.recycle()
        return b
    }

    /**
     * Le grain du papier, posé une fois sur chaque calque rendu, au pixel :
     * une tuile de bruit clair et sombre très léger, et quelques fibres.
     */
    private object Grain {
        private var tuile: Bitmap? = null

        @Synchronized
        private fun tuile(): Bitmap = tuile ?: run {
            val n = 160
            val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val r = Alea(97)
            val px = IntArray(n * n)
            for (i in px.indices) {
                val v = r()
                val a = (r() * 20f).toInt()
                px[i] = if (v < 0.5f) (a shl 24) else ((a + 4) shl 24) or 0xFFFFFF
            }
            b.setPixels(px, 0, n, 0, 0, n, n)
            val c = Canvas(b)
            val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 0.8f; style = Paint.Style.STROKE }
            repeat(70) {
                val x = r() * n; val y = r() * n; val l = 3f + r() * 9f; val a = r() * 6.3f
                f.color = if (r() < 0.6f) 0x1EFFFFFF else 0x14000000
                c.drawLine(x, y, x + cos(a) * l, y + sin(a) * l, f)
            }
            b.also { tuile = it }
        }

        fun poser(b: Bitmap) {
            val p = Paint().apply {
                shader = BitmapShader(tuile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            Canvas(b).drawRect(0f, 0f, b.width.toFloat(), b.height.toFloat(), p)
        }
    }

    /**
     * Les nuages de carton, le seul plan qui bouge seul (règle 7 de la skill
     * `direction-artistique`) : une bande de toute la largeur de la boîte, de
     * [BANDE_NUAGES] unités de haut, qui se raccorde à elle-même. [CadreOrne]
     * la fait traverser le ciel de gauche à droite, et glisser avec la carte
     * quand on la penche.
     *
     * Chaque nuage porte son ombre sur son propre carton, sous lui, pour
     * qu'elle voyage avec lui ; aucun autre carton ne lui en fait. Les mêmes
     * nuages pour tous les lieux : ils passent, leur place ne dit rien.
     */
    private fun nuages(heure: Heure, l: Int, h: Int): Bitmap {
        val k = cadrage(l.toFloat(), h.toFloat())[0]
        val b = Bitmap.createBitmap(ceil(LARGEUR * k).toInt(), ceil(BANDE_NUAGES * k).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(k, k)
        val p = Peintre(c, heure, facture = Facture.GOUACHE, echelle = k)
        var i = 0
        while (i < NUEES.size) {
            val x = NUEES[i]; val y = NUEES[i + 1]; val longueur = NUEES[i + 2]
            // Un nuage qui déborde à droite repart à gauche : la bande se répète.
            for (decalage in floatArrayOf(0f, -LARGEUR)) {
                if (x + decalage + longueur > -2f && x + decalage < LARGEUR + 2f) p.nuage(x + decalage, y, longueur)
            }
            i += 3
        }
        val forme = b.extractAlpha()
        trancher(b, forme, k, heure, force = 0.6f)
        forme.recycle()
        return b
    }

    /** La hauteur de la bande des nuages, en unités du paysage : le haut du ciel. */
    const val BANDE_NUAGES = 16f

    /**
     * Les nuages : x, y, longueur, en unités du paysage (maquette
     * `cinq_plans.html`).
     */
    private val NUEES = floatArrayOf(57f, 5.5f, 15f, 80f, 11f, 10f, 5f, 8f, 11f)

    /**
     * La part de chemin vers la couleur de l'horizon, par plan : le lointain
     * s'efface, le milieu à peine, le proche garde ses couleurs.
     *
     * C'est la perspective atmosphérique, qui remplace le flou d'avant (la
     * profondeur de champ d'un appareil photo, jusqu'en 37.8.0) : au théâtre
     * de papier tout est net, la profondeur vient de l'empilement des cartons
     * et de leurs ombres, et le lointain se reconnaît à sa couleur.
     */
    private val ESTOMPE = floatArrayOf(0.30f, 0.10f, 0f)

    /**
     * Ce qui tombe sur le premier calque est plus léger : la toile de fond
     * est loin derrière les cartons, et une ombre franche sur un ciel se lit
     * comme une fumée. Moins encore pour la crête, posée sur le ciel même.
     */
    private const val OMBRE_SUR_LE_FOND = 0.6f
    private const val OMBRE_SUR_LE_CIEL = 0.35f

    /**
     * L'ombre d'un carton sur un carton de derrière, en unités de la boîte :
     * où elle tombe par plan d'écart (vers le haut, vers la droite), puis son
     * flou.
     *
     * La lumière est celle de la **rampe**, au bas de la scène et un peu à
     * gauche : elle porte les ombres vers le haut, au-dessus des crêtes, là
     * où on les voit, et de côté, le long des bords droits des cartons. Une
     * lumière d'en haut les cacherait derrière le carton même qui les porte.
     * La même rampe éclaire toutes les cartes.
     *
     * Nette et marquée (2026-10-30) : l'ombre courte et douce d'avant ne
     * séparait pas les cartons, la fenêtre se lisait comme une seule image à
     * liseré blanc. C'est le vide qu'on voit derrière chaque découpe qui fait
     * le théâtre de papier.
     */
    private val OMBRE_MONTEE = if (PAPIER) 0.35f else 1f
    private val OMBRE_COTE = if (PAPIER) 0.15f else 0.55f
    private val OMBRE_FLOU = if (PAPIER) 0.55f else 0.22f

    /**
     * Mélange [b] vers [vers] de la part [t], là où la silhouette [forme] (de
     * même taille) est pleine, sans toucher la transparence de [b].
     */
    private fun eclaircir(b: Bitmap, forme: Bitmap, vers: Int, t: Float) {
        if (t <= 0f) return
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        val mk = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        forme.getPixels(mk, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val a = mk[i] ushr 24
            if (a == 0) continue
            val c = px[i]
            px[i] = (c and 0xFF000000.toInt()) or (Heure.melange(c, vers, t * a / 255f) and 0x00FFFFFF)
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
    }

    /**
     * Peint sur [derriere] l'ombre de la silhouette [forme], posée à
     * ([dx], [dy]) pixels de son origine, [ecart] plans plus loin : plus le
     * carton de derrière est loin, plus l'ombre monte et s'étale. Elle ne
     * tombe que sur ce que [derriere] porte.
     */
    private fun ombrer(derriere: Bitmap, forme: Bitmap, dx: Int, dy: Int, unite: Float, heure: Heure,
                       ecart: Int, force: Float) {
        val flou = Paint().apply {
            maskFilter = BlurMaskFilter(max(1f, OMBRE_FLOU * unite * ecart), BlurMaskFilter.Blur.NORMAL)
        }
        val ou = IntArray(2)
        val ombre = forme.extractAlpha(flou, ou)
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            color = OMBRE
            alpha = ((if (heure.nuit) 0.7f else 0.5f) * (if (PAPIER) 0.7f else 1f) * force * 255f).roundToInt()
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        Canvas(derriere).drawBitmap(
            ombre, dx + ou[0] + OMBRE_COTE * unite * ecart, dy + ou[1] - OMBRE_MONTEE * unite * ecart, p
        )
        ombre.recycle()
    }

    /** Une ombre de carton, pas de suie : un bleu nuit presque noir. */
    private const val OMBRE = 0xFF0B1020.toInt()

    /** Ce qui n'est pas de la végétation efface le relevé : voir [Peintre.relever]. */
    private val EFFACER = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    /**
     * La gouache est un peu mince : 84 % d'opacité, le kraft transparaît
     * (maquette `peint.js`).
     */
    private const val GOUACHE_ALPHA = 214

    /** Le sens des touches courtes et obliques de la pierre, en radians. */
    private const val OBLIQUE = 1f

    /**
     * L'ombre d'une pièce sur ce qui est derrière elle dans le même carton,
     * en unités : elle monte (la rampe), et son flou, en écart type.
     */
    private const val OMBRE_PIECE_MONTEE = 0.55f
    private const val OMBRE_PIECE_FLOU = 0.55f

    /**
     * La tranche du carton, vue d'en haut : un liseré de lumière le long du
     * bord haut de chaque découpe, que la scène éclaire. Posé derrière ce que
     * le calque porte, il ne dépasse qu'en haut.
     *
     * Jusqu'au chantier gouache (2026-11-02), une bordure blanche faisait le
     * tour de chaque découpe, l'âme du papier. Le carton peint montre le sien
     * autrement : sa couleur kraft reparaît au bord de chaque pièce, là où le
     * pinceau ne l'a pas couverte (voir [Peintre.peint]). Repris de la
     * maquette `cinq_plans.html` de la skill `direction-artistique`, validée
     * par le propriétaire le 2026-11-01.
     */
    private fun trancher(b: Bitmap, forme: Bitmap, unite: Float, heure: Heure, force: Float = 1f) {
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            color = Heure.melange(heure.cielBas, 0xFFFFFFFF.toInt(), if (heure.nuit) 0.3f else 0.6f)
            alpha = ((if (heure.nuit) 0.45f else 0.7f) * force * 255f).roundToInt()
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        }
        Canvas(b).drawBitmap(forme, 0f, -max(1f, 0.3f * unite), p)
    }

    /**
     * Les bêtes de chaque lieu, où elles se tiennent dans la boîte 100 × 66,
     * et le plan qui les porte (pour glisser avec lui quand la carte penche).
     *
     * Une seule table pour les deux rendus : la vignette les peint au repos,
     * la carte ouverte les fait bouger au même endroit ([Atelier]). Une ou
     * deux par lieu, pas plus (propriétaire, 2026-10-19).
     *
     * Les barques des Saintes et les fanions de la Victoire y sont aussi, sans être des bêtes : ce qui
     * bouge sur la carte ouverte doit être tu dans ses calques et peint au
     * même endroit sur la vignette, et cette table fait exactement cela.
     */
    /**
     * Les persiennes de la place de la Victoire qui battent quand on secoue
     * le téléphone ([Peintre.persienneBattante]) : u0, y0, u1, y1 dans la rue
     * en perspective, le plan de leur maison, et leur plus grande ouverture
     * en radians (le garde-corps de l'étage arrête les battants). Les deux
     * premières sont celles de la case jaune, les deux autres de la case
     * blanche.
     */
    internal val PERSIENNES_VICTOIRE = arrayOf(
        floatArrayOf(40.5f, 11f, 46.5f, 25f, 1f, 1.3f),
        floatArrayOf(52f, 11f, 58f, 25f, 1f, 1.3f),
        floatArrayOf(67f, 15.6f, 76f, 24f, 2f, 0.8f),
        floatArrayOf(77.6f, 31f, 81.4f, 46f, 2f, 1.7f)
    )

    /** Le bougainvillier de Marie-Galante, sur le plan du milieu : x0, x1, y0, y1 de ses fleurs. */
    internal val BOUGAINVILLIER = floatArrayOf(8f, 22f, 50f, 55f)

    /** Les grappes du flamboyant de Bouillante, sur le plan de devant : x0, x1, y0, y1. */
    internal val FLAMBOYANT_BOUILLANTE = floatArrayOf(80f, 102f, 24f, 33f)

    /** Les feuillages de la mangrove de l'îlet Macou, à gauche et à droite, sur le plan du milieu : x0, x1, y0, y1. */
    internal val FEUILLAGE_MACOU_GAUCHE = floatArrayOf(-2f, 18f, 8f, 36f)

    /** Le sol de feuilles mortes du Figuier maudit, que la secousse soulève : x0, x1, y0, y1. */
    internal val FEUILLES_FIGUIER = floatArrayOf(0f, 100f, 57f, 64f)

    /** Le haut du mur en ruine du Figuier maudit, d'où se détachent les gravats : x0, x1, et le sol à son pied. */
    internal val MUR_FIGUIER = floatArrayOf(0f, 40f, 56f)

    /** La voûte de la forêt des Roches gravées, d'où tombent les feuilles : x0, x1, y0, y1. */
    internal val VOUTE_ROCHES = floatArrayOf(4f, 80f, 6f, 16f)
    internal val FEUILLAGE_MACOU_DROITE = floatArrayOf(76f, 100f, 2f, 30f)

    /** Les grappes du flamboyant de Morne-à-l'Eau, celui de Bouillante retourné : x0, x1, y0, y1. */
    internal val FLAMBOYANT_MORNE = floatArrayOf(-2f, 20f, 24f, 33f)

    /**
     * Les sources chaudes de la plage sombre de Bouillante, sur le plan du
     * milieu, au format de [VAPEURS_RAVINE] : x, pied, haut, largeur, dérive.
     * Seule la carte ouverte les fait fumer, et à peine.
     */
    internal val VAPEURS_BOUILLANTE = arrayOf(floatArrayOf(51f, 40.8f, 34f, 0.9f, -1.2f),
        floatArrayOf(58.4f, 41.4f, 35.4f, 0.8f, 1f), floatArrayOf(79.4f, 44.2f, 38.6f, 0.9f, -1.4f))

    /** Le centre de la peau du ka de Vélo, une fois la statue retournée et placée : d'où il résonne. */
    internal val PEAU_KA = floatArrayOf(16.8f, 46.4f)

    internal val BETES: Map<Lieu, List<Bete>> = mapOf(
        // Les tourterelles, plus petites au loin : sur le bord du toit et le
        // garde-corps de la case de bois, et sur celui de la case blanche ;
        // hors du milieu, que le dessin du mot couvre. Les jardinières sont
        // accrochées dehors, aux garde-corps.
        Lieu.PLACE_VICTOIRE to listOf(Bete(Espece.FANIONS, 50f, 3f, plan = 0),
            Bete(Espece.TOURTERELLE, 22.4f, 16.1f, plan = 0, taille = 0.8f),
            Bete(Espece.TOURTERELLE, 29.7f, 25.6f, plan = 0, taille = 0.8f),
            Bete(Espece.TOURTERELLE, 96.9f, 18.7f, plan = 2, taille = 1.1f),
            Bete(Espece.FLEURS, 53.5f, 23.1f, plan = 1, taille = 0.85f),
            Bete(Espece.PLANTE, 84f, 19.7f, plan = 2), Bete(Espece.PLANTE, 91.9f, 19f, plan = 2)),
        // Des pots de fleurs sur les tombes et de minuscules abeilles qui
        // butinent autour, et dans le flamboyant : tout reste à sa place
        // quand on secoue, les abeilles s'égaillent et reviennent, les
        // fleurs tremblent.
        Lieu.MORNE_A_L_EAU to listOf(Bete(Espece.FLEURS, 17f, 55.2f, plan = 2), Bete(Espece.FLEURS, 95f, 50.8f, plan = 2),
            Bete(Espece.ABEILLE, 17f, 52.2f, plan = 2), Bete(Espece.ABEILLE, 18.4f, 52.8f, plan = 2),
            Bete(Espece.ABEILLE, 95f, 47.8f, plan = 2), Bete(Espece.ABEILLE, 93.4f, 48.4f, plan = 2),
            Bete(Espece.ABEILLE, 11f, 29.6f, plan = 2), Bete(Espece.ABEILLE, 15.6f, 31.4f, plan = 2)),
        // L'iguane des Petites Antilles, dont la Désirade garde l'une des
        // dernières grandes populations : sur le plateau, à gauche du mot.
        Lieu.DESIRADE to listOf(Bete(Espece.IGUANE, 8f, 57.5f, taille = 1.1f)),
        Lieu.MARIE_GALANTE to listOf(1f to 39.4f, 7.5f to 40.6f, 13.6f to 38.6f, 17.2f to 37.2f).map { (x, y) ->
            // Sous le manguier réduit et poussé vers le bord, comme lui.
            Bete(Espece.MANGUE, 4f + (x - 4f) * MANGUIER_REDUCTION + MANGUIER_DECALAGE, 58.6f + (y - 58.6f) * MANGUIER_REDUCTION)
        },
        Lieu.LES_SAINTES to listOf(Bete(Espece.BARQUE, 30f, 40f, plan = 1), Bete(Espece.BARQUE, 47f, 42.5f, plan = 1),
            Bete(Espece.BARQUE, 64f, 39.5f, plan = 1)),
        Lieu.GRANDE_ANSE to listOf(Bete(Espece.TOULOULOU, 38f, 53f, plan = 1), Bete(Espece.TOULOULOU, 46f, 54.6f, plan = 1),
            Bete(Espece.TOULOULOU, 61f, 52.4f, plan = 1)),
        // Une tortue verte de Malendure nage dans l'eau claire du bord, à gauche du mot ;
        // une secousse la fait plonger.
        Lieu.BOUILLANTE to listOf(Bete(Espece.TORTUE, 17f, 54.6f, plan = 1, taille = 5f),
            Bete(Espece.BATEAU, 7.5f, 47.6f, plan = 1)),
        Lieu.ILET_MACOU to listOf(Bete(Espece.PELICAN_PIEU, 92f, 56f, taille = 1.7f)),
        Lieu.SOUFRIERE to listOf(Bete(Espece.SIFFLEUR, 72f, 54f)),
        // Les lianes qui pendent de la voûte, jusqu'à leur bout ; une secousse les balance.
        Lieu.ROCHES_GRAVEES to listOf(34f to 30f, 38f to 24f, 52f to 34f, 66f to 26f, 18f to 28f).map { (x, y) ->
            Bete(Espece.LIANE, x, y, plan = 0)
        },
        Lieu.GRAND_ETANG to listOf(Bete(Espece.LIBELLULE, 62f, 49f, plan = 1)),
        Lieu.BAINS_JAUNES to listOf(Bete(Espece.RATON, 80f, 40.4f, plan = 1)),
        Lieu.CASCADE_ECREVISSES to listOf(Bete(Espece.ECREVISSE, 28f, 62f, taille = 1.5f), Bete(Espece.ECREVISSE, 50f, 63.6f, taille = 1.7f),
            Bete(Espece.ECREVISSE, 68f, 61.6f, taille = 1.3f), Bete(Espece.LIBELLULE, 33f, 43f)),
        Lieu.SAUT_LEZARDE to listOf(Bete(Espece.LIBELLULE, 44f, 50f, plan = 1)),
        Lieu.GRANDE_VIGIE to listOf(Bete(Espece.BALEINE, 26f, 46f, plan = 1)),
        Lieu.PORTE_ENFER to listOf(Bete(Espece.TOULOULOU, 30f, 61f), Bete(Espece.TOULOULOU, 62f, 62.4f)),
        Lieu.PETITE_TERRE to listOf(Bete(Espece.IGUANE, 9.6f, 50.6f, taille = 1.5f), Bete(Espece.TOULOULOU, 62f, 58.6f), Bete(Espece.TOULOULOU, 78f, 60.4f)),
        Lieu.ILET_GOSIER to listOf(Bete(Espece.PELICAN, 22f, 47f, plan = 1), Bete(Espece.TOULOULOU, 40f, 61.6f), Bete(Espece.TOULOULOU, 58f, 62.6f)),
        Lieu.GRAND_CUL_DE_SAC to listOf(Bete(Espece.TORTUE, 34f, 58f, plan = 1, taille = 3f)),
        Lieu.TRAVERSEE to listOf(Bete(Espece.AGOUTI, 50f, 57f, plan = 1)),
        Lieu.GRIVELIERE to listOf(Bete(Espece.PARULINE, 22f, 52f)),
        Lieu.MATOUBA to listOf(Bete(Espece.PIC, 13.6f, 30f)),
        Lieu.FORT_DELGRES to listOf(Bete(Espece.ANOLI, 20f, 55f)),
        Lieu.MEMORIAL_ACTE to listOf(Bete(Espece.PELICAN_QUAI, 92f, 50f))
    )

    /**
     * Ce qui peint les bêtes d'un lieu dans l'ambiance de la carte ouverte,
     * avec les mêmes tracés et la même palette que le paysage : une bête qui
     * bouge ne change pas de couleur en quittant la vignette.
     */
    /**
     * Les étoiles d'un ciel de nuit, quatre nombres par étoile : x, y, rayon
     * et éclat, en unités de la boîte. Le ciel peint et [Ambiance], qui les
     * fait scintiller sur la carte ouverte, lisent le même tirage.
     */
    internal val ETOILES: FloatArray = run {
        val r = Alea(41)
        val e = FloatArray(46 * 4)
        for (k in 0 until 46) {
            e[k * 4] = r() * 100f
            e[k * 4 + 1] = r() * 38f
            e[k * 4 + 2] = 0.18f + r() * 0.38f
            e[k * 4 + 3] = if (k % 5 == 0) 1f else 0.35f + r() * 0.6f
        }
        e
    }
    internal const val COULEUR_ETOILE = 0xFFFFF8E0.toInt()

    internal class Atelier(heure: Heure) {
        private val peintre = Peintre(Canvas(), heure, decoupe = false)

        /**
         * Peint [b] à l'instant [t], en secondes ; [i] décale son rythme de
         * celui des autres. [rafale] est ce qui reste d'une secousse, de 0 à
         * 1, et [depuis] le temps écoulé depuis elle : les fanions claquent,
         * les jardinières tremblent.
         */
        fun peindre(c: Canvas, b: Bete, t: Float, i: Int, rafale: Float = 0f, depuis: Float = 0f) {
            peintre.c = c
            peintre.rafale = rafale
            peintre.depuisRafale = depuis
            peintre.bete(b, t, i, immobile = false)
        }

        /** Peint [b] au repos, à sa place : pour une image figée, qui n'a pas d'ambiance. */
        fun poser(c: Canvas, b: Bete, i: Int) {
            peintre.c = c
            peintre.rafale = 0f
            peintre.bete(b, 0f, i, immobile = true)
        }

        /** Une tourterelle ou un pélican [b] en vol, les ailes à [aile] (1 levées, négatif baissées), tourné vers [sens]. */
        fun envol(c: Canvas, b: Bete, aile: Float, sens: Float) {
            peintre.c = c
            peintre.aplat {
                if (b.espece == Espece.PELICAN_PIEU) peintre.pelicanEnVol(b.x, b.y, aile, sens, b.taille)
                else peintre.tourterelleEnVol(b.x, b.y, aile, sens, b.taille)
            }
        }

        /** La mangue [b] détachée, son fruit décalé de ([dx], [dy]), tourné de [angle] degrés et fané à [opacite]. */
        fun mangueTombee(c: Canvas, b: Bete, dx: Float, dy: Float, angle: Float, opacite: Float = 1f) {
            peintre.c = c
            peintre.aplat { peintre.mangue(b.x, b.y, dx, dy, angle, false, opacite) }
        }

        /** La mangue [b] qui repousse à sa branche, à [grosseur] de sa taille. */
        fun mangueQuiPousse(c: Canvas, b: Bete, grosseur: Float) {
            peintre.c = c
            peintre.aplat { peintre.mangue(b.x, b.y, 0f, 0f, 0f, true, grosseur = grosseur) }
        }

        /** La persienne [k] de [PERSIENNES_VICTOIRE], ses battants ouverts de [angle] radians. */
        fun persienne(c: Canvas, k: Int, angle: Float) {
            peintre.c = c
            peintre.aplat { peintre.persienneBattante(k, angle) }
        }
    }

    /**
     * Les lieux de Guadeloupe sont dessinés (2026-10-19). Éteint, une carte ne
     * montrerait que le ciel de son heure et l'album des paysages disparaîtrait.
     */
    internal const val LIEUX_DESSINES = true

    /**
     * Maquette du papier découpé en couches (skill `direction-artistique`,
     * référence du propriétaire du 2026-11-07) : papier opaque, grain,
     * tranche de kraft, ombres douces, Grand Étang redessiné. Sans arche ni
     * coulisses (l'arche de feuilles n'a pas plu, 2026-11-07).
     * Faux pour retrouver la gouache.
     */
    internal const val PAPIER = true

    /**
     * Le tuyau des douches de Sofaïa, qui traverse le bassin en diagonale
     * sur son poteau : de ([TUYAU_DOUCHES] 0, 1) à (2, 3). Il est percé en
     * dessous et porte de minuscules pommeaux, d'où tombent les filets.
     */
    internal val TUYAU_DOUCHES = floatArrayOf(16f, 50f, 72f, 37.6f)

    /** Où un filet de x [x] quitte son pommeau, sous le tuyau. */
    internal fun doucheHaut(x: Float): Float {
        val (x0, y0, x1, y1) = TUYAU_DOUCHES
        return y0 + (x - x0) / (x1 - x0) * (y1 - y0) + 1.1f
    }

    /**
     * Où le filet de x [x] touche l'eau : sous le tuyau, sur la ligne qui va
     * du pied du poteau (30 ; 61) vers le fond du bassin. Les filets du
     * devant sont donc plus longs que ceux du fond, en perspective.
     */
    internal fun doucheBas(x: Float): Float = 61f - (x - 30f) * 0.3f

    /**
     * Les douches de Sofaïa : x de chaque pommeau et l'endroit où son filet
     * touche l'eau, en deux groupes de part et d'autre du mot, pour qu'il
     * n'en cache aucun. Le dessin les peint, [Ambiance] y fait tomber les gouttes.
     */
    internal val DOUCHES = floatArrayOf(23f, 25.8f, 28.4f, 63f, 65.8f, 68.4f).map { it to doucheBas(it) }.toTypedArray()

    /** Les fumées du bassin de Ravine Chaude, sur le plan de devant : x, pied, haut, largeur et dérive de chacune. */
    internal val VAPEURS_RAVINE = arrayOf(floatArrayOf(40f, 57f, 49f, 1.4f, -2f), floatArrayOf(58f, 58f, 51f, 1.2f, 2f))

    /** Les deux fumées légères du bassin des Bains Jaunes, sur le plan du milieu : x, pied, haut, largeur, dérive. */
    internal val VAPEURS_BAINS = arrayOf(floatArrayOf(76f, 49f, 38f, 1.4f, -2f), floatArrayOf(90f, 55f, 43f, 1.4f, 1.6f))

    /** La fumée du gouffre de la Soufrière, sur le plan du milieu : x, pied, haut, largeur, dérive. */
    internal val FUMEE_SOUFRIERE = arrayOf(floatArrayOf(85.5f, 41f, 13f, 5.2f, 8f))

    /**
     * Le bord de l'eau sur la plage de Grande Anse, sur le plan du milieu :
     * l'abscisse où le sable sec s'arrête, à la hauteur y. Le dessin y pose le
     * sable mouillé et la frange d'écume, [Ambiance] y fait monter et
     * redescendre les vagues.
     */
    internal fun rivageGrandeAnse(y: Float): Float = 61f + 37f * ((y - 34f) / 32f).coerceIn(0f, 1f).pow(1.2f)

    /** Le bord gauche du Grand Étang à la hauteur y, sur le plan du milieu : le lac naît à l'horizon et s'élargit vers soi. */
    internal fun etangGauche(y: Float) = 36f - (y - 32f) / 28f * 30f

    /** Le bord droit du Grand Étang à la hauteur y. */
    internal fun etangDroite(y: Float) = 64f + (y - 32f) / 28f * 30f

    /**
     * L'eau du Grand Étang qu'on voit vraiment, sur le plan du milieu : x0,
     * x1, y0, y1. Les lisières des rives mordent sur le lac bien au-delà de
     * [etangGauche] et [etangDroite] ; ses bords visibles, relevés sur la
     * carte ouverte (2026-11-09), sont [etangVuGauche] et [etangVuDroite].
     */
    internal val EAU_ETANG = floatArrayOf(24f, 77f, 38f, 59f)

    /** Le bord gauche de l'eau visible du Grand Étang, entre les lisières, à la hauteur y. */
    internal fun etangVuGauche(y: Float) = 33f - (y - 38f) / 22f * 9f

    /** Le bord droit de l'eau visible du Grand Étang. */
    internal fun etangVuDroite(y: Float) = 72f + (y - 38f) / 22f * 5f

    /** La baie des Saintes, sur le plan du milieu, entre le pied du morne et la plage : x0, x1, y0, y1. */
    internal val EAU_SAINTES = floatArrayOf(-2f, 95f, 37.6f, 47f)

    /** La mer de Grande Anse visible sur le plan du milieu, au-dessus des raisiniers du bas : x0, x1, y0, y1, à droite du rivage. */
    internal val EAU_GRANDE_ANSE = floatArrayOf(57f, 102f, 34.6f, 49f)

    /**
     * Les raisiniers de droite de Grande Anse, sur le plan de devant : sous y
     * (premier nombre), leur feuillage cache la mer à droite de x (second).
     * L'eau animée s'y arrête, sinon elle passerait devant les feuilles.
     */
    internal val FEUILLAGE_GRANDE_ANSE = floatArrayOf(41f, 74f)

    /** L'écume du rivage de la Pointe des Châteaux, sur le plan du milieu : sa ligne. */
    internal const val RIVAGE_CHATEAUX = 39.2f

    /** La crête de la grosse vague de la Pointe des Châteaux, sur le plan de devant : de (x0, y0) à (x1, y1). */
    internal val VAGUE_CHATEAUX = floatArrayOf(17f, 44.8f, 34f, 42.6f)

    /**
     * La mer de la Désirade, sur le plan du loin : de x0 à x1, la ligne du
     * large et celle où le ressac blanchit au pied du morne. Le dessin y peint
     * l'écume au repos, [Ambiance] y fait rouler et casser le ressac.
     */
    internal val RESSAC_DESIRADE = floatArrayOf(-2f, 50f, 36f, 44.6f)

    /** Où la mer de la Désirade frappe le pied du morne, sur le plan du milieu : (x, y) de chaque gerbe. */
    internal val GERBES_DESIRADE = arrayOf(5f to 45f, 15f to 44.8f, 27f to 44.3f, 37f to 43.3f, 46f to 42.2f)

    /** Le ponton de Bouillante, sur le plan du milieu : du bateau (x0, y0) au rivage (x1, y1). */
    internal val PONTON_BOUILLANTE = floatArrayOf(13f, 46.2f, 88f, 46.6f)

    /** L'eau de Bouillante qui scintille, sur le plan du milieu : x0, x1, y0, y1. */
    internal val REFLETS_BOUILLANTE = floatArrayOf(-2f, 84f, 48f, 64f)

    /** Un caveau du cimetière de Morne-à-l'Eau : son pied, sa largeur, sa hauteur, sa sorte, le plan du lieu qui le porte. */
    internal class Caveau(val x: Float, val sol: Float, val l: Float, val h: Float, val sorte: Int, val plan: Int)

    /**
     * Les caveaux de Morne-à-l'Eau, rang après rang, du haut du morne au bas :
     * (plan du lieu, pied du rang, début, largeur et hauteur d'un caveau).
     * Les rangs montent vers la droite comme le morne sur la photo.
     */
    private val RANGS_MORNE = arrayOf(
        floatArrayOf(0f, 13f, 58f, 5f, 4.2f), floatArrayOf(0f, 19.4f, 46f, 5.6f, 4.6f),
        floatArrayOf(1f, 26f, 36f, 6.2f, 5f), floatArrayOf(1f, 32.6f, 22f, 6.8f, 5.4f),
        floatArrayOf(1f, 39.4f, 8f, 7.4f, 5.8f), floatArrayOf(1f, 46.4f, -4f, 8.2f, 6.2f))

    internal val CAVEAUX_MORNE: List<Caveau> by lazy {
        val r = Alea(2010)
        val liste = ArrayList<Caveau>()
        for ((plan, sol, debut, l, h) in RANGS_MORNE) {
            var x = debut - r() * l * 0.5f
            while (x < LARGEUR + 2f) {
                val w = l * (0.8f + r() * 0.4f)
                liste += Caveau(x, sol + (r() - 0.5f) * 0.6f, w, h * (0.85f + r() * 0.3f), (r() * 4f).toInt().coerceAtMost(3), plan.toInt())
                x += w + 0.25f + r() * 0.6f
            }
        }
        liste
    }

    /** Les caveaux à toit plat, ceux qui portent des bougies le soir et la nuit. */
    internal const val CAVEAU_PLAT = 1

    /**
     * Les bougies de Morne-à-l'Eau : x, y et plan du lieu de chacune, posées
     * sur le rebord des caveaux à toit plat, là où le rang de devant ne les
     * cache pas. Le dessin les peint, [Ambiance] fait vaciller leur flamme.
     */
    internal val BOUGIES_MORNE: List<FloatArray> by lazy {
        val r = Alea(2011)
        CAVEAUX_MORNE.filter { it.sorte == CAVEAU_PLAT }.flatMap { c ->
            (0 until 1 + (r() * 2f).toInt()).map { k -> floatArrayOf(c.x + c.l * (0.2f + 0.3f * k + r() * 0.1f), c.sol - c.h - 0.35f, c.plan.toFloat()) }
        } + listOf(floatArrayOf(82f, 52.2f, 2f), floatArrayOf(88f, 51.8f, 2f), floatArrayOf(10f, 55.4f, 2f), floatArrayOf(20f, 55f, 2f))
    }

    /** L'eau de la mangrove de l'Îlet Macou, sur le plan du milieu : x0, x1, y0, y1. */
    internal val EAU_MACOU = floatArrayOf(-2f, 102f, 48f, 64f)

    /** Le lagon du Grand Cul-de-Sac Marin, à gauche de l'îlet, sur le plan du milieu : x0, x1, y0, y1. */
    /** Le lagon de la Porte d'Enfer, sur le plan du milieu : x0, x1, y0, y1. */
    internal val EAU_PORTE_ENFER = floatArrayOf(-2f, 102f, 42f, 56f)

    /** Le lagon devant l'Îlet du Gosier, sur le plan du milieu : x0, x1, y0, y1. */
    /** Le lagon de Petite-Terre, sur le plan du milieu : x0, x1, y0, y1. */
    internal val EAU_PETITE_TERRE = floatArrayOf(-2f, 102f, 41f, 52f)

    /** L'océan sous Gueule Grand Gouffre, sur le plan du fond : x0, x1, y0, y1. */
    internal val EAU_GOUFFRE = floatArrayOf(-2f, 66f, 16f, 42f)

    /** L'océan de la Grande Vigie, à gauche de la falaise, sur le plan du milieu : x0, x1, y0, y1. */
    internal val EAU_VIGIE = floatArrayOf(-2f, 56f, 32f, 56f)

    internal val EAU_GOSIER = floatArrayOf(-2f, 60f, 46f, 58f)

    internal val EAU_CUL_DE_SAC = floatArrayOf(-2f, 60f, 44f, 62f)

    /** L'eau de la baie devant le Mémorial ACTe, sur le plan de devant : x0, x1, y0, y1. */
    internal val EAU_ACTE = floatArrayOf(-2f, 76f, 52f, 64f)

    /** La lanterne du phare de la Désirade, sur le plan du milieu : d'où part le faisceau, allumé le soir et la nuit. */
    internal val PHARE_DESIRADE = floatArrayOf(89f, 16.9f)

    /**
     * La réduction du moulin de Marie-Galante, autour du pied de sa tour (86,
     * 57) et décalé de 1,4 vers le bord : sans elle l'arche des cartes rares
     * coupait le bout des ailes.
     */
    internal const val MOULIN_REDUCTION = 0.8f

    /**
     * La réduction du manguier de Marie-Galante autour du pied de son tronc
     * (4, 58,6), et son décalage vers le bord : la place qu'il laisse à
     * l'Habitation Murat, entre lui et le mot (propriétaire, 2026-11-09).
     */
    internal const val MANGUIER_REDUCTION = 0.85f
    internal const val MANGUIER_DECALAGE = -3f

    /** Le moulin de Marie-Galante, sur le plan du milieu : le moyeu (x, y) et la longueur d'une aile, une fois réduit. */
    internal val MOULIN = floatArrayOf(86f + (87.4f - 86f) * MOULIN_REDUCTION + 1.4f,
        57f - (57f - 23.4f) * MOULIN_REDUCTION, 14f * MOULIN_REDUCTION)

    /** L'angle des ailes à l'arrêt, en degrés : en X, comme sur la photo. */
    internal const val ANGLE_MOULIN = 22f

    /** Un tour d'ailes, en millisecondes : lent, un moulin par petite brise. */
    internal const val TOUR_MOULIN = 18_000L

    /** Le bois des ailes à l'heure [heure], teinté comme le reste du lieu (`Peintre.duJour`). */
    internal fun couleurAiles(heure: Heure): Int =
        Heure.melange(0xFFCDB890.toInt(), heure.pres, floatArrayOf(0.04f, 0.14f, 0.42f, 0.72f)[heure.rang])

    /** Une couleur du lieu à l'heure [heure], comme `Peintre.duJour` : pour ce que les ambiances peignent. */
    internal fun aLHeure(col: Int, heure: Heure): Int =
        Heure.melange(col, heure.pres, floatArrayOf(0.04f, 0.14f, 0.42f, 0.72f)[heure.rang])

    /** Le moyeu, foncé, à l'heure [heure]. */
    internal fun couleurMoyeu(heure: Heure): Int =
        Heure.melange(0xFF4A3A2E.toInt(), heure.pres, floatArrayOf(0.04f, 0.14f, 0.42f, 0.72f)[heure.rang])

    /**
     * Les quatre ailes du moulin tournées de [angle] degrés : chacune un bras
     * et, d'un côté, un cadre à claire-voie (deux longerons et des barreaux).
     * Une seule forme, peinte par la vignette et redessinée à chaque trame
     * par l'ambiance de la carte ouverte.
     */
    internal fun ailesMoulin(angle: Float): Path {
        val (hx, hy, r) = MOULIN
        val t = Path()
        fun barreau(ax: Float, ay: Float, bx: Float, by: Float, e: Float) {
            val dx = bx - ax; val dy = by - ay
            val n = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val nx = -dy / n * e / 2f; val ny = dx / n * e / 2f
            t.moveTo(ax + nx, ay + ny); t.lineTo(bx + nx, by + ny); t.lineTo(bx - nx, by - ny); t.lineTo(ax - nx, ay - ny); t.close()
        }
        for (k in 0 until 4) {
            val a = Math.toRadians((angle + k * 90f).toDouble())
            val dx = kotlin.math.cos(a).toFloat(); val dy = kotlin.math.sin(a).toFloat()
            val nx = -dy; val ny = dx
            barreau(hx, hy, hx + dx * r, hy + dy * r, 0.7f)
            val w = r * 0.24f
            val d0 = r * 0.26f
            barreau(hx + dx * d0 + nx * w, hy + dy * d0 + ny * w, hx + dx * r + nx * w, hy + dy * r + ny * w, 0.4f)
            barreau(hx + dx * d0 + nx * w * 0.5f, hy + dy * d0 + ny * w * 0.5f, hx + dx * r + nx * w * 0.5f, hy + dy * r + ny * w * 0.5f, 0.2f)
            var d = d0
            while (d <= r + 0.01f) {
                barreau(hx + dx * d, hy + dy * d, hx + dx * d + nx * w, hy + dy * d + ny * w, 0.26f)
                d += r * 0.0925f
            }
        }
        t.fillType = Path.FillType.WINDING
        return t
    }

    /** La chute du Carbet, sur le plan du milieu : son x, son haut, son pied et sa largeur. */
    internal val CHUTE_CARBET = floatArrayOf(80f, 12f, 47f, 4.6f)

    /** La chute du Saut de la Lézarde, à droite du mot, sur le plan du milieu : x, haut, bas, largeur. */
    /** La Cascade aux Écrevisses, ses deux bras de part et d'autre du rocher, sur le plan du milieu : x, haut, bas, largeur. */
    internal val CHUTE_ECREVISSES = floatArrayOf(79f, 18f, 45f, 9f)

    internal val CHUTE_LEZARDE = floatArrayOf(86f, 17f, 39.4f, 6f)
    fun dessiner(c: Canvas, lieu: Lieu, heure: Heure, ciel: Boolean = true, lieuAussi: Boolean = true,
                 seul: Int = -1, faune: Boolean = true, etoiles: Boolean = true,
                 facture: Facture = Facture.VIGNETTE, echelle: Float = 1f, releve: Canvas? = null) {
        val p = Peintre(c, heure, seul, facture, echelle, releve, faune = faune)
        if (ciel) { if (PAPIER) p.cielPapier() else if (facture == Facture.GOUACHE) p.cielPeint(etoiles) else p.ciel(etoiles) }
        if (!lieuAussi || !LIEUX_DESSINES) return
        when (lieu) {
            Lieu.GRANDE_ANSE -> p.grandeAnse()
            Lieu.LES_SAINTES -> p.lesSaintes()
            Lieu.SOUFRIERE -> p.soufriere()
            Lieu.CHUTES_CARBET -> p.chutesCarbet()
            Lieu.SOFAIA -> p.sofaia()
            Lieu.RAVINE_CHAUDE -> p.ravineChaude()
            Lieu.POINTE_CHATEAUX -> p.pointeChateaux()
            Lieu.MARIE_GALANTE -> p.marieGalante()
            Lieu.DESIRADE -> p.desirade()
            Lieu.BOUILLANTE -> p.bouillante()
            Lieu.MORNE_A_L_EAU -> p.morneALEau()
            Lieu.ILET_MACOU -> p.iletMacou()
            Lieu.ROCHES_GRAVEES -> p.rochesGravees()
            Lieu.PLACE_VICTOIRE -> p.placeVictoire()
            Lieu.FIGUIER_MAUDIT -> p.figuierMaudit()
            Lieu.GRAND_ETANG -> if (PAPIER) p.grandEtangPapier() else p.grandEtang()
            Lieu.BAINS_JAUNES -> p.bainsJaunes()
            Lieu.CASCADE_ECREVISSES -> p.cascadeEcrevisses()
            Lieu.SAUT_LEZARDE -> p.sautLezarde()
            Lieu.GRANDE_VIGIE -> p.grandeVigie()
            Lieu.PORTE_ENFER -> p.porteEnfer()
            Lieu.PETITE_TERRE -> p.petiteTerre()
            Lieu.ILET_GOSIER -> p.iletGosier()
            Lieu.GRAND_CUL_DE_SAC -> p.grandCulDeSac()
            Lieu.GUEULE_GRAND_GOUFFRE -> p.gueuleGrandGouffre()
            Lieu.TRAVERSEE -> p.traversee()
            Lieu.GRIVELIERE -> p.griveliere()
            Lieu.MATOUBA -> p.matouba()
            Lieu.FORT_DELGRES -> p.fortDelgres()
            Lieu.MEMORIAL_ACTE -> p.memorialActe()
        }
        // Les bêtes au repos, par-dessus le lieu. La carte ouverte les tait
        // ici et les fait vivre dans son ambiance (voir [Atelier]).
        if (faune) BETES[lieu]?.forEachIndexed { i, b -> p.bete(b, 0f, i, immobile = true) }
    }

    /** Un canevas sur [b], à l'échelle [k] d'un [cadrage]. */
    private fun aLEchelle(b: Bitmap, k: FloatArray) = Canvas(b).apply { translate(k[1], k[2]); scale(k[0], k[0]) }

    /**
     * Le hasard à graine fixe (mulberry32) : le même que celui de la maquette,
     * pour que l'appli dessine exactement les lieux qui ont été choisis.
     */
    internal class Alea(graine: Int) {
        private var s = graine
        operator fun invoke(): Float {
            s += 0x6D2B79F5
            var t = s
            t = (t xor (t ushr 15)) * (1 or t)
            t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
        }
    }

    /**
     * Un chemin écrit comme en SVG, en coordonnées absolues : M, L, H, V, C, Q, Z.
     *
     * Les lieux ont été mis au point en SVG ; recopier leurs contours tels
     * quels évite de les retraduire à la main, ce qui est l'endroit où une
     * colline se retrouve à l'envers.
     */
    internal fun chemin(d: String): Path {
        val path = Path()
        val jetons = Regex("[MLHVCQZmlhvcqz]|-?\\d*\\.?\\d+(?:[eE]-?\\d+)?").findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        var x = 0f
        var y = 0f
        fun n(): Float = jetons[i++].toFloat()
        while (i < jetons.size) {
            val j = jetons[i]
            if (j[0].isLetter()) { cmd = j[0]; i++; if (cmd == 'Z' || cmd == 'z') { path.close(); continue } }
            when (cmd) {
                'M' -> { x = n(); y = n(); path.moveTo(x, y); cmd = 'L' }
                'L' -> { x = n(); y = n(); path.lineTo(x, y) }
                'H' -> { x = n(); path.lineTo(x, y) }
                'V' -> { y = n(); path.lineTo(x, y) }
                'C' -> {
                    val a = n(); val b = n(); val cc = n(); val dd = n(); x = n(); y = n()
                    path.cubicTo(a, b, cc, dd, x, y)
                }
                'Q' -> { val a = n(); val b = n(); x = n(); y = n(); path.quadTo(a, b, x, y) }
                else -> i++
            }
        }
        return path
    }

    /**
     * Le feuillage d'une coulisse, côté gauche, en unités du paysage : x, y,
     * rayon. Il passe sous l'arche du cadre des rares, qui mange les coins
     * hauts, et ne descend pas sous la moitié de la fenêtre ; la droite en
     * est le miroir.
     */
    private val FEUILLES = floatArrayOf(
        0f, 12f, 7.5f, 6f, 9.5f, 6f, 11f, 13f, 4.6f, 14.5f, 17.5f, 3.4f,
        2f, 19.5f, 6.5f, 8f, 21f, 4.6f, 1f, 27f, 5.5f, 5.5f, 29.5f, 3.6f, 11.5f, 25f, 2.8f
    )

    /**
     * La seconde rangée de coulisses ([Peintre.coulissesDuFond]), côté
     * gauche, comme [FEUILLES] : un arbre plus petit, un pas en retrait, qui
     * dépasse sous et à côté du feuillage de devant.
     */
    private val FEUILLES_DU_FOND = floatArrayOf(
        13f, 27f, 4.6f, 18.5f, 29.5f, 4f, 15.5f, 33.5f, 4.4f, 20.5f, 35f, 3f, 11.5f, 32f, 3.4f, 16.5f, 23.5f, 3.4f
    )

    /**
     * Comment les grandes pièces d'un lieu sont peintes.
     *
     * - [VIGNETTE] : des aplats, avec le bord de carton seul. En petit, des
     *   coups de pinceau feraient du bruit, et trente vignettes se peignent
     *   à l'ouverture du carnet.
     * - [GOUACHE] : le carton peint de la carte ouverte et de la carte
     *   partagée, avec son ombre, ses coups de pinceau et son bord sec.
     */
    enum class Facture { VIGNETTE, GOUACHE }

    /**
     * Les coups de pinceau, préparés d'avance en tuiles qui se répètent sans
     * couture, puis posés dans le sens de chaque pièce par la matrice de leur
     * [BitmapShader].
     *
     * La maquette (`peint.js`) trace jusqu'à sept cents touches de trois poils
     * par pièce : des milliers de tracés par carte, trop pour un Galaxy A21s.
     * Une touche y est une version plus claire ou plus sombre de la couleur de
     * la pièce, posée en transparence ; c'est exactement un voile de blanc ou
     * de noir, si bien qu'une seule tuile sert à toutes les couleurs. Deux
     * tuiles : des touches longues (le ciel, l'eau, les collines, le
     * feuillage, tournées selon la pièce) et des touches courtes et obliques
     * (la pierre et le grès des pièces hautes).
     */
    private object Pinceaux {
        /** Le côté d'une tuile, en unités du paysage. */
        const val TUILE = 24f

        /** Les touches par unité carrée, comme la maquette sur une pièce moyenne. */
        private const val DENSITE = 0.5f

        private val tuiles = HashMap<String, Bitmap>()

        /**
         * La tuile à [echelle] pixels par unité, bornée : au-delà de dix, sur
         * la carte imprimée, elle est agrandie au rendu, et des touches de
         * plusieurs unités supportent très bien l'agrandissement.
         */
        @Synchronized
        fun tuile(courte: Boolean, echelle: Float): Bitmap {
            val q = (echelle.coerceIn(2f, 10f) * 2f).roundToInt() / 2f
            val cle = "${courte}_$q"
            tuiles[cle]?.takeIf { !it.isRecycled }?.let { return it }
            return dessiner(courte, q).also { tuiles[cle] = it }
        }

        private fun dessiner(courte: Boolean, q: Float): Bitmap {
            val n = ceil(TUILE * q).toInt()
            val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.scale(n / TUILE, n / TUILE)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
            val t = Path()
            val r = Alea(if (courte) 77 else 55)
            val decalages = FloatArray(3)
            repeat((TUILE * TUILE * DENSITE).roundToInt()) {
                val x = r() * TUILE
                val y = r() * TUILE
                val a = (r() - 0.5f) * (if (courte) 1.4f else 0.5f)
                val l = if (courte) 1.5f + r() * 3f else 2.5f + r() * 6f
                val w = 0.9f + r() * 1.3f
                // Plus claire ou plus sombre que la pièce, comme la maquette :
                // un voile de blanc ou de noir.
                val v = (r() - 0.5f) * 0.34f
                val blanc = v > 0f
                val force = if (blanc) v * 1.4f else -v
                val dx = cos(a) * l / 2f
                val dy = sin(a) * l / 2f
                val px = -sin(a)
                val py = cos(a)
                val portee = l / 2f + w
                for (poil in -1..1) {
                    p.color = if (blanc) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                    p.alpha = ((0.4f + r() * 0.3f) * force * 255f).roundToInt()
                    p.strokeWidth = w * 0.45f
                    val ox = px * poil * w * 0.35f
                    val oy = py * poil * w * 0.35f
                    val jx = px * (r() - 0.5f) * 0.6f
                    val jy = py * (r() - 0.5f) * 0.6f
                    // La tuile se répète : une touche qui déborde est aussi
                    // peinte de l'autre côté.
                    val nx = repliements(x, portee, decalages)
                    val xs = decalages.copyOf(nx)
                    val ny = repliements(y, portee, decalages)
                    for (i in 0 until nx) for (k in 0 until ny) {
                        val ex = xs[i] + ox
                        val ey = decalages[k] + oy
                        t.rewind()
                        t.moveTo(x - dx + ex, y - dy + ey)
                        t.quadTo(x + ex + jx, y + ey + jy, x + dx + ex, y + dy + ey)
                        c.drawPath(t, p)
                    }
                }
            }
            return b
        }

        /** Les décalages où peindre une touche en [v] de [portee] pour que la tuile se raccorde. */
        private fun repliements(v: Float, portee: Float, sortie: FloatArray): Int {
            var n = 0
            sortie[n++] = 0f
            if (v - portee < 0f) sortie[n++] = TUILE
            if (v + portee > TUILE) sortie[n++] = -TUILE
            return n
        }
    }

    /** Tout l'outillage de tracé d'un lieu, sur un canevas déjà à l'échelle. */
    private class Peintre(
        var c: Canvas, val P: Heure, private val seul: Int = -1,
        private val facture: Facture = Facture.VIGNETTE,
        /** Les pixels par unité du canevas : la finesse des coups de pinceau. */
        private val echelle: Float = 1f,
        /**
         * Le relevé de la végétation, à la même échelle que [c] : chaque
         * pièce y est posée pleine si sa couleur, avant la gouache, est de la
         * végétation ([Habillage.vegetal]), et l'efface sinon, si bien que la
         * pièce de devant décide. [Habillage] le lit au lieu de deviner pixel
         * par pixel sur le carton peint. Les pièces peintes dans [mineral]
         * n'en sont jamais.
         */
        private val releve: Canvas? = null,
        /**
         * Faux pour l'[Atelier], qui repeint les bêtes à chaque trame : rien
         * n'y devient un carton, une bête qui bouge reste un aplat.
         */
        private val decoupe: Boolean = true,
        /** Faux sur les calques de la carte ouverte : ce qui bouge y est repeint par l'ambiance. */
        val faune: Boolean = true
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val BAS = HAUTEUR

        private val releveP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val vegetaux = HashMap<Int, Boolean>()
        private var mineraux = 0

        /**
         * Peint [dessin] en pierre, bois, enduit ou métal : quelle que soit sa
         * couleur, la saison n'y verra jamais de végétation. Le soir et la
         * nuit, les palettes rapprochent la pierre des mornes, et le carême
         * aurait jauni les cases et les tombeaux.
         */
        fun mineral(dessin: () -> Unit) {
            mineraux++
            try { dessin() } finally { mineraux-- }
        }

        /** Pose sur le [releve] une pièce de couleur [col], tracée par [trace]. */
        private fun relever(col: Int, o: Float, trace: (Canvas, Paint) -> Unit) {
            val r = releve ?: return
            if (o < 0.95f || !actif) return
            val vegetal = mineraux == 0 && vegetaux.getOrPut(col) { Habillage.vegetal(col, P) }
            releveP.style = Paint.Style.FILL
            releveP.xfermode = if (vegetal) null else EFFACER
            releveP.color = 0xFFFFFFFF.toInt()
            trace(r, releveP)
        }

        /**
         * Le plan où l'on peint : 0 le lointain, 1 le milieu, 2 le proche.
         * Chaque lieu le fait avancer à mesure qu'il approche, jamais reculer.
         */
        private var plan = 0

        /**
         * Faux quand on ne rend qu'un plan et que ce n'est pas celui-ci : le
         * tracé est tu, mais tout le reste se déroule, si bien que les tirages
         * au sort restent les mêmes et qu'un plan seul tombe exactement sur
         * le lieu entier.
         */
        val actif: Boolean get() = seul < 0 || plan == seul

        fun plan(n: Int) { plan = n }

        fun mix(a: Int, b: Int, t: Float) = Heure.melange(a, b, t)

        private companion object {
            /** Le siffleur des montagnes, gris ardoise ; la paruline caféiette, vert olive. */
            const val COULEUR_SIFFLEUR = 0xFF4E5560.toInt()
            const val COULEUR_PARULINE = 0xFF8C8A3A.toInt()

            // La palette de la référence papier découpé (skill `direction-artistique`).
            const val CREME = 0xFFFCF5EC.toInt()
            const val TURQUOISE = 0xFF00AEA9.toInt()
            const val VERT_D_EAU = 0xFF30D5AD.toInt()
            const val EAU_CLAIRE = 0xFFC6F2D2.toInt()
            const val MENTHE = 0xFF91E1AF.toInt()
            const val SAUGE = 0xFF9FAD7A.toInt()
            const val LIME = 0xFFB9CF7D.toInt()
            const val SARCELLE = 0xFF1E8E7A.toInt()
            const val VERT_PROFOND = 0xFF214C40.toInt()
            const val PRUNE = 0xFF793437.toInt()
            const val TERRE = 0xFF935E4C.toInt()
            const val BLEU_PROFOND = 0xFF00446F.toInt()
            const val LAVANDE = 0xFF767DB2.toInt()
            const val MOUTARDE = 0xFFD9A93A.toInt()
        }

        /** Une opacité de plus sur tout ce qui se peint : une bête qui paraît ou s'efface. */
        var voile = 1f

        /** Plus que zéro le temps d'une bête : voir [aplat]. */
        private var aplats = 0

        /**
         * Peint [dessin] en aplats, sans carton : les bêtes. Peintes au repos
         * dans la vignette, elles doivent être celles que l'[Atelier] fait
         * bouger sur la carte ouverte, qui ne découpe rien.
         */
        fun aplat(dessin: () -> Unit) {
            aplats++
            try { dessin() } finally { aplats-- }
        }

        fun teinte(col: Int, opacite: Float) {
            p.shader = null
            p.color = col
            p.alpha = (opacite * voile * 255f).roundToInt().coerceIn(0, 255)
        }

        fun rect(x: Float, y: Float, w: Float, h: Float, col: Int, o: Float = 1f) {
            if (!actif) return
            if (enCarton(w, h, o)) {
                val t = Path()
                t.moveTo(x, y); t.lineTo(x + w, y); t.lineTo(x + w, y + h); t.lineTo(x, y + h); t.close()
                boite.set(x, y, x + w, y + h)
                peint(t, col, sensDe(boite), boite)
                return
            }
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawRect(x, y, x + w, y + h, p)
            relever(col, o) { r, rp -> r.drawRect(x, y, x + w, y + h, rp) }
        }

        fun rond(cx: Float, cy: Float, r: Float, col: Int, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawCircle(cx, cy, r, p)
            relever(col, o) { rc, rp -> rc.drawCircle(cx, cy, r, rp) }
        }

        fun forme(d: String, col: Int, o: Float = 1f) {
            if (!actif) return
            forme(chemin(d), col, o)
        }

        fun forme(path: Path, col: Int, o: Float = 1f) {
            if (!actif) return
            if (o >= 0.95f) {
                path.computeBounds(boite, true)
                if (enCarton(boite.width(), boite.height(), o)) {
                    peint(path, col, sensDe(boite), boite)
                    return
                }
            }
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawPath(path, p)
            relever(col, o) { r, rp -> r.drawPath(path, rp) }
        }

        // ------------------------------------------------- le carton peint

        private val boite = RectF()

        /**
         * Vrai pour une pièce assez grande pour être un carton peint : les
         * petits détails (fenêtres, joints, lanternes), les bandes trop fines
         * pour un bord de carton (le liseré clair du pont) et ce qui est posé
         * en transparence (ombres, reflets, brume) restent des aplats nets.
         */
        private fun enCarton(w: Float, h: Float, o: Float) =
            decoupe && aplats == 0 && o >= 0.95f && w * h >= 14f && min(w, h) >= 1.2f

        /** Le sens des coups de pinceau : horizontaux, ou courts et obliques sur une pièce haute (la pierre). */
        private fun sensDe(b: RectF) = if (b.height() > b.width() * 1.4f) OBLIQUE else 0f

        /** La couleur du carton, un brun kraft que la nuit assombrit. */
        private val kraft = mix(0xFFB08A5A.toInt(), P.pres, if (P.nuit) 0.55f else 0.18f)

        private val gouache by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
        private val ombreCarton by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }
        private val bord by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE } }
        private val brosses = HashMap<Boolean, BitmapShader>()
        private val placeBrosse = Matrix()
        private val versPixels = Matrix()

        /**
         * Une pièce de carton peint, comme `peint` dans la maquette
         * (`peint.js` de la skill `direction-artistique`) :
         *
         * 1. le carton kraft, avec son ombre, que la rampe porte vers le haut ;
         * 2. la gouache, mate et un peu mince : le kraft transparaît ;
         * 3. les coups de pinceau dans le sens de la forme ([dir], en radians :
         *    0 horizontaux, [OBLIQUE] courts et obliques), pris dans
         *    [Pinceaux] ;
         * 4. le bord sec : le pinceau n'a pas couvert jusqu'à la découpe, le
         *    carton reparaît par endroits. C'est la tranche brune qui a
         *    remplacé la bordure blanche du papier.
         *
         * En [Facture.VIGNETTE], seuls le carton, l'aplat et le bord sec.
         *
         * [union] : une forme faite de plusieurs morceaux qui se chevauchent
         * (un feuillage de ronds) ; son bord de carton se pose à l'extérieur
         * seulement, faute de quoi chaque rond garderait le sien.
         */
        fun peint(chemin: Path, col: Int, dir: Float, b: RectF, ombre: Float = 1f, union: Boolean = false) {
            if (!actif) return
            relever(col, 1f) { r, rp -> r.drawPath(chemin, rp) }
            val plein = facture == Facture.GOUACHE
            if (PAPIER) {
                // Le papier découpé : une feuille opaque, son ombre douce, et
                // dessous une fine tranche de kraft qui dépasse en bas à droite.
                if (plein && ombre > 0f) ombrerPapier(chemin, ombre)
                gouache.shader = null
                gouache.style = Paint.Style.FILL
                if (plein && ombre > 0f) {
                    c.save()
                    c.translate(0.18f, 0.4f)
                    gouache.color = kraft
                    c.drawPath(chemin, gouache)
                    c.restore()
                }
                gouache.color = col
                c.drawPath(chemin, gouache)
                return
            }
            if (plein) ombrer(chemin, ombre)
            gouache.shader = null
            gouache.style = Paint.Style.FILL
            gouache.color = kraft
            c.drawPath(chemin, gouache)
            if (union) {
                bord.pathEffect = null
                bord.color = kraft
                bord.strokeWidth = 0.9f
                c.drawPath(chemin, bord)
            }
            c.save()
            c.clipPath(chemin)
            gouache.color = col
            gouache.alpha = GOUACHE_ALPHA
            c.drawPath(chemin, gouache)
            val r = Alea(((b.left * 131f + b.top * 71f + b.right * 17f + b.bottom) * 10f).roundToInt())
            if (plein) brosser(dir, b, r)
            if (!union) {
                bord.color = kraft
                bord.alpha = 217
                bord.strokeWidth = 0.9f
                bord.pathEffect = DashPathEffect(
                    floatArrayOf(0.6f + r() * 1.4f, 0.4f + r() * 1.2f, 1.5f + r(), 0.8f, 0.3f, 1.1f), 0f
                )
                c.drawPath(chemin, bord)
                bord.pathEffect = null
                bord.alpha = 89
                bord.strokeWidth = 1.3f
                c.drawPath(chemin, bord)
            }
            c.restore()
        }

        /**
         * L'ombre du carton sur ce qui est derrière lui dans le même calque,
         * tracée en pixels : le flou d'un `BlurMaskFilter` ne suit pas
         * toujours l'échelle du canevas.
         */
        @Suppress("DEPRECATION")
        private fun ombrer(chemin: Path, force: Float) {
            val m = c.matrix
            val enPixels = Path(chemin)
            enPixels.transform(m)
            enPixels.offset(0f, -OMBRE_PIECE_MONTEE * echelle)
            val sigma = OMBRE_PIECE_FLOU * echelle
            ombreCarton.maskFilter = BlurMaskFilter(max(0.5f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
            ombreCarton.color = OMBRE
            ombreCarton.alpha = ((if (P.nuit) 0.5f else 0.32f) * force * 255f).roundToInt()
            c.save()
            c.setMatrix(versPixels)
            c.drawPath(enPixels, ombreCarton)
            c.restore()
        }

        /**
         * L'ombre d'une feuille de papier sur ce qui est derrière elle : à
         * peine décalée vers le bas, douce, dense contre le bord découpé.
         */
        @Suppress("DEPRECATION")
        private fun ombrerPapier(chemin: Path, force: Float) {
            val enPixels = Path(chemin)
            enPixels.transform(c.matrix)
            enPixels.offset(0f, 0.2f * echelle)
            ombreCarton.maskFilter = BlurMaskFilter(max(0.5f, 0.5f * echelle), BlurMaskFilter.Blur.NORMAL)
            ombreCarton.color = OMBRE
            ombreCarton.alpha = ((if (P.nuit) 0.55f else 0.4f) * force * 255f).roundToInt().coerceAtMost(255)
            c.save()
            c.setMatrix(versPixels)
            c.drawPath(enPixels, ombreCarton)
            c.restore()
        }

        /** Les coups de pinceau sur la pièce, déjà découpée par le canevas. */
        private fun brosser(dir: Float, b: RectF, r: Alea) {
            val courte = dir == OBLIQUE
            val tuile = Pinceaux.tuile(courte, echelle)
            val brosse = brosses.getOrPut(courte) {
                BitmapShader(tuile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
            val unite = Pinceaux.TUILE / tuile.width
            placeBrosse.setScale(unite, unite)
            placeBrosse.postTranslate(r() * Pinceaux.TUILE, r() * Pinceaux.TUILE)
            placeBrosse.postRotate(Math.toDegrees(dir.toDouble()).toFloat(), b.centerX(), b.centerY())
            brosse.setLocalMatrix(placeBrosse)
            gouache.color = 0xFF000000.toInt()
            gouache.shader = brosse
            gouache.isFilterBitmap = true
            c.drawRect(b.left - 2f, b.top - 2f, b.right + 2f, b.bottom + 2f, gouache)
            gouache.shader = null
        }

        /**
         * Le ciel peint, comme `cielPeint` dans la maquette : le dégradé, de
         * larges touches horizontales un peu plus claires ou plus sombres,
         * puis l'astre, peint après d'un seul aplat.
         */
        fun cielPeint(etoiles: Boolean = true) {
            ciel(etoiles)
            val r = Alea(5)
            val t = Path()
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 260) {
                val x = r() * 110f - 5f
                val y = r() * 46f
                val l = 6f + r() * 12f
                // La couleur de la touche suit un dégradé du haut vers le bas
                // sans le rose du milieu : c'est ce qui la fait voir.
                val base = mix(P.cielHaut, P.cielBas, if (y < 23f) y / 46f else y / 40f)
                val v = if (r() < 0.5f) mix(base, 0xFFFFFFFF.toInt(), 0.12f) else mix(base, 0xFF000000.toInt(), 0.05f)
                teinte(v, 0.3f + r() * 0.2f)
                p.strokeWidth = 1.4f + r() * 2f
                t.rewind()
                t.moveTo(x, y)
                t.quadTo(x + l / 2f, y + (r() - 0.5f) * 0.8f, x + l, y + (r() - 0.5f) * 0.6f)
                if (actif) c.drawPath(t, p)
            }
            p.style = Paint.Style.FILL
            p.strokeCap = Paint.Cap.BUTT
            if (!P.nuit) rond(P.astreX, P.astreY, P.astreR, P.astre, 0.95f)
        }


        fun trait(d: String, col: Int, largeur: Float, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; p.strokeCap = Paint.Cap.BUTT
            teinte(col, o)
            val t = chemin(d)
            c.drawPath(t, p)
            p.style = Paint.Style.FILL
            relever(col, o) { r, rp -> rp.style = Paint.Style.STROKE; rp.strokeWidth = largeur; r.drawPath(t, rp) }
        }

        fun ligne(x1: Float, y1: Float, x2: Float, y2: Float, col: Int, largeur: Float, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; teinte(col, o)
            c.drawLine(x1, y1, x2, y2, p)
            p.style = Paint.Style.FILL
            relever(col, o) { r, rp -> rp.style = Paint.Style.STROKE; rp.strokeWidth = largeur; r.drawLine(x1, y1, x2, y2, rp) }
        }

        fun anneau(cx: Float, cy: Float, r: Float, col: Int, largeur: Float) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; teinte(col, 1f)
            c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.FILL
            relever(col, 1f) { rc, rp -> rp.style = Paint.Style.STROKE; rp.strokeWidth = largeur; rc.drawCircle(cx, cy, r, rp) }
        }

        fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, col: Int, o: Float = 1f) {
            val t = Path(); t.moveTo(x1, y1); t.lineTo(x2, y2); t.lineTo(x3, y3); t.close()
            forme(t, col, o)
        }

        // ------------------------------------------------------ les plans

        fun ciel(etoiles: Boolean = true) {
            val couleurs = P.cielMilieu?.let { intArrayOf(P.cielHaut, it, P.cielBas) }
                ?: intArrayOf(P.cielHaut, P.cielBas)
            val positions = if (P.cielMilieu != null) floatArrayOf(0f, 0.58f, 1f) else null
            p.style = Paint.Style.FILL
            p.color = 0xFF000000.toInt()
            p.shader = LinearGradient(0f, 0f, 0f, BAS, couleurs, positions, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, LARGEUR, BAS, p)
            p.shader = null

            val transparent = P.astre and 0x00FFFFFF
            fun halo(r: Float, o: Float) {
                p.color = 0xFF000000.toInt()
                p.alpha = (o * 255f).roundToInt()
                p.shader = RadialGradient(
                    P.astreX, P.astreY, r,
                    intArrayOf((0xD9 shl 24) or (P.astre and 0xFFFFFF), transparent),
                    null, Shader.TileMode.CLAMP
                )
                c.drawCircle(P.astreX, P.astreY, r, p)
                p.shader = null
            }
            if (P.nuit) {
                if (etoiles) {
                    var k = 0
                    while (k < ETOILES.size) {
                        rond(ETOILES[k], ETOILES[k + 1], ETOILES[k + 2], COULEUR_ETOILE, ETOILES[k + 3])
                        k += 4
                    }
                }
                halo(P.astreR * 3.2f, 0.55f)
                rond(P.astreX, P.astreY, P.astreR, P.astre)
                rond(P.astreX + 1.9f, P.astreY - 1.1f, P.astreR * 0.92f, mix(P.cielHaut, P.cielBas, 0.25f))
            } else {
                halo(P.astreR * 3.4f, 1f)
                rond(P.astreX, P.astreY, P.astreR, P.astre, 0.95f)
            }
            // La brume du matin.
            if (P.rang == 0) rect(0f, 26f, 100f, 16f, 0xFFFFFFFF.toInt(), 0.16f)
        }
        fun crete(y0: Float, amp: Float, graine: Int, col: Int, x0: Float = 0f, x1: Float = 100f,
                  f1: Float = 0.07f, f2: Float = 0.19f) {
            val r = Alea(graine)
            val p1 = r() * 6f
            val p2 = r() * 6f
            val t = Path()
            t.moveTo(x0, BAS)
            var x = x0
            while (x <= x1) {
                t.lineTo(x, y0 + sin(x * f1 + p1) * amp + sin(x * f2 + p2) * amp * 0.45f)
                x += 2f
            }
            t.lineTo(x1, BAS)
            t.close()
            forme(t, col)
        }

        fun bosquet(x0: Float, x1: Float, y: Float, taille: Float, graine: Int, col: Int) {
            val r = Alea(graine)
            var x = x0
            while (x <= x1) {
                val rr = taille * (0.65f + 0.5f * r())
                val cx = x + r() * taille * 0.4f
                val cy = y - rr * 0.35f + r() * 1.2f
                rond(cx, cy, rr, col)
                x += taille * 1.05f
            }
        }

        fun sapins(col: Int, vararg liste: Float) {
            var i = 0
            while (i < liste.size) {
                val x = liste[i]; val y = liste[i + 1]; val h = liste[i + 2]
                triangle(x, y - h, x + h * 0.32f, y, x - h * 0.32f, y, col)
                i += 3
            }
        }

        fun fenetre(x: Float, y: Float, w: Float, h: Float, rnd: Alea?) = mineral {
            val allumee = P.allume && (rnd == null || rnd() < 0.7f)
            if (allumee) rect(x - w * 0.6f, y - h * 0.4f, w * 2.2f, h * 1.8f, P.fenetre, 0.22f)
            rect(x, y, w, h, if (allumee) P.fenetre else mix(P.pierre, P.pres, 0.55f))
        }

        fun maison(x: Float, y: Float, w: Float, h: Float, rnd: Alea?, toit: Float? = null) = mineral {
            val ombre = mix(P.pierre, P.pres, 0.28f)
            rect(x, y - h, w, h, P.pierre)
            rect(x + w * 0.62f, y - h, w * 0.38f, h, ombre, 0.55f)
            val th = toit ?: (w * 0.55f)
            triangle(x - 0.4f, y - h, x + w / 2f, y - h - th, x + w + 0.4f, y - h, P.toit)
            if (h > 2.4f) fenetre(x + w * 0.3f, y - h * 0.62f, w * 0.2f, h * 0.26f, rnd)
        }

        fun eau(y: Float, graine: Int, vararg lumieres: Float) {
            val r = Alea(graine)
            rect(0f, y, 100f, BAS - y, P.eau)
            val clair = mix(P.eau, 0xFFFFFFFF.toInt(), if (P.nuit) 0.18f else 0.45f)
            for (k in 0 until 14) {
                val yy = y + 1f + r() * (BAS - y - 1.5f)
                val xx = r() * 92f
                val w = 3f + r() * 9f
                rect(xx, yy, w, 0.35f, clair, 0.7f)
            }
            if (P.allume) for (x in lumieres) rect(x - 0.3f, y + 0.6f, 0.6f, (BAS - y) * 0.7f, P.fenetre, 0.45f)
        }

        /**
         * Les coulisses : deux châssis de feuillage, un de chaque côté, qui
         * encadrent la scène sans entrer dans son milieu, où se tient le
         * dessin du mot. Une silhouette sombre en aplat, comme un décor de
         * théâtre de papier vu à contre-jour.
         */
        fun coulisses() {
            val feuillage = mix(P.pres, 0xFF000000.toInt(), if (P.nuit) 0.25f else 0.38f)
            val tronc = mix(P.sol, 0xFF000000.toInt(), 0.3f)
            for (droite in booleanArrayOf(false, true)) {
                fun x(v: Float) = if (droite) LARGEUR - v else v
                val t = Path()
                t.moveTo(x(0.6f), BAS); t.lineTo(x(1.8f), 34f); t.lineTo(x(3.4f), 26f)
                t.lineTo(x(4.6f), 35f); t.lineTo(x(5.6f), BAS); t.close()
                forme(t, tronc)
                trait("M${x(3.8f)} 39Q${x(8.5f)} 35 ${x(12.5f)} 28.5", tronc, 1f)
                val r = Alea(if (droite) 62 else 61)
                feuillage(FEUILLES, ::x, r, feuillage)
            }
        }

        /**
         * Un feuillage de ronds ([ronds] : x, y, rayon), découpé d'un seul
         * carton : tous les ronds réunis, peints d'un coup, le bord de carton
         * à l'extérieur seulement.
         */
        private fun feuillage(ronds: FloatArray, x: (Float) -> Float, r: Alea, col: Int) {
            val f = Path()
            for (i in ronds.indices step 3) {
                val rr = ronds[i + 2] * (0.9f + 0.2f * r())
                f.addCircle(x(ronds[i]), ronds[i + 1], rr, Path.Direction.CW)
            }
            f.computeBounds(boite, true)
            peint(f, col, -0.6f, boite, 0.8f, union = true)
        }

        /**
         * La seconde rangée de coulisses des rares, un pas en retrait de la
         * première : un arbre de chaque côté, plus petit, plus clair, qui
         * dépasse de derrière le feuillage de devant. Deux châssis étagés au
         * lieu d'un seul : c'est la boîte d'un théâtre, pas un cadre.
         */
        fun coulissesDuFond() {
            val feuillage = mix(P.pres, 0xFF000000.toInt(), if (P.nuit) 0.12f else 0.22f)
            val tronc = mix(P.sol, 0xFF000000.toInt(), 0.15f)
            for (droite in booleanArrayOf(false, true)) {
                fun x(v: Float) = if (droite) LARGEUR - v else v
                val t = Path()
                t.moveTo(x(13.4f), BAS); t.lineTo(x(14.3f), 40f); t.lineTo(x(15.4f), 33f)
                t.lineTo(x(16.5f), 40f); t.lineTo(x(17.4f), BAS); t.close()
                forme(t, tronc)
                val r = Alea(if (droite) 64 else 63)
                feuillage(FEUILLES_DU_FOND, ::x, r, feuillage)
            }
        }

        /**
         * Un nuage de carton peint, de [l] unités de long, sa base droite à
         * [y] + 4 × [l] / 20 : trois bosses rondes, comme découpé aux ciseaux
         * (`nuagePeint` dans la maquette).
         */
        fun nuage(x: Float, y: Float, l: Float) {
            val s = l / 20f
            val col = mix(P.cielHaut, 0xFFFFFFFF.toInt(), if (P.nuit) 0.3f else 0.85f)
            val t = Path()
            t.moveTo(x, y + 4f * s)
            t.lineTo(x + 20f * s, y + 4f * s)
            arc(t, x + 17f * s, y + 3f * s, 3f * s, 0.3f, -PI.toFloat() * 0.7f)
            arc(t, x + 11.5f * s, y + 1.2f * s, 4.4f * s, -0.2f, -PI.toFloat() * 0.95f)
            arc(t, x + 5f * s, y + 2.6f * s, 3.2f * s, -0.4f, -PI.toFloat() * 1.05f)
            t.close()
            boite.set(x, y - 4f * s, x + 20f * s, y + 4f * s)
            peint(t, col, 0f, boite, 1.2f)
        }

        /**
         * Une pièce de carton peint écrite comme en SVG : [dir] est le sens
         * des coups de pinceau, [ombre] la force de son ombre (`peint` de la
         * maquette, appelé directement).
         */
        fun carton(d: String, col: Int, dir: Float, ombre: Float = 1f) {
            if (!actif) return
            val t = chemin(d)
            t.computeBounds(boite, true)
            peint(t, col, dir, boite, ombre)
        }

        /** Un rond de carton peint (`rondPeint` de la maquette). */
        fun rondPeint(cx: Float, cy: Float, r: Float, col: Int, ombre: Float = 0.8f) {
            if (!actif) return
            val t = Path()
            t.addCircle(cx, cy, r, Path.Direction.CW)
            boite.set(cx - r, cy - r, cx + r, cy + r)
            peint(t, col, -0.6f, boite, ombre)
        }

        /**
         * Un feuillage de ronds de carton peint, deux rangées, la claire
         * devant la sombre (`feuillagePeint` de la maquette) : là où le
         * [bosquet] posait des ronds plats.
         */
        fun feuillagePeint(x0: Float, x1: Float, y: Float, taille: Float, graine: Int, col: Int) {
            val r = Alea(graine)
            val ronds = ArrayList<Float>()
            var x = x0
            while (x <= x1) {
                val rr = taille * (0.65f + 0.5f * r())
                val cx = x + r() * taille * 0.4f
                val cy = y - rr * 0.35f + r() * 1.2f
                ronds.add(cx); ronds.add(cy); ronds.add(rr)
                x += taille * 1.05f
            }
            val sombre = mix(col, 0xFF000000.toInt(), 0.12f)
            val claire = mix(col, 0xFFFFFFFF.toInt(), 0.1f)
            for (i in ronds.indices step 3) rondPeint(ronds[i], ronds[i + 1] - ronds[i + 2] * 0.35f, ronds[i + 2], sombre)
            for (i in ronds.indices step 3) {
                val rr = ronds[i + 2]
                rondPeint(ronds[i] + rr * 0.25f, ronds[i + 1] + rr * 0.25f, rr * 0.82f, claire)
            }
        }

        /** Un arc de cercle tourné dans le sens inverse des aiguilles, de [de] à [a] radians, comme `arc` du canevas HTML. */
        private fun arc(t: Path, cx: Float, cy: Float, r: Float, de: Float, a: Float) {
            var balayage = a - de
            while (balayage > 0f) balayage -= 2f * PI.toFloat()
            t.arcTo(RectF(cx - r, cy - r, cx + r, cy + r),
                Math.toDegrees(de.toDouble()).toFloat(), Math.toDegrees(balayage.toDouble()).toFloat())
        }

        // ------------------------------------------------------ outils des îles

        /**
         * Une couleur de plein jour (sable, calcaire, coque peinte), assombrie
         * avec l'heure vers le premier plan : la palette de [Heure] n'a ni
         * sable ni lagon, et une couleur fixe resterait éclairée en pleine nuit.
         */
        fun duJour(col: Int): Int = mix(col, P.pres, floatArrayOf(0.04f, 0.14f, 0.42f, 0.72f)[P.rang])

        fun sable() = duJour(0xFFF0D9A6.toInt())
        fun calcaire() = duJour(0xFFECE4D0.toInt())
        fun blanc() = if (P.nuit) 0xFF9FB3D8.toInt() else 0xFFFFFFFF.toInt()

        fun ovale(cx: Float, cy: Float, rx: Float, ry: Float, col: Int, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, p)
            relever(col, o) { r, rp -> r.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, rp) }
        }

        /** Une volute de vapeur qui monte de ([x], [y]) sur [h] unités. */
        fun vapeur(x: Float, y: Float, h: Float) {
            val col = if (P.nuit) 0xFFB8C4DC.toInt() else 0xFFFFFFFF.toInt()
            trait("M$x ${y}Q${x - 2f} ${y - h * 0.3f} $x ${y - h * 0.5f}Q${x + 2.2f} ${y - h * 0.72f} ${x + 0.4f} ${y - h}",
                col, 1.6f, 0.38f)
            trait("M${x + 1f} ${y - 1f}Q${x + 2.6f} ${y - h * 0.35f} ${x + 1.2f} ${y - h * 0.6f}", col, 1f, 0.28f)
        }

        /**
         * Un balisier : de grandes feuilles en lame dressées, et la hampe rouge
         * en zigzag. Il remplace la fougère arborescente, dont la couronne se
         * lisait comme un petit cocotier à cette taille.
         */
        fun balisier(x: Float, y: Float, h: Float, col: Int) {
            for ((dx, hh) in arrayOf(-0.55f to 0.85f, 0.5f to 1f, -0.15f to 0.7f, 0.25f to 0.9f)) {
                val tx = x + dx * h
                val ty = y - hh * h
                forme("M$x ${y}Q${(x + tx) / 2f - h * 0.14f} ${(y + ty) / 2f} $tx ${ty}Q${(x + tx) / 2f + h * 0.14f} ${(y + ty) / 2f} $x ${y}Z", col)
            }
            val rouge = duJour(0xFFD63A2A.toInt())
            val sx = x + h * 0.08f
            trait("M$sx ${y}L$sx ${y - h * 0.62f}", mix(col, P.sol, 0.3f), 0.35f)
            for (k in 0 until 4) {
                val yy = y - h * (0.3f + k * 0.08f)
                if (k % 2 == 0) triangle(sx, yy, sx + h * 0.16f, yy - h * 0.05f, sx, yy - h * 0.06f, rouge)
                else triangle(sx, yy, sx - h * 0.16f, yy - h * 0.05f, sx, yy - h * 0.06f, rouge)
            }
        }

        /** Un flamboyant : tronc court, branches étalées, couronne plate et rouge. */
        fun flamboyant(x: Float, y: Float, s: Float) {
            val bois = mix(P.sol, P.pierre, 0.2f)
            trait("M$x ${y}Q${x + s * 0.1f} ${y - s * 0.6f} ${x - s * 0.1f} ${y - s}", bois, s * 0.1f)
            trait("M$x ${y - s * 0.55f}Q${x - s * 0.5f} ${y - s * 0.8f} ${x - s * 1.1f} ${y - s * 0.85f}" +
                "M$x ${y - s * 0.65f}Q${x + s * 0.5f} ${y - s * 0.9f} ${x + s * 1.1f} ${y - s * 0.95f}", bois, s * 0.06f)
            val rouge = duJour(0xFFE2502C.toInt())
            val orange = duJour(0xFFF2873D.toInt())
            ovale(x, y - s * 1.02f, s * 0.95f, s * 0.36f, rouge)
            ovale(x - s * 0.72f, y - s * 0.78f, s * 0.48f, s * 0.22f, rouge)
            ovale(x + s * 0.74f, y - s * 0.86f, s * 0.48f, s * 0.22f, rouge)
            ovale(x - s * 0.3f, y - s * 1.12f, s * 0.32f, s * 0.13f, orange)
            ovale(x + s * 0.4f, y - s * 1.06f, s * 0.28f, s * 0.11f, orange)
        }

        /** Un touloulou, le petit crabe rouge des plages, vu de face. */
        fun touloulou(x: Float, y: Float) {
            val rouge = duJour(0xFFD8402E.toInt())
            ovale(x, y, 1.1f, 0.65f, rouge)
            trait("M${x - 1f} ${y}L${x - 1.9f} ${y + 0.7f}M${x + 1f} ${y}L${x + 1.9f} ${y + 0.7f}" +
                "M${x - 0.8f} ${y + 0.3f}L${x - 1.5f} ${y + 1.1f}M${x + 0.8f} ${y + 0.3f}L${x + 1.5f} ${y + 1.1f}", rouge, 0.22f)
            rond(x - 1.4f, y - 0.7f, 0.45f, rouge)
            rond(x + 1.4f, y - 0.7f, 0.45f, rouge)
        }

        /**
         * Une tortue verte qui nage sous la surface, vue d'en haut, la tête
         * vers le haut tournée de [cap] degrés ; [brasse] fait battre ses
         * nageoires avant, de tant de degrés vers l'arrière.
         */
        fun tortue(x: Float, y: Float, l: Float, cap: Float = 0f, brasse: Float = 0f) {
            val dos = mix(duJour(0xFF5E6B3A.toInt()), P.eau, 0.35f)
            val nageoire = mix(dos, P.eau, 0.3f)
            c.save()
            c.rotate(cap, x, y)
            for (cote in intArrayOf(-1, 1)) {
                c.save()
                c.rotate(-cote * brasse, x + cote * l * 0.25f, y - l * 0.3f)
                ovale(x + cote * l * 0.5f, y - l * 0.32f, l * 0.32f, l * 0.11f, nageoire)
                c.restore()
            }
            ovale(x - l * 0.3f, y + l * 0.36f, l * 0.18f, l * 0.08f, nageoire)
            ovale(x + l * 0.3f, y + l * 0.36f, l * 0.18f, l * 0.08f, nageoire)
            ovale(x, y - l * 0.55f, l * 0.13f, l * 0.15f, nageoire)
            ovale(x, y, l * 0.38f, l * 0.48f, dos)
            trait("M$x ${y - l * 0.4f}L$x ${y + l * 0.4f}M${x - l * 0.3f} ${y}L${x + l * 0.3f} $y", mix(dos, P.pres, 0.35f), l * 0.04f)
            c.restore()
        }

        /** Le pieu où se pose le pélican de l'îlet Macou. */
        fun pieu(x: Float, y: Float) = mineral { rect(x - 0.4f, y, 0.8f, 6f, mix(P.sol, P.pierre, 0.2f)) }

        /** Un pélican brun posé sur un pieu, le bec rentré. */
        fun pelican(x: Float, y: Float) {
            val plume = duJour(0xFF7A6A58.toInt())
            ovale(x, y - 1.6f, 1.4f, 1.8f, plume)
            trait("M${x + 0.4f} ${y - 3f}Q${x + 0.8f} ${y - 4.8f} ${x + 0.2f} ${y - 5.2f}", plume, 0.7f)
            rond(x + 0.2f, y - 5.2f, 0.6f, duJour(0xFFE9E2D0.toInt()))
            trait("M${x + 0.6f} ${y - 5.1f}L${x + 2.6f} ${y - 3.4f}", duJour(0xFFC9A24A.toInt()), 0.4f)
        }

        /**
         * Le pélican du pieu en vol, de profil, ses pattes en ([x], [y]) :
         * le corps lourd, la tête rentrée sur les épaules et le long bec
         * devant, les grandes ailes levées ([aile] 1) ou baissées (négatif),
         * tourné vers la droite ([sens] -1 : vers la gauche).
         */
        fun pelicanEnVol(x: Float, y: Float, aile: Float, sens: Float, taille: Float) {
            voile = 1f
            c.save()
            c.scale(sens * taille, taille, x, y)
            // Plus clair qu'à l'arrêt : le dessus des ailes est gris argent.
            val plume = duJour(0xFFA89C8A.toInt())
            val cy = y - 3f
            forme("M${x - 1f} ${cy}L${x + 1f} ${cy}L${x - 1.6f} ${cy - 4f * aile}Z", duJour(0xFF6E6052.toInt()))
            forme("M${x - 2.4f} ${cy - 0.2f}L${x - 3.6f} ${cy - 0.1f}L${x - 2.4f} ${cy + 0.4f}Z", plume)
            ovale(x, cy, 2.6f, 0.8f, plume)
            rond(x + 2.2f, cy - 0.5f, 0.6f, duJour(0xFFE9E2D0.toInt()))
            forme("M${x + 2.6f} ${cy - 0.7f}L${x + 5.6f} ${cy}L${x + 2.6f} ${cy - 0.1f}Z", duJour(0xFFC9A24A.toInt()))
            forme("M${x - 1.4f} ${cy - 0.1f}L${x + 1.2f} ${cy - 0.1f}L${x - 0.8f} ${cy - 5.4f * aile}Z", plume)
            c.restore()
        }

        /** Un damier de [n] cases de côté [s], depuis le coin ([x], [y]), dans [w] × [h]. */
        fun damier(x: Float, y: Float, w: Float, h: Float, s: Float, noir: Int) = mineral {
            var yy = y
            var rangee = 0
            while (yy < y + h - 0.01f) {
                val hh = min(s, y + h - yy)
                var xx = x
                var k = rangee
                while (xx < x + w - 0.01f) {
                    if (k % 2 == 0) rect(xx, yy, min(s, x + w - xx), hh, noir)
                    xx += s
                    k++
                }
                yy += s
                rangee++
            }
        }

        /**
         * Un caveau de Morne-à-l'Eau : une petite maison blanche à pignon,
         * carrelée de damier noir et blanc, sa croix au faîte. [motif] choisit
         * la façade : damier entier, soubassement en damier, ou mur blanc à
         * porte noire, comme les tombeaux mêlés de la photo de référence.
         */
        fun caveau(x: Float, y: Float, w: Float, h: Float, noir: Int, blanc: Int, motif: Int, bougie: Boolean = false) = mineral {
            val ombre = mix(blanc, noir, 0.22f)
            rect(x, y - h, w, h, blanc)
            val s = w / 5f
            when (motif) {
                0 -> damier(x, y - h, w, h, s, noir)
                1 -> damier(x, y - h * 0.45f, w, h * 0.45f, s, noir)
                else -> rect(x + w * 0.3f, y - h * 0.62f, w * 0.4f, h * 0.62f, noir)
            }
            // Le pan à l'ombre donne le volume.
            rect(x + w * 0.82f, y - h, w * 0.18f, h, ombre, 0.6f)
            val th = w * 0.42f
            triangle(x - 0.15f, y - h, x + w / 2f, y - h - th, x + w + 0.15f, y - h, if (motif == 2) noir else blanc)
            ligne(x - 0.15f, y - h, x + w / 2f, y - h - th, noir, w * 0.06f)
            ligne(x + w / 2f, y - h - th, x + w + 0.15f, y - h, noir, w * 0.06f)
            rect(x + w / 2f - w * 0.04f, y - h - th - w * 0.36f, w * 0.08f, w * 0.38f, blanc)
            rect(x + w / 2f - w * 0.16f, y - h - th - w * 0.26f, w * 0.32f, w * 0.08f, blanc)
            if (bougie && P.allume) {
                val bx = x + w * 0.5f
                rond(bx, y - h * 0.2f, w * 0.5f, P.fenetre, 0.25f)
                rect(bx - w * 0.04f, y - h * 0.2f, w * 0.08f, h * 0.14f, P.fenetre)
            }
        }

        // ------------------------------------------------------ les lieux

        // ------------------------------------------- Grande Anse en papier

        /** Un cumulus de papier : des bosses rondes sur une base plate, la face à l'ombre dessous. */
        fun cumulus(x: Float, y: Float, l: Float, graine: Int) {
            val r = Alea(graine)
            val t = Path()
            t.addRoundRect(RectF(x, y - l * 0.12f, x + l, y), l * 0.06f, l * 0.06f, Path.Direction.CW)
            var cx = x + l * 0.12f
            while (cx < x + l * 0.9f) {
                val rr = l * (0.1f + 0.14f * sin(PI.toFloat() * (cx - x) / l) + r() * 0.05f)
                t.addCircle(cx, y - rr * 0.8f, rr, Path.Direction.CW)
                cx += rr * (0.9f + r() * 0.3f)
            }
            val dessous = Path()
            dessous.addRoundRect(RectF(x + l * 0.05f, y - l * 0.08f, x + l * 0.95f, y), l * 0.04f, l * 0.04f, Path.Direction.CW)
            piece(dessous, mix(blanc(), P.cielHaut, 0.25f), 0.3f)
            piece(t, blanc(), 0.4f)
        }

        /**
         * Une feuille de raisinier bord de mer : ronde, un peu en rein, la
         * nervure rouge qui part du pétiole et deux nervures de côté.
         */
        fun feuilleRaisinier(x: Float, y: Float, r: Float, angle: Float, col: Int) {
            val f = Path()
            f.moveTo(0f, 0f)
            f.cubicTo(-r * 1.3f, -r * 0.1f, -r * 1.1f, -r * 2f, 0f, -r * 2f)
            f.cubicTo(r * 1.1f, -r * 2f, r * 1.3f, -r * 0.1f, 0f, 0f)
            f.close()
            val n = Path()
            n.moveTo(-0.12f, 0f); n.lineTo(0f, -r * 1.6f); n.lineTo(0.12f, 0f); n.close()
            for (s in floatArrayOf(-1f, 1f)) {
                n.moveTo(0f, -r * 0.55f); n.lineTo(s * r * 0.7f, -r * 1.15f); n.lineTo(s * r * 0.7f, -r * 1.05f); n.lineTo(0f, -r * 0.4f); n.close()
            }
            pose.setRotate(angle)
            pose.postTranslate(x, y)
            f.transform(pose)
            n.transform(pose)
            piece(f, col, 0.8f)
            piece(n, papier(0xFFB8503A.toInt()), 0f)
        }

        /** Une branche de raisinier : la tige brun-rouge et ses feuilles rondes étagées. */
        fun brancheRaisinier(x: Float, y: Float, h: Float, penche: Float, graine: Int, feuilles: Int) {
            val r = Alea(graine)
            val tige = Path()
            tige.moveTo(x - 0.35f, y); tige.quadTo(x + penche * 0.4f, y - h * 0.5f, x + penche, y - h)
            tige.lineTo(x + penche + 0.3f, y - h); tige.quadTo(x + penche * 0.4f + 0.4f, y - h * 0.5f, x + 0.35f, y); tige.close()
            piece(tige, papier(0xFF8A4A35.toInt()), 0.5f)
            val cols = intArrayOf(papier(0xFFB5D36A.toInt()), papier(0xFF9CC75A.toInt()), papier(0xFF7FB04A.toInt()),
                papier(0xFF8CC060.toInt()), papier(0xFF6FA84A.toInt()), papier(0xFFA8CC62.toInt()), papier(0xFF7FB04A.toInt()),
                papier(0xFFE8C04A.toInt()))
            for (k in 0 until feuilles) {
                val t = 0.25f + 0.75f * k / max(1, feuilles - 1)
                val cote = if (k % 2 == 0) -1f else 1f
                val bx = x + penche * t * t + cote * (0.6f + r() * 1.2f)
                val by = y - h * t
                val c = if (r() < 0.06f) papier(0xFFE08A3A.toInt()) else cols[min((r() * cols.size).toInt(), cols.size - 1)]
                feuilleRaisinier(bx, by, 3f + r() * 1.6f, cote * (30f + r() * 45f), c)
            }
        }

        /**
         * Grande Anse en papier découpé, d'après la photo du propriétaire
         * (2026-11-07) : le grand morne boisé qui plonge dans la mer et son
         * rocher, les cumulus, la mer en lanières du large au bord, la plage
         * en diagonale et son écume, les raisiniers bord de mer devant.
         */
        fun grandeAnsePapier() {
            cumulus(64f, 25f, 30f, 960)
            cumulus(4f, 14f, 22f, 961)
            cumulus(84f, 12f, 18f, 962)
            // Le morne, arrondi, couvert de forêt jusqu'à la mer, et son rocher.
            val crete = { x: Float ->
                val u = ((x - 12f) / 54f).coerceIn(0f, 1f)
                34f - 21f * sin(PI.toFloat() * u.pow(0.7f)).pow(1.2f) + sin(x * 0.4f) * 0.3f
            }
            val morne = Path()
            morne.moveTo(10f, BAS + 2f)
            var x = 10f
            while (x <= 66f) { morne.lineTo(x, crete(x)); x += 1.5f }
            morne.lineTo(66f, 34f); morne.lineTo(66f, BAS + 2f); morne.close()
            morne.op(bosses(14f, 64f, crete, 1.2f, 1.6f, Alea(963)), Path.Op.UNION)
            piece(morne, papier(0xFF256B3E.toInt()))
            piece(Path().apply { addOval(RectF(65.5f, 31.8f, 69f, 34.4f), Path.Direction.CW) }, papier(0xFF3F4A44.toInt()), 0.6f)
            plan(1)
            // La mer, du bleu profond au large au turquoise clair près du bord.
            // Elle ne commence qu'à la pointe de la plage : à gauche, le vert
            // du morne descend jusqu'à l'horizon.
            val mer = Path()
            mer.moveTo(61f, 34f); mer.lineTo(LARGEUR + 2f, 33.8f); mer.lineTo(LARGEUR + 2f, BAS + 2f); mer.lineTo(61f, BAS + 2f); mer.close()
            piece(mer, papier(0xFF2F8FD0.toInt()), 0.3f)
            val bleus = arrayOf(
                intArrayOf(papier(0xFF2F8FD0.toInt()), papier(0xFF3FA3DA.toInt())),
                intArrayOf(papier(0xFF3FB0D8.toInt()), papier(0xFF58C4DC.toInt())),
                intArrayOf(papier(0xFF6FD3DC.toInt()), papier(0xFF9BE3E2.toInt()))
            )
            val r = Alea(964)
            var y = 34.6f
            while (y < 64f) {
                val p = (y - 34f) / 30f
                val e = 0.35f + 0.8f * p
                val teintes = bleus[min((p * 3f).toInt(), 2)]
                var xx = 61f + r() * 3f
                while (xx < LARGEUR + 2f) {
                    val l = (4f + r() * 9f) * (0.6f + p)
                    val t = Path()
                    t.addRoundRect(RectF(xx, y, xx + l, y + e), e / 2f, e / 2f, Path.Direction.CW)
                    piece(t, teintes[if (r() < 0.5f) 0 else 1], 0.25f)
                    xx += l + 0.8f + r() * 2.5f
                }
                y += e + 0.5f + 0.5f * p
            }
            // La lisière de forêt qui descend du morne jusqu'au haut de la plage.
            rive(true, 30f, 36f, 60f, 965, papier(0xFF3F8F4A.toInt()), 2.6f)
            rive(true, 34f, 41f, 40f, 966, papier(0xFF5FA84A.toInt()), 3.2f)
            // La plage en diagonale, son sable mouillé et sa frange d'écume.
            // Sur le plan de la mer : elle file du bas de la fenêtre jusqu'au
            // pied du morne, et sur le plan de devant sa pointe lointaine se
            // décollait de la mer quand la carte penche.
            fun bord(y: Float) = rivageGrandeAnse(y)
            // Le dos de la plage part du même point que le bord de l'eau : elle
            // finit en pointe sur l'horizon, comme une plage qui file au loin.
            fun dos(y: Float) = 61f - 49f * ((y - 34f) / 32f).coerceIn(0f, 1f).pow(0.9f)
            val mouille = Path()
            mouille.moveTo(dos(34f), 34f)
            y = 34f
            while (y <= BAS + 2f) { mouille.lineTo(bord(y) + 1.2f + 1.5f * (y - 34f) / 32f, y); y += 1f }
            mouille.lineTo(dos(BAS + 2f), BAS + 2f); mouille.close()
            piece(mouille, papier(0xFFD9B077.toInt()), 0.3f)
            val sable = Path()
            sable.moveTo(dos(34f), 34f)
            y = 34f
            while (y <= BAS + 2f) { sable.lineTo(bord(y) - 0.4f * (y - 34f) / 32f, y); y += 1f }
            sable.lineTo(dos(BAS + 2f), BAS + 2f); sable.close()
            piece(sable, papier(0xFFEBCB92.toInt()), 0.5f)
            y = 35f
            while (y < BAS) {
                val p = (y - 34f) / 32f
                val l = 2f + 5f * p
                val e = 0.25f + 0.5f * p
                val ex = bord(y) + 1.6f + 1.8f * p + (r() - 0.5f) * 1.2f
                val t = Path()
                t.addRoundRect(RectF(ex - l / 2f, y, ex + l / 2f, y + e), e / 2f, e / 2f, Path.Direction.CW)
                piece(t, blanc(), 0.2f)
                y += 0.9f + 1.4f * p
            }
            // Des traces de pas dans le sable.
            for (py in floatArrayOf(62f, 59f, 56.3f, 53.8f, 51.5f, 49.4f)) {
                val px = dos(py) + (bord(py) - dos(py)) * 0.32f + if (py.toInt() % 2 == 0) 0.6f else -0.6f
                piece(Path().apply { addOval(RectF(px - 0.6f, py - 0.35f, px + 0.6f, py + 0.35f), Path.Direction.CW) },
                    papier(0xFFD4AE76.toInt()), 0f)
            }
            plan(2)
            // Les raisiniers bord de mer : un massif à gauche, une branche en bas à droite.
            brancheRaisinier(0f, BAS + 1f, 54f, 5f, 970, 12)
            brancheRaisinier(7f, BAS + 1f, 40f, 7f, 971, 9)
            brancheRaisinier(-3f, BAS + 1f, 30f, 9f, 973, 7)
            brancheRaisinier(13f, BAS + 1f, 20f, 5f, 972, 5)
            brancheRaisinier(82f, BAS + 2f, 24f, 2f, 974, 7)
            brancheRaisinier(90f, BAS + 2f, 15f, 3f, 975, 4)
        }

        /** Grande Anse : la grande plage de Deshaies, les raisiniers, un flamboyant, des touloulous, l'îlet au large. */
        fun grandeAnse() {
            if (PAPIER) { grandeAnsePapier(); return }
            crete(30f, 1.2f, 101, P.loin, x0 = 52f)
            forme("M30 35Q33 31.6 37 31.8Q41 32.2 43 35Z", mix(P.loin, P.milieu, 0.3f))
            plan(1)
            eau(35f, 102)
            forme("M64 66L64 40C70 32 80 26 100 23L100 66Z", P.milieu)
            val cime = mix(P.milieu, P.pres, 0.3f)
            rond(76f, 30.5f, 3f, cime); rond(82f, 27.6f, 3.4f, cime); rond(89f, 25.6f, 3.4f, cime); rond(96f, 24.2f, 3.2f, cime)
            forme("M0 66L0 46Q30 42.5 60 45Q78 46.6 100 45L100 66Z", sable())
            trait("M0 46Q30 42.5 60 45Q78 46.6 100 45", mix(P.eau, 0xFFFFFFFF.toInt(), 0.6f), 0.5f, 0.8f)
            plan(2)
            // Les raisiniers bord de mer, pas de cocotier.
            bosquet(-2f, 20f, 53f, 3.6f, 103, P.pres)
            bosquet(2f, 16f, 50f, 2.2f, 104, mix(P.pres, P.milieu, 0.5f))
            flamboyant(84f, 56f, 14f)
            forme("M0 66L0 58Q20 55 40 58.5Q60 62 80 57.5Q92 55.5 100 56L100 66Z", mix(sable(), P.sol, 0.3f))
        }

        // ------------------------------------------- Les Saintes en papier

        /** Une pièce de papier écrite comme en SVG. */
        private fun pieceD(d: String, col: Int, ombre: Float = 1f) = piece(chemin(d), col, ombre)

        /**
         * Une saintoise tirée au sec, vue de trois quarts et d'un peu haut :
         * l'intérieur orangé et ses bancs entre les deux plats-bords, la coque
         * blanche qui se relève vers l'étrave, le liston vert et son filet
         * rouge. [versDroite] : l'étrave à droite. Longue de [l], posée sur [y].
         */
        fun saintoise(x: Float, y: Float, l: Float, versDroite: Boolean) {
            fun X(u: Float) = if (versDroite) x + u * l else x + l - u * l
            val h = l * 0.14f
            val n = 24
            fun pres(u: Float) = y - h * (1f + 0.6f * u * u * u)
            fun loin(u: Float) = pres(u) - h * 0.55f * (1f - u.pow(2.5f)) * (0.5f + 0.5f * sin(PI.toFloat() * u))
            fun quille(u: Float) = y - h * 0.05f - h * 1.45f * u.pow(4)
            fun bande(haut: (Float) -> Float, bas: (Float) -> Float, u1: Float = 1f): Path {
                val p = Path()
                for (k in 0..n) { val u = u1 * k / n; if (k == 0) p.moveTo(X(u), haut(u)) else p.lineTo(X(u), haut(u)) }
                for (k in n downTo 0) { val u = u1 * k / n; p.lineTo(X(u), bas(u)) }
                p.close(); return p
            }
            piece(bande(::loin, ::pres), papier(0xFFE07A3E.toInt()), 0.3f)
            for (b in floatArrayOf(0.3f, 0.5f, 0.7f)) {
                val p = Path()
                p.moveTo(X(b), loin(b)); p.lineTo(X(b + 0.03f), loin(b + 0.03f))
                p.lineTo(X(b + 0.045f), pres(b + 0.045f)); p.lineTo(X(b + 0.015f), pres(b + 0.015f)); p.close()
                piece(p, papier(0xFFA9512C.toInt()), 0f)
            }
            piece(bande({ u -> loin(u) - h * 0.1f }, ::loin), papier(0xFF2E8E6A.toInt()), 0f)
            val coque = bande(::pres, ::quille)
            coque.op(Path().apply {
                moveTo(X(0.97f), pres(0.97f)); lineTo(X(1.04f), pres(1f) - h * 0.08f); lineTo(X(0.99f), quille(0.99f)); close()
            }, Path.Op.UNION)
            piece(coque, papier(0xFFF4F1EA.toInt()))
            piece(bande(::pres, { u -> min(pres(u) + h * 0.24f, quille(u)) }), papier(0xFF2E8E6A.toInt()), 0.2f)
            piece(bande({ u -> min(pres(u) + h * 0.24f, quille(u)) }, { u -> min(pres(u) + h * 0.33f, quille(u)) }),
                papier(0xFFC0392B.toInt()), 0f)
        }

        /**
         * Un salako, le chapeau des pêcheurs saintois, vu d'un peu haut et posé
         * à plat : un large disque à peine bombé, blanc rayé de bleu et de vert
         * du sommet au bord, et sa petite pointe au milieu (photo du
         * propriétaire, 2026-11-09). Centré sur [x], posé sur [y], de rayon [r].
         */
        fun salako(x: Float, y: Float, r: Float) {
            val k = r * 0.55f
            // Le point du bord avant (la courbe du bas) au paramètre t, de 0 (gauche) à 1 (droite).
            fun bordX(t: Float) = (1 - t) * (1 - t) * (x - r) + 2 * t * (1 - t) * x + t * t * (x + r)
            fun bordY(t: Float) = (1 - t) * (1 - t) * y + 2 * t * (1 - t) * (y + 0.8f * k) + t * t * y
            pieceD("M${x - r * 1.05f} ${y + 0.4f * k}Q$x ${y + 1.5f * k} ${x + r * 1.05f} ${y + 0.4f * k}Q$x ${y + 0.9f * k} ${x - r * 1.05f} ${y + 0.4f * k}Z",
                papier(0xFF6A5A48.toInt()), 0f)
            pieceD("M${x - r} ${y}Q$x ${y - 1.9f * k} ${x + r} ${y}Q$x ${y + 0.8f * k} ${x - r} ${y}Z", papier(0xFFF1EEE4.toInt()), 0.5f)
            val sommetY = y - 0.95f * k
            val raies = intArrayOf(papier(0xFF3A96C4.toInt()), papier(0xFF5FB894.toInt()))
            for (i in 0..8) {
                val t = 0.06f + i * 0.11f
                val d = 0.03f
                pieceD("M$x ${sommetY}L${bordX(t - d)} ${bordY(t - d)}L${bordX(t + d)} ${bordY(t + d)}Z", raies[i % 2], 0f)
            }
            // Le bord, un liseré plus sombre, et la pointe.
            pieceD("M${x - r} ${y}Q$x ${y + 0.8f * k} ${x + r} ${y}Q$x ${y + 0.95f * k} ${x - r} ${y}Z", papier(0xFF9A9488.toInt()), 0f)
            piece(disque(x, sommetY - 0.15f, 0.28f), papier(0xFFE4E0D4.toInt()), 0.3f)
        }

        /**
         * Un amandier pays qui entre par le bord droit : le tronc contre le
         * bord, ses branches en étages plats qui s'avancent au-dessus du bourg.
         */
        fun amandierPays(x: Float, sol: Float) {
            val brun = papier(0xFF6A4A35.toInt())
            val etages = floatArrayOf(4f, 12f, 19.5f)
            val bouts = floatArrayOf(74f, 79f, 87f)
            for ((k, ey) in etages.withIndex()) {
                pieceD("M$x ${ey + 1.6f}Q${(x + bouts[k]) / 2f} ${ey + 0.6f} ${bouts[k] + 4f} ${ey + 0.4f}L${bouts[k] + 4f} ${ey + 1.1f}Q${(x + bouts[k]) / 2f} ${ey + 1.6f} $x ${ey + 2.6f}Z", brun, 0.4f)
            }
            pieceD("M${x - 1.4f} ${sol}Q${x - 0.6f} 30 ${x - 2.2f} -2L${x + 4f} -2L${x + 4f} ${sol}Z", brun, 0.8f)
            val dessous = papier(0xFF2F5E2C.toInt())
            val cols = intArrayOf(papier(0xFF4E8A3A.toInt()), papier(0xFF6FA84A.toInt()), papier(0xFF5A9640.toInt()))
            for ((k, ey) in etages.withIndex()) {
                val g = bouts[k]; val d = x + 3f
                val sous = Path().apply { addRoundRect(RectF(g + 0.6f, ey - 0.4f, d, ey + 1.8f), 1.1f, 1.1f, Path.Direction.CW) }
                piece(sous, dessous, 0.6f)
                val dessus = Path().apply { addRoundRect(RectF(g, ey - 1.6f, d, ey + 0.8f), 1.2f, 1.2f, Path.Direction.CW) }
                dessus.op(bosses(g + 1f, d, { ey - 1.2f }, 0.9f, 1.5f, Alea(980 + k)), Path.Op.UNION)
                piece(dessus, cols[k], 0.8f)
            }
            feuilleCoeur(82f, 13.6f, 1.5f, -25f, papier(0xFFC0533A.toInt()))
        }

        /**
         * Les Saintes en papier découpé, d'après la carte postale du
         * propriétaire (2026-11-07) : le morne boisé de Terre-de-Haut, la
         * maison en paquebot à son pied, le bourg et sa case au toit rouge,
         * la baie, et deux saintoises tirées sur la plage. Le cocotier de la
         * carte postale devient un amandier pays (pas de cocotier).
         */
        fun lesSaintesPapier() {
            cumulus(66f, 15f, 26f, 990)
            cumulus(2f, 11f, 18f, 991)
            // Le morne, de la pointe à gauche jusqu'en haut à droite.
            val crete = { x: Float ->
                val u = ((x - 4f) / 72f).coerceIn(0f, 1f)
                35f - 25f * (u * u * (3f - 2f * u)) + sin(x * 0.35f) * 0.4f
            }
            val morne = Path()
            morne.moveTo(2f, BAS + 2f)
            var x = 2f
            while (x <= LARGEUR + 2f) { morne.lineTo(x, crete(x)); x += 1.5f }
            morne.lineTo(LARGEUR + 2f, BAS + 2f); morne.close()
            morne.op(bosses(4f, 103f, crete, 1.3f, 1.8f, Alea(992)), Path.Op.UNION)
            piece(morne, papier(0xFF5E7A34.toInt()))
            plan(1)
            // La pointe boisée à gauche, puis la baie.
            pieceD("M-2 40L-2 31Q3 27.5 8 30Q12 32 13 37L13 40Z", papier(0xFF4E7A3A.toInt()))
            pieceD("M-2 36.5L102 36.5L102 56L-2 56Z", papier(0xFF2F6E9A.toInt()), 0.3f)
            val r = Alea(993)
            val bleus = intArrayOf(papier(0xFF3F86B0.toInt()), papier(0xFF5A9FC4.toInt()), papier(0xFF2A5F88.toInt()), papier(0xFF6FB3D0.toInt()))
            var y = 37.2f
            while (y < 54f) {
                val p = (y - 37f) / 17f
                val e = 0.35f + 0.6f * p
                var xx = -2f + r() * 3f
                while (xx < LARGEUR + 2f) {
                    val l = (4f + r() * 9f) * (0.6f + p)
                    val t = Path()
                    t.addRoundRect(RectF(xx, y, xx + l, y + e), e / 2f, e / 2f, Path.Direction.CW)
                    piece(t, bleus[min((r() * 4f).toInt(), 3)], 0.2f)
                    xx += l + 0.8f + r() * 2.5f
                }
                y += e + 0.5f + 0.4f * p
            }
            // Le pied du morne au bord de l'eau, et le chemin qui va du paquebot au bourg.
            pieceD("M10 37.5Q30 34.6 50 35.2Q70 35.6 102 34.4L102 37.6Q60 38.4 10 38.2Z", papier(0xFF4E7A3A.toInt()), 0.6f)
            pieceD("M18 36.4Q40 34.6 60 35.1Q72 35.3 84 34.8L84 35.4Q72 35.9 60 35.7Q40 35.2 18 37Z", papier(0xFFC8B48A.toInt()), 0f)
            // La maison en paquebot, l'étrave tournée vers la baie.
            val blancCase = papier(0xFFF2F0EA.toInt())
            pieceD("M12 26.6L12 21L12.25 21L12.25 26.6Z", papier(0xFF5A5A5A.toInt()), 0f)
            pieceD("M16.5 26.6L16.5 22L16.75 22L16.75 26.6Z", papier(0xFF5A5A5A.toInt()), 0f)
            pieceD("M9.6 31L9.6 27.2L19.4 27.2L19.4 31Z", blancCase, 0.6f)
            pieceD("M9.2 27.4L9.2 26.6L19.8 26.6L19.8 27.4Z", papier(0xFF9A4A3A.toInt()), 0.3f)
            pieceD("M6.5 31Q8 35 10 38.4L21.5 38.4L21.5 31Z", blancCase)
            for (k in 0 until 4) pieceD("M${10.6f + k * 2.2f} 28.4L${11.8f + k * 2.2f} 28.4L${11.8f + k * 2.2f} 29.8L${10.6f + k * 2.2f} 29.8Z", papier(0xFF3A4A5A.toInt()), 0f)
            for (k in 0 until 4) piece(disque(12.5f + k * 2.4f, 33.5f, 0.4f), papier(0xFF3A4A5A.toInt()), 0f)
            // Les barques au mouillage sont dans [BETES] : elles se balancent sur la carte ouverte.
            // Le bourg : la grande case blanche au toit de tôle rouge, ses voisines, le kiosque.
            pieceD("M75 37L75 33.5L80 33.5L80 37Z", papier(0xFFC9B79A.toInt()), 0.6f)
            pieceD("M74.3 33.8L77.5 31.4L80.7 33.8Z", papier(0xFF8A5A3A.toInt()), 0.6f)
            pieceD("M84 38L84 27.5L96 27.5L96 38Z", blancCase)
            pieceD("M83.4 27.8L86.5 22.8L96.6 22.8L96.6 27.8Z", papier(0xFFB0463A.toInt()), 0.8f)
            pieceD("M83.4 27.8L86.5 22.8L89.6 27.8Z", papier(0xFF8E3A30.toInt()), 0.4f)
            for (row in 0 until 2) for (k in 0 until 3) {
                val wx = 87.5f + k * 2.8f; val wy = 29.3f + row * 4.2f
                pieceD("M$wx ${wy}L${wx + 1.4f} ${wy}L${wx + 1.4f} ${wy + 2.2f}L$wx ${wy + 2.2f}Z", papier(0xFF3F7A86.toInt()), 0f)
            }
            pieceD("M95.5 38L95.5 30L104 30L104 38Z", papier(0xFFE6E2D8.toInt()))
            pieceD("M95 30.4L96.5 28.2L104 28.2L104 30.4Z", papier(0xFFB0463A.toInt()), 0.6f)
            plan(2)
            // La plage brune, le sable mouillé au bord de l'eau.
            pieceD("M-2 50Q30 47.6 60 48.6Q82 49.2 102 47.4L102 68L-2 68Z", papier(0xFF9C8058.toInt()), 0.4f)
            pieceD("M-2 51.4Q30 49 60 50Q82 50.6 102 48.8L102 68L-2 68Z", papier(0xFFB89A6A.toInt()), 0.6f)
            saintoise(66f, 56.5f, 40f, false)
            saintoise(-6f, 62.5f, 62f, true)
            // Un salako posé sur le banc de la saintoise, à gauche du mot.
            salako(18f, 51.2f, 4.2f)
            galet(30f, 64.5f, 3f, papier(0xFF8A8478.toInt()))
            amandierPays(98f, 50f)
        }

        /** Les Saintes : la baie, le Pain de Sucre, une saintoise sous voile. */
        fun lesSaintes() {
            if (PAPIER) { lesSaintesPapier(); return }
            val r = Alea(111)
            crete(34f, 1.4f, 112, P.loin, x0 = 60f)
            plan(1)
            eau(37f, 113, 22f, 30f, 74f)
            forme("M0 37.5L0 30C6 27.5 14 29 20 31.5C26 33.5 32 35 38 37.5Z", P.milieu)
            forme("M46 37.5Q48.5 27 53.5 24.6Q58.5 25.2 61 37.5Z", P.milieu)
            forme("M53.5 24.6Q58.5 25.2 61 37.5L56 37.5Q56.5 30 53.5 24.6Z", mix(P.milieu, P.pres, 0.35f), 0.7f)
            forme("M72 37.5C78 32 88 29.5 100 30.5L100 37.5Z", P.milieu)
            var x = 3f
            while (x < 34f) {
                maison(x, 37.6f, 2.4f, 1.8f + r() * 1f, r, toit = 1.3f)
                x += 3.3f + r() * 1.2f
            }
            plan(2)
            forme("M14 55Q24 58.6 36 55L34.4 58Q24 60.6 15.6 58Z", duJour(0xFFF4F1EA.toInt()))
            forme("M15.6 58Q24 60.6 34.4 58L34 59Q24 61.6 16 59Z", duJour(0xFF2F6FA6.toInt()))
            ligne(25f, 56f, 25f, 41f, P.sol, 0.45f)
            forme("M25.3 42L33 55L25.3 55Z", duJour(0xFFF5E9D8.toInt()))
            forme("M24.7 44L18.6 55L24.7 55Z", duJour(0xFFE3B65C.toInt()))
        }

        // ------------------------------------------- la Soufrière en papier

        /** Une ligne brisée lue comme une fonction : la hauteur en x, entre les points donnés. */
        private fun brisee(vararg pts: Float): (Float) -> Float = { x ->
            var y = pts[1]
            var k = 0
            while (k + 3 < pts.size) {
                val xa = pts[k]; val ya = pts[k + 1]; val xb = pts[k + 2]; val yb = pts[k + 3]
                if (x >= xa && x <= xb) { y = ya + (yb - ya) * (x - xa) / (xb - xa); break }
                if (x > xb) y = yb
                k += 2
            }
            y
        }

        /** La pièce sous une ligne de crête, de [x0] à [x1], jusqu'en bas de la fenêtre. */
        private fun sousLaCrete(x0: Float, x1: Float, crete: (Float) -> Float): Path {
            val t = Path()
            t.moveTo(x0, BAS + 2f)
            var x = x0
            while (x < x1) { t.lineTo(x, crete(x)); x += 1f }
            t.lineTo(x1, crete(x1))
            t.lineTo(x1, BAS + 2f)
            t.close()
            return t
        }

        /** Des taches de mousse jaune semées sous la ligne [haut], jusqu'à [y1]. */
        private fun taches(x0: Float, x1: Float, y1: Float, n: Int, graine: Int, col: Int, haut: (Float) -> Float) {
            val r = Alea(graine)
            repeat(n) {
                val x = x0 + r() * (x1 - x0)
                val h = haut(x) + 1.4f
                val y = h + r() * (y1 - h)
                val rx = 0.7f + r() * 1.3f
                if (h < y1) {
                    val t = Path()
                    for (k in 0 until 3) {
                        val dx = (r() - 0.5f) * rx * 2.2f; val dy = (r() - 0.5f) * rx * 0.6f; val q = rx * (0.5f + 0.5f * r())
                        t.op(Path().apply { addOval(RectF(x + dx - q, y + dy - q * 0.6f, x + dx + q, y + dy + q * 0.6f), Path.Direction.CW) }, Path.Op.UNION)
                    }
                    piece(t, col, 0f)
                }
            }
        }

        /** Le panache d'une fumerolle : des bouffées qui montent de [bas] à [haut] en grossissant et en dérivant. */
        /**
         * Une vapeur légère, en voile de papier calque : des nappes ovales à
         * peine opaques qui s'élargissent en montant, et s'effacent en haut.
         */
        private fun voileVapeur(x: Float, bas: Float, haut: Float, w: Float, derive: Float, graine: Int) {
            val r = Alea(graine)
            val col = if (P.nuit) mix(P.cielBas, P.cielHaut, 0.35f) else 0xFFFFFFFF.toInt()
            // Deux voiles : le large et pâle, puis le cœur plus étroit qui s'arrête plus bas.
            for ((large, fin, o) in arrayOf(Triple(1f, 1f, 0.2f), Triple(0.55f, 0.6f, 0.2f))) {
                val t = Path()
                var u = 0f
                while (u <= fin) {
                    val cx = x + derive * u * u + (r() - 0.5f) * w * 0.6f
                    val cy = bas + (haut - bas) * u
                    val rx = w * (1.2f + 2.2f * u) * large; val ry = w * (0.7f + 0.6f * u) * large
                    t.op(Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }, Path.Op.UNION)
                    u += 0.08f
                }
                forme(t, col, o)
            }
        }

        private fun panache(x: Float, bas: Float, haut: Float, w: Float, derive: Float, graine: Int) {
            val r = Alea(graine)
            val col = if (P.nuit) mix(P.cielBas, P.cielHaut, 0.35f) else papier(0xFFF3F2EC.toInt())
            for ((couche, teinte) in listOf(1f to col, 0.6f to mix(col, 0xFFFFFFFF.toInt(), 0.5f))) {
                val t = Path()
                var u = 0f
                while (u <= 1f) {
                    val cx = x + derive * u * u + (r() - 0.5f) * w * 0.5f
                    val cy = bas + (haut - bas) * u + (r() - 0.5f) * w * 0.3f
                    t.op(disque(cx, cy, w * (0.6f + 0.9f * u) * couche * (0.8f + 0.35f * r())), Path.Op.UNION)
                    u += 0.06f
                }
                piece(t, teinte, if (couche == 1f) 0.5f else 0f)
            }
        }

        /**
         * La Soufrière en papier découpé, d'après la photo du propriétaire
         * (2026-11-07) : le sommet, son dôme de roche brune et ocre aux
         * aiguilles sombres, la mousse vert-jaune qui l'habille à gauche, le
         * gouffre fumant à droite, la clôture et le sentier, et devant le creux
         * vert où descend la trace, les buttes de mousse et leurs blocs noirs.
         */
        fun soufrierePapier() {
            brumePapier(66f, 104f, 9f, 7f, 1200)
            val crete = brisee(-2f, 28f, 6f, 19f, 12f, 13f, 18f, 12.2f, 23f, 13.5f, 28f, 11f, 32f, 8.5f, 35f, 9.5f, 38f, 6.5f,
                42f, 7.5f, 45f, 5.5f, 49f, 8f, 53f, 7f, 57f, 9.5f, 61f, 8.5f, 66f, 11f, 72f, 10.5f, 80f, 12f, 88f, 11f, 96f, 12.5f, 102f, 13.5f)
            piece(sousLaCrete(-2f, 102f, crete), papier(0xFF7E604A.toInt()))
            pieceD("M28 20Q40 14 56 18Q70 16 86 17Q96 17 102 19L102 30Q80 26 60 30Q44 28 28 31Z", papier(0xFFA27F58.toInt()), 0.4f)
            val rd = Alea(1213)
            val dalles = intArrayOf(papier(0xFF5E4A3E.toInt()), papier(0xFF6E584A.toInt()), papier(0xFF8E8070.toInt()), papier(0xFFB8996A.toInt()))
            repeat(26) {
                val dx = 26f + rd() * 74f
                val dy = crete(dx) + 1f + rd() * (30f - crete(dx))
                val l = 2f + rd() * 4.5f; val e = 1f + rd() * 1.6f
                pieceD("M$dx ${dy}L${dx + l * 0.3f} ${dy - e}L${dx + l * 0.8f} ${dy - e * 0.8f}L${dx + l} ${dy + e * 0.2f}L${dx + l * 0.5f} ${dy + e * 0.4f}Z",
                    dalles[min((rd() * 4f).toInt(), 3)], 0.4f)
            }
            val mousse = brisee(-2f, 30f, 6f, 21f, 12f, 15.6f, 18f, 15.2f, 22f, 17f, 30f, 24f, 40f, 34f)
            val cap = sousLaCrete(-2f, 40f, mousse)
            cap.op(bosses(-2f, 34f, { x -> mousse(x) + 0.6f }, 0.9f, 1.6f, Alea(1201)), Path.Op.UNION)
            piece(cap, papier(0xFF6FA83A.toInt()))
            taches(-2f, 30f, 40f, 9, 1202, papier(0xFFAFC748.toInt()), mousse)
            plan(1)
            // Le flanc de roche ocre à droite, le gouffre et sa fumée.
            val flanc = brisee(48f, 33f, 60f, 28f, 72f, 26f, 84f, 24.5f, 96f, 25f, 102f, 24f)
            piece(sousLaCrete(48f, 102f, flanc), papier(0xFFB39466.toInt()))
            pieceD("M73 27Q84 23.5 97 27.5Q94 34 90 44Q86 47 83 44Q78 34 73 27Z", papier(0xFF6E665C.toInt()))
            pieceD("M73 27Q78 34 83 44Q85 46 86.5 45.5Q82 34 80 25.6Z", papier(0xFF4E4842.toInt()), 0.4f)
            FUMEE_SOUFRIERE.forEach { (x, pied, haut, w, derive) -> panache(x, pied, haut, w, derive, 1203) }
            // La clôture le long du bord du gouffre.
            val brun = papier(0xFF5A4636.toInt())
            pieceD("M66 26.2Q80 22.4 99 25.6L99 26.1Q80 22.9 66 26.7Z", brun, 0f)
            for (k in 0 until 9) {
                val fx = 66f + k * 4.1f
                val fy = 26f - sin(PI.toFloat() * (fx - 66f) / 33f) * 2.6f + (fx - 66f) * 0.02f
                pieceD("M$fx ${fy - 1.3f}L${fx + 0.35f} ${fy - 1.3f}L${fx + 0.35f} ${fy + 0.8f}L$fx ${fy + 0.8f}Z", brun, 0f)
            }
            // Le versant de mousse à gauche, et le sentier qui monte vers le gouffre.
            val versant = brisee(-2f, 30f, 10f, 29f, 22f, 31f, 34f, 36f, 46f, 40f, 58f, 44f, 70f, 46f)
            val pente = sousLaCrete(-2f, 70f, versant)
            pente.op(bosses(-2f, 64f, { x -> versant(x) + 0.5f }, 0.8f, 1.5f, Alea(1204)), Path.Op.UNION)
            piece(pente, papier(0xFF8DBE3E.toInt()))
            taches(-2f, 66f, 50f, 14, 1205, papier(0xFFBCD04C.toInt()), versant)
            taches(-2f, 66f, 50f, 6, 1206, papier(0xFF5E9A34.toInt()), versant)
            pieceD("M14 33.2Q30 35 42 34.4Q56 33.6 66 30.4Q70 29 74 28L74 28.8Q70 29.8 66 31.2Q56 34.6 42 35.4Q30 36 14 34Z",
                papier(0xFFD2BE92.toInt()), 0f)
            val rr = Alea(1207)
            repeat(14) {
                val gx = 50f + rr() * 24f; val gy = 31f + rr() * 9f
                if (gy < flanc(gx) + 0.8f || gy > versant(gx) - 0.8f) galet(gx, gy, 0.6f + rr() * 0.8f, papier(0xFF7A6656.toInt()))
            }
            plan(2)
            // Le creux vert et la trace qui descend vers soi.
            val creux = brisee(-2f, 46f, 20f, 45f, 40f, 47f, 52f, 46f, 66f, 44f, 84f, 42f, 102f, 41f)
            piece(sousLaCrete(-2f, 102f, creux), papier(0xFF4F9A3C.toInt()))
            taches(-2f, 102f, 60f, 8, 1208, papier(0xFF6DB646.toInt()), creux)
            pieceD("M51.5 46.3Q53 46.2 53.5 47Q52 55 55 66.5L46 66.5Q50 56 51.5 46.3Z", papier(0xFFB9A57A.toInt()), 0.3f)
            pieceD("M52.2 47Q51 56 48.6 66.5L52.6 66.5Q51.6 56 52.8 47Z", papier(0xFF8E9A54.toInt()), 0f)
            // La butte de gauche, sa fente sombre, sa mousse jaune.
            val gauche = brisee(-2f, 40f, 8f, 40.5f, 18f, 43f, 26f, 49f, 32f, 56f, 36f, 64f, 38f, 68f)
            val bg = sousLaCrete(-2f, 38f, gauche)
            bg.op(bosses(-2f, 34f, { x -> gauche(x) + 0.5f }, 1f, 1.8f, Alea(1209)), Path.Op.UNION)
            piece(bg, papier(0xFF7DB43A.toInt()))
            pieceD("M18 50Q21 56 19 66L14 66Q17 57 15 51Z", papier(0xFF2F4E26.toInt()), 0.3f)
            taches(-2f, 34f, 66f, 10, 1210, papier(0xFFB5CC48.toInt()), gauche)
            // La butte de droite, ses blocs de roche noire.
            val droite = brisee(58f, 68f, 62f, 58f, 68f, 53f, 76f, 49.5f, 86f, 45f, 94f, 42.5f, 102f, 41.5f)
            val bd = sousLaCrete(58f, 102f, droite)
            bd.op(bosses(64f, 102f, { x -> droite(x) + 0.5f }, 1f, 1.8f, Alea(1211)), Path.Op.UNION)
            piece(bd, papier(0xFF86BC3E.toInt()))
            taches(62f, 102f, 66f, 9, 1212, papier(0xFFB9CF4C.toInt()), droite)
            galet(93f, 44.5f, 4.2f, papier(0xFF3E3632.toInt()))
            galet(99f, 50f, 3.4f, papier(0xFF4A403A.toInt()))
            galet(85f, 47.6f, 2.4f, papier(0xFF4A403A.toInt()))
            // Au ras du sol : les rosettes de broméliacées, quelques fleurs roses, et les balisiers du colibri.
            for ((bx, by) in arrayOf(70f to 64f, 78f to 61f, 28f to 64.5f)) {
                for (k in -3..3) brin(bx + k * 0.4f, by, 3.2f + (3 - abs(k)) * 0.9f, k * 1.1f, papier(if (k % 2 == 0) 0xFF5FA845.toInt() else 0xFF8CC85A.toInt()))
            }
            for ((fx, fy) in arrayOf(82f to 58.4f, 84.5f to 57.2f, 88f to 56.2f, 74f to 60.6f)) {
                piece(disque(fx, fy, 0.75f), papier(0xFFC2307A.toInt()), 0.3f)
            }
            balisier(12f, 64f, 14f, feuille())
            balisier(88f, 64f, 14f, feuille())
        }

        /**
         * La Soufrière : le dôme raide qui remplit la fenêtre, son chapeau de
         * nuages, les fumerolles, la savane d'altitude sur ses flancs et la
         * forêt dense tout autour. Le premier dessin la posait sur une bande
         * verte qui se lisait comme une plaine (propriétaire, 2026-10-19).
         */
        fun soufriere() {
            if (PAPIER) { soufrierePapier(); return }
            forme("M0 34Q12 26 24 30L24 66L0 66Z", P.loin)
            forme("M100 32Q88 25 76 30L76 66L100 66Z", P.loin)
            plan(1)
            forme("M16 54Q20 30 33 20Q42 12 52 12Q63 13 71 22Q81 32 86 54Z", mix(P.pierre, P.milieu, 0.45f))
            forme("M52 12Q63 13 71 22Q81 32 86 54L66 54Q68 30 52 12Z", mix(P.pierre, P.sol, 0.45f), 0.4f)
            trait("M40 18Q42 28 38 38M50 13Q52 24 49 36M60 16Q58 26 63 40M33 26Q32 32 29 36", mix(P.pierre, P.sol, 0.5f), 0.5f)
            ovale(46f, 15.6f, 3.2f, 1f, duJour(0xFFD8C467.toInt()), 0.9f)
            vapeur(46f, 13f, 10f)
            vapeur(58f, 14.6f, 8f)
            brume(46f, 11.5f, 20f, 3.4f, 0.75f)
            brume(62f, 9.6f, 12f, 2.6f, 0.6f)
            for ((nx, ny, nr) in arrayOf(Triple(38f, 10.6f, 3.2f), Triple(45f, 9.4f, 3.8f), Triple(52f, 10f, 3.4f), Triple(59f, 9f, 2.8f)))
                rond(nx, ny, nr, blanc(), 0.22f)
            bosquet(16f, 86f, 53f, 2.2f, 122, P.milieu)
            plan(2)
            forme("M0 44Q14 38 26 48Q34 55 40 66L0 66Z", P.milieu)
            forme("M100 42Q86 38 76 48Q66 55 60 66L100 66Z", P.milieu)
            bosquet(-2f, 30f, 45f, 3.4f, 123, P.milieu)
            bosquet(70f, 102f, 44f, 3.4f, 124, P.milieu)
            forme("M0 66L0 56Q50 50 100 56L100 66Z", P.pres)
            bosquet(-2f, 102f, 56f, 4.2f, 125, P.pres)
            bosquet(-2f, 102f, 62f, 3.6f, 126, mix(P.pres, P.sol, 0.3f))
            balisier(10f, 65f, 12f, feuille())
            balisier(90f, 65f, 12f, feuille())
        }

        // ------------------------------------------- les Chutes du Carbet en papier

        private fun roche() = papier(0xFF5E6660.toInt())

        /** Une chute : le rideau d'eau clair, des filets blancs de longueurs inégales, l'écume au pied. */
        fun cascade(x: Float, haut: Float, bas: Float, w: Float, graine: Int) {
            val r = Alea(graine)
            val rideau = Path()
            rideau.moveTo(x - w * 0.35f, haut); rideau.lineTo(x + w * 0.35f, haut)
            rideau.lineTo(x + w * 0.55f, bas); rideau.lineTo(x - w * 0.55f, bas); rideau.close()
            piece(rideau, mix(blanc(), papier(EAU_CLAIRE), 0.35f), 0.6f)
            repeat(14) {
                val fx = x + (r() - 0.5f) * w * 0.9f
                val y0 = haut + r() * (bas - haut) * 0.3f
                val y1 = bas - r() * (bas - haut) * 0.2f
                val e = 0.35f + r() * 0.6f
                val t = Path()
                t.addRoundRect(RectF(fx - e / 2f, y0, fx + e / 2f, y1), e / 2f, e / 2f, Path.Direction.CW)
                piece(t, if (r() < 0.75f) blanc() else papier(EAU_CLAIRE), 0.3f)
            }
            for (j in 0 until 5) piece(disque(x + (j - 2f) * w * 0.32f, bas + r() * 0.6f, w * (0.3f + r() * 0.2f)), blanc(), 0.5f)
        }

        /**
         * Une chute en filets : des bandes d'eau blanche et bleutée qui
         * s'élargissent en tombant, la roche visible entre elles, l'écume au
         * pied. Un rideau d'une seule pièce se lit comme un pilier.
         */
        fun filets(x: Float, haut: Float, bas: Float, w: Float, graine: Int) {
            val r = Alea(graine)
            val n = 5 + (w * 0.8f).toInt()
            for (k in 0 until n) {
                val u = (k + 0.5f) / n - 0.5f
                val x0 = x + u * w * 0.7f + (r() - 0.5f) * 0.4f
                val x1 = x + u * w * 1.1f + (r() - 0.5f) * 0.8f
                val e0 = w / n * (0.4f + r() * 0.4f)
                val e1 = w / n * (0.7f + r() * 0.6f)
                val y0 = haut + r() * (bas - haut) * 0.12f
                val mx = (x0 + x1) / 2f + (r() - 0.5f) * 0.8f
                val t = Path()
                t.moveTo(x0 - e0 / 2f, y0)
                t.quadTo(mx - (e0 + e1) / 4f, (y0 + bas) / 2f, x1 - e1 / 2f, bas)
                t.lineTo(x1 + e1 / 2f, bas)
                t.quadTo(mx + (e0 + e1) / 4f, (y0 + bas) / 2f, x0 + e0 / 2f, y0)
                t.close()
                piece(t, if (k % 3 == 1) papier(0xFFCFE6EA.toInt()) else papier(0xFFF4F8F8.toInt()), 0.3f)
            }
            for (j in 0 until 5) piece(disque(x + (j - 2f) * w * 0.3f, bas + r() * 0.6f, w * (0.2f + r() * 0.14f) + 0.6f), papier(0xFFF4F8F8.toInt()), 0.4f)
        }

        /** Une bande de brume en papier, effilée aux deux bouts. */
        fun brumePapier(x0: Float, x1: Float, y: Float, e: Float, graine: Int) {
            val ph = Alea(graine)() * 6f
            val col = if (P.nuit) mix(P.cielBas, P.cielHaut, 0.3f) else mix(P.cielBas, 0xFFF2F3EF.toInt(), 0.75f)
            val t = Path()
            fun epais(x: Float) = sin(PI.toFloat() * ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)).pow(0.5f) * e * 0.5f
            var x = x0
            t.moveTo(x0, y)
            while (x <= x1) { t.lineTo(x, y - epais(x) * (0.8f + 0.3f * sin(x * 0.23f + ph))); x += 1.5f }
            x = x1
            while (x >= x0) { t.lineTo(x, y + epais(x) * (0.7f + 0.3f * sin(x * 0.17f + 2f * ph))); x -= 1.5f }
            t.close()
            piece(t, col, 0.4f)
        }

        /** La mousse et les fougères qui coulent le long de la paroi, en longues gouttes verticales. */
        private fun mousses(x0: Float, x1: Float, y0: Float, y1: Float, n: Int, graine: Int) {
            val r = Alea(graine)
            val cols = intArrayOf(papier(0xFF3FA86A.toInt()), papier(0xFF2E9E6E.toInt()), papier(0xFF6FBF5A.toInt()))
            repeat(n) {
                val cx = x0 + r() * (x1 - x0); val haut = y0 + r() * (y1 - y0) * 0.6f
                val l = 4f + r() * (y1 - haut) * 0.6f; val w = 0.8f + r() * 1.2f
                val t = Path()
                t.moveTo(cx, haut)
                t.cubicTo(cx + w, haut + l * 0.2f, cx + w * 0.8f, haut + l * 0.8f, cx, haut + l)
                t.cubicTo(cx - w * 0.8f, haut + l * 0.8f, cx - w, haut + l * 0.2f, cx, haut)
                t.close()
                piece(t, cols[min((r() * 3f).toInt(), 2)], 0.5f)
            }
        }

        /** Un galet de rivière : la pierre sombre, et sa face éclairée en haut. */
        fun galet(cx: Float, cy: Float, rx: Float, col: Int) {
            val ry = rx * 0.62f
            piece(Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }, col, 0.8f)
            piece(Path().apply { addOval(RectF(cx - rx * 0.75f, cy - ry * 0.95f, cx + rx * 0.55f, cy + ry * 0.05f), Path.Direction.CW) },
                mix(col, 0xFFFFFFFF.toInt(), 0.16f), 0f)
        }

        /**
         * La rivière qui part du pied de la chute ([xs], [ys]) et s'élargit
         * jusqu'au bas de la fenêtre entre [xl] et [xr] : l'eau sombre, des
         * remous blancs, des galets de plus en plus gros vers soi.
         */
        fun riviere(xs: Float, ys: Float, xl: Float, xr: Float, graine: Int) {
            val milieu = (xl + xr) / 2f
            fun centre(t: Float) = xs + (milieu - xs) * t.pow(0.7f)
            fun largeur(t: Float) = 2.5f + (xr - xl - 2.5f) * t
            val eau = Path()
            eau.moveTo(xs - 1.2f, ys)
            var t = 0f
            while (t <= 1f) { eau.lineTo(centre(t) - largeur(t) / 2f, ys + (BAS + 2f - ys) * t); t += 0.05f }
            t = 1f
            while (t >= 0f) { eau.lineTo(centre(t) + largeur(t) / 2f, ys + (BAS + 2f - ys) * t); t -= 0.05f }
            eau.close()
            piece(eau, papier(0xFF35585A.toInt()), 0.4f)
            val r = Alea(graine)
            t = 0.04f
            while (t < 1f) {
                val y = ys + (BAS - ys) * t
                val n = 1 + (t * 3f).toInt()
                repeat(n) {
                    val x = centre(t) + (r() - 0.5f) * largeur(t) * 0.8f
                    val l = (1.5f + r() * 3f) * (0.5f + t)
                    val e = 0.3f + 0.5f * t
                    val w = Path()
                    w.addRoundRect(RectF(x - l / 2f, y, x + l / 2f, y + e), e / 2f, e / 2f, Path.Direction.CW)
                    piece(w, if (r() < 0.7f) blanc() else papier(EAU_CLAIRE), 0.2f)
                }
                t += 0.035f + 0.02f * t
            }
            val pierres = intArrayOf(papier(0xFF5C5650.toInt()), papier(0xFF6E665E.toInt()), papier(0xFF4A4642.toInt()), papier(0xFF7A7068.toInt()))
            t = 0.02f
            while (t < 1.05f) {
                val y = ys + (BAS - ys) * t
                for (cote in floatArrayOf(-1f, 1f, 0f)) {
                    if (cote == 0f && r() < 0.6f) continue
                    val x = centre(t) + cote * largeur(t) * (0.42f + r() * 0.15f) + (r() - 0.5f) * 1.5f
                    galet(x, y, (0.8f + r() * 1.4f) * (0.5f + 1.6f * t), pierres[min((r() * 4f).toInt(), 3)])
                }
                t += 0.05f + 0.04f * t
            }
        }

        /** Les feuilles du sous-bois au premier plan, dans les deux coins. */
        private fun sousBois() {
            feuilleBananier(-1f, 62f, 15f, -35f, papier(0xFF4FB06A.toInt()))
            feuilleCoeur(8f, 64f, 4.5f, -15f, papier(VERT_PROFOND))
            balisier(15f, 65f, 8f, papier(SARCELLE))
            feuilleCoeur(93f, 64f, 4.5f, 15f, papier(VERT_PROFOND))
            feuilleBananier(101f, 63f, 13f, -150f, papier(0xFF3FA86A.toInt()))
        }

        /**
         * Les Chutes du Carbet en papier découpé, d'après la photo du
         * propriétaire (proposition A, retenue le 2026-11-07) : la chute
         * tombe d'une haute falaise à droite, coiffée de forêt et de brume ;
         * la forêt de gauche descend en lisières jusqu'à la rivière, qui
         * vient vers soi entre les galets. La chute reste à côté du dessin du
         * mot, jamais derrière.
         */
        fun carbetPapier() {
            morneBoise(17f, 3f, 900, mix(papier(0xFF5FA88A.toInt()), P.cielBas, 0.55f), 1.8f)
            brumePapier(-2f, 102f, 22f, 6f, 901)
            plan(1)
            val sommet = { x: Float -> 9f - (x - 64f) * 0.04f + sin(x * 0.3f) * 0.6f }
            val falaise = Path()
            falaise.moveTo(60f, BAS + 2f); falaise.lineTo(62f, 30f)
            falaise.cubicTo(62.5f, 18f, 64f, 11f, 66f, sommet(66f))
            var x = 66f
            while (x <= 102f) { falaise.lineTo(x, sommet(x)); x += 2f }
            falaise.lineTo(102f, BAS + 2f); falaise.close()
            falaise.op(bosses(64f, 103f, sommet, 1.8f, 2.6f, Alea(902)), Path.Op.UNION)
            piece(falaise, papier(0xFF3F5A44.toInt()))
            val paroi = Path()
            paroi.moveTo(66f, 50f); paroi.cubicTo(66f, 30f, 68f, 18f, 72f, 14f)
            paroi.lineTo(90f, 13f); paroi.cubicTo(92f, 26f, 93f, 40f, 92f, 50f); paroi.close()
            piece(paroi, roche(), 0.6f)
            mousses(66f, 92f, 16f, 48f, 14, 906)
            cascade(CHUTE_CARBET[0], CHUTE_CARBET[1], CHUTE_CARBET[2], CHUTE_CARBET[3], 903)
            brumePapier(56f, 102f, 11f, 4.5f, 904)
            val verts = intArrayOf(papier(VERT_PROFOND), papier(0xFF2E9E6E.toInt()), papier(0xFF3FA86A.toInt()), papier(SARCELLE))
            for (k in 0 until 4) rive(true, 14f + k * 6f, 40f + k * 4f, 48f - k * 7f, 905 + k, verts[k], 4.5f + k * 0.6f)
            plan(2)
            riviere(78f, 47f, 30f, 74f, 910)
            rive(false, 30f, 54f, 90f, 911, papier(0xFF2E9E6E.toInt()), 5f)
            sousBois()
        }

        /** Les Chutes du Carbet : la chute dans la paroi, le bassin, les fougères. */
        fun chutesCarbet() {
            if (PAPIER) {
                carbetPapier()
                return
            }
            val r = Alea(131)
            crete(24f, 2.2f, 132, P.loin)
            plan(1)
            forme("M34 66L34 17C42 15.5 54 15.5 64 17L64 66Z", mix(P.pierre, P.pres, 0.45f))
            forme("M0 66L0 13C8 11 22 13 36 17L40 66Z", P.milieu)
            forme("M100 66L100 11C92 9 76 11 62 17L58 66Z", P.milieu)
            bosquet(-2f, 36f, 14f, 2.8f, 133, mix(P.milieu, P.pres, 0.35f))
            bosquet(62f, 102f, 13f, 2.8f, 134, mix(P.milieu, P.pres, 0.35f))
            rect(46f, 16f, 6.4f, 42f, blanc(), 0.85f)
            val ecume = mix(P.eau, 0xFFFFFFFF.toInt(), 0.5f)
            for (k in 0 until 16) rect(46.4f + r() * 5.6f, 17f + r() * 38f, 0.3f, 2f + r() * 4f, ecume, 0.8f)
            plan(2)
            eau(57f, 135)
            rond(49f, 57.5f, 6f, blanc(), 0.35f)
            rond(8f, 64f, 8f, P.sol)
            rond(93f, 65f, 7f, P.sol)
            balisier(16f, 60f, 11f, mix(P.pres, P.milieu, 0.3f))
            balisier(82f, 59f, 13f, mix(P.pres, P.milieu, 0.3f))
        }

        // ------------------------------------------- Sofaïa en papier

        /** Une pièce droite et mince de [a] à [b], épaisse de [e] : un barreau, une lisse, un filet d'eau. */
        private fun barre(ax: Float, ay: Float, bx: Float, by: Float, e: Float, col: Int, ombre: Float = 0.3f) {
            val dx = bx - ax; val dy = by - ay
            val n = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val nx = -dy / n * e / 2f; val ny = dx / n * e / 2f
            pieceD("M${ax + nx} ${ay + ny}L${bx + nx} ${by + ny}L${bx - nx} ${by - ny}L${ax - nx} ${ay - ny}Z", col, ombre)
        }

        /** Une rambarde de [a] à [b], haute de [h] : poteaux, lisse haute, lisse basse et barreaux. */
        private fun rambarde(ax: Float, ay: Float, bx: Float, by: Float, h: Float, poteaux: Int, barreaux: Int, col: Int, e: Float) {
            val sombre = mix(col, 0xFF000000.toInt(), 0.18f)
            for (k in 0..barreaux) {
                val u = k / barreaux.toFloat()
                val x = ax + (bx - ax) * u; val y = ay + (by - ay) * u
                barre(x, y - h * 0.15f, x, y - h * 0.85f, e * 0.45f, sombre, 0.2f)
            }
            barre(ax, ay - h * 0.15f, bx, by - h * 0.15f, e * 0.7f, col)
            barre(ax, ay - h * 0.85f, bx, by - h * 0.85f, e * 0.7f, col)
            for (k in 0..poteaux) {
                val u = k / poteaux.toFloat()
                val x = ax + (bx - ax) * u; val y = ay + (by - ay) * u
                barre(x, y, x, y - h, e, col, 0.5f)
            }
            barre(ax, ay - h, bx, by - h, e, col, 0.5f)
        }

        /**
         * Sofaïa en papier découpé, d'après la photo du propriétaire
         * (2026-11-07) : le bassin carré creusé dans la dalle de béton, l'eau
         * chaude qui y tombe en filets du mur du fond, l'escalier, la
         * rambarde de bois à gauche et au fond, la rambarde blanche à droite
         * et son ombre en barreaux sur le béton, la poutre en travers ; derrière,
         * la pelouse au soleil et la forêt.
         */
        fun sofaiaPapier() {
            morneBoise(14f, 2.5f, 1300, papier(0xFF2E5E36.toInt()), 3.2f)
            morneBoise(22f, 1.8f, 1301, papier(0xFF3E7A3A.toInt()), 3f)
            plan(1)
            morneBoise(25f, 1.6f, 1303, papier(0xFF2A5530.toInt()), 2.6f)
            for ((ax, ar) in arrayOf(4f to 3.6f, 15f to 3f, 88f to 3.2f, 98f to 3.8f)) arbreRond(ax, 31f, ar, papier(0xFF3F7A36.toInt()), papier(0xFF4A3A2E.toInt()))
            // La pelouse, ses taches de soleil et d'ombre.
            pieceD("M-2 31Q30 28.5 60 30Q82 31 102 29L102 68L-2 68Z", papier(0xFF5DA03A.toInt()), 0.5f)
            val r = Alea(1302)
            repeat(10) {
                val x = -2f + r() * 104f; val y = 31.5f + r() * 7f; val l = 4f + r() * 9f
                pieceD("M$x ${y}Q${x + l / 2f} ${y - 1.2f} ${x + l} ${y}Q${x + l / 2f} ${y + 1.2f} $x ${y}Z",
                    papier(if (r() < 0.6f) 0xFF9CCF52.toInt() else 0xFF3F7F35.toInt()), 0f)
            }
            // Les murets de béton à gauche et à droite.
            pieceD("M-2 35L14 34L15 44L-2 46Z", papier(0xFFA9A598.toInt()))
            pieceD("M-2 35L14 34L14.4 35.6L-2 36.8Z", papier(0xFFCFCBC0.toInt()), 0.2f)
            pieceD("M90 33L102 32.5L102 44L91 43Z", papier(0xFFA9A598.toInt()))
            plan(2)
            // La dalle de béton, puis le trou du bassin.
            val beton = papier(0xFFBDB8AC.toInt())
            pieceD("M-2 41Q50 37.5 102 40L102 68L-2 68Z", beton, 0.5f)
            pieceD("M-2 41Q50 37.5 102 40L102 41.4Q50 39 -2 42.6Z", papier(0xFFD6D2C6.toInt()), 0.2f)
            pieceD("M22 41L76 41L88 64L10 64Z", papier(0xFF3A3C38.toInt()), 0.2f)
            // Le mur du fond, ses marches à gauche.
            pieceD("M23 41.6L75.4 41.6L74.6 49.4L23.8 49.4Z", papier(0xFF5A5852.toInt()), 0f)
            for (k in 0 until 3) {
                val y = 42.4f + k * 2.4f
                pieceD("M23.6 ${y}L${30f + k * 2.6f} ${y}L${30f + k * 2.6f} ${y + 1.2f}L23.6 ${y + 1.2f}Z", papier(0xFF7C7A72.toInt()), 0.2f)
            }
            // L'eau sombre, ses reflets, le petit bloc immergé.
            pieceD("M23.8 49.4L74.6 49.4L87 63.6L11 63.6Z", papier(0xFF26403A.toInt()), 0f)
            for ((x, y, l) in arrayOf(Triple(30f, 53f, 9f), Triple(48f, 56f, 12f), Triple(24f, 59f, 8f), Triple(62f, 60f, 10f), Triple(40f, 61.5f, 7f)))
                pieceD("M$x ${y}Q${x + l / 2f} ${y - 0.5f} ${x + l} ${y}Q${x + l / 2f} ${y + 0.4f} $x ${y}Z", papier(0xFF4E6E64.toInt()), 0f)
            pieceD("M52 50.6L56.4 50.2L56.8 53.4L52.4 53.8Z", papier(0xFF9A9A94.toInt()), 0.3f)
            // Les filets d'eau chaude qui tombent des pommeaux du tuyau.
            val filet = papier(0xFFDCEAE8.toInt())
            for ((x, y1) in DOUCHES) {
                // L'échelle de la perspective : 1 pour le filet le plus long, au devant.
                val k = (y1 - doucheHaut(x)) / (doucheBas(23f) - doucheHaut(23f))
                var y = doucheHaut(x)
                while (y < y1) {
                    val l = min((1.2f + r() * 1.2f) * k, y1 - y)
                    val dx = sin(y * 1.7f + x) * 0.15f * k
                    barre(x + dx, y, x + dx + 0.05f, y + l, 0.3f * k, filet, 0f)
                    y += l + (0.3f + r() * 0.4f) * k
                }
                val w = 1.7f * k
                pieceD("M${x - w} ${y1 + 0.5f * k}Q${x + 0.2f} ${y1 - 0.3f * k} ${x + w} ${y1 + 0.5f * k}Q${x + 0.2f} ${y1 + k} ${x - w} ${y1 + 0.5f * k}Z",
                    papier(0xFF6F928A.toInt()), 0f)
            }
            // L'ombre des barreaux de la rambarde blanche, en travers du béton à droite.
            val ombreBeton = papier(0xFF8F8A80.toInt())
            for (k in 0 until 6) {
                val x = 84f + k * 3f
                pieceD("M$x ${43f + k * 2f}L${x + 1f} ${43f + k * 2f}L${x + 9f} ${50f + k * 2.6f}L${x + 8f} ${50f + k * 2.6f}Z", ombreBeton, 0f)
            }
            // La conduite grise qui longe le bord gauche.
            barre(-2f, 55f, 14f, 52.4f, 1.6f, papier(0xFF9C9E9A.toInt()), 0.5f)
            // Les rambardes : bois au fond et à gauche, métal blanc à droite.
            val bois = papier(0xFF8C5E3C.toInt())
            rambarde(50f, 41.2f, 76f, 41.2f, 6.5f, 3, 12, bois, 0.9f)
            rambarde(22f, 41.2f, 10f, 64f, 8f, 3, 10, bois, 1.1f)
            // Le tuyau des douches : il sort de la conduite, se coude et traverse le
            // bassin sur son poteau de bois ; dessous, ses minuscules pommeaux.
            val (tx0, ty0, tx1, ty1) = TUYAU_DOUCHES
            barre(30f, ty0 + (30f - tx0) / (tx1 - tx0) * (ty1 - ty0), 30.4f, 62f, 1f, bois, 0.6f)
            val metal = papier(0xFF9C9E9A.toInt())
            barre(13.4f, 52.6f, tx0, ty0, 1.2f, metal, 0.5f)
            barre(tx0, ty0, tx1, ty1, 1.2f, metal, 0.7f)
            barre(tx0 + 1f, ty0 - 0.4f, tx1, ty1 - 0.4f, 0.3f, mix(metal, 0xFFFFFFFF.toInt(), 0.45f), 0f)
            for ((x, _) in DOUCHES) {
                val y = doucheHaut(x)
                pieceD("M${x - 0.45f} ${y - 0.6f}L${x + 0.45f} ${y - 0.6f}L${x + 0.3f} ${y}L${x - 0.3f} ${y}Z", papier(0xFF5E605C.toInt()), 0f)
            }
            rambarde(76f, 41.2f, 89f, 64.5f, 9.5f, 4, 14, papier(0xFFE4E6E2.toInt()), 0.9f)
            balisier(5f, 67f, 13f, feuille())
            balisier(96f, 67f, 13f, feuille())
        }

        /** Sofaïa : le bassin d'eau chaude en forêt, le bambou qui l'alimente, la vapeur. */
        fun sofaia() {
            if (PAPIER) { sofaiaPapier(); return }
            crete(28f, 2f, 141, P.loin)
            bosquet(-2f, 102f, 30f, 2.4f, 142, mix(P.loin, P.milieu, 0.5f))
            plan(1)
            forme("M0 66L0 35C20 31 40 37 60 34C80 31 90 35 100 34L100 66Z", P.milieu)
            bosquet(-2f, 102f, 37f, 3f, 143, mix(P.milieu, P.pres, 0.35f))
            plan(2)
            rect(0f, 49f, 100f, 17f, P.pres)
            forme("M20 55Q50 45.5 80 55Q50 64.5 20 55Z", P.pierre)
            forme("M24.5 55Q50 48 75.5 55Q50 62 24.5 55Z", mix(P.eau, 0xFF6FC7B8.toInt(), if (P.nuit) 0.1f else 0.35f))
            rect(10f, 47.4f, 15f, 1.3f, duJour(0xFFB49A52.toInt()))
            trait("M25 48.2Q26.4 50 26.8 53.4", blanc(), 0.8f, 0.85f)
            vapeur(40f, 52f, 11f)
            vapeur(52f, 50.5f, 13f)
            vapeur(63f, 52.5f, 9f)
            bosquet(-2f, 18f, 53f, 3.2f, 144, P.sol)
            bosquet(82f, 102f, 53f, 3.2f, 145, P.sol)
            balisier(12f, 60f, 11f, mix(P.pres, P.milieu, 0.3f))
            balisier(88f, 60f, 12f, mix(P.pres, P.milieu, 0.3f))
        }

        // ------------------------------------------- Ravine Chaude en papier

        /** Une case de bois vue de trois quarts : le pignon à droite, le long pan à gauche, la tôle claire. */
        private fun caseBois(x: Float, y: Float, l: Float, p: Float, h: Float, mur: Int, pignon: Int, tole: Int, portes: Int) {
            val fuite = l * 0.22f
            val faite = h * 0.75f
            // Le long pan : de (x, y) à (x + l, y + fuite) au sol.
            pieceD("M$x ${y - h}L${x + l} ${y + fuite - h}L${x + l} ${y + fuite}L$x ${y}Z", mur, 0.6f)
            for (k in 0 until 6) {
                val u = (k + 0.5f) / 6f
                barre(x + l * u, y + fuite * u - h * 0.98f, x + l * u, y + fuite * u - 0.1f, 0.12f, mix(mur, 0xFF000000.toInt(), 0.2f), 0f)
            }
            for (k in 0 until portes) {
                val u = 0.18f + k * 0.62f / max(1, portes - 1)
                val px = x + l * u; val py = y + fuite * u
                pieceD("M$px ${py - h * 0.7f}L${px + l * 0.1f} ${py + fuite * 0.1f - h * 0.7f}L${px + l * 0.1f} ${py + fuite * 0.1f - 0.2f}L$px ${py - 0.2f}Z",
                    papier(0xFF2E2620.toInt()), 0f)
            }
            // Le pignon : de (x + l, y + fuite) à (x + l + p, y + fuite - p * 0.35).
            val gx = x + l; val gy = y + fuite; val dx = p; val dy = -p * 0.35f
            pieceD("M$gx ${gy - h}L${gx + dx / 2f} ${gy + dy / 2f - h - faite}L${gx + dx} ${gy + dy - h}L${gx + dx} ${gy + dy}L$gx ${gy}Z", pignon, 0.6f)
            pieceD("M${gx + dx * 0.4f} ${gy + dy * 0.4f - h * 0.68f}L${gx + dx * 0.62f} ${gy + dy * 0.62f - h * 0.68f}L${gx + dx * 0.62f} ${gy + dy * 0.62f - 0.1f}L${gx + dx * 0.4f} ${gy + dy * 0.4f - 0.1f}Z",
                papier(0xFF2E2620.toInt()), 0f)
            // La tôle : du bord du long pan jusqu'au faîtage, avec un peu de débord.
            val ax = x - 0.8f; val ay = y - h - 0.3f; val bx = gx + 0.6f; val by = gy - h - 0.2f
            val fx = gx + dx / 2f + 0.6f; val fy = gy + dy / 2f - h - faite - 0.3f
            val rx = fx - (bx - ax); val ry = fy - (by - ay)
            pieceD("M$ax ${ay}L$bx ${by}L$fx ${fy}L$rx ${ry}Z", tole, 0.9f)
            for (k in 1 until 8) {
                val u = k / 8f
                barre(ax + (bx - ax) * u, ay + (by - ay) * u, rx + (fx - rx) * u, ry + (fy - ry) * u, 0.1f, mix(tole, 0xFF000000.toInt(), 0.12f), 0f)
            }
            barre(ax, ay, bx, by, 0.35f, mix(tole, 0xFF000000.toInt(), 0.25f), 0f)
        }

        /** Une touffe de bambous : des chaumes qui s'arquent vers [sens], leurs feuilles fines au bout. */
        private fun bambous(x: Float, y: Float, h: Float, sens: Float, graine: Int) {
            val r = Alea(graine)
            val chaume = papier(0xFF7A9436.toInt())
            val feuilles = intArrayOf(papier(0xFF4E8030.toInt()), papier(0xFF6E9E3C.toInt()), papier(0xFF3E6E2C.toInt()))
            for (k in 0 until 9) {
                val bx = x + (r() - 0.5f) * 4f
                val penche = sens * (3f + k * 1.6f + r() * 2f)
                val hk = h * (0.7f + r() * 0.3f)
                var px = bx; var py = y
                for (s in 1..6) {
                    val u = s / 6f
                    val nx = bx + penche * u * u; val ny = y - hk * u * (1f - 0.22f * u)
                    barre(px, py, nx, ny, 0.5f - u * 0.25f, chaume, 0.3f)
                    // Les feuilles retombent, du côté où le chaume s'arque.
                    if (s >= 3) repeat(4) {
                        val a = if (sens < 0) 115f + r() * 55f else 10f + r() * 55f
                        feuillePliee(nx, ny, 2f + r() * 1.6f, a, feuilles[(r() * 3f).toInt().coerceAtMost(2)], 0.2f)
                    }
                    px = nx; py = ny
                }
            }
        }

        /**
         * Ravine Chaude en papier découpé, d'après une carte postale d'époque
         * du propriétaire, passée en couleur (2026-11-07) : l'établissement
         * thermal, sa case de bois à toit de tôle claire, le bassin d'eau
         * chaude à côté, les deux cases sur le morne derrière, la forêt, et
         * la touffe de bambous à droite. Les cocotiers de la carte postale
         * deviennent de grands arbres à houppier rond (pas de cocotier).
         */
        fun ravineChaudePapier() {
            morneBoise(15f, 2.2f, 1400, papier(0xFF2F5E36.toInt()), 3f)
            // La forêt du haut, serrée : les houppiers se recouvrent, on n'y voit pas de clairière.
            val rf = Alea(1408)
            for (rang in 0 until 2) {
                var ax = -2f + rang * 3.5f
                while (ax < 104f) {
                    val ar = 3.4f + rf() * 1.4f
                    arbreRond(ax, 22f + rang * 4f - ax * 0.05f + rf() * 1.5f, ar,
                        papier(if (rang == 0) 0xFF356A36.toInt() else 0xFF417E3A.toInt()), papier(0xFF5A4636.toInt()))
                    ax += 6f + rf() * 2.5f
                }
            }
            // Le sous-bois cache les troncs : une forêt, pas un verger.
            val sousBois = { x: Float -> 20f - x * 0.05f + sin(x * 0.4f) * 0.5f }
            val sb = sousLaCrete(-2f, 102f, sousBois)
            sb.op(bosses(-2f, 102f, sousBois, 1.6f, 2.4f, Alea(1409)), Path.Op.UNION)
            piece(sb, papier(0xFF2E5A30.toInt()))
            plan(1)
            // Le morne en herbe, les deux cases du haut, et les grands arbres de gauche.
            val herbe = brisee(-2f, 40f, 20f, 36f, 40f, 31f, 60f, 27.5f, 80f, 25f, 102f, 23f)
            val t = sousLaCrete(-2f, 102f, herbe)
            t.op(bosses(-2f, 102f, { x -> herbe(x) + 0.6f }, 0.7f, 1.2f, Alea(1401)), Path.Op.UNION)
            piece(t, papier(0xFF7DAE48.toInt()))
            taches(-2f, 102f, 50f, 10, 1402, papier(0xFF5E9238.toInt()), herbe)
            val creme = papier(0xFFE6DCC4.toInt())
            caseBois(84f, 28.5f, 7f, 4f, 3.6f, papier(0xFFD2C6AA.toInt()), creme, papier(0xFF8E8A84.toInt()), 1)
            caseBois(67f, 35.5f, 6f, 3.4f, 3.2f, papier(0xFFC8B896.toInt()), creme, papier(0xFF8E8A84.toInt()), 1)
            for ((ax, ay, ar) in arrayOf(Triple(3f, 44f, 5.2f), Triple(13f, 42f, 4.4f), Triple(24f, 40f, 3.8f)))
                arbreRond(ax, ay, ar, papier(0xFF46843C.toInt()), papier(0xFF6A5442.toInt()))
            plan(2)
            // Le bas du morne, sombre de végétation, puis la case du bain et son bassin.
            val bas = brisee(-2f, 50f, 24f, 51.5f, 50f, 52f, 76f, 50.5f, 102f, 49f)
            val b = sousLaCrete(-2f, 102f, bas)
            b.op(bosses(-2f, 102f, { x -> bas(x) + 0.5f }, 0.9f, 1.6f, Alea(1403)), Path.Op.UNION)
            piece(b, papier(0xFF4E7E38.toInt()))
            pieceD("M24 60.5Q24 54.4 48 54.2Q72 54.4 72 59Q72 63.6 48 63.8Q24 63.6 24 60.5Z", papier(0xFFA9A496.toInt()), 0.6f)
            pieceD("M26 60.2Q26 55.6 48 55.4Q70 55.6 70 59Q70 62.6 48 62.8Q26 62.6 26 60.2Z", papier(0xFF86C2B0.toInt()), 0.2f)
            for ((x, y, l) in arrayOf(Triple(32f, 58f, 8f), Triple(46f, 57f, 11f), Triple(56f, 60.4f, 9f), Triple(38f, 61.2f, 7f)))
                pieceD("M$x ${y}Q${x + l / 2f} ${y - 0.4f} ${x + l} ${y}Q${x + l / 2f} ${y + 0.35f} $x ${y}Z", papier(0xFFB8E0D2.toInt()), 0f)
            VAPEURS_RAVINE.forEachIndexed { i, (x, pied, haut, w, derive) -> panache(x, pied, haut, w, derive, 1404 + i) }
            caseBois(1f, 55f, 17f, 8f, 9f, papier(0xFF8A6A4A.toInt()), papier(0xFF735638.toInt()), papier(0xFFD0CCC2.toInt()), 3)
            // La touffe de bambous à droite, la végétation devant, les balisiers du colibri.
            bambous(96f, 60f, 34f, -1f, 1406)
            val devant = brisee(-2f, 63f, 14f, 62.5f, 28f, 64.5f, 70f, 65f, 86f, 62f, 102f, 61f)
            val d = sousLaCrete(-2f, 102f, devant)
            d.op(bosses(-2f, 102f, { x -> devant(x) + 0.4f }, 0.8f, 1.4f, Alea(1407)), Path.Op.UNION)
            piece(d, papier(0xFF35602C.toInt()))
            balisier(8f, 67f, 13f, feuille())
            balisier(88f, 67f, 13f, feuille())
        }

        /** Ravine Chaude : la rivière tiède au fond de la ravine, les roches, les fougères. */
        fun ravineChaude() {
            if (PAPIER) { ravineChaudePapier(); return }
            val r = Alea(151)
            crete(24f, 2f, 152, P.loin)
            plan(1)
            forme("M0 66L0 22C14 24 26 33 36 47L40 66Z", P.milieu)
            forme("M100 66L100 20C86 22 72 33 62 47L58 66Z", P.milieu)
            bosquet(-2f, 30f, 24f, 2.8f, 153, mix(P.milieu, P.pres, 0.35f))
            bosquet(70f, 102f, 22f, 2.8f, 154, mix(P.milieu, P.pres, 0.35f))
            plan(2)
            val riviere = mix(P.eau, 0xFF7FC4B4.toInt(), if (P.nuit) 0.1f else 0.3f)
            forme("M33 66L45.5 46.4Q50 45.6 54.5 46.4L67 66Z", riviere)
            val clair = mix(riviere, 0xFFFFFFFF.toInt(), if (P.nuit) 0.18f else 0.45f)
            for (k in 0 until 8) {
                val y = 49f + r() * 15f
                val demi = (y - 46f) * 0.55f
                rect(50f - demi * 0.6f + r() * demi, y, 1.5f + r() * 3f, 0.3f, clair, 0.7f)
            }
            val roc = mix(P.pierre, P.pres, 0.45f)
            ovale(40f, 61f, 3.4f, 1.8f, roc); ovale(58f, 59.5f, 3f, 1.5f, roc)
            ovale(49f, 64.6f, 2.6f, 1.2f, roc); ovale(53.5f, 51.5f, 1.6f, 0.8f, roc)
            vapeur(46f, 52f, 11f)
            vapeur(53f, 56f, 10f)
            forme("M0 66L0 56Q10 54 22 58L30 66Z", P.pres)
            forme("M100 66L100 55Q90 53 78 57L70 66Z", P.pres)
            balisier(14f, 58f, 13f, mix(P.pres, P.milieu, 0.3f))
            balisier(86f, 57f, 14f, mix(P.pres, P.milieu, 0.3f))
        }

        // ------------------------------------------- la Pointe des Châteaux en papier

        /** Des bandes d'eau en lanières de [y0] à [y1], plus épaisses vers soi. */
        private fun lanieresMer(y0: Float, y1: Float, graine: Int, cols: IntArray) {
            val r = Alea(graine)
            var y = y0
            while (y < y1) {
                val q = (y - y0) / max(1f, y1 - y0)
                val e = 0.4f + 0.7f * q
                var x = -2f + r() * 3f
                while (x < LARGEUR + 2f) {
                    val l = (4f + r() * 10f) * (0.7f + q)
                    val t = Path()
                    t.addRoundRect(RectF(x, y, x + l, y + e), e / 2f, e / 2f, Path.Direction.CW)
                    piece(t, cols[min((r() * cols.size).toInt(), cols.size - 1)], 0.2f)
                    x += l + 0.8f + r() * 2.5f
                }
                y += e + 0.45f + 0.4f * q
            }
        }

        /**
         * La Pointe des Châteaux en papier découpé, d'après la photo du
         * propriétaire (2026-11-07) : le morne couvert de broussaille sèche,
         * sa falaise de calcaire en profil à gauche, la grande croix au
         * sommet, à droite pour rester à côté du dessin du mot ; le plateau
         * rocheux du rivage et l'écume, la mer d'un bleu profond, une grosse
         * vague qui se brise, et la roche noire au premier plan. Les vagues
         * bougent sur la carte ouverte (`Vagues` dans [Ambiance]).
         */
        fun pointeChateauxPapier() {
            cumulus(-4f, 21f, 26f, 1500)
            cumulus(30f, 12f, 14f, 1501)
            plan(1)
            // Le morne, sa falaise à gauche, sa croix, tassés vers le rivage
            // pour que la croix tienne sous l'arche des cartes rares.
            tasser(50f, 36f, 1f, 0.7f) {
                val crete = brisee(16f, 36f, 30f, 33.5f, 44f, 31f, 52f, 28.5f, 55f, 24f, 58f, 21f, 62f, 18.6f, 70f, 17.4f,
                    80f, 17f, 92f, 18f, 102f, 19f)
                val morne = sousLaCrete(16f, 102f, crete)
                morne.op(bosses(50f, 103f, { x -> crete(x) + 0.5f }, 1f, 1.6f, Alea(1502)), Path.Op.UNION)
                piece(morne, papier(0xFF2C5C26.toInt()))
                pieceD("M52 28.5L55 24L58 21L62 18.6L64.5 21Q62 23 63 26Q60 28 61 31L50 33Z", papier(0xFF8E8070.toInt()), 0.5f)
                pieceD("M55 24L58 21L59.6 22.4Q57.4 24.6 58.4 27L55.6 28Z", papier(0xFF6E6052.toInt()), 0.2f)
                taches(48f, 102f, 34f, 14, 1503, papier(0xFF4A8236.toInt()), crete)
                taches(56f, 102f, 34f, 8, 1504, papier(0xFF1E441C.toInt()), crete)
                pieceD("M14 37Q40 33.4 70 32.6Q86 32.2 102 31.6L102 36L14 38Z", papier(0xFF9DAE4E.toInt()), 0.3f)
                val pierre = papier(0xFFD8C9A4.toInt())
                pieceD("M86.6 17.6L89.4 17.6L89 16.4L87 16.4Z", papier(0xFFB8A984.toInt()), 0.4f)
                barre(88f, 16.6f, 88f, 4.5f, 1.2f, pierre, 0.6f)
                barre(85.2f, 8.2f, 90.8f, 8.2f, 1f, pierre, 0.6f)
                barre(88.35f, 16f, 88.35f, 5f, 0.3f, papier(0xFFB0A07C.toInt()), 0f)
            }
            // Le plateau de calcaire du rivage, ses blocs, l'écume qui le borde.
            pieceD("M-2 36Q20 34.4 40 35.2Q70 35.8 102 34.4L102 41L-2 41Z", papier(0xFF9C7A55.toInt()), 0.5f)
            pieceD("M-2 37.6Q30 36.4 60 37.2Q80 37.4 102 36.6L102 38L-2 39Z", papier(0xFF7C5E42.toInt()), 0.2f)
            for ((gx, gy, gr) in arrayOf(Triple(18f, 37.2f, 1.4f), Triple(44f, 37.4f, 1.1f), Triple(71f, 37f, 1.6f), Triple(77f, 36.9f, 1.1f), Triple(92f, 36.6f, 1.3f)))
                galet(gx, gy, gr, papier(0xFF5A4636.toInt()))
            pieceD("M-2 39.4L102 39.2L102 42L-2 42Z", papier(0xFF2A6EA8.toInt()), 0f)
            val ecume = Path()
            val re = Alea(1505)
            var ex = -2f
            // Une bande d'écume continue, bosselée, plus épaisse là où la mer frappe.
            ecume.op(chemin("M-2 ${RIVAGE_CHATEAUX - 0.4f}L102 ${RIVAGE_CHATEAUX - 0.6f}L102 ${RIVAGE_CHATEAUX + 1.6f}L-2 ${RIVAGE_CHATEAUX + 1.8f}Z"), Path.Op.UNION)
            while (ex < 104f) {
                val grosse = ex in 30f..56f
                val rr = if (grosse) 1.2f + re() * 1.6f else 0.5f + re() * 1.1f
                ecume.op(disque(ex, RIVAGE_CHATEAUX - re() * (if (grosse) 2f else 0.6f), rr), Path.Op.UNION)
                ex += 0.9f + re() * 2.6f
            }
            ecume.op(chemin("M34 40Q36 33.5 41 34.4Q44 32.6 48 35Q52 34 53 39Z"), Path.Op.UNION)
            piece(ecume, papier(0xFFF6F8F6.toInt()), 0.4f)
            plan(2)
            // La mer profonde, la grosse vague qui se brise, la roche noire devant.
            val bleus = intArrayOf(papier(0xFF2A78B8.toInt()), papier(0xFF1E4E8C.toInt()), papier(0xFF3E9AD0.toInt()), papier(0xFF2AA0B8.toInt()))
            pieceD("M-2 41L102 41L102 68L-2 68Z", papier(0xFF1F5C9E.toInt()), 0.2f)
            lanieresMer(41.4f, 50f, 1506, bleus)
            // La grosse vague, qui se brise vers la gauche : le dos turquoise,
            // le creux sombre sous la lèvre, l'écume qui dévale la face.
            pieceD("M-2 56L-2 53.5Q8 52 15 47.5Q21 42.4 28 42Q35 42.4 41 47.4Q49 53 64 53.8L64 56Z", papier(0xFF1F9FB0.toInt()))
            pieceD("M30 43Q39 45.4 48 51.8L46 52.4Q37 46.4 30 43.8Z", papier(0xFF52C6CA.toInt()), 0f)
            pieceD("M15 47.6Q19.6 44 26 43Q21.6 45.6 20.4 51.6Q17.4 50 15 47.6Z", papier(0xFF125A78.toInt()), 0.2f)
            pieceD("M16 47Q21.6 42.2 29 41.8Q33.4 42 35.6 44Q31 43.8 28.6 45.2Q25.6 47.6 24.4 51.8Q22.4 49.4 20.8 52.4Q19.4 49.8 17.2 51.4Q17.4 49 15.4 49.4Z",
                papier(0xFFF6F8F6.toInt()), 0.4f)
            val (c0, cy0, c1, cy1) = VAGUE_CHATEAUX
            val rc = Alea(1507)
            val embruns = Path()
            for (k in 0..8) {
                val u = k / 8f
                embruns.op(disque(c0 + (c1 - c0) * u + rc() * 0.8f, cy0 + (cy1 - cy0) * u - 0.8f - rc() * 1.2f, 0.4f + rc() * 0.6f), Path.Op.UNION)
            }
            piece(embruns, papier(0xFFF6F8F6.toInt()), 0.2f)
            lanieresMer(55f, 62f, 1508, bleus)
            val roche = brisee(-2f, 61.5f, 8f, 60.4f, 16f, 61.6f, 26f, 60.6f, 38f, 62f, 50f, 61f, 62f, 62.2f, 74f, 61f, 86f, 62f, 96f, 60.8f, 102f, 61.6f)
            val rocher = sousLaCrete(-2f, 102f, roche)
            rocher.op(bosses(-2f, 102f, { x -> roche(x) + 0.3f }, 0.5f, 0.9f, Alea(1509)), Path.Op.UNION)
            piece(rocher, papier(0xFF3E322A.toInt()))
            taches(-2f, 102f, 66f, 12, 1510, papier(0xFF5E4C3E.toInt()), roche)
        }

        /** La Pointe des Châteaux : les falaises calcaires, la Roche dans l'écume, la croix, la Désirade au loin. */
        fun pointeChateaux() {
            if (PAPIER) { pointeChateauxPapier(); return }
            forme("M66 33.5L68 31Q72 30 84 30Q93 30.2 95 33.5Z", P.loin)
            plan(1)
            eau(33.5f, 161)
            val calc = calcaire()
            forme("M28 34L29.5 26L32 22.6L35.4 22L37.6 25L39.5 34Z", calc)
            forme("M35.4 22L37.6 25L39.5 34L36 34L35.8 26Z", mix(calc, P.pres, 0.35f), 0.7f)
            forme("M43 34L44 30L46 29.4L47.4 34Z", calc)
            for (x in floatArrayOf(27f, 38.5f, 42f, 47f)) rect(x, 33.6f, 2.4f, 0.5f, blanc(), 0.8f)
            plan(2)
            val lande = mix(P.milieu, calc, 0.3f)
            forme("M0 66L0 29L7 27.5L15 30.5L22 37L28 46L36 53L50 57.5L70 59.5L100 61L100 66Z", lande)
            forme("M15 30.5L22 37L28 46L36 53L33.6 54L26 47.4L20 38.6L13.6 31.6Z", calc)
            bosquet(0f, 14f, 31f, 1.8f, 162, mix(lande, P.pres, 0.4f))
            val bois = mix(P.sol, P.pierre, 0.2f)
            rect(6.6f, 16f, 0.9f, 12f, bois)
            rect(4.6f, 18.6f, 4.9f, 0.9f, bois)
        }

        // ------------------------------------------- Marie-Galante en papier

        /**
         * Les ailes du moulin, peintes à l'arrêt : la vignette et le lieu à
         * plat seulement. Le lieu en plans les tait et [Motif] les fait
         * tourner entre le plan du milieu et celui de devant.
         */
        fun ailesDuMoulin(angle: Float) {
            piece(ailesMoulin(angle), couleurAiles(P), 0.6f)
            piece(disque(MOULIN[0], MOULIN[1], 1.1f), couleurMoyeu(P), 0.4f)
        }

        /**
         * Marie-Galante en papier découpé, d'après la photo du propriétaire
         * (2026-11-07) : le moulin à vent de pierre, son toit conique de
         * bardeaux, ses quatre ailes à claire-voie et la queue qui l'oriente,
         * le petit abri de bois et son rôle, un bougainvillier, un manguier
         * au bord gauche, la pelouse. Les palmiers de la photo ne sont pas
         * repris (pas de cocotier ni de silhouette qui y ressemble).
         */
        fun marieGalantePapier() {
            cumulus(-4f, 30f, 24f, 1600)
            cumulus(40f, 16f, 14f, 1601)
            val lisiere = { x: Float -> 49f + sin(x * 0.21f) * 1.2f + sin(x * 0.07f) * 1.6f }
            val l = sousLaCrete(-2f, 102f, lisiere)
            l.op(bosses(-2f, 102f, lisiere, 1.6f, 2.6f, Alea(1602)), Path.Op.UNION)
            piece(l, papier(0xFF3E7A3A.toInt()))
            // À gauche du mot, pour que le moulin reste seul à droite
            // (propriétaire, 2026-11-09) : la sucrerie est dessinée à droite,
            // puis réduite et ramenée entre le manguier et le mot.
            tasser(86f, 53f, 0.9f, 0.9f, dx = -52f) { habitationMurat() }
            plan(1)
            // La pelouse du fond, la cheminée ronde de Murat, le bougainvillier.
            pieceD("M-2 54Q40 52.6 102 53.4L102 68L-2 68Z", papier(0xFF62A83E.toInt()), 0.4f)
            chemineeRonde()
            val rose = papier(0xFFD0407A.toInt())
            val bougain = Path()
            val rb = Alea(1603)
            // Ses fleurs s'envolent quand on secoue le téléphone : voir [BOUGAINVILLIER].
            repeat(14) { bougain.op(disque(8f + rb() * 14f, 51f + rb() * 4f, 1.4f + rb() * 1.6f), Path.Op.UNION) }
            piece(bougain, papier(0xFF3E7A34.toInt()))
            repeat(16) { piece(disque(8f + rb() * 14f, 50f + rb() * 4.5f, 0.5f + rb() * 0.7f), rose, 0f) }
            // Le moulin : la tour de pierre, la porte, la queue, le toit. Réduit
            // et poussé vers le bord pour que les ailes tiennent sous l'arche
            // des cartes rares et que le mot ne cache pas la tour ; [MOULIN]
            // donne le moyeu et les ailes déjà réduits.
            tasser(86f, 57f, MOULIN_REDUCTION, MOULIN_REDUCTION, 1.4f) {
                val pierre = papier(0xFFB8AE9C.toInt())
                pieceD("M77 57L80.6 29.4L91.4 29.4L95 57Z", pierre)
                val rp = Alea(1604)
                val moellons = intArrayOf(papier(0xFF9C9282.toInt()), papier(0xFFCFC6B4.toInt()), papier(0xFF857B6C.toInt()))
                repeat(46) {
                    val y = 30.5f + rp() * 26f
                    val demi = 5.4f + (y - 29.4f) / 27.6f * 3.6f
                    val x = 86f - demi + 0.6f + rp() * (2f * demi - 2.4f)
                    val w = 0.8f + rp() * 1.2f
                    pieceD("M$x ${y}L${x + w} ${y - 0.1f}L${x + w + 0.1f} ${y + 0.6f}L${x - 0.1f} ${y + 0.65f}Z", moellons[(rp() * 3f).toInt().coerceAtMost(2)], 0f)
                }
                pieceD("M91.4 29.4L95 57L92 57L89.6 29.4Z", papier(0xFF9A907E.toInt()), 0.2f)
                pieceD("M83 57L83 50.6Q84.6 49.4 86.2 50.6L86.2 57Z", papier(0xFF3A3028.toInt()), 0.3f)
                val queue = papier(0xFF9A8A70.toInt())
                barre(86.6f, 25f, 78.4f, 56.4f, 0.9f, queue, 0.6f)
                barre(88.4f, 25.6f, 81f, 56.4f, 0.5f, queue, 0.4f)
                for (k in 1 until 10) {
                    val u = k / 10f
                    barre(86.6f + (78.4f - 86.6f) * u, 25f + 31.4f * u, 88.4f + (81f - 88.4f) * u, 25.6f + 30.8f * u, 0.25f, queue, 0f)
                }
                pieceD("M79.6 30Q82 22 86 18.8Q90 22 92.4 30Z", papier(0xFF7E7A72.toInt()), 0.8f)
                for (k in 1 until 4) barre(80.4f + k * 0.9f, 30f - k * 2.6f, 91.6f - k * 0.9f, 30f - k * 2.6f, 0.14f, papier(0xFF64605A.toInt()), 0f)
                pieceD("M85.6 19L86.4 19L86.2 17.2L85.8 17.2Z", papier(0xFF5A4636.toInt()), 0f)
            }
            if (faune) ailesDuMoulin(ANGLE_MOULIN)
            plan(2)
            // Le manguier au bord gauche : tronc court et épais qui se divise
            // tôt, grand dôme plus large que haut, d'un vert sombre et luisant,
            // des pousses bronze, quelques mangues qui pendent sous le feuillage.
            // Réduit et poussé contre le bord, pour laisser l'Habitation Murat
            // entre lui et le mot ; [BETES] place les mangues en conséquence.
            tasser(4f, 58.6f, MANGUIER_REDUCTION, MANGUIER_REDUCTION, MANGUIER_DECALAGE) {
                val ecorce = papier(0xFF4E3C2E.toInt())
                pieceD("M1 58.6Q3 50 3.4 44Q1.8 40 -2 37L0.4 35.6Q3.8 38.6 5.2 41.6Q7 37.6 11 35L12.4 36.6Q8.4 40 7.6 44Q8 51 10.4 58.6Z", ecorce, 0.7f)
                val dome = Path()
                val rm = Alea(1605)
                val bord = { x: Float -> 9f + ((x - 4f) / 15f).let { it * it } * 14f }
                repeat(22) {
                    val x = -9f + rm() * 28f
                    val y = bord(x) + 2f + rm() * (33f - bord(x)).coerceAtLeast(1f)
                    dome.op(disque(x, y, 3.4f + rm() * 2.6f), Path.Op.UNION)
                }
                dome.op(chemin("M-10 38Q4 41 20 37L20 34L-10 34Z"), Path.Op.UNION)
                piece(dome, papier(0xFF1F4A22.toInt()))
                val lumiere = Path()
                repeat(9) { lumiere.op(disque(-4f + rm() * 18f, bord(4f) + 1.5f + rm() * 9f, 2f + rm() * 1.8f), Path.Op.UNION) }
                piece(lumiere, papier(0xFF2F6630.toInt()), 0.3f)
                // Les feuilles en lance au bord du dôme, et quelques pousses bronze.
                val feuilles = intArrayOf(papier(0xFF1F4A22.toInt()), papier(0xFF2A5E2A.toInt()), papier(0xFF16381A.toInt()))
                repeat(26) {
                    val a = rm() * PI.toFloat()
                    val x = 4f + cos(a) * 15f * (0.9f + rm() * 0.15f)
                    val y = 23f - sin(a) * 14f * (0.9f + rm() * 0.15f)
                    feuillePliee(x, y, 2.6f + rm() * 1.2f, -Math.toDegrees(a.toDouble()).toFloat() + (rm() - 0.5f) * 50f,
                        feuilles[(rm() * 3f).toInt().coerceAtMost(2)], 0.26f)
                }
                repeat(5) {
                    val x = -2f + rm() * 18f; val y = bord(x) + 0.4f
                    for (k in 0 until 3) feuillePliee(x, y, 2.2f, -110f + k * 25f, papier(0xFFA0522D.toInt()), 0.25f)
                }
            }
            // Les mangues qui pendent sont dans [BETES] : elles tombent quand on secoue le téléphone.
            pieceD("M-2 58Q50 56 102 57.4L102 68L-2 68Z", papier(0xFF74B842.toInt()), 0.4f)
            for (k in 0 until 4) {
                val y = 59.5f + k * 1.8f
                pieceD("M-2 ${y}Q50 ${y - 1f} 102 ${y + 0.2f}L102 ${y + 0.7f}Q50 ${y - 0.3f} -2 ${y + 0.7f}Z", papier(0xFF8CC852.toInt()), 0f)
            }
            pieceD("M-2 58.4Q8 57.6 20 59Q12 60.6 -2 61Z", papier(0xFF4E8E34.toInt()), 0f)
        }

        // Une baie en plein cintre, ouverte sur le ciel : la ruine n'a plus de toit.
        private fun baie(x: Float, y0: Float, y1: Float, w: Float, ciel: Int, tableau: Int) {
            val h = w / 2f
            pieceD("M${x - h - 0.25f} ${y1}L${x - h - 0.25f} ${y0 + h}Q${x - h - 0.25f} ${y0 - 0.25f} $x ${y0 - 0.25f}" +
                "Q${x + h + 0.25f} ${y0 - 0.25f} ${x + h + 0.25f} ${y0 + h}L${x + h + 0.25f} ${y1}Z", tableau, 0f)
            pieceD("M${x - h} ${y1}L${x - h} ${y0 + h}Q${x - h} $y0 $x ${y0}Q${x + h} $y0 ${x + h} ${y0 + h}L${x + h} ${y1}Z", ciel, 0f)
        }

        /**
         * Les ruines de l'Habitation Murat, à Grand-Bourg, d'après la photo
         * du propriétaire (2026-11-09) : la sucrerie sans toit, son pignon
         * de pierre grise percé de baies en plein cintre qui ouvrent sur le
         * ciel, son aile basse de pierre claire, et sa haute cheminée carrée.
         * Au loin, à gauche du mot, l'aile filant derrière lui. Ni les
         * cocotiers de la photo, ni sa tour de moulin : le moulin de la carte
         * en tient lieu.
         */
        private fun habitationMurat() {
            val ciel = mix(P.cielBas, P.cielHaut, 0.35f)
            val tableau = papier(0xFF4E4A42.toInt())
            val gris = papier(0xFF8E887A.toInt())
            val clair = papier(0xFFC9BFA2.toInt())
            val rp = Alea(1606)
            // L'aile basse, de pierre claire, qui file vers la droite.
            pieceD("M86 53L86 40.2L89 40L89.6 39.6L94 39.8L95 40.4L102 40.2L102 53Z", clair, 0.5f)
            for (bx in floatArrayOf(89.5f, 93.5f, 97.5f)) {
                baie(bx, 42.4f, 45.6f, 1.5f, ciel, tableau)
                baie(bx, 47.6f, 51.6f, 1.6f, ciel, tableau)
            }
            // Le corps principal et son pignon, de pierre grise.
            pieceD("M72.6 53L72.6 37.4L79.8 32.8L87 37.4L87 53Z", gris, 0.6f)
            val moellons = intArrayOf(papier(0xFF7A7468.toInt()), papier(0xFFA49C8C.toInt()))
            repeat(30) {
                val x = 73.4f + rp() * 12.4f
                val y = 38.4f + rp() * 13.6f
                pieceD("M$x ${y}L${x + 1f} ${y}L${x + 1f} ${y + 0.5f}L$x ${y + 0.5f}Z", moellons[(rp() * 2f).toInt().coerceAtMost(1)], 0f)
            }
            baie(79.8f, 35f, 37f, 1.1f, ciel, tableau)
            for (bx in floatArrayOf(75.4f, 79.8f, 84.2f)) baie(bx, 38.8f, 42.2f, 1.5f, ciel, tableau)
            baie(75.4f, 45f, 49.6f, 1.7f, ciel, tableau)
            baie(79.8f, 44.4f, 52.6f, 2.2f, ciel, tableau)
            baie(84.2f, 45f, 49.6f, 1.7f, ciel, tableau)
            // La haute cheminée carrée, au bout de la sucrerie, sa face à l'ombre et son chapeau.
            pieceD("M69.6 53L70.2 24L72.4 24L73 53Z", clair, 0.6f)
            pieceD("M71.8 24L72.4 24L73 53L72.2 53Z", papier(0xFF9C9480.toInt()), 0f)
            pieceD("M69.8 24.2L72.8 24.2L72.8 23.2L69.8 23.2Z", papier(0xFF9C9480.toInt()), 0.3f)
            for (k in 0 until 8) {
                val y = 26f + k * 3.4f
                pieceD("M${70.2f - k * 0.06f} ${y}L${71.4f} ${y}L${71.4f} ${y + 0.3f}L${70.2f - k * 0.06f} ${y + 0.3f}Z", moellons[k % 2], 0f)
            }
        }

        /**
         * La cheminée ronde de Murat, colonne de moellons seule dans la
         * pelouse : sur le plan du milieu, entre le manguier et le mot.
         */
        private fun chemineeRonde() {
            val pierre = papier(0xFFCFC6B0.toInt())
            pieceD("M26 56L26.4 33.6L29.2 33.6L29.6 56Z", pierre, 0.6f)
            pieceD("M28.4 33.6L29.2 33.6L29.6 56L28.6 56Z", papier(0xFFA89E88.toInt()), 0f)
            val joints = intArrayOf(papier(0xFF9C9282.toInt()), papier(0xFFB4AA96.toInt()))
            for (k in 0 until 17) {
                val y = 34.8f + k * 1.25f
                val d = if (k % 2 == 0) 0f else 0.7f
                pieceD("M26.3 ${y}L29.4 ${y}L29.4 ${y + 0.18f}L26.3 ${y + 0.18f}Z", joints[k % 2], 0f)
                pieceD("M${27.2f + d} ${y - 1.05f}L${27.36f + d} ${y - 1.05f}L${27.36f + d} ${y}L${27.2f + d} ${y}Z", joints[0], 0f)
            }
            // Le sommet ébréché, et la touffe d'herbe qui y a poussé.
            pieceD("M26.4 33.8L26.6 33L27.4 33.4L28.2 32.8L29.2 33.4L29.2 33.8Z", pierre, 0.3f)
            for (k in 0 until 4) feuillePliee(27.8f, 33.2f, 1.6f, -150f + k * 40f, papier(0xFF5E8E3A.toInt()), 0.3f)
        }

        /** Marie-Galante : la plaine de canne, le moulin de pierre et ses ailes. */
        fun marieGalante() {
            if (PAPIER) { marieGalantePapier(); return }
            val r = Alea(171)
            crete(39f, 0.5f, 172, P.loin)
            plan(1)
            rect(0f, 40f, 100f, 26f, P.milieu)
            bosquet(-2f, 30f, 41f, 2f, 173, mix(P.milieu, P.pres, 0.4f))
            forme("M60 42L61.4 22L67.6 22L69 42Z", P.pierre)
            forme("M65 22L67.6 22L69 42L66 42Z", mix(P.pierre, P.pres, 0.3f), 0.6f)
            triangle(60.8f, 22.2f, 64.5f, 17.6f, 68.2f, 22.2f, P.toit)
            rect(63.6f, 36.5f, 1.8f, 5.5f, mix(P.pres, 0xFF000000.toInt(), 0.3f))
            val aile = mix(P.sol, P.pierre, 0.3f)
            for (k in 0 until 4) {
                val a = PI.toFloat() / 4f + k * PI.toFloat() / 2f
                val ux = cos(a); val uy = sin(a)
                val nx = -uy * 1.8f; val ny = ux * 1.8f
                ligne(64.5f, 24f, 64.5f + ux * 11f, 24f + uy * 11f, aile, 0.5f)
                ligne(64.5f + ux * 3f + nx, 24f + uy * 3f + ny, 64.5f + ux * 11f + nx, 24f + uy * 11f + ny, aile, 0.3f)
                for (t in 1..4) {
                    val f = 3f + t * 2f
                    ligne(64.5f + ux * f, 24f + uy * f, 64.5f + ux * f + nx, 24f + uy * f + ny, aile, 0.25f)
                }
            }
            rond(64.5f, 24f, 0.8f, aile)
            plan(2)
            val canne = mix(P.pres, 0xFF8FA84A.toInt(), if (P.nuit) 0.1f else 0.3f)
            rect(0f, 56f, 100f, 10f, mix(canne, P.sol, 0.4f))
            var x = -1f
            while (x < 101f) {
                val h = 9f + r() * 6f
                val y = 66f - h
                trait("M$x 66L${x + 0.4f} $y", canne, 0.5f)
                trait("M${x + 0.4f} ${y + 1.5f}Q${x + 2.4f} $y ${x + 3.4f} ${y + 2.4f}", canne, 0.4f)
                trait("M${x + 0.3f} ${y + 3f}Q${x - 1.6f} ${y + 1.8f} ${x - 2.6f} ${y + 4.4f}", canne, 0.4f)
                x += 1.5f + r() * 0.6f
            }
        }

        // ------------------------------------------- la Désirade en papier

        /**
         * La Désirade en papier découpé, d'après la photo du propriétaire
         * (2026-11-07) : le phare de la pointe Doublé, sa tour blanche, sa
         * lanterne rouge et l'échelle qui y monte, à droite pour rester à côté
         * du dessin du mot ; le morne de broussaille et de blocs bruns sur
         * lequel il est posé, la mer de l'Atlantique à gauche et son ressac,
         * le plateau de terre sèche semé de pierres et de têtes-à-l'anglais
         * devant (photo du propriétaire, 2026-11-09), et l'iguane des
         * Petites Antilles qui s'y chauffe ([BETES]). Le ressac bouge sur la
         * carte ouverte, et le soir et la nuit le faisceau du phare tourne
         * (`Ressac` dans [Ambiance]).
         */
        fun desiradePapier() {
            cumulus(-6f, 16f, 30f, 1700)
            cumulus(40f, 9f, 16f, 1701)
            // La mer, du large au rivage, et le ressac qui blanchit au bord.
            val (rx0, rx1, large, rivage) = RESSAC_DESIRADE
            pieceD("M-2 $large L102 $large L102 50L-2 50Z", papier(0xFF2C6FA6.toInt()), 0.2f)
            lanieresMer(large + 0.4f, rivage + 1f, 1702, intArrayOf(papier(0xFF2A78B8.toInt()), papier(0xFF1E5A94.toInt()),
                papier(0xFF3A8CC4.toInt()), papier(0xFF4C9CC8.toInt())))
            val re = Alea(1703)
            for (k in 0 until 4) {
                val y = large + 3f + k * 1.4f
                var x = rx0 + re() * 6f
                while (x < rx1 - 4f) {
                    val l = 3f + re() * 7f
                    pieceD("M$x ${y + 0.3f}Q${x + l / 2f} ${y - 0.5f} ${x + l} ${y + 0.3f}L${x + l} ${y + 0.7f}Q${x + l / 2f} ${y + 0.1f} $x ${y + 0.7f}Z",
                        papier(0xFFF2F6F4.toInt()), 0f)
                    x += l + 3f + re() * 7f
                }
            }
            plan(1)
            // Le morne qui monte vers la droite, sa broussaille, ses blocs, le phare.
            val crete = brisee(-2f, rivage + 0.6f, 24f, rivage, 42f, 42.6f, 54f, 40.6f, 62f, 37.6f, 70f, 35.2f, 78f, 34f,
                86f, 33.6f, 96f, 33.8f, 102f, 34.2f)
            // L'écume au pied du morne : une bande continue et bosselée, jamais
            // un rang de perles.
            val ecume = chemin("M-2 ${crete(-2f) + 0.6f}" + (0..52 step 2).joinToString("") { x -> "L$x ${crete(x.toFloat()) + 0.6f}" } +
                (52 downTo -2 step 2).joinToString("") { x -> "L$x ${crete(x.toFloat()) - 0.5f - (if (x % 4 == 0) 0.3f else 0f)}" } + "Z")
            ecume.op(bosses(-2f, 50f, { x -> crete(x) - 0.3f }, 0.4f, 0.8f, Alea(1711)), Path.Op.UNION)
            piece(ecume, papier(0xFFF6F8F6.toInt()), 0.3f)
            val morne = sousLaCrete(-2f, 102f, crete)
            morne.op(bosses(46f, 103f, { x -> crete(x) + 0.4f }, 0.8f, 1.5f, Alea(1704)), Path.Op.UNION)
            piece(morne, papier(0xFFA88A62.toInt()))
            val herbe = brisee(40f, 44f, 54f, 41.6f, 62f, 38.8f, 70f, 36.4f, 78f, 35.2f, 102f, 35.4f)
            pieceD("M44 ${crete(44f) + 0.6f}" + (46..102 step 2).joinToString("") { x -> "L$x ${crete(x.toFloat()) + 0.5f}" } +
                (102 downTo 44 step 2).joinToString("") { x -> "L$x ${herbe(x.toFloat()) + 2.2f + (x % 6) * 0.25f}" } + "Z",
                papier(0xFF8C9A4A.toInt()), 0.3f)
            taches(46f, 102f, 41f, 16, 1705, papier(0xFF6E8A34.toInt()), crete)
            taches(54f, 102f, 40f, 9, 1706, papier(0xFF4E6E2A.toInt()), crete)
            for ((gx, gy, gr) in arrayOf(Triple(60f, 39.6f, 1.2f), Triple(66f, 37.8f, 1.6f), Triple(73.4f, 37.2f, 1.1f),
                    Triple(80f, 35.8f, 1.8f), Triple(95.6f, 35.4f, 1.5f), Triple(50f, 42.4f, 0.9f), Triple(32f, 44.6f, 0.8f)))
                galet(gx, gy, gr, papier(0xFF8E5E44.toInt()))
            teteALAnglais(70f, 37.6f, 1f)
            teteALAnglais(84.4f, 35.6f, 0.85f)
            // Le phare : la tour blanche et son ombre, l'échelle sur sa face
            // gauche, la porte rouge, la lanterne rouge, sa vitre et l'antenne.
            // Il tient sous le plein cintre des cartes rares (voir
            // CadreOrne.cheminFenetre) : plus haut, l'arche coupe la lanterne.
            val blancTour = papier(0xFFF2F0EA.toInt())
            val rouge = papier(0xFFD8332C.toInt())
            val (px, py) = PHARE_DESIRADE
            pieceD("M92.6 34.4L92.6 31.4L95.4 31.4L95.4 34.6Z", papier(0xFFDAD6CC.toInt()), 0.5f)
            pieceD("M${px - 2.1f} 34.6L${px - 1.8f} 21L${px + 1.8f} 21L${px + 2.1f} 34.6Z", blancTour, 0.8f)
            pieceD("M${px + 0.8f} 34.6L${px + 0.7f} 21L${px + 1.8f} 21L${px + 2.1f} 34.6Z", papier(0xFFCFCBC2.toInt()), 0f)
            val fer = papier(0xFF4A4C50.toInt())
            barre(px - 1.4f, 32.4f, px - 1.3f, 21.4f, 0.2f, fer, 0f)
            barre(px - 0.4f, 32.4f, px - 0.4f, 21.4f, 0.2f, fer, 0f)
            var ey = 31.8f
            while (ey > 21.8f) { barre(px - 1.4f, ey, px - 0.4f, ey, 0.14f, fer, 0f); ey -= 0.9f }
            pieceD("M${px + 0.6f} 34.6L${px + 0.6f} 32.6L${px + 1.6f} 32.6L${px + 1.6f} 34.6Z", rouge, 0.2f)
            pieceD("M${px - 3f} 21.2L${px - 3f} 20.4L${px + 3f} 20.4L${px + 3f} 21.2Z", papier(0xFFB82A24.toInt()), 0.3f)
            pieceD("M${px - 2.4f} 20.4L${px - 2.4f} ${py + 0.9f}L${px + 2.4f} ${py + 0.9f}L${px + 2.4f} 20.4Z", rouge, 0.6f)
            val vitre = if (P.allume) duJour(0xFFFFE08A.toInt()) else papier(0xFF3C4450.toInt())
            pieceD("M${px - 1.6f} ${py + 0.9f}L${px - 1.4f} ${py - 0.9f}L${px + 1.4f} ${py - 0.9f}L${px + 1.6f} ${py + 0.9f}Z", vitre, 0.3f)
            pieceD("M${px - 1.8f} ${py - 0.7f}Q$px ${py - 2.6f} ${px + 1.8f} ${py - 0.7f}Z", papier(0xFFE8E6E0.toInt()), 0.3f)
            barre(px, py - 1.6f, px, py - 3.6f, 0.2f, fer, 0f)
            plan(2)
            // Le plateau de terre sèche, ses pierres claires, ses touffes.
            val sol = brisee(-2f, 50.6f, 18f, 49.4f, 36f, 50f, 56f, 49f, 76f, 48.4f, 102f, 47.4f)
            val plateau = sousLaCrete(-2f, 102f, sol)
            plateau.op(bosses(-2f, 102f, { x -> sol(x) + 0.3f }, 0.5f, 1f, Alea(1707)), Path.Op.UNION)
            piece(plateau, papier(0xFFB98E68.toInt()))
            pieceD("M-2 56Q30 53.6 60 55Q80 55.8 102 54L102 68L-2 68Z", papier(0xFFA87652.toInt()), 0.4f)
            pieceD("M-2 61.6Q36 59.8 66 61.2Q86 61.8 102 60.4L102 68L-2 68Z", papier(0xFF96643F.toInt()), 0.4f)
            val rp = Alea(1708)
            repeat(26) {
                val x = -1f + rp() * 102f
                val y = sol(x) + 1.6f + rp() * (66f - sol(x) - 1.6f)
                val r = (0.3f + rp() * 0.5f) * (0.6f + (y - 48f) / 18f)
                galet(x, y, r, papier(if (rp() < 0.7f) 0xFFDCCDB4.toInt() else 0xFF9C8C78.toInt()))
            }
            taches(-2f, 102f, 54f, 7, 1709, papier(0xFF7A8A3A.toInt()), sol)
            for ((tx, ty) in arrayOf(30f to 51.4f, 4f to 52.6f, 90f to 50.6f)) {
                val t = Path()
                val rt = Alea(1710 + tx.toInt())
                repeat(5) { t.op(disque(tx + (rt() - 0.5f) * 4f, ty + (rt() - 0.5f) * 0.8f, 0.9f + rt() * 0.8f), Path.Op.UNION) }
                piece(t, papier(0xFF5E7A2E.toInt()), 0.5f)
            }
            // Les têtes-à-l'anglais du plateau, hors du dessin du mot.
            teteALAnglais(24.6f, 53.6f, 1.6f)
            teteALAnglais(93.6f, 54f, 2.4f)
            teteALAnglais(82.6f, 59f, 3.3f)
        }

        /**
         * Une tête-à-l'anglais (Melocactus), d'après la photo du propriétaire
         * (2026-11-09) : le globe vert à côtes, ses épines claires, et la
         * coiffe de laine brun rouge qui le surmonte, blanche au sommet, où
         * pointent de petites fleurs roses. Posée en ([x], [y]), le globe de
         * rayon [r].
         */
        private fun teteALAnglais(x: Float, y: Float, r: Float) {
            val cy = y - 0.8f * r
            pieceD("M${x - r} ${cy}Q${x - r} ${y + 0.05f * r} $x ${y + 0.05f * r}Q${x + r} ${y + 0.05f * r} ${x + r} ${cy}" +
                "Q${x + r} ${y - 1.65f * r} $x ${y - 1.65f * r}Q${x - r} ${y - 1.65f * r} ${x - r} ${cy}Z", papier(0xFF3E6A2A.toInt()), 0.5f)
            // Les côtes, larges et claires, séparées par le creux sombre du
            // globe, qui suivent son galbe ; une arête d'épines blondes au milieu.
            for (k in -3..3) {
                val u = k / 3.5f
                val bas = x + u * r * 0.42f
                val ventre = x + u * r * 0.98f
                val haut = x + u * r * 0.48f
                val l = 0.27f * r * (1f - abs(u) * 0.55f)
                pieceD("M$bas ${y - 0.05f * r}Q${ventre - l} $cy $haut ${y - 1.55f * r}Q${ventre + l} $cy $bas ${y - 0.05f * r}Z",
                    papier(if (k % 2 == 0) 0xFF9CC050.toInt() else 0xFF7EAA40.toInt()), 0f)
                pieceD("M$bas ${y - 0.1f * r}Q$ventre $cy $haut ${y - 1.5f * r}Q${ventre + 0.03f * r} $cy $bas ${y - 0.1f * r}Z",
                    papier(0xFFE6D8A0.toInt()), 0f)
            }
            // La coiffe de laine, ses soies sombres, son sommet blanc et ses fleurs.
            val w = 0.52f * r
            pieceD("M${x - w} ${y - 1.45f * r}L${x - w * 0.92f} ${y - 2.7f * r}Q$x ${y - 2.95f * r} ${x + w * 0.92f} ${y - 2.7f * r}" +
                "L${x + w} ${y - 1.45f * r}Q$x ${y - 1.3f * r} ${x - w} ${y - 1.45f * r}Z", papier(0xFF8A3A2A.toInt()), 0.5f)
            for (k in -2..2) {
                val sx = x + k * w * 0.36f
                pieceD("M${sx - 0.04f * r} ${y - 1.5f * r}L${sx + 0.04f * r} ${y - 1.5f * r}L${sx + 0.03f * r} ${y - 2.65f * r}" +
                    "L${sx - 0.03f * r} ${y - 2.65f * r}Z", papier(0xFF5E2418.toInt()), 0f)
            }
            pieceD("M${x - w * 0.9f} ${y - 2.66f * r}Q$x ${y - 3.15f * r} ${x + w * 0.9f} ${y - 2.66f * r}Q$x ${y - 2.5f * r} ${x - w * 0.9f} ${y - 2.66f * r}Z",
                papier(0xFFEDE4DC.toInt()), 0.2f)
            piece(disque(x - w * 0.45f, y - 2.82f * r, 0.13f * r + 0.06f), papier(0xFFE0408A.toInt()), 0f)
            piece(disque(x + w * 0.35f, y - 2.86f * r, 0.11f * r + 0.06f), papier(0xFFE0408A.toInt()), 0f)
        }

        /** La Désirade vue de la côte : la longue île en table, un gommier tiré sur le sable. */
        fun desirade() {
            if (PAPIER) { desiradePapier(); return }
            val r = Alea(181)
            plan(1)
            eau(40f, 182)
            forme("M6 40.2L12 33.4Q16 30.2 28 29.8L80 29.4Q87 29.8 91 34L95 40.2Z", P.milieu)
            val face = mix(P.milieu, P.pierre, 0.35f)
            var x = 14f
            while (x < 88f) {
                trait("M$x 33L${x - 0.6f} 39.6", face, 0.4f, 0.7f)
                x += 3.5f + r() * 2.5f
            }
            rect(9f, 39.6f, 84f, 0.6f, calcaire(), 0.9f)
            plan(2)
            forme("M0 66L0 55Q30 51.5 60 54.5Q80 56.5 100 53.5L100 66Z", sable())
            trait("M0 55Q30 51.5 60 54.5Q80 56.5 100 53.5", mix(P.eau, 0xFFFFFFFF.toInt(), 0.6f), 0.5f, 0.8f)
            forme("M30 58.5Q40 61.6 52 58.2L50.6 60.6Q40 63 31.6 60.6Z", duJour(0xFFE2C33A.toInt()))
            forme("M31.6 60.6Q40 63 50.6 60.6L50.2 61.3Q40 63.6 32 61.3Z", duJour(0xFF2F8F5A.toInt()))
            bosquet(80f, 102f, 56f, 3f, 183, P.pres)
        }

        // ------------------------------------------- Bouillante en papier

        /**
         * Bouillante en papier découpé, d'après la photo du propriétaire
         * (2026-11-07), l'anse de Malendure : la pointe basse et boisée à
         * gauche et ses maisons aux toits rouges, le grand morne couvert de
         * forêt à droite, la plage de sable sombre et ses kayaks à son pied,
         * la mer calme, le long ponton et le bateau de plongée amarré au bout,
         * à gauche du mot ; devant, les roches et un flamboyant en fleurs qui
         * entre par le bord droit. Les cocotiers de la plage deviennent des
         * houppiers ronds, et personne sur le ponton. L'eau scintille sur la
         * carte ouverte (`Reflets` dans [Ambiance]), la tortue y nage.
         */
        fun bouillantePapier() {
            cumulus(-8f, 30f, 34f, 1800)
            cumulus(20f, 27f, 22f, 1801)
            cumulus(56f, 10f, 16f, 1802)
            // La mer jusqu'au large, et la pointe basse de gauche, ses maisons.
            pieceD("M-2 35.6L102 35.6L102 68L-2 68Z", papier(0xFF6E9CB4.toInt()), 0f)
            val pointe = brisee(-2f, 36f, 3f, 35.2f, 10f, 32.4f, 20f, 30.6f, 32f, 29f, 42f, 27.6f, 52f, 27f, 60f, 28f)
            val p0 = sousLaCrete(-2f, 60f, pointe)
            p0.op(bosses(4f, 60f, { x -> pointe(x) + 0.4f }, 0.9f, 1.6f, Alea(1803)), Path.Op.UNION)
            piece(p0, papier(0xFF4C7E3A.toInt()))
            taches(6f, 58f, 35f, 12, 1804, papier(0xFF3A6A2E.toInt()), pointe)
            val murs = papier(0xFFF0E8DA.toInt())
            val tuiles = papier(0xFFB4483A.toInt())
            for ((hx, hy) in arrayOf(24f to 31.4f, 29f to 30.4f, 34f to 31.2f, 38.6f to 29.6f, 43f to 30.6f, 47f to 29.4f, 40f to 32.6f)) {
                pieceD("M$hx ${hy}L${hx + 2f} ${hy}L${hx + 2f} ${hy + 1.2f}L$hx ${hy + 1.2f}Z", murs, 0.2f)
                pieceD("M${hx - 0.3f} ${hy}L${hx + 1f} ${hy - 0.8f}L${hx + 2.3f} ${hy}Z", tuiles, 0f)
            }
            plan(1)
            // Le grand morne de droite, sa forêt, la plage sombre à son pied.
            val morne = brisee(44f, 40f, 50f, 30f, 58f, 24f, 66f, 19.6f, 76f, 15.6f, 86f, 13.4f, 96f, 12f, 102f, 11.6f)
            val m = sousLaCrete(44f, 102f, morne)
            m.op(bosses(46f, 103f, { x -> morne(x) + 0.5f }, 1.2f, 2.2f, Alea(1805)), Path.Op.UNION)
            piece(m, papier(0xFF2E6A2A.toInt()))
            // La forêt du morne en lisières de houppiers, de plus en plus
            // claires vers le pied : des lignes de niveau, pas des pois.
            for ((k, col) in listOf(0xFF3A7A30, 0xFF468A36, 0xFF2A6226).withIndex()) {
                val dy = 5f + k * 6.5f
                val lis = { x: Float -> morne(x) + dy + sin(x * 0.4f + k) * 0.8f }
                val bande = sousLaCrete(48f + k * 4f, 102f, lis)
                bande.op(bosses(48f + k * 4f, 103f, lis, 1f, 1.8f, Alea(1806 + k)), Path.Op.UNION)
                piece(bande, papier(col.toInt()), 0.5f)
            }
            for ((ax, ay, ar) in arrayOf(Triple(60f, 41.4f, 2f), Triple(66f, 42.2f, 2.4f), Triple(73f, 43.2f, 2.2f), Triple(80f, 44f, 2.6f)))
                arbreRond(ax, ay, ar, papier(0xFF3E8A36.toInt()), papier(0xFF5A4636.toInt()))
            pieceD("M44 40.2Q60 40.4 72 42.4Q84 44.4 102 45.6L102 47.2Q84 46.4 72 44.6Q58 42.2 44 41.6Z", papier(0xFF6A5C50.toInt()), 0.4f)
            for ((kx, ky, kc) in arrayOf(Triple(62f, 42f, 0xFFE8742E), Triple(66.4f, 42.6f, 0xFFD8332C), Triple(70.6f, 43.4f, 0xFFF0C030), Triple(75f, 44.2f, 0xFFE8742E)))
                pieceD("M$kx ${ky}Q${kx + 1.2f} ${ky - 0.4f} ${kx + 2.4f} ${ky}Q${kx + 1.2f} ${ky + 0.3f} $kx ${ky}Z", papier(kc.toInt()), 0.2f)
            pieceD("M64.4 41.6L65 40L67.6 40L68.2 41.6Z", papier(0xFFF4F1EA.toInt()), 0.3f)
            pieceD("M68.8 42.2L69 40.8L72.2 40.8L72.6 42.2Z", papier(0xFFE07890.toInt()), 0.3f)
            // L'eau de l'anse, calme, en lanières claires.
            val gris = intArrayOf(papier(0xFF7FA8BE.toInt()), papier(0xFF6890A8.toInt()), papier(0xFF94B6C6.toInt()), papier(0xFF5E86A0.toInt()))
            pieceD("M-2 47.6L102 47.6L102 68L-2 68Z", papier(0xFF6A94AC.toInt()), 0f)
            pieceD("M44 41.6Q58 42.2 72 44.6Q84 46.4 102 47.2L102 48L-2 48L-2 41.6Z", papier(0xFF7AA4BA.toInt()), 0f)
            lanieresMer(48.6f, 64f, 1808, gris)
            // Le ponton, du bateau au rivage, ses pneus et ses pieds.
            val (j0, jy0, j1, jy1) = PONTON_BOUILLANTE
            val deck = papier(0xFF8E8678.toInt())
            val pied = papier(0xFF4A443C.toInt())
            var px = j0 + 1f
            while (px < j1) {
                val py = jy0 + (jy1 - jy0) * (px - j0) / (j1 - j0)
                barre(px, py, px, py + 1.4f, 0.3f, pied, 0f)
                if (px < 54f) piece(disque(px + 1.2f, py + 0.6f, 0.45f), papier(0xFF2A2622.toInt()), 0f)
                px += 2.6f
            }
            barre(j0, jy0, j1, jy1, 0.8f, deck, 0.5f)
            barre(j0, jy0 - 0.35f, j1, jy1 - 0.35f, 0.18f, papier(0xFFB4AC9C.toInt()), 0f)
            // Le bateau de plongée amarré au bout est dans [BETES] : il tangue sur la carte ouverte.
            pieceD("M37 44.2Q39 43.4 41.6 44.2Q39.4 44.9 37 44.2Z", papier(0xFFF0C030.toInt()), 0.2f)
            plan(2)
            // Les roches et l'herbe du bord, le flamboyant en fleurs à droite.
            val bord = brisee(66f, 66f, 72f, 61.4f, 80f, 59f, 90f, 57f, 102f, 55.6f)
            val roches = sousLaCrete(66f, 102f, bord)
            piece(roches, papier(0xFF3A3028.toInt()))
            for ((gx, gy, gr) in arrayOf(Triple(70f, 62.6f, 1.8f), Triple(74.4f, 60.6f, 2f), Triple(79f, 59.6f, 1.6f), Triple(83.6f, 59.2f, 2.2f), Triple(76f, 64f, 2.2f)))
                galet(gx, gy, gr, papier(0xFF6E5444.toInt()))
            pieceD("M84 66Q88 60.4 102 58.4L102 68L84 68Z", papier(0xFF5E9E3A.toInt()), 0.4f)
            // Le flamboyant : un tronc qui monte des roches et s'ouvre en
            // branches, un houppier en parasol, large, plat dessous et bombé
            // dessus, qui entre par le coin haut droit ; le dessous sombre et
            // festonné, le dessus semé de grappes rouges. Une secousse en fait
            // tomber des fleurs : voir [FLAMBOYANT_BOUILLANTE].
            flamboyantEnParasol(1809)
        }

        /** Bouillante : les îlets Pigeon, une tortue verte, le pavillon de plongée, la plage sombre qui fume. */
        fun bouillante() {
            if (PAPIER) { bouillantePapier(); return }
            plan(1)
            eau(36f, 191)
            forme("M0 66L0 22C10 22 22 28 32 38L36 66Z", P.milieu)
            bosquet(-2f, 26f, 25f, 2.8f, 192, mix(P.milieu, P.pres, 0.35f))
            val ilet = mix(P.milieu, P.pierre, 0.25f)
            forme("M58 36.2Q60 31 63.5 30.4Q67.5 31 69 36.2Z", ilet)
            forme("M72 36.2Q73.4 33.4 75.6 33.1Q78 33.4 79 36.2Z", ilet)
            bosquet(60f, 67f, 31.5f, 1.4f, 193, mix(P.milieu, P.pres, 0.35f))
            ligne(84f, 44f, 84f, 38f, P.sol, 0.3f)
            rect(84f, 38f, 3.6f, 2.6f, duJour(0xFFD6352E.toInt()))
            trait("M84 38L87.6 40.6", duJour(0xFFF4F1EA.toInt()), 0.5f)
            rond(84f, 44.4f, 0.9f, duJour(0xFFE87A2E.toInt()))
            plan(2)
            forme("M0 66L0 53Q40 49 70 52.6Q86 54.6 100 52.4L100 66Z", mix(P.sol, P.pierre, 0.25f))
            vapeur(40f, 52f, 10f)
            vapeur(46f, 52.5f, 8f)
        }

        // ------------------------------------------- Morne-à-l'Eau en papier

        /** Un damier noir de [cols] × [rangs] cases de [c] à partir de ([x], [y]), en une seule pièce. */
        private fun damier(x: Float, y: Float, cols: Int, rangs: Int, c: Float, col: Int) {
            val t = Path()
            for (i in 0 until cols) for (j in 0 until rangs) if ((i + j) % 2 == 0) t.addRect(x + i * c, y + j * c, x + (i + 1) * c, y + (j + 1) * c, Path.Direction.CW)
            piece(t, col, 0f)
        }

        /**
         * Un caveau en tombe-maison : le corps blanc et son flanc à l'ombre,
         * une bande de damier, la niche sombre, et selon sa sorte un pignon
         * à croix, un toit plat à balustrade, une façade toute en damier ou
         * une chapelle à fronton.
         */
        private fun caveau(k: Caveau) {
            val x = k.x; val y = k.sol; val l = k.l; val h = k.h
            // Un caveau sur quatre est crème ou ocre pâle, comme sur la photo.
            val teinte = when (((x * 7f + y * 3f).toInt() % 8 + 8) % 8) { 0 -> 0xFFEEDDB4.toInt(); 3 -> 0xFFE8D2A2.toInt(); else -> 0xFFF2EFE8.toInt() }
            val blanc = papier(teinte)
            val ombre = papier(mix(teinte, 0xFF8C8478.toInt(), 0.18f))
            val noir = papier(0xFF1E1E22.toInt())
            val cote = l / 8f
            pieceD("M$x ${y}L$x ${y - h}L${x + l} ${y - h}L${x + l} ${y}Z", blanc, 0.5f)
            pieceD("M${x + l * 0.8f} ${y}L${x + l * 0.8f} ${y - h}L${x + l} ${y - h}L${x + l} ${y}Z", ombre, 0f)
            when (k.sorte) {
                2 -> damier(x, y - h, 8, (h / cote).toInt().coerceAtLeast(1), cote, noir)
                else -> damier(x, y - h, 8, 2, cote, noir)
            }
            pieceD("M${x + l * 0.36f} ${y}L${x + l * 0.36f} ${y - h * 0.42f}L${x + l * 0.64f} ${y - h * 0.42f}L${x + l * 0.64f} ${y}Z",
                papier(0xFF2C2A2C.toInt()), 0f)
            val croix = papier(0xFFF6F4EE.toInt())
            when (k.sorte) {
                0 -> {
                    pieceD("M${x - 0.3f} ${y - h}L${x + l / 2f} ${y - h - h * 0.42f}L${x + l + 0.3f} ${y - h}Z", blanc, 0.4f)
                    pieceD("M${x + l * 0.1f} ${y - h - 0.2f}L${x + l / 2f} ${y - h - h * 0.34f}L${x + l * 0.9f} ${y - h - 0.2f}Z", noir, 0f)
                    barre(x + l / 2f, y - h - h * 0.4f, x + l / 2f, y - h - h * 0.4f - 2.2f, 0.32f, croix, 0.2f)
                    barre(x + l / 2f - 0.7f, y - h - h * 0.4f - 1.5f, x + l / 2f + 0.7f, y - h - h * 0.4f - 1.5f, 0.32f, croix, 0f)
                }
                CAVEAU_PLAT -> {
                    pieceD("M${x - 0.3f} ${y - h}L${x - 0.3f} ${y - h - 0.6f}L${x + l + 0.3f} ${y - h - 0.6f}L${x + l + 0.3f} ${y - h}Z", blanc, 0.3f)
                    var bx = x + 0.4f
                    while (bx < x + l - 0.2f) { barre(bx, y - h - 0.6f, bx, y - h - 1.5f, 0.2f, blanc, 0f); bx += 0.7f }
                    barre(x, y - h - 1.6f, x + l, y - h - 1.6f, 0.25f, blanc, 0f)
                }
                3 -> {
                    pieceD("M${x + l * 0.15f} ${y - h}L${x + l * 0.15f} ${y - h - 1.4f}Q${x + l / 2f} ${y - h - 3.4f} ${x + l * 0.85f} ${y - h - 1.4f}L${x + l * 0.85f} ${y - h}Z", blanc, 0.4f)
                    barre(x + l / 2f, y - h - 2.6f, x + l / 2f, y - h - 4.8f, 0.32f, croix, 0.2f)
                    barre(x + l / 2f - 0.7f, y - h - 4f, x + l / 2f + 0.7f, y - h - 4f, 0.32f, croix, 0f)
                }
                else -> pieceD("M${x - 0.2f} ${y - h}L${x - 0.2f} ${y - h - 0.5f}L${x + l + 0.2f} ${y - h - 0.5f}L${x + l + 0.2f} ${y - h}Z", noir, 0.2f)
            }
        }

        /** Une bougie posée en ([x], [y]) : sa cire, et sa flamme peinte quand l'heure les allume et que rien ne l'anime. */
        private fun bougie(x: Float, y: Float) {
            pieceD("M${x - 0.25f} ${y}L${x - 0.25f} ${y - 0.8f}L${x + 0.25f} ${y - 0.8f}L${x + 0.25f} ${y}Z", papier(0xFFF4EEDC.toInt()), 0f)
            if (P.allume && faune) {
                piece(disque(x, y - 1.6f, 1.6f), (0x50 shl 24) or 0xFFC060, 0f)
                pieceD("M$x ${y - 2.1f}Q${x + 0.4f} ${y - 1.2f} $x ${y - 0.9f}Q${x - 0.4f} ${y - 1.2f} $x ${y - 2.1f}Z", duJour(0xFFFFC23A.toInt()), 0f)
            }
        }

        /**
         * Le cimetière de Morne-à-l'Eau en papier découpé, d'après la photo
         * du propriétaire (2026-11-07) : les tombes-maisons blanches en damier
         * noir et blanc qui montent à flanc de morne vers la droite, rang
         * après rang, pignons et croix, toits plats à balustrade, chapelles ;
         * la forêt sombre en haut à gauche ; devant, les dalles des grands
         * caveaux. Le soir et la nuit, les bougies sont allumées sur les toits
         * et leur flamme vacille sur la carte ouverte (`Bougies` dans
         * [Ambiance]). Les palmiers de la photo ne sont pas repris.
         */
        fun morneALEauPapier() {
            // La forêt du morne en haut à gauche, en lisières de houppiers.
            val crete = brisee(-2f, 30f, 14f, 24f, 30f, 17f, 46f, 10f, 60f, 5f, 72f, 2f)
            val foret = sousLaCrete(-2f, 72f, crete)
            foret.op(bosses(-2f, 72f, { x -> crete(x) + 0.4f }, 1.6f, 2.8f, Alea(2001)), Path.Op.UNION)
            foret.op(chemin("M60 -4L104 -4L104 20L60 20Z"), Path.Op.UNION)
            piece(foret, papier(0xFF244E24.toInt()))
            for ((k, col) in listOf(0xFF3A7232, 0xFF2E622A, 0xFF447E36).withIndex()) {
                val lis = { x: Float -> crete(x) + 4.5f + k * 5f + sin(x * 0.35f + k) * 0.8f }
                val b = sousLaCrete(-2f, 72f, lis)
                b.op(bosses(-2f, 72f, lis, 1.4f, 2.4f, Alea(2002 + k)), Path.Op.UNION)
                piece(b, papier(col.toInt()), 0.5f)
            }
            plan(1)
            pieceD("M-4 45L104 45L104 68L-4 68Z", papier(0xFF9C978E.toInt()), 0f)
            plan(0)
            val ciment = papier(0xFFB4AEA2.toInt())
            // Les rangs de caveaux, chacun sur le muret de sa terrasse.
            var plan = 0
            var rang = -100f
            for (c in CAVEAUX_MORNE) {
                if (c.plan != plan) { plan = c.plan; plan(plan) }
                if (abs(c.sol - rang) > 3f) {
                    rang = c.sol
                    pieceD("M${c.x - 1f} ${rang - 0.4f}L104 ${rang - 0.4f}L104 ${rang + 1.6f}L${c.x - 1f} ${rang + 1.6f}Z", ciment, 0.5f)
                }
                caveau(c)
            }
            // Les bougies posées sur les toits plats.
            for (b in BOUGIES_MORNE) if (b[2] < 2f) {
                if (b[2].toInt() != plan) { plan = b[2].toInt(); plan(plan) }
                bougie(b[0], b[1])
            }
            plan(2)
            // Un flamboyant en fleurs à gauche, au-dessus des tombes : celui de
            // Bouillante retourné. Une secousse emporte ses fleurs sur le
            // cimetière : voir [FLAMBOYANT_MORNE].
            tasser(50f, 0f, -1f, 1f) { flamboyantEnParasol(2020) }
            // Devant : la dalle d'un caveau en damier à gauche, les grandes
            // dalles grises en gradins à droite.
            pieceD("M-2 55.6L24 54.8L30 57.6L30 68L-2 68Z", papier(0xFF4A4846.toInt()), 0.8f)
            pieceD("M-2 58.4L30 58.4L30 62.6L-2 62.6Z", papier(0xFFF2EFE8.toInt()), 0.2f)
            damier(-2f, 58.4f, 16, 2, 2.1f, papier(0xFF1E1E22.toInt()))
            pieceD("M56 66L60 53L102 50.6L102 68L56 68Z", papier(0xFF8C8882.toInt()), 0.8f)
            pieceD("M58.6 58L102 55.6L102 57.4L58.2 59.4Z", papier(0xFFB0ACA4.toInt()), 0.3f)
            pieceD("M57.2 62.6L102 60.6L102 62.2L56.8 64Z", papier(0xFFB0ACA4.toInt()), 0.3f)
            pieceD("M60 53L102 50.6L102 51.6L59.8 54Z", ciment, 0f)
            for (b in BOUGIES_MORNE) if (b[2] >= 2f) bougie(b[0], b[1])
        }

        /** Le cimetière de Morne-à-l'Eau : le morne couvert de caveaux en damier noir et blanc, gradin après gradin, une dalle en damier devant. */
        fun morneALEau() {
            if (PAPIER) { morneALEauPapier(); return }
            val r = Alea(201)
            crete(26f, 1.4f, 202, P.loin)
            val noir = mix(0xFF1C1C1E.toInt(), P.pres, if (P.nuit) 0.3f else 0f)
            val blanc = duJour(0xFFF4F1EA.toInt())
            // Le morne couvert de caveaux, gradin après gradin : l'amphithéâtre
            // blanc et noir vu depuis l'entrée. Les rangs du fond se fondent
            // un peu dans le lointain.
            plan(1)
            forme("M0 66L0 34C24 27 56 23 100 25L100 66Z", mix(P.milieu, P.pierre, 0.35f))
            val gradins = floatArrayOf(30f, 2.2f, 34f, 2.7f, 38.6f, 3.2f, 43.6f, 3.8f)
            var i = 0
            while (i < gradins.size) {
                val y = gradins[i]
                val w = gradins[i + 1]
                val brume = 0.42f - i * 0.07f
                val n = mix(noir, P.loin, brume)
                val b = mix(blanc, P.loin, brume * 0.7f)
                rect(0f, y - 0.2f, 100f, 0.6f, mix(P.pierre, P.pres, 0.3f))
                var x = -w * r()
                while (x < 100f) {
                    val h = w * (0.9f + r() * 0.5f)
                    caveau(x, y, w, h, n, b, (r() * 3f).toInt(), bougie = r() < 0.25f)
                    x += w * (1.08f + r() * 0.22f)
                }
                i += 2
            }
            plan(2)
            rect(0f, 52f, 100f, BAS - 52f, mix(P.sol, P.pres, 0.5f))
            // Au premier plan, les grands caveaux et une dalle en damier vue de dessus.
            caveau(2f, 55f, 9f, 9.5f, noir, blanc, 0, bougie = true)
            caveau(76f, 56f, 10f, 11f, noir, blanc, 1, bougie = true)
            caveau(89f, 54f, 8f, 8f, noir, blanc, 2)
            val dx = 4f
            val haut = 54f
            val prof = 4f
            val gauche = 26f
            val large = 40f
            val face = mix(blanc, noir, 0.08f)
            rect(gauche, haut, large, 5f, face)
            damier(gauche, haut, large, 5f, 2.5f, noir)
            // Le dessus de la dalle, en perspective : chaque case est un
            // parallélogramme, le damier fuit vers le morne.
            val cols = 12
            val rangs = 4
            for (ry in 0 until rangs) for (cx in 0 until cols) {
                val t0 = ry / rangs.toFloat(); val t1 = (ry + 1) / rangs.toFloat()
                fun px(c: Int, t: Float) = gauche + dx * t + c * (large - 2f * dx * t) / cols
                val y0 = haut - prof * t0; val y1 = haut - prof * t1
                val col = if ((cx + ry) % 2 == 0) noir else blanc
                forme("M${px(cx, t0)} ${y0}L${px(cx + 1, t0)} ${y0}L${px(cx + 1, t1)} ${y1}L${px(cx, t1)} ${y1}Z", col)
            }
            if (P.allume) for (bx in floatArrayOf(31f, 46f, 60f)) {
                rond(bx, haut - 1.6f, 1.6f, P.fenetre, 0.3f)
                rect(bx - 0.25f, haut - 2.2f, 0.5f, 1.2f, P.fenetre)
            }
        }

        /** L'îlet Macou : le banc de sable, sa mangrove et la chapelle des marins disparus, une yole, un pélican. */
        fun iletMacou() {
            if (PAPIER) { iletMacouPapier(); return }
            crete(34f, 0.7f, 211, P.loin)
            plan(1)
            eau(36f, 212)
            forme("M10 52Q50 40 90 52Q50 62 10 52Z", mix(P.eau, 0xFF8FE0D2.toInt(), if (P.nuit) 0.08f else 0.4f))
            forme("M30 48.4Q50 43 70 48.4Q50 51.6 30 48.4Z", sable())
            var x = 36f
            while (x < 62f) { trait("M$x 48.2Q${x + 1f} 46 ${x + 2f} 48.2", P.sol, 0.3f); x += 2f }
            bosquet(34f, 62f, 46.5f, 2.4f, 213, P.milieu)
            rond(48f, 42f, 3.4f, mix(P.milieu, P.pres, 0.3f))
            rond(53f, 43f, 2.8f, mix(P.milieu, P.pres, 0.3f))
            // La chapelle des marins disparus, dans la mangrove.
            val mur = duJour(0xFFF4F1EA.toInt())
            rect(56.6f, 44f, 3.6f, 3.6f, mur)
            triangle(56.2f, 44f, 58.4f, 41.8f, 60.6f, 44f, P.toit)
            rect(58.2f, 39.8f, 0.4f, 2.2f, mur)
            rect(57.7f, 40.4f, 1.4f, 0.4f, mur)
            rect(58f, 45.6f, 0.8f, 2f, mix(P.pres, 0xFF000000.toInt(), 0.3f))
            plan(2)
            forme("M70 58Q78 60.4 88 57.6L87 59.6Q78 61.8 71 59.8Z", duJour(0xFFF4F1EA.toInt()))
            forme("M71 59.8Q78 61.8 87 59.6L86.8 60.3Q78 62.4 71.4 60.5Z", duJour(0xFFD6452E.toInt()))
            rect(68.4f, 56.6f, 1.6f, 2.6f, P.sol)
            pieu(92f, 56f)
        }

        // ------------------------------------------- l'Îlet Macou en papier

        /** Une racine-échasse de palétuvier : un arc de ([x0], [y0]) à ([x1], [y1]) qui monte de [h], épais de [e]. */
        private fun racine(x0: Float, y0: Float, x1: Float, y1: Float, h: Float, e: Float, col: Int) {
            val cx = (x0 + x1) / 2f + (x1 - x0) * 0.15f
            val cy = min(y0, y1) - h
            pieceD("M$x0 ${y0}Q$cx $cy $x1 ${y1}L${x1 + e} ${y1}Q${cx + e * 0.4f} ${cy + e * 1.6f} ${x0 + e} ${y0}Z", col, 0.25f)
        }

        /**
         * Deux ou trois feuilles de palétuvier près de ([x], [y]), chacune
         * dans son sens : posées en étoile, cinq feuilles se lisent comme un
         * astérisque.
         */
        private fun rosette(x: Float, y: Float, l: Float, col: Int, r: Alea) {
            repeat(2 + (r() * 2f).toInt()) {
                feuillePliee(x + (r() - 0.5f) * l * 1.6f, y + (r() - 0.5f) * l, l * (0.8f + r() * 0.5f), -150f + r() * 120f, col, 0.45f)
            }
        }

        /**
         * L'Îlet Macou en papier découpé, d'après la photo du propriétaire
         * (2026-11-08) : la mangrove du Grand Cul-de-Sac, ses palétuviers
         * rouges posés sur leurs racines-échasses arquées au ras de l'eau, le
         * feuillage jaune-vert à gauche, sombre au fond, un tronc clair qui
         * monte en biais à droite, à côté du mot, et derrière lui la petite
         * chapelle blanche des marins disparus (demandée par le propriétaire,
         * absente de la photo) ; l'eau calme et brune qui reflète le tout. Une trouée de ciel au-dessus du mot garde le
         * centre clair. Le pélican se pose sur son pieu devant, l'eau bouge
         * sur la carte ouverte (`Reflets` dans [Ambiance]).
         */
        fun iletMacouPapier() {
            // Le fond de la mangrove : une masse sombre en lisières, une
            // trouée de ciel au-dessus du mot, des troncs fins qui se croisent.
            val fond = brisee(-2f, 4f, 22f, 8f, 36f, 13f, 50f, 15.6f, 64f, 12f, 80f, 6f, 102f, 3f)
            val f = sousLaCrete(-2f, 102f, fond)
            f.op(bosses(-2f, 102f, { x -> fond(x) + 0.4f }, 1.6f, 2.6f, Alea(2101)), Path.Op.UNION)
            piece(f, papier(0xFF1E3E22.toInt()))
            for ((k, col) in listOf(0xFF2A5228, 0xFF24482A).withIndex()) {
                val lis = { x: Float -> fond(x) + 7f + k * 8f + sin(x * 0.3f + k) * 1f }
                val b = sousLaCrete(-2f, 102f, lis)
                b.op(bosses(-2f, 102f, lis, 1.4f, 2.4f, Alea(2102 + k)), Path.Op.UNION)
                piece(b, papier(col.toInt()), 0.5f)
            }
            val gris = papier(0xFF8A7A66.toInt())
            barre(38f, 44f, 43f, 22f, 0.6f, gris, 0.3f)
            barre(43f, 22f, 40f, 15f, 0.35f, gris, 0f)
            barre(48f, 44f, 53f, 18f, 0.55f, gris, 0.3f)
            barre(51f, 28f, 57f, 22f, 0.3f, gris, 0f)
            barre(62f, 44f, 58f, 24f, 0.5f, gris, 0.3f)
            val rf = Alea(2104)
            repeat(14) { rosette(30f + rf() * 40f, fond(50f) + 6f + rf() * 20f, 1.6f, papier(0xFF3A6A30.toInt()), rf) }
            plan(1)
            // Les racines-échasses du fond, sombres, puis l'eau.
            val brunRouge = papier(0xFF6E4634.toInt())
            var rx = -4f
            while (rx < 102f) {
                val l = 6f + rf() * 9f
                racine(rx, 46.6f, rx + l, 46.4f + rf() * 0.6f, 5f + rf() * 4f, 0.45f + rf() * 0.2f, brunRouge)
                rx += 2f + rf() * 3f
            }
            // L'eau calme et sombre : le reflet rouge des racines au bord, le
            // reflet vert de la mangrove dessous, quelques longs filets clairs.
            pieceD("M-2 46.2L102 46.2L102 68L-2 68Z", papier(0xFF3E4430.toInt()), 0.3f)
            pieceD("M-2 47Q30 47.6 60 47.2Q80 47 102 47.4L102 51Q70 50.2 40 51Q20 51.4 -2 50.6Z", papier(0xFF5A4232.toInt()), 0f)
            pieceD("M-2 53Q30 52.4 60 53.6Q80 54 102 53L102 56Q70 57 40 56Q20 55.6 -2 56.4Z", papier(0xFF34402A.toInt()), 0f)
            for ((y, x0, l) in arrayOf(Triple(49f, 6f, 18f), Triple(51.6f, 40f, 26f), Triple(54.6f, 12f, 22f), Triple(57.4f, 56f, 30f),
                    Triple(60.4f, 2f, 26f), Triple(62.8f, 44f, 34f), Triple(52.6f, 78f, 18f)))
                pieceD("M$x0 ${y}Q${x0 + l / 2f} ${y - 0.3f} ${x0 + l} ${y}Q${x0 + l / 2f} ${y + 0.5f} $x0 ${y}Z", papier(0xFF7A7458.toInt()), 0f)
            // Les reflets, d'une seule pièce chacun, au bord ondulé : le
            // feuillage jaune-vert de gauche, le tronc clair de droite.
            val ondes = Alea(2106)
            val bordFeuillage = StringBuilder("M-2 50.4")
            var oy = 50.4f
            while (oy < 61f) { bordFeuillage.append("L${17f - (oy - 50f) * 1.1f + ondes() * 4f} $oy"); oy += 0.9f }
            bordFeuillage.append("L-2 61Z")
            pieceD(bordFeuillage.toString(), papier(0xFF627A34.toInt()), 0f)
            val reflet = Path()
            reflet.moveTo(84.6f, 47f)
            oy = 47f
            var sens = 1f
            while (oy < 58f) { reflet.quadTo(84.6f + sens * 0.7f, oy + 1.2f, 84.8f, oy + 2.4f); oy += 2.4f; sens = -sens }
            reflet.lineTo(85.8f, 59.4f)
            reflet.lineTo(86.6f, oy)
            while (oy > 47.5f) { reflet.quadTo(86.6f + sens * 0.7f, oy - 1.2f, 86.4f, oy - 2.4f); oy -= 2.4f; sens = -sens }
            reflet.close()
            piece(reflet, papier(0xFF8A7E62.toInt()), 0f)
            // Le feuillage jaune-vert de gauche et ses rosettes.
            val gauche = Path()
            repeat(18) { gauche.op(disque(-4f + rf() * 24f, 2f + rf() * 38f, 3.2f + rf() * 2.8f), Path.Op.UNION) }
            piece(gauche, papier(0xFF5E8A2A.toInt()))
            repeat(34) { rosette(-3f + rf() * 24f, 2f + rf() * 38f, 1.7f, papier(if (rf() < 0.6f) 0xFFA8C440.toInt() else 0xFF7EA634.toInt()), rf) }
            // Le tronc clair qui monte en biais à droite, son feuillage, ses racines.
            val droite = Path()
            repeat(16) { droite.op(disque(74f + rf() * 30f, -2f + rf() * 34f, 3f + rf() * 2.6f), Path.Op.UNION) }
            piece(droite, papier(0xFF3A6A2C.toInt()))
            repeat(26) { rosette(74f + rf() * 28f, -1f + rf() * 33f, 1.6f, papier(if (rf() < 0.5f) 0xFF6E9E36.toInt() else 0xFF8AB23E.toInt()), rf) }
            // La chapelle des marins disparus, petite et blanche entre les
            // palétuviers, à droite du tronc clair : pignon face à nous, toit
            // gris, porte cintrée, croix au faîte.
            val chaux = papier(0xFFF4F1EA.toInt())
            pieceD("M84 46.6Q86 42.6 91 42.4Q96 42.6 99 46.6Z", papier(0xFF4A3E30.toInt()), 0.4f)
            pieceD("M87.4 43L87.4 36.8L95.4 36.8L95.4 43Z", chaux, 0.6f)
            pieceD("M93.8 43L93.8 36.8L95.4 36.8L95.4 43Z", papier(0xFFD8D4CA.toInt()), 0f)
            pieceD("M86.6 37.2L91.4 32.4L96.2 37.2Z", papier(0xFF8E8A84.toInt()), 0.5f)
            pieceD("M88 36.8L91.4 33.4L94.8 36.8Z", chaux, 0f)
            pieceD("M90.4 43L90.4 39.8Q91.4 38.6 92.4 39.8L92.4 43Z", papier(0xFF3A3430.toInt()), 0.2f)
            barre(91.4f, 32.6f, 91.4f, 29.6f, 0.35f, chaux, 0.3f)
            barre(90.4f, 30.6f, 92.4f, 30.6f, 0.35f, chaux, 0f)
            val clair = papier(0xFFCDB89C.toInt())
            pieceD("M84.6 42Q87.6 24 80.4 -2L82.6 -2Q90 24 86.8 42Z", clair, 0.6f)
            for ((x1, h) in arrayOf(76f to 5f, 80f to 4f, 92f to 4.6f, 95f to 3.6f, 72f to 6f))
                racine(85.6f, 41f, x1, 47f, h, 0.45f, clair)
            // Les racines-échasses du devant, rouges et claires, au ras de l'eau.
            val racines = intArrayOf(papier(0xFFA8684A.toInt()), papier(0xFFD2B49A.toInt()), papier(0xFF8A5038.toInt()))
            rx = -4f
            while (rx < 102f) {
                val l = 5f + rf() * 11f
                val y0 = 47f + rf() * 0.8f
                racine(rx, y0, rx + l, y0 + (rf() - 0.5f) * 0.8f, 4f + rf() * 6f, 0.55f + rf() * 0.35f, racines[(rf() * 3f).toInt().coerceAtMost(2)])
                rx += 1.4f + rf() * 2.6f
            }
            plan(2)
            // Des racines qui entrent par le bas à gauche, et le pieu du pélican.
            for ((x0, x1, h) in arrayOf(Triple(-3f, 9f, 8f), Triple(2f, 15f, 6f), Triple(-2f, 6f, 10f)))
                racine(x0, 64f, x1, 63.4f, h, 0.8f, racines[2])
            pieceD("M91.4 56L92.6 56L92.8 64L91.2 64Z", papier(0xFF5E4C3E.toInt()), 0.5f)
        }

        // ------------------------------------------- les Roches gravées en papier

        /** Un sillon gravé en anneau : le creux sombre, et sa lèvre basse éclairée. */
        private fun anneauGrave(cx: Float, cy: Float, r: Float, e: Float, creux: Int, levre: Int) {
            val anneau = disque(cx, cy, r).apply { op(disque(cx, cy, r - e), Path.Op.DIFFERENCE) }
            piece(Path(anneau).apply { offset(0.18f, 0.28f) }, levre, 0f)
            piece(anneau, creux, 0f)
        }

        /** Un sillon gravé en arc de cercle, de [debut] sur [balayage] degrés. */
        private fun arcGrave(cx: Float, cy: Float, r: Float, e: Float, debut: Float, balayage: Float, creux: Int, levre: Int) {
            val coin = Path().apply {
                moveTo(cx, cy)
                arcTo(RectF(cx - r - 1f, cy - r - 1f, cx + r + 1f, cy + r + 1f), debut, balayage)
                close()
            }
            val arc = disque(cx, cy, r).apply { op(disque(cx, cy, r - e), Path.Op.DIFFERENCE); op(coin, Path.Op.INTERSECT) }
            piece(Path(arc).apply { offset(0.18f, 0.28f) }, levre, 0f)
            piece(arc, creux, 0f)
        }

        /**
         * Les Roches gravées en papier découpé, d'après la photo du
         * propriétaire (2026-11-08) : dans la forêt de Trois-Rivières, peinte
         * de l'intérieur, un gros rocher gris taché de lichen porte à droite
         * du mot le visage gravé : la tête ronde, les yeux, la bouche, les
         * volutes de chaque côté, les rayons au-dessus et les deux spirales
         * dessous ; à gauche un rocher plus petit garde un visage effacé.
         * Balisiers pour le colibri.
         */
        /** La lisière de la voûte des Roches gravées, d'où pendent les lianes. */
        private val vouteRoches = brisee(-2f, 14f, 20f, 18f, 40f, 12f, 60f, 16f, 80f, 10f, 102f, 13f)

        /** Une liane des Roches gravées, de la voûte jusqu'à ([lx], [ly]), balancée de [angle] degrés autour de son attache. */
        fun liane(lx: Float, ly: Float, angle: Float) {
            val haut = vouteRoches(lx)
            c.save()
            c.rotate(angle, lx, haut)
            pieceD("M$lx ${haut}Q${lx + 1.6f} ${(haut + ly) / 2f} $lx ${ly}L${lx + 0.35f} ${ly}Q${lx + 1.95f} ${(haut + ly) / 2f} ${lx + 0.35f} ${haut}Z",
                papier(0xFF4E5A34.toInt()), 0f)
            c.restore()
        }

        fun rochesGraveesPapier() {
            // La forêt de l'intérieur : la voûte, des troncs à contreforts, des lianes.
            val voute = vouteRoches
            val v = Path().apply { addRect(-4f, -4f, 104f, 6f, Path.Direction.CW) }
            v.op(bosses(-2f, 102f, voute, 2.4f, 3.6f, Alea(2201)), Path.Op.UNION)
            v.op(chemin("M-4 -4L104 -4L104 ${voute(102f)}L-4 ${voute(-2f)}Z"), Path.Op.UNION)
            val feuillage = papier(0xFF2E5E2A.toInt())
            val tronc = papier(0xFF5E5448.toInt())
            for ((tx, w) in arrayOf(26f to 2.2f, 44f to 1.6f, 58f to 2.6f)) {
                pieceD("M${tx - w * 1.8f} 52Q${tx - w * 0.5f} 46 ${tx - w / 2f} 30L${tx - w / 2f} 0L${tx + w / 2f} 0L${tx + w / 2f} 30Q${tx + w * 0.5f} 46 ${tx + w * 1.8f} 52Z",
                    tronc, 0.5f)
            }
            piece(v, feuillage, 0.6f)
            for ((k, col) in listOf(0xFF3C7232, 0xFF4E8A3A).withIndex()) {
                piece(bosses(-2f, 102f, { x -> voute(x) - 2f - k * 2.4f }, 1.8f, 2.8f, Alea(2202 + k)), papier(col.toInt()), 0.2f)
            }
            // Les lianes sont dans [BETES] : elles se balancent sur la carte ouverte.
            plan(1)
            // Le sol de la forêt, des fougères basses.
            pieceD("M-2 50Q30 47.6 60 49Q80 50 102 48L102 68L-2 68Z", papier(0xFF3E4A2A.toInt()), 0.5f)
            val rf = Alea(2204)
            repeat(16) { feuillePliee(rf() * 100f, 50f + rf() * 4f, 2.4f + rf() * 1.4f, -150f + rf() * 120f, papier(0xFF4E7A34.toInt()), 0.32f) }
            plan(2)
            // Le gros rocher à droite, son ombre, son lichen, sa mousse.
            val roc = papier(0xFF8E8C84.toInt())
            pieceD("M62 64Q60 46 66 32Q72 20 86 18Q100 17 104 22L104 66L62 66Z", roc, 0.9f)
            pieceD("M62 64Q61 50 65.6 38Q68 46 74 51Q70 58 72 65Z", papier(0xFF6A6862.toInt()), 0f)
            pieceD("M96 18Q101 18.4 104 22L104 50Q98 40 97 28Z", papier(0xFF5E7A3A.toInt()), 0f)
            taches(64f, 102f, 62f, 12, 2205, papier(0xFFC4CCB6.toInt()), { x -> 20f + (x - 86f).let { it * it } * 0.04f })
            // Le visage gravé : la tête, les yeux, la bouche, les volutes, les rayons, les deux spirales.
            val creux = papier(0xFF4E4C48.toInt())
            val levre = papier(0xFFB8B6AE.toInt())
            val (fx, fy) = 86f to 34f
            anneauGrave(fx, fy, 3.2f, 0.5f, creux, levre)
            piece(disque(fx - 1.1f, fy - 0.5f, 0.5f), creux, 0f)
            piece(disque(fx + 1.1f, fy - 0.5f, 0.5f), creux, 0f)
            piece(Path().apply { addOval(RectF(fx - 0.7f, fy + 1f, fx + 0.7f, fy + 1.8f), Path.Direction.CW) }, creux, 0f)
            arcGrave(fx - 4.6f, fy - 1f, 1.8f, 0.45f, 90f, 270f, creux, levre)
            arcGrave(fx + 4.6f, fy - 1f, 1.8f, 0.45f, 180f, 270f, creux, levre)
            for (k in 0 until 7) {
                val a = PI.toFloat() * (1.2f + k * 0.1f)
                barre(fx + cos(a) * 3.9f, fy + sin(a) * 3.9f, fx + cos(a) * 6.4f, fy + sin(a) * 6.4f, 0.4f, creux, 0f)
            }
            anneauGrave(fx - 2.6f, fy + 7.4f, 2.1f, 0.45f, creux, levre)
            anneauGrave(fx + 2.6f, fy + 7.4f, 2.1f, 0.45f, creux, levre)
            arcGrave(fx - 2.6f, fy + 7.4f, 1f, 0.4f, 0f, 270f, creux, levre)
            arcGrave(fx + 2.6f, fy + 7.4f, 1f, 0.4f, 270f, 270f, creux, levre)
            // Le petit rocher de gauche et son visage effacé.
            pieceD("M-2 66L-2 46Q6 42 14 44Q20 48 20 58Q20 63 18 66Z", papier(0xFF7E7C76.toInt()), 0.8f)
            taches(-2f, 18f, 62f, 5, 2206, papier(0xFFB8C0A8.toInt()), { 46f })
            anneauGrave(8f, 53f, 2.2f, 0.4f, creux, levre)
            piece(disque(7.2f, 52.6f, 0.4f), creux, 0f)
            piece(disque(8.8f, 52.6f, 0.4f), creux, 0f)
            balisier(12f, 64f, 13f, feuille())
            balisier(88f, 64f, 13f, feuille())
        }

        // ------------------------------------------- la place de la Victoire en papier

        /**
         * La statue de Vélo, place de la Victoire, en papier découpé, d'après
         * les photos du propriétaire (2026-11-08 et 2026-11-09) : Marcel
         * Lollia, le tambouyé, en bronze, à califourchon sur son ka couché,
         * les deux mains sur la peau, sur son socle de pierre ; à gauche du
         * mot, la tête tournée vers la droite.
         *
         * Derrière, une rue de Pointe-à-Pitre vue de biais, comme sur la photo
         * du propriétaire (2026-11-09) : les façades fuient vers la gauche,
         * on voit le dessous des avant-toits et la tôle qui monte, et chaque
         * maison a son carton, la plus proche à droite. Les deux plus loin,
         * la case crème et la case de bois patiné, partagent le lointain : la
         * carte n'a que trois cartons. Le poteau rouge sur le trottoir, des
         * fanions tendus en haut. Ni voiture ni enseigne lisible.
         */
        // ------------------------------------------- la rue de Pointe-à-Pitre

        // La rue derrière Vélo, en perspective : u court le long des façades,
        // y est la hauteur telle qu'on la lit au bord droit, d l'avancée vers
        // la rue. Plus u est petit, plus c'est loin, et tout se resserre vers
        // l'horizon, à hauteur d'œil (y 38). [placeVictoirePapier] y peint les
        // façades, [persienneBattante] les persiennes qui battent.
        private fun recul(u: Float) = (u + 150f) / 254f
        private fun ex(u: Float, d: Float) = u - d * 0.6f * recul(u)
        private fun ey(u: Float, y: Float, d: Float) = 38f + (y - 38f) * recul(u) * (1f + d * 0.02f)
        // Une pièce de la rue, ses sommets donnés en triplets (u, y, d).
        private fun rue(col: Int, ombre: Float, vararg p: Float) {
            val t = Path()
            var i = 0
            while (i < p.size) {
                val x = ex(p[i], p[i + 2])
                val y = ey(p[i], p[i + 1], p[i + 2])
                if (i == 0) t.moveTo(x, y) else t.lineTo(x, y)
                i += 3
            }
            t.close()
            piece(t, col, ombre)
        }
        private fun mur(u0: Float, y0: Float, u1: Float, y1: Float, col: Int, ombre: Float = 0f, d: Float = 0f) =
            rue(col, ombre, u0, y0, d, u1, y0, d, u1, y1, d, u0, y1, d)
        // Des planches couchées, qui filent vers l'horizon.
        private fun lattes(u0: Float, y0: Float, u1: Float, y1: Float, col: Int, pas: Float = 1.3f) {
            var y = y0 + pas
            while (y < y1 - 0.2f) { mur(u0, y, u1, y + 0.16f, col); y += pas }
        }
        // Une persienne : le cadre, puis les lames.
        private fun persienne(u0: Float, y0: Float, u1: Float, y1: Float, cadre: Int, lame: Int, fond: Int) {
            mur(u0 - 0.5f, y0 - 0.5f, u1 + 0.5f, y1 + 0.3f, cadre, 0.3f)
            mur(u0, y0, u1, y1, fond)
            var y = y0 + 0.6f
            while (y < y1 - 0.3f) { mur(u0 + 0.2f, y, u1 - 0.2f, y + 0.35f, lame); y += 0.9f }
            mur((u0 + u1) / 2f - 0.12f, y0, (u0 + u1) / 2f + 0.12f, y1, cadre)
        }
        // Une console de fer sous un balcon, dans le plan qui avance vers la rue.
        private fun console(u: Float, y: Float, avance: Float, col: Int) {
            val t = Path().apply {
                moveTo(ex(u, avance), ey(u, y, avance))
                lineTo(ex(u, 0f), ey(u, y, 0f))
                lineTo(ex(u, 0f), ey(u, y + 3.4f, 0f))
                quadTo(ex(u, avance * 0.3f), ey(u, y + 1.2f, avance * 0.3f), ex(u, avance), ey(u, y, avance))
                close()
            }
            piece(t, col, 0.2f)
        }
        // Un balcon : les consoles, le dessous de la dalle, sa tranche, le
        // garde-corps devant et son retour sur le côté.
        private fun balcon(u0: Float, u1: Float, y: Float, fer: Int, dalle: Int, avance: Float = 3f) {
            for (cu in floatArrayOf(u0 + 1.5f, (u0 + u1) / 2f, u1 - 1.5f)) console(cu, y + 1.1f, avance * 0.9f, fer)
            rue(mix(dalle, 0xFF000000.toInt(), 0.25f), 0.4f,
                u0 - 0.6f, y + 1.1f, 0f, u1 + 0.6f, y + 1.1f, 0f, u1 + 0.6f, y + 1.1f, avance, u0 - 0.6f, y + 1.1f, avance)
            mur(u0 - 0.6f, y, u1 + 0.6f, y + 1.1f, dalle, 0.5f, avance)
            rue(fer, 0.2f, u0 - 0.6f, y - 5.4f, 0f, u0 - 0.6f, y - 5.4f, avance, u0 - 0.6f, y - 4.9f, avance, u0 - 0.6f, y - 4.9f, 0f)
            for (d in floatArrayOf(1f, 2f)) mur(u0 - 0.7f, y - 5f, u0 - 0.5f, y, fer, 0f, d)
            gardeCorps(u0, u1, y, fer, avance)
        }
        // Le garde-corps d'un balcon, vu de face ; [de] et [a] n'en gardent
        // qu'un tronçon, celui qui passe devant une persienne qui bat.
        private fun gardeCorps(u0: Float, u1: Float, y: Float, fer: Int, avance: Float, de: Float = -1e9f, a: Float = 1e9f) {
            val g = max(u0 - 0.6f, de)
            val dr = min(u1 + 0.6f, a)
            mur(g, y - 5.4f, dr, y - 4.9f, fer, 0.3f, avance)
            var u = u0
            while (u <= u1 + 0.01f) { if (u in de..a) mur(u - 0.1f, y - 5f, u + 0.1f, y, fer, 0f, avance); u += 1.1f }
            mur(g, y - 0.6f, dr, y - 0.3f, fer, 0f, avance)
        }

        /** Les couleurs de la persienne [k] : cadre, lames, fond. La case jaune les a rouges, la blanche vertes. */
        private fun couleursPersienne(k: Int) = if (k < 2)
            intArrayOf(papier(0xFF9A2E2A.toInt()), papier(0xFF7A2420.toInt()), papier(0xFFB8403A.toInt()))
        else intArrayOf(papier(0xFF3E7A5A.toInt()), papier(0xFF2E5E44.toInt()), papier(0xFF5E9A76.toInt()))

        /**
         * La persienne [k] de [PERSIENNES_VICTOIRE] ouverte de [angle]
         * radians : la pièce sombre derrière, allumée le soir, les deux
         * battants pivotés vers la rue sur leurs gonds, et devant eux le
         * tronçon du garde-corps qui les arrête.
         */
        fun persienneBattante(k: Int, angle: Float) {
            voile = 1f
            val q = PERSIENNES_VICTOIRE[k]
            val (cadre, lame, fond) = couleursPersienne(k).toList()
            val sombre = papier(0xFF3A302A.toInt())
            mur(q[0], q[1], q[2], q[3], if (P.allume) mix(sombre, P.fenetre or 0xFF000000.toInt(), 0.55f) else sombre)
            val w = (q[2] - q[0]) / 2f
            battant(q[0], w, 1f, angle, q[1], q[3], cadre, lame, fond)
            battant(q[2], w, -1f, angle * 0.92f, q[1], q[3], cadre, lame, fond)
            when (k) {
                0, 1 -> gardeCorps(38.5f, 60.5f, 26f, papier(0xFF5A2A22.toInt()), 3f, q[0] - 1.2f, q[2] + 1.2f)
                2 -> gardeCorps(63f, 108f, 25f, papier(0xFFF2F0EC.toInt()), 3.5f, q[0] - 1.2f, q[2] + 1.2f)
            }
        }

        // Un battant de [w] de large, son gond en [gond], qui s'ouvre vers
        // [sens] (1 : il pivote depuis la gauche) de [angle] radians.
        private fun battant(gond: Float, w: Float, sens: Float, angle: Float, y0: Float, y1: Float, cadre: Int, lame: Int, fond: Int) {
            val cu = cos(angle)
            val sd = sin(angle)
            fun u(f: Float) = gond + sens * f * w * cu
            fun d(f: Float) = f * w * sd
            fun bande(f0: Float, f1: Float, ya: Float, yb: Float, col: Int) =
                rue(col, 0f, u(f0), ya, d(f0), u(f1), ya, d(f1), u(f1), yb, d(f1), u(f0), yb, d(f0))
            bande(0f, 1f, y0, y1, cadre)
            bande(0.1f, 0.9f, y0 + 0.3f, y1 - 0.3f, fond)
            var y = y0 + 0.6f
            while (y < y1 - 0.4f) { bande(0.1f, 0.9f, y, y + 0.35f, lame); y += 0.9f }
        }
        // Un toit de tôle : le dessous de l'avant-toit et ses chevrons, la
        // planche de rive, et la tôle qui monte vers le faîte.
        private fun toit(u0: Float, u1: Float, y: Float, tole: Int, sous: Int, avance: Float = 5f) {
            rue(tole, 0.7f, u0 - 1f, y - 0.6f, avance, u1 + 1f, y - 0.6f, avance, u1 + 1f, y - 12f, -4f, u0 - 1f, y - 12f, -4f)
            var u = u0 - 0.4f
            val onde = mix(tole, 0xFF000000.toInt(), 0.22f)
            while (u < u1 + 0.6f) {
                rue(onde, 0f, u, y - 0.6f, avance, u + 0.3f, y - 0.6f, avance, u + 0.3f, y - 12f, -4f, u, y - 12f, -4f)
                u += 1.2f
            }
            rue(sous, 0.5f, u0 - 1f, y, 0f, u1 + 1f, y, 0f, u1 + 1f, y - 0.6f, avance, u0 - 1f, y - 0.6f, avance)
            u = u0
            val chevron = mix(sous, 0xFF000000.toInt(), 0.3f)
            while (u < u1) {
                rue(chevron, 0f, u, y, 0f, u + 0.3f, y, 0f, u + 0.3f, y - 0.6f, avance, u, y - 0.6f, avance)
                u += 2.4f
            }
            mur(u0 - 1f, y - 1.6f, u1 + 1f, y - 0.4f, mix(tole, 0xFFFFFFFF.toInt(), 0.25f), 0.3f, avance)
        }
        // Le trottoir devant une maison et sa bordure.
        private fun trottoir(u0: Float, u1: Float, col: Int) {
            rue(col, 0.3f, u0, 52f, 0f, u1, 52f, 0f, u1, 52f, 6f, u0, 52f, 6f)
            mur(u0, 52f, u1, 53f, mix(col, 0xFF000000.toInt(), 0.2f), 0.3f, 6f)
        }

        fun placeVictoirePapier() {
            val lueur = (0x60 shl 24) or (P.fenetre and 0xFFFFFF)

            // 1. Au plus loin, la case crème au balcon blanc et sa boutique jaune,
            // puis la case de bois patiné au balcon de fer noir.
            trottoir(-12f, 18f, papier(0xFFC8C4BA.toInt()))
            mur(-12f, 12f, 18f, 52f, papier(0xFFEDE3C8.toInt()), 0.3f)
            rue(papier(0xFFF2EAD4.toInt()), 0.5f, -13f, 12f, 0f, 19f, 12f, 0f, 19f, 11.2f, 4f, -13f, 11.2f, 4f)
            mur(-13f, 10f, 19f, 11.2f, papier(0xFFD8CCAE.toInt()), 0.4f, 4f)
            mur(3f, 15f, 15f, 25f, papier(0xFFF4F1EA.toInt()), 0.2f)
            for (k in 0 until 9) mur(3f, 15.6f + k * 1f, 15f, 15.8f + k * 1f, papier(0xFFD6D0C4.toInt()))
            balcon(-11f, 16f, 27f, papier(0xFFF2F0EC.toInt()), papier(0xFFE0D8C4.toInt()))
            mur(2f, 31f, 15f, 49f, papier(0xFFE8C840.toInt()), 0.3f)
            mur(2f, 31f, 15f, 32.6f, papier(0xFF4E8A3E.toInt()))
            val fer = papier(0xFF2A2624.toInt())
            trottoir(18f, 37f, papier(0xFFB8B4AA.toInt()))
            mur(18f, 10f, 37f, 28f, papier(0xFF8A6A4E.toInt()), 0.4f)
            lattes(18f, 10f, 37f, 28f, papier(0xFF6E523C.toInt()))
            mur(18f, 28f, 37f, 52f, papier(0xFF9A948A.toInt()), 0.4f)
            lattes(18f, 28f, 37f, 52f, papier(0xFF7E786E.toInt()))
            toit(18f, 37f, 10f, papier(0xFF7E5A44.toInt()), papier(0xFF5E4636.toInt()), 4f)
            for (fu in floatArrayOf(20.5f, 28.5f)) {
                mur(fu, 13f, fu + 5.5f, 26.4f, papier(0xFF6A3E30.toInt()), 0.3f)
                mur(fu + 2.65f, 13f, fu + 2.85f, 26.4f, papier(0xFF4A2A20.toInt()))
            }
            if (P.allume) for (fu in floatArrayOf(20.5f, 28.5f)) mur(fu + 0.4f, 13.6f, fu + 5.1f, 26f, lueur)
            balcon(19f, 36f, 27f, fer, papier(0xFF6E6860.toInt()))
            for (fu in floatArrayOf(20f, 28.5f)) {
                mur(fu, 31f, fu + 6.5f, 51f, papier(0xFF86807A.toInt()), 0.3f)
                mur(fu + 3.15f, 31f, fu + 3.35f, 51f, papier(0xFF5E5A54.toInt()))
            }
            mur(36.6f, 10f, 37.4f, 52f, papier(0xFF8E8A84.toInt()), 0.2f, 0.6f)

            // 2. Au milieu, la case jaune aux encadrements rouges, sa lucarne,
            // son balcon fleuri, le damier de son soubassement.
            plan(1)
            val rougeCase = papier(0xFF9A2E2A.toInt())
            trottoir(37f, 62f, papier(0xFFC2BEB4.toInt()))
            val rt = Alea(2302)
            repeat(14) {
                val u = 38f + rt() * 22f
                val d = 0.6f + rt() * 4.6f
                val tesselle = papier(intArrayOf(0xFF7A9AB8.toInt(), 0xFFD8A040.toInt(), 0xFFB85A48.toInt(), 0xFFE8E4DA.toInt())[(rt() * 4f).toInt().coerceAtMost(3)])
                rue(tesselle, 0f, u, 52f, d, u + 1.2f, 52f, d, u + 1.2f, 52f, d + 0.8f, u, 52f, d + 0.8f)
            }
            mur(37f, 7f, 62f, 52f, papier(0xFFF0C84A.toInt()), 0.5f)
            toit(37f, 62f, 7f, papier(0xFF9C9890.toInt()), papier(0xFF7A3A2E.toInt()))
            // La lucarne posée sur la tôle : sa joue blanche, sa fenêtre, son pignon.
            mur(46f, -1f, 53f, 6f, papier(0xFFF2EEE4.toInt()), 0.4f, -2f)
            mur(47.6f, 0.4f, 51.4f, 5f, rougeCase, 0f, -2f)
            rue(papier(0xFF8A8680.toInt()), 0.3f, 45.4f, -1f, -2f, 49.5f, -4.6f, -2f, 53.6f, -1f, -2f)
            for (k in 0..1) PERSIENNES_VICTOIRE[k].let { q -> couleursPersienne(k).let { c -> persienne(q[0], q[1], q[2], q[3], c[0], c[1], c[2]) } }
            if (P.allume) for (fu in floatArrayOf(40.5f, 52f)) mur(fu, 11f, fu + 6f, 25f, lueur)
            balcon(38.5f, 60.5f, 26f, papier(0xFF5A2A22.toInt()), rougeCase)
            mur(43f, 30f, 57f, 51f, rougeCase, 0.4f)
            mur(43.8f, 30.8f, 49.8f, 51f, papier(0xFFE2B040.toInt()))
            mur(50.2f, 30.8f, 56.2f, 51f, papier(0xFFE2B040.toInt()))
            for (zu in floatArrayOf(37.6f, 57.6f)) {
                mur(zu, 46f, zu + 4.6f, 51.4f, papier(0xFF6A3A2E.toInt()), 0.2f)
                for (k in 0 until 3) rue(papier(0xFFE8E2D4.toInt()), 0f,
                    zu + k * 1.5f, 51f, 0f, zu + k * 1.5f + 0.75f, 47f, 0f, zu + k * 1.5f + 1.5f, 51f, 0f)
            }
            // Le poteau rouge, sur la bordure du trottoir.
            barre(ex(36f, 6f), ey(36f, 12f, 6f), ex(36f, 6f), ey(36f, 53f, 6f), 1.2f, papier(0xFFC23A30.toInt()), 0.6f)

            // 3. Devant, la grande case de planches blanches aux boiseries
            // vertes, et la chaussée.
            plan(2)
            pieceD("M${ex(-20f, 6f)} ${ey(-20f, 53f, 6f)}L${ex(120f, 6f)} ${ey(120f, 53f, 6f)}L110 70L-10 70Z",
                papier(0xFF6E6C68.toInt()), 0.6f)
            for (k2 in 0 until 4) pieceD("M${-4f + k2 * 7f} 66L${1.4f + k2 * 7f} 66L${4.4f + k2 * 7f} 58L${-1f + k2 * 7f} 58Z", papier(0xFFF0EEE8.toInt()), 0f)
            val vert = papier(0xFF3E7A5A.toInt())
            val vitre = papier(0xFF2E3A40.toInt())
            trottoir(62f, 112f, papier(0xFFC8C4BA.toInt()))
            mur(62f, 7f, 112f, 52f, papier(0xFFE6E2D8.toInt()), 0.5f)
            lattes(62f, 7f, 112f, 52f, papier(0xFFCAC4B8.toInt()), 1.5f)
            toit(62f, 112f, 7f, papier(0xFF8C7A66.toInt()), papier(0xFF6E5E4E.toInt()), 6f)
            mur(66.4f, 10.4f, 76.6f, 24.4f, vert, 0.3f)
            mur(67f, 11f, 71.4f, 15f, vitre)
            mur(71.6f, 11f, 76f, 15f, vitre)
            for (k in 2..3) PERSIENNES_VICTOIRE[k].let { q -> couleursPersienne(k).let { c -> persienne(q[0], q[1], q[2], q[3], c[0], c[1], c[2]) } }
            mur(85.4f, 9.4f, 97.6f, 24.4f, vert, 0.3f)
            mur(86f, 10f, 97f, 24.4f, papier(0xFFEEEAE2.toInt()))
            mur(91.4f, 10f, 91.6f, 24.4f, papier(0xFFC2BCB0.toInt()))
            if (P.allume) { mur(67f, 11f, 76f, 15f, lueur); mur(86f, 10f, 97f, 24.4f, lueur) }
            mur(65.4f, 29.4f, 72.6f, 51f, vert, 0.3f)
            mur(66f, 30f, 72f, 51f, papier(0xFFEEEAE2.toInt()))
            mur(87.4f, 28.4f, 101.6f, 51f, vert, 0.3f)
            mur(88f, 29f, 101f, 51f, papier(0xFFEEEAE2.toInt()))
            mur(94.4f, 29f, 94.6f, 51f, papier(0xFFC2BCB0.toInt()))
            balcon(63f, 108f, 25f, papier(0xFFF2F0EC.toInt()), papier(0xFFD6D0C4.toInt()), 3.5f)
            // Les fanions, les tourterelles et les jardinières sont dans [BETES] : ils bougent sur la carte ouverte.
            // Le socle, le ka couché, Vélo à califourchon dessus : à gauche de la
            // fenêtre, la tête tournée vers la droite (propriétaire,
            // 2026-11-09). Le groupe, dessiné tourné vers la gauche, est
            // retourné en miroir et un peu réduit, pour que la peau du ka reste
            // à gauche du dessin du mot.
            tasser(84f, 52.6f, -0.85f, 1f, dx = -72.6f) {
                pieceD("M70 60L98 60L98 54L70 54Z", papier(0xFF6E6A64.toInt()), 0.8f)
                pieceD("M70 54L98 54L97 52.6L71 52.6Z", papier(0xFF8E8A84.toInt()), 0.3f)
            }
            tasser(84.3f, 52.6f, -0.8f, 0.8f, dx = -74.9f) { veloSurSonKa() }
        }

        /**
         * Vélo et son ka sur la plinthe de bronze, dessinés tournés vers la
         * gauche, posés sur y 52.6 entre x 72.6 et 96 ; [placeVictoirePapier]
         * les retourne et les place.
         */
        private fun veloSurSonKa() {
            pieceD("M72.6 52.6L96 52.6L96 49.4L72.6 49.4Z", papier(0xFF5A4A3A.toInt()), 0.6f)
            val bronze = papier(0xFF9C7440.toInt())
            val sombre = papier(0xFF5E4426.toInt())
            val clair = papier(0xFFCCA466.toInt())
            // Le ka couché sur le socle, la peau tournée vers la gauche : au
            // gwoka, le joueur s'assied à califourchon sur le tambour
            // (propriétaire, 2026-11-09). Le fût, ses cercles, les cordes en
            // zigzag, puis la peau vue de trois quarts.
            pieceD("M75 40.6Q84 38.6 93 40.8Q94.4 44.8 93 48.8Q84 50.6 75 49.2Z", bronze, 0.7f)
            pieceD("M76.4 40.2L78 40.1L78 49.3L76.4 49.3Z", sombre, 0f)
            pieceD("M91 40.6L92.6 40.7L92.6 48.9L91 49Z", sombre, 0f)
            for (k2 in 0 until 3) {
                val x0 = 78.6f + k2 * 4f
                barre(x0, 40.6f, x0 + 2f, 49f, 0.25f, clair, 0f)
                barre(x0 + 2f, 49f, x0 + 4f, 40.6f, 0.25f, clair, 0f)
            }
            piece(Path().apply { addOval(RectF(73.6f, 40.4f, 76.6f, 49.3f), Path.Direction.CW) }, clair, 0.3f)
            // Vélo à califourchon, tourné vers la peau : la jambe le long du
            // fût et le pied sur le socle, le dos rond, la tête penchée sur le
            // ka, les mains sur la peau. Dessinée éclairée de droite, retournée elle l'est de gauche.
            pieceD("M88.6 41.4Q84 41 80 42.8L80.6 45.4Q84.4 43.8 89 44.6Z", sombre, 0.4f)
            pieceD("M79.8 44Q79 46.8 79.6 49.4L81.4 49.4Q81 46.8 81.8 44.6Z", bronze, 0.4f)
            pieceD("M77.8 50.2Q78.2 49 81.2 49L81.8 50.2Z", bronze, 0.2f)
            pieceD("M85.4 41.6Q84.2 35.6 85.4 31Q86.4 28.6 89 28.2Q92 28.2 92.6 31Q93.4 36 92.4 41.6Z", bronze, 0.7f)
            pieceD("M85.4 41.6Q84.2 35.6 85.4 31Q86 29.4 87.2 28.6Q86.4 32 86.6 35.6Q86.8 39 87.6 41.6Z", sombre, 0f)
            pieceD("M90.4 28.6Q92.2 29 92.6 31.4Q93.2 36 92 41L91 41Q92 35.4 90.4 28.6Z", clair, 0f)
            pieceD("M85.8 28.9L87.8 28.7L87.4 26.8L85.4 27.1Z", bronze, 0.2f)
            piece(Path().apply { addOval(RectF(83.2f, 22.4f, 87f, 27.2f), Path.Direction.CW) }, bronze, 0.5f)
            piece(Path().apply { addOval(RectF(84.8f, 22.7f, 86.8f, 25f), Path.Direction.CW) }, clair, 0f)
            piece(disque(84.2f, 25.2f, 0.4f), sombre, 0f)
            // Le bras de derrière, dans l'ombre, puis celui de devant.
            pieceD("M86 31Q82.6 33.6 80.6 37.6Q78.6 41.4 77 45L78.2 45.8Q80 42.2 81.8 38.4Q83.6 34.8 86.8 32.4Z", sombre, 0.2f)
            pieceD("M88 30.2Q84 31.6 81.8 35.6L83.2 36.4Q85.2 33 88.6 31.8Z", clair, 0.3f)
            pieceD("M81.8 35.6Q79.4 39 77.4 42.4L78.8 43.2Q80.8 39.6 83.2 36.4Z", bronze, 0.3f)
            piece(Path().apply { addOval(RectF(75.8f, 42f, 78.6f, 44.4f), Path.Direction.CW) }, clair, 0.2f)
            piece(Path().apply { addOval(RectF(75.6f, 45.2f, 78f, 47.2f), Path.Direction.CW) }, bronze, 0.2f)
        }

        // ------------------------------------------- le Figuier maudit en papier

        /**
         * Une racine de figuier qui coule de ([x0], [y0]) vers ([x1], [y1]) en
         * S, plus épaisse en haut, avec un liseré clair sur son flanc droit.
         */
        private fun coulee(x0: Float, y0: Float, x1: Float, y1: Float, e: Float, onde: Float, col: Int) {
            val c1x = x0 + onde; val c1y = y0 + (y1 - y0) * 0.33f
            val c2x = x1 - onde; val c2y = y0 + (y1 - y0) * 0.66f
            pieceD("M${x0 - e / 2f} ${y0}C${c1x - e / 2f} $c1y ${c2x - e * 0.4f} $c2y ${x1 - e * 0.3f} ${y1}" +
                "L${x1 + e * 0.3f} ${y1}C${c2x + e * 0.4f} $c2y ${c1x + e / 2f} $c1y ${x0 + e / 2f} ${y0}Z", col, 0.5f)
            val l = e * 0.22f
            pieceD("M${x0 + e * 0.2f} ${y0}C${c1x + e * 0.2f} $c1y ${c2x + e * 0.15f} $c2y ${x1 + e * 0.1f} ${y1}" +
                "L${x1 + e * 0.1f + l} ${y1}C${c2x + e * 0.15f + l} $c2y ${c1x + e * 0.2f + l} $c1y ${x0 + e * 0.2f + l} ${y0}Z",
                mix(col, 0xFFFFFFFF.toInt(), 0.3f), 0f)
        }

        /**
         * Le Figuier maudit en papier découpé, d'après la photo du
         * propriétaire (2026-11-08) : les murs d'une ruine de pierre enduite,
         * mangés par les racines d'un figuier étrangleur qui coulent sur eux ;
         * à gauche la porte cintrée, au fond la forêt claire, à droite un
         * second pan de mur et une grosse racine qui descend en biais ; le
         * sol de feuilles mortes et de pierres moussues que les racines
         * traversent. Balisiers pour le colibri.
         */
        fun figuierMauditPapier() {
            // La forêt claire au fond, ses troncs fins.
            pieceD("M-4 -4L104 -4L104 40L-4 40Z", papier(0xFF7AB04E.toInt()), 0f)
            val rf = Alea(2401)
            val fonds = Path()
            repeat(24) { fonds.op(disque(rf() * 104f - 2f, rf() * 24f, 3f + rf() * 3f), Path.Op.UNION) }
            piece(fonds, papier(0xFF4E8A36.toInt()), 0.3f)
            for (tx in floatArrayOf(46f, 54f, 64f, 72f)) barre(tx, 40f, tx + (rf() - 0.5f) * 3f, 0f, 0.6f, papier(0xFF8A8270.toInt()), 0.2f)
            plan(1)
            // La ruine : le mur de gauche et sa porte cintrée, le pan de droite.
            val enduit = papier(0xFF6A6650.toInt())
            val mur = Path().apply { op(chemin("M-4 4L44 8L46 18L42 56L-4 56Z"), Path.Op.UNION) }
            mur.op(chemin("M14 56L14 34Q20 26 26 34L26 56Z"), Path.Op.DIFFERENCE)
            piece(mur, enduit, 0.7f)
            taches(-2f, 44f, 54f, 10, 2402, papier(0xFF6E7A50.toInt()), { 10f })
            pieceD("M14 56L14 34Q20 26 26 34L26 56Z", papier(0xFF6E6A58.toInt()), 0f)
            pieceD("M16.4 48L16.4 38Q20 33.6 23.6 38L23.6 48Z", papier(0xFFC8C2A6.toInt()), 0f)
            pieceD("M15 56Q20 48 25 56Z", papier(0xFF8E7656.toInt()), 0f)
            pieceD("M70 56L72 26L84 22L92 30L104 26L104 56Z", papier(0xFF7A765C.toInt()), 0.7f)
            pieceD("M80 56L80 40Q83 36.6 86 40L86 56Z", papier(0xFF3E3E32.toInt()), 0f)
            taches(72f, 102f, 54f, 7, 2403, papier(0xFF6E7A50.toInt()), { 28f })
            // Les racines qui coulent sur les murs, claires et grises.
            val racines = intArrayOf(papier(0xFFB8B4A2.toInt()), papier(0xFF9C9684.toInt()), papier(0xFFCCC8B6.toInt()))
            for ((x0, x1, e) in arrayOf(Triple(2f, 4f, 2.6f), Triple(8f, 11f, 1.6f), Triple(12f, 13.4f, 2.2f), Triple(28f, 27f, 2.6f),
                    Triple(33f, 36f, 1.8f), Triple(38f, 41f, 2.8f), Triple(6f, 2f, 1.2f), Triple(24f, 30f, 1f)))
            {
                val col = racines[(rf() * 3f).toInt().coerceAtMost(2)]
                val y0 = 2f + rf() * 6f
                coulee(x0, y0, x1, 56f, e, (rf() - 0.5f) * 10f, col)
                // Une ou deux racines filles qui s'en écartent en biais, comme une coulée qui se divise.
                repeat(1 + (rf() * 2f).toInt()) {
                    val yb = 14f + rf() * 26f
                    val xb = x0 + (x1 - x0) * (yb - y0) / (56f - y0)
                    coulee(xb, yb, xb + (rf() - 0.5f) * 16f, 56f, e * 0.5f, (rf() - 0.5f) * 6f, col)
                }
            }
            for ((x0, x1) in arrayOf(74f to 76f, 90f to 88f, 97f to 100f)) coulee(x0, 24f, x1, 56f, 1.4f, (rf() - 0.5f) * 4f, racines[1])
            plan(2)
            // Le sol de feuilles mortes, les pierres moussues, les racines qui le traversent.
            pieceD("M-4 55L104 54L104 68L-4 68Z", papier(0xFF7A6448.toInt()), 0.5f)
            repeat(40) {
                val x = rf() * 104f - 2f
                val y = 56f + rf() * 10f
                feuillePliee(x, y, 1f + rf() * 0.6f, rf() * 360f, papier(if (rf() < 0.5f) 0xFF9A7A50.toInt() else 0xFF6A5236.toInt()), 0.45f)
            }
            for ((gx, gy, gr) in arrayOf(Triple(84f, 58f, 2.6f), Triple(92f, 61f, 2.2f), Triple(4f, 62f, 2f)))
                galet(gx, gy, gr, papier(0xFF7E8A5A.toInt()))
            // La grosse racine qui descend en biais à droite puis serpente au sol.
            val grosse = papier(0xFF9A8268.toInt())
            // Un tronc-racine noueux : des disques le long d'une courbe, de
            // rayon inégal, et un liseré clair sur le flanc éclairé. D'une
            // seule bande droite, il se lisait comme une planche.
            val noueux = Path()
            val lumiere = Path()
            val pts = floatArrayOf(72f, 10f, 70f, 30f, 90f, 38f, 93f, 50f)
            var u = 0f
            while (u <= 1f) {
                val a = 1f - u
                val x = a * a * a * pts[0] + 3f * a * a * u * pts[2] + 3f * a * u * u * pts[4] + u * u * u * pts[6]
                val y = a * a * a * pts[1] + 3f * a * a * u * pts[3] + 3f * a * u * u * pts[5] + u * u * u * pts[7]
                val r = 2.2f + u * 0.8f + sin(u * 23f) * 0.5f
                noueux.op(disque(x, y, r), Path.Op.UNION)
                lumiere.op(disque(x + r * 0.45f, y - r * 0.2f, r * 0.4f), Path.Op.UNION)
                u += 0.025f
            }
            piece(noueux, grosse, 0.8f)
            piece(lumiere, mix(grosse, 0xFFFFFFFF.toInt(), 0.25f), 0f)
            coulee(92f, 49f, 104f, 60f, 3.6f, 2f, grosse)
            for ((x0, y0, x1, y1) in arrayOf(floatArrayOf(90f, 50f, 70f, 63f), floatArrayOf(93f, 52f, 99f, 66f), floatArrayOf(30f, 57f, 4f, 64f),
                    floatArrayOf(28f, 57f, 46f, 66f), floatArrayOf(40f, 56f, 60f, 60f)))
                coulee(x0, y0, x1, y1, 1.6f, (rf() - 0.5f) * 4f, grosse)
            balisier(10f, 63f, 13f, feuille())
            balisier(92f, 63f, 12f, feuille())
        }

        /** Les Roches gravées : la forêt de Trois-Rivières, les visages taillés dans le rocher. */
        fun rochesGravees() {
            if (PAPIER) { rochesGraveesPapier(); return }
            crete(24f, 2.2f, 221, P.loin)
            bosquet(-2f, 102f, 26f, 2.6f, 222, mix(P.loin, P.milieu, 0.5f))
            plan(1)
            forme("M0 66L0 32C30 28 70 30 100 32L100 66Z", P.milieu)
            val tronc = mix(P.sol, P.pierre, 0.2f)
            for (x in floatArrayOf(8f, 22f, 37f, 90f)) rect(x, 34f, 1.1f, 20f, tronc)
            bosquet(-2f, 102f, 34f, 3.2f, 223, mix(P.milieu, P.pres, 0.35f))
            plan(2)
            rect(0f, 53f, 100f, 13f, P.pres)
            val roc = mix(P.pierre, P.pres, 0.25f)
            val grave = mix(roc, P.sol, 0.6f)
            forme("M14 60Q12 47 26 42.5Q40 40.5 47 48Q51 56 47 61Z", roc)
            forme("M56 61Q54 51 64 46.6Q78 44.6 84 52Q86.4 58 82 62Z", roc)
            // Les visages des Roches gravées : un ovale, des yeux cerclés, une
            // bouche ronde. Pas de sourire : il en ferait un émoji.
            trait("M31 45.2C35 45.2 35.6 54.8 31 54.8C26.4 54.8 27 45.2 31 45.2Z", grave, 0.55f)
            anneau(29.4f, 48.8f, 0.9f, grave, 0.45f); anneau(32.6f, 48.8f, 0.9f, grave, 0.45f)
            anneau(31f, 52.4f, 0.8f, grave, 0.45f)
            trait("M31 45.2L31 43.2M28.4 46.4L27.2 44.8M33.6 46.4L34.8 44.8", grave, 0.45f)
            anneau(70f, 53f, 3.2f, grave, 0.5f)
            anneau(68.8f, 52.4f, 0.6f, grave, 0.4f); anneau(71.2f, 52.4f, 0.6f, grave, 0.4f); anneau(70f, 54.6f, 0.5f, grave, 0.4f)
            for (k in 0 until 7) {
                val a = PI.toFloat() * (1.05f + k * 0.15f)
                ligne(70f + cos(a) * 3.6f, 53f + sin(a) * 3.6f, 70f + cos(a) * 5.4f, 53f + sin(a) * 5.4f, grave, 0.45f)
            }
            bosquet(10f, 20f, 61f, 2f, 224, mix(P.pres, P.milieu, 0.4f))
            bosquet(84f, 96f, 62f, 2f, 225, mix(P.pres, P.milieu, 0.4f))
            balisier(8f, 64f, 11f, mix(P.pres, P.milieu, 0.3f))
            balisier(93f, 64f, 12f, mix(P.pres, P.milieu, 0.3f))
        }

        /** La place de la Victoire : les maisons à balcons, les sabliers, la statue de Vélo et son ka. */
        fun placeVictoire() {
            if (PAPIER) { placeVictoirePapier(); return }
            val r = Alea(231)
            plan(1)
            val pastels = intArrayOf(0xFFF2D8A7.toInt(), 0xFFBFDCCF.toInt(), 0xFFF0C2B0.toInt(), 0xFFC9D6EC.toInt())
            val balcon = mix(P.pierre, P.pres, 0.55f)
            var x = -2f
            var k = 0
            while (x < 100f) {
                val w = 9f + r() * 3f
                val h = 13f + r() * 4f
                rect(x, 40f - h, w, h, mix(P.pierre, pastels[k % 4], if (P.nuit) 0.12f else 0.45f))
                forme("M${x - 0.6f} ${40f - h}L${x + w + 0.6f} ${40f - h}L${x + w - 1f} ${37.6f - h}L${x + 1f} ${37.6f - h}Z", P.toit)
                rect(x, 40f - h * 0.55f, w, 0.5f, balcon)
                var b = x + 0.4f
                while (b < x + w) { rect(b, 40f - h * 0.55f - 1.6f, 0.2f, 1.6f, balcon); b += 1f }
                var f = x + 1.2f
                while (f < x + w - 1.4f) {
                    fenetre(f, 40f - h * 0.85f, 1.3f, 2.6f, r)
                    fenetre(f, 40f - h * 0.4f, 1.3f, 3f, r)
                    f += 2.8f
                }
                x += w + 0.4f
                k++
            }
            bosquet(-2f, 102f, 42f, 4.2f, 232, P.milieu)
            plan(2)
            rect(0f, 48f, 100f, 18f, mix(P.pierre, P.sol, 0.45f))
            val socle = mix(P.pierre, P.pres, 0.15f)
            rect(66f, 49f, 14f, 8f, socle)
            rect(65f, 56.5f, 16f, 1.6f, mix(socle, P.pres, 0.3f))
            rect(65.4f, 48.4f, 15.2f, 1f, mix(socle, 0xFFFFFFFF.toInt(), 0.2f))
            // La statue de Vélo, Marcel Lollia, le tambouyé : assis à
            // califourchon sur son ka couché, les mains sur la peau.
            val bronze = mix(0xFF4A3A2A.toInt(), P.pres, if (P.nuit) 0.3f else 0f)
            val reflet = mix(bronze, 0xFFFFFFFF.toInt(), 0.18f)
            forme("M68.6 45.2Q72 43 75.8 43L76.6 47.6Q72.6 48.6 69.2 48.4Z", bronze)
            ovale(76.2f, 45.3f, 1.1f, 2.3f, reflet)
            trait("M70.6 44.6L70.8 48.4M73.4 43.8L73.6 48.2", reflet, 0.3f)
            trait("M71.4 44.4L71.6 38.6", bronze, 2.4f)
            rond(71.9f, 36.9f, 1.5f, bronze)
            trait("M72.2 39.6Q74.8 40.6 75.8 43.6M71.6 40Q73.4 42.2 75.4 44.4", bronze, 0.8f)
            trait("M70.8 45.2L69.8 48.2L71.4 48.4", bronze, 0.9f)
            for (lx in floatArrayOf(12f, 40f, 92f)) {
                rect(lx, 38f, 0.5f, 12f, P.sol)
                if (P.allume) rond(lx + 0.25f, 37.6f, 3f, P.fenetre, 0.25f)
                rond(lx + 0.25f, 37.6f, 0.9f, if (P.allume) P.fenetre else mix(P.pierre, P.pres, 0.3f))
            }
        }

        /** Un figuier maudit : les racines devenues tronc, les racines aériennes qui pendent. */
        fun figuierMaudit() {
            if (PAPIER) { figuierMauditPapier(); return }
            val r = Alea(241)
            crete(34f, 1.8f, 242, P.loin)
            plan(1)
            forme("M0 66L0 42C24 38 50 41 76 38C88 37 96 39 100 40L100 66Z", P.milieu)
            plan(2)
            rect(0f, 58f, 100f, 8f, P.pres)
            val bois = mix(P.pierre, P.sol, 0.55f)
            val clair = mix(bois, P.pierre, 0.35f)
            for (k in 0 until 7) {
                val x0 = 40f + k * 3.2f + r() * 1.2f
                val x1 = 47f + k * 1.2f
                trait("M$x0 60Q${x0 + (r() - 0.5f) * 8f} 45 $x1 30", if (k % 2 == 0) bois else clair, 1.6f)
            }
            forme("M34 61Q40 55 44 52L56 52Q60 55 66 61Z", bois)
            trait("M50 32Q40 26 24 24M51 31Q62 24 78 22M50 30Q50 22 46 14", bois, 1.4f)
            bosquet(14f, 86f, 22f, 4.6f, 243, P.pres)
            bosquet(22f, 78f, 15f, 4f, 244, mix(P.pres, P.milieu, 0.35f))
            bosquet(34f, 66f, 10f, 3.4f, 245, mix(P.pres, P.milieu, 0.2f))
            balisier(10f, 62f, 11f, mix(P.pres, P.milieu, 0.3f))
            balisier(90f, 62f, 12f, mix(P.pres, P.milieu, 0.3f))
            val liane = mix(bois, P.pres, 0.2f)
            for (k in 0 until 14) {
                val x = 24f + k * 4f + r() * 2f
                ligne(x, 22f + r() * 4f, x + (r() - 0.5f) * 1.2f, 42f + r() * 16f, liane, 0.35f)
            }
        }

        // ------------------------------------------------------ flore et faune des trente lieux

        /** La couleur des feuilles qui bordent un lieu de forêt, pour les balisiers. */
        fun feuille() = mix(P.pres, P.milieu, 0.3f)

        /** Le lagon : l'eau de l'heure, éclaircie vers le turquoise le jour. */
        fun lagon() = mix(P.eau, 0xFF8FE0D2.toInt(), if (P.nuit) 0.08f else 0.4f)

        /** Un anthurium : la spathe rouge en cœur, le spadice jaune, une feuille. */
        fun anthurium(x: Float, y: Float) {
            val tige = feuille()
            trait("M$x ${y}Q${x + 0.4f} ${y - 2f} ${x + 0.2f} ${y - 3.4f}", tige, 0.3f)
            ovale(x - 1.4f, y - 1.6f, 0.9f, 1.6f, tige)
            forme("M${x + 0.2f} ${y - 3.4f}Q${x - 1.6f} ${y - 4.8f} ${x - 0.8f} ${y - 5.6f}Q${x + 0.2f} ${y - 5.2f} " +
                "${x + 0.2f} ${y - 4.6f}Q${x + 0.2f} ${y - 5.2f} ${x + 1.2f} ${y - 5.6f}Q${x + 2f} ${y - 4.8f} ${x + 0.2f} ${y - 3.4f}Z",
                duJour(0xFFC8282E.toInt()))
            trait("M${x + 0.2f} ${y - 4.4f}L${x + 0.9f} ${y - 6f}", duJour(0xFFE8C440.toInt()), 0.3f)
        }

        /** Un raisinier bord de mer : des feuilles rondes serrées, une qui rougit. */
        fun raisinier(x: Float, y: Float, s: Float) {
            val vert = duJour(0xFF6E9A44.toInt())
            for ((dx, dy) in arrayOf(0f to 0f, -1.4f to 0.5f, 1.4f to 0.5f, -0.7f to -1.1f, 0.8f to -1f, 0f to 1f))
                rond(x + dx * s, y + dy * s, 0.95f * s, vert)
            rond(x + 0.8f * s, y - s, 0.5f * s, duJour(0xFFB0603A.toInt()))
        }

        /** Un amandier pays : le tronc et ses étages de feuillage à plat, une feuille rouge. */
        fun amandier(x: Float, y: Float, h: Float) {
            trait("M$x ${y}L$x ${y - h}M$x ${y - h * 0.45f}L${x - h * 0.4f} ${y - h * 0.6f}M$x ${y - h * 0.7f}L${x + h * 0.4f} ${y - h * 0.82f}",
                mix(P.sol, P.pierre, 0.2f), 0.7f)
            val vert = duJour(0xFF4E7A3A.toInt())
            ovale(x - h * 0.4f, y - h * 0.62f, h * 0.38f, h * 0.07f, vert)
            ovale(x + h * 0.4f, y - h * 0.84f, h * 0.36f, h * 0.07f, vert)
            ovale(x, y - h, h * 0.44f, h * 0.09f, vert)
            ovale(x + h * 0.2f, y - h * 0.98f, h * 0.12f, h * 0.05f, duJour(0xFFB8502E.toInt()))
        }

        /** Un palétuvier dressé sur ses racines échasses. */
        fun paletuvier(x: Float, y: Float) {
            trait("M$x ${y - 6f}L$x ${y - 14f}M$x ${y - 6f}Q${x - 3f} ${y - 4f} ${x - 4f} ${y}M$x ${y - 6f}Q${x - 1f} ${y - 3f} ${x - 1.4f} ${y}" +
                "M$x ${y - 6f}Q${x + 1.4f} ${y - 3f} ${x + 1.6f} ${y}M$x ${y - 6f}Q${x + 3.4f} ${y - 4f} ${x + 4.4f} $y",
                mix(P.sol, P.pierre, 0.2f), 0.5f)
            ovale(x, y - 15f, 6f, 3.4f, duJour(0xFF3E6A36.toInt()))
            ovale(x - 3f, y - 13.4f, 3.4f, 2f, duJour(0xFF4E7A3A.toInt()))
            ovale(x + 3f, y - 13.6f, 3.4f, 2f, duJour(0xFF4E7A3A.toInt()))
        }

        /** Un bananier : le stipe, et ses grandes feuilles retombantes. */
        fun bananier(x: Float, y: Float, h: Float) {
            rect(x - 0.7f, y - h * 0.55f, 1.4f, h * 0.55f, duJour(0xFF7A8A4A.toInt()))
            val vert = duJour(0xFF5E8A3A.toInt())
            val ax = x
            val ay = y - h * 0.55f
            for ((angle, l) in arrayOf(-48f to 0.5f, -22f to 0.55f, 12f to 0.55f, 42f to 0.5f, 70f to 0.4f)) {
                c.save()
                c.rotate(angle, ax, ay)
                ovale(ax, ay - h * l / 2f, 1.3f, h * l / 2f, vert)
                c.restore()
            }
        }

        /** Des fleurs jaunes de coquelicot des Antilles, semées dans l'herbe. */
        fun turnera(vararg xy: Float) {
            val jaune = duJour(0xFFE8C440.toInt())
            var i = 0
            while (i < xy.size) { rond(xy[i], xy[i + 1], 0.45f, jaune); i += 2 }
        }

        /** Un grand tronc de forêt, avec ses contreforts au pied. */
        fun tronc(x: Float, w: Float, haut: Float, pied: Float) {
            val bois = mix(P.pierre, P.sol, 0.65f)
            forme("M$x ${haut}L${x + w} ${haut}L${x + w + 0.4f} ${pied - 14f}Q${x + w + 2f} ${pied - 6f} ${x + w + 6f} ${pied}" +
                "L${x - 6f} ${pied}Q${x - 2f} ${pied - 6f} ${x - 0.4f} ${pied - 14f}Z", bois)
            trait("M${x + w / 2f} ${pied - 12f}L${x + w / 2f - 1f} $pied", mix(bois, P.sol, 0.5f), 0.4f)
        }

        /** Une libellule au repos dans l'air : le corps, deux paires d'ailes. Le jour seulement. */
        fun libellule(x: Float, y: Float) {
            if (P.nuit) return
            ligne(x - 1.6f, y, x + 1.6f, y, duJour(0xFF3B6E8C.toInt()), 0.35f)
            trait("M${x - 0.4f} ${y - 1f}L${x + 0.4f} ${y + 1f}M${x + 0.4f} ${y - 1f}L${x - 0.4f} ${y + 1f}", blanc(), 0.3f, 0.8f)
        }

        /** Un pélican brun posé, le bec le long du cou. */
        fun pelicanPose(x: Float, y: Float) {
            val plume = duJour(0xFF6E6258.toInt())
            ovale(x, y, 2.4f, 1.3f, plume)
            ovale(x + 1.6f, y - 1.9f, 0.8f, 0.7f, duJour(0xFFD9CBB0.toInt()))
            trait("M${x + 1.2f} ${y - 1f}L${x + 1.5f} ${y - 1.5f}", plume, 0.45f)
            trait("M${x + 2.2f} ${y - 1.9f}L${x + 4.4f} ${y - 1f}", duJour(0xFFC9A24A.toInt()), 0.45f)
        }

        /** Un anoli au soleil, le fanon orange déployé. Le jour seulement. */
        fun anoli(x: Float, y: Float) {
            if (P.nuit) return
            val vert = duJour(0xFF5E9A3A.toInt())
            forme("M${x - 2f} ${y}Q$x ${y - 0.7f} ${x + 2f} ${y}Q$x ${y + 0.5f} ${x - 2f} ${y}Z", vert)
            trait("M${x - 2f} ${y}Q${x - 4f} ${y + 0.4f} ${x - 5f} ${y + 1.6f}", vert, 0.35f)
            rond(x + 1.4f, y + 0.6f, 0.5f, duJour(0xFFE07A2E.toInt()))
        }

        /** Le pic de la Guadeloupe accroché à un tronc. Le jour seulement. */
        fun pic(x: Float, y: Float) {
            if (P.nuit) return
            val noir = duJour(0xFF26262C.toInt())
            ovale(x, y, 0.8f, 1.7f, noir)
            rond(x + 0.2f, y - 2f, 0.7f, noir)
            trait("M${x + 0.7f} ${y - 2f}L${x + 1.8f} ${y - 2.2f}", duJour(0xFF4A4A4A.toInt()), 0.25f)
            rond(x + 0.4f, y - 1.2f, 0.4f, duJour(0xFF7A2E2E.toInt()))
        }

        /** Un agouti qui traverse. Le jour seulement. */
        fun agouti(x: Float, y: Float) {
            if (P.nuit) return
            val poil = duJour(0xFF9A6A3A.toInt())
            ovale(x, y, 2.4f, 1.2f, poil)
            ovale(x + 2.2f, y - 0.5f, 0.9f, 0.7f, poil)
            trait("M${x - 1.4f} ${y + 0.8f}L${x - 1.6f} ${y + 2f}M${x + 1.2f} ${y + 0.8f}L${x + 1.4f} ${y + 2f}",
                duJour(0xFF5C3E22.toInt()), 0.3f)
        }

        /** Le raton laveur, la queue annelée. La nuit seulement, quand il sort. */
        fun raton(x: Float, y: Float) {
            if (!P.nuit) return
            val gris = 0xFF6E7488.toInt()
            ovale(x, y, 2.2f, 1.3f, gris)
            ovale(x + 2f, y - 0.7f, 0.9f, 0.8f, 0xFF7C8296.toInt())
            ovale(x + 2.3f, y - 0.8f, 0.45f, 0.25f, 0xFF1C2030.toInt())
            trait("M${x - 2f} ${y}Q${x - 3.6f} ${y - 0.4f} ${x - 4.2f} ${y - 1.6f}", gris, 0.7f)
            trait("M${x - 3f} ${y - 0.2f}L${x - 3f} ${y + 0.3f}M${x - 3.8f} ${y - 0.9f}L${x - 3.6f} ${y - 0.5f}", 0xFF2A2E40.toInt(), 0.3f)
        }

        /** Un petit passereau perché, de la couleur dite. Le jour seulement. */
        fun oiselet(x: Float, y: Float, col: Int) {
            if (P.nuit) return
            val plume = duJour(col)
            ovale(x, y, 1.1f, 0.8f, plume)
            rond(x + 0.9f, y - 0.6f, 0.5f, plume)
            trait("M${x - 1f} ${y}L${x - 2f} ${y + 0.6f}", plume, 0.4f)
        }

        /** Une baleine à bosse au large : la queue qui sonde, le souffle. Le jour seulement. */
        fun baleine(x: Float, y: Float) {
            queue(x, y)
            souffle(x, y)
        }

        /**
         * La queue de la baleine qui sonde, l'écume à son pied. Le jour
         * seulement. Assez grande pour se lire dans la fenêtre : à 5 unités de
         * large au ras de l'horizon, elle passait pour une mouette.
         */
        fun queue(x: Float, y: Float) {
            if (P.nuit) return
            val s = 1.9f
            ovale(x, y + 0.2f, 3.2f * s, 0.6f, blanc(), 0.7f)
            forme("M$x ${y}L${x - 0.4f * s} ${y - 1.6f * s}Q${x - 2f * s} ${y - 2.4f * s} ${x - 2.6f * s} ${y - 1.8f * s}" +
                "Q${x - 1f * s} ${y - 1.6f * s} ${x - 0.2f * s} ${y - 1.4f * s}L${x + 0.2f * s} ${y - 1.4f * s}" +
                "Q${x + 1f * s} ${y - 1.6f * s} ${x + 2.6f * s} ${y - 1.8f * s}Q${x + 2f * s} ${y - 2.4f * s} ${x + 0.4f * s} ${y - 1.6f * s}" +
                "L${x + 0.6f * s} ${y}Z", duJour(0xFF3A4652.toInt()))
            ovale(x - 1.4f * s, y - 1.85f * s, 0.9f, 0.25f, duJour(0xFFE8ECEF.toInt()), 0.5f)
        }

        /** Le dos qui affleure et le souffle, un panache haut et blanc. Le jour seulement. */
        fun souffle(x: Float, y: Float) {
            if (P.nuit) return
            ovale(x - 9f, y + 0.1f, 4.4f, 0.9f, duJour(0xFF3A4652.toInt()))
            ovale(x - 9f, y + 0.5f, 5.6f, 0.5f, blanc(), 0.6f)
            ovale(x - 10.6f, y - 2.4f, 0.9f, 2.4f, blanc(), 0.6f)
            ovale(x - 10.6f, y - 4.8f, 2.2f, 1.4f, blanc(), 0.5f)
            ovale(x - 10.6f, y - 5.6f, 1.4f, 0.9f, blanc(), 0.4f)
        }

        /**
         * Une nappe de brume au bord fondu : quatre ovales de plus en plus
         * petits, chacun très léger. Un seul ovale opaque se lisait comme une
         * soucoupe posée sur le paysage.
         */
        fun brume(x: Float, y: Float, rx: Float, ry: Float, o: Float = 0.3f) {
            for (k in 0 until 4) {
                val f = 1f - k * 0.2f
                ovale(x, y, rx * f, ry * (0.6f + f * 0.4f), blanc(), o / 3f)
            }
        }

        /** Le feuillage qui déborde du bord d'une paroi, de [y0] à [y1], du côté [sens]. */
        fun bordFeuillu(x: Float, y0: Float, y1: Float, sens: Float, graine: Int, col: Int) {
            val r = Alea(graine)
            var y = y0
            while (y < y1) {
                val t = 1.6f + r() * 1.6f
                rond(x + sens * (r() * 2.4f - 0.6f), y, t, col)
                y += 2.2f + r() * 2.2f
            }
        }

        /** Une ligne d'écume, un ressac. */
        fun ecume(d: String) = trait(d, blanc(), 0.5f, 0.7f)

        /** L'iguane des Petites Antilles, gris-vert, couché ; [tete] lève ou baisse la tête. */
        fun iguane(x: Float, y: Float, tete: Float = 0f, s: Float = 1f) {
            // L'iguane des Petites Antilles d'après la photo du propriétaire
            // (2026-11-08) : corps gris anthracite, tête claire, crête dorsale.
            fun q(dx: Float, dy: Float) = "${x + dx * s} ${y + dy * s}"
            val peau = papier(0xFF2E3234.toInt())
            val flanc = papier(0xFF4A5052.toInt())
            val tet = papier(0xFFB8B4A8.toInt())
            // La queue, qui retombe en s'effilant derrière la branche.
            pieceD("M${q(0.6f, -1.6f)}Q${q(-5f, -1.2f)} ${q(-10f, 2.8f)}L${q(-10.4f, 3.2f)}Q${q(-5f, 0.6f)} ${q(0.6f, 0.4f)}Z", peau, 0.4f)
            // La patte arrière, repliée.
            pieceD("M${q(0.6f, -0.4f)}L${q(-1.2f, 1.6f)}L${q(0.4f, 2f)}L${q(2.4f, 0f)}Z", peau, 0.3f)
            // Le corps, son flanc plus clair.
            pieceD("M${q(0f, 0f)}Q${q(0.4f, -2.6f)} ${q(4f, -2.8f)}Q${q(8f, -3f)} ${q(10f, -2f)}L${q(10.4f, 0.4f)}Q${q(5f, 1.2f)} ${q(0f, 0f)}Z", peau, 0.5f)
            pieceD("M${q(1.4f, -0.8f)}Q${q(5f, -1.8f)} ${q(9f, -1.2f)}L${q(9f, -0.6f)}Q${q(5f, -0.8f)} ${q(1.4f, -0.2f)}Z", flanc, 0f)
            // La crête dorsale, en dents de scie, de la nuque à la queue.
            val crete = StringBuilder("M${q(10f, -2.3f + tete * 0.3f)}")
            var cx = 10f
            while (cx > 0.6f) {
                val dos = -2.4f - 0.4f * kotlin.math.sin((cx / 10f) * PI.toFloat())
                crete.append("L${q(cx - 0.35f, dos - (if (cx > 6f) 0.9f else 0.6f))}L${q(cx - 0.7f, dos)}")
                cx -= 0.7f
            }
            crete.append("L${q(0.6f, -1.6f)}Z")
            pieceD(crete.toString(), papier(0xFF4E5654.toInt()), 0f)
            // La patte avant, droite, les doigts sur la branche.
            pieceD("M${q(7.4f, -0.6f)}L${q(7.8f, 2f)}L${q(9.2f, 2f)}L${q(8.8f, -0.6f)}Z", peau, 0.3f)
            // Le fanon sous le cou, puis la tête claire et son œil.
            pieceD("M${q(9.6f, -0.2f)}Q${q(11f, 1.6f + tete)} ${q(12.6f, 0.2f + tete)}L${q(11.4f, -0.6f + tete)}Z", flanc, 0.2f)
            pieceD("M${q(9.6f, -2.4f + tete)}Q${q(11f, -3.4f + tete)} ${q(13f, -2.6f + tete)}Q${q(14.6f, -2f + tete)} ${q(14.4f, -0.8f + tete)}" +
                "Q${q(12.4f, 0f + tete)} ${q(9.8f, -0.4f + tete)}Z", tet, 0.5f)
            piece(disque(x + 12.2f * s, y + (-1.9f + tete) * s, 0.28f * s), papier(0xFF2A2420.toInt()), 0f)
        }

        /**
         * Une écrevisse de rivière, de profil vers la droite : la queue en
         * anneaux et son éventail, la carapace, les deux pinces en avant, les
         * longues antennes. [repli] de 0 à 1 replie la queue dessous, le
         * geste de fuite ; [pas] fait marcher les pattes.
         */
        fun ecrevisse(x: Float, y: Float, s: Float = 1f, repli: Float = 0f, pas: Float = 0f) {
            fun q(dx: Float, dy: Float) = "${x + dx * s} ${y + dy * s}"
            val corps = papier(0xFF7A5634.toInt())
            val sombre = papier(0xFF4E3622.toInt())
            val clair = papier(0xFFA87E52.toInt())
            // Les pattes.
            for (k in 0 until 3) {
                val px = -0.2f + k * 0.5f
                val b = sin(pas * 6f + k * 2f) * 0.25f
                barre(x + px * s, y - 0.1f * s, x + (px + b) * s, y + 0.55f * s, 0.12f * s, sombre, 0f)
            }
            // La queue : trois anneaux puis l'éventail, repliés dessous en fuite.
            val ry = repli * 0.7f
            pieceD("M${q(-0.4f, -0.8f)}Q${q(-1.6f, -0.9f + ry * 0.4f)} ${q(-2.3f, -0.4f + ry)}L${q(-2.2f, 0.1f + ry)}Q${q(-1.4f, 0.1f + ry * 0.5f)} ${q(-0.4f, 0f)}Z", corps, 0.3f)
            for (k in 1..2) {
                val ax = -0.4f - k * 0.65f
                barre(x + ax * s, y + (-0.85f + ry * k * 0.25f) * s, x + ax * s, y + (0.05f + ry * k * 0.25f) * s, 0.1f * s, sombre, 0f)
            }
            pieceD("M${q(-2.2f, -0.5f + ry)}L${q(-3f, -0.8f + ry * 1.3f)}L${q(-3.1f, 0.2f + ry * 1.3f)}L${q(-2.2f, 0.1f + ry)}Z", clair, 0.2f)
            // La carapace.
            piece(Path().apply { addOval(RectF(x - 0.7f * s, y - 1f * s, x + 1.5f * s, y + 0.15f * s), Path.Direction.CW) }, corps, 0.4f)
            piece(Path().apply { addOval(RectF(x - 0.4f * s, y - 0.95f * s, x + 1.2f * s, y - 0.5f * s), Path.Direction.CW) }, clair, 0f)
            // Les pinces, l'une haute, l'autre basse.
            for ((dy, ouv) in arrayOf(-0.55f to -0.35f, -0.15f to 0.3f)) {
                barre(x + 1.3f * s, y + dy * s, x + 2.4f * s, y + (dy + ouv * 0.4f) * s, 0.22f * s, corps, 0.2f)
                piece(Path().apply { addOval(RectF(x + 2.2f * s, y + (dy + ouv * 0.4f - 0.3f) * s, x + 3.4f * s, y + (dy + ouv * 0.4f + 0.2f) * s), Path.Direction.CW) },
                    sombre, 0.2f)
            }
            // Les antennes, longues, qui ondulent avec le pas.
            val a = sin(pas * 2f) * 0.3f
            pieceD("M${q(1.4f, -0.8f)}Q${q(3.2f, -2.4f + a)} ${q(5.2f, -1.6f - a)}L${q(5.2f, -1.5f - a)}Q${q(3.2f, -2.2f + a)} ${q(1.4f, -0.7f)}Z", sombre, 0f)
            pieceD("M${q(1.4f, -0.7f)}Q${q(3f, -1.6f - a)} ${q(4.8f, -0.4f + a)}L${q(4.8f, -0.3f + a)}Q${q(3f, -1.4f - a)} ${q(1.4f, -0.6f)}Z", sombre, 0f)
            piece(disque(x + 1.25f * s, y - 0.8f * s, 0.14f * s), papier(0xFF1A1410.toInt()), 0f)
        }

        /**
         * Une bête à l'instant [t]. [immobile] : la pose de repos, celle des
         * vignettes, à l'endroit exact de la table. Chaque mouvement est celui
         * de l'espèce : le touloulou file de côté, l'anoli fait ses pompes et
         * déploie son fanon, le pic tape, l'agouti traverse, la baleine souffle
         * puis sonde, l'iguane hoche la tête.
         */
        fun bete(b: Bete, t: Float, i: Int, immobile: Boolean) = aplat {
            val ph = i * 1.7f
            val x = b.x
            val y = b.y
            voile = 1f
            if (immobile) {
                when (b.espece) {
                    Espece.TOULOULOU -> touloulou(x, y)
                    Espece.TORTUE -> tortue(x, y, b.taille)
                    Espece.PELICAN_PIEU -> { c.save(); c.scale(b.taille, b.taille, x, y); pelican(x, y); c.restore() }
                    Espece.PELICAN -> pelicanPose(x, y)
                    Espece.PELICAN_QUAI -> if (!P.nuit) pelican(x, y)
                    Espece.LIBELLULE -> libellule(x, y)
                    Espece.ANOLI -> anoli(x, y)
                    Espece.PIC -> pic(x, y)
                    Espece.AGOUTI -> agouti(x, y)
                    Espece.RATON -> raton(x, y)
                    Espece.SIFFLEUR -> oiselet(x, y, COULEUR_SIFFLEUR)
                    Espece.PARULINE -> oiselet(x, y, COULEUR_PARULINE)
                    Espece.BALEINE -> baleine(x, y)
                    Espece.ECREVISSE -> ecrevisse(x, y, b.taille)
                    Espece.IGUANE -> iguane(x, y, 0f, b.taille)
                    Espece.BARQUE -> barque(x, y)
                    Espece.FANIONS -> fanions(0f)
                    Espece.TOURTERELLE -> if (!P.nuit) tourterelle(x, y, if (x < 50f) 1f else -1f, 0f, b.taille)
                    Espece.PLANTE -> jardiniere(x, y, 0f, false, b.taille)
                    Espece.FLEURS -> jardiniere(x, y, 0f, true, b.taille)
                    Espece.MANGUE -> mangue(x, y, 0f, 0f, 0f, true)
                    Espece.BATEAU -> bateauPlongee(x, y, 0f, 0f)
                    Espece.ABEILLE -> if (!P.nuit) abeille(x, y, 1f, 1f, b.taille)
                    Espece.LIANE -> liane(x, y, 0f)
                }
                return@aplat
            }
            when (b.espece) {
                // Un pas de côté, une pause, puis le retour.
                Espece.TOULOULOU -> {
                    val u = (t + ph) % 6f
                    val dx = when {
                        u < 2.4f -> 0f
                        u < 2.8f -> (u - 2.4f) / 0.4f * 2.4f
                        u < 4.8f -> 2.4f
                        u < 5.3f -> 2.4f * (1f - (u - 4.8f) / 0.5f)
                        else -> 0f
                    }
                    touloulou(x + dx, y)
                }
                // Elle nage devant elle sur une boucle lente, aplatie par la
                // perspective de l'eau, la tête dans le sens de la marche, en
                // battant des nageoires avant.
                Espece.TORTUE -> {
                    val a = t * 0.16f + ph
                    val cap = atan2(-sin(a) * 1f, cos(a) * 4f) * 57.3f + 90f
                    tortue(x + sin(a) * 4f, y + cos(a) * 1f, b.taille, cap, sin(t * 1.7f + ph) * 22f)
                }
                Espece.PELICAN_PIEU -> {
                    c.save(); c.scale(b.taille, b.taille, x, y)
                    pelican(x, y + sin(t * 0.8f + ph) * 0.15f)
                    c.restore()
                }
                // Posé sur l'eau, il monte et descend avec la houle.
                Espece.PELICAN -> pelicanPose(x + sin(t * 0.15f + ph) * 1.5f, y + sin(t * 1.2f + ph) * 0.35f)
                Espece.PELICAN_QUAI -> if (!P.nuit) pelican(x, y + sin(t * 0.7f + ph) * 0.12f)
                Espece.LIBELLULE -> libellule(
                    x + sin(t * 1.3f + ph) * 3f + sin(t * 3.1f) * 0.6f,
                    y + cos(t * 1.7f + ph) * 1.2f
                )
                Espece.ANOLI -> {
                    val u = (t + ph) % 4f
                    val pompe = if (u < 1.2f) abs(sin(u / 1.2f * PI.toFloat() * 3f)) * 0.4f else 0f
                    if (P.nuit) return@aplat
                    anoli(x, y - pompe)
                    // Le fanon s'ouvre pendant les pompes.
                    if (u < 1.2f) rond(x + 1.4f, y + 0.7f - pompe, 0.5f + pompe * 1.4f, duJour(0xFFE07A2E.toInt()))
                }
                Espece.PIC -> {
                    val u = (t + ph) % 3f
                    pic(x - if (u < 0.8f) abs(sin(u * 18f)) * 0.35f else 0f, y)
                }
                // Il traverse la route, puis la forêt le cache un moment.
                Espece.AGOUTI -> {
                    val u = (t + 4f + ph) % 11f
                    if (u < 8f) agouti(x - 14f + u / 8f * 28f, y + abs(sin(u * 9f)) * -0.2f)
                }
                Espece.RATON -> {
                    val dx = sin(t * 0.3f + ph) * 6f
                    val sens = if (cos(t * 0.3f + ph) >= 0f) 1f else -1f
                    c.save()
                    c.scale(sens, 1f, x + dx, y)
                    raton(x + dx, y)
                    c.restore()
                }
                Espece.SIFFLEUR, Espece.PARULINE -> {
                    val u = (t + ph) % 3.4f
                    val saut = if (u < 0.35f) sin(u / 0.35f * PI.toFloat()) * 1.2f else 0f
                    oiselet(x, y - saut, if (b.espece == Espece.SIFFLEUR) COULEUR_SIFFLEUR else COULEUR_PARULINE)
                }
                // Le souffle, puis la queue qui sort de l'eau et sonde.
                Espece.BALEINE -> {
                    val u = (t + ph) % 12f
                    voile = when {
                        u < 1f -> u
                        u < 2.5f -> 1f
                        u < 3.5f -> 3.5f - u
                        u in 5f..6f -> u - 5f
                        u in 6f..8.5f -> 1f
                        u in 8.5f..9.5f -> 9.5f - u
                        else -> 0f
                    }
                    if (voile > 0f) {
                        if (u < 4f) souffle(x, y) else queue(x, y + (if (u < 6f) (6f - u) else if (u > 8.5f) (u - 8.5f) else 0f) * 1.2f)
                    }
                    voile = 1f
                }
                // Elle avance à petits pas, s'arrête, puis file en arrière d'un
                // coup de queue, comme les vraies, et se remet en marche.
                Espece.ECREVISSE -> {
                    val u = (t + ph * 2.3f) % 7f
                    val sens = if (i % 2 == 0) 1f else -1f
                    val dx = when {
                        u < 4.5f -> u / 4.5f * 4f - 2f
                        u < 5f -> 2f
                        u < 5.35f -> 2f - (u - 5f) / 0.35f * 4f
                        else -> -2f
                    } * b.taille
                    val repli = if (u in 5f..5.6f) sin((u - 5f) / 0.6f * PI.toFloat()) else 0f
                    c.save()
                    c.scale(sens, 1f, x, y)
                    ecrevisse(x + dx, y, b.taille, repli, if (u < 4.5f) u else 0f)
                    c.restore()
                }
                Espece.IGUANE -> {
                    val u = (t + ph) % 5f
                    iguane(x, y, if (u < 1.4f) -abs(sin(u / 1.4f * PI.toFloat() * 3f)) * 0.6f else 0f, b.taille)
                }
                // La houle soulève la barque et la fait tanguer, chacune à son rythme.
                Espece.BARQUE -> barque(x, y + sin(t * 1.1f + ph) * 0.25f, sin(t * 0.8f + ph * 1.3f) * 0.35f)
                Espece.FANIONS -> fanions(t)
                // Posée, elle picore de temps en temps le garde-corps. La
                // nuit elle dort ailleurs.
                Espece.TOURTERELLE -> if (!P.nuit) {
                    val u = (t + ph) % 5.5f
                    tourterelle(x, y, if (x < 50f) 1f else -1f, if (u < 0.5f) sin(u / 0.5f * PI.toFloat()) else 0f, b.taille)
                }
                // Il roule doucement à son amarre ; une secousse le fait tanguer fort, puis il se calme.
                Espece.BATEAU -> bateauPlongee(x, y + sin(t * 1.1f + ph) * 0.15f,
                    sin(t * 0.9f + ph) * 1.2f + rafale * 7f * sin(depuisRafale * 5f), 0f)
                // Elle se balance un peu au bout de sa queue ; une secousse la secoue plus fort.
                Espece.MANGUE -> mangue(x, y, 0f, 0f, sin(t * 1.2f + ph) * 3f + rafale * 14f * sin(depuisRafale * 13f), true)
                // Un souffle d'air les berce ; une secousse les fait trembler, puis s'apaiser.
                Espece.PLANTE, Espece.FLEURS -> jardiniere(x, y,
                    sin(t * 1.1f + ph) * 2f + rafale * 16f * sin(depuisRafale * 2f * PI.toFloat() * 2.6f),
                    b.espece == Espece.FLEURS, b.taille)
                // Elle se balance à peine dans l'air de la forêt ; une secousse
                // la fait osciller, de moins en moins.
                Espece.LIANE -> liane(x, y, sin(t * 0.7f + ph) * 1.5f + rafale * 12f * sin(depuisRafale * 3.4f + ph * 0.3f))
                // Elle zigzague autour de sa fleur en vrombissant ; une
                // secousse l'égaille loin, puis elle revient butiner. La nuit
                // elle est à la ruche.
                Espece.ABEILLE -> if (!P.nuit) {
                    val a = t * 1.9f + ph
                    val loin = 1f + rafale * 7f
                    val dx = (sin(a) * 1.8f + sin(a * 2.3f + 1f) * 0.7f) * loin
                    val dy = (cos(a * 1.4f) * 0.9f + sin(a * 3.7f) * 0.35f) * loin
                    val vers = cos(a) * 1.8f + cos(a * 2.3f + 1f) * 1.6f
                    abeille(x + dx, y + dy, if (vers >= 0f) 1f else -1f, sin(t * 90f + ph), b.taille)
                }
            }
        }

        /**
         * Une abeille minuscule vue de profil en ([x], [y]), la tête vers la
         * droite ([sens] -1 : vers la gauche) ; [aile] de -1 à 1 fait vibrer
         * ses ailes.
         */
        fun abeille(x: Float, y: Float, sens: Float, aile: Float, taille: Float) {
            val s = taille
            val noir = duJour(0xFF2A2218.toInt())
            ovale(x - sens * 0.1f * s, y - 0.32f * s, 0.26f * s, (0.08f + 0.06f * abs(aile)) * s, duJour(0xFFF4F4F0.toInt()), 0.75f)
            ovale(x, y, 0.42f * s, 0.26f * s, duJour(0xFFE8B428.toInt()))
            ovale(x - sens * 0.06f * s, y, 0.07f * s, 0.25f * s, noir)
            ovale(x - sens * 0.26f * s, y, 0.06f * s, 0.2f * s, noir)
            rond(x + sens * 0.42f * s, y, 0.15f * s, noir)
        }

        /** Ce qui reste d'une secousse, de 0 à 1, et depuis quand elle a eu lieu : voir [Atelier.peindre]. */
        var rafale = 0f
        var depuisRafale = 0f

        /**
         * Les fanions de la place de la Victoire, tendus d'un bord à l'autre.
         * Au vent ([t] > 0), la guirlande se creuse et se relève lentement et
         * chaque fanion flotte à son rythme ; à zéro, ils pendent droit.
         */
        fun fanions(t: Float) {
            val couleurs = intArrayOf(papier(0xFFD8402E.toInt()), papier(0xFF3A9A4A.toInt()), papier(0xFFF0C030.toInt()), papier(0xFF2A6AB8.toInt()))
            // Une secousse est une rafale : la guirlande rebondit et les fanions claquent.
            val creux = 4f + sin(t * 0.8f) * 0.35f + rafale * 1.4f * sin(depuisRafale * 7f)
            val corde = { x: Float -> 3f + ((x - 50f) / 52f).let { 1f - it * it } * creux }
            var fx = -2f
            var k = 0
            while (fx < 102f) {
                val y = corde(fx)
                val vent = sin(t * 2.3f + k * 0.8f) * 0.55f * min(t, 1f) + rafale * 1.8f * sin(depuisRafale * 13f + k * 1.1f)
                forme("M$fx ${y}L${fx + 2.4f} ${corde(fx + 2.4f)}L${fx + 1.3f + vent} ${y + 2.8f - abs(vent) * 0.3f}Z", couleurs[k % 4])
                fx += 3.4f; k++
            }
        }

        /**
         * Une tourterelle à queue carrée posée, les pattes en ([x], [y]),
         * tournée vers la droite ([sens] -1 : vers la gauche). [picore]
         * penche la tête vers l'avant, de 0 à 1.
         */
        fun tourterelle(x: Float, y: Float, sens: Float, picore: Float, taille: Float) {
            c.save()
            c.scale(sens * taille, taille, x, y)
            val aile = duJour(0xFF8A7262.toInt())
            val noir = duJour(0xFF2A2420.toInt())
            forme("M${x - 0.6f} ${y - 1.1f}L${x - 3f} ${y - 0.2f}L${x - 2.8f} ${y + 0.3f}L${x - 0.4f} ${y - 0.5f}Z", aile)
            forme("M${x - 1.2f} ${y - 1f}Q${x - 0.6f} ${y - 2.3f} ${x + 0.8f} ${y - 1.9f}Q${x + 1.4f} ${y - 0.6f} ${x + 0.4f} ${y - 0.2f}" +
                "Q${x - 0.8f} ${y - 0.2f} ${x - 1.2f} ${y - 1f}Z", duJour(0xFFA48A78.toInt()))
            forme("M${x - 1.4f} ${y - 1.1f}Q${x - 0.2f} ${y - 2f} ${x + 0.6f} ${y - 1.4f}Q${x - 0.2f} ${y - 0.6f} ${x - 1.4f} ${y - 1.1f}Z", aile)
            rond(x - 0.3f, y - 1.35f, 0.14f, noir)
            rond(x + 0.2f, y - 1.25f, 0.12f, noir)
            rect(x - 0.1f, y - 0.35f, 0.14f, 0.4f, duJour(0xFFC0504A.toInt()))
            rect(x + 0.3f, y - 0.35f, 0.14f, 0.4f, duJour(0xFFC0504A.toInt()))
            val hx = x + 1.1f + picore * 0.5f
            val hy = y - 2.3f + picore * 0.9f
            rond(hx, hy, 0.55f, duJour(0xFFB49C8A.toInt()))
            forme("M${hx + 0.45f} ${hy - 0.05f}L${hx + 1f} ${hy + 0.15f}L${hx + 0.45f} ${hy + 0.25f}Z", duJour(0xFF3A3030.toInt()))
            rond(hx + 0.18f, hy - 0.12f, 0.12f, duJour(0xFF141414.toInt()))
            c.restore()
        }

        /**
         * La même en vol, de profil : le corps fuselé, la queue étalée, et
         * l'aile proche levée ([aile] 1) ou baissée (négatif) ; l'aile
         * lointaine suit, plus courte.
         */
        fun tourterelleEnVol(x: Float, y: Float, aile: Float, sens: Float, taille: Float) {
            voile = 1f
            c.save()
            c.scale(sens * taille, taille, x, y)
            val plume = duJour(0xFF8A7262.toInt())
            val cy = y - 1.2f
            forme("M${x - 0.6f} ${cy}L${x + 0.5f} ${cy}L${x - 0.6f} ${cy - 2.2f * aile}Z", duJour(0xFF6E5A4C.toInt()))
            forme("M${x - 1f} ${cy - 0.3f}L${x - 3f} ${cy - 0.6f}L${x - 3f} ${cy + 0.5f}L${x - 1f} ${cy + 0.3f}Z", plume)
            forme("M${x - 1.4f} ${cy}Q${x - 0.4f} ${cy - 0.7f} ${x + 1.2f} ${cy - 0.3f}Q${x + 1.4f} ${cy + 0.4f} ${x}" +
                " ${cy + 0.55f}Q${x - 1f} ${cy + 0.5f} ${x - 1.4f} ${cy}Z", duJour(0xFFA48A78.toInt()))
            rond(x + 1.45f, cy - 0.3f, 0.48f, duJour(0xFFB49C8A.toInt()))
            forme("M${x + 1.85f} ${cy - 0.35f}L${x + 2.35f} ${cy - 0.15f}L${x + 1.85f} ${cy - 0.05f}Z", duJour(0xFF3A3030.toInt()))
            forme("M${x - 0.9f} ${cy - 0.1f}L${x + 0.7f} ${cy - 0.1f}L${x - 0.5f} ${cy - 2.8f * aile}Z", plume)
            rond(x - 0.45f, cy - 2.5f * aile, 0.22f, duJour(0xFF2A2420.toInt()))
            c.restore()
        }

        /**
         * Une jardinière de terre cuite accrochée dehors au garde-corps, son
         * bord en ([x], [y]), et sa touffe de feuilles penchée de [penche]
         * degrés ; [fleurs] : des géraniums rouges au bout des tiges.
         */
        fun jardiniere(x: Float, y: Float, penche: Float, fleurs: Boolean, taille: Float) {
            voile = 1f
            val l = taille
            c.save()
            c.rotate(penche, x, y)
            for (k in -3..3) feuillePliee(x, y + 0.2f, (2.6f - abs(k) * 0.25f) * l, -90f + k * 24f, duJour(0xFF4E8A3E.toInt()), 0.45f)
            if (fleurs) for (k in -2..2 step 2) {
                val a = (-90f + k * 24f) * PI.toFloat() / 180f
                rond(x + cos(a) * 2.3f * l, y + 0.2f + sin(a) * 2.3f * l, 0.5f * l, duJour(0xFFD8402E.toInt()))
            }
            c.restore()
            forme("M${x - 1.2f * l} ${y}L${x + 1.2f * l} ${y}L${x + 0.9f * l} ${y + 1.8f * l}L${x - 0.9f * l} ${y + 1.8f * l}Z", duJour(0xFFB0603E.toInt()))
            rect(x - 1.3f * l, y - 0.15f, 2.6f * l, 0.4f * l, duJour(0xFF8E4A30.toInt()))
        }

        /**
         * Une mangue du manguier de Marie-Galante, pendue en ([x], [y]) sous
         * sa queue : orange, la joue rouge. Tombée, la queue reste à la
         * branche ([attachee] faux) et le fruit est décalé de ([dx], [dy]) ;
         * [angle] la fait pivoter, en degrés, autour de la queue ou sur
         * elle-même dans l'herbe. [opacite] la fane dans l'herbe, [grosseur]
         * la fait repousser au bout de sa queue.
         */
        fun mangue(x: Float, y: Float, dx: Float, dy: Float, angle: Float, attachee: Boolean,
                   opacite: Float = 1f, grosseur: Float = 1f) {
            voile = 1f
            val queue = duJour(0xFF4E3C2E.toInt())
            rect(x - 0.09f, y - 2.2f, 0.18f, if (attachee) 1.4f else 0.9f, queue)
            voile = opacite
            c.save()
            if (attachee) c.rotate(angle, x, y - 2.2f) else c.rotate(angle, x + dx, y + dy)
            if (grosseur < 1f) c.scale(grosseur, grosseur, x, y - 1.1f)
            c.translate(dx, dy)
            forme(Path().apply { addOval(RectF(x - 0.8f, y - 1.1f, x + 0.8f, y + 1.1f), Path.Direction.CW) }, duJour(0xFFE89A2E.toInt()))
            forme(Path().apply { addOval(RectF(x - 0.75f, y - 1.05f, x + 0.1f, y + 0.2f), Path.Direction.CW) }, duJour(0xFFC8432E.toInt()))
            c.restore()
            voile = 1f
        }

        /**
         * Un flamboyant en fleurs, celui de Bouillante : un tronc qui monte
         * du bas et s'ouvre en branches, un houppier en parasol qui entre par
         * le coin haut droit, semé de grappes rouges tirées de [graine]. Pour
         * l'avoir à gauche, le peindre retourné avec [tasser].
         */
        fun flamboyantEnParasol(graine: Int) {
            val brun = papier(0xFF4E3C2E.toInt())
            pieceD("M97 64Q95.4 52 92.6 42.6L94.4 42Q97.6 50 100.4 56L102 64Z", brun, 0.6f)
            barre(93.4f, 43f, 84f, 35.4f, 0.8f, brun, 0.4f)
            barre(94f, 43f, 99f, 33f, 0.8f, brun, 0.4f)
            barre(90f, 39.6f, 90.6f, 34f, 0.5f, brun, 0.3f)
            val rf = Alea(graine)
            val dessus = { x: Float -> 26f - ((x - 96f) / 20f).let { 1f - it * it } * 9f }
            val dessous = { x: Float -> 34.6f + (x - 78f) * 0.06f + sin(x * 1.3f) * 0.3f }
            val houppier = Path()
            houppier.moveTo(76f, dessous(76f))
            var hx = 76f
            while (hx < 104f) { houppier.lineTo(hx, dessus(hx)); hx += 1f }
            houppier.lineTo(104f, dessous(104f))
            houppier.close()
            houppier.op(bosses(76f, 104f, dessus, 1.4f, 2.4f, rf), Path.Op.UNION)
            val festons = Path(houppier).apply { op(bosses(77f, 104f, dessous, 1f, 1.5f, rf), Path.Op.UNION) }
            piece(festons, papier(0xFF2E6A22.toInt()), 0.7f)
            piece(houppier, papier(0xFF4E9E2E.toInt()), 0f)
            piece(bosses(78f, 104f, { x -> dessus(x) + 0.4f }, 1f, 1.6f, rf), papier(0xFF72BE40.toInt()), 0f)
            pieceD("M80 ${dessous(80f) - 2.4f}Q92 ${dessous(92f) - 3.6f} 104 ${dessous(104f) - 2.6f}L104 ${dessous(104f) - 1.4f}Q92 ${dessous(92f) - 2.2f} 80 ${dessous(80f) - 1.2f}Z",
                papier(0xFF3A8228.toInt()), 0f)
            val rouges = intArrayOf(papier(0xFFE8401E.toInt()), papier(0xFFF26A2A.toInt()), papier(0xFFC8301C.toInt()))
            repeat(14) {
                val cx = 79f + rf() * 24f
                val cy = dessus(cx) + 1f + rf() * (dessous(cx) - dessus(cx) - 3f)
                repeat(6) {
                    piece(disque(cx + (rf() - 0.5f) * 2.8f, cy + (rf() - 0.5f) * 1.4f, 0.45f + rf() * 0.45f),
                        rouges[(rf() * 3f).toInt().coerceAtMost(2)], 0.2f)
                }
            }
        }

        /**
         * Le bateau de plongée de Bouillante, coque rouge et cabine blanche,
         * amarré au bout du ponton : ([x], [y]) est le milieu de sa ligne de
         * flottaison, autour duquel il roule de [angle] degrés. [dy] le
         * soulève.
         */
        fun bateauPlongee(x: Float, y: Float, angle: Float, dy: Float) {
            voile = 1f
            c.save()
            c.rotate(angle, x, y)
            c.translate(x - 7.5f, y - 47.6f + dy)
            pieceD("M0.4 44.6L14.6 44.6L13.4 48.4Q8 49.2 2 48.4Z", papier(0xFFB8322C.toInt()), 0.6f)
            pieceD("M0.4 44.6L14.6 44.6L14.4 45.4L0.6 45.4Z", papier(0xFFF4F1EA.toInt()), 0f)
            pieceD("M2 44.6L2.4 40.8L10.2 40.8L10.6 44.6Z", papier(0xFFF2EFE8.toInt()), 0.5f)
            pieceD("M1.6 41L10.8 41L10.6 40.2L1.8 40.2Z", papier(0xFF3A3A3A.toInt()), 0f)
            for (k in 0 until 4) pieceD("M${3f + k * 1.8f} 41.8L${4.2f + k * 1.8f} 41.8L${4.2f + k * 1.8f} 43L${3f + k * 1.8f} 43Z", papier(0xFF3C4450.toInt()), 0f)
            barre(11.6f, 44.6f, 12.4f, 40.6f, 0.2f, papier(0xFFDAD6CC.toInt()), 0f)
            barre(12.4f, 40.6f, 14.6f, 40.6f, 0.2f, papier(0xFFDAD6CC.toInt()), 0f)
            c.restore()
        }

        /** Une barque blanche au mouillage, l'étrave à droite ; [tangage] lève l'étrave et baisse la poupe. */
        fun barque(x: Float, y: Float, tangage: Float = 0f) {
            val a = tangage
            forme("M${x - 2.2f} ${y + a}L${x + 2.4f} ${y - 0.3f - a}Q${x + 2f} ${y + 0.9f - a * 0.6f} ${x + 1.6f} ${y + 0.9f - a * 0.5f}" +
                "L${x - 1.8f} ${y + 0.9f + a * 0.5f}Z", blanc())
        }

        // ------------------------------------------------------ les quinze lieux de la deuxième vague

        // ------------------------------------------------- maquette papier découpé

        /** Une couleur de la référence, à l'heure de la carte. */
        fun papier(col: Int): Int = duJour(col)

        /** Une pièce de papier découpé, quelle que soit sa taille : une feuille, une lanière. */
        /** Le tassement en cours (voir [tasser]), appliqué à chaque pièce. */
        private var tasse: Matrix? = null

        /**
         * Peint [bloc] réduit de [sx] × [sy] autour de ([cx], [cy]), puis
         * décalé de [dx] : pour faire tenir sous l'arche des cartes rares un
         * élément haut posé près d'un bord, sans réécrire ses cotes.
         */
        fun tasser(cx: Float, cy: Float, sx: Float, sy: Float, dx: Float = 0f, bloc: () -> Unit) {
            tasse = Matrix().apply { setScale(sx, sy, cx, cy); postTranslate(dx, 0f) }
            try { bloc() } finally { tasse = null }
        }

        fun piece(t0: Path, col: Int, ombre: Float = 1f) {
            if (!actif) return
            val t = tasse?.let { m -> Path(t0).apply { transform(m) } } ?: t0
            if (!decoupe || aplats > 0) { forme(t, col); return }
            t.computeBounds(boite, true)
            peint(t, col, 0f, boite, ombre)
        }

        private fun disque(cx: Float, cy: Float, r: Float) = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }

        private val pose = Matrix()

        /**
         * Une feuille en amande pliée sur sa nervure, de [l] unités, posée
         * par sa base en ([x], [y]) et tournée de [angle] degrés : deux
         * moitiés de deux tons, la claire par-dessus.
         */
        fun feuillePliee(x: Float, y: Float, l: Float, angle: Float, col: Int, large: Float = 0.5f) {
            val w = l * large
            val haut = Path().apply { moveTo(0f, 0f); quadTo(l * 0.45f, -w, l, 0f); close() }
            val bas = Path().apply { moveTo(0f, 0f); quadTo(l * 0.45f, w, l, 0f); close() }
            pose.setRotate(angle)
            pose.postTranslate(x, y)
            haut.transform(pose)
            bas.transform(pose)
            piece(bas, mix(col, 0xFF000000.toInt(), 0.14f), 0.8f)
            piece(haut, mix(col, 0xFFFFFFFF.toInt(), 0.1f), 0f)
        }

        /** Un relief en lignes de niveau : [n] bandes découpées, de la plus haute (derrière) à la plus basse. */
        fun bandes(y0: Float, pas: Float, n: Int, amp: Float, graine: Int, de: Int, a: Int, f1: Float = 0.07f) {
            for (i in 0 until n) {
                crete(y0 + i * pas, amp * (1f - 0.12f * i), graine + i, mix(de, a, if (n == 1) 0f else i / (n - 1f)), -2f, 102f, f1)
            }
        }

        private fun profil(gauche: Boolean, haut: Float, bas: Float, fin: Float, ph: Float, x: Float): Float {
            val u = (if (gauche) x / fin else (LARGEUR - x) / (LARGEUR - fin)).coerceIn(0f, 1f)
            return haut + (bas - haut) * u * u * (3f - 2f * u) + sin(x * 0.3f + ph) * 0.6f + sin(x * 0.11f + 2f * ph) * 0.9f
        }

        /** Un morne qui descend du bord de la fenêtre vers l'eau, et finit à [fin] au niveau [bas]. */
        fun pente(gauche: Boolean, haut: Float, bas: Float, fin: Float, graine: Int, col: Int): (Float) -> Float {
            val ph = Alea(graine)() * 6f
            val sens = if (gauche) 1f else -1f
            val t = Path()
            var x = if (gauche) -2f else LARGEUR + 2f
            t.moveTo(x, BAS + 2f)
            while ((x - fin) * sens < 0f) { t.lineTo(x, profil(gauche, haut, bas, fin, ph, x)); x += 1.5f * sens }
            t.lineTo(fin, profil(gauche, haut, bas, fin, ph, fin))
            t.quadTo(fin + 3f * sens, bas + 1f, fin + 2f * sens, bas + 5f)
            t.lineTo(fin, BAS + 2f)
            t.close()
            piece(t, col)
            return { v -> profil(gauche, haut, bas, fin, ph, v) }
        }

        /** Une eau en lanières de papier, rangée par rangée, avec des jours entre elles. */
        fun lanieres(x0: Float, x1: Float, y0: Float, y1: Float, n: Int, graine: Int, cols: IntArray) {
            val r = Alea(graine)
            for (i in 0 until n) {
                val y = y0 + (y1 - y0) * i / n
                var x = x0 + r() * 3f
                while (x < x1) {
                    val l = 4f + r() * 10f
                    val e = 0.7f + r() * 0.6f
                    val t = Path()
                    t.addRoundRect(RectF(x, y, min(x + l, x1), y + e), e / 2f, e / 2f, Path.Direction.CW)
                    piece(t, cols[min((r() * cols.size).toInt(), cols.size - 1)], 0.5f)
                    x += l + 0.6f + r() * 2.4f
                }
            }
        }

        /** Un brin d'herbe en lanière, de [h] unités, penché de [penche]. */
        fun brin(x: Float, y: Float, h: Float, penche: Float, col: Int) {
            val t = Path()
            t.moveTo(x - 0.4f, y)
            t.quadTo(x + penche * 0.3f, y - h * 0.6f, x + penche, y - h)
            t.quadTo(x + penche * 0.3f + 0.45f, y - h * 0.55f, x + 0.4f, y)
            t.close()
            piece(t, col, 0.5f)
        }

        fun herbes(x0: Float, x1: Float, y: Float, graine: Int, cols: IntArray) {
            val r = Alea(graine)
            var x = x0
            while (x < x1) {
                brin(x, y + r() * 1.5f, 3f + r() * 4f, (r() - 0.5f) * 4f, papier(cols[min((r() * cols.size).toInt(), cols.size - 1)]))
                x += 0.5f + r() * 0.9f
            }
        }

        fun butte(cx: Float, cy: Float, rx: Float, ry: Float, col: Int) {
            piece(Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }, col)
        }

        /** Le ciel en feuilles de papier crème, du zénith vers l'horizon, et l'astre en disque de papier. */
        fun cielPapier() {
            val fond = if (P.nuit) P.cielBas else mix(P.cielBas, CREME, 0.6f)
            p.shader = null
            p.style = Paint.Style.FILL
            teinte(fond, 1f)
            if (actif) c.drawRect(-2f, -2f, LARGEUR + 2f, BAS + 2f, p)
            val bords = floatArrayOf(34f, 24f, 15f, 7f)
            for ((i, y) in bords.withIndex()) {
                val t = i / 3f
                val col = mix(mix(P.cielBas, P.cielHaut, 0.3f + 0.7f * t), CREME, if (P.nuit) 0.04f else 0.45f - 0.3f * t)
                val ph = Alea(40 + i)() * 6f
                val bande = Path()
                bande.moveTo(-2f, -2f)
                bande.lineTo(LARGEUR + 2f, -2f)
                var x = LARGEUR + 2f
                while (x >= -2f) { bande.lineTo(x, y + sin(x * 0.09f + ph) * 1.6f + sin(x * 0.23f + ph) * 0.6f); x -= 2f }
                bande.close()
                piece(bande, col, 0.6f)
            }
            if (P.nuit) {
                piece(disque(P.astreX, P.astreY, P.astreR * 1.8f), mix(P.cielHaut, P.astre, 0.22f), 0.5f)
                piece(disque(P.astreX, P.astreY, P.astreR), P.astre, 0.6f)
                piece(disque(P.astreX + 1.9f, P.astreY - 1.1f, P.astreR * 0.92f), mix(P.cielHaut, P.astre, 0.22f), 0.6f)
            } else {
                piece(disque(P.astreX, P.astreY, P.astreR * 1.9f), mix(P.astre, CREME, 0.55f), 0.5f)
                piece(disque(P.astreX, P.astreY, P.astreR), P.astre, 0.6f)
            }
        }

        /** Des bosses de canopée, du ([x0]) au ([x1]), posées sur la ligne [crete] : une lisière de forêt. */
        private fun bosses(x0: Float, x1: Float, crete: (Float) -> Float, rMin: Float, rMax: Float, r: Alea): Path {
            val t = Path()
            var x = x0
            val sens = if (x1 >= x0) 1f else -1f
            while ((x1 - x) * sens > 0f) {
                val p = ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)
                val rr = rMax + (rMin - rMax) * p + (r() - 0.5f) * rMin * 0.6f
                t.addCircle(x, crete(x) + rr * 0.25f, rr, Path.Direction.CW)
                x += rr * (1.1f + r() * 0.4f) * sens
            }
            return t
        }

        /**
         * Une rive de forêt tropicale qui descend du bord de la fenêtre jusqu'à
         * l'eau : le versant, et dessus une lisière de houppiers ronds, plus
         * gros près du bord, plus petits vers l'eau. Une seule feuille de papier.
         */
        fun rive(gauche: Boolean, haut: Float, bas: Float, fin: Float, graine: Int, col: Int, rMax: Float) {
            val ph = Alea(graine)() * 6f
            val sens = if (gauche) 1f else -1f
            val t = Path()
            var x = if (gauche) -2f else LARGEUR + 2f
            t.moveTo(x, BAS + 2f)
            while ((x - fin) * sens < 0f) { t.lineTo(x, profil(gauche, haut, bas, fin, ph, x)); x += 1.5f * sens }
            // Le bout de la rive s'arrondit dans l'eau, puis rentre sous elle.
            val yb = profil(gauche, haut, bas, fin, ph, fin)
            t.lineTo(fin, yb)
            t.cubicTo(fin + 3.5f * sens, yb, fin + 3.5f * sens, yb + 4f, fin, yb + 4.5f)
            t.cubicTo(fin - 3f * sens, yb + 5f, fin - 6f * sens, yb + 7f, fin - 8f * sens, BAS + 2f)
            t.close()
            val canopee = bosses(if (gauche) -3f else LARGEUR + 3f, fin - 2f * sens,
                { v -> profil(gauche, haut, bas, fin, ph, v) }, rMax * 0.35f, rMax, Alea(graine + 50))
            t.op(canopee, Path.Op.UNION)
            piece(t, col)
        }

        /** Un morne couvert de forêt : une crête arrondie hérissée de houppiers. */
        fun morneBoise(y0: Float, amp: Float, graine: Int, col: Int, r: Float) {
            val ph = Alea(graine)() * 6f
            fun crete(x: Float) = y0 + sin(x * 0.045f + ph) * amp + sin(x * 0.13f + 2f * ph) * amp * 0.35f
            val t = Path()
            t.moveTo(-2f, BAS + 2f)
            var x = -2f
            while (x <= LARGEUR + 2f) { t.lineTo(x, crete(x)); x += 2f }
            t.lineTo(LARGEUR + 2f, BAS + 2f)
            t.close()
            t.op(bosses(-3f, LARGEUR + 3f, ::crete, r, r, Alea(graine + 50)), Path.Op.UNION)
            piece(t, col)
        }

        /**
         * Un arbre de la forêt : le tronc fin, évasé en contreforts au pied,
         * et un houppier en chou-fleur de deux tons, l'ombre dessous, la
         * lumière en haut.
         */
        fun arbreRond(x: Float, y: Float, r: Float, col: Int, tronc: Int) {
            val t = Path()
            t.moveTo(x - 1.6f, y); t.quadTo(x - 0.4f, y - 1f, x - 0.35f, y - r * 1.6f)
            t.lineTo(x + 0.35f, y - r * 1.6f); t.quadTo(x + 0.4f, y - 1f, x + 1.6f, y); t.close()
            piece(t, tronc, 0.6f)
            val cy = y - r * 1.9f
            val al = Alea((x * 13f + y * 7f).toInt())
            val sombre = Path()
            for (k in 0 until 6) {
                val a = k / 6f * 2f * PI.toFloat()
                sombre.addCircle(x + cos(a) * r * 0.62f, cy + sin(a) * r * 0.42f, r * (0.5f + al() * 0.15f), Path.Direction.CW)
            }
            sombre.addCircle(x, cy, r * 0.7f, Path.Direction.CW)
            piece(sombre, mix(col, 0xFF000000.toInt(), 0.12f), 0.9f)
            val clair = Path()
            for (k in 0 until 4) {
                clair.addCircle(x - r * 0.35f + k * r * 0.25f, cy - r * 0.35f + (al() - 0.5f) * r * 0.2f, r * (0.32f + al() * 0.1f), Path.Direction.CW)
            }
            piece(clair, mix(col, 0xFFFFFFFF.toInt(), 0.12f), 0.5f)
        }

        /** Une grande feuille de bananier, en pagaie, sa nervure et deux déchirures. */
        fun feuilleBananier(x: Float, y: Float, l: Float, angle: Float, col: Int) {
            val w = l * 0.24f
            val f = Path()
            f.moveTo(0f, 0f)
            f.cubicTo(l * 0.15f, -w, l * 0.85f, -w, l, -w * 0.15f)
            f.cubicTo(l * 0.9f, w * 0.9f, l * 0.2f, w, 0f, 0f)
            f.close()
            for (d in floatArrayOf(0.45f, 0.7f)) {
                val coupe = Path()
                coupe.moveTo(l * d, -w * 1.2f); coupe.lineTo(l * d + 0.5f, -w * 1.2f); coupe.lineTo(l * d + 0.25f, -w * 0.05f); coupe.close()
                f.op(coupe, Path.Op.DIFFERENCE)
            }
            val nervure = Path()
            nervure.moveTo(-l * 0.25f, 0.25f); nervure.lineTo(l * 0.95f, -w * 0.1f); nervure.lineTo(-l * 0.25f, -0.25f); nervure.close()
            pose.setRotate(angle)
            pose.postTranslate(x, y)
            f.transform(pose)
            nervure.transform(pose)
            piece(f, col, 0.9f)
            piece(nervure, mix(col, 0xFFFFFFFF.toInt(), 0.3f), 0f)
        }

        /** Une feuille en cœur sur sa tige, comme celles des malangas du sous-bois. */
        fun feuilleCoeur(x: Float, y: Float, s: Float, angle: Float, col: Int) {
            val f = Path()
            f.moveTo(0f, 0f)
            f.cubicTo(-s * 0.9f, -s * 0.15f, -s * 0.7f, -s * 1.1f, 0f, -s * 1.3f)
            f.cubicTo(s * 0.7f, -s * 1.1f, s * 0.9f, -s * 0.15f, 0f, 0f)
            f.close()
            val n = Path()
            n.moveTo(-0.15f, 0f); n.lineTo(0f, -s * 1.2f); n.lineTo(0.15f, 0f); n.close()
            pose.setRotate(angle)
            pose.postTranslate(x, y)
            f.transform(pose)
            n.transform(pose)
            val tige = Path()
            tige.moveTo(x - 0.25f, y + s * 1.2f); tige.lineTo(x - 0.2f, y); tige.lineTo(x + 0.2f, y); tige.lineTo(x + 0.25f, y + s * 1.2f); tige.close()
            piece(tige, mix(col, 0xFF000000.toInt(), 0.2f), 0.4f)
            piece(f, col, 0.9f)
            piece(n, mix(col, 0xFFFFFFFF.toInt(), 0.3f), 0f)
        }

        /**
         * Grand Étang en papier découpé, d'après la référence du propriétaire
         * (2026-11-07) transposée en forêt tropicale : des mornes boisés au
         * loin, l'étang en lanières qui s'élargit vers soi entre deux rives de
         * forêt aux houppiers ronds, de grandes feuilles devant. Sans voûte ni
         * rien de pointu : tout tient dans l'empilement des plans.
         */
        fun grandEtangPapier() {
            morneBoise(22f, 3f, 700, mix(papier(0xFF7FB8A8.toInt()), P.cielBas, 0.35f), 1.6f)
            morneBoise(26f, 2.4f, 701, mix(papier(0xFF5FA88A.toInt()), P.cielBas, 0.2f), 1.8f)
            morneBoise(29.5f, 1.6f, 702, papier(0xFF6FCB8A.toInt()), 1.6f)
            plan(1)
            // L'étang, qui naît à l'horizon et s'élargit jusqu'au premier plan.
            fun gauche(y: Float) = etangGauche(y)
            fun droite(y: Float) = etangDroite(y)
            val eau = Path()
            eau.moveTo(gauche(32f) - 2f, 32f); eau.lineTo(droite(32f) + 2f, 32f)
            eau.lineTo(droite(66f) + 4f, BAS); eau.lineTo(gauche(66f) - 4f, BAS); eau.close()
            piece(eau, papier(SARCELLE), 0.3f)
            val reflets = intArrayOf(papier(TURQUOISE), papier(VERT_D_EAU), papier(EAU_CLAIRE), papier(0xFF20B298.toInt()))
            val r = Alea(710)
            var y = 32.6f
            while (y < 62f) {
                val p = (y - 32f) / 28f
                val e = 0.35f + 0.9f * p
                var x = gauche(y) - 2f + r() * 2f
                while (x < droite(y) + 2f) {
                    val l = (3f + r() * 7f) * (0.6f + p)
                    val t = Path()
                    t.addRoundRect(RectF(x, y, min(x + l, droite(y) + 2f), y + e), e / 2f, e / 2f, Path.Direction.CW)
                    piece(t, reflets[min((r() * 4f).toInt(), 3)], 0.4f)
                    x += l + 0.5f + r() * 2f * (0.5f + p)
                }
                y += e + 0.35f + 0.6f * p
            }
            // Les rives de forêt, en lisières de houppiers qui descendent jusqu'à l'eau.
            val verts = intArrayOf(papier(0xFF2E9E6E.toInt()), papier(0xFF8FD99A.toInt()), papier(SARCELLE),
                papier(LIME), papier(0xFF4FB06A.toInt()), papier(VERT_PROFOND))
            for (k in 0 until 6) {
                val bas = 33f + k * 3.6f
                rive(true, 27f + k * 4f, bas, gauche(bas), 720 + k, verts[k], 3.2f + k * 0.5f)
                rive(false, 28f + k * 4f, bas + 0.5f, droite(bas + 0.5f), 730 + k, verts[(k + 2) % 6], 3.2f + k * 0.5f)
                if (k == 2) {
                    // Quelques grands arbres qui dépassent de la canopée.
                    val tronc = papier(TERRE)
                    arbreRond(7f, 44f, 4.5f, papier(0xFF3FA86A.toInt()), tronc)
                    arbreRond(17f, 45f, 3.6f, papier(MOUTARDE), tronc)
                    arbreRond(84f, 45f, 3.6f, papier(0xFF8FD99A.toInt()), tronc)
                    arbreRond(93f, 44f, 4.5f, papier(SARCELLE), tronc)
                }
            }
            plan(2)
            // Le sous-bois du premier plan : grandes feuilles, balisier, anthurium.
            butte(4f, 74f, 24f, 11f, papier(TURQUOISE))
            butte(96f, 73f, 24f, 11f, papier(LIME))
            feuilleBananier(-1f, 60f, 16f, -38f, papier(0xFF4FB06A.toInt()))
            feuilleBananier(2f, 63f, 14f, -12f, papier(SARCELLE))
            feuilleBananier(101f, 60f, 16f, -142f, papier(0xFF3FA86A.toInt()))
            feuilleCoeur(10f, 63f, 4.5f, -18f, papier(VERT_PROFOND))
            feuilleCoeur(16f, 65f, 3.5f, 12f, papier(0xFF2E9E6E.toInt()))
            feuilleCoeur(90f, 64f, 4.2f, 15f, papier(VERT_PROFOND))
            balisier(22f, 64f, 9f, papier(SARCELLE))
            anthurium(81f, 64f)
        }

        /** Le Grand Étang : le lac dans sa cuvette de forêt, les mornes, une libellule. */
        fun grandEtang() {
            forme("M0 30Q14 17 28 23Q44 9 60 21Q78 13 100 25L100 66L0 66Z", P.loin)
            bosquet(-2f, 102f, 26f, 2.4f, 251, mix(P.loin, P.milieu, 0.5f))
            plan(1)
            forme("M0 38Q14 32 30 42L30 50L0 50Z", P.milieu)
            forme("M70 42Q86 31 100 36L100 50L70 50Z", P.milieu)
            bosquet(-2f, 30f, 37f, 3f, 252, mix(P.milieu, P.pres, 0.2f))
            bosquet(70f, 102f, 36f, 3f, 253, mix(P.milieu, P.pres, 0.2f))
            forme("M0 46Q50 41 100 46L100 52L0 52Z", P.milieu)
            bosquet(-2f, 102f, 46f, 2.6f, 254, P.milieu)
            ovale(50f, 53f, 44f, 6f, P.eau)
            trait("M30 52L46 52M56 54.5L72 54.5M40 56.5L52 56.5", blanc(), 0.5f, 0.6f)
            brume(40f, 45f, 26f, 1.8f)
            brume(72f, 41f, 16f, 1.3f, 0.25f)
            plan(2)
            forme("M0 60Q20 56 40 61Q62 57 80 60Q92 57 100 59L100 66L0 66Z", P.pres)
            bosquet(-2f, 102f, 61f, 3.2f, 255, P.pres)
            anthurium(24f, 61f)
            balisier(12f, 63f, 10f, feuille())
            balisier(88f, 64f, 11f, feuille())
        }

        // ------------------------------------------- les Bains Jaunes en papier

        /**
         * Les Bains Jaunes en papier découpé, d'après la photo du propriétaire
         * (2026-11-08) : le bassin rectangulaire d'eau sombre, son rebord de
         * pierre qui fuit vers le fond à droite du mot ; le chemin de pavés
         * mouillés semé de feuilles jaunes qui longe le bassin ; la forêt
         * dense des deux côtés, les bambous qui se penchent sur l'eau, les
         * grandes feuilles claires devant à droite, les marches de bois. Le
         * raton laveur longe le rebord du fond la nuit.
         */
        fun bainsJaunesPapier() {
            // Le fond de la forêt en lisières, plus clair au bout du chemin.
            morneBoise(2f, 2f, 1601, papier(0xFF2E5E30.toInt()), 3f)
            val pale = mix(papier(0xFF6E7E62.toInt()), P.cielBas, 0.35f)
            for ((x, e) in arrayOf(34f to 0.6f, 42f to 0.5f, 55f to 0.7f)) barre(x, 40f, x + 0.6f, 4f, e, pale, 0f)
            morneBoise(14f, 2.4f, 1602, mix(papier(0xFF6EA858.toInt()), P.cielBas, 0.3f), 3f)
            brumePapier(26f, 72f, 24f, 4f, 1603)
            morneBoise(24f, 2f, 1604, mix(papier(0xFF4E8E44.toInt()), P.cielBas, 0.15f), 2.8f)
            morneBoise(33f, 1.6f, 1605, papier(0xFF6AA24E.toInt()), 2.4f)
            plan(1)
            // Les deux bords de forêt.
            val verts = intArrayOf(papier(0xFF5E9A44.toInt()), papier(0xFF2E6230.toInt()), papier(0xFF7AAE4E.toInt()))
            for (k in 0 until 3) rive(true, 10f + k * 8f, 40f + k * 3f, 44f - k * 6f, 1610 + k, verts[k], 3.4f + k * 0.5f)
            for (k in 0 until 2) rive(false, 14f + k * 8f, 38f + k * 1.6f, 64f + k * 6f, 1620 + k, verts[(k + 1) % 3], 3.4f + k * 0.5f)
            bambous(98f, 42f, 30f, -1f, 1625)
            // Le chemin de pavés mouillés, qui monte vers le fond.
            pieceD("M-2 68L-2 52Q20 46 44 41.6L60 41.2L70 68Z", papier(0xFF5E6466.toInt()), 0.4f)
            val rp = Alea(1626)
            var y = 42f
            while (y < 67.5f) {
                val p = (y - 41f) / 27f
                val g = -2f + (44f + 2f) * (1f - p) - (if (y > 52f) 0f else 0f)
                val gauche = if (y < 52f) 44f - (y - 41.6f) / 10.4f * 46f else -2f
                val droite = 60f + (y - 41.2f) / 26.8f * 10f
                var x = gauche + rp() * 1.2f
                val e = 0.4f + p * 1.4f
                while (x < droite - 0.6f) {
                    val l = (0.8f + rp() * 1f) * (0.5f + p * 1.6f)
                    galet(x + l / 2f, y + e / 2f, l / 2f, papier(if (rp() < 0.5f) 0xFF7A8284.toInt() else 0xFF8E9698.toInt()))
                    x += l + 0.15f
                }
                y += e + 0.15f
                if (g > 1000f) break
            }
            for ((fx, fy) in arrayOf(20f to 58f, 34f to 62f, 46f to 55f, 12f to 64f, 52f to 64.6f, 40f to 48f))
                feuillePliee(fx, fy, 1.4f, rp() * 360f, papier(0xFFD8B83A.toInt()), 0.4f)
            // Le bassin : l'eau sombre, ses reflets, le rebord de pierre.
            val eau = papier(0xFF1E2C24.toInt())
            pieceD("M61.4 42.6L98 41.8L104 41.8L104 68L71.6 68Z", eau, 0.2f)
            pieceD("M66 46Q80 45 98 46L98 49Q84 50.6 70 52Q67 50 66 46Z", papier(0xFF3E5A34.toInt()), 0f)
            pieceD("M72 56Q86 54.6 104 56L104 59Q88 60.6 74 61.4Z", papier(0xFF2E4A30.toInt()), 0f)
            for ((ry, rx0, rl) in arrayOf(Triple(53f, 76f, 14f), Triple(63f, 80f, 18f)))
                pieceD("M$rx0 ${ry}Q${rx0 + rl / 2f} ${ry - 0.3f} ${rx0 + rl} ${ry}Q${rx0 + rl / 2f} ${ry + 0.4f} $rx0 ${ry}Z", papier(0xFF6E8A6A.toInt()), 0f)
            val rebord = papier(0xFF8A8E8A.toInt())
            val paroi = papier(0xFF4E5250.toInt())
            pieceD("M59.6 41L98 40.2L98 41.8L61.4 42.6Z", rebord, 0.4f)
            pieceD("M61.4 42.6L98 41.8L98 42.8L61.8 43.6Z", paroi, 0f)
            pieceD("M59.6 41L61.4 42.6L71.6 68L68 68Z", rebord, 0.6f)
            pieceD("M61.4 42.6L62.6 43.4L72.8 68L71.6 68Z", paroi, 0f)
            var k = 0
            while (k < 12) {
                val u = k / 12f
                val x0 = 59.6f + (68f - 59.6f) * u; val y0 = 41f + 27f * u
                barre(x0, y0, x0 + 1.6f + u * 1.6f, y0 + 1.4f * (1f - u), 0.1f, papier(0xFF6E726E.toInt()), 0f)
                k++
            }
            // La rive de roche noire au fond à droite.
            pieceD("M96 40.4Q100 38 104 38L104 44L98 41.8Z", papier(0xFF2E2C2A.toInt()), 0.4f)
            // Une vapeur légère monte de l'eau chaude.
            VAPEURS_BAINS.forEachIndexed { i, (x, pied, haut, w, derive) -> voileVapeur(x, pied, haut, w, derive, 1630 + i) }
            for ((vx, vy, vl) in arrayOf(Triple(70f, 48f, 9f), Triple(86f, 52f, 11f), Triple(78f, 58f, 12f)))
                forme(Path().apply { addOval(RectF(vx - vl, vy - 1.2f, vx + vl, vy + 1.2f), Path.Direction.CW) }, 0xFFFFFFFF.toInt(), 0.18f)
            plan(2)
            // La mousse du bord gauche, les marches de bois, les grandes feuilles claires.
            pieceD("M-2 56Q4 58 6 68L-2 68Z", papier(0xFF5E9A3A.toInt()), 0.4f)
            val bois = papier(0xFF7A5A40.toInt())
            pieceD("M76 62L90 61.4L90.6 64L76.4 64.6Z", bois, 0.6f)
            pieceD("M75.4 65.4L91 64.8L91.6 67.6L75.8 68.2Z", bois, 0.6f)
            for ((fx, fy, fa) in arrayOf(Triple(104f, 50f, -160f), Triple(104f, 58f, -150f), Triple(104f, 64f, -170f), Triple(96f, 68f, -120f)))
                feuilleBananier(fx, fy, 11f, fa, papier(0xFFB4D86A.toInt()))
            balisier(10f, 62f, 13f, feuille())
            balisier(92f, 60f, 12f, feuille())
        }

        /** Les Bains Jaunes : le bassin de pierre au pied de la Soufrière, la vapeur, le raton laveur la nuit. */
        fun bainsJaunes() {
            if (PAPIER) { bainsJaunesPapier(); return }
            forme("M0 26Q20 16 40 22Q60 12 80 20Q92 16 100 20L100 66L0 66Z", P.loin)
            bosquet(-2f, 102f, 25f, 2.6f, 261, mix(P.loin, P.milieu, 0.5f))
            plan(1)
            forme("M0 36Q25 28 50 36Q75 28 100 34L100 66L0 66Z", P.milieu)
            bosquet(-2f, 102f, 35f, 3.4f, 262, P.milieu)
            bosquet(-2f, 102f, 41f, 3f, 263, mix(P.milieu, P.pres, 0.25f))
            plan(2)
            rect(18f, 44f, 64f, 12f, P.pierre)
            trait("M18 48H82M18 52H82M26 44V48M40 48V52M54 44V48M68 48V52M33 52V56M61 52V56", mix(P.pierre, P.sol, 0.4f), 0.35f)
            rect(22f, 45.5f, 56f, 3f, mix(P.eau, 0xFFD8C467.toInt(), if (P.nuit) 0.15f else 0.45f))
            rect(10f, 53f, 8f, 3f, P.pierre)
            rect(13f, 50.5f, 5f, 2.5f, P.pierre)
            vapeur(36f, 45f, 10f)
            vapeur(56f, 44f, 11f)
            forme("M0 58Q30 55 50 58Q70 55 100 58L100 66L0 66Z", P.pres)
            rect(0f, 62.5f, 100f, 3.5f, P.sol)
            anthurium(86f, 60f)
            balisier(7f, 62f, 11f, feuille())
            balisier(93f, 62f, 10f, feuille())
        }

        // ------------------------------------------- la Cascade aux Écrevisses en papier

        /**
         * La Cascade aux Écrevisses en papier découpé, d'après la photo du
         * propriétaire (2026-11-08) : la chute en deux bras de part et d'autre
         * d'un rocher noir, à droite du mot ; à gauche la paroi couverte de
         * mousse vive, de fougères et de grandes feuilles ; à droite la berge
         * de roche sombre semée de feuilles mortes ; le bassin vert-bleu et,
         * devant, le fond de galets où les écrevisses marchent puis filent en
         * arrière sur la carte ouverte (`Peintre.bete`). La chute tombe
         * (`Cascade`), le colibri visite les balisiers.
         */
        fun cascadeEcrevissesPapier() {
            morneBoise(3f, 2f, 1701, papier(0xFF3E7A36.toInt()), 3f)
            morneBoise(10f, 1.6f, 1702, papier(0xFF2E6230.toInt()), 2.6f)
            val rl = Alea(1703)
            repeat(10) {
                val x = 30f + rl() * 50f
                barre(x, 8f, x + (rl() - 0.5f) * 0.6f, 18f + rl() * 10f, 0.15f, papier(0xFF5A6A3A.toInt()), 0f)
            }
            plan(1)
            // La paroi moussue de gauche, ses fougères, ses grandes feuilles.
            val crete = brisee(-2f, 10f, 20f, 12f, 40f, 14f, 56f, 16f, 68f, 19f, 72f, 24f)
            val mur = sousLaCrete(-2f, 72f, crete)
            mur.op(bosses(-3f, 70f, { x -> crete(x) + 0.4f }, 1.6f, 2.6f, Alea(1704)), Path.Op.UNION)
            mur.op(chemin("M-4 0L74 0L74 46Q40 45 -4 46Z"), Path.Op.INTERSECT)
            piece(mur, papier(0xFF3E7A2A.toInt()))
            taches(-2f, 70f, 45f, 18, 1705, papier(0xFF7AB43A.toInt()), crete)
            taches(-2f, 66f, 45f, 8, 1706, papier(0xFF2A5A24.toInt()), crete)
            pieceD("M-2 40Q20 38.6 40 41Q56 42 70 41L70 46L-2 46Z", papier(0xFF2E3A24.toInt()), 0.3f)
            val fougeres = intArrayOf(papier(0xFF6AB04A.toInt()), papier(0xFF4E9A3E.toInt()), papier(0xFF8CC456.toInt()))
            for ((fx, fy, fl, fa) in arrayOf(floatArrayOf(4f, 18f, 12f, -20f), floatArrayOf(18f, 22f, 11f, -150f), floatArrayOf(10f, 30f, 12f, -10f),
                    floatArrayOf(30f, 20f, 10f, -30f), floatArrayOf(-2f, 34f, 11f, -15f)))
                fougere(fx, fy, fl, fa, fougeres[(fx.toInt() and 0xFF) % 3])
            feuilleBananier(46f, 30f, 9f, -60f, papier(0xFF4FB06A.toInt()))
            feuilleBananier(52f, 31f, 8f, -110f, papier(0xFF3FA86A.toInt()))
            feuilleCoeur(40f, 34f, 3f, -12f, papier(VERT_PROFOND))
            // La roche noire de la chute.
            val roche = brisee(66f, 46f, 68f, 30f, 72f, 20f, 77f, 17.6f, 86f, 17.4f, 92f, 22f, 96f, 34f, 98f, 46f)
            piece(sousLaCrete(66f, 98f, roche).apply { op(chemin("M60 0L104 0L104 46L60 46Z"), Path.Op.INTERSECT) }, papier(0xFF2E2C2A.toInt()))
            pieceD("M88 46L89 26Q92 24 95 32L97 46Z", papier(0xFF3E3A36.toInt()), 0f)
            mousses(66f, 74f, 22f, 44f, 5, 1707)
            // La chute, ses deux bras, et le rocher noir qui les sépare.
            val (cx, ch, cb, cw) = CHUTE_ECREVISSES
            filets(cx - cw * 0.26f, ch + 0.6f, cb, cw * 0.48f, 1708)
            filets(cx + cw * 0.26f, ch, cb, cw * 0.4f, 1712)
            // Les fougères de droite, au-dessus de la berge.
            val droite = brisee(88f, 14f, 96f, 10f, 104f, 9f)
            val masse = sousLaCrete(86f, 104f, droite)
            masse.op(bosses(87f, 104f, { x -> droite(x) + 0.3f }, 1.4f, 2.2f, Alea(1709)), Path.Op.UNION)
            masse.op(chemin("M86 0L104 0L104 30Q94 26 92 18Q90 16 86 16Z"), Path.Op.INTERSECT)
            piece(masse, papier(0xFF3E8A34.toInt()))
            fougere(102f, 18f, 11f, -160f, fougeres[0])
            fougere(102f, 25f, 10f, -170f, fougeres[2])
            // Le bassin, l'écume au pied de la chute.
            pieceD("M-2 44.6Q40 43.6 70 45Q84 45.6 92 45L92 68L-2 68Z", papier(0xFF2E7470.toInt()), 0.3f)
            lanieresMer(45.6f, 60f, 1710, intArrayOf(papier(0xFF3A8480.toInt()), papier(0xFF266662.toInt()), papier(0xFF4E968E.toInt())))
            for ((ex, ey, el) in arrayOf(Triple(70f, 45.2f, 12f), Triple(73f, 46.6f, 9f), Triple(76f, 48f, 5f)))
                pieceD("M$ex ${ey}Q${ex + el / 2f} ${ey - 1f} ${ex + el} ${ey}Q${ex + el / 2f} ${ey + 0.7f} $ex ${ey}Z", blanc(), 0.2f)
            plan(2)
            // La berge de roche sombre à droite, ses feuilles mortes.
            pieceD("M88 68Q86 56 90 48Q94 44 104 42L104 68Z", papier(0xFF3A3632.toInt()), 0.7f)
            pieceD("M92 46Q96 43.4 104 42.6L104 50Q98 49 92 50Z", papier(0xFF6A9A36.toInt()), 0.3f)
            repeat(10) {
                feuillePliee(89f + rl() * 13f, 50f + rl() * 16f, 1.2f + rl() * 0.6f, rl() * 360f,
                    papier(if (rl() < 0.5f) 0xFFC8782E.toInt() else 0xFFA85A2A.toInt()), 0.4f)
            }
            // Le fond de galets, sous l'eau claire du bord.
            pieceD("M-2 60.6Q40 59.4 88 61L88 68L-2 68Z", papier(0xFF587A6A.toInt()), 0.2f)
            val rg = Alea(1711)
            repeat(46) {
                galet(-2f + rg() * 90f, 61.6f + rg() * 6f, 0.6f + rg() * 0.9f,
                    papier(if (rg() < 0.5f) 0xFF6E8A78.toInt() else 0xFF7E9282.toInt()))
            }
            balisier(9f, 62f, 14f, feuille())
            balisier(93f, 63f, 13f, feuille())
        }

        /** La Cascade aux Écrevisses : la chute large et basse, le bassin, une écrevisse sur une pierre. */
        fun cascadeEcrevisses() {
            if (PAPIER) { cascadeEcrevissesPapier(); return }
            val r = Alea(271)
            forme("M0 18Q20 8 50 14Q80 6 100 16L100 66L0 66Z", P.loin)
            bosquet(-2f, 102f, 21f, 3.6f, 272, mix(P.loin, P.milieu, 0.5f))
            plan(1)
            forme("M0 30Q50 24 100 30L100 66L0 66Z", P.milieu)
            bosquet(-2f, 102f, 29f, 3.2f, 273, mix(P.milieu, P.pres, 0.2f))
            forme("M26 31Q50 27 74 31L77 47L23 47Z", mix(P.pierre, P.pres, 0.5f))
            rect(40f, 30f, 20f, 17f, blanc(), 0.85f)
            val ecume = mix(P.eau, 0xFFFFFFFF.toInt(), 0.5f)
            for (k in 0 until 18) rect(40.5f + r() * 19f, 31f + r() * 13f, 0.3f, 2f + r() * 3f, ecume, 0.8f)
            plan(2)
            ovale(50f, 51f, 31f, 5.5f, P.eau)
            ovale(50f, 47.5f, 14f, 1.6f, blanc(), 0.9f)
            val roc = mix(P.pierre, P.pres, 0.35f)
            ovale(23f, 53f, 7f, 4f, roc); ovale(77f, 52f, 8f, 4f, roc); ovale(62f, 57f, 5f, 2.4f, roc)
            forme("M0 66L0 38Q10 42 16 52Q20 60 22 66Z", P.pres)
            forme("M100 66L100 36Q90 42 85 52Q81 60 79 66Z", P.pres)
            anthurium(88f, 57f)
            balisier(10f, 64f, 10f, feuille())
        }

        // ------------------------------------------- le Saut de la Lézarde en papier

        /** Un gros rocher de rivière : la masse, sa face éclairée, une tache de mousse. */
        private fun bloc(cx: Float, cy: Float, rx: Float, ry: Float, col: Int, mousse: Int) {
            val t = Path()
            t.moveTo(cx - rx, cy + ry * 0.6f)
            t.cubicTo(cx - rx * 1.05f, cy - ry * 0.7f, cx - rx * 0.4f, cy - ry, cx + rx * 0.1f, cy - ry)
            t.cubicTo(cx + rx * 0.8f, cy - ry, cx + rx * 1.05f, cy - ry * 0.3f, cx + rx, cy + ry * 0.6f)
            t.quadTo(cx, cy + ry * 1.1f, cx - rx, cy + ry * 0.6f)
            t.close()
            piece(t, col, 0.9f)
            val dessus = Path()
            dessus.moveTo(cx - rx * 0.85f, cy - ry * 0.3f)
            dessus.cubicTo(cx - rx * 0.6f, cy - ry * 0.95f, cx + rx * 0.6f, cy - ry * 1.02f, cx + rx * 0.85f, cy - ry * 0.35f)
            dessus.quadTo(cx, cy - ry * 0.55f, cx - rx * 0.85f, cy - ry * 0.3f)
            dessus.close()
            piece(dessus, mix(col, 0xFFFFFFFF.toInt(), 0.18f), 0f)
            piece(Path().apply { addOval(RectF(cx - rx * 0.7f, cy - ry * 0.2f, cx - rx * 0.1f, cy + ry * 0.35f), Path.Direction.CW) }, mousse, 0f)
        }

        /**
         * Le Saut de la Lézarde en papier découpé, d'après la photo du
         * propriétaire (2026-11-08) : au fond, la paroi de roche sombre sous la
         * forêt, ses lianes qui pendent, un mince filet d'eau à gauche et sa
         * dalle rousse au pied ; la chute à droite du mot, qui tombe dans le
         * bassin vert ; la dalle moussue à gauche, les rapides blancs qui
         * sortent du bassin en bas à gauche, les gros rochers moussus et le
         * gué de galets à droite. La chute tombe sur la carte ouverte
         * (`Cascade`), la libellule passe sur le bassin, le colibri visite les
         * balisiers.
         */
        fun sautLezardePapier() {
            // La forêt au-dessus de la paroi.
            morneBoise(4f, 2f, 1801, papier(0xFF3E7A36.toInt()), 3f)
            morneBoise(9f, 1.6f, 1802, papier(0xFF2A5A2C.toInt()), 2.6f)
            // La paroi sombre, ses strates, ses lianes, le filet d'eau de gauche.
            val paroi = brisee(-2f, 14f, 20f, 12.4f, 40f, 15f, 60f, 14.6f, 80f, 12f, 102f, 11f)
            val mur = sousLaCrete(-2f, 102f, paroi)
            mur.op(chemin("M-2 0L104 0L104 40L-2 40Z"), Path.Op.INTERSECT)
            piece(mur, papier(0xFF3A3430.toInt()))
            for ((y, col) in arrayOf(22f to 0xFF4A423C, 29f to 0xFF2E2A28, 34f to 0xFF443C36))
                pieceD("M-2 ${y}Q30 ${y - 1.2f} 60 ${y + 0.4f}Q80 ${y + 1f} 102 ${y - 0.6f}L102 ${y + 1.4f}Q70 ${y + 2f} 40 ${y + 1f}Q20 ${y + 0.6f} -2 ${y + 1.6f}Z",
                    papier(col.toInt()), 0f)
            val rl = Alea(1803)
            val liane = papier(0xFF5A6A3A.toInt())
            repeat(16) {
                val x = 2f + rl() * 70f
                barre(x, paroi(x) + 1f, x + (rl() - 0.5f) * 0.6f, paroi(x) + 6f + rl() * 14f, 0.16f + rl() * 0.12f, liane, 0f)
            }
            val feuilles = intArrayOf(papier(0xFF4E9A3E.toInt()), papier(0xFF6AB04A.toInt()), papier(0xFF3A7A34.toInt()))
            repeat(26) {
                val x = -2f + rl() * 104f
                feuillePliee(x, paroi(x) + rl() * 4f, 2.4f + rl() * 1.6f, 30f + rl() * 120f, feuilles[min((rl() * 3f).toInt(), 2)], 0.45f)
            }
            filets(9f, 15f, 33f, 1.6f, 1804)
            pieceD("M-2 35.4Q30 34 60 35.6Q80 36.4 102 35.6L102 39L-2 39Z", papier(0xFF8A4E3A.toInt()), 0.4f)
            pieceD("M-2 35.4Q30 34 60 35.6Q80 36.4 102 35.6L102 36.4Q70 37 40 36Q20 35.4 -2 36.4Z", papier(0xFFA86A50.toInt()), 0f)
            plan(1)
            // La roche de la chute, à droite, coiffée de forêt.
            val roche = brisee(74f, 40f, 76f, 22f, 80f, 15.6f, 88f, 14.6f, 96f, 13.4f, 102f, 13f)
            val pan = sousLaCrete(74f, 102f, roche)
            pan.op(bosses(76f, 103f, { x -> roche(x) + 0.3f }, 1.4f, 2.2f, Alea(1805)), Path.Op.UNION)
            pan.op(chemin("M70 0L104 0L104 40L70 40Z"), Path.Op.INTERSECT)
            piece(pan, papier(0xFF2E2A28.toInt()))
            pieceD("M77 40L78 24Q80 18 84 17.4L84 40Z", papier(0xFF443C36.toInt()), 0f)
            pieceD("M91 40L90 17.6Q95 16.4 99 18L100 40Z", papier(0xFF3A3430.toInt()), 0f)
            mousses(76f, 101f, 18f, 38f, 8, 1806)
            val (cx, ch, cb, cw) = CHUTE_LEZARDE
            filets(cx, ch, cb, cw, 1807)
            // Le bassin vert, ses lanières, l'écume au pied de la chute.
            pieceD("M-2 38.6L102 38.6L102 54L-2 54Z", papier(0xFF2E7A6E.toInt()), 0.3f)
            lanieresMer(39.2f, 52f, 1808, intArrayOf(papier(0xFF3E8A7C.toInt()), papier(0xFF266A62.toInt()), papier(0xFF5AA090.toInt())))
            for ((ex, ey, el) in arrayOf(Triple(80f, 40f, 9f), Triple(83f, 41.4f, 6f)))
                pieceD("M$ex ${ey}Q${ex + el / 2f} ${ey - 0.8f} ${ex + el} ${ey}Q${ex + el / 2f} ${ey + 0.5f} $ex ${ey}Z", blanc(), 0f)
            // La dalle moussue de gauche.
            pieceD("M-2 37.6Q14 36.6 26 38.6Q34 40 36 44Q30 48 20 49.4Q8 50.6 -2 51Z", papier(0xFF2E2A26.toInt()), 0.6f)
            pieceD("M-2 37Q14 36 26 38Q33 39.4 35 43Q24 42.4 12 43Q4 43.4 -2 44Z", papier(0xFF6A9A36.toInt()), 0.5f)
            taches(-2f, 34f, 43f, 10, 1809, papier(0xFF8CB848.toInt())) { x -> 37f + x * 0.05f }
            plan(2)
            // Les rapides blancs qui sortent du bassin, en bas à gauche.
            pieceD("M-2 52Q20 50 40 53L44 68L-2 68Z", papier(0xFF3E7A74.toInt()), 0.3f)
            val rr = Alea(1810)
            var y = 53f
            while (y < 67f) {
                var x = -2f + rr() * 3f
                while (x < 40f + (y - 53f) * 0.3f) {
                    val l = 3f + rr() * 6f; val e = 0.4f + rr() * 0.5f
                    piece(Path().apply { addRoundRect(RectF(x, y, x + l, y + e), e / 2f, e / 2f, Path.Direction.CW) },
                        if (rr() < 0.6f) blanc() else papier(0xFFB8DCD6.toInt()), 0.2f)
                    x += l + 0.6f + rr() * 2f
                }
                y += 1f + rr() * 0.6f
            }
            // Le gué clair à droite, ses galets, les gros rochers moussus.
            pieceD("M44 53Q70 51.6 102 52.4L102 68L48 68Z", papier(0xFF6A8A70.toInt()), 0.3f)
            repeat(40) {
                galet(48f + rr() * 54f, 54f + rr() * 13f, 0.6f + rr() * 0.8f, papier(if (rr() < 0.5f) 0xFF8A7E6A.toInt() else 0xFF6E6658.toInt()))
            }
            val pierre = papier(0xFF8A8072.toInt()); val mousse = papier(0xFF6E9A3A.toInt())
            bloc(60f, 56f, 6f, 3.4f, papier(0xFF7A7064.toInt()), mousse)
            bloc(74f, 59f, 8f, 4.4f, pierre, mousse)
            bloc(90f, 56.6f, 6f, 3.6f, papier(0xFF948A7C.toInt()), mousse)
            bloc(84f, 64.6f, 9f, 4.6f, papier(0xFF7E7468.toInt()), mousse)
            // Le bois flotté, à droite.
            barre(88f, 50.6f, 102f, 52.6f, 0.9f, papier(0xFFA89A84.toInt()), 0.4f)
            barre(94f, 51.4f, 98f, 48.6f, 0.4f, papier(0xFFA89A84.toInt()), 0.2f)
            balisier(9f, 62f, 14f, feuille())
            balisier(96f, 62f, 13f, feuille())
        }

        /** Le Saut de la Lézarde : la gorge étroite, la chute droite, le bassin sombre, les lianes. */
        fun sautLezarde() {
            if (PAPIER) { sautLezardePapier(); return }
            val r = Alea(281)
            forme("M36 10Q50 7 64 10L62 66L38 66Z", mix(P.pierre, P.sol, 0.55f))
            plan(1)
            rect(47f, 10f, 6f, 45f, blanc(), 0.9f)
            val ecume = mix(P.eau, 0xFFFFFFFF.toInt(), 0.5f)
            for (k in 0 until 14) rect(47.4f + r() * 5f, 11f + r() * 40f, 0.3f, 2f + r() * 4f, ecume, 0.8f)
            ovale(50f, 59f, 15f, 4f, mix(P.eau, P.sol, 0.35f))
            ovale(50f, 56f, 6f, 1.4f, blanc(), 0.9f)
            plan(2)
            forme("M0 0L38 0Q33 20 40 40Q36 55 42 66L0 66Z", P.milieu)
            forme("M100 0L62 0Q67 22 58 40Q62 55 56 66L100 66Z", P.milieu)
            val ombre = mix(P.milieu, P.sol, 0.45f)
            forme("M30 0L38 0Q33 20 40 40Q36 55 42 66L34 66Q30 50 33 36Q28 18 30 0Z", ombre)
            forme("M70 0L62 0Q67 22 58 40Q62 55 56 66L64 66Q68 52 65 40Q71 22 70 0Z", ombre)
            val bord = mix(P.milieu, P.pres, 0.3f)
            bordFeuillu(33f, 4f, 40f, 1f, 284, bord)
            bordFeuillu(67f, 4f, 40f, -1f, 285, bord)
            bosquet(-2f, 36f, 2f, 3.6f, 282, P.pres)
            bosquet(64f, 102f, 2f, 3.6f, 283, P.pres)
            trait("M37 2Q39 12 36 24M63 2Q61 14 65 22M40 4Q41 9 39 15", P.pres, 0.5f)
            anthurium(36f, 48f)
        }

        // ------------------------------------------- la Pointe de la Grande Vigie en papier

        /**
         * La Pointe de la Grande Vigie en papier découpé, d'après la photo du
         * propriétaire (2026-11-08) : les falaises de calcaire qui se suivent
         * en caps de plus en plus pâles vers le large, le plateau vert de la
         * Grande-Terre au-dessus ; la grande falaise sombre à droite du mot,
         * coiffée de broussaille, l'aiguille de roche détachée devant elle
         * dans l'écume ; l'océan bleu profond à gauche, où la baleine souffle ;
         * les raisiniers devant. La mer scintille sur la carte ouverte.
         */
        fun grandeVigiePapier() {
            cumulus(4f, 8f, 18f, 1901)
            // Le plateau au loin, ses falaises pâles, le cap du milieu.
            pieceD("M-2 12Q50 10.6 102 11L102 30L-2 30Z", mix(papier(0xFF6E9A4E.toInt()), P.cielBas, 0.4f), 0.3f)
            pieceD("M-2 17Q20 16.4 46 17.6L46 23.4Q20 22.6 -2 23Z", mix(papier(0xFFD8C4A4.toInt()), P.cielBas, 0.3f), 0.4f)
            // Des pans plus sombres dans la craie, pas des barreaux : en barres, la falaise faisait une palissade.
            for ((x0, l) in arrayOf(4f to 5f, 17f to 7f, 33f to 6f))
                pieceD("M$x0 17.6Q${x0 + l / 2f} 19 ${x0 + l} 17.8L${x0 + l - 1f} 23Q${x0 + l / 2f} 22.4 ${x0 + 0.6f} 23Z", mix(papier(0xFFB8A488.toInt()), P.cielBas, 0.3f), 0f)
            pieceD("M-2 22.6L102 22.6L102 68L-2 68Z", papier(0xFF1E5A9A.toInt()), 0.2f)
            pieceD("M-2 23Q20 22.4 46 23.4", papier(0xFFF2F6F6.toInt()), 0f)
            val cap = brisee(42f, 26f, 50f, 22.4f, 70f, 21.6f, 90f, 22f, 102f, 21.4f)
            val c = sousLaCrete(42f, 102f, cap)
            c.op(chemin("M40 0L104 0L104 30.6Q70 31 44 30Z"), Path.Op.INTERSECT)
            piece(c, mix(papier(0xFF6A645C.toInt()), P.cielBas, 0.2f))
            pieceD("M72 22.4L80 22.2L79 28L73 28.6Z", mix(papier(0xFFC89A72.toInt()), P.cielBas, 0.2f), 0f)
            pieceD("M44 22.6Q60 21 80 21.4Q92 21.4 102 20.6L102 22.4Q80 23 60 23Q50 23.4 44 24Z", mix(papier(0xFF5E9440.toInt()), P.cielBas, 0.2f), 0f)
            pieceD("M42 30Q60 29.4 102 30.4L102 31.2Q60 30.4 42 30.8Z", papier(0xFFF2F6F6.toInt()), 0f)
            plan(1)
            // L'océan, ses longues houles, ses moutons.
            pieceD("M-2 30L102 30L102 68L-2 68Z", papier(0xFF1E5A9A.toInt()), 0.2f)
            val rm = Alea(1902)
            val houle = intArrayOf(papier(0xFF2A70B0.toInt()), papier(0xFF184E8A.toInt()), papier(0xFF3A86C0.toInt()))
            repeat(24) {
                val y = 31f + rm() * 30f; val x = -2f + rm() * 70f; val l = (5f + rm() * 9f) * (0.5f + y / 50f)
                pieceD("M$x ${y}Q${x + l / 2f} ${y - 0.5f} ${x + l} ${y}Q${x + l / 2f} ${y + 0.4f} $x ${y}Z", houle[min((rm() * 3f).toInt(), 2)], 0f)
            }
            repeat(18) {
                val x = -2f + rm() * 64f; val y = 32f + rm() * 26f; val l = 0.8f + rm() * 1.4f
                pieceD("M$x ${y}Q${x + l / 2f} ${y - 0.3f} ${x + l} ${y}Q${x + l / 2f} ${y + 0.2f} $x ${y}Z", papier(0xFFF2F6F6.toInt()), 0f)
            }
            // La grande falaise sombre, sa broussaille, ses strates.
            val crete = brisee(60f, 33f, 64f, 29.6f, 74f, 28.4f, 88f, 27.4f, 102f, 26.6f)
            val face = Path()
            face.moveTo(58f, 53f); face.lineTo(57.4f, 44f); face.quadTo(57f, 38f, 60f, 36f); face.lineTo(60f, crete(60f))
            var x = 60f
            while (x <= 102f) { face.lineTo(x, crete(x)); x += 2f }
            face.lineTo(102f, 53f); face.close()
            piece(face, papier(0xFF5A5650.toInt()))
            val rr = Alea(1903)
            repeat(16) {
                val sx = 60f + rr() * 40f; val sy = crete(sx) + 3f + rr() * 18f; val l = 3f + rr() * 6f
                pieceD("M$sx ${sy}Q${sx + l * 0.4f} ${sy - 0.8f} ${sx + l} ${sy - 0.2f}Q${sx + l * 0.6f} ${sy + 0.6f} $sx ${sy}Z",
                    papier(if (rr() < 0.6f) 0xFF6E6862.toInt() else 0xFF46423E.toInt()), 0f)
            }
            pieceD("M66 ${crete(66f) + 0.6f}L72 ${crete(72f) + 0.6f}L71 36L67 36.4Z", papier(0xFFB88A66.toInt()), 0f)
            val brousse = Path()
            x = 60f
            brousse.moveTo(60f, crete(60f) + 1.4f)
            while (x <= 102f) { brousse.lineTo(x, crete(x) + 1.2f); x += 2f }
            x = 102f
            while (x >= 60f) { brousse.lineTo(x, crete(x) - 0.3f); x -= 2f }
            brousse.close()
            brousse.op(bosses(60f, 102f, { v -> crete(v) - 0.1f }, 0.6f, 1.1f, Alea(1904)), Path.Op.UNION)
            piece(brousse, papier(0xFF6E9A44.toInt()), 0.4f)
            // L'aiguille détachée devant la falaise, l'écume au pied de tout.
            pieceD("M64 54L64.6 46Q65.6 41.6 68.6 41.2Q72 41.6 73 45L74 54Z", papier(0xFF4E4A46.toInt()), 0.7f)
            pieceD("M68.6 41.2Q72 41.6 73 45L74 54L71 54Q71.6 46 68.6 41.2Z", papier(0xFF6A645E.toInt()), 0f)
            val ecume = papier(0xFFF4F8F8.toInt())
            for ((ex, ey, el) in arrayOf(Triple(55f, 53f, 9f), Triple(62f, 54.2f, 14f), Triple(76f, 53.4f, 12f), Triple(88f, 53f, 14f), Triple(70f, 55.4f, 8f)))
                pieceD("M$ex ${ey}Q${ex + el / 2f} ${ey - 1.1f} ${ex + el} ${ey}Q${ex + el / 2f} ${ey + 0.6f} $ex ${ey}Z", ecume, 0.2f)
            plan(2)
            // Les raisiniers de devant, leurs feuilles rondes.
            for ((x0, x1, y0) in arrayOf(Triple(-2f, 36f, 60f), Triple(62f, 102f, 57f))) {
                val haut = { v: Float -> y0 + sin(v * 0.3f) * 1.2f + ((v - (x0 + x1) / 2f) / (x1 - x0)).let { it * it } * 8f }
                val masse = sousLaCrete(x0, x1, haut)
                masse.op(bosses(x0, x1, { v -> haut(v) + 0.3f }, 1.4f, 2.4f, Alea((x0 * 7f).toInt() + 1905)), Path.Op.UNION)
                piece(masse, papier(0xFF4E8A34.toInt()))
                val rf = Alea((x1 * 3f).toInt() + 1906)
                repeat(22) {
                    val fx = x0 + rf() * (x1 - x0); val fy = haut(fx) + 0.6f + rf() * 7f
                    piece(disque(fx, fy, 0.9f + rf() * 0.6f), papier(if (rf() < 0.5f) 0xFF7AB84A.toInt() else 0xFF5E9E3A.toInt()), 0.3f)
                }
            }
        }

        /** La Pointe de la Grande Vigie : la falaise au nord de la Grande-Terre, la houle, Antigua au loin, une baleine. */
        fun grandeVigie() {
            if (PAPIER) { grandeVigiePapier(); return }
            forme("M76 34Q80 31.4 84 31.4Q88 31.4 92 34Z", P.loin)
            plan(1)
            eau(34f, 291)
            plan(2)
            forme("M0 26L52 24Q56 24 57 28L60 66L0 66Z", mix(P.pierre, P.milieu, 0.25f))
            forme("M52 24Q56 24 57 28L60 66L44 66Q49 46 52 24Z", mix(P.pierre, P.sol, 0.4f))
            forme("M0 25.2L52 23.4L52.6 25.4L0 28Z", P.milieu)
            raisinier(10f, 24.4f, 0.9f)
            raisinier(30f, 23.8f, 0.7f)
            turnera(18f, 25.6f, 22f, 25.2f, 38f, 24.6f, 44f, 24.4f, 6f, 26f)
            ecume("M58 58Q64 56 70 58Q76 60 82 58M60 63Q68 61 76 63")
        }

        /** La Porte d'Enfer : le lagon entre deux bras de falaise, la passe, la plage et ses touloulous. */
        fun porteEnfer() {
            if (PAPIER) { porteEnferPapier(); return }
            plan(1)
            eau(30f, 301)
            val falaise = mix(P.pierre, P.milieu, 0.3f)
            forme("M0 28L40 30Q44 31 44 34L44 46Q36 50 0 52Z", falaise)
            forme("M100 28L60 30Q56 31 56 34L56 46Q64 50 100 52Z", falaise)
            forme("M0 27.4L40 29.4L41 30.6L0 29.4Z", P.milieu)
            forme("M100 27.4L60 29.4L59 30.6L100 29.4Z", P.milieu)
            forme("M44 34L56 34L56 46Q64 50 100 52L100 58L0 58L0 52Q36 50 44 46Z", lagon())
            ecume("M45 35Q50 34 55 35")
            plan(2)
            forme("M0 58Q50 54 100 58L100 66L0 66Z", sable())
            raisinier(14f, 56f, 1.3f)
            amandier(86f, 58f, 12f)
        }

        // ------------------------------------------- la Porte d'Enfer en papier

        /**
         * La Porte d'Enfer en papier découpé, d'après la photo du propriétaire
         * (2026-11-08) : le lagon clair fermé par deux bras de terre boisés,
         * la falaise de calcaire du bras droit, à droite du mot, qui tombe
         * dans la passe où l'océan bleu sombre écume ; le rivage de roche
         * rousse au pied du morne, un bateau à cabine bleue au mouillage ;
         * la plage de sable devant, sa laisse d'algues, les touloulous. L'eau
         * du lagon scintille sur la carte ouverte.
         */
        fun porteEnferPapier() {
            cumulus(-4f, 22f, 24f, 2001)
            cumulus(56f, 16f, 18f, 2002)
            // L'océan, dans la passe entre les deux bras.
            pieceD("M20 36.8L80 36.8L80 41L20 41Z", papier(0xFF1E4E8A.toInt()), 0.2f)
            lanieresMer(37.2f, 40.4f, 2003, intArrayOf(papier(0xFF2A5E9A.toInt()), papier(0xFF183E74.toInt())))
            plan(1)
            // Le bras de droite : le morne boisé, sa falaise face à la passe.
            val crete = brisee(64f, 27.6f, 70f, 25.6f, 78f, 24.6f, 88f, 23.4f, 102f, 22.4f)
            val morne = sousLaCrete(64f, 102f, crete)
            morne.op(bosses(66f, 103f, { x -> crete(x) + 0.4f }, 1.2f, 2f, Alea(2004)), Path.Op.UNION)
            piece(morne, papier(0xFF3E7A2E.toInt()))
            taches(70f, 102f, 36f, 12, 2005, papier(0xFF5E9A3A.toInt()), crete)
            taches(74f, 102f, 36f, 6, 2006, papier(0xFF2A5A24.toInt()), crete)
            val calcaire = papier(0xFFA89C88.toInt())
            pieceD("M63.4 41L63 30.4Q63.6 27.6 66 27.2L70 26.8Q71 32 74 34.4Q76.4 37 80 38.4L80 41Z", calcaire, 0.7f)
            for ((x0, y0, x1) in arrayOf(Triple(64.6f, 29f, 64.2f), Triple(66.4f, 28f, 66.8f), Triple(68.4f, 28.4f, 68f), Triple(70.6f, 31f, 71f)))
                barre(x0, y0, x1, 40.6f, 0.45f, papier(0xFF7E7262.toInt()), 0f)
            pieceD("M63 39.2Q66 38.2 70 39.4L70 41L63 41Z", papier(0xFF4E463E.toInt()), 0f)
            // Le rivage de roche rousse au pied du morne.
            pieceD("M72 39.4Q86 37.6 102 38.2L102 42.4Q86 42 72 41.4Z", papier(0xFFB0805A.toInt()), 0.5f)
            val rr = Alea(2007)
            repeat(14) { galet(73f + rr() * 29f, 39.6f + rr() * 2.2f, 0.6f + rr() * 0.6f, papier(if (rr() < 0.5f) 0xFF8E6A4E.toInt() else 0xFF9E9282.toInt())) }
            // Le bras de gauche : bas, boisé, bordé de roche grise.
            val creteG = brisee(-2f, 29f, 12f, 30.4f, 24f, 33.4f, 34f, 36.6f, 38f, 38.4f)
            val gauche = sousLaCrete(-2f, 38f, creteG)
            gauche.op(bosses(-3f, 36f, { x -> creteG(x) + 0.4f }, 1.2f, 2.2f, Alea(2008)), Path.Op.UNION)
            piece(gauche, papier(0xFF2E6A2C.toInt()))
            taches(-2f, 34f, 40f, 9, 2009, papier(0xFF4E8A34.toInt()), creteG)
            pieceD("M31.6 39L32.6 35.6Q33.6 34.4 34.6 35.8L35.4 39.4Z", papier(0xFFB4A890.toInt()), 0.4f)
            pieceD("M-2 40.6Q14 39.4 28 39.6Q36 39.4 40 40.6L36 42.6Q20 44 6 46.6L-2 47.4Z", papier(0xFF8A847A.toInt()), 0.5f)
            repeat(12) { galet(-1f + rr() * 36f, 41f + rr() * 3.4f, 0.6f + rr() * 0.7f, papier(if (rr() < 0.5f) 0xFF6E6860.toInt() else 0xFFA29C90.toInt())) }
            // Le lagon : bleu au fond, turquoise, puis clair sur le sable.
            pieceD("M-2 47.4L6 46.6Q20 44 36 42.6L40 40.6Q60 41 72 41.4Q86 42 102 42.4L102 58L-2 58Z", papier(0xFF5AAEC8.toInt()), 0.3f)
            pieceD("M-2 50Q50 48 102 49L102 58L-2 58Z", papier(0xFF7CC6CC.toInt()), 0f)
            pieceD("M-2 53.6Q50 52.4 102 53.4L102 58L-2 58Z", papier(0xFFA8DCD2.toInt()), 0f)
            lanieresMer(43f, 56f, 2010, intArrayOf(papier(0xFF6ABACC.toInt()), papier(0xFF8ED0D0.toInt()), papier(0xFF4E9EC0.toInt())))
            // L'écume au pied de la falaise, dans la passe.
            for ((ex, ey, el) in arrayOf(Triple(60f, 40.6f, 3.4f), Triple(56f, 40.8f, 2f), Triple(66f, 41f, 2.4f)))
                pieceD("M$ex ${ey}Q${ex + el / 2f} ${ey - 0.7f} ${ex + el} ${ey}Q${ex + el / 2f} ${ey + 0.3f} $ex ${ey}Z", papier(0xFFF2F4F2.toInt()), 0f)
            // Le bateau au mouillage : coque blanche, cabine bleue.
            pieceD("M74 44.2L88 44.2L86.8 46.4Q81 47 75.6 46.4Z", papier(0xFFE8ECEE.toInt()), 0.5f)
            pieceD("M74 44.2L88 44.2L87.8 44.8L74.2 44.8Z", papier(0xFF4A5868.toInt()), 0f)
            pieceD("M80 44.2L80.6 41.6L85 41.6L85.4 44.2Z", papier(0xFF3A8ACC.toInt()), 0.4f)
            pieceD("M79.6 41.8L86 41.8L85.8 41L79.8 41Z", papier(0xFF2A5E8A.toInt()), 0f)
            pieceD("M86.6 43.4L88.4 43.4L88.4 45.6L86.6 45.6Z", papier(0xFF2E2E34.toInt()), 0.2f)
            plan(2)
            // La plage, sa laisse d'algues, le sable piqué.
            pieceD("M-2 56.6Q50 55.2 102 56L102 68L-2 68Z", papier(0xFFE6D2A8.toInt()), 0.5f)
            pieceD("M-2 56.6Q50 55.2 102 56L102 56.8Q50 56.2 -2 57.4Z", papier(0xFFF2E4C4.toInt()), 0f)
            val rs = Alea(2011)
            var lx = -2f
            while (lx < 102f) {
                // Des paquets d'algues de taille inégale, pas un trait.
                repeat(1 + (rs() * 4f).toInt()) {
                    val ax = lx + rs() * 2.4f; val ay = 57.4f + rs() * 1.2f; val ar = 0.25f + rs() * 0.45f
                    piece(Path().apply { addOval(RectF(ax - ar * 1.6f, ay - ar * 0.6f, ax + ar * 1.6f, ay + ar * 0.6f), Path.Direction.CW) },
                        papier(if (rs() < 0.6f) 0xFF6E6A4A.toInt() else 0xFF8A7A52.toInt()), 0f)
                }
                lx += 1f + rs() * 4f
            }
            repeat(60) {
                val x = -2f + rs() * 104f; val y = 59f + rs() * 8f
                piece(disque(x, y, 0.18f + rs() * 0.2f), papier(if (rs() < 0.5f) 0xFFC8B48C.toInt() else 0xFFF4EAD2.toInt()), 0f)
            }
        }

        // ------------------------------------------- Petite-Terre en papier

        /**
         * Petite-Terre en papier découpé, d'après la photo de l'iguane envoyée
         * par le propriétaire (2026-11-08) : au premier plan à gauche, la
         * broussaille basse où l'iguane des Petites Antilles se chauffe au
         * soleil (peint par `Peintre.bete`, il hoche la tête sur la carte
         * ouverte) ; derrière, le lagon turquoise, Terre-de-Bas basse et
         * boisée, son phare blanc à droite du mot ; la plage devant pour les
         * touloulous.
         */
        fun petiteTerrePapier() {
            cumulus(-6f, 22f, 22f, 2101)
            cumulus(54f, 14f, 20f, 2102)
            // Le large, puis Terre-de-Bas, basse, et son phare.
            pieceD("M-2 36.6L102 36.6L102 42L-2 42Z", papier(0xFF2A70A8.toInt()), 0.2f)
            lanieresMer(37f, 41.4f, 2103, intArrayOf(papier(0xFF3A82B8.toInt()), papier(0xFF205E98.toInt())))
            val ile = brisee(40f, 38.8f, 52f, 36.6f, 70f, 35.6f, 86f, 35f, 102f, 35.4f)
            val tdb = sousLaCrete(40f, 102f, ile)
            tdb.op(bosses(42f, 103f, { x -> ile(x) + 0.3f }, 0.9f, 1.4f, Alea(2104)), Path.Op.UNION)
            tdb.op(chemin("M38 0L104 0L104 40L38 40Z"), Path.Op.INTERSECT)
            piece(tdb, mix(papier(0xFF4E8A44.toInt()), P.cielBas, 0.3f))
            val px = 86f
            pieceD("M${px - 1.6f} 35.6L${px - 1.2f} 24L${px + 1.2f} 24L${px + 1.6f} 35.6Z", papier(0xFFF0EEE6.toInt()), 0.6f)
            pieceD("M${px + 0.4f} 35.6L${px + 0.3f} 24L${px + 1.2f} 24L${px + 1.6f} 35.6Z", papier(0xFFCFCBC2.toInt()), 0f)
            pieceD("M${px - 2f} 24.2L${px - 2f} 23.4L${px + 2f} 23.4L${px + 2f} 24.2Z", papier(0xFF3A3C40.toInt()), 0.2f)
            pieceD("M${px - 1.1f} 23.4L${px - 1f} 21.4L${px + 1f} 21.4L${px + 1.1f} 23.4Z",
                if (P.allume) duJour(0xFFFFE08A.toInt()) else papier(0xFF3C4450.toInt()), 0.2f)
            pieceD("M${px - 1.4f} 21.6L$px 20L${px + 1.4f} 21.6Z", papier(0xFF3A3C40.toInt()), 0.2f)
            pieceD("M38 39.6Q70 39 102 39.4L102 40.4L38 40.6Z", papier(0xFFF2E8CE.toInt()), 0f)
            plan(1)
            // Le lagon turquoise, très clair sur le sable.
            pieceD("M-2 40.4L102 40.4L102 68L-2 68Z", papier(0xFF3EBCB6.toInt()), 0.2f)
            pieceD("M-2 47Q50 46 102 47L102 68L-2 68Z", papier(0xFF72D4C6.toInt()), 0f)
            lanieresMer(41f, 52f, 2105, intArrayOf(papier(0xFF52C6BC.toInt()), papier(0xFF8ADCCC.toInt()), papier(0xFF30AAAA.toInt())))
            plan(2)
            // La plage à droite, pour les touloulous.
            pieceD("M30 56Q60 53.6 102 54.4L102 68L30 68Z", papier(0xFFF0E2C0.toInt()), 0.5f)
            pieceD("M30 56Q60 53.6 102 54.4L102 55.2Q60 54.6 30 56.8Z", papier(0xFFFAF4E2.toInt()), 0f)
            val rs = Alea(2106)
            repeat(40) {
                val x = 32f + rs() * 70f; val y = 56.6f + rs() * 10f
                piece(disque(x, y, 0.16f + rs() * 0.18f), papier(if (rs() < 0.5f) 0xFFD2BC92.toInt() else 0xFFFCF6E6.toInt()), 0f)
            }
            // La broussaille basse de gauche, où l'iguane se chauffe.
            val crete = brisee(-2f, 49.4f, 10f, 51f, 22f, 51.4f, 30f, 53f, 38f, 57f, 44f, 62f, 50f, 68f)
            val brousse = sousLaCrete(-2f, 50f, crete)
            brousse.op(bosses(-3f, 48f, { x -> crete(x) + 0.4f }, 1.2f, 2f, Alea(2107)), Path.Op.UNION)
            piece(brousse, papier(0xFF4E8A2E.toInt()))
            taches(-2f, 46f, 66f, 16, 2108, papier(0xFF7AB040.toInt()), crete)
            taches(-2f, 40f, 66f, 8, 2109, papier(0xFF2E6224.toInt()), crete)
            repeat(28) {
                val x = -2f + rs() * 44f
                feuillePliee(x, crete(x) + 1f + rs() * 10f, 1.2f + rs() * 0.8f, -160f + rs() * 140f,
                    papier(if (rs() < 0.5f) 0xFF8CC048.toInt() else 0xFF5E9A34.toInt()), 0.45f)
            }
            // Quelques brindilles sèches qui dépassent.
            val brindille = papier(0xFF9A8A6E.toInt())
            for ((bx, l, a) in arrayOf(Triple(6f, 5f, -70f), Triple(19f, 6f, -100f), Triple(27f, 4f, -60f), Triple(34f, 4.4f, -110f))) {
                val r = Math.toRadians(a.toDouble())
                barre(bx, crete(bx) + 0.6f, bx + l * cos(r).toFloat(), crete(bx) + 0.6f + l * sin(r).toFloat(), 0.18f, brindille, 0f)
            }
        }

        /** Petite-Terre : les deux îlets, le lagon, le phare, l'iguane des Petites Antilles, des touloulous. */
        fun petiteTerre() {
            if (PAPIER) { petiteTerrePapier(); return }
            plan(1)
            eau(34f, 311)
            forme("M0 40Q20 34 46 38L46 42L0 44Z", P.milieu)
            forme("M54 38Q76 33 100 37L100 43L54 42Z", P.milieu)
            raisinier(8f, 39.6f, 0.8f)
            raisinier(92f, 38.6f, 0.8f)
            rect(0f, 42.5f, 100f, 7f, lagon())
            rect(80f, 22f, 3f, 14f, duJour(0xFFF4F1EA.toInt()))
            rect(79.4f, 20f, 4.2f, 2.6f, P.toit)
            if (P.allume) rond(81.5f, 21.3f, 2.4f, P.fenetre, 0.5f)
            plan(2)
            forme("M0 56Q30 50 60 54Q80 52 100 56L100 66L0 66Z", sable())
            ovale(28f, 57.5f, 11f, 3.6f, mix(P.pierre, P.sol, 0.35f))
        }

        // ------------------------------------------- l'Îlet du Gosier en papier

        /**
         * L'Îlet du Gosier en papier découpé, d'après l'affiche envoyée par le
         * propriétaire (2026-11-08) : l'îlet posé sur le lagon, son anneau de
         * sable blanc, ses buissons, le phare blanc à lanterne rouge qui sort
         * de la verdure à droite du mot ; les cocotiers de l'affiche deviennent
         * des arbres à houppier rond (pas de cocotier). Une barque de bois sur
         * l'eau à gauche, le sable du rivage devant pour les touloulous. Le
         * lagon scintille sur la carte ouverte, la lanterne s'allume le soir.
         */
        fun iletGosierPapier() {
            cumulus(-4f, 24f, 22f, 2201)
            cumulus(58f, 15f, 24f, 2202)
            // Le large, bleu profond, à l'horizon.
            pieceD("M-2 38.6L102 38.6L102 44L-2 44Z", papier(0xFF2A7AB0.toInt()), 0.2f)
            lanieresMer(39f, 43.4f, 2203, intArrayOf(papier(0xFF3A8ABE.toInt()), papier(0xFF2068A0.toInt())))
            plan(1)
            // Le lagon turquoise, en lanières, plus clair vers soi.
            pieceD("M-2 43.6L102 43.6L102 68L-2 68Z", papier(0xFF3EB8B4.toInt()), 0.2f)
            pieceD("M-2 52Q50 50.6 102 52L102 68L-2 68Z", papier(0xFF5ACCC0.toInt()), 0f)
            lanieresMer(44.2f, 60f, 2204, intArrayOf(papier(0xFF4EC2BA.toInt()), papier(0xFF78D6C8.toInt()), papier(0xFF34A8AA.toInt())))
            // Le reflet de l'îlet, d'une seule pièce au bord ondulé.
            val ondes = Alea(2205)
            val reflet = StringBuilder("M52 45.6L102 45.6")
            var oy = 46f
            while (oy < 51f) { reflet.append("L${102f - (oy - 46f) * 0.6f} $oy"); oy += 1f }
            while (oy > 46f) { oy -= 1f; reflet.append("L${56f + (oy - 46f) * 1.8f + ondes() * 3f} $oy") }
            reflet.append("Z")
            pieceD(reflet.toString(), papier(0xFF8ACFB4.toInt()), 0f)
            // L'anneau de sable.
            pieceD("M50 44.4Q54 41.6 64 41Q84 40.4 102 41L102 46.2Q80 46.8 60 46.2Q52 45.8 50 44.4Z", papier(0xFFF2E8CE.toInt()), 0.5f)
            pieceD("M50 44.4Q52 45.8 60 46.2Q80 46.8 102 46.2L102 45.4Q80 46 60 45.4Q53 45 50 44.4Z", papier(0xFFD8C8A4.toInt()), 0f)
            // Les buissons de derrière, puis le phare qui en sort.
            val crete = brisee(55f, 41.6f, 62f, 38.4f, 70f, 36.4f, 80f, 35.2f, 90f, 36f, 98f, 38f, 102f, 39.4f)
            val dos = sousLaCrete(55f, 102f, crete)
            dos.op(bosses(56f, 103f, { x -> crete(x) + 0.3f }, 1.4f, 2.2f, Alea(2206)), Path.Op.UNION)
            dos.op(chemin("M50 0L104 0L104 43L50 43Z"), Path.Op.INTERSECT)
            piece(dos, papier(0xFF2E6A2E.toInt()))
            val px = 82f
            val blancTour = papier(0xFFF2F0EA.toInt())
            val rouge = papier(0xFFD03A2E.toInt())
            pieceD("M${px - 2.2f} 37L${px - 1.6f} 20.6L${px + 1.6f} 20.6L${px + 2.2f} 37Z", blancTour, 0.8f)
            pieceD("M${px + 0.6f} 37L${px + 0.5f} 20.6L${px + 1.6f} 20.6L${px + 2.2f} 37Z", papier(0xFFCFCBC2.toInt()), 0f)
            pieceD("M${px - 0.3f} 27L${px + 0.3f} 27L${px + 0.3f} 28.6L${px - 0.3f} 28.6Z", papier(0xFF3C4450.toInt()), 0f)
            pieceD("M${px - 2.8f} 20.8L${px - 2.8f} 20L${px + 2.8f} 20L${px + 2.8f} 20.8Z", papier(0xFF2E2E34.toInt()), 0.3f)
            val vitre = if (P.allume) duJour(0xFFFFE08A.toInt()) else papier(0xFF3C4450.toInt())
            pieceD("M${px - 1.5f} 20L${px - 1.4f} 17.6L${px + 1.4f} 17.6L${px + 1.5f} 20Z", vitre, 0.3f)
            barre(px, 20f, px, 17.6f, 0.15f, rouge, 0f)
            pieceD("M${px - 1.9f} 17.8L$px 15L${px + 1.9f} 17.8Z", rouge, 0.4f)
            barre(px, 15.2f, px, 13.8f, 0.2f, rouge, 0f)
            // Les buissons de devant, deux arbres ronds, des taches de lumière.
            val avant = brisee(52f, 44f, 60f, 41.4f, 70f, 39.6f, 82f, 38.4f, 94f, 39.6f, 102f, 41.4f)
            val bas = sousLaCrete(52f, 102f, avant)
            bas.op(bosses(53f, 103f, { x -> avant(x) + 0.3f }, 1.2f, 2f, Alea(2207)), Path.Op.UNION)
            bas.op(chemin("M50 0L104 0L104 43.8Q80 44.4 60 43.8Q54 43.6 52 44Z"), Path.Op.INTERSECT)
            piece(bas, papier(0xFF4E8E3A.toInt()))
            taches(54f, 102f, 42.6f, 10, 2208, papier(0xFF6EAA48.toInt()), avant)
            val tronc = papier(0xFF6A4A35.toInt())
            arbreRond(64f, 41.6f, 3.2f, papier(0xFF5A9A40.toInt()), tronc)
            arbreRond(96f, 42f, 2.8f, papier(0xFF4E8E3A.toInt()), tronc)
            // La barque de bois sur l'eau, à gauche.
            val bois = papier(0xFF8A5A34.toInt())
            pieceD("M4 52.4L22 52.4L19.6 55Q12 55.8 6 55Z", bois, 0.6f)
            pieceD("M4 52.4L22 52.4L21.8 53L4.2 53Z", papier(0xFFB07A48.toInt()), 0f)
            for (k in 0 until 3) barre(7f + k * 5f, 52.6f, 7.6f + k * 5f, 54.8f, 0.25f, papier(0xFF5E3C22.toInt()), 0f)
            pieceD("M5 55.6Q13 56.6 21 55.6Q13 57.4 5 55.6Z", papier(0xFF2E9A98.toInt()), 0f)
            plan(2)
            // Le sable du rivage, devant.
            pieceD("M-2 60.2Q50 58.8 102 60L102 68L-2 68Z", papier(0xFFEEDDB8.toInt()), 0.5f)
            pieceD("M-2 60.2Q50 58.8 102 60L102 60.8Q50 59.8 -2 61Z", papier(0xFFF8F0DC.toInt()), 0f)
            val rs = Alea(2209)
            repeat(40) {
                val x = -2f + rs() * 104f; val y = 61.6f + rs() * 5.6f
                piece(disque(x, y, 0.16f + rs() * 0.18f), papier(if (rs() < 0.5f) 0xFFCCB68C.toInt() else 0xFFFAF2E0.toInt()), 0f)
            }
        }

        /** L'îlet du Gosier : l'îlet et son phare tout proches, un canot, un pélican, des touloulous. */
        fun iletGosier() {
            if (PAPIER) { iletGosierPapier(); return }
            plan(1)
            eau(36f, 321)
            forme("M28 41Q50 33 74 41Z", sable())
            ovale(52f, 37f, 11f, 3.4f, P.milieu)
            ovale(63f, 38f, 7f, 2.4f, P.milieu)
            rect(38f, 26f, 2.4f, 13f, duJour(0xFFF4F1EA.toInt()))
            rect(37.6f, 24.6f, 3.2f, 1.8f, P.toit)
            if (P.allume) rond(39.2f, 25.5f, 2.2f, P.fenetre, 0.5f)
            ecume("M20 44Q26 43 32 44M70 45Q76 44 82 45")
            forme("M64 50L76 50L74 52.4L66 52.4Z", duJour(0xFF2F6FA6.toInt()))
            rect(64f, 49.6f, 12f, 0.5f, duJour(0xFFF4F1EA.toInt()))
            plan(2)
            forme("M0 57Q40 53 100 58L100 66L0 66Z", sable())
            raisinier(8f, 56f, 1.2f)
            amandier(90f, 58f, 10f)
        }

        // ------------------------------------------- le Grand Cul-de-Sac Marin en papier

        /** Une frégate posée, de profil vers la droite : le corps noir, la queue en ciseaux, le bec crochu. */
        private fun fregatePosee(x: Float, y: Float, s: Float, teteBlanche: Boolean) {
            val noir = papier(0xFF1E1C20.toInt())
            pieceD("M${x - s * 0.9f} ${y + s * 0.2f}L${x - s * 1.9f} ${y + s * 1.3f}L${x - s * 1.6f} ${y + s * 1.4f}L${x - s * 0.5f} ${y + s * 0.5f}Z", noir, 0.2f)
            piece(Path().apply { addOval(RectF(x - s, y - s * 0.55f, x + s * 0.6f, y + s * 0.5f), Path.Direction.CW) }, noir, 0.4f)
            if (teteBlanche) piece(Path().apply { addOval(RectF(x - s * 0.1f, y - s * 0.3f, x + s * 0.6f, y + s * 0.45f), Path.Direction.CW) },
                papier(0xFFEDEBE4.toInt()), 0f)
            val tete = if (teteBlanche) papier(0xFFEDEBE4.toInt()) else noir
            piece(disque(x + s * 0.55f, y - s * 0.5f, s * 0.32f), tete, 0.2f)
            pieceD("M${x + s * 0.8f} ${y - s * 0.6f}L${x + s * 1.5f} ${y - s * 0.5f}L${x + s * 1.5f} ${y - s * 0.32f}L${x + s * 0.8f} ${y - s * 0.4f}Z",
                papier(0xFF7A7A80.toInt()), 0f)
        }

        /**
         * Le Grand Cul-de-Sac Marin en papier découpé, d'après la photo du
         * propriétaire (2026-11-08) : un îlet de palétuviers posé sur l'eau
         * du lagon, à droite du mot, son dôme de feuillage sombre où les
         * frégates sont posées en rang, l'enchevêtrement de branches et de
         * racines-échasses dessous, des aigrettes blanches dedans ; au loin la
         * ligne basse de la mangrove et un petit îlet à gauche. Les frégates
         * volent au-dessus et l'eau bouge sur la carte ouverte ; la tortue
         * verte nage dans le lagon.
         */
        fun grandCulDeSacPapier() {
            // L'horizon : la ligne basse de la mangrove, le petit îlet à gauche.
            val loin = mix(papier(0xFF3E5A44.toInt()), P.cielBas, 0.45f)
            pieceD("M70 41.4Q84 40.2 102 40.6L102 42.4L70 42.4Z", loin, 0.3f)
            val ilot = Path()
            val ri = Alea(2301)
            repeat(7) { ilot.op(disque(2f + ri() * 10f, 40.6f + ri() * 1.2f, 0.9f + ri() * 1f), Path.Op.UNION) }
            ilot.op(chemin("M-2 41.6L16 41.6L16 42.6L-2 42.6Z"), Path.Op.UNION)
            piece(ilot, loin, 0.3f)
            plan(1)
            // Le lagon gris-vert, en lanières, plus sombre vers soi.
            pieceD("M-2 41.8L102 41.8L102 68L-2 68Z", papier(0xFF5E8E88.toInt()), 0.2f)
            lanieresMer(42.4f, 64f, 2302, intArrayOf(papier(0xFF6E9E96.toInt()), papier(0xFF4E7E7A.toInt()), papier(0xFF8AB4AC.toInt()), papier(0xFF5A8A84.toInt())))
            // Le reflet sombre de l'îlet, d'une seule pièce au bord ondulé.
            val ondes = Alea(2303)
            val reflet = StringBuilder("M60 46.4L100 46.4L102 46.6")
            var oy = 46.6f
            while (oy < 54f) { reflet.append("L${100f - (oy - 46f) * 0.8f + ondes() * 3f} $oy"); oy += 1f }
            while (oy > 46.6f) { oy -= 1f; reflet.append("L${64f + (oy - 46f) * 1.4f + ondes() * 3f} $oy") }
            reflet.append("Z")
            pieceD(reflet.toString(), papier(0xFF2E4A44.toInt()), 0f)
            // Le dôme de feuillage : un petit lobe bas, un grand à droite.
            fun crete(x: Float): Float {
                val a = 33f + ((x - 68f) / 6f).let { it * it } * 4f
                val b = 21f + ((x - 85f) / 15f).let { it * it } * 13f
                return min(a, b)
            }
            val rc = Alea(2304)
            // Les branches et les racines-échasses sous le feuillage.
            val bois = papier(0xFF4E4440.toInt())
            val boisClair = papier(0xFF8A7C70.toInt())
            var bx = 63f
            while (bx < 99f) {
                val haut = crete(bx) + 4f + rc() * 3f
                val mx = bx + (rc() - 0.5f) * 8f
                val my = (haut + 46f) / 2f
                barre(bx + (rc() - 0.5f) * 4f, 46.4f, mx, my, 0.35f + rc() * 0.25f, if (rc() < 0.3f) boisClair else bois, 0.2f)
                barre(mx, my, mx + (rc() - 0.5f) * 10f, haut, 0.3f + rc() * 0.2f, if (rc() < 0.3f) boisClair else bois, 0.2f)
                bx += 1.2f + rc() * 1.4f
            }
            val feuillage = Path()
            var fx = 62f
            while (fx < 103f) {
                val c = crete(fx)
                repeat(3) { feuillage.op(disque(fx + (rc() - 0.5f) * 2f, c + 2f + rc() * 6f, 2f + rc() * 1.8f), Path.Op.UNION) }
                fx += 1.6f
            }
            piece(feuillage, papier(0xFF2A4A2C.toInt()), 0.8f)
            repeat(30) {
                val x = 63f + rc() * 38f
                rosette(x, crete(x) + 1.6f + rc() * 3f, 1.4f, papier(if (rc() < 0.5f) 0xFF4E7A3A.toInt() else 0xFF3E6A34.toInt()), rc)
            }
            // Les frégates posées en rang sur le dôme.
            for (x in floatArrayOf(66f, 71.4f, 77f, 82.4f, 88f, 93f, 98f))
                fregatePosee(x, crete(x) + 0.6f + rc() * 0.8f, 1.7f + rc() * 0.3f, rc() < 0.35f)
            // Les aigrettes blanches dans les branches, à gauche de l'îlet.
            val aigrette = papier(0xFFF2F0EA.toInt())
            for ((ax, ay) in arrayOf(66f to 40f, 69.4f to 42.4f, 72f to 38.6f, 75f to 41.8f, 80f to 40.4f)) {
                piece(Path().apply { addOval(RectF(ax - 0.6f, ay - 0.9f, ax + 0.6f, ay + 0.9f), Path.Direction.CW) }, aigrette, 0.3f)
                piece(disque(ax + 0.2f, ay - 1.2f, 0.35f), aigrette, 0f)
            }
            // Les racines-échasses au ras de l'eau.
            val racines = intArrayOf(papier(0xFF5A4A40.toInt()), papier(0xFF7A6658.toInt()))
            var rx = 61f
            while (rx < 100f) {
                val l = 3f + rc() * 6f
                racine(rx, 46.6f, rx + l, 46.4f + rc() * 0.4f, 2.4f + rc() * 3f, 0.3f + rc() * 0.2f, racines[(rc() * 2f).toInt().coerceAtMost(1)])
                rx += 1.2f + rc() * 2f
            }
            plan(2)
            // L'eau du devant, plus sombre, ses rides.
            pieceD("M-2 62.6Q50 61.8 102 62.8L102 68L-2 68Z", papier(0xFF3E6A66.toInt()), 0.3f)
            lanieresMer(63.2f, 67f, 2305, intArrayOf(papier(0xFF4E7E78.toInt()), papier(0xFF6A9A92.toInt())))
        }

        /** Le Grand Cul-de-Sac Marin : le lagon, la mangrove, le banc de sable de l'îlet Caret, une tortue verte. */
        fun grandCulDeSac() {
            if (PAPIER) { grandCulDeSacPapier(); return }
            forme("M0 30Q10 22 22 26Q34 16 48 24Q60 20 72 28Q86 24 100 30L100 40L0 40Z", P.loin)
            plan(1)
            forme("M0 38.6Q50 35.6 100 38.6L100 42L0 42Z", P.milieu)
            bosquet(-2f, 102f, 39f, 1.6f, 331, P.milieu)
            rect(0f, 41.6f, 100f, 24.4f, lagon())
            rect(0f, 41.6f, 100f, 4f, P.eau, 0.6f)
            forme("M20 52Q50 46 82 52Q50 54.4 20 52Z", sable())
            ovale(44f, 49.4f, 2.6f, 1.6f, P.pres)
            ovale(49f, 49.2f, 1.8f, 1.1f, P.pres)
            ecume("M14 56Q20 55 26 56M70 58Q78 57 86 58")
            forme("M60 59Q66 60.6 72 59L71 60.6Q66 62 61 60.6Z", duJour(0xFFF4F1EA.toInt()))
            forme("M61 60.6Q66 62 71 60.6L70.8 61.2Q66 62.6 61.4 61.2Z", duJour(0xFFD6452E.toInt()))
            plan(2)
            paletuvier(7f, 60f)
        }

        // ------------------------------------------- Gueule Grand Gouffre en papier

        /**
         * Gueule Grand Gouffre en papier découpé, d'après la photo du
         * propriétaire (2026-11-08), vue d'en haut : l'océan turquoise qui
         * monte jusqu'à un horizon haut, la pointe boisée au loin à droite et
         * son écume ; l'arête de calcaire coiffée d'herbe qui monte en biais
         * vers la droite, percée de l'arche où la mer s'engouffre, à droite
         * du mot ; les blocs clairs du bord devant. La mer scintille sur la
         * carte ouverte.
         */
        fun gueuleGrandGouffrePapier() {
            // L'océan, bleu profond au large, turquoise vers la côte.
            pieceD("M-2 12.6L102 12.6L102 68L-2 68Z", papier(0xFF2A72A8.toInt()), 0.2f)
            pieceD("M-2 20Q50 19 102 21L102 68L-2 68Z", papier(0xFF1E9AB8.toInt()), 0f)
            pieceD("M-2 30Q50 29 102 31L102 68L-2 68Z", papier(0xFF22B4C0.toInt()), 0f)
            // Peu de lanières, longues : serrées, leurs tranches de kraft
            // brunissent la mer.
            val rm = Alea(2402)
            val houle = intArrayOf(papier(0xFF2A86B4.toInt()), papier(0xFF46C2CA.toInt()), papier(0xFF1E8AB0.toInt()))
            repeat(22) {
                val y = 14f + rm() * 34f; val x = -2f + rm() * 90f; val l = (5f + rm() * 10f) * (0.5f + y / 40f)
                pieceD("M$x ${y}Q${x + l / 2f} ${y - 0.5f} ${x + l} ${y}Q${x + l / 2f} ${y + 0.4f} $x ${y}Z", houle[min((rm() * 3f).toInt(), 2)], 0f)
            }
            repeat(26) {
                val x = -2f + rm() * 70f; val y = 18f + rm() * 30f; val l = 0.8f + rm() * 1.6f * (y / 30f)
                pieceD("M$x ${y}Q${x + l / 2f} ${y - 0.3f} ${x + l} ${y}Q${x + l / 2f} ${y + 0.2f} $x ${y}Z", papier(0xFFF2F6F6.toInt()), 0f)
            }
            // La pointe boisée au loin, sa falaise, l'écume à son pied.
            val pointe = brisee(68f, 26f, 74f, 21f, 82f, 18.4f, 92f, 17.4f, 102f, 17f)
            val p = sousLaCrete(68f, 102f, pointe)
            p.op(chemin("M66 0L104 0L104 29L66 29Z"), Path.Op.INTERSECT)
            piece(p, papier(0xFF8A8478.toInt()))
            val vert = { x: Float -> pointe(x) }
            val herbe = sousLaCrete(70f, 102f, vert)
            herbe.op(chemin("M66 0L104 0L104 23Q88 22 76 24.6L72 24Z"), Path.Op.INTERSECT)
            herbe.op(bosses(72f, 103f, { x -> pointe(x) + 0.2f }, 0.7f, 1.2f, Alea(2403)), Path.Op.UNION)
            piece(herbe, papier(0xFF4E8A3A.toInt()))
            for ((ex, ey, el) in arrayOf(Triple(64f, 28f, 6f), Triple(70f, 29f, 5f), Triple(60f, 30.4f, 4f)))
                pieceD("M$ex ${ey}Q${ex + el / 2f} ${ey - 0.7f} ${ex + el} ${ey}Q${ex + el / 2f} ${ey + 0.4f} $ex ${ey}Z", papier(0xFFF2F6F6.toInt()), 0f)
            plan(1)
            // L'arête de calcaire qui monte vers la droite, coiffée d'herbe.
            val arete = brisee(-2f, 50f, 20f, 47f, 40f, 43f, 56f, 38f, 70f, 32.6f, 84f, 27.4f, 102f, 22f)
            val roc = sousLaCrete(-2f, 102f, arete)
            // L'arche : la mer passe dessous, à droite du mot.
            val arche = chemin("M75.4 70L76 52Q77.6 44 84 43.4Q91 44 92.4 52L93 70Z")
            roc.op(arche, Path.Op.DIFFERENCE)
            piece(roc, papier(0xFF8E867C.toInt()))
            // Les strates, la face ombrée sous la crête, l'ombre dans l'arche.
            val rr = Alea(2407)
            repeat(26) {
                val x = -2f + rr() * 104f
                val y = arete(x) + 2.4f + rr() * 18f
                val l = 3f + rr() * 6f
                if (x + l < 75f || x > 93f || y < 42f)
                    pieceD("M$x ${y}Q${x + l * 0.4f} ${y - 1f - rr()} ${x + l} ${y - 0.3f}Q${x + l * 0.6f} ${y + 0.8f} $x ${y}Z",
                        papier(if (rr() < 0.6f) 0xFF8A7E70.toInt() else 0xFFB8AE9E.toInt()), 0f)
            }
            pieceD("M93 70L92.4 52Q91 44 84 43.4Q90 42 96 40Q100 38 102 36L102 70Z", papier(0xFF6E6458.toInt()), 0.3f)
            val dedans = Path(arche)
            piece(dedans, papier(0xFF1E8EA4.toInt()), 0f)
            for ((y, x0, l) in arrayOf(Triple(53f, 79f, 6f), Triple(56.6f, 78f, 9f), Triple(60.4f, 77.4f, 11f), Triple(64f, 77f, 12f)))
                pieceD("M$x0 ${y}Q${x0 + l / 2f} ${y - 0.5f} ${x0 + l} ${y}Q${x0 + l / 2f} ${y + 0.4f} $x0 ${y}Z", papier(0xFF5ACCD2.toInt()), 0f)
            pieceD("M77 66Q83 64.6 92 66Q85 67.2 77 66Z", papier(0xFFF2F6F6.toInt()), 0f)
            pieceD("M76 52Q77.6 44 84 43.4L84 44.8Q79 45.6 77.4 52.6L77 68L75.8 68Z", papier(0xFF544A40.toInt()), 0f)
            // L'herbe de la crête.
            val gazon = Path()
            var gx = -2f
            gazon.moveTo(-2f, arete(-2f) + 1.4f)
            while (gx <= 102f) { gazon.lineTo(gx, arete(gx) + 1.2f + sin(gx * 0.7f) * 0.2f); gx += 2f }
            gx = 102f
            while (gx >= -2f) { gazon.lineTo(gx, arete(gx) - 0.2f); gx -= 2f }
            gazon.close()
            gazon.op(bosses(-2f, 102f, { x -> arete(x) - 0.1f }, 0.5f, 0.9f, Alea(2404)), Path.Op.UNION)
            piece(gazon, papier(0xFF5E9A3A.toInt()), 0.4f)
            plan(2)
            // Les blocs clairs du bord, devant à droite.
            val bord = brisee(60f, 70f, 66f, 64f, 74f, 62f, 84f, 61f, 94f, 58f, 102f, 56f)
            val blocs = sousLaCrete(54f, 102f, bord)
            blocs.op(bosses(62f, 103f, { x -> bord(x) + 0.6f }, 1.2f, 2.4f, Alea(2405)), Path.Op.UNION)
            piece(blocs, papier(0xFFC6C0B4.toInt()))
            taches(64f, 102f, 68f, 10, 2406, papier(0xFFA8A296.toInt()), bord)
            repeat(6) {
                val x = 66f + rm() * 34f
                feuillePliee(x, bord(x) + 1f + rm() * 3f, 1.2f, -150f + rm() * 120f, papier(0xFF6E9A3A.toInt()), 0.45f)
            }
        }

        /** Gueule Grand Gouffre : la falaise de Marie-Galante percée d'un gouffre où la mer s'engouffre. */
        fun gueuleGrandGouffre() {
            if (PAPIER) { gueuleGrandGouffrePapier(); return }
            plan(1)
            eau(40f, 341)
            ecume("M84 50Q90 48 96 50M82 58Q90 56 98 58")
            plan(2)
            forme("M0 22L70 24Q78 25 80 30L84 66L0 66Z", mix(P.pierre, P.milieu, 0.25f))
            forme("M0 21.4L70 23.4L71 25L0 24Z", P.milieu)
            bosquet(4f, 40f, 23f, 1.6f, 342, P.pres)
            turnera(8f, 23.6f, 16f, 23.8f, 24f, 24f, 34f, 24.2f, 44f, 24.4f)
            forme("M48 66C48 50 52 40 61.5 40C71 40 75 50 75 66Z", mix(P.pierre, 0xFF000000.toInt(), 0.62f))
            forme("M51 66Q53 55 61.5 53Q70 55 72 66Z", P.eau)
            ovale(61.5f, 56f, 6f, 1.4f, blanc(), 0.7f)
        }

        // ------------------------------------------- la Traversée en papier

        /**
         * Une fougère basse : une tige qui s'arque et retombe, ses folioles
         * pliées de plus en plus courtes vers la pointe. Toujours couchée :
         * dressée, elle se lirait comme un sapin.
         */
        private fun fougere(x: Float, y: Float, l: Float, angle: Float, col: Int) {
            val a = Math.toRadians(angle.toDouble()).toFloat()
            val sens = if (cos(a) >= 0f) 1f else -1f
            val tige = mix(col, 0xFF000000.toInt(), 0.25f)
            val n = 9
            var px = x; var py = y
            for (k in 1..n) {
                val u = k / n.toFloat()
                val ang = a + sens * u * 1.1f
                val nx = px + cos(ang) * l / n; val ny = py + sin(ang) * l / n
                barre(px, py, nx, ny, 0.28f * (1f - u * 0.5f), tige, 0f)
                val f = l * 0.3f * (1f - u * 0.7f)
                val deg = Math.toDegrees(ang.toDouble()).toFloat()
                feuillePliee(nx, ny, f, deg - 65f * sens, col, 0.38f)
                feuillePliee(nx, ny, f * 0.9f, deg + 75f * sens, mix(col, 0xFF000000.toInt(), 0.1f), 0.38f)
                px = nx; py = ny
            }
        }

        /** Une rambarde de bois en perspective, de ([ax], [ay]) loin à ([bx], [by]) près : poteaux, deux lisses. */
        private fun rambardeFuyante(ax: Float, ay: Float, bx: Float, by: Float, ha: Float, hb: Float, poteaux: Int, col: Int) {
            fun pt(u: Float) = floatArrayOf(ax + (bx - ax) * u * u, ay + (by - ay) * u * u, ha + (hb - ha) * u * u)
            val sombre = mix(col, 0xFF000000.toInt(), 0.2f)
            for (lisse in floatArrayOf(0.45f, 1f)) {
                var prec = pt(0f)
                for (k in 1..24) {
                    val q = pt(k / 24f)
                    barre(prec[0], prec[1] - prec[2] * lisse, q[0], q[1] - q[2] * lisse, 0.15f + q[2] * 0.09f,
                        if (lisse < 1f) sombre else col, 0.3f)
                    prec = q
                }
            }
            for (k in 0..poteaux) {
                val q = pt(k / poteaux.toFloat())
                val e = 0.25f + q[2] * 0.14f
                barre(q[0], q[1], q[0], q[1] - q[2] * 1.08f, e, col, 0.5f)
                pieceD("M${q[0] - e / 2f} ${q[1] - q[2] * 1.08f}L${q[0] + e / 2f} ${q[1] - q[2] * 1.08f}L${q[0] + e / 2f} ${q[1]}L${q[0] + e * 0.2f} ${q[1]}Z",
                    sombre, 0f)
            }
        }

        /**
         * La route de la Traversée en papier découpé, d'après la photo du
         * propriétaire (2026-11-08) : le chemin pavé du parc national qui
         * s'enfonce sous la forêt entre ses deux rambardes de bois et tourne
         * à gauche au fond, la lumière qui tombe au bout, les grands troncs,
         * un très gros à droite du mot, pris de lianes ; les fougères et les
         * grandes feuilles des deux bords. Personne sur le chemin : l'agouti
         * le traverse sur la carte ouverte. Balisiers pour le colibri.
         */
        fun traverseePapier() {
            // Le fond baigné de lumière, des troncs pâles dans la brume.
            morneBoise(6f, 2.4f, 2501, mix(papier(0xFF9CCB84.toInt()), P.cielBas, 0.5f), 2.6f)
            val pale = mix(papier(0xFF7E8E70.toInt()), P.cielBas, 0.4f)
            for ((x, e) in arrayOf(30f to 0.7f, 38f to 0.5f, 61f to 0.6f, 66f to 0.8f)) barre(x, 44f, x + 0.8f, 2f, e, pale, 0f)
            morneBoise(17f, 2.6f, 2504, mix(papier(0xFF7DB666.toInt()), P.cielBas, 0.35f), 3f)
            brumePapier(30f, 72f, 24f, 5f, 2505)
            morneBoise(27f, 2f, 2502, mix(papier(0xFF5E9E52.toInt()), P.cielBas, 0.25f), 2.8f)
            morneBoise(35f, 1.6f, 2506, mix(papier(0xFF7AB868.toInt()), P.cielBas, 0.2f), 2.4f)
            brumePapier(30f, 72f, 39f, 3.4f, 2503)
            plan(1)
            // Les deux bords de forêt, en lisières, qui descendent vers le chemin.
            val verts = intArrayOf(papier(0xFF4E8E46.toInt()), papier(0xFF2E6A3A.toInt()), papier(0xFF6FA84E.toInt()))
            for (k in 0 until 3) {
                rive(true, 8f + k * 9f, 42f + k * 3f, 40f - k * 5f, 2510 + k, verts[k], 3.4f + k * 0.5f)
                rive(false, 10f + k * 9f, 42f + k * 3f, 60f + k * 5f, 2520 + k, verts[(k + 1) % 3], 3.4f + k * 0.5f)
            }
            // Un tronc fin qui monte en biais à gauche.
            val ecorce = papier(0xFF5A4E3E.toInt())
            barre(33f, 47f, 25f, -2f, 1.3f, ecorce, 0.5f)
            barre(33.2f, 47f, 25.4f, -2f, 0.35f, mix(ecorce, 0xFFFFFFFF.toInt(), 0.2f), 0f)
            // Le gros tronc à droite du mot : ses contreforts, sa mousse, ses lianes.
            val gros = papier(0xFF4A4036.toInt())
            pieceD("M70.4 -2L77.6 -2Q77 20 78 34Q79 42 84 47L64 47Q69 42 70 34Q71 20 70.4 -2Z", gros, 0.8f)
            pieceD("M75.6 -2L77.6 -2Q77 20 78 34Q79 42 84 47L80.4 47Q77 40 76.2 30Q75.6 14 75.6 -2Z", mix(gros, 0xFFFFFFFF.toInt(), 0.12f), 0f)
            for ((y, l) in arrayOf(8f to 5f, 18f to 4f, 30f to 6f)) feuillePliee(71f, y, l, 95f, papier(0xFF5E9A44.toInt()), 0.35f)
            val liane = papier(0xFF6A7A48.toInt())
            barre(72.2f, -2f, 71.4f, 30f, 0.35f, liane, 0.2f)
            barre(74.4f, -2f, 75.6f, 22f, 0.3f, liane, 0.2f)
            barre(82f, -2f, 81.2f, 26f, 0.3f, liane, 0.2f)
            // Le chemin pavé, qui tourne à gauche au fond.
            val pave = papier(0xFF8C8478.toInt())
            pieceD("M20 68L44.4 48Q43.4 45.6 40 44.6L48.6 44.6Q52 45.6 54.2 48L80 68Z", pave, 0.5f)
            fun gauche(y: Float) = 44.4f - (y - 48f) / 20f * 24.4f
            fun droite(y: Float) = 54.2f + (y - 48f) / 20f * 25.8f
            val pierres = intArrayOf(papier(0xFFA49A8C.toInt()), papier(0xFF9A9284.toInt()), papier(0xFF7A7266.toInt()))
            val rp = Alea(2530)
            var y = 48.6f
            while (y < 67f) {
                val p = (y - 48f) / 20f
                val e = 0.4f + 1.2f * p
                var x = gauche(y) + 0.4f + rp() * 1.2f
                while (x < droite(y) - 1f) {
                    val l = (1.4f + rp() * 1.6f) * (0.5f + p * 1.4f)
                    val t = Path()
                    t.addRoundRect(RectF(x, y, min(x + l, droite(y) - 0.4f), y + e), e * 0.4f, e * 0.4f, Path.Direction.CW)
                    piece(t, pierres[min((rp() * 3f).toInt(), 2)], 0.3f)
                    x += l + 0.3f + p * 0.4f
                }
                y += e + 0.25f + 0.3f * p
            }
            // Les taches de soleil sur les pavés, le jour.
            if (!P.allume) for ((tx, ty, tl) in arrayOf(Triple(47f, 51f, 3f), Triple(40f, 57f, 5f), Triple(56f, 61f, 6f)))
                pieceD("M$tx ${ty}Q${tx + tl / 2f} ${ty - tl * 0.18f} ${tx + tl} ${ty}Q${tx + tl / 2f} ${ty + tl * 0.18f} $tx ${ty}Z",
                    mix(papier(0xFFE8E0B8.toInt()), pave, 0.25f), 0f)
            val bois = papier(0xFF8A5634.toInt())
            rambardeFuyante(43.4f, 46.4f, 17f, 70f, 1.6f, 9f, 9, bois)
            rambardeFuyante(55f, 46.6f, 83f, 70f, 1.6f, 9f, 9, bois)
            plan(2)
            // Les deux bords de sous-bois : fougères, grandes feuilles, balisiers.
            val fougeres = intArrayOf(papier(0xFF5EA04A.toInt()), papier(0xFF3E8A44.toInt()), papier(0xFF7AB654.toInt()))
            for ((fx, fy, fl, fa) in arrayOf(
                floatArrayOf(-2f, 54f, 14f, -25f), floatArrayOf(2f, 60f, 15f, -15f), floatArrayOf(6f, 66f, 13f, -30f),
                floatArrayOf(102f, 55f, 14f, -155f), floatArrayOf(98f, 61f, 15f, -165f), floatArrayOf(95f, 67f, 12f, -150f)))
                fougere(fx, fy, fl, fa, fougeres[((fx + fy).toInt() and 0xFF) % 3])
            feuilleBananier(-2f, 66f, 13f, -40f, papier(0xFF4FB06A.toInt()))
            feuilleCoeur(18f, 67f, 4f, -10f, papier(VERT_PROFOND))
            feuilleBananier(102f, 66f, 12f, -142f, papier(0xFF3FA86A.toInt()))
            feuilleCoeur(84f, 67f, 3.6f, 14f, papier(VERT_PROFOND))
            balisier(12f, 61f, 14f, feuille())
            balisier(88f, 61f, 14f, feuille())
        }

        // ------------------------------------------- la Grivelière en papier

        /**
         * L'habitation La Grivelière en papier découpé, d'après la photo du
         * propriétaire (2026-11-08) : la grande case de bois gris sur son
         * soubassement de pierre, le toit de tôle rouillée à trois lucarnes,
         * l'aile basse au toit rayé rouge et blanc, à droite du mot ; à gauche
         * le petit oratoire blanc à niche cintrée et son pot de fleurs ; le
         * jardin de crotons sur son mur de pierres sèches, le chemin pavé
         * devant, le morne boisé derrière. Balisiers pour le colibri.
         */
        fun grivelierePapier() {
            morneBoise(8f, 3f, 2601, mix(papier(0xFF6EA866.toInt()), P.cielBas, 0.45f), 2.4f)
            morneBoise(18f, 2.6f, 2602, mix(papier(0xFF3E7A44.toInt()), P.cielBas, 0.22f), 2.8f)
            plan(1)
            morneBoise(29f, 2.2f, 2603, papier(0xFF2E6236.toInt()), 3f)
            val tronc = papier(0xFF4A3A2E.toInt())
            arbreRond(6f, 40f, 5f, papier(0xFF4E8E46.toInt()), tronc)
            arbreRond(30f, 41f, 3.6f, papier(0xFF6FA84E.toInt()), tronc)
            arbreRond(58f, 40f, 3.2f, papier(0xFF3E7A44.toInt()), tronc)
            // L'herbe de la cour.
            pieceD("M-2 44Q40 41 102 44L102 68L-2 68Z", papier(0xFF5E9446.toInt()), 0.5f)
            // L'aile basse à droite : mur de planches, toit rayé rouge et blanc.
            val gris = papier(0xFF8A8276.toInt())
            pieceD("M88 39.6L97.6 41L97.6 47.4L88 47Z", mix(gris, 0xFF000000.toInt(), 0.1f), 0.5f)
            pieceD("M91 42L94 42.4L94 47.2L91 47.1Z", papier(0xFF2E2620.toInt()), 0f)
            val rouge = papier(0xFFB4443A.toInt()); val blanc = papier(0xFFDCD4C8.toInt())
            pieceD("M86.6 39.8L98.6 41.6L97 34.6L87.4 33.4Z", rouge, 0.7f)
            for (k in 0 until 4) {
                val u0 = 0.12f + k * 0.22f; val u1 = u0 + 0.09f
                val bx0 = 86.6f + 12f * u0; val by0 = 39.8f + 1.8f * u0; val bx1 = 86.6f + 12f * u1; val by1 = 39.8f + 1.8f * u1
                val hx0 = 87.4f + 9.6f * u0; val hy0 = 33.4f + 1.2f * u0; val hx1 = 87.4f + 9.6f * u1; val hy1 = 33.4f + 1.2f * u1
                pieceD("M$bx0 ${by0}L$bx1 ${by1}L$hx1 ${hy1}L$hx0 ${hy0}Z", blanc, 0f)
            }
            // La grande case : soubassement de pierre, murs de planches grises, volets ouverts.
            val pierre = papier(0xFF8E8A82.toInt())
            pieceD("M66 45.2L89 45.2L89 48L66 48Z", pierre, 0.6f)
            val rs = Alea(2604)
            repeat(12) { galet(66.6f + rs() * 22f, 45.8f + rs() * 1.8f, 0.5f + rs() * 0.4f, papier(if (rs() < 0.5f) 0xFF7A766E.toInt() else 0xFFA29E94.toInt())) }
            pieceD("M67 37.6L88.4 37.6L88.4 45.4L67 45.4Z", gris, 0.6f)
            var px = 68f
            while (px < 88f) { barre(px, 37.8f, px, 45.2f, 0.12f, mix(gris, 0xFF000000.toInt(), 0.18f), 0f); px += 1.1f }
            val ouverture = if (P.allume) P.fenetre else papier(0xFF2E2620.toInt())
            val volet = papier(0xFF9C9284.toInt())
            for (ox in floatArrayOf(69f, 74.4f, 79.8f, 85f)) {
                pieceD("M$ox 39L${ox + 2.2f} 39L${ox + 2.2f} 45.2L$ox 45.2Z", ouverture, 0.2f)
                pieceD("M${ox - 1f} 39L$ox 39L$ox 45L${ox - 1f} 44.8Z", volet, 0.3f)
                pieceD("M${ox + 2.2f} 39L${ox + 3.1f} 39L${ox + 3.1f} 44.8L${ox + 2.2f} 45Z", volet, 0.3f)
            }
            // Le toit de tôle rouillée, ses ondes, ses trois lucarnes.
            val tole = papier(0xFF6E4834.toInt())
            pieceD("M65.4 38L90 38L86.6 28L69 28Z", tole, 0.9f)
            for (k in 1 until 14) {
                val u = k / 14f
                barre(65.4f + 24.6f * u, 38f, 69f + 17.6f * u, 28f, 0.1f, mix(tole, 0xFF000000.toInt(), 0.15f), 0f)
            }
            pieceD("M65.4 37.4L90 37.4L90 38.4L65.4 38.4Z", mix(tole, 0xFF000000.toInt(), 0.25f), 0f)
            val lucarne = papier(0xFFB4AC9E.toInt())
            for (lx in floatArrayOf(71.6f, 77.8f, 84f)) {
                pieceD("M${lx - 1.7f} 34.4L${lx + 1.7f} 34.4L${lx + 1.7f} 30.6L$lx 28.8L${lx - 1.7f} 30.6Z", lucarne, 0.5f)
                pieceD("M${lx - 0.8f} 33.8L${lx + 0.8f} 33.8L${lx + 0.8f} 31.2L${lx - 0.8f} 31.2Z", if (P.allume) P.fenetre else papier(0xFF3A322A.toInt()), 0f)
                pieceD("M${lx - 2.3f} 31L$lx 28.2L${lx + 2.3f} 31L${lx + 1.8f} 31.3L$lx 29.2L${lx - 1.8f} 31.3Z", tole, 0.4f)
            }
            plan(2)
            // L'oratoire blanc à gauche du mot, sa niche cintrée, le pot de fleurs.
            val chaux = papier(0xFFEEEAE2.toInt())
            fun cintre(x0: Float, x1: Float, haut: Float, bas: Float) = Path().apply {
                moveTo(x0, bas); lineTo(x0, haut)
                arcTo(RectF(x0, haut - (x1 - x0) / 2f, x1, haut + (x1 - x0) / 2f), 180f, 180f)
                lineTo(x1, bas); close()
            }
            piece(cintre(18f, 27.2f, 44f, 54f), chaux, 0.8f)
            pieceD("M26 44L27.2 44L27.2 54L26 54Z", papier(0xFFC8C4BA.toInt()), 0f)
            piece(cintre(19.8f, 25.4f, 45f, 51.6f), papier(0xFFB4B0A6.toInt()), 0.3f)
            pieceD("M21.4 51.6L21.8 49.4L23.4 49.4L23.8 51.6Z", papier(0xFFC0602E.toInt()), 0.2f)
            for (a in floatArrayOf(-150f, -110f, -70f, -30f)) feuillePliee(22.6f, 49.4f, 2f, a, papier(0xFF3E8A44.toInt()), 0.4f)
            pieceD("M16.8 54L28.4 54L28.4 55.4L16.8 55.4Z", papier(0xFFD8D4CA.toInt()), 0.4f)
            // Le jardin de crotons sur son mur de pierres sèches.
            pieceD("M-2 54Q30 53 60 55Q80 56 102 54L102 58.6L-2 58.6Z", papier(0xFF4E8A3E.toInt()), 0.4f)
            val crotons = intArrayOf(papier(0xFFC8402A.toInt()), papier(0xFFE0A030.toInt()), papier(0xFF3E8A44.toInt()), papier(0xFF9A2E2A.toInt()))
            val rc = Alea(2605)
            for (cx in floatArrayOf(32f, 36f, 41f, 62f, 67f, 72f, 77f, 94f)) repeat(3) {
                feuilleCoeur(cx + (rc() - 0.5f) * 3f, 55.6f + rc(), 1.6f + rc() * 1f, (rc() - 0.5f) * 70f, crotons[min((rc() * 4f).toInt(), 3)])
            }
            val mur = Path()
            mur.moveTo(-2f, 57.6f); mur.quadTo(50f, 56.6f, 102f, 57.8f); mur.lineTo(102f, 63.6f); mur.quadTo(50f, 62.4f, -2f, 63.4f); mur.close()
            piece(mur, papier(0xFF6E6A62.toInt()), 0.6f)
            val rm = Alea(2606)
            val moellons = intArrayOf(papier(0xFF9A968C.toInt()), papier(0xFF84807A.toInt()), papier(0xFFB0ACA2.toInt()))
            var my = 58.6f
            while (my < 63f) {
                var mx = -2f + rm() * 2f
                while (mx < 102f) { val l = 1.6f + rm() * 2.2f; galet(mx + l / 2f, my, l / 2f, moellons[min((rm() * 3f).toInt(), 2)]); mx += l + 0.3f }
                my += 1.4f
            }
            // Le chemin pavé au pied du mur.
            pieceD("M-2 63.4Q50 62.4 102 63.6L102 68L-2 68Z", papier(0xFF6A6A62.toInt()), 0.4f)
            val rpv = Alea(2607)
            repeat(40) {
                galet(-2f + rpv() * 104f, 64.4f + rpv() * 3f, 1f + rpv() * 0.8f, papier(if (rpv() < 0.5f) 0xFF8C8C82.toInt() else 0xFF7A7A70.toInt()))
            }
            balisier(10f, 60f, 14f, feuille())
            balisier(88f, 60f, 14f, feuille())
        }

        /** La route de la Traversée : sous la canopée, les grands troncs, une Mamelle boisée dans la trouée, un agouti. */
        fun traversee() {
            if (PAPIER) { traverseePapier(); return }
            forme("M30 36Q36 22 43 19Q49 18 52 24Q56 16 62 16Q69 18 72 36Z", mix(P.milieu, P.loin, 0.3f))
            bosquet(34f, 70f, 26f, 2f, 351, mix(P.milieu, P.loin, 0.15f))
            brume(50f, 31f, 20f, 2.4f, 0.4f)
            plan(1)
            forme("M0 0L34 0Q27 20 33 38L36 66L0 66Z", P.milieu)
            forme("M100 0L68 0Q75 18 67 38L64 66L100 66Z", P.milieu)
            val bord = mix(P.milieu, P.pres, 0.3f)
            bordFeuillu(31f, 4f, 38f, 1f, 353, bord)
            bordFeuillu(69f, 4f, 38f, -1f, 354, bord)
            forme("M44 14L56 14L68 66L34 66Z", blanc(), 0.07f)
            forme("M27 66Q41 52 48 38L53 38Q58 52 75 66Z", mix(P.pierre, P.sol, 0.45f))
            for (k in 0 until 7) rect(50.4f + k * 0.08f, 41f + k * 3.4f, 0.3f, 1.6f, blanc(), 0.55f)
            val fut = mix(P.pierre, P.sol, 0.7f)
            rect(26f, 10f, 1.4f, 40f, fut)
            rect(71f, 8f, 1.6f, 42f, fut)
            plan(2)
            forme("M0 0L100 0L100 8Q80 14 70 10Q60 16 50 12Q40 16 30 10Q18 14 0 8Z", P.pres)
            bosquet(-2f, 102f, 10f, 4f, 352, P.pres)
            tronc(8f, 6f, 0f, 66f)
            tronc(86f, 5f, 0f, 66f)
            ovale(14f, 24f, 2.6f, 1.3f, P.milieu); ovale(8.4f, 34f, 2f, 1f, P.milieu)
            ovale(87f, 30f, 2.8f, 1.3f, P.milieu); ovale(91.6f, 18f, 2f, 1f, P.milieu)
            trait("M14 4Q18 20 15 34Q14 40 16 46M23 8Q21 18 25 30M78 6Q80 22 76 36M86 10Q82 22 84 30M40 12Q42 18 40 24",
                mix(P.pres, P.sol, 0.3f), 0.5f)
            anthurium(88f, 42f)
            forme("M0 66L0 50Q8 54 14 60Q18 64 20 66Z", P.pres)
            forme("M100 66L100 52Q92 56 86 61Q83 64 82 66Z", P.pres)
            balisier(21f, 65f, 12f, feuille())
            balisier(80f, 65f, 11f, feuille())
        }

        /** L'habitation La Grivelière : la case à galerie dans sa clairière, le glacis à café, les caféiers sous les arbres. */
        fun griveliere() {
            if (PAPIER) { grivelierePapier(); return }
            forme("M0 16Q20 4 42 12Q62 2 82 10Q92 6 100 10L100 66L0 66Z", mix(P.milieu, P.loin, 0.45f))
            bosquet(-2f, 102f, 14f, 3f, 361, mix(P.milieu, P.loin, 0.35f))
            brume(40f, 16f, 26f, 2.2f)
            plan(1)
            forme("M0 24Q30 16 60 22Q80 16 100 22L100 66L0 66Z", P.milieu)
            bosquet(-2f, 102f, 24f, 3.4f, 362, P.milieu)
            bosquet(-2f, 102f, 30f, 3.6f, 363, mix(P.milieu, P.pres, 0.25f))
            forme("M30 44Q40 34 66 34Q82 36 86 44Z", mix(P.milieu, sable(), 0.25f))
            triangle(44f, 32f, 57f, 25f, 70f, 32f, P.toit)
            rect(46f, 32f, 22f, 8f, mix(P.pierre, 0xFFB88E62.toInt(), 0.25f))
            fenetre(49f, 34f, 3f, 4f, null)
            fenetre(55.5f, 34f, 3f, 4f, null)
            fenetre(62f, 34f, 3f, 4f, null)
            rect(44f, 40f, 26f, 1.4f, P.toit)
            trait("M45 40V36M51 40V36M57 40V36M63 40V36M69 40V36", mix(P.pierre, P.sol, 0.3f), 0.4f)
            forme("M34 46L66 44.4L68 48.6L32 50.6Z", mix(P.pierre, 0xFFC9B79A.toInt(), 0.2f))
            val grain = duJour(0xFF7A3A26.toInt())
            for ((gx, gy) in arrayOf(38f to 47.6f, 44f to 47f, 50f to 47.8f, 56f to 46.6f, 42f to 49f, 54f to 48.4f, 61f to 46.8f))
                rect(gx, gy - 0.3f, 1f, 0.6f, grain)
            plan(2)
            val fut = mix(P.pierre, P.sol, 0.65f)
            rect(14f, 0f, 2.6f, 66f, fut)
            rect(26f, 6f, 1.6f, 46f, fut)
            rect(82f, 0f, 3f, 66f, fut)
            rect(74f, 8f, 1.6f, 44f, fut)
            forme("M0 0L44 0Q36 6 28 4Q20 10 10 6Q4 10 0 8Z", P.pres)
            forme("M100 0L60 0Q66 6 76 5Q84 10 92 6Q96 9 100 8Z", P.pres)
            bosquet(-2f, 40f, 6f, 3.6f, 364, P.pres)
            bosquet(62f, 102f, 6f, 3.6f, 365, P.pres)
            trait("M16 6Q19 20 16 32M84 6Q80 18 83 28M28 6Q30 14 27 22", mix(P.pres, P.sol, 0.3f), 0.45f)
            ovale(16.4f, 22f, 2.4f, 1.1f, P.milieu)
            ovale(83f, 30f, 2.6f, 1.2f, P.milieu)
            // Des cabosses de cacao sur le tronc.
            val cabosse = duJour(0xFFC07A2A.toInt())
            ovale(27f, 40f, 0.6f, 1f, cabosse)
            ovale(26.2f, 44f, 0.6f, 1f, cabosse)
            bananier(90f, 56f, 16f)
            forme("M0 66L0 52Q30 50 50 56Q70 50 100 52L100 66Z", P.pres)
            bosquet(-2f, 102f, 56f, 3.6f, 366, P.pres)
            bosquet(-2f, 102f, 62f, 3.2f, 367, mix(P.pres, P.sol, 0.3f))
            // Les cerises rouges des caféiers.
            val cerise = duJour(0xFFC0302A.toInt())
            for ((cx, cy) in arrayOf(6f to 54f, 10f to 56.4f, 22f to 53.6f, 30f to 57f, 70f to 54.4f, 78f to 56.6f, 90f to 53.4f, 94f to 56f))
                rond(cx, cy, 0.45f, cerise)
        }

        // ------------------------------------------- Matouba en papier

        /** Un pied de bananier : le faux tronc, et ses grandes feuilles qui montent puis retombent. */
        private fun pied(x: Float, y: Float, h: Float, graine: Int) {
            val r = Alea(graine)
            barre(x, y, x + (r() - 0.5f) * 1f, y - h * 0.55f, 1.1f, papier(0xFF8AA046.toInt()), 0.4f)
            val tx = x; val ty = y - h * 0.55f
            for (a in floatArrayOf(-150f, -120f, -95f, -70f, -40f)) {
                feuilleBananier(tx, ty, h * (0.5f + r() * 0.25f), a + (r() - 0.5f) * 14f,
                    papier(if (r() < 0.5f) 0xFF7AB848.toInt() else 0xFF5EA03E.toInt()))
            }
        }

        /**
         * Les bains de Matouba en papier découpé, d'après la photo du
         * propriétaire (2026-11-08) : le flanc du morne couvert de forêt qui
         * monte à pic vers la droite, en bandes de lisières ; le carbet de bois
         * ouvert à gauche, son toit gris et sa rambarde en croix, sous un grand
         * arbre dont le tronc porte le pic de Guadeloupe ; le petit bassin de
         * pierre dans l'herbe à droite du mot, la clôture de piquets, les
         * bananiers et les fougères à droite ; l'herbe et les marches de bois
         * devant. Balisiers pour le colibri.
         */
        fun matoubaPapier() {
            // Le fond de forêt en couches, des troncs pâles dans la brume.
            morneBoise(4f, 2.4f, 2711, mix(papier(0xFF8CBE74.toInt()), P.cielBas, 0.5f), 2.6f)
            val pale = mix(papier(0xFF7E8E70.toInt()), P.cielBas, 0.4f)
            for ((x, e) in arrayOf(34f to 0.6f, 41f to 0.5f, 52f to 0.7f, 58f to 0.5f)) barre(x, 44f, x + 0.6f, 4f, e, pale, 0f)
            // Le flanc du morne qui monte à droite, hérissé de houppiers.
            val flanc = brisee(20f, 44f, 34f, 30f, 46f, 18f, 58f, 10f, 70f, 4f, 82f, -1f, 102f, -4f)
            val verts = intArrayOf(papier(0xFF3E7A34.toInt()), papier(0xFF2E6A2E.toInt()), papier(0xFF4A8638.toInt()))
            for (k in 0 until 3) {
                val c = { x: Float -> flanc(x - k * 7f) + k * 6f }
                val bande = sousLaCrete(20f + k * 7f, 104f, c)
                bande.op(bosses(20f + k * 7f, 104f, { x -> c(x) + 0.4f }, 1.8f, 3.2f, Alea(2703 + k)), Path.Op.UNION)
                piece(bande, if (k == 0) mix(verts[k], P.cielBas, 0.3f) else mix(verts[k], P.cielBas, 0.15f), 0.6f)
                taches(24f + k * 7f, 104f, 46f, 10, 2712 + k, mix(papier(0xFF6EA846.toInt()), P.cielBas, 0.2f), c)
            }
            brumePapier(24f, 76f, 30f, 4f, 2715)
            morneBoise(30f, 1.8f, 2716, mix(papier(0xFF5E9E52.toInt()), P.cielBas, 0.2f), 2.6f)
            brumePapier(30f, 70f, 40f, 3f, 2717)
            morneBoise(38f, 1.4f, 2718, mix(papier(0xFF4E8E46.toInt()), P.cielBas, 0.1f), 2.2f)
            plan(1)
            // Les deux lisières de la forêt, qui descendent vers la clairière.
            val lisieres = intArrayOf(papier(0xFF2E6A3A.toInt()), papier(0xFF4E8E46.toInt()))
            for (k in 0 until 2) {
                rive(true, 6f + k * 12f, 40f + k * 3f, 30f - k * 6f, 2720 + k, lisieres[k], 3.6f)
                rive(false, 4f + k * 12f, 41f + k * 3f, 66f + k * 6f, 2722 + k, lisieres[1 - k], 3.6f)
            }
            // Le gros tronc de droite, ses contreforts, ses épiphytes et ses lianes.
            val gros = papier(0xFF4A4036.toInt())
            pieceD("M78.4 -2L84.6 -2Q84 20 85 34Q86 42 91 47.6L72 47.6Q77 42 77.8 34Q78.8 20 78.4 -2Z", gros, 0.8f)
            pieceD("M82.8 -2L84.6 -2Q84 20 85 34Q86 42 91 47.6L87.6 47.6Q84.4 40 83.4 30Q82.8 14 82.8 -2Z", mix(gros, 0xFFFFFFFF.toInt(), 0.12f), 0f)
            for ((y, l) in arrayOf(10f to 5f, 22f to 4f)) feuillePliee(79f, y, l, 95f, papier(0xFF5E9A44.toInt()), 0.35f)
            // La voûte : les houppiers qui se rejoignent en haut, à droite.
            val voute = Path()
            val rv = Alea(2724)
            repeat(14) { voute.op(disque(56f + rv() * 50f, -2f + rv() * 9f, 3f + rv() * 2.6f), Path.Op.UNION) }
            piece(voute, papier(0xFF2E6A2E.toInt()))
            taches(56f, 104f, 8f, 8, 2725, papier(0xFF5E9A44.toInt())) { -2f }
            val liane = papier(0xFF6A7A48.toInt())
            barre(64f, 2f, 63.2f, 24f, 0.3f, liane, 0.2f)
            barre(71f, 4f, 72f, 32f, 0.35f, liane, 0.2f)
            barre(94f, 4f, 93f, 28f, 0.3f, liane, 0.2f)
            // Le grand arbre de gauche : son tronc pour le pic, son houppier en haut.
            val ecorce = papier(0xFF5A4A3A.toInt())
            barre(14.4f, 46f, 13.8f, 6f, 1.4f, ecorce, 0.5f)
            barre(14f, 18f, 24f, 10f, 0.5f, ecorce, 0.3f)
            val houppier = Path()
            val rh = Alea(2704)
            repeat(12) { houppier.op(disque(-2f + rh() * 30f, -1f + rh() * 12f, 3f + rh() * 2.4f), Path.Op.UNION) }
            piece(houppier, papier(0xFF3E7A30.toInt()))
            taches(-2f, 28f, 12f, 8, 2705, papier(0xFF6EA846.toInt())) { 0f }
            // Le sol du sous-bois, sombre, et l'herbe de la petite clairière au milieu.
            pieceD("M-2 46Q30 44.6 60 45.6Q80 46.4 102 45L102 68L-2 68Z", papier(0xFF4E7A38.toInt()), 0.4f)
            pieceD("M26 47Q46 45.6 64 46.6Q74 47.4 80 51Q70 56 52 56.6Q34 56 22 52Q20 48.6 26 47Z", papier(0xFF7EAE44.toInt()), 0f)
            taches(-2f, 102f, 58f, 16, 2726, papier(0xFF3E6A2E.toInt())) { 45f }
            // Le carbet de bois, ouvert, à gauche.
            val bois = papier(0xFF8A7458.toInt()); val sombre = papier(0xFF4E4234.toInt())
            pieceD("M4 39.4L24 38.8L24 46L4 46.6Z", sombre, 0.2f)
            pieceD("M4 45.8L24.6 45.2L24.6 46.6L4 47.2Z", bois, 0.4f)
            for (px in floatArrayOf(5f, 14.4f, 23.6f)) barre(px, 46.6f - (px - 4f) * 0.03f, px, 38.6f, 0.6f, bois, 0.4f)
            for ((x0, x1) in arrayOf(5f to 14.4f, 14.4f to 23.6f)) {
                barre(x0, 41f, x1, 44.6f, 0.3f, bois, 0f)
                barre(x0, 44.6f, x1, 41f, 0.3f, bois, 0f)
                barre(x0, 41f, x1, 41f, 0.35f, bois, 0f)
            }
            val tole = papier(0xFF8E9290.toInt())
            pieceD("M2.2 39.2L25 38.4L21.6 33L6.6 33.4Z", tole, 0.8f)
            pieceD("M21.6 33L25 38.4L27.4 38.2Z", papier(0xFF6E7270.toInt()), 0.4f)
            for (k in 1 until 8) {
                val u = k / 8f
                barre(2.2f + 22.8f * u, 39.2f - 0.8f * u, 6.6f + 15f * u, 33.4f - 0.4f * u, 0.1f, papier(0xFF6E7270.toInt()), 0f)
            }
            pieceD("M26.4 39.6L28.6 39.6L28.6 42.6L26.4 42.6Z", papier(0xFFE8E6E0.toInt()), 0.3f)
            barre(27.5f, 42.6f, 27.5f, 46f, 0.2f, sombre, 0f)
            // Dans un coin, au bord de la clairière, la petite stèle de Delgrès et de ses compagnons.
            val stele = papier(0xFF9A968E.toInt())
            pieceD("M29.2 47.4L34.2 47.4L34.2 48.4L29.2 48.4Z", mix(stele, 0xFF000000.toInt(), 0.15f), 0.3f)
            pieceD("M30.2 42.2Q31.7 41.2 33.2 42.2L33.6 47.4L29.8 47.4Z", stele, 0.4f)
            pieceD("M30.7 43.2L32.7 43.2L32.7 44.8L30.7 44.8Z", papier(0xFF5E5A54.toInt()), 0f)
            // Deux fleurs rouges déposées au pied.
            for ((fx, fy) in arrayOf(29.8f to 47.6f, 33.6f to 47.8f))
                pieceD("M${fx - 0.45f} ${fy}Q$fx ${fy - 0.7f} ${fx + 0.45f} ${fy}Q$fx ${fy + 0.5f} ${fx - 0.45f} ${fy}Z", papier(0xFFC8402E.toInt()), 0f)
            // La clôture de piquets, au fond de la clairière.
            val piquet = papier(0xFF7A6A56.toInt())
            var fx = 38f
            while (fx < 60f) { barre(fx, 45.6f, fx, 42f, 0.35f, piquet, 0.2f); fx += 4.6f }
            barre(38f, 43f, 56.6f, 42.8f, 0.08f, papier(0xFF5A5450.toInt()), 0f)
            // Le petit bassin de pierre, creusé dans l'herbe, à droite du mot.
            val pierre = papier(0xFF8E8A82.toInt())
            pieceD("M62 47.4L76 47L78 50L60 50.4Z", papier(0xFF2A2E2A.toInt()), 0.2f)
            pieceD("M60.4 46.4L64 45.6L64 47.8L60.4 48.6Z", pierre, 0.5f)
            pieceD("M74 45.4L78.6 46.8L78.6 48.6L74 47.6Z", pierre, 0.5f)
            pieceD("M64 45.6L74 45.4L74 46.4L64 46.6Z", papier(0xFF6E6A64.toInt()), 0.3f)
            barre(69f, 47f, 69f, 43f, 0.3f, papier(0xFF9E9A94.toInt()), 0.2f)
            barre(69f, 43.2f, 71.6f, 44.2f, 0.25f, papier(0xFF9E9A94.toInt()), 0.2f)
            // Les bananiers à droite.
            pied(90f, 50f, 18f, 2706)
            pied(98f, 52f, 16f, 2707)
            pied(84f, 50f, 12f, 2708)
            plan(2)
            // Le sous-bois de devant : les marches de bois, fougères et grandes feuilles des deux bords.
            val planche = papier(0xFF9A8A70.toInt())
            pieceD("M24 60.6L36 59.4L36.4 60.8L24.4 62Z", planche, 0.5f)
            pieceD("M20 65.6L40 64L40.4 65.6L20.4 67.2Z", planche, 0.5f)
            val fougeres = intArrayOf(papier(0xFF5EA04A.toInt()), papier(0xFF3E8A44.toInt()), papier(0xFF7AB654.toInt()))
            for ((fx2, fy, fl, fa) in arrayOf(
                floatArrayOf(-2f, 52f, 13f, -25f), floatArrayOf(2f, 58f, 14f, -15f), floatArrayOf(4f, 66f, 12f, -32f),
                floatArrayOf(102f, 52f, 13f, -155f), floatArrayOf(100f, 58f, 13f, -165f), floatArrayOf(96f, 67f, 11f, -140f)))
                fougere(fx2, fy, fl, fa, fougeres[((fx2 + fy).toInt() and 0xFF) % 3])
            feuilleBananier(-2f, 67f, 12f, -40f, papier(0xFF4FB06A.toInt()))
            feuilleCoeur(16f, 67f, 4f, -10f, papier(VERT_PROFOND))
            feuilleBananier(102f, 67f, 12f, -142f, papier(0xFF3FA86A.toInt()))
            feuilleCoeur(82f, 67f, 3.6f, 14f, papier(VERT_PROFOND))
            balisier(12f, 61f, 13f, feuille())
            balisier(87f, 61f, 13f, feuille())
        }

        /** Matouba : la stèle de Delgrès et de ses compagnons, dans sa clairière, au cœur de la forêt. */
        fun matouba() {
            if (PAPIER) { matoubaPapier(); return }
            val fut = mix(P.pierre, P.sol, 0.65f)
            rect(0f, 0f, 100f, 66f, mix(P.milieu, P.loin, 0.4f))
            bosquet(-2f, 102f, 10f, 4f, 371, mix(P.milieu, P.loin, 0.3f))
            bosquet(-2f, 102f, 20f, 4f, 372, mix(P.milieu, P.loin, 0.2f))
            rect(36f, 4f, 1.4f, 44f, mix(P.pierre, P.sol, 0.55f), 0.7f)
            rect(62f, 2f, 1.6f, 46f, mix(P.pierre, P.sol, 0.55f), 0.7f)
            brume(50f, 30f, 30f, 2.6f, 0.4f)
            plan(1)
            bosquet(-2f, 102f, 34f, 4f, 373, P.milieu)
            forme("M30 50Q40 40 60 40Q72 42 74 50Z", mix(P.milieu, sable(), 0.25f))
            forme("M44 0L56 0L64 54L36 54Z", blanc(), 0.1f)
            forme("M46.4 38Q50 35.6 53.6 38L54.8 53L45.2 53Z", P.pierre)
            rect(47.6f, 41.6f, 4.8f, 4f, mix(P.pierre, P.sol, 0.35f))
            rect(42f, 53f, 16f, 2.6f, mix(P.pierre, P.sol, 0.2f))
            trait("M45.6 52.6Q44 51 44.4 49M54.4 52.6Q56.2 51.4 55.6 49.4", P.milieu, 0.6f)
            plan(2)
            rect(22f, 4f, 1.8f, 50f, fut)
            rect(76f, 6f, 1.8f, 48f, fut)
            tronc(10f, 3f, 0f, 66f)
            tronc(86f, 3.2f, 0f, 66f)
            forme("M0 0L42 0Q34 6 26 4Q18 10 8 6Q3 9 0 8Z", P.pres)
            forme("M100 0L58 0Q66 6 74 4Q82 10 92 6Q97 9 100 8Z", P.pres)
            bosquet(-2f, 40f, 6f, 3.6f, 374, P.pres)
            bosquet(60f, 102f, 6f, 3.6f, 375, P.pres)
            trait("M12 6Q16 20 12 34Q11 40 13 44M24 6Q22 16 26 26M88 6Q84 20 87 32M78 8Q80 16 77 22", mix(P.pres, P.sol, 0.3f), 0.45f)
            ovale(12.6f, 24f, 2.6f, 1.2f, P.milieu)
            ovale(87.6f, 28f, 2.6f, 1.2f, P.milieu)
            ovale(23f, 36f, 1.8f, 0.9f, P.milieu)
            forme("M0 66L0 54Q22 52 38 58Q50 54 62 58Q78 52 100 54L100 66Z", P.pres)
            bosquet(-2f, 36f, 57f, 3.6f, 376, P.pres)
            bosquet(64f, 102f, 57f, 3.6f, 377, P.pres)
            bosquet(-2f, 102f, 63f, 3.2f, 378, mix(P.pres, P.sol, 0.3f))
            anthurium(57f, 54f)
            balisier(30f, 65f, 11f, feuille())
            balisier(71f, 65f, 10f, feuille())
        }

        // ------------------------------------------- le Fort Delgrès en papier

        /** Une pierre dressée du mémorial : un bloc de lave sombre au sommet arrondi, sa face éclairée. */
        private fun pierreDressee(x: Float, sol: Float, l: Float, h: Float, col: Int, r: Alea) {
            val pench = (r() - 0.5f) * l * 0.4f
            pieceD("M${x - l / 2f} ${sol}L${x - l * 0.45f + pench} ${sol - h * 0.75f}Q${x - l * 0.3f + pench} ${sol - h} ${x + pench} ${sol - h}" +
                "Q${x + l * 0.45f + pench} ${sol - h * 0.95f} ${x + l * 0.5f + pench} ${sol - h * 0.6f}L${x + l / 2f} ${sol}Z", col, 0.6f)
            pieceD("M${x + l * 0.1f + pench} ${sol - h * 0.92f}Q${x + l * 0.45f + pench} ${sol - h * 0.9f} ${x + l * 0.5f + pench} ${sol - h * 0.6f}" +
                "L${x + l / 2f} ${sol}L${x + l * 0.2f} ${sol}Z", mix(col, 0xFFFFFFFF.toInt(), 0.16f), 0f)
        }

        /**
         * Le Fort Delgrès en papier découpé, d'après la photo du propriétaire
         * (2026-11-08) : les mornes verts de la Soufrière derrière ; les
         * remparts de pierre sombre et la longue caserne au toit de tuiles
         * brunes ; sur la pelouse, à droite du mot, le mémorial : la grande
         * tête de Delgrès entourée de pierres de lave dressées le long d'une
         * spirale de pavés. L'anoli fait ses pompes sur un bloc à gauche.
         */
        fun fortDelgresPapier() {
            cumulus(-6f, 16f, 20f, 2801)
            // Les mornes de la Soufrière.
            val loin = brisee(-2f, 26f, 14f, 22f, 28f, 18f, 38f, 12.6f, 46f, 11f, 54f, 13f, 62f, 17.6f, 68f, 15.6f, 76f, 12.4f,
                84f, 16f, 96f, 23f, 102f, 26f)
            val m = sousLaCrete(-2f, 102f, loin)
            m.op(bosses(-2f, 102f, { x -> loin(x) + 0.3f }, 1f, 1.6f, Alea(2802)), Path.Op.UNION)
            piece(m, mix(papier(0xFF4E8A4A.toInt()), P.cielBas, 0.3f))
            taches(-2f, 102f, 36f, 16, 2803, mix(papier(0xFF6EA858.toInt()), P.cielBas, 0.25f), loin)
            morneBoise(28f, 2.4f, 2804, papier(0xFF3E7A3A.toInt()), 2.4f)
            plan(1)
            // Les remparts de pierre sombre, leurs moellons.
            val pierre = papier(0xFF5E5A56.toInt())
            pieceD("M-2 33.6L30 34.6L30 44L-2 44Z", pierre, 0.6f)
            pieceD("M66 35L102 34L102 44L66 44Z", pierre, 0.6f)
            pieceD("M84 44L102 38.6L102 44Z", papier(0xFF6E6A64.toInt()), 0.3f)
            val rm = Alea(2805)
            repeat(40) {
                val x = -2f + rm() * 104f; val y = 35.4f + rm() * 8f
                if (x < 30f || x > 66f)
                    piece(Path().apply { addRoundRect(RectF(x, y, x + 1.6f + rm(), y + 0.8f), 0.3f, 0.3f, Path.Direction.CW) },
                        papier(if (rm() < 0.5f) 0xFF4A4644.toInt() else 0xFF726C66.toInt()), 0f)
            }
            pieceD("M4 31.6L9 31.6L9 34L4 34Z", pierre, 0.4f)
            // La longue caserne : murs de pierre, toit de tuiles brunes en croupe, l'auvent.
            pieceD("M28 40L72 40L72 45L28 45Z", papier(0xFF6E6862.toInt()), 0.5f)
            val tuile = papier(0xFF8E5E48.toInt())
            pieceD("M26.6 40.4L73.4 40.4L68 34L32 34Z", tuile, 0.8f)
            var tx = 33f
            while (tx < 68f) { barre(tx - 0.4f, 34.2f, tx - 1.2f, 40.2f, 0.1f, papier(0xFF6E4636.toInt()), 0f); tx += 1.4f }
            for (k in 1..3) barre(27f + k * 1.4f, 40.2f - k * 1.5f, 73f - k * 1.4f, 40.2f - k * 1.5f, 0.1f, papier(0xFF6E4636.toInt()), 0f)
            pieceD("M24 43L76 43L76.6 44.6L23.4 44.6Z", papier(0xFF9E6A50.toInt()), 0.4f)
            // La pelouse.
            pieceD("M-2 44Q50 43 102 44L102 68L-2 68Z", papier(0xFF8EAA4A.toInt()), 0.4f)
            pieceD("M-2 48Q40 46.6 102 48.6L102 68L-2 68Z", papier(0xFF7E9E42.toInt()), 0f)
            plan(2)
            // Le mémorial : la spirale de pavés, les pierres dressées, la tête,
            // agrandis autour du centre de la spirale.
            tasser(80f, 58f, 1.3f, 1.3f) {
            val pave = papier(0xFF5A5A58.toInt())
            for ((rx, ry) in arrayOf(17f to 5.4f, 11f to 3.4f)) {
                val anneau = Path().apply { addOval(RectF(80f - rx, 57f - ry, 80f + rx, 57f + ry), Path.Direction.CW) }
                anneau.op(Path().apply { addOval(RectF(80f - rx + 0.8f, 57f - ry + 0.5f, 80f + rx - 0.8f, 57f + ry - 0.5f), Path.Direction.CW) }, Path.Op.DIFFERENCE)
                piece(anneau, pave, 0f)
            }
            val lave = intArrayOf(papier(0xFF4A4A4C.toInt()), papier(0xFF5E5C5A.toInt()), papier(0xFF3E3E40.toInt()))
            val rp = Alea(2806)
            // Les pierres du fond, puis la tête, puis celles de devant.
            val fond = arrayOf(floatArrayOf(66f, 54f), floatArrayOf(71f, 52.4f), floatArrayOf(76f, 52f), floatArrayOf(86f, 52.2f),
                floatArrayOf(91f, 53f), floatArrayOf(95.6f, 54.6f))
            for ((x, y) in fond) pierreDressee(x, y, 2f + rp() * 1f, 4f + rp() * 2.4f, lave[min((rp() * 3f).toInt(), 2)], rp)
            // La tête de Delgrès, de trois quarts : le crâne, le front, le nez, la bouche, le menton.
            val bronze = papier(0xFF4E4E50.toInt())
            pieceD("M75.4 56Q74.6 50 76 46.6Q78 43.4 81.6 43.6Q85.6 44 86.4 47.6L86.6 49.4L87.8 51.2L86.8 51.6L86.8 53Q86.4 54.6 84.6 55L84 56.4Z", bronze, 0.8f)
            pieceD("M82.4 44Q85.6 44.6 86.4 47.6L86.6 49.4L87.8 51.2L86.8 51.6L86.8 53Q86.4 54.6 84.6 55L84 56.4L82.6 56.4Q84 52 82.4 44Z",
                mix(bronze, 0xFFFFFFFF.toInt(), 0.15f), 0f)
            pieceD("M84.4 48.2Q85.6 47.6 86.4 48.2L86.2 48.6Q85.4 48.2 84.6 48.6Z", papier(0xFF2E2E30.toInt()), 0f)
            pieceD("M85.2 53Q86 52.8 86.6 53.2L86.4 53.5Q85.8 53.3 85.3 53.4Z", papier(0xFF2E2E30.toInt()), 0f)
            piece(Path().apply { addOval(RectF(77.4f, 48.4f, 79.4f, 51.6f), Path.Direction.CW) }, mix(bronze, 0xFF000000.toInt(), 0.2f), 0f)
            val devant = arrayOf(floatArrayOf(64f, 59f), floatArrayOf(69f, 61f), floatArrayOf(74.6f, 62.4f), floatArrayOf(81f, 62.6f),
                floatArrayOf(87f, 62f), floatArrayOf(93f, 60.4f), floatArrayOf(97f, 58.4f), floatArrayOf(73f, 57.4f), floatArrayOf(88f, 57.6f))
            for ((x, y) in devant) pierreDressee(x, y, 2.2f + rp() * 1.2f, 4.4f + rp() * 3f, lave[min((rp() * 3f).toInt(), 2)], rp)
            }
            // Le bloc de pierre de l'anoli, à gauche, et la butte d'herbe.
            pieceD("M-2 58Q20 55.6 40 60L44 68L-2 68Z", papier(0xFF8CB04C.toInt()), 0.4f)
            pieceD("M15.6 55.2L24.6 55.2L24.6 61L15.6 61Z", papier(0xFF6E6A64.toInt()), 0.6f)
            pieceD("M15.6 55.2L24.6 55.2L24.6 56L15.6 56Z", papier(0xFF8E8A84.toInt()), 0f)
        }

        /** Le fort Delgrès : le bastion et son échauguette, la mer, la Soufrière derrière, un anoli sur le mur. */
        fun fortDelgres() {
            if (PAPIER) { fortDelgresPapier(); return }
            forme("M0 40Q20 30 36 20Q44 14 52 14Q60 16 68 24Q84 34 100 38L100 66L0 66Z", P.loin)
            vapeur(50f, 14f, 8f)
            plan(1)
            rect(58f, 45f, 42f, 21f, P.eau)
            ecume("M70 52Q76 51 82 52M84 58Q90 57 96 58")
            flamboyant(10f, 47f, 9f)
            plan(2)
            forme("M0 44L30 44L42 50L58 46L58 66L0 66Z", P.pierre)
            forme("M0 44L30 44L42 50L58 46L58 48L42 52L30 46L0 46Z", mix(P.pierre, P.sol, 0.3f))
            trait("M0 52H40M0 58H58M0 63H58M10 46V52M24 46V52M18 52V58M36 52V58M48 58V63M28 58V63", mix(P.pierre, P.sol, 0.35f), 0.3f)
            rect(40f, 42.6f, 4.4f, 8f, P.pierre)
            ovale(42.2f, 42.6f, 2.6f, 1.6f, mix(P.pierre, P.sol, 0.2f))
            rect(41.8f, 45f, 0.8f, 2f, mix(P.pierre, P.sol, 0.6f))
            forme("M0 62Q8 59 18 62L18 66L0 66Z", P.pres)
        }

        // ------------------------------------------- le Mémorial ACTe en papier

        /**
         * Le Mémorial ACTe en papier découpé, d'après la photo du
         * propriétaire (2026-11-08) : le long bâtiment de granit noir sous sa
         * résille d'argent, des barres blanches entrecroisées qui montent en
         * perspective vers la droite, à côté du mot, avec la grande croix au
         * bout ; le parvis, l'enrochement de la darse, l'eau claire de la baie
         * devant à gauche, le quai et sa bitte où se tient le pélican. L'eau
         * scintille sur la carte ouverte (`Reflets` dans [Ambiance]).
         */
        fun memorialActePapier() {
            plan(1)
            val haut = brisee(-2f, 31f, 102f, 19f)
            val bas = brisee(-2f, 35.6f, 102f, 29f)
            val sol = brisee(-2f, 40.4f, 102f, 42f)
            // Le granit noir, ses joints, ses vitrages, le bloc clair.
            val granit = papier(0xFF26282E.toInt())
            pieceD("M-2 ${bas(-2f)}L102 ${bas(102f)}L102 ${sol(102f)}L-2 ${sol(-2f)}Z", granit, 0.6f)
            for ((x0, x1, col) in arrayOf(Triple(30f, 38f, 0xFF3A4048), Triple(62f, 67f, 0xFFC8C8C0), Triple(80f, 92f, 0xFF3A4450)))
                pieceD("M$x0 ${bas(x0) + 0.4f}L$x1 ${bas(x1) + 0.4f}L$x1 ${sol(x1)}L$x0 ${sol(x0)}Z", papier(col.toInt()), 0.2f)
            var jx = 4f
            while (jx < 100f) { barre(jx, bas(jx) + 0.4f, jx, sol(jx), 0.15f, papier(0xFF3A3C44.toInt()), 0f); jx += 5.5f }
            if (P.allume) for (x0 in floatArrayOf(31f, 81f)) {
                val x1 = x0 + 6f
                pieceD("M$x0 ${bas(x0) + 1.4f}L$x1 ${bas(x1) + 1.4f}L$x1 ${sol(x1) - 0.4f}L$x0 ${sol(x0) - 0.4f}Z", P.fenetre, 0f)
            }
            // La résille : l'ombre sous elle, puis les barres blanches
            // entrecroisées, découpées à la forme de la bande.
            val bande = chemin("M-2 ${haut(-2f)}L102 ${haut(102f)}L102 ${bas(102f) + 0.6f}L-2 ${bas(-2f) + 0.6f}Z")
            piece(Path(bande), papier(0xFF3A3E46.toInt()), 0.6f)
            val barres = Path()
            val rb = Alea(2901)
            var bx = -6f
            while (bx < 106f) {
                val h = bas(bx) - haut(bx) + 2f
                val incl = (if (rb() < 0.5f) -1f else 1f) * (2f + rb() * 5f)
                val e = 0.3f + rb() * 0.5f
                barres.op(chemin("M${bx - e} ${haut(bx) - 1f}L${bx + e} ${haut(bx) - 1f}L${bx + incl + e} ${haut(bx) + h}L${bx + incl - e} ${haut(bx) + h}Z"), Path.Op.UNION)
                bx += 0.9f + rb() * 1.3f
            }
            repeat(10) {
                val x = rb() * 100f
                barres.op(chemin("M${x - 4f} ${haut(x) + 1f + rb() * 2f}L${x + 4f} ${haut(x) + 0.6f + rb() * 2f}L${x + 4f} ${haut(x) + 1.1f + rb() * 2f}L${x - 4f} ${haut(x) + 1.5f + rb() * 2f}Z"), Path.Op.UNION)
            }
            barres.op(chemin("M84.6 ${haut(84.6f)}L86 ${haut(86f)}L92.6 ${bas(92.6f)}L91.2 ${bas(91.2f)}Z"), Path.Op.UNION)
            barres.op(chemin("M91 ${haut(91f)}L92.4 ${haut(92.4f)}L86 ${bas(86f)}L84.6 ${bas(84.6f)}Z"), Path.Op.UNION)
            barres.op(bande, Path.Op.INTERSECT)
            piece(barres, papier(0xFFE8ECEE.toInt()), 0.4f)
            // Le parvis, ses lampadaires, l'enrochement de la darse à droite.
            pieceD("M-2 ${sol(-2f)}L102 ${sol(102f)}L102 ${sol(102f) + 2.6f}L-2 ${sol(-2f) + 2.6f}Z", papier(0xFFB4B2AC.toInt()), 0.4f)
            for (lx in floatArrayOf(22f, 48f, 70f)) barre(lx, sol(lx) + 0.6f, lx, sol(lx) - 4.4f, 0.25f, papier(0xFF4A4C50.toInt()), 0f)
            pieceD("M36 ${sol(36f) + 2.6f}L102 ${sol(102f) + 2.6f}L102 54L60 52Z", papier(0xFF8C8A84.toInt()), 0.6f)
            val rg = Alea(2902)
            repeat(60) {
                val x = 40f + rg() * 62f
                val yh = sol(x) + 2.8f
                val yb = 52f + (x - 60f).coerceAtLeast(0f) * 0.05f
                val y = yh + rg() * (yb - yh)
                galet(x, y, 0.5f + rg() * 0.6f, papier(if (rg() < 0.5f) 0xFFA8A6A0.toInt() else 0xFF78766E.toInt()))
            }
            plan(2)
            // L'eau claire de la baie, le quai et sa bitte d'amarrage.
            val (ex0, ex1, ey0, ey1) = EAU_ACTE
            pieceD("M-2 ${ey0 - 4f}L36 ${ey0 - 4f}Q60 ${ey0 - 1f} $ex1 ${ey0 + 2f}L${ex1 + 4f} 68L-2 68Z", papier(0xFF7EC4C6.toInt()), 0.4f)
            lanieresMer(ey0 - 3.4f, ey1, 2903, intArrayOf(papier(0xFF8ED0D0.toInt()), papier(0xFF6AB4BA.toInt()), papier(0xFFA6DCD8.toInt())))
            pieceD("M-2 ${ey0 - 4.6f}L30 ${ey0 - 4.6f}L32 ${ey0 - 3.4f}L-2 ${ey0 - 3.4f}Z", papier(0xFF5A5C60.toInt()), 0.5f)
            pieceD("M80 50L104 50L104 68L76 68Q78 58 80 50Z", papier(0xFFA8A49C.toInt()), 0.7f)
            pieceD("M80 50L104 50L104 51.2L79.8 51.2Z", papier(0xFFC8C4BC.toInt()), 0f)
            val fonte = papier(0xFF2B2D33.toInt())
            pieceD("M91.1 50L91.1 52.6L92.9 52.6L92.9 50Z", fonte, 0.4f)
            piece(Path().apply { addOval(RectF(90.7f, 49.5f, 93.3f, 50.5f), Path.Direction.CW) }, fonte, 0.2f)
        }

        /** Le Mémorial ACTe : le granit noir pris dans ses racines d'argent, la passerelle, la baie, un pélican. */
        fun memorialActe() {
            if (PAPIER) { memorialActePapier(); return }
            forme("M0 38Q50 34.4 100 38L100 41L0 41Z", P.loin)
            plan(1)
            rect(0f, 40.6f, 100f, 10f, P.eau)
            ecume("M8 46H20M76 47H90")
            val granit = mix(0xFF2B2D33.toInt(), P.pres, if (P.nuit) 0.3f else 0f)
            val argent = duJour(0xFFC9CDD3.toInt())
            rect(14f, 32f, 72f, 18f, granit)
            if (P.allume) { rect(20f, 44f, 8f, 3f, P.fenetre); rect(60f, 44f, 10f, 3f, P.fenetre) }
            trait("M14 32Q22 26 30 32Q38 38 46 32Q54 26 62 32Q70 38 78 32Q82 29 86 30" +
                "M14 38Q24 30 34 38Q44 46 54 38Q64 30 74 38Q80 42 86 36" +
                "M18 32L26 50M34 32L30 50M42 32L50 50M58 32L54 50M66 32L74 50M80 32L78 50", argent, 0.5f, 0.9f)
            trait("M86 40Q94 36 100 34", argent, 0.7f)
            plan(2)
            rect(0f, 50f, 100f, 16f, mix(P.pierre, P.sol, 0.3f))
            ligne(0f, 54f, 100f, 54f, mix(P.pierre, P.sol, 0.5f), 0.3f)
            // La bitte d'amarrage où se tient le pélican : posé à plat sur le
            // quai, il avait l'air de flotter sur le béton (propriétaire).
            val fonte = mix(0xFF2B2D33.toInt(), P.pres, if (P.nuit) 0.3f else 0f)
            rect(91.1f, 50f, 1.8f, 2.6f, fonte)
            ovale(92f, 50f, 1.3f, 0.5f, fonte)
        }
    }
}

