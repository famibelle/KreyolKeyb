package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.example.kreyolkeyboard.R
import org.json.JSONObject

/**
 * Les séries du carnet : des pages d'album **finies**, à emplacements vides.
 *
 * C'est ce qui manquait à la collection : on accumulait sans jamais rien
 * terminer, et une collection sans emplacement vide n'a pas de « encore
 * trois ». Voir `GAMIFICATION-CARNET.md` à la racine du dépôt LuxKeyb.
 *
 * Repris de LuxKeyb 35.0.0 à 36.1.0 (v23.0.0). Les séries y sont connues
 * d'avance et **prouvées complétables** :
 *
 * - [NOMBRES] : côté LuxKeyb, les nombres de 0 à 100 de Zuelwuert. Le kréyòl
 *   n'a pas de jeu de nombres : la série est vide, et le carnet ne l'affiche
 *   pas ;
 * - [ENLUMINURES] : les dessins dont un mot sort d'un jeu, lus dans
 *   `creole_series.json`. Cet actif n'existe pas encore : il suppose la
 *   classification des blasons (`creole_blasons.json`), pas faite pour le
 *   kréyòl. Absent, la série est vide et le carnet ne l'affiche pas ;
 * - [JEUX] : une carte de chacun des six jeux ;
 * - [PAYSAGES] : chaque lieu des cartes à chacune de ses quatre heures.
 *
 * Deux règles, qui valent pour toutes :
 *
 * - **Une série compte des emplacements, pas des cartes.** Le dessin du
 *   téléphone est porté par `Handy`, `Telefon` et `Smartphone` : gagner l'un
 *   remplit l'emplacement, les deux autres n'en ouvrent pas de nouveaux.
 * - **Un emplacement vide est un indice, jamais la réponse** : le nombre en
 *   chiffres, la silhouette du dessin, l'emblème du jeu, le lieu en grisé.
 *   Jamais le mot.
 *
 * Le calcul ([avancement]) n'a pas de `Context`, pour être testé sur la JVM
 * comme [Sonje].
 */
enum class Serie(val id: String, @StringRes val titre: Int) {
    /**
     * `nombres_101` et non plus `nombres` : la série a grandi de 18 à 101
     * emplacements après la 36.x, et un joueur qui avait fini les dix-huit porte
     * déjà `nombres` dans ses séries fêtées. Garder l'identifiant lui volerait
     * la fête des cent un.
     */
    /**
     * En tête : c'est la page la plus visuelle du carnet, et la seule dont
     * chaque case vide montre déjà un morceau de l'image.
     */
    PAYSAGES("paysages", R.string.serie_paysages),
    NOMBRES("nombres_101", R.string.serie_nombres),
    ENLUMINURES("enluminures", R.string.serie_enluminures),
    JEUX("jeux", R.string.serie_jeux)
}

/**
 * Un emplacement de série. [cle] est la valeur du numéral, le nom du dessin ou
 * l'identifiant du jeu ; [cartes] celles qui le remplissent, dans l'ordre de
 * capture, vide tant qu'il attend.
 */
data class Emplacement<C>(val cle: String, val cartes: List<C>) {
    val rempli: Boolean get() = cartes.isNotEmpty()
}

data class Avancement<C>(val serie: Serie, val emplacements: List<Emplacement<C>>) {
    val remplis: Int get() = emplacements.count { it.rempli }
    val total: Int get() = emplacements.size
    val complete: Boolean get() = total > 0 && remplis == total
}

object Series {

    private const val TAG = "Series"
    private const val ACTIF = "creole_series.json"

    /** Vide ici : le kréyòl n'a pas de jeu de nombres, voir [Serie.NOMBRES]. */
    val NOMBRES: List<Int> = emptyList()

    /**
     * Les cent vingt cases de l'album des paysages, rangées lieu par lieu, du
     * matin à la nuit : la grille du carnet en fait trente rangées de quatre.
     */
    val PAYSAGES: List<String> = Lieu.values().flatMap { l -> Rarete.values().map { r -> cleDePaysage(l, r) } }

    fun cleDePaysage(lieu: Lieu, rarete: Rarete): String = "${lieu.name}:${rarete.ordinal}"

    /** Le lieu et l'heure d'une case, ou `null` pour une clef inconnue. */
    fun paysageDe(cle: String): Pair<Lieu, Rarete>? {
        val (l, r) = cle.split(':').takeIf { it.size == 2 } ?: return null
        val lieu = Lieu.values().firstOrNull { it.name == l } ?: return null
        val rarete = r.toIntOrNull()?.let { Rarete.values().getOrNull(it) } ?: return null
        return lieu to rarete
    }

    /** La case d'une carte : le lieu de son mot, à l'heure de sa rareté. */
    fun paysage(contenu: ContenuCarte): String =
        cleDePaysage(Lieu.du(contenu.carte.forme), contenu.rarete)

    @Volatile
    private var enluminures: List<String>? = null

    /**
     * Les dessins de la série des enluminures. Un actif illisible donne une
     * série vide, que le carnet n'affiche pas : mieux vaut une série absente
     * qu'une série fausse.
     */
    fun enluminures(context: Context): List<String> {
        enluminures?.let { return it }
        val lus = try {
            val racine = JSONObject(
                context.assets.open(ACTIF).bufferedReader().use { it.readText() }
            )
            val tableau = racine.getJSONArray("enluminures")
            List(tableau.length()) { tableau.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Séries illisibles: ${e.message}", e)
            emptyList()
        }
        enluminures = lus
        return lus
    }

    /**
     * L'avancement d'une série sur une collection.
     *
     * [carte], [meuble], [paysage] disent comment lire une carte, ce qui garde le calcul
     * indépendant de [ContenuCarte] et de ses actifs : les tests passent des
     * cartes nues.
     */
    fun <C> avancement(
        serie: Serie,
        collection: List<C>,
        carte: (C) -> CarteMot,
        meuble: (C) -> String?,
        enluminures: List<String>,
        paysage: (C) -> String? = { null }
    ): Avancement<C> {
        val ordonnees = collection.sortedBy { carte(it).numero }
        val emplacements = when (serie) {
            Serie.NOMBRES -> NOMBRES.map { n ->
                Emplacement("$n", ordonnees.filter { carte(it).nombre == n })
            }
            Serie.ENLUMINURES -> enluminures.map { m ->
                Emplacement(m, ordonnees.filter { meuble(it) == m })
            }
            Serie.JEUX -> JeuCarte.JEUX.map { jeu ->
                Emplacement(jeu.id, ordonnees.filter { jeu in carte(it).jeux })
            }
            Serie.PAYSAGES -> {
                val cles = ordonnees.map { paysage(it) }
                PAYSAGES.map { k -> Emplacement(k, ordonnees.filterIndexed { i, _ -> cles[i] == k }) }
            }
        }
        return Avancement(serie, emplacements)
    }

    /**
     * Les séries que les cartes [neuves] viennent de compléter, et qui n'ont
     * encore jamais été fêtées. Les marque comme fêtées.
     *
     * Une série ne se complète qu'en gagnant une carte neuve, puisque le carnet
     * ne fait que grandir : on ne recalcule donc que les séries qu'une neuve
     * peut concerner. La marque garantit le « une seule fois » même si une
     * pochette se rejoue. Elle vit dans un fichier de préférences à part, que
     * les règles de sauvegarde n'incluent pas, et ne contient que des
     * identifiants de séries.
     *
     * Lit la fiche de chaque carte pour les enluminures : **hors du fil
     * principal**, comme l'assemblage de la pochette qui l'appelle.
     */
    fun aFeter(context: Context, neuves: Collection<CarteMot>): List<Serie> {
        if (neuves.isEmpty()) return emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fetees = prefs.getStringSet(CLE_FETEES, emptySet()).orEmpty()
        val dessins = enluminures(context)
        val meubles = HashMap<String, String?>()
        fun meuble(c: CarteMot): String? = if (c.nombre != null) null else meubles.getOrPut(c.forme) {
            Armorial.pour(context, c.forme, c.forme).meuble
        }
        // Le paysage passe par le contenu complet de la carte, celui que la
        // grille affiche : un numéral y reçoit son champ, une carte d'accueil
        // sa correction, et la case cochée est bien celle qu'on voit.
        val paysages = HashMap<String, String>()
        fun paysage(c: CarteMot): String = paysages.getOrPut(c.forme) {
            Series.paysage(CarteCarnet.contenu(context, c))
        }

        val concernees = Serie.values().filter { s ->
            s.id !in fetees && when (s) {
                Serie.NOMBRES -> NOMBRES.isNotEmpty() && neuves.any { it.nombre in NOMBRES }
                Serie.JEUX -> true
                Serie.PAYSAGES -> Paysages.LIEUX_DESSINES
                Serie.ENLUMINURES -> dessins.isNotEmpty() && neuves.any { meuble(it) in dessins }
            }
        }
        if (concernees.isEmpty()) return emptyList()

        val collection = Carnet.cartes(context)
        val completes = concernees.filter { s ->
            val paysageDe: (CarteMot) -> String? = if (s == Serie.PAYSAGES) { c -> paysage(c) } else { _ -> null }
            avancement(s, collection, { it }, { meuble(it) }, dessins, paysageDe).complete
        }
        if (completes.isNotEmpty()) {
            prefs.edit().putStringSet(CLE_FETEES, fetees + completes.map { it.id }).apply()
        }
        return completes
    }

    private const val PREFS = "carnet_series"
    private const val CLE_FETEES = "fetees"

    /** Les séries d'une collection de cartes déjà assemblées. */
    fun toutes(context: Context, contenus: List<ContenuCarte>): List<Avancement<ContenuCarte>> {
        val dessins = enluminures(context)
        return Serie.values()
            .filter { it != Serie.ENLUMINURES || dessins.isNotEmpty() }
            .filter { it != Serie.NOMBRES || NOMBRES.isNotEmpty() }
            .filter { it != Serie.PAYSAGES || Paysages.LIEUX_DESSINES }
            .map {
                avancement(it, contenus, { c -> c.carte }, { c -> c.blason.meuble }, dessins, { c -> paysage(c) })
            }
    }
}

/**
 * Les étapes de la collection : la 10ᵉ carte, la 25ᵉ, la 50ᵉ…
 *
 * Une série fête un emplacement rempli ; rien ne fêtait le carnet lui-même, et
 * la centième carte arrivait comme la quatre-vingt-dix-neuvième. Les paliers
 * s'espacent avec la taille : au début une étape vient vite, plus tard elle se
 * mérite.
 *
 * Chaque palier se fête **une fois**, à la pochette qui le franchit. La marque
 * vit à côté des séries fêtées, dans un fichier que la sauvegarde n'emporte
 * pas, et ne contient qu'un nombre. Un carnet qui a déjà dépassé plusieurs
 * paliers à la mise à jour n'est fêté qu'une fois, pour le plus haut.
 */
object Etapes {

    val PALIERS = listOf(10, 25, 50, 100, 250, 500, 1000, 2000)

    private const val PREFS = "carnet_series"
    private const val CLE = "etape"

    /** Le plus haut palier que [taille] atteint au-delà de [deja], ou `null`. */
    fun franchi(taille: Int, deja: Int): Int? = PALIERS.lastOrNull { it in (deja + 1)..taille }

    /** Le palier à fêter pour un carnet de [taille] cartes, marqué comme fêté. */
    fun aFeter(context: Context, taille: Int): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val palier = franchi(taille, prefs.getInt(CLE, 0)) ?: return null
        prefs.edit().putInt(CLE, palier).apply()
        return palier
    }
}
