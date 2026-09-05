package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 📖 TranslationDictionary — glose française des mots kréyòl
 *
 * L'actif `creole_translations.json` est produit par
 * `Dictionnaires/generate_translations.py` à partir de deux sources en
 * CC BY-SA 4.0 : Kreyolopedia et le Wiktionnaire francophone. Rien n'est
 * traduit ici : ce fichier ne fait que lire et chercher.
 *
 * **Le clavier lui-même ne s'en sert pas.** Un dictionnaire de traduction n'a
 * rien à faire dans le service de saisie, où il coûterait de la mémoire à
 * chaque ouverture du clavier pour un usage nul. Il n'est chargé que par
 * l'onglet Dictionnaire, l'onglet « Kréyòl an mwen » et les quatre jeux.
 *
 * La table glose 1 145 formes. Elles couvrent 622 des 5 296 mots du
 * dictionnaire de fréquences, soit 42 % des occurrences du corpus : le repli
 * d'accents de [parFormePliee] en rattrape 109 que les clés exactes ratent,
 * `kòlè` trouvant `kolé` et `evè` trouvant `èvè`. Le reste ne sert qu'à la
 * recherche, les jeux ne tirant que dans le dictionnaire.
 *
 * Ce qui reste sans glose n'est pas du bruit. `sé`, troisième mot le plus
 * fréquent du kréyòl, n'est glosé par aucune des deux sources. La couverture
 * montera quand les sources grandiront, pas quand ce fichier changera.
 *
 * @author Médhi Famibelle - Potomitan™
 */
object TranslationDictionary {

    private const val ASSET = "creole_translations.json"
    private const val TAG = "TranslationDictionary"

    /** Code de source écrit dans chaque entrée par le générateur. */
    private const val SOURCE_KREYOLOPEDIA = "K"
    private const val SOURCE_WIKTIONNAIRE = "W"
    private const val SOURCE_TRADUCTION = "T"

    /**
     * Une entrée de la table, telle qu'elle est livrée.
     *
     * [glose] tient sur une ligne sous le mot ; [definition] est vide quand
     * elle n'aurait rien dit de plus. [exemples] et [phonetique] ne viennent
     * que de Kreyolopedia, dont les fiches sont écrites à la main.
     */
    data class Entree(
        val mot: String,
        val glose: String,
        val source: String,
        val definition: String = "",
        val phonetique: String = "",
        val exemples: List<String> = emptyList(),
        val reference: String = ""
    ) {
        /** Le nom de la source, tel qu'il s'affiche sous une fiche. */
        val libelleSource: String
            get() = when (source) {
                SOURCE_KREYOLOPEDIA -> "Kreyolopedia"
                else -> "Wiktionnaire"
            }

        /**
         * L'adresse de l'article qui a fourni la glose, ou chaîne vide.
         *
         * Trois formes selon la source, et c'est la raison d'être du code :
         * Kreyolopedia livre l'URL complète de sa fiche ; une page
         * guadeloupéenne du Wiktionnaire porte le mot lui-même comme titre ;
         * un mot atteint par les traductions n'a **pas** de page à lui, et
         * son article est la page française qui le cite. Envoyer ce
         * troisième cas sur `wiktionary.org/wiki/<mot>` mènerait à une page
         * qui n'existe pas, ou pire, à un homographe d'une autre langue.
         */
        val url: String
            get() = when (source) {
                SOURCE_KREYOLOPEDIA -> reference
                SOURCE_WIKTIONNAIRE -> "https://fr.wiktionary.org/wiki/" + encoder(mot)
                SOURCE_TRADUCTION ->
                    if (reference.isEmpty()) ""
                    else "https://fr.wiktionary.org/wiki/" + encoder(reference)
                else -> ""
            }

        private fun encoder(titre: String): String =
            java.net.URLEncoder.encode(titre.replace(' ', '_'), "UTF-8")
                .replace("+", "_")
    }

    /** Forme telle que livrée par la table → son entrée. */
    private var entrees: Map<String, Entree> = emptyMap()

    /**
     * Même table, clés repliées (casse et accents retirés).
     *
     * Les jeux manipulent tantôt la casse du dictionnaire, tantôt une forme
     * majuscule (la grille de Mots Mêlés) ; et quelqu'un qui cherche « kreyol »
     * doit trouver « kréyòl », comme au clavier. Replier une fois au
     * chargement coûte moins cher que de replier à chaque consultation.
     */
    private var parFormePliee: Map<String, Entree> = emptyMap()

    private var attribution: String = ""
    private var estCharge = false

    /**
     * Formes pliées que l'application peut proposer d'elle-même.
     *
     * Le mot du jour n'en teste que quelques-unes, mais les jeux filtrent leur
     * réserve entière à chaque partie : `filtrerMotsTraduits` parcourt jusqu'à
     * 3 900 mots pour Mots Mêlés. Le calcul est donc fait une fois au
     * chargement. Il se confond aujourd'hui avec les clés de la table — le
     * générateur écarte déjà les gloses égales au mot — mais l'ensemble reste
     * le point de passage unique : si une source se met à en livrer, c'est ici
     * qu'on les arrêtera, pas dans chacun des quatre jeux.
     */
    private var proposables: Set<String> = emptySet()

    @Synchronized
    fun charger(context: Context) {
        if (estCharge) return
        estCharge = true

        try {
            val contenu = BufferedReader(
                InputStreamReader(context.assets.open(ASSET))
            ).use { it.readText() }

            val racine = JSONObject(contenu)
            val table = racine.getJSONObject("translations")
            val exactes = HashMap<String, Entree>(table.length())
            val pliees = HashMap<String, Entree>(table.length())

            val cles = table.keys()
            while (cles.hasNext()) {
                val mot = cles.next()
                val brut = table.getJSONObject(mot)
                val exemples = brut.optJSONArray("x")
                val entree = Entree(
                    mot = mot,
                    glose = brut.getString("g"),
                    source = brut.optString("s", SOURCE_TRADUCTION),
                    definition = brut.optString("d", ""),
                    phonetique = brut.optString("p", ""),
                    exemples = if (exemples == null) emptyList() else
                        (0 until exemples.length()).map { exemples.getString(it) },
                    reference = brut.optString("u", "")
                )
                exactes[mot] = entree
                // Premier arrivé, premier servi : le fichier est trié par
                // fréquence décroissante, donc à graphie repliée identique
                // (« sé » et « se ») c'est la forme la plus courante qui sert.
                // (putIfAbsent est API 24, minSdk vaut 21)
                val cle = AccentTolerantMatcher.normalize(mot)
                if (!pliees.containsKey(cle)) pliees[cle] = entree
            }

            entrees = exactes
            parFormePliee = pliees
            proposables = pliees.keys.toSet()

            val sources = racine.optJSONArray("attribution")
            attribution = if (sources == null) "" else
                (0 until sources.length()).joinToString("\n") { sources.getString(it) }

            Log.d(TAG, "${exactes.size} gloses chargées")
        } catch (e: Exception) {
            // Une glose manquante dégrade l'affichage, elle ne casse aucun jeu :
            // les mots restent jouables, ils ne sont simplement plus traduits.
            Log.e(TAG, "Actif $ASSET illisible: ${e.message}", e)
            entrees = emptyMap()
            parFormePliee = emptyMap()
            proposables = emptySet()
        }
    }

    /** L'entrée d'un mot, ou null s'il n'en a pas. */
    fun entree(context: Context, mot: String): Entree? {
        charger(context)
        if (mot.isEmpty()) return null
        return entrees[mot] ?: parFormePliee[AccentTolerantMatcher.normalize(mot)]
    }

    /** Glose française d'un mot, ou null s'il n'en a pas. */
    fun traduire(context: Context, mot: String): String? = entree(context, mot)?.glose

    /**
     * Glose prête à afficher à côté du mot, ou chaîne vide.
     * Le tiret cadratin sépare mieux que les parenthèses sur une seule ligne.
     */
    fun libelle(context: Context, mot: String, prefixe: String = "— "): String {
        val glose = traduire(context, mot) ?: return ""
        return "$prefixe$glose"
    }

    /**
     * Vrai si l'application peut proposer ce mot d'elle-même.
     *
     * C'est le point de passage unique du mot du jour et des quatre jeux :
     * un mot sans glose laisse une ligne vide là où le joueur attend un sens,
     * et fait passer le jeu pour cassé.
     */
    fun estProposable(context: Context, mot: String): Boolean {
        charger(context)
        return AccentTolerantMatcher.normalize(mot) in proposables
    }

    /**
     * Restreint une réserve de mots à ceux qui portent une glose. C'est ce qui
     * alimente le tirage des jeux ; la table complète, elle, reste consultable.
     *
     * Le repli n'est pas décoratif : si une régénération du dictionnaire ou un
     * actif manquant vidait la table, filtrer rendrait les jeux injouables sans
     * le moindre message. En dessous de [minimum] mots on rend donc la liste
     * d'origine, non traduite mais jouable.
     *
     * Les réserves mesurées le 5 septembre 2026 vont de 93 mots (Mo an
     * Karénaj, cinq lettres) à 380 (Mots Mêlés) : toutes très au-dessus du
     * seuil, qui ne se déclenche donc que sur panne.
     */
    fun filtrerMotsTraduits(
        context: Context,
        mots: List<String>,
        minimum: Int = 50
    ): List<String> {
        charger(context)
        val traduits = mots.filter { estProposable(context, it) }
        return if (traduits.size >= minimum) traduits else mots
    }

    /**
     * Cherche dans les deux sens : un mot kréyòl comme un mot français.
     *
     * Qui ouvre un dictionnaire ne sait pas toujours de quel côté il se tient :
     * il tape « maison » aussi souvent que « kaz ». Distinguer deux champs
     * aurait demandé de lui poser la question ; les deux sens partagent donc le
     * même champ, et le classement fait le tri.
     *
     * Le sens de la requête est **déduit, pas demandé** : si une glose vaut
     * exactement ce qui est tapé, alors ce qui est tapé est du français, et les
     * mots dont c'est le sens passent devant.
     *
     * Cette passe préalable n'est pas un raffinement. Le kréyòl a le français
     * pour langue lexificatrice : « liv » se glose « livre », et « livre » est
     * aussi une forme kréyòl. Sans elle, chercher « livre » remonterait
     * l'homographe avant le mot qu'on cherchait. Le même piège attend
     * « chat », « pou », « si ».
     *
     * Cinq rangs, du plus sûr au plus lâche : la glose exacte quand la requête
     * est française, la forme kréyòl exacte, le préfixe kréyòl, la glose où la
     * requête **commence un mot**, puis la glose où elle n'est qu'un morceau.
     * À rang égal, le mot le plus court d'abord.
     *
     * Ce dernier rang est séparé du précédent pour la même raison que chez le
     * clavier luxembourgeois, où la confusion des deux rendait les mots courts
     * inutilisables : « eau » y ramenait « beaucoup » et « nouveau » avant
     * « Waasser ». Ces résultats ne sont pas écartés, seulement relégués.
     */
    fun rechercher(context: Context, requete: String, maximum: Int = 40): List<Entree> {
        charger(context)
        val pliee = AccentTolerantMatcher.normalize(requete.trim())
        if (pliee.isEmpty()) return emptyList()

        // Repliés une fois par recherche, pas une fois par entrée et par rang.
        val index = entrees.values.map {
            Triple(it, AccentTolerantMatcher.normalize(it.mot),
                AccentTolerantMatcher.normalize(it.glose))
        }

        fun sensExact(glosePliee: String) =
            glosePliee.split(",").any { it.trim() == pliee }

        val requeteFrancaise = index.any { sensExact(it.third) }
        val rangFormeExacte = if (requeteFrancaise) 1 else 0

        val trouves = ArrayList<Pair<Int, Entree>>()
        for ((entree, formePliee, glosePliee) in index) {
            val rang = when {
                // N'arrive que si la requête est française : sinon aucune glose
                // ne lui est exactement égale.
                sensExact(glosePliee) -> 0
                formePliee == pliee -> rangFormeExacte
                formePliee.startsWith(pliee) -> 2
                debuteUnMot(glosePliee, pliee) -> 3
                glosePliee.contains(pliee) -> 4
                else -> continue
            }
            trouves.add(rang to entree)
        }

        return trouves
            .sortedWith(compareBy({ it.first }, { it.second.mot.length }, { it.second.mot }))
            .take(maximum)
            .map { it.second }
    }

    /**
     * La requête commence-t-elle un mot du texte ?
     *
     * Écrit à la main plutôt qu'avec une expression régulière : la boucle
     * tourne sur toute la table à chaque frappe, et compiler un motif par
     * recherche coûterait plus que la recherche elle-même. Un mot commence là
     * où le caractère précédent n'est pas une lettre — les deux textes sont
     * déjà repliés en minuscules sans accents.
     */
    private fun debuteUnMot(texte: String, motif: String): Boolean {
        var depuis = 0
        while (true) {
            val i = texte.indexOf(motif, depuis)
            if (i < 0) return false
            if (i == 0 || !texte[i - 1].isLetter()) return true
            depuis = i + 1
        }
    }

    /**
     * Crédits des deux sources, tels qu'ils voyagent dans l'actif lui-même.
     *
     * Kreyolopedia et le Wiktionnaire sont en CC BY-SA 4.0 : citer la source
     * n'est pas une politesse, c'est la condition de la réutilisation. Ce texte
     * s'affiche en pied de l'onglet Dictionnaire ; ne pas le retirer.
     */
    fun attribution(context: Context): String {
        charger(context)
        return attribution
    }

    /** Nombre de formes glosées, pour l'en-tête de l'onglet. */
    fun taille(context: Context): Int {
        charger(context)
        return entrees.size
    }
}
