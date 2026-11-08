package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Le blason d'une carte : le champ de son sens, et le meuble des rares.
 *
 * ## Ce que la fenêtre d'une carte dit, et d'où ça vient
 *
 * Trois sources, et elles ne se mélangent jamais :
 *
 * | ce qu'on voit | d'où ça vient | ce que ça coûte |
 * |---|---|---|
 * | la **matière** — grain, halo, irisation | le palier, donc le rang de fréquence | rien |
 * | la **partition** — la division du champ | la nature du mot, lue sur sa majuscule | rien |
 * | la **teinte** — le champ | le sens, classé hors ligne | 52 ko |
 * | le **sujet** — tracé ou meuble | le mot | 111 silhouettes |
 *
 * ## Pourquoi le champ et pas l'image
 *
 * La demande d'origine était une image par mot. Elle a été mesurée et elle est
 * hors de portée : **1,12 mot par dessin** sur ce carnet, 89 % des têtes de
 * glose ne servant qu'un seul mot. Il n'y a pas de Pareto à exploiter — la
 * courbe de couverture colle à la diagonale.
 *
 * Le champ, lui, amortit par construction : huit valeurs pour 2 798
 * emplacements, un facteur trois cent cinquante. Il tient la promesse qui
 * comptait — feuilleter le carnet cesse d'être un nuancier aléatoire — sans
 * demander un seul dessin. Huit et non quatorze : au-delà, deux teintes
 * voisines ne se séparent plus sur une vignette de 160 dp.
 *
 * ## Ce qu'un mot sans blason devient
 *
 * Rien de spécial, et c'est le point. Il garde la teinte de ses trois
 * premières lettres et son tracé, c'est-à-dire exactement l'état où toutes les
 * cartes étaient en 22.6.0. Aucune version intermédiaire du classement n'est
 * laide, donc il peut s'enrichir par vagues sans jamais bloquer une livraison.
 *
 * L'actif couvre aujourd'hui **71 %** des emplacements du carnet ; le reste est
 * pour l'essentiel des mots-outils — pronoms, déterminants, adverbes — qui
 * n'ont pas de domaine et qu'il serait faux de ranger de force.
 */
enum class Champ(val id: String, val nom: String, val hue: Float) {
    // Les teintes sont posées à intervalles inégaux sur le cercle : l'œil
    // sépare mal deux bleus voisins et très bien deux verts, et c'est la
    // séparation perçue qui compte, pas la régularité du calcul.
    MOUVEMENT("mouvement", "Mouvement", 20f),
    TEMPS("temps", "Temps et mesure", 52f),
    TERRITOIRE("territoire", "Territoire", 96f),
    ECONOMIE("economie", "Économie", 158f),
    SAVOIR("savoir", "Savoir et parole", 205f),
    PUBLIC("public", "Chose publique", 268f),
    SOCIETE("societe", "Société", 312f),
    VIE("vie", "Vie et corps", 352f);

    companion object {
        private val PAR_ID = values().associateBy { it.id }
        fun parId(id: String?): Champ? = id?.let { PAR_ID[it] }
    }
}

/**
 * La nature du mot, telle que la partition la montre.
 *
 * **En kréyòl elle vaut toujours [AUTRE], et c'est une perte assumée.** Le
 * carnet luxembourgeois la lit sans aucune donnée, parce que là-bas la
 * majuscule *est* l'étiquette du substantif et que l'infinitif se termine en
 * `-en`. Le kréyòl ne capitalise que les noms propres et ne fléchit pas ses
 * verbes : la forme ne porte aucun signal, ce qui est déjà acté pour *Fraz a
 * twou* et *Mokwaré*.
 *
 * Lire la nature sur la glose française a été mesuré et écarté : la règle
 * « premier mot de la glose en -er/-ir/-re, donc verbe » touche 144 des 622
 * mots glosés et se trompe sur un tiers d'entre eux — `té` « terre », `manman`
 * « mère », `pyébwa` « arbre », `kat` « quatre ». Une partition fausse une
 * fois sur trois désinforme là où l'absence de partition ne fait rien.
 *
 * L'énumération est gardée entière plutôt que réduite à une constante : c'est
 * le schéma que reprendrait une source grammaticale kréyòl, le jour où il y en
 * aura une.
 */
enum class Nature { NOM, VERBE, AUTRE }

/**
 * Ce que le carnet sait du sens d'un mot : son champ, et son meuble s'il en a.
 *
 * [nature] n'est pas lue dans l'actif — elle se calcule sur la forme, ce qui
 * la rend disponible même pour les mots que le classement ne connaît pas.
 */
data class Blasonnement(
    val champ: Champ? = null,
    val meuble: String? = null,
    val nature: Nature = Nature.AUTRE
) {
    companion object {
        val AUCUN = Blasonnement()
    }
}

/**
 * L'armorial : le classement des lemmes, lu une fois dans l'actif.
 *
 * L'actif est produit hors du build par `Dictionnaires/generate_blasons.py`,
 * qui porte la table des dessins, relue mot par mot contre la glose. Il est
 * petit, une quarantaine de mots, donc il se lit d'un coup au premier besoin.
 *
 * La clé est la forme elle-même : le kréyòl n'a pas de familles de flexions
 * auxquelles déléguer le rangement, contrairement au carnet luxembourgeois.
 */
object Armorial {

    /**
     * L'actif des dessins, livré depuis la 24.0.0 : **des meubles seulement,
     * aucun champ**.
     *
     * Le carnet luxembourgeois range aussi ses lemmes en huit champs
     * sémantiques. Ce classement n'a pas d'équivalent kréyòl : il se relit
     * mot par mot, et la table de gloses ne porte rien qui le donne. Chaque
     * carte garde donc la teinte de ses trois premières lettres ; seuls les
     * mots de la table reçoivent un dessin à la place du tracé de leur mot.
     *
     * Son absence reste **une livraison valide**, pas une panne : toutes les
     * cartes retombent sur leur tracé, et la série des enluminures disparaît.
     */
    private const val ACTIF = "creole_blasons.json"

    private var table: Map<String, Blasonnement>? = null

    /**
     * Le blason d'un lemme.
     *
     * [forme] sert uniquement à la nature : c'est le mot que la carte montre,
     * et c'est sa majuscule à lui qui décide de la partition — « Männer » est
     * un nom parce qu'il s'écrit avec une majuscule, pas parce que son
     * représentant en est un.
     */
    fun pour(context: Context, lemme: String, forme: String): Blasonnement {
        val nature = natureDe(forme)
        val trouve = charger(context)[lemme] ?: return Blasonnement(nature = nature)
        return trouve.copy(nature = nature)
    }

    /**
     * La nature d'une forme : [Nature.AUTRE], toujours.
     *
     * Voir [Nature] : aucun signal de la forme kréyòl ne la donne, et la lire
     * sur la glose se trompe une fois sur trois. La fonction existe pour que
     * le point de décision reste unique et nommé le jour où une source
     * grammaticale apparaîtra, pas pour calculer quelque chose aujourd'hui.
     */
    fun natureDe(forme: String): Nature = Nature.AUTRE

    /** Lit l'armorial d'avance, sur un fil de fond : voir `CarnetFragment.chargerEnFond`. */
    fun precharger(context: Context) {
        charger(context)
    }

    private fun charger(context: Context): Map<String, Blasonnement> {
        table?.let { return it }
        val lu = try {
            val texte = BufferedReader(
                InputStreamReader(context.assets.open(ACTIF))
            ).use { it.readText() }
            val blasons = JSONObject(texte).getJSONObject("blasons")
            val hors = HashMap<String, Blasonnement>(blasons.length())
            val cles = blasons.keys()
            while (cles.hasNext()) {
                val lemme = cles.next()
                val entree = blasons.getJSONObject(lemme)
                hors[lemme] = Blasonnement(
                    champ = Champ.parId(entree.optString("c").ifEmpty { null }),
                    meuble = entree.optString("m").ifEmpty { null }
                )
            }
            hors
        } catch (e: Exception) {
            // Un actif absent ou abîmé ne doit pas éteindre le carnet : sans
            // lui, toutes les cartes retombent sur la teinte de leurs trois
            // premières lettres et sur leur tracé, ce qui est présentable.
            Log.w("Armorial", "blasons illisibles, le carnet garde ses teintes de lettres", e)
            emptyMap()
        }
        table = lu
        return lu
    }
}
