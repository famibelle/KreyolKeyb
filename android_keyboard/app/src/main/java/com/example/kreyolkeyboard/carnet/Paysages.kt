package com.example.kreyolkeyboard.carnet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import kotlin.math.PI
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
 * LuxKeyb tire le lieu du champ sémantique du mot ; le kréyòl n'a pas encore
 * ce classement (`creole_blasons.json`). Le lieu se tire donc **du mot
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
    SIFFLEUR, PARULINE, BALEINE, ECREVISSE, IGUANE
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

        private val BASE = arrayOf(
            Heure(0, h(0xFFA6C6DC), null, h(0xFFF3EBDC), h(0xFFFFF7DF), 22f, 15f, 6f,
                h(0xFFB3C3C4), h(0xFF8AA392), h(0xFF5D7964), h(0xFF43594A),
                h(0xFFE4D8C2), h(0xFF8E5C4B), h(0xFF5B6670), false, h(0xFFB7D0D9), false),
            Heure(1, h(0xFFE2A865), null, h(0xFFFCE6B6), h(0xFFFFF3C6), 78f, 13f, 7f,
                h(0xFFD6B387), h(0xFFAA895A), h(0xFF735C39), h(0xFF504028),
                h(0xFFF1D5A1), h(0xFFA3533A), h(0xFF6E4E33), false, h(0xFFECCB8E), false),
            Heure(2, h(0xFF463A76), h(0xFFC66F8A), h(0xFFF7B179), h(0xFFFFD690), 68f, 31f, 10f,
                h(0xFF8D6A8A), h(0xFF5D4766), h(0xFF392C46), h(0xFF241C30),
                h(0xFFC49A8F), h(0xFF5A3141), h(0xFFFFCD78), true, h(0xFFCF8689), false),
            Heure(3, h(0xFF091030), null, h(0xFF2A3B70), h(0xFFF6F1D8), 79f, 12f, 4.6f,
                h(0xFF27355B), h(0xFF1A2445), h(0xFF111930), h(0xFF0A1022),
                h(0xFF4B5474), h(0xFF2A2F4B), h(0xFFFFD98A), true, h(0xFF2A3A68), true)
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
              souvenir: Souvenir? = null): Bitmap? {
        if (largeurPx <= 0 || hauteurPx <= 0) return null
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir)
        CACHE.get(cle)?.let { if (!it.isRecycled) return it }
        val bitmap = rendu(lieu, heure, largeurPx, hauteurPx, souvenir) ?: return null
        CACHE.put(cle, bitmap)
        return bitmap
    }

    private fun cle(lieu: Lieu, heure: Heure, teinte: Float?, l: Int, h: Int, souvenir: Souvenir?) =
        "${lieu.ordinal}_${heure.rang}_${teinte}_${souvenir?.saison}_${souvenir?.fete}_${l}x$h"

    /** Le rendu s'il est déjà en cache, sans rien calculer. */
    fun enCache(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
                souvenir: Souvenir? = null): Bitmap? =
        CACHE.get(cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir))?.takeIf { !it.isRecycled }

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

    /** Un rendu hors cache, que l'appelant garde pour lui. */
    fun rendu(lieu: Lieu, heure: Heure, largeurPx: Int, hauteurPx: Int, souvenir: Souvenir? = null): Bitmap? {
        if (largeurPx <= 0 || hauteurPx <= 0) return null
        val bitmap = Bitmap.createBitmap(largeurPx, hauteurPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val k = cadrage(largeurPx.toFloat(), hauteurPx.toFloat())
        c.save()
        c.translate(k[1], k[2])
        c.scale(k[0], k[0])
        if (souvenir == null) {
            dessiner(c, lieu, heure)
            c.restore()
            return bitmap
        }
        // Avec une saison, le lieu est peint à part sur un calque transparent,
        // puis posé sur le ciel : ce calque est aussi le masque dont
        // [Habillage] a besoin, et le lieu n'est peint qu'une fois.
        dessiner(c, lieu, heure, ciel = true, lieuAussi = false)
        c.restore()
        val calque = Bitmap.createBitmap(largeurPx, hauteurPx, Bitmap.Config.ARGB_8888)
        val cc = Canvas(calque)
        cc.translate(k[1], k[2])
        cc.scale(k[0], k[0])
        dessiner(cc, lieu, heure, ciel = false)
        c.drawBitmap(calque, 0f, 0f, null)
        Habillage.appliquer(bitmap, calque, lieu, heure, souvenir, k)
        calque.recycle()
        return bitmap
    }

    /**
     * De combien chaque plan glisse au plus quand la carte penche à fond, en
     * part de la largeur (de la hauteur pour le tangage) : le lointain, le
     * milieu, le proche.
     *
     * C'est le lointain qui bouge le plus, comme derrière une vitre : le
     * cadre de la carte est ce qu'il y a de plus près, le dessin du mot est
     * posé dessus en nacre et ne bouge pas, et plus un plan est loin du
     * verre, plus il glisse quand l'œil change d'angle.
     */
    val GLISSEMENT = floatArrayOf(0.04f, 0.022f, 0.008f)

    /**
     * Le lieu en trois calques, pour la carte ouverte : voir [GLISSEMENT].
     * Le premier porte le ciel et ne laisse rien de vide, les deux autres
     * sont transparents autour de ce qu'ils portent.
     *
     * Chaque calque déborde de la fenêtre de quoi glisser sans découvrir son
     * bord : voir [marge], et [CadreOrne] qui le peint centré sur elle. Le
     * lointain est légèrement flou : c'est la profondeur de champ, l'œil fait
     * le point sur le dessin du mot, au premier plan.
     *
     * `null` pour une carte gagnée un jour de fête, qui garde son rendu à
     * plat : les feux, les braises et les lumières s'ajoutent à la lumière
     * du paysage entier, et ne se répartissent pas en calques.
     */
    fun plans(lieu: Lieu, heure: Heure, teinte: Float?, largeurPx: Int, hauteurPx: Int,
              souvenir: Souvenir? = null): Array<Bitmap>? {
        if (largeurPx <= 0 || hauteurPx <= 0 || souvenir?.fete != null) return null
        val cle = cle(lieu, heure, teinte, largeurPx, hauteurPx, souvenir)
        val caches = Array(GLISSEMENT.size) { CACHE.get("${cle}_p$it")?.takeIf { b -> !b.isRecycled } }
        if (caches.all { it != null }) return caches.requireNoNulls()
        val rendus = rendrePlans(lieu, heure, largeurPx, hauteurPx, souvenir)
        rendus.forEachIndexed { i, b -> CACHE.put("${cle}_p$i", b) }
        return rendus
    }

    /**
     * Ce que le calque [i] déborde de chaque côté d'une fenêtre de [n] pixels :
     * sa plus grande course, plus un pixel de garde.
     *
     * Jusqu'en 37.4.1 les calques avaient la taille exacte de la fenêtre, le
     * dessin seul était agrandi dedans : en glissant, le bord de l'image
     * découvrait une bande du fond de la fenêtre (vu sur un Galaxy A21s).
     */
    fun marge(i: Int, n: Int): Int = ceil(GLISSEMENT[i] * n).toInt() + 1

    private fun rendrePlans(lieu: Lieu, heure: Heure, l: Int, h: Int, souvenir: Souvenir?): Array<Bitmap> {
        val tailles = Array(GLISSEMENT.size) { i -> intArrayOf(l + 2 * marge(i, l), h + 2 * marge(i, h)) }
        val ks = Array(GLISSEMENT.size) { i ->
            cadrage(tailles[i][0].toFloat(), tailles[i][1].toFloat())
        }
        fun calque(i: Int, ciel: Boolean, lieuAussi: Boolean): Bitmap {
            val b = Bitmap.createBitmap(tailles[i][0], tailles[i][1], Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.translate(ks[i][1], ks[i][2])
            c.scale(ks[i][0], ks[i][0])
            dessiner(c, lieu, heure, ciel = ciel, lieuAussi = lieuAussi, seul = i, faune = false)
            return b
        }
        // Le lointain : le ciel, puis la crête, floutée seule pour que la lune
        // et les étoiles gardent leur netteté.
        val fond = calque(0, ciel = true, lieuAussi = false)
        val loin = calque(0, ciel = false, lieuAussi = true)
        flouter(loin, max(1, (ks[0][0] * FLOU_LOINTAIN).roundToInt()))
        Canvas(fond).drawBitmap(loin, 0f, 0f, null)
        val couches = arrayOf(fond, calque(1, false, true), calque(2, false, true))
        if (souvenir != null) {
            Habillage.appliquerPlans(couches, arrayOf(loin, couches[1], couches[2]), lieu, heure, souvenir, ks)
        }
        loin.recycle()
        return couches
    }

    /** Le flou du lointain, en unités de la boîte : à peine, de quoi ne plus être net. */
    private const val FLOU_LOINTAIN = 0.22f

    /**
     * Deux passes de flou en boîte, à peu près gaussiennes, sur les couleurs
     * prémultipliées : sans cela, le bord d'une colline fondrait vers le noir
     * des pixels transparents.
     */
    private fun flouter(b: Bitmap, rayon: Int) {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val canaux = Array(4) { FloatArray(w * h) }
        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) / 255f
            canaux[0][i] = a
            canaux[1][i] = ((c shr 16) and 0xFF) * a
            canaux[2][i] = ((c shr 8) and 0xFF) * a
            canaux[3][i] = (c and 0xFF) * a
        }
        val tampon = FloatArray(max(w, h))
        repeat(2) {
            for (v in canaux) {
                boite(v, tampon, w, h, rayon, horizontal = true)
                boite(v, tampon, w, h, rayon, horizontal = false)
            }
        }
        for (i in px.indices) {
            val a = canaux[0][i]
            px[i] = if (a <= 0.002f) 0 else
                ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or
                    ((canaux[1][i] / a).roundToInt().coerceIn(0, 255) shl 16) or
                    ((canaux[2][i] / a).roundToInt().coerceIn(0, 255) shl 8) or
                    (canaux[3][i] / a).roundToInt().coerceIn(0, 255)
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** Une moyenne glissante sur 2r + 1 pixels, bords prolongés, ligne par ligne ou colonne par colonne. */
    private fun boite(v: FloatArray, t: FloatArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val n = if (horizontal) w else h
        val lignes = if (horizontal) h else w
        val pas = if (horizontal) 1 else w
        val norme = 1f / (2 * r + 1)
        for (j in 0 until lignes) {
            val debut = if (horizontal) j * w else j
            for (k in 0 until n) t[k] = v[debut + k * pas]
            var somme = 0f
            for (k in -r..r) somme += t[k.coerceIn(0, n - 1)]
            for (k in 0 until n) {
                v[debut + k * pas] = somme * norme
                somme += t[(k + r + 1).coerceAtMost(n - 1)] - t[(k - r).coerceAtLeast(0)]
            }
        }
    }

    /**
     * Les bêtes de chaque lieu, où elles se tiennent dans la boîte 100 × 66,
     * et le plan qui les porte (pour glisser avec lui quand la carte penche).
     *
     * Une seule table pour les deux rendus : la vignette les peint au repos,
     * la carte ouverte les fait bouger au même endroit ([Atelier]). Une ou
     * deux par lieu, pas plus (propriétaire, 2026-10-19).
     */
    internal val BETES: Map<Lieu, List<Bete>> = mapOf(
        Lieu.GRANDE_ANSE to listOf(Bete(Espece.TOULOULOU, 38f, 53f), Bete(Espece.TOULOULOU, 46f, 54.6f), Bete(Espece.TOULOULOU, 61f, 52.4f)),
        Lieu.BOUILLANTE to listOf(Bete(Espece.TORTUE, 56f, 45f, plan = 1, taille = 4f)),
        Lieu.ILET_MACOU to listOf(Bete(Espece.PELICAN_PIEU, 92f, 56f)),
        Lieu.SOUFRIERE to listOf(Bete(Espece.SIFFLEUR, 72f, 54f)),
        Lieu.GRAND_ETANG to listOf(Bete(Espece.LIBELLULE, 62f, 49f, plan = 1)),
        Lieu.BAINS_JAUNES to listOf(Bete(Espece.RATON, 80f, 43f)),
        Lieu.CASCADE_ECREVISSES to listOf(Bete(Espece.ECREVISSE, 62f, 54.6f), Bete(Espece.LIBELLULE, 33f, 43f)),
        Lieu.SAUT_LEZARDE to listOf(Bete(Espece.LIBELLULE, 44f, 50f, plan = 1)),
        Lieu.GRANDE_VIGIE to listOf(Bete(Espece.BALEINE, 78f, 42f, plan = 1)),
        Lieu.PORTE_ENFER to listOf(Bete(Espece.TOULOULOU, 30f, 61f), Bete(Espece.TOULOULOU, 62f, 62.4f)),
        Lieu.PETITE_TERRE to listOf(Bete(Espece.IGUANE, 22f, 54.4f), Bete(Espece.TOULOULOU, 62f, 58.6f), Bete(Espece.TOULOULOU, 78f, 60.4f)),
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
    internal class Atelier(heure: Heure) {
        private val peintre = Peintre(Canvas(), heure)

        /** Peint [b] à l'instant [t], en secondes ; [i] décale son rythme de celui des autres. */
        fun peindre(c: Canvas, b: Bete, t: Float, i: Int) {
            peintre.c = c
            peintre.bete(b, t, i, immobile = false)
        }
    }

    /**
     * Les lieux de Guadeloupe sont dessinés (2026-10-19). Éteint, une carte ne
     * montrerait que le ciel de son heure et l'album des paysages disparaîtrait.
     */
    internal const val LIEUX_DESSINES = true

    fun dessiner(c: Canvas, lieu: Lieu, heure: Heure, ciel: Boolean = true, lieuAussi: Boolean = true,
                 seul: Int = -1, faune: Boolean = true) {
        val p = Peintre(c, heure, seul)
        if (ciel) p.ciel()
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
            Lieu.GRAND_ETANG -> p.grandEtang()
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

    /** Tout l'outillage de tracé d'un lieu, sur un canevas déjà à l'échelle. */
    private class Peintre(var c: Canvas, val P: Heure, private val seul: Int = -1) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val BAS = HAUTEUR

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
        }

        /** Une opacité de plus sur tout ce qui se peint : une bête qui paraît ou s'efface. */
        var voile = 1f

        fun teinte(col: Int, opacite: Float) {
            p.shader = null
            p.color = col
            p.alpha = (opacite * voile * 255f).roundToInt().coerceIn(0, 255)
        }

        fun rect(x: Float, y: Float, w: Float, h: Float, col: Int, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawRect(x, y, x + w, y + h, p)
        }

        fun rond(cx: Float, cy: Float, r: Float, col: Int, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawCircle(cx, cy, r, p)
        }

        fun forme(d: String, col: Int, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawPath(chemin(d), p)
        }

        fun forme(path: Path, col: Int, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.FILL; teinte(col, o)
            c.drawPath(path, p)
        }

        fun trait(d: String, col: Int, largeur: Float, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; p.strokeCap = Paint.Cap.BUTT
            teinte(col, o)
            c.drawPath(chemin(d), p)
            p.style = Paint.Style.FILL
        }

        fun ligne(x1: Float, y1: Float, x2: Float, y2: Float, col: Int, largeur: Float, o: Float = 1f) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; teinte(col, o)
            c.drawLine(x1, y1, x2, y2, p)
            p.style = Paint.Style.FILL
        }

        fun anneau(cx: Float, cy: Float, r: Float, col: Int, largeur: Float) {
            if (!actif) return
            p.style = Paint.Style.STROKE; p.strokeWidth = largeur; teinte(col, 1f)
            c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.FILL
        }

        fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, col: Int, o: Float = 1f) {
            val t = Path(); t.moveTo(x1, y1); t.lineTo(x2, y2); t.lineTo(x3, y3); t.close()
            forme(t, col, o)
        }

        // ------------------------------------------------------ les plans

        fun ciel() {
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
                val r = Alea(41)
                for (k in 0 until 46) {
                    val x = r() * 100f
                    val y = r() * 38f
                    val t = 0.18f + r() * 0.38f
                    val o = if (k % 5 == 0) 1f else 0.35f + r() * 0.6f
                    rond(x, y, t, 0xFFFFF8E0.toInt(), o)
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

        fun fenetre(x: Float, y: Float, w: Float, h: Float, rnd: Alea?) {
            val allumee = P.allume && (rnd == null || rnd() < 0.7f)
            if (allumee) rect(x - w * 0.6f, y - h * 0.4f, w * 2.2f, h * 1.8f, P.fenetre, 0.22f)
            rect(x, y, w, h, if (allumee) P.fenetre else mix(P.pierre, P.pres, 0.55f))
        }

        fun maison(x: Float, y: Float, w: Float, h: Float, rnd: Alea?, toit: Float? = null) {
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

        /** Une tortue verte qui nage sous la surface, vue d'en haut. */
        fun tortue(x: Float, y: Float, l: Float) {
            val dos = mix(duJour(0xFF5E6B3A.toInt()), P.eau, 0.35f)
            val nageoire = mix(dos, P.eau, 0.3f)
            ovale(x - l * 0.5f, y - l * 0.32f, l * 0.32f, l * 0.11f, nageoire)
            ovale(x + l * 0.5f, y - l * 0.32f, l * 0.32f, l * 0.11f, nageoire)
            ovale(x - l * 0.3f, y + l * 0.36f, l * 0.18f, l * 0.08f, nageoire)
            ovale(x + l * 0.3f, y + l * 0.36f, l * 0.18f, l * 0.08f, nageoire)
            ovale(x, y - l * 0.55f, l * 0.13f, l * 0.15f, nageoire)
            ovale(x, y, l * 0.38f, l * 0.48f, dos)
            trait("M$x ${y - l * 0.4f}L$x ${y + l * 0.4f}M${x - l * 0.3f} ${y}L${x + l * 0.3f} $y", mix(dos, P.pres, 0.35f), l * 0.04f)
        }

        /** Le pieu où se pose le pélican de l'îlet Macou. */
        fun pieu(x: Float, y: Float) = rect(x - 0.4f, y, 0.8f, 6f, mix(P.sol, P.pierre, 0.2f))

        /** Un pélican brun posé sur un pieu, le bec rentré. */
        fun pelican(x: Float, y: Float) {
            val plume = duJour(0xFF7A6A58.toInt())
            ovale(x, y - 1.6f, 1.4f, 1.8f, plume)
            trait("M${x + 0.4f} ${y - 3f}Q${x + 0.8f} ${y - 4.8f} ${x + 0.2f} ${y - 5.2f}", plume, 0.7f)
            rond(x + 0.2f, y - 5.2f, 0.6f, duJour(0xFFE9E2D0.toInt()))
            trait("M${x + 0.6f} ${y - 5.1f}L${x + 2.6f} ${y - 3.4f}", duJour(0xFFC9A24A.toInt()), 0.4f)
        }

        /** Une tombe en damier, comme au cimetière de Morne-à-l'Eau. */
        fun tombe(x: Float, y: Float, w: Float, h: Float, noir: Int, blanc: Int) {
            rect(x, y - h, w, h, blanc)
            val s = w / 4f
            var yy = y - h
            var rangee = 0
            while (yy < y - 0.01f) {
                val hh = min(s, y - yy)
                for (k in 0 until 4) if ((k + rangee) % 2 == 0) rect(x + k * s, yy, s, hh, noir)
                yy += s
                rangee++
            }
            triangle(x - 0.2f, y - h, x + w / 2f, y - h - w * 0.45f, x + w + 0.2f, y - h, noir)
            rect(x + w / 2f - w * 0.05f, y - h - w * 0.85f, w * 0.1f, w * 0.42f, blanc)
            rect(x + w / 2f - w * 0.2f, y - h - w * 0.72f, w * 0.4f, w * 0.1f, blanc)
        }

        // ------------------------------------------------------ les lieux

        /** Grande Anse : la grande plage de Deshaies, les raisiniers, un flamboyant, des touloulous, l'îlet au large. */
        fun grandeAnse() {
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

        /** Les Saintes : la baie, le Pain de Sucre, une saintoise sous voile. */
        fun lesSaintes() {
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

        /**
         * La Soufrière : le dôme raide qui remplit la fenêtre, son chapeau de
         * nuages, les fumerolles, la savane d'altitude sur ses flancs et la
         * forêt dense tout autour. Le premier dessin la posait sur une bande
         * verte qui se lisait comme une plaine (propriétaire, 2026-10-19).
         */
        fun soufriere() {
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

        /** Les Chutes du Carbet : la chute dans la paroi, le bassin, les fougères. */
        fun chutesCarbet() {
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

        /** Sofaïa : le bassin d'eau chaude en forêt, le bambou qui l'alimente, la vapeur. */
        fun sofaia() {
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

        /** Ravine Chaude : la rivière tiède au fond de la ravine, les roches, les fougères. */
        fun ravineChaude() {
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

        /** La Pointe des Châteaux : les falaises calcaires, la Roche dans l'écume, la croix, la Désirade au loin. */
        fun pointeChateaux() {
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

        /** Marie-Galante : la plaine de canne, le moulin de pierre et ses ailes. */
        fun marieGalante() {
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

        /** La Désirade vue de la côte : la longue île en table, un gommier tiré sur le sable. */
        fun desirade() {
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

        /** Bouillante : les îlets Pigeon, une tortue verte, le pavillon de plongée, la plage sombre qui fume. */
        fun bouillante() {
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

        /** Le cimetière de Morne-à-l'Eau : les tombes en damier noir et blanc, en terrasses. */
        fun morneALEau() {
            val r = Alea(201)
            crete(30f, 1.4f, 202, P.loin)
            plan(1)
            forme("M0 66L0 33C30 29 60 29 100 35L100 66Z", P.milieu)
            plan(2)
            val noir = mix(0xFF1C1C1E.toInt(), P.pres, if (P.nuit) 0.3f else 0f)
            val blanc = duJour(0xFFF4F1EA.toInt())
            val rangs = floatArrayOf(40f, 3f, 49f, 4.2f, 60f, 5.8f)
            var i = 0
            while (i < rangs.size) {
                val y = rangs[i]
                val w = rangs[i + 1]
                rect(0f, y, 100f, BAS - y, mix(P.milieu, P.pres, 0.3f + i * 0.08f))
                var x = -w * r()
                while (x < 100f) {
                    tombe(x, y, w, w * (1f + r() * 0.5f), noir, blanc)
                    x += w * (2.2f + r() * 0.9f)
                }
                i += 2
            }
        }

        /** L'îlet Macou : le banc de sable, sa mangrove et la chapelle des marins disparus, une yole, un pélican. */
        fun iletMacou() {
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

        /** Les Roches gravées : la forêt de Trois-Rivières, les visages taillés dans le rocher. */
        fun rochesGravees() {
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
        fun iguane(x: Float, y: Float, tete: Float = 0f) {
            val peau = duJour(mix(0xFF7E8E6A.toInt(), 0xFFB8B0A0.toInt(), 0.3f))
            forme("M$x ${y}Q${x + 4f} ${y - 2f} ${x + 10f} ${y - 1f}L${x + 13.4f} ${y - 0.2f}Q${x + 11.6f} ${y + 0.8f} ${x + 8f} ${y + 0.8f}" +
                "Q${x + 4f} ${y + 1.2f} $x ${y + 0.8f}Z", peau)
            trait("M$x ${y + 0.4f}Q${x - 6f} ${y + 0.6f} ${x - 9f} ${y + 3f}", peau, 0.7f)
            trait("M${x + 2f} ${y - 0.8f}L${x + 2.6f} ${y - 1.7f}L${x + 3.3f} ${y - 1.1f}L${x + 4f} ${y - 1.9f}L${x + 4.7f} ${y - 1.3f}" +
                "L${x + 5.4f} ${y - 2f}L${x + 6.1f} ${y - 1.4f}", mix(peau, P.sol, 0.3f), 0.3f)
            ovale(x + 14.2f, y - 0.6f + tete, 1.7f, 1f, peau)
            trait("M${x + 4f} ${y + 0.8f}L${x + 3.4f} ${y + 2.2f}M${x + 9f} ${y + 0.8f}L${x + 9.6f} ${y + 2.2f}", peau, 0.5f)
        }

        /** Une écrevisse sur sa pierre. */
        fun ecrevisse(x: Float, y: Float) {
            val carapace = duJour(0xFF8A4B2E.toInt())
            ovale(x, y, 1.5f, 0.6f, carapace)
            trait("M${x - 1.4f} ${y - 0.2f}L${x - 2.6f} ${y - 1f}M${x - 1.4f} ${y + 0.2f}L${x - 2.6f} ${y + 0.8f}M${x + 1.4f} ${y}L${x + 2.6f} ${y - 0.4f}",
                carapace, 0.3f)
        }

        /**
         * Une bête à l'instant [t]. [immobile] : la pose de repos, celle des
         * vignettes, à l'endroit exact de la table. Chaque mouvement est celui
         * de l'espèce : le touloulou file de côté, l'anoli fait ses pompes et
         * déploie son fanon, le pic tape, l'agouti traverse, la baleine souffle
         * puis sonde, l'iguane hoche la tête.
         */
        fun bete(b: Bete, t: Float, i: Int, immobile: Boolean) {
            val ph = i * 1.7f
            val x = b.x
            val y = b.y
            voile = 1f
            if (immobile) {
                when (b.espece) {
                    Espece.TOULOULOU -> touloulou(x, y)
                    Espece.TORTUE -> tortue(x, y, b.taille)
                    Espece.PELICAN_PIEU -> pelican(x, y)
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
                    Espece.ECREVISSE -> ecrevisse(x, y)
                    Espece.IGUANE -> iguane(x, y)
                }
                return
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
                Espece.TORTUE -> tortue(x + sin(t * 0.25f + ph) * 3f, y + sin(t * 0.5f + ph) * 0.3f, b.taille)
                Espece.PELICAN_PIEU -> pelican(x, y + sin(t * 0.8f + ph) * 0.15f)
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
                    if (P.nuit) return
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
                Espece.ECREVISSE -> ecrevisse(x + sin(t * 0.6f + ph) * 1.2f, y)
                Espece.IGUANE -> {
                    val u = (t + ph) % 5f
                    iguane(x, y, if (u < 1.4f) -abs(sin(u / 1.4f * PI.toFloat() * 3f)) * 0.6f else 0f)
                }
            }
        }

        // ------------------------------------------------------ les quinze lieux de la deuxième vague

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

        /** Les Bains Jaunes : le bassin de pierre au pied de la Soufrière, la vapeur, le raton laveur la nuit. */
        fun bainsJaunes() {
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

        /** La Cascade aux Écrevisses : la chute large et basse, le bassin, une écrevisse sur une pierre. */
        fun cascadeEcrevisses() {
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

        /** Le Saut de la Lézarde : la gorge étroite, la chute droite, le bassin sombre, les lianes. */
        fun sautLezarde() {
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

        /** La Pointe de la Grande Vigie : la falaise au nord de la Grande-Terre, la houle, Antigua au loin, une baleine. */
        fun grandeVigie() {
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

        /** Petite-Terre : les deux îlets, le lagon, le phare, l'iguane des Petites Antilles, des touloulous. */
        fun petiteTerre() {
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

        /** L'îlet du Gosier : l'îlet et son phare tout proches, un canot, un pélican, des touloulous. */
        fun iletGosier() {
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

        /** Le Grand Cul-de-Sac Marin : le lagon, la mangrove, le banc de sable de l'îlet Caret, une tortue verte. */
        fun grandCulDeSac() {
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

        /** Gueule Grand Gouffre : la falaise de Marie-Galante percée d'un gouffre où la mer s'engouffre. */
        fun gueuleGrandGouffre() {
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

        /** La route de la Traversée : sous la canopée, les grands troncs, une Mamelle boisée dans la trouée, un agouti. */
        fun traversee() {
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

        /** Matouba : la stèle de Delgrès et de ses compagnons, dans sa clairière, au cœur de la forêt. */
        fun matouba() {
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

        /** Le fort Delgrès : le bastion et son échauguette, la mer, la Soufrière derrière, un anoli sur le mur. */
        fun fortDelgres() {
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

        /** Le Mémorial ACTe : le granit noir pris dans ses racines d'argent, la passerelle, la baie, un pélican. */
        fun memorialActe() {
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
